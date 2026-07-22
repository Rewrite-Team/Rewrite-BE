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
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "백엔드 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자"
  },
  "interviewSession": null
}
```

면접 세션이 있는 경우:

```json
{
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "백엔드 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자"
  },
  "interviewSession": {
    "id": "is_01HZ...",
    "initialSourceReviewVersionId": "rv_01HZ...",
    "status": "ACTIVE",
    "jobId": null,
    "createdAt": "2026-06-20T16:00:00"
  }
}
```

면접 세션이 없는 것은 정상 상태이므로 `200 OK`와 `interviewSession: null`을 반환한다.
`coverLetter`는 면접 세션 존재 여부와 관계없이 항상 반환하며, AI 면접 화면의 자기소개서 제목, 회사명, 직무 표시에 사용한다. `id`, `title`, `companyName`, `positionTitle`은 모두 non-null이다.
자기소개서가 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 경우에는 `NOT_FOUND`를 반환한다.
재첨삭 후에도 기존 면접 세션을 유지하므로 자기소개서의 현재 상태와 관계없이 세션을 조회한다.
`interviewSession.jobId`는 아직 화면에서 처리해야 하는 질문 생성 Job이 있을 때 반환한다. `QUESTION_GENERATING`에서는 실행 중이거나 실패한 초기 질문 생성 Job ID를 반환한다. `ACTIVE`에서는 추가 질문 생성 Job이 `PENDING`, `PROCESSING`, `FAILED`이면 해당 Job ID를 반환하고, 처리할 질문 생성 Job이 없으면 `null`이다. 면접 답변 피드백 Job은 질문별 thread에 속하므로 API-025에 포함하지 않는다.

새로고침 후 `jobId`가 있으면 API-016에 연결해 `job.state`로 진행 또는 실패 상태를 복구한다. SSE 연결에 실패하면 해당 `jobId`로 API-015를 polling한다. 완료되면 초기 질문은 API-025와 API-026, 추가 질문은 API-026을 다시 조회하고, 실패하면 각각 API-022 또는 API-027 수동 재시도를 제공한다. API-025 polling은 현재 세션과 복구할 `jobId`를 다시 찾는 용도로 사용한다.

### 모의면접 시작

```http
POST /cover-letters/{coverLetterId}/interviews
```

Request body는 없다. 서버는 호출 시점의 최신 성공 첨삭 버전을 기준으로 면접 질문을 생성한다.

면접 질문은 총 5개 생성한다. 모든 질문은 자기소개서 최종 작성본에 드러난 경험, 역할, 행동, 성과, 문제 해결 과정과 의사결정을 기반으로 하며 질문 종류를 구분하지 않는다.

Response:

```json
{
  "interviewSessionId": "is_01HZ...",
  "jobId": "job_01HZ...",
  "status": "QUESTION_GENERATING"
}
```

`LlmJob(type=INTERVIEW_INITIAL_QUESTION_GENERATION)`은 `requestRef.type=REVIEW_VERSION`, `requestRef.id=생성 기준 첨삭 버전 id`를 저장하고 after-commit 비동기 worker에서 실행한다.
생성에 성공하면 질문 5개와 질문별 thread 5개를 저장하고 `InterviewSession.status`를 `ACTIVE`로 전환한다.
완료 Job의 `resultRef`는 해당 면접 세션을 가리킨다.
클라이언트는 응답의 `jobId`로 API-016 공통 Job SSE에 연결한다. `job.state.status=COMPLETED`이면 API-025와 API-026을 다시 조회한다.

질문 생성 실패 시 `InterviewSession.status`는 `FAILED`로 저장된다. 실패한 세션은 현재 면접 세션 조회 API에서 그대로 반환한다.

이미 `ACTIVE` 또는 `QUESTION_GENERATING` 면접 세션이 있는 경우 이 API에서는 초기 질문 생성 Job을 새로 만들지 않고 기존 세션을 반환한다. `QUESTION_GENERATING`이면 기존 Job ID를, `ACTIVE`이면 `jobId=null`을 반환한다. 기존 세션에 질문을 추가하려면 `POST /interviews/{interviewSessionId}/questions`를 사용한다.

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
최신 성공 ReviewVersion이 존재해야 한다.
같은 자기소개서에 PENDING 또는 PROCESSING 상태의 LLM Job이 없어야 한다.
```

