package com.daon.rewrite.coverletter.service;

import com.daon.rewrite.auth.CurrentUser;
import com.daon.rewrite.auth.CurrentUserProvider;
import com.daon.rewrite.coverletter.entity.CoverLetter;
import com.daon.rewrite.coverletter.entity.CoverLetterQuestion;
import com.daon.rewrite.coverletter.entity.CoverLetterStatus;
import com.daon.rewrite.coverletter.repository.CoverLetterQuestionRepository;
import com.daon.rewrite.coverletter.repository.CoverLetterRepository;
import com.daon.rewrite.global.exception.BusinessException;
import com.daon.rewrite.global.exception.ErrorCode;
import com.daon.rewrite.global.util.IdGenerator;
import com.daon.rewrite.llmjob.entity.LlmJob;
import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.llmjob.repository.LlmJobRepository;
import com.daon.rewrite.llmjob.service.LlmJobCreatedEvent;
import com.daon.rewrite.llmjob.service.LlmJobService;
import com.daon.rewrite.reviewversion.entity.ReviewVersion;
import com.daon.rewrite.reviewversion.repository.ReviewVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 자기소개서 초안 생성, 등록 단계별 임시저장, 최초 첨삭 제출과 soft delete를 처리한다.
 * 변경 요청은 소유자의 활성 CoverLetter를 먼저 잠그며, 원본 편집은 WRITING에서만 허용한다.
 * 임시저장은 미완성 폼을 보존하고, 제출 시 입력 완성도를 검증한 뒤 Job·버전·자기소개서 상태를 함께 저장한다.
 */
@Service
@RequiredArgsConstructor
public class CoverLetterService {

    private static final String COVER_LETTER_ID_PREFIX = "cl";
    private static final int MAX_LIST_SIZE = 9;
    private static final String COVER_LETTER_QUESTION_ID_PREFIX = "clq";
    private static final String LLM_JOB_ID_PREFIX = "job";
    private static final String REVIEW_VERSION_ID_PREFIX = "rv";
    private final CurrentUserProvider currentUserProvider;
    private final CoverLetterRepository coverLetterRepository;
    private final CoverLetterQuestionRepository coverLetterQuestionRepository;
    private final CoverLetterInputPolicy inputPolicy;
    private final LlmJobRepository llmJobRepository;
    private final ReviewVersionRepository reviewVersionRepository;
    private final LlmJobService llmJobService;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    /** 입력을 받기 전부터 이어서 작성할 ID를 확보하도록 현재 사용자의 빈 WRITING 초안을 저장한다. */
    @Transactional
    public CoverLetter create() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = CoverLetter.create(
                idGenerator.generate(COVER_LETTER_ID_PREFIX),
                currentUser.id(),
                Instant.now(clock)
        );

