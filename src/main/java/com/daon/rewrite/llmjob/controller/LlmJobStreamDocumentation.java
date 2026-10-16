package com.daon.rewrite.llmjob.controller;

/** Swagger 본문과 named examples에서 같은 SSE 예시를 사용하는 문서용 상수다. */
final class LlmJobStreamDocumentation {

    static final String COVER_LETTER_REVIEW = """
            event: job.state
            data: {"jobType":"COVER_LETTER_REVIEW","status":"PROCESSING","progress":{"current":0,"total":1,"message":"첨삭을 시작합니다."},"resultRef":null,"error":null}

            event: review.questions
            data: {"items":[]}

            event: heartbeat
            data: {}

            event: review.questions
            data: {"items":[{"questionId":"question_01","order":1,"aiReport":"분석","rewrittenAnswer":"수정 답변","rewrittenAnswerLength":5,"finalAnswer":"수정 답변","finalAnswerLength":5}]}

            event: job.state
            data: {"jobType":"COVER_LETTER_REVIEW","status":"COMPLETED","progress":{"current":1,"total":1,"message":"첨삭이 완료되었습니다."},"resultRef":{"type":"REVIEW_VERSION","id":"review_version_01"},"error":null}

            """;

    static final String COVER_LETTER_RE_REVIEW = """
            event: job.state
            data: {"jobType":"COVER_LETTER_RE_REVIEW","status":"PROCESSING","progress":{"current":0,"total":1,"message":"재첨삭을 시작합니다."},"resultRef":null,"error":null}

            event: review.questions
            data: {"items":[]}

            event: heartbeat
            data: {}

            event: review.questions
            data: {"items":[{"questionId":"question_01","order":1,"aiReport":"성과 보강","rewrittenAnswer":"보강 답변","rewrittenAnswerLength":5,"finalAnswer":"보강 답변","finalAnswerLength":5}]}

            event: job.state
            data: {"jobType":"COVER_LETTER_RE_REVIEW","status":"COMPLETED","progress":{"current":1,"total":1,"message":"첨삭이 완료되었습니다."},"resultRef":{"type":"REVIEW_VERSION","id":"review_version_02"},"error":null}

            """;

    static final String KEYWORD_ANALYSIS = """
            event: job.state
            data: {"jobType":"KEYWORD_ANALYSIS","status":"PROCESSING","progress":{"current":0,"total":1,"message":"키워드 분석을 시작합니다."},"resultRef":null,"error":null}

            event: heartbeat
            data: {}

            event: job.state
            data: {"jobType":"KEYWORD_ANALYSIS","status":"COMPLETED","progress":{"current":1,"total":1,"message":"키워드 분석이 완료되었습니다."},"resultRef":{"type":"KEYWORD_ANALYSIS","id":"keyword_analysis_01"},"error":null}

            """;

    static final String INTERVIEW_INITIAL_QUESTION_GENERATION = """
            event: job.state
            data: {"jobType":"INTERVIEW_INITIAL_QUESTION_GENERATION","status":"PROCESSING","progress":{"current":0,"total":5,"message":"면접 질문 생성을 시작합니다."},"resultRef":null,"error":null}

            event: heartbeat
            data: {}

            event: job.state
            data: {"jobType":"INTERVIEW_INITIAL_QUESTION_GENERATION","status":"COMPLETED","progress":{"current":5,"total":5,"message":"면접 질문 생성이 완료되었습니다."},"resultRef":{"type":"INTERVIEW_SESSION","id":"interview_session_01"},"error":null}

            """;

    static final String INTERVIEW_ADDITIONAL_QUESTION_GENERATION = """
            event: job.state
            data: {"jobType":"INTERVIEW_ADDITIONAL_QUESTION_GENERATION","status":"PROCESSING","progress":{"current":0,"total":1,"message":"면접 질문 생성을 시작합니다."},"resultRef":null,"error":null}

            event: heartbeat
            data: {}

            event: job.state
            data: {"jobType":"INTERVIEW_ADDITIONAL_QUESTION_GENERATION","status":"COMPLETED","progress":{"current":1,"total":1,"message":"면접 질문 생성이 완료되었습니다."},"resultRef":{"type":"INTERVIEW_QUESTION","id":"interview_question_06"},"error":null}

            """;

    static final String INTERVIEW_MESSAGE_FEEDBACK = """
            event: job.state
            data: {"jobType":"INTERVIEW_MESSAGE_FEEDBACK","status":"PROCESSING","progress":{"current":0,"total":1,"message":"면접 답변 피드백 생성을 시작합니다."},"resultRef":null,"error":null}

            event: heartbeat
            data: {}

            event: interview.feedback.delta
            data: {"sequence":1,"contentDelta":"답변에서 API 설계 경험을 구체적으로 설명한 점이 좋습니다. 성과 지표"}

            event: interview.feedback.delta
            data: {"sequence":2,"contentDelta":"를 더하면 전달력이 좋아집니다."}

            event: job.state
            data: {"jobType":"INTERVIEW_MESSAGE_FEEDBACK","status":"COMPLETED","progress":{"current":1,"total":1,"message":"면접 답변 피드백 생성이 완료되었습니다."},"resultRef":{"type":"INTERVIEW_MESSAGE","id":"interview_message_02"},"error":null}

            """;