### 면접 질문 목록 조회

```http
GET /interviews/{interviewSessionId}/questions?size=10
GET /interviews/{interviewSessionId}/questions?size=10&cursor=Mg
```

Query:

| Field | Type | Required | Nullable | Description |
|---|---|---:|---:|---|
| `cursor` | string | 아니요 | 예 | 다음 질문 묶음을 조회할 때 직전 응답의 `nextCursor`를 그대로 전달한다. 최초 요청에서는 생략한다. |
| `size` | integer | 아니요 | 아니요 | 조회할 질문 수. 기본값 `10`, 최솟값 `1`, 최댓값 `20`이다. |

Response:

```json
{
  "items": [
    {
      "id": "iq_01HV...",
      "order": 5,
      "question": "문제 해결 과정에서 가장 중요하게 내린 의사결정을 설명해 주세요.",
      "threadId": "it_01HV..."
    },
    {
      "id": "iq_01HW...",
      "order": 4,
      "question": "프로젝트 성과를 만들기 위해 본인이 직접 수행한 행동을 설명해 주세요.",
      "threadId": "it_01HW..."
    }
  ],
  "nextCursor": "NA"
}
```

질문은 최신 질문이 먼저 보이도록 `order` 내림차순으로 반환한다. 모든 질문은 생성 시 thread가 함께 저장되므로 `threadId`는 필수값이다. 질문은 존재하지만 thread가 없으면 데이터 불변식 위반으로 처리한다.

최초 조회에서는 `cursor`를 생략한다. 다음 질문이 있으면 `nextCursor`에 불투명 문자열을 반환하며, 프론트엔드는 이 값을 다음 요청의 `cursor`로 그대로 전달해 응답 `items`를 기존 목록 뒤에 추가한다. 더 조회할 질문이 없으면 `nextCursor`는 `null`이다. 새 질문 생성 완료 후에는 cursor 없는 첫 요청을 다시 호출하고 `id` 기준으로 새 질문을 목록 앞에 병합한다.

면접 세션 ID는 path와 중복되므로 응답하지 않는다. 질문의 생성 기준 첨삭 버전은 화면에서 사용하지 않는 내부 추적 정보이므로 `sourceReviewVersionId`도 응답하지 않는다.

질문 생성 중이거나 생성 결과가 없는 세션은 정상 상태이므로 `200 OK`, 빈 `items`, `nextCursor: null`을 반환한다.
`cursor` 형식이 올바르지 않거나 `size`가 허용 범위를 벗어나면 `VALIDATION_ERROR`를 반환한다. 면접 세션이 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 세션이면 `NOT_FOUND`를 반환한다.

### 면접 질문 추가 생성

화면 우측 예상 질문 리스트 상단의 `새로운 질문 추가하기` 버튼을 눌렀을 때 사용한다.

```http
POST /interviews/{interviewSessionId}/questions
```

Request body는 없다. 서버는 항상 해당 자기소개서의 최신 첨삭 버전인 `CoverLetter.latestReviewedVersionId`를 기준으로 질문을 생성한다.

요청 시점의 최신 첨삭 버전은 Job의 `requestRef.type=REVIEW_VERSION`, `requestRef.id=latestReviewedVersionId`로 확정한다. 추가 질문 생성 Job은 `type=INTERVIEW_ADDITIONAL_QUESTION_GENERATION`, `progress.total=1`로 생성한다.

추가 질문은 자기소개서 최종 작성본을 기반으로 총 1개 생성한다. 질문 종류는 구분하지 않는다.

OpenAI 요청에는 기존 질문 목록을 함께 전달한다. 생성 결과가 기존 질문과 trim 후 동일하거나 정확히 1개가 아니면 출력 검증 실패로 처리하며 새 질문을 저장하지 않는다.

생성된 질문은 기존 질문 목록 뒤에 이어서 `order`를 부여한다.