        return coverLetterRepository.save(coverLetter);
    }

    /**
     * 현재 사용자의 삭제되지 않은 초안·첨삭 자기소개서를 상태 필터 없이 생성 시각 내림차순으로 조회한다.
     * page는 1 이상, size는 목록 최대 크기 이내인지 검증하고 JPA의 0부터 시작하는 페이지로 변환한다.
     * 전체 페이지를 넘는 page는 빈 목록으로 반환한다.
     */
    @Transactional(readOnly = true)
    public Page<CoverLetter> findMyCoverLetters(int page, int size) {
        validateListQuery(page, size);

        CurrentUser currentUser = currentUserProvider.currentUser();
        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return coverLetterRepository.findByOwnerIdAndDeletedAtIsNull(
                currentUser.id(),
                pageable
        );
    }

    /**
     * CoverLetter → 진행 Job 순서로 잠그고, Job 취소와 자기소개서 삭제 표시를 같은 트랜잭션에 반영한다.
     * 하위 데이터는 보존하며 사용자 조회 경로가 deletedAt을 기준으로 접근을 차단한다.
     */
    @Transactional
    public void deleteMyCoverLetter(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Instant now = Instant.now(clock);
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());
        if (runningJob != null) {
            runningJob.cancel(now);
        }
        coverLetter.markDeleted(now);
    }

    /**
     * 기본 정보 step의 전체 폼을 정규화해 교체한다. 미입력 필드는 null로 저장할 수 있다.
     * 제출과 같은 CoverLetter 잠금을 사용하므로 먼저 제출된 초안에 늦게 도착한 자동저장은 거부한다.
     */
    @Transactional
    public void saveBasicInfo(
            String coverLetterId,
            String title,
            String companyName,
            String positionTitle,
            String jobPostingUrl
    ) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_WRITING);
        }

        CoverLetterInputPolicy.BasicInfo input = inputPolicy.normalizeBasicInfo(
                title,
                companyName,
                positionTitle,
                jobPostingUrl
        );
        coverLetter.fillBasicInfo(
                input.title(),
                input.companyName(),
                input.positionTitle(),
                input.jobPostingUrl(),
                Instant.now(clock)
        );

    }

    /** 우대사항 step을 교체 저장하며, WRITING에서는 빈 입력도 미완성 값으로 보존한다. */
    @Transactional
    public void savePreferences(String coverLetterId, String preferences) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_WRITING);
        }

        String normalizedPreferences = inputPolicy.normalizePreferences(preferences);
        coverLetter.fillPreferences(normalizedPreferences, Instant.now(clock));

    }

    /**
     * 문항 step의 전체 목록을 검증한 뒤 기존 행을 새 문항 ID로 교체하고 배열 순서대로 1부터 순서를 부여한다.
     * null·빈 목록이면 전체 삭제하며, 입력 오류가 있으면 기존 문항을 교체하지 않는다.
     * 문항 필수값의 완성 여부는 제출 시 확인하므로 임시저장 중에는 nullable 필드를 허용한다.
     */
    @Transactional
    public void saveQuestions(String coverLetterId, List<SaveQuestionInput> questions) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (coverLetter.getStatus() != CoverLetterStatus.WRITING) {
            throw new BusinessException(ErrorCode.COVER_LETTER_NOT_WRITING);
        }

        List<SaveQuestionInput> normalizedQuestions = inputPolicy.normalizeQuestions(questions);
        coverLetterQuestionRepository.deleteByCoverLetter(coverLetter);

        List<CoverLetterQuestion> savedQuestions = new ArrayList<>();
        for (int index = 0; index < normalizedQuestions.size(); index++) {
            SaveQuestionInput question = normalizedQuestions.get(index);
            savedQuestions.add(CoverLetterQuestion.create(
                    idGenerator.generate(COVER_LETTER_QUESTION_ID_PREFIX),
                    coverLetter,
                    index + 1,
                    question.question(),
                    question.maxAnswerLength(),
                    question.originalAnswer()
            ));
        }
        savedQuestions = coverLetterQuestionRepository.saveAll(savedQuestions);

        coverLetter.touch(Instant.now(clock));
    }

    /**
     * 최초 제출 또는 최신 성공 버전이 없는 최초 실패 후 재시도는 새 Job·버전을 만들고 REVIEWING을 반환한다.
     * 같은 최초 첨삭 중 중복 제출은 REVIEWING과 기존 Job을 반환한다.
     * 이미 첨삭 완료된 자기소개서는 REVIEWED와 null Job을 반환한다.
     * 새 작업은 저장된 원본의 필수값 검증 후 생성하며, 검증 실패나 기존 Job 재사용에는 새 버전을 만들지 않는다.
     * Job·버전·REVIEWING 전환을 함께 커밋하고 AI 완료를 기다리지 않고 반환하며, worker 실행은 커밋 후 이벤트 리스너로 이어진다.
     */
    @Transactional
    public SubmitCoverLetterResult submit(String coverLetterId) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        // Job이 아직 없는 경우에도 동시 제출이 둘 다 새 Job을 만들지 않도록 자기소개서를 먼저 잠근다.
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());

        if (runningJob != null) {
            // 버튼 중복 클릭·네트워크 재전송에는 같은 최초 첨삭만 재사용하며, 다른 종류의 진행 Job은 충돌로 처리한다.
            if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING
                    && runningJob.getType() == LlmJobType.COVER_LETTER_REVIEW) {
                return new SubmitCoverLetterResult(coverLetter, runningJob);
            }
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        // 성공 이력이 있는 재첨삭 실패는 기존 최종 작성본을 기준으로 재첨삭 API에서 재시도해야 한다.
        if (coverLetter.getLatestReviewedVersionId() != null) {
            if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return new SubmitCoverLetterResult(coverLetter, null);
        }

        // REVIEWING에 대응하는 진행 Job이 없으면 정상적인 수동 재시도로 취급할 수 없다.
        if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        List<CoverLetterQuestion> questions = coverLetterQuestionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        inputPolicy.validateSubmit(coverLetter, questions);

        Instant now = Instant.now(clock);
        // 결과 완성 전부터 시도 이력을 남기므로 새 Job과 빈 첨삭 버전을 같은 트랜잭션에서 생성한다.
        LlmJob job = llmJobRepository.save(LlmJob.pendingReview(
                idGenerator.generate(LLM_JOB_ID_PREFIX),
                coverLetter.getId(),
                now,
                questions.size()
        ));
        long nextPatch = reviewVersionRepository.countByCoverLetterId(coverLetter.getId()) + 1;
        reviewVersionRepository.save(ReviewVersion.started(
                idGenerator.generate(REVIEW_VERSION_ID_PREFIX),
                coverLetter,
                "v0." + nextPatch,
                null,
                job,
                now
        ));

        coverLetter.startReview(now);

        // ReviewJobEventListener가 커밋 후 최초 첨삭 worker를 실행한다. 메모리 이벤트의 재전달은 보장하지 않는다.
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        return new SubmitCoverLetterResult(coverLetter, job);
    }

    private void validateListQuery(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_LIST_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

}