    static final String BEHAVIOR = """
            job.state 이벤트로 최초 상태, 진행률과 종료 상태를 전달한다.
            job.state는 jobType, status, progress, resultRef, error를 항상 포함한다. progress의 current, total, message도 항상 포함한다.
            status는 PENDING, PROCESSING, COMPLETED, FAILED, CANCELED 중 하나이며 resultRef는 완료 시, error는 실패 시에만 값이 있고 그 외에는 null이다.
            job.state.status=FAILED는 정상 SSE 연결에서 받은 비동기 작업 결과이며 HTTP ErrorResponse가 아니다.
            상태 변화가 없어도 기본 15초 주기로 빈 객체를 담은 heartbeat 이벤트를 전달한다. 전송 주기는 서버 설정에 따라 바뀔 수 있다.

            #### Job별 연결과 완료 후 조회

            시작 API에서 반환한 jobId로 이 API에 연결한다. 아래 6종 Job을 모두 지원한다.

            | 작업 | jobType | 시작 API | 공통 상태 외 추가 이벤트 | COMPLETED 후 조회 |
            |---|---|---|---|---|
            | 최초 첨삭 | COVER_LETTER_REVIEW | API-014 | review.questions | API-012 또는 API-018 |
            | 재첨삭 | COVER_LETTER_RE_REVIEW | API-024 | review.questions | API-012 또는 API-018 |
            | 키워드 분석 | KEYWORD_ANALYSIS | API-020 | 없음 | API-021 |
            | 초기 면접 질문 생성 | INTERVIEW_INITIAL_QUESTION_GENERATION | API-022 | 없음 | API-025와 API-026 |
            | 추가 면접 질문 생성 | INTERVIEW_ADDITIONAL_QUESTION_GENERATION | API-027 | 없음 | API-026 |
            | 면접 답변 피드백 | INTERVIEW_MESSAGE_FEEDBACK | API-023 | interview.feedback.delta | API-029 |

            키워드 분석과 초기·추가 면접 질문 생성은 job.state와 heartbeat로 상태를 확인하고, 결과 내용은 완료 후 표의 조회 API로 받는다.
            첨삭은 연결 직후 review.questions.items로 전체 완료 문항 스냅샷을 보내며, 이후 새 문항이 완성되면 해당 문항 하나를 보낸 뒤 갱신된 job.state를 보낸다. 문항은 questionId로 갱신하고 order로 정렬한다.
            면접 피드백은 전체 AI 응답을 검증한 뒤 완성된 content를 sequence 순서의 조각으로 분할 전송한다. sequence 순서로 contentDelta를 이어 붙이고 완료 후 API-029의 저장된 content와 score로 확정한다.

            #### Job별 SSE 예시

            아래 코드블록은 서로 다른 Job의 독립적인 성공 예시다. 첨삭 예시는 입력 문항 1개와 연결 시 완료 문항이 없는 경우를 사용한다.
            연결 시점과 작업 속도에 따라 중간 상태와 heartbeat 수신 여부는 달라지며, heartbeat 간격 전에 완료되면 heartbeat 없이 종료될 수 있다.
            이미 종료된 Job에 연결하면 최종 상태를 받고 첨삭은 완료 문항 스냅샷까지 받은 뒤 종료된다.

            ##### 최초 첨삭 · COVER_LETTER_REVIEW

            ```text
            """ + COVER_LETTER_REVIEW + """
            ```

            ##### 재첨삭 · COVER_LETTER_RE_REVIEW

            ```text
            """ + COVER_LETTER_RE_REVIEW + """
            ```

            ##### 키워드 분석 · KEYWORD_ANALYSIS

            ```text
            """ + KEYWORD_ANALYSIS + """
            ```

            ##### 초기 면접 질문 생성 · INTERVIEW_INITIAL_QUESTION_GENERATION

            ```text
            """ + INTERVIEW_INITIAL_QUESTION_GENERATION + """
            ```

            ##### 추가 면접 질문 생성 · INTERVIEW_ADDITIONAL_QUESTION_GENERATION

            ```text
            """ + INTERVIEW_ADDITIONAL_QUESTION_GENERATION + """
            ```

            ##### 면접 답변 피드백 · INTERVIEW_MESSAGE_FEEDBACK

            ```text
            """ + INTERVIEW_MESSAGE_FEEDBACK + """
            ```
            """;

    private LlmJobStreamDocumentation() {
    }
}