```text
기존 질문 order: 1~5
추가 질문 order: 6
```

Response:

```json
{
  "jobId": "job_01HZ..."
}
```

응답은 `200 OK`다. Job은 transaction commit 이후 기존 면접 질문 생성 worker에서 비동기로 실행한다.

질문과 thread는 같은 transaction에서 저장한다. 클라이언트는 응답의 `jobId`로 API-016에 연결한다. 추가 질문 생성은 중간 도메인 이벤트를 제공하지 않으며 Job이 완료되면 `job.state`의 `progress.current=1`, `resultRef.type=INTERVIEW_QUESTION`, `resultRef.id=생성된 질문 id`가 된다. `job.state.status=COMPLETED`이면 API-026을 다시 조회해 추가된 질문과 `threadId`를 확인한다.

질문 추가 생성 중에도 기존 면접 세션과 기존 질문별 대화방은 유지된다. 추가 질문 생성 Job이 실패해도 `InterviewSession.status`는 `ACTIVE`를 유지하고, 새 질문과 thread는 저장하지 않는다. 실패 상태는 API-016의 `job.state.status=FAILED` 또는 API-025가 반환한 Job ID로 다시 연결한 `job.state`에서 복구한다.

동일한 추가 질문 생성 Job이 이미 `PENDING` 또는 `PROCESSING`이면 새 Job을 만들지 않고 기존 Job의 같은 성공 응답을 반환한다. 다른 종류의 LLM Job이 진행 중이면 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

Validation:

```text
interviewSession.status는 ACTIVE여야 한다.
연결된 자기소개서에 최신 성공 ReviewVersion이 존재해야 한다.
동일한 추가 질문 생성 Job 외에 같은 자기소개서의 PENDING 또는 PROCESSING LLM Job이 없어야 한다.
```

면접 세션이 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 세션이면 `NOT_FOUND`를 반환한다. 세션 또는 자기소개서 상태가 생성 조건과 맞지 않으면 `CONFLICT`, 진행 중 Job이 있으면 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

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
  "jobId": null,
  "items": [
    {
      "id": "im_01HY...",
      "role": "USER",
      "content": "저는 프로젝트에서 API 설계를 담당했습니다.",
      "score": null,
      "createdAt": "2026-06-20T16:06:00"
    },
    {
      "id": "im_01HZ2...",
      "role": "ASSISTANT",
      "content": "답변에서 API 설계 경험은 드러났지만 성과와 의사결정 근거가 부족합니다. 이어서, 그 API 설계에서 가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
      "score": 78,
      "createdAt": "2026-06-20T16:06:00"
    }
  ]
}
```

최초 면접 질문은 API-026의 `question`으로 표시한다. `items`에는 사용자가 답변을 전송한 시점부터 `USER`, `ASSISTANT` 메시지가 순서대로 저장된다.

메시지는 `createdAt`, `id` 오름차순으로 반환한다. 메시지가 없는 새 thread는 정상 상태이므로 `200 OK`와 빈 `items`를 반환한다. thread가 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 thread이면 `NOT_FOUND`를 반환한다.

thread ID는 path와 중복되므로 응답하지 않는다. `role`은 `USER | ASSISTANT` 중 하나다. `score`는 USER 메시지에서 `null`, ASSISTANT 메시지에서 1~100 정수다. ASSISTANT `content`는 핵심 피드백과 다음 꼬리질문을 자연스럽게 연결한 전체 표시 문장이다. 구조화된 피드백과 별도 `followUpQuestion`은 다음 LLM 문맥을 위해 서버 내부에 유지하되 공개 응답에는 포함하지 않는다.

최상위 `jobId`는 이 thread에서 아직 화면이 처리해야 하는 면접 피드백 Job이 `PENDING`, `PROCESSING`, `FAILED`이면 해당 ID를 반환하고, 완료됐거나 처리할 Job이 없으면 `null`이다. 새로고침 후 `jobId`가 있으면 API-016에 연결해 진행 또는 실패 상태를 복구한다. 메시지 수가 작은 현재 범위에서는 페이지네이션을 제공하지 않는다.

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
  "jobId": "job_01HZ..."
}
```

