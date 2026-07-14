# Interview API

## Interview API

자기소개서당 AI 면접 세션은 하나만 유지한다. AI 면접 페이지는 현재 면접 세션 상태에 따라 다음 화면을 표시한다.

```text
세션 없음: 모의면접 시작하기 화면
QUESTION_GENERATING: 초기 질문 생성 진행 화면
ACTIVE: 기존 대화 화면과 질문 리스트
FAILED: 실패 안내와 다시 시작하기 또는 재시도 버튼
```

면접 세션은 최초 질문 세트를 생성할 때 기준이 된 첨삭 버전을 `initialSourceReviewVersionId`로 기록한다. 재첨삭 후에도 기존 면접 세션과 기존 질문별 대화방은 유지된다.

면접 질문과 질문별 thread는 항상 같은 transaction에서 1:1로 생성한다. 사용자가 `새로운 질문 추가하기`를 실행하면 기존 면접 세션에 가장 최근 첨삭 버전 기준 면접 질문 1개와 thread 1개를 추가한다. 기존 질문과 대화 기록은 변경하지 않는다.

### 현재 면접 세션 조회

AI 면접 페이지 진입 시 사용한다.

```http
GET /cover-letters/{coverLetterId}/interview
```

면접 세션이 없는 경우:

```json
{
  "coverLetterId": "cl_01HZ...",
  "interviewSession": null
}
```

면접 세션이 있는 경우:

```json
{
  "coverLetterId": "cl_01HZ...",
  "interviewSession": {
    "id": "is_01HZ...",
    "initialSourceReviewVersionId": "rv_01HZ...",
    "status": "ACTIVE",
    "createdAt": "2026-06-20T16:00:00"
  }
}
```

면접 세션이 없는 것은 정상 상태이므로 `200 OK`와 `interviewSession: null`을 반환한다.
자기소개서가 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 경우에는 `NOT_FOUND`를 반환한다.
재첨삭 후에도 기존 면접 세션을 유지하므로 자기소개서의 현재 상태와 관계없이 세션을 조회한다.

### 모의면접 시작

```http
POST /cover-letters/{coverLetterId}/interviews
```

Request:

```json
{
  "sourceReviewVersionId": "rv_01HZ..."
}
```

`sourceReviewVersionId`를 생략하면 최신 첨삭 버전을 기준으로 면접 질문을 생성한다.

면접 질문은 총 5개 생성한다. 모든 질문은 자기소개서 최종 작성본에 드러난 경험, 역할, 행동, 성과, 문제 해결 과정과 의사결정을 기반으로 하며 질문 종류를 구분하지 않는다.

Response:

```json
{
  "interviewSessionId": "is_01HZ...",
  "jobId": "job_01HZ...",
  "status": "QUESTION_GENERATING"
}
```

`LlmJob(type=INTERVIEW_QUESTION_GENERATION)`은 after-commit 비동기 worker에서 실행한다.
생성에 성공하면 질문 5개와 질문별 thread 5개를 저장하고 `InterviewSession.status`를 `ACTIVE`로 전환한다.
완료 Job의 `resultRef`는 해당 면접 세션을 가리킨다.

질문 생성 실패 시 `InterviewSession.status`는 `FAILED`로 저장된다. 실패한 세션은 현재 면접 세션 조회 API에서 그대로 반환한다.

이미 `ACTIVE` 또는 `QUESTION_GENERATING` 면접 세션이 있는 경우 이 API에서는 초기 질문 생성 Job을 새로 만들지 않고 기존 세션을 반환한다. 이때 `jobId`는 `null`이고 `status`는 현재 세션 상태를 반환한다. 기존 세션에 질문을 추가하려면 `POST /interviews/{interviewSessionId}/questions`를 사용한다.

```json
{
  "interviewSessionId": "is_01HZ...",
  "jobId": null,
  "status": "ACTIVE"
}
```

단, 기존 면접 세션이 `FAILED` 상태이면 같은 `interviewSessionId`를 재사용해 다시 `QUESTION_GENERATING`으로 전환하고 새 질문 생성 Job을 시작한다. 프론트엔드는 `FAILED` 상태에서 다시 시작하기 또는 재시도 버튼으로 이 API를 다시 호출한다.

```json
{
  "interviewSessionId": "is_01HZ...",
  "jobId": "job_01HZ...",
  "status": "QUESTION_GENERATING"
}
```

Validation:

