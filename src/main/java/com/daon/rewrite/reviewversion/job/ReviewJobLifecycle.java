package com.daon.rewrite.reviewversion.job;

import com.daon.rewrite.llmjob.entity.LlmJobType;
import com.daon.rewrite.reviewversion.client.ReviewClientException;

/**
 * 첨삭 Job 종류별로 달라지는 시작 입력 준비·실패 전환·전체 확정을 ReviewJobExecutor에 연결한다.
 * 각 구현은 자신의 Job 종류만 처리하며, 메서드마다 별도의 짧은 트랜잭션을 연다.
 */
interface ReviewJobLifecycle {

    /** 이 구현이 처리하는 Job 종류다. ReviewJobExecutor가 Job 종류로 구현을 선택한다. */
    LlmJobType jobType();

    /** 외부 호출에 필요한 입력을 준비하고 PROCESSING으로 전환한다. 이미 시작했거나 종료된 Job이면 null을 반환한다. */
    ReviewJobInput start(String jobId);

    /** 분류된 LLM 실패로 Job과 자기소개서 표시 상태를 실패로 전환한다. 이미 종료된 Job은 변경하지 않는다. */
    void fail(String jobId, ReviewClientException.Reason reason);

    /** 분류되지 않은 예상 밖 오류를 내부 오류로 기록한다. 이미 종료된 Job은 변경하지 않는다. */
    void failUnexpected(String jobId);

    /** 모든 문항이 성공한 Job의 버전을 확정한다. */
    void complete(String jobId);
}