`userMessageId`는 저장된 USER 메시지 ID이고, `jobId`는 API-016에 연결할 피드백 Job ID다. 생성 직후 항상 같은 값인 Job 상태는 응답하지 않으며, 실제 상태는 API-016의 `job.state`로 확인한다.

요청이 성공하면 trim된 USER 메시지와 `INTERVIEW_MESSAGE_FEEDBACK` Job을 같은 transaction에서 저장한다. 생성 직후 Job 상태는 `PENDING`이며, Job이 실제 실행을 시작하면 `PROCESSING`으로 전환된다.

thread가 존재하지 않거나 현재 사용자 소유가 아니거나 soft delete된 자기소개서의 thread이면 `NOT_FOUND`를 반환한다. 같은 자기소개서에 이미 `PENDING` 또는 `PROCESSING` Job이 있으면 USER 메시지를 저장하지 않고 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

피드백 Job은 생성된 USER 메시지 ID를 입력 참조로 저장한다. 비동기 worker는 이 참조를 기준으로 처리할 답변을 확정하고, 원본 면접 질문과 해당 USER 메시지까지의 대화 이력을 순서대로 OpenAI client에 전달한다.

LLM 작업이 완료되면 assistant 메시지와 Job 완료 상태를 같은 transaction에서 저장한다. 내부 assistant 메시지는 항상 `feedback`, `score`, `followUpQuestion`을 포함하며, 완료된 Job의 `resultRef`는 생성된 `INTERVIEW_MESSAGE`를 가리킨다. API-029 공개 응답에서는 내부 구조를 직접 노출하지 않고 `content`와 `score`만 반환한다.

provider 호출 또는 출력 검증에 실패하면 Job은 `FAILED`로 종료하고 assistant 메시지는 저장하지 않는다. 이미 완료되거나 실패한 Job event가 다시 전달되어도 메시지를 중복 생성하지 않는다.

면접 답변 피드백 Job(`INTERVIEW_MESSAGE_FEEDBACK`)은 진행 중 assistant 메시지를 미리 생성하지 않는다. 서버는 OpenAI 전체 응답을 받은 뒤 구조와 필수 값을 검증하고, 검증된 `content`를 화면 표시용 문자열 조각으로 나눠 API-016 `interview.feedback.delta`로 점진 전송한다. 이는 실제 OpenAI 토큰 스트리밍이 아니라 검증이 끝난 전체 문장을 순차적으로 보여주는 방식이다. 프론트엔드는 `contentDelta`를 `sequence` 순서대로 이어 붙인다.

서버는 `PROCESSING` 동안 이미 전송한 delta를 Job별 메모리에 보관한다. 사용자가 생성 중 화면을 이탈했다가 다시 진입하면 API-029의 `jobId`로 API-016에 다시 연결하고, 서버는 이전 delta를 `sequence=1`부터 replay한 뒤 새 delta를 이어서 전송한다. 프론트엔드는 이미 반영한 sequence를 무시한다. 서버 재시작 등으로 replay 버퍼가 없으면 불완전한 뒷부분을 표시하지 않고 완료 전까지 진행 상태만 복구한다. SSE 연결에 실패하면 해당 `jobId`로 API-015를 polling한다. `job.state.status=COMPLETED`이면 API-029를 다시 조회해 임시 문장을 저장된 assistant 메시지의 `content`와 `score`로 교체한다. `job.state.status=FAILED`이면 임시 문장을 제거하고 실패 상태를 표시한다.

사용자 답변 1개에 대해 assistant 메시지 1개를 저장한다. 꼬리질문은 내부 `followUpQuestion` 필드에 포함하며 별도 assistant 메시지로 분리하지 않는다. 공개 응답에서는 피드백과 꼬리질문을 합친 표시 문장을 `content`로 제공한다.

면접 평가 생성의 핵심 정보는 내부 구조화된 `feedback`이다. 프론트엔드에는 이를 표시 문장으로 만든 `content`와 답변 품질을 빠르게 가늠하기 위한 보조 지표인 `score`만 제공한다.