```text
coverLetter.status는 REVIEWED여야 한다.
sourceReviewVersionId가 있으면 해당 자기소개서의 첨삭 버전이어야 한다.
같은 자기소개서에 PENDING 또는 PROCESSING 상태의 LLM Job이 없어야 한다.
```

### 면접 질문 목록 조회

```http
GET /interviews/{interviewSessionId}/questions
```

Response:

```json
{
  "interviewSessionId": "is_01HZ...",
  "items": [
    {
      "id": "iq_01HZ...",
      "sourceReviewVersionId": "rv_01HZ...",
      "order": 1,
      "question": "프로젝트에서 맡은 역할을 더 구체적으로 설명해 주세요.",
      "threadId": "it_01HZ..."
    },
    {
      "id": "iq_01HY...",
      "sourceReviewVersionId": "rv_01HZ...",
      "order": 2,
      "question": "지원 동기에서 언급한 회사 선택 기준을 실제 경험과 연결해 설명해 주세요.",
      "threadId": "it_01HY..."
    },
    {
      "id": "iq_01HX...",
      "sourceReviewVersionId": "rv_01HZ...",
      "order": 3,
      "question": "자기소개서에 작성한 협업 경험에서 갈등을 어떻게 해결했는지 설명해 주세요.",
      "threadId": "it_01HX..."
    },
    {
      "id": "iq_01HW...",
      "sourceReviewVersionId": "rv_01HZ...",
      "order": 4,
      "question": "프로젝트 성과를 만들기 위해 본인이 직접 수행한 행동을 설명해 주세요.",
      "threadId": "it_01HW..."
    },
    {
      "id": "iq_01HV...",
      "sourceReviewVersionId": "rv_01HZ...",
      "order": 5,
      "question": "문제 해결 과정에서 가장 중요하게 내린 의사결정을 설명해 주세요.",
      "threadId": "it_01HV..."
    }
  ]
}
```

질문은 `order` 오름차순으로 반환한다. 모든 질문은 생성 시 thread가 함께 저장되므로 `threadId`는 필수값이다. 질문은 존재하지만 thread가 없으면 데이터 불변식 위반으로 처리한다.

질문 생성 중이거나 생성 결과가 없는 세션은 정상 상태이므로 `200 OK`와 빈 `items`를 반환한다.
면접 세션이 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 세션이면 `NOT_FOUND`를 반환한다.

### 면접 질문 추가 생성

화면 우측 예상 질문 리스트 상단의 `새로운 질문 추가하기` 버튼을 눌렀을 때 사용한다.

```http
POST /interviews/{interviewSessionId}/questions
```

Request body는 없다. 서버는 항상 해당 자기소개서의 최신 첨삭 버전인 `CoverLetter.latestReviewVersionId`를 기준으로 질문을 생성한다.

추가 질문은 자기소개서 최종 작성본을 기반으로 총 1개 생성한다. 질문 종류는 구분하지 않는다.

생성된 질문은 기존 질문 목록 뒤에 이어서 `order`를 부여한다.

```text
기존 질문 order: 1~5
추가 질문 order: 6
```

Response:

```json
{
  "interviewSessionId": "is_01HZ...",
  "jobId": "job_01HZ...",
  "status": "ACTIVE",
  "jobStatus": "PENDING"
}
```

질문 추가 생성 중에도 기존 면접 세션과 기존 질문별 대화방은 유지된다. 추가 질문 생성 Job이 실패해도 `InterviewSession.status`는 `ACTIVE`를 유지하고, 실패 상태는 `LlmJob.status`와 `LlmJob.error`로 확인한다.

Validation:

```text
interviewSession.status는 ACTIVE여야 한다.
연결된 coverLetter.status는 REVIEWED여야 한다.
같은 자기소개서에 PENDING 또는 PROCESSING 상태의 LLM Job이 없어야 한다.
```

### 질문별 대화방 정책

질문별 thread는 질문 생성 시 함께 저장하므로 별도 생성 API를 제공하지 않는다. API-028 `POST /interviews/{interviewSessionId}/threads`는 사용하지 않는다.

화면에서 질문을 선택하면 API-026이 반환한 `threadId`로 해당 대화 메시지를 조회한다. 최초 면접 질문은 `InterviewQuestion.question`을 표시하며 `ASSISTANT` 메시지로 중복 저장하지 않는다.

### 대화 메시지 조회

```http
GET /interview-threads/{threadId}/messages
```

Response:

