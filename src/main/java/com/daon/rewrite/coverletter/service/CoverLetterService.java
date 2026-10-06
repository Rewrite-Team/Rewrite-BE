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
        // 해당 자기소개서에 기존에 저장돼 있던 문항들을 전부 삭제
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

    /*
    자기소개서 제출을 처리하고, 필요하면 최초 AI 첨삭 Job 생성

    [성공 케이스 3가지]
    상황                      상태          반환Job
    최초 제출 또는 실패 후 재시도	 REVIEWING	  새 Job
    최초 첨삭 중 중복 제출	     REVIEWING	  기존 Job
    이미 최초 첨삭 완료	         REVIEWED	  null
     */
    @Transactional
    public SubmitCoverLetterResult submit(String coverLetterId) {
        // 현재 사용자 조회
        CurrentUser currentUser = currentUserProvider.currentUser();
        // 자기소개서 조회 (coverLetterId + 현재 사용자 소유 + 삭제x)
        CoverLetter coverLetter = coverLetterRepository
                .findActiveByIdAndOwnerIdForUpdate(coverLetterId, currentUser.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 이미 진행 중인 LLM job(PENDING, PROCESSING) 조회
        LlmJob runningJob = llmJobService.findRunningCoverLetterJob(coverLetter.getId());

        // 이미 Job이 진행 중이라면
        if (runningJob != null) {
            // 동일한 최초 첨삭 Job이 이미 진행 중이라면, 새 Job을 만들지 않고 기존 Job 반환
            // 버튼 중복 클릭, 네트워크 재시도에 대한 멱등 처리.
            if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING
                    && runningJob.getType() == LlmJobType.COVER_LETTER_REVIEW) {
                return new SubmitCoverLetterResult(coverLetter, runningJob);
            }
            // 다른 종류의 Job 이 진행중이라면, 409 Conflict 반환
            throw new BusinessException(ErrorCode.LLM_JOB_ALREADY_RUNNING);
        }

        // 이미 첨삭 결과가 있는 경우 이미 최초 첨삭이 완료된 것이기에 새 Job을 만들지 않는다.
        if (coverLetter.getLatestReviewedVersionId() != null) {
            // 성공 버전이 있는데 REVIEW_FAILED 라면, 최초 첨삭 API 가 아니라 재첨삭 API를 사용해야 하므로 CONFLICT 반환
            if (coverLetter.getStatus() != CoverLetterStatus.REVIEWED) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            return new SubmitCoverLetterResult(coverLetter, null);
        }

        // 자기소개서는 REVIEWING인데 진행 중 Job이 발견되지 않은 비정상적인 상태 조합 방어
        if (coverLetter.getStatus() == CoverLetterStatus.REVIEWING) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        // 저장된 문항을 등록 순서대로 조회
        List<CoverLetterQuestion> questions = coverLetterQuestionRepository
                .findByCoverLetterIdOrderByQuestionOrderAsc(coverLetter.getId());
        // 제출 필수값 최종 검증
        inputPolicy.validateSubmit(coverLetter, questions);

        Instant now = Instant.now(clock);
        /*
         아래 상태의 LlmJob 저장
         type: COVER_LETTER_REVIEW
         status: PENDING
         targetType: COVER_LETTER
         targetId: 자기소개서 ID
         progressCurrent: 0
         progressTotal: 문항 수
         maxAttempts: 2
         */
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

        /*
         자소서 상태를 REVIEWING으로 변경
         최초 제출이면 submittedAt 설정 및 updatedAt 갱신
         */
        coverLetter.startReview(now);

        /*
         * IMPROVE
         *  현재 구조에서는 LlmJobCreatedEvent가 애플리케이션 메모리 안에서만 전달된다. 따라서 커밋 직후 종료되면 인메모리 이벤트가 유실될 수 있다.
         *  서버 재시작 중 Job 유실 방지나 Worker 수평 확장이 필요해지는 시점에 "Outbox 패턴 + 메시지큐 + 독립 Worker" 구조로 전환 필요
         */
        // 비동기 첨삭을 시작하는 이벤트 발행 (ReviewJobEventListener.java의 리스너가 FirstReviewJobWorker.execute(jobId) 실행)
        eventPublisher.publishEvent(new LlmJobCreatedEvent(job.getId()));

        // AI 첨삭 완료를 기다리지 않고 REVIEWING과 jobId를 바로 반환
        return new SubmitCoverLetterResult(coverLetter, job);
    }

    private void validateListQuery(int page, int size) {
        if (page < 1 || size < 1 || size > MAX_LIST_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

}