`score`는 1~100 범위의 정수다. 만점은 항상 100이므로 별도 max 필드를 저장하거나 응답하지 않으며, 항목별 점수도 제공하지 않는다.

### API-022~023, API-025~029 오류 처리

COMMON의 인증·CSRF·서버 오류 처리를 기본으로 적용한다. 질문·피드백 생성 실패는 시작 요청의 HTTP 오류가 아니라 API-016 `job.state.status=FAILED`, API-025 또는 API-029의 조건부 `jobId`와 상태로 처리한다.

| API | HTTP 상태 | 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---|---:|---|---|---|
| API-022 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 대상 없음 안내 후 목록으로 이동한다. |
| API-022 | 409 | `CONFLICT` | 성공한 첨삭 버전이 없음 | API-012를 재조회해 현재 상태 화면으로 전환한다. |
| API-022 | 409 | `LLM_JOB_ALREADY_RUNNING` | 초기 질문 생성 외 다른 AI Job이 진행 중 | 다른 AI 작업이 진행 중임을 안내하고 자동 재시도하지 않는다. |
| API-023 | 400 | `VALIDATION_ERROR` | `content` 누락, trim 후 빈 값 또는 2000자 초과 | `details[field=content].reason`을 답변 입력란에 표시한다. |
| API-023 | 404 | `NOT_FOUND` | thread 없음·비소유 또는 삭제된 자기소개서에 연결됨 | 대화 화면을 종료하고 API-025를 재조회한다. |
| API-023 | 409 | `LLM_JOB_ALREADY_RUNNING` | 같은 자기소개서에 다른 AI Job이 진행 중 | USER 메시지가 저장되지 않았음을 유지하고 기존 작업 완료 후 사용자가 다시 전송하도록 안내한다. |
| API-025 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 대상 없음 안내 후 목록으로 이동한다. 세션 없음과 `FAILED`는 `200` 정상 상태다. |
| API-026 | 400 | `VALIDATION_ERROR` | `cursor` 형식 오류 또는 `size`가 1~20 범위를 벗어남 | 목록 추가를 중단한다. 최초 조회는 cursor를 생략하고, 이후에는 서버가 반환한 `nextCursor`만 사용한다. |
| API-026 | 404 | `NOT_FOUND` | 세션 없음·비소유 또는 삭제된 자기소개서의 세션 | 면접 화면을 종료하고 API-025를 재조회한다. `items=[]`는 데이터 없음만 의미하며 진행·실패는 API-025와 API-015로 판단한다. |
| API-027 | 404 | `NOT_FOUND` | 세션 없음·비소유 또는 삭제된 자기소개서의 세션 | API-025를 재조회한다. |
| API-027 | 409 | `CONFLICT` | 세션이 `ACTIVE`가 아니거나 자기소개서가 `REVIEWED`가 아님 | API-025를 재조회하고 가능한 동작만 활성화한다. |
| API-027 | 409 | `LLM_JOB_ALREADY_RUNNING` | 추가 질문 생성 외 다른 AI Job이 진행 중 | 다른 AI 작업이 진행 중임을 안내하고 자동 재시도하지 않는다. |
| API-028 | - | `API별 오류 없음` | Deprecated되어 호출하지 않는 API | API-026의 `threadId`를 사용한다. `DEPRECATED`는 실제 HTTP 오류 코드가 아니다. |
| API-029 | 404 | `NOT_FOUND` | thread 없음·비소유 또는 삭제된 자기소개서에 연결됨 | 대화 화면을 종료하고 API-025를 재조회한다. `jobId`가 있으면 API-016에 연결하고, 연결 실패 시 API-015를 polling한다. |

초기 질문 생성 실패는 API-025의 `FAILED` 상태에서 API-022 수동 재시도를 제공하고, 추가 질문 생성 실패는 기존 질문과 대화를 유지한 채 API-027 수동 재시도를 제공한다. 답변 피드백 실패에서는 임시 delta를 제거하고 저장된 USER 메시지는 유지하며, 존재하지 않는 자동 재처리 API를 가정하지 않는다.