```json
{
  "threadId": "it_01HZ...",
  "items": [
    {
      "id": "im_01HY...",
      "role": "USER",
      "content": "저는 프로젝트에서 API 설계를 담당했습니다.",
      "feedback": null,
      "score": null,
      "followUpQuestion": null,
      "createdAt": "2026-06-20T16:06:00"
    },
    {
      "id": "im_01HZ2...",
      "role": "ASSISTANT",
      "content": "답변에서 API 설계 경험은 드러났지만 성과와 의사결정 근거가 부족합니다. 이어서, 그 API 설계에서 가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
      "feedback": {
        "summary": "역할은 명확하지만 성과와 판단 근거가 부족합니다.",
        "strengths": [
          "담당 역할을 구체적으로 언급했습니다."
        ],
        "improvements": [
          "성과 지표와 문제 해결 과정을 보강하세요."
        ]
      },
      "score": 78,
      "followUpQuestion": "그 API 설계에서 가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
      "createdAt": "2026-06-20T16:06:00"
    }
  ]
}
```

최초 면접 질문은 API-026의 `question`으로 표시한다. `items`에는 사용자가 답변을 전송한 시점부터 `USER`, `ASSISTANT` 메시지가 순서대로 저장된다.

메시지는 `createdAt`, `id` 오름차순으로 반환한다. 메시지가 없는 새 thread는 정상 상태이므로 `200 OK`와 빈 `items`를 반환한다. thread가 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 thread이면 `NOT_FOUND`를 반환한다.

### 사용자 답변 전송

```http
POST /interview-threads/{threadId}/messages
```

Request:

```json
{
  "content": "저는 프로젝트에서 API 설계를 담당했습니다."
}
```

Validation:

```text
content: 필수, 최대 2000자
같은 자기소개서에 PENDING 또는 PROCESSING 상태의 LLM Job이 없어야 한다.
```

Response:

```json
{
  "userMessageId": "im_01HY...",
  "jobId": "job_01HZ...",
  "status": "PENDING"
}
```

요청이 성공하면 trim된 USER 메시지와 `INTERVIEW_MESSAGE_FEEDBACK` Job을 같은 transaction에서 저장한다. 생성 직후 Job 상태는 `PENDING`이며, Job이 실제 실행을 시작하면 `PROCESSING`으로 전환된다.

thread가 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 thread이면 `NOT_FOUND`를 반환한다. 같은 자기소개서에 이미 `PENDING` 또는 `PROCESSING` Job이 있으면 USER 메시지를 저장하지 않고 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

피드백 Job은 생성된 USER 메시지 ID를 입력 참조로 저장한다. 비동기 worker는 이 참조를 기준으로 처리할 답변을 확정하고, 원본 면접 질문과 해당 USER 메시지까지의 대화 이력을 순서대로 OpenAI client에 전달한다.

LLM 작업이 완료되면 assistant 메시지와 Job 완료 상태를 같은 transaction에서 저장한다. assistant 메시지는 항상 `feedback`, `score`, `followUpQuestion`을 포함하며, 완료된 Job의 `resultRef`는 생성된 `INTERVIEW_MESSAGE`를 가리킨다.

provider 호출 또는 출력 검증에 실패하면 Job은 `FAILED`로 종료하고 assistant 메시지는 저장하지 않는다. 이미 완료되거나 실패한 Job event가 다시 전달되어도 메시지를 중복 생성하지 않는다.

면접 답변 피드백 Job(`INTERVIEW_MESSAGE_FEEDBACK`)은 진행 중 assistant 메시지를 미리 생성하지 않는다. 스트리밍 중 생성되는 delta는 현재 SSE 연결 화면에만 표시하며, 서버는 면접 피드백용 partial result를 저장하지 않는다.

사용자가 면접 피드백 생성 중 화면을 이탈했다가 다시 진입하면, 완료 전까지는 진행 상태만 복구한다. Job이 완료되면 저장된 assistant 메시지를 `GET /interview-threads/{threadId}/messages`로 조회해 표시한다.

사용자 답변 1개에 대해 assistant 메시지 1개를 저장한다. 꼬리질문은 `followUpQuestion` 필드로 포함하며 별도 assistant 메시지로 분리하지 않는다.

면접 평가의 핵심 정보는 `feedback`이다. `score`는 답변 품질을 빠르게 가늠하기 위한 보조 지표로 제공한다.

`score`는 1~100 범위의 정수다. 만점은 항상 100이므로 별도 max 필드를 저장하거나 응답하지 않으며, 항목별 점수도 제공하지 않는다.
