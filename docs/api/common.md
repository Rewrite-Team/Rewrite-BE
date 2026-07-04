# Common API Rules

## 설계 원칙

### LLM 작업 처리

첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백은 비동기 Job으로 처리한다.

LLM 작업 요청 API는 즉시 `jobId`를 반환한다. 클라이언트는 Job 상태 조회 API 또는 스트리밍 API를 통해 진행 상태와 결과를 확인한다.

### 버전 관리

자기소개서 버전 히스토리는 AI 첨삭 또는 재첨삭 결과를 기준으로 생성한다.

`ReviewVersion`은 성공한 첨삭 결과에 대해서만 생성한다. 첨삭 또는 재첨삭 LLM Job이 진행 중이거나 실패한 상태는 `ReviewVersion`으로 만들지 않고 `LlmJob`과 `CoverLetter.status`로 표현한다.

사용자가 최종 작성본을 저장하는 행위만으로는 새 버전을 생성하지 않는다.

### 임시저장

임시저장된 자기소개서도 내 자기소개서 목록에 노출한다.

자기소개서 상태는 다음 값을 사용한다.

```text
DRAFT
REVIEWING
REVIEWED
REVIEW_FAILED
```

첨삭 실패 시 상태 전이는 최초 첨삭과 재첨삭을 구분한다.

```text
최초 첨삭 실패: REVIEWING -> REVIEW_FAILED
재첨삭 실패: REVIEWED 유지, 실패한 LLM Job만 FAILED
```

### 문항별 첨삭 결과

AI 첨삭 결과는 질문별로 독립 저장한다.

각 첨삭 버전은 여러 개의 질문별 첨삭 결과를 가진다.


## 공통 규칙

### Base URL

API 경로에는 별도의 `/api` prefix를 붙이지 않는다.

```text
/
```

### 인증

인증은 HttpOnly Cookie 기반으로 처리한다.

카카오 로그인 성공 후 백엔드는 Rewrite 서비스용 access token과 refresh token을 발급하고 `Set-Cookie`로 전달한다.

```http
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1800
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=1209600
```

`access_token`은 30분, `refresh_token`은 14일 동안 유효하다. Refresh token은 rotation을 사용한다. Refresh 성공 시 새 access token과 새 refresh token을 모두 발급하고, 기존 refresh token은 폐기한다.

프론트엔드는 인증이 필요한 요청에 cookie가 포함되도록 요청한다.

```text
credentials: include
```

JavaScript에서는 token 값을 직접 읽지 않는다.

인증이 필요한 모든 리소스 접근은 현재 로그인 사용자의 소유자인지 검증한다.

소유자 검증 대상:

```text
coverLetterId
reviewVersionId
keywordAnalysisId
interviewSessionId
interviewQuestionId
threadId
messageId
jobId
```

중첩 리소스는 상위 리소스와의 관계도 함께 검증한다. 예를 들어 `reviewVersionId`는 해당 `coverLetterId`에 속해야 하고, `threadId`는 해당 사용자의 면접 세션에 속해야 한다.

리소스가 존재하지 않거나 현재 사용자의 소유가 아니면 `NOT_FOUND`를 반환한다. 다른 사용자의 리소스 존재 여부를 노출하지 않기 위해 소유자 불일치에도 `FORBIDDEN`이 아니라 `NOT_FOUND`를 사용한다.

`CoverLetter.deletedAt`이 있는 삭제된 자기소개서와 그 하위 리소스도 사용자-facing API에서는 `NOT_FOUND`를 반환한다.

상태 변경 요청에는 CSRF 토큰을 포함해야 한다.

```http
X-CSRF-Token: csrf-token-value
```

CSRF 토큰이 필요한 HTTP method:

```text
POST
PUT
PATCH
DELETE
```

### Content Type

```http
Content-Type: application/json
```

스트리밍 API는 SSE(Server-Sent Events)를 사용한다.

```http
Accept: text/event-stream
```

응답:

```http
Content-Type: text/event-stream
```

### 날짜 형식

서버 내부의 모든 시간 값은 `Instant`로 저장하고 처리한다.

API 응답 DTO로 변환할 때는 `ZoneId.of("Asia/Seoul")` 기준으로 변환한 `LocalDateTime`을 사용한다. 따라서 날짜/시간 응답 값은 timezone offset이 없는 ISO 8601 local date-time 문자열이다.

```json
"createdAt": "2026-06-20T14:00:00"
```

화면 표기 형식은 프론트엔드에서 변환한다.

- 목록 등록일: `2026.06.20`
- 버전 기록 시각: `2026.05.20 14:00`

### 에러 응답

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값이 올바르지 않습니다.",
    "details": [
      {
        "field": "title",
        "reason": "자기소개서 제목은 최대 50자까지 입력할 수 있습니다."
      }
    ]
  }
}
```

대표 에러 코드:

```text
UNAUTHORIZED
NOT_FOUND
VALIDATION_ERROR
CONFLICT
LLM_JOB_NOT_READY
LLM_JOB_FAILED
LLM_JOB_ALREADY_RUNNING
COVER_LETTER_NOT_DRAFT
REVIEW_VERSION_NOT_LATEST
INTERNAL_ERROR
```

LLM Job의 `error.code`는 프론트엔드 사용자 메시지 매핑 기준으로 사용한다. 프론트엔드는 provider 원문이나 `error.message`를 그대로 사용자에게 노출하지 않고, `error.code`별 사용자 친화 문구를 표시한다.

대표 LLM Job error code:

```text
LLM_PROVIDER_TIMEOUT
LLM_PROVIDER_UNAVAILABLE
LLM_PROVIDER_RATE_LIMITED
LLM_CONTEXT_LENGTH_EXCEEDED
LLM_CONTENT_FILTERED
LLM_OUTPUT_VALIDATION_FAILED
LLM_PROVIDER_ERROR
```

권장 사용자 메시지:

```text
LLM_PROVIDER_TIMEOUT: AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.
LLM_PROVIDER_UNAVAILABLE: AI 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.
LLM_PROVIDER_RATE_LIMITED: 요청이 일시적으로 많아 처리하지 못했습니다. 잠시 후 다시 시도해주세요.
LLM_CONTEXT_LENGTH_EXCEEDED: 입력 내용이 너무 길어 AI가 처리하지 못했습니다.
LLM_CONTENT_FILTERED: 입력 내용 또는 생성 결과가 처리 정책에 맞지 않아 첨삭에 실패했습니다.
LLM_OUTPUT_VALIDATION_FAILED: AI 응답 형식이 올바르지 않아 처리하지 못했습니다. 잠시 후 다시 시도해주세요.
LLM_PROVIDER_ERROR: AI 첨삭에 실패했습니다. 잠시 후 다시 시도해주세요.
```

프론트엔드가 알 수 없는 LLM Job `error.code`를 받은 경우에도 `error.message`를 그대로 표시하지 않는다. 기본 fallback 메시지로 `AI 첨삭에 실패했습니다. 잠시 후 다시 시도해주세요.`를 표시한다.

### 글자 수 산정

문자열 길이 제한과 `*Length` 응답 필드는 Unicode code point 수를 기준으로 계산한다.

적용 대상:

```text
originalAnswerLength
rewrittenAnswerLength
finalAnswerLength
originalAnswer
rewrittenAnswer
finalAnswer
```

프론트엔드의 실시간 글자 수 표시도 같은 기준을 사용해야 한다.


## 도메인 모델

### User

```json
{
  "id": "user_01HZ...",
  "nickname": "홍길동",
  "profileImageUrl": "https://...",
  "provider": "KAKAO",
  "createdAt": "2026-06-20T14:00:00"
}
```

### CoverLetter

```json
{
  "id": "cl_01HZ...",
  "title": "2026 상반기 백엔드 개발자 자기소개서",
  "companyName": "Rewrite Corp",
  "positionTitle": "백엔드 개발자",
  "jobPostingUrl": "https://...",
  "preferences": "Java/Spring 경험 우대...",
  "status": "REVIEWED",
  "createdAt": "2026-06-20T14:00:00",
  "updatedAt": "2026-06-20T14:30:00",
  "submittedAt": "2026-06-20T14:10:00",
  "deletedAt": null,
  "latestReviewVersionId": "rv_01HZ..."
}
```

자기소개서 삭제는 soft delete로 처리한다. `deletedAt`이 있는 자기소개서는 목록과 상세 조회에서 기본적으로 제외한다.

삭제된 자기소개서와 그 하위 리소스는 사용자-facing API에서 모두 `NOT_FOUND`를 반환한다.

적용 대상:

```text
CoverLetter
CoverLetterQuestion
ReviewVersion
ReviewVersionQuestionResult
KeywordAnalysis
InterviewSession
InterviewQuestion
InterviewThread
InterviewMessage
LlmJob
```

MVP에서는 삭제된 자기소개서 목록 조회 API와 사용자-facing 복구 API를 제공하지 않는다.

### CoverLetterQuestion

```json
{
  "id": "clq_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "order": 1,
  "question": "지원 동기를 작성해주세요.",
  "maxAnswerLength": 1000,
  "originalAnswer": "제가 지원한 이유는..."
}
```

제약:

```text
question: trim 후 Unicode code point 기준 1~300자
maxAnswerLength: 100~5000
originalAnswer: trim 후 Unicode code point 기준 1~5000자
```

서버는 `question`과 `originalAnswer`의 앞뒤 공백을 제거한 뒤 길이를 검증하고, 공백이 제거된 값을 저장한다. trim 후 빈 문자열이면 `VALIDATION_ERROR`를 반환한다.

### ReviewVersion

`version`은 화면 표시용 revision label이다. 최초 첨삭 완료 시 `v0.1`로 생성하고, 재첨삭 완료마다 `v0.2`, `v0.3`처럼 patch 숫자를 1씩 증가시킨다. semantic versioning 의미는 없다.

`ReviewVersion`은 성공한 첨삭 결과에 대해서만 생성되는 완료 스냅샷이다. LLM Job 진행 중이거나 실패한 첨삭 시도는 `ReviewVersion`으로 저장하지 않고 `LlmJob`과 `CoverLetter.status`로 표현한다.

최신 첨삭 버전의 저장 기준은 `CoverLetter.latestReviewVersionId`다. `ReviewVersion`은 `isLatest`를 저장 필드로 갖지 않는다.

성공한 첨삭 결과만 `ReviewVersion`으로 저장하므로 `ReviewVersion`은 별도의 `status` 필드를 갖지 않는다.

`createdAt`은 첨삭 결과 스냅샷이 생성된 시각이다. 첨삭 Job의 시작/완료 시각은 `LlmJob.createdAt`, `LlmJob.completedAt`으로 확인한다.

```json
{
  "id": "rv_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "version": "v0.1",
  "requestInstruction": "직무 적합성을 더 강조해주세요.",
  "createdAt": "2026-06-20T14:21:10"
}
```

### ReviewVersionQuestionResult

```json
{
  "questionResultId": "rvqr_01HZ...",
  "questionId": "clq_01HZ...",
  "order": 1,
  "question": "지원 동기를 작성해주세요.",
  "maxAnswerLength": 1000,
  "originalAnswer": "제가 지원한 이유는...",
  "originalAnswerLength": 530,
  "aiReport": "STAR 관점에서 상황과 과제는 드러나지만 행동과 결과가 약합니다. 성과 수치를 추가하면 더 설득력 있습니다. 우대사항 중 Spring 경험과의 연결이 부족하므로 백엔드, API, 장애 대응 키워드를 보강하는 것이 좋습니다.",
  "rewrittenAnswer": "저는 백엔드 개발자로서...",
  "rewrittenAnswerLength": 820,
  "finalAnswer": "저는 백엔드 개발자로서...",
  "finalAnswerLength": 810
}
```

`questionResultId`는 서버가 발급하는 opaque identifier이며, 하나의 `ReviewVersion` 안에서 유일하다. 클라이언트는 최종 작성본 일괄 저장 요청의 `answers[].questionResultId`에 이 값을 그대로 사용한다. `questionId`는 원본 자기소개서 문항 ID이고, `questionResultId`는 특정 첨삭 버전의 문항별 결과 ID다.

`aiReport`는 프론트엔드에 그대로 렌더링할 단일 문자열이다. STAR, 구체성, 우대사항 적합성, 직무 키워드, 맞춤법, 문장 자연스러움, 중복 표현, 글자 수 준수 여부는 API 필드가 아니라 LLM 프롬프트의 평가 기준으로 관리한다.

Diff는 API가 제공하지 않는다. 프론트엔드는 `originalAnswer`와 `rewrittenAnswer`를 비교해 화면에서 diff를 계산한다.

제약:

```text
finalAnswer: trim 후 Unicode code point 기준 1~5000자
```

### LlmJob

```json
{
  "id": "job_01HZ...",
  "type": "COVER_LETTER_REVIEW",
  "status": "PROCESSING",
  "targetType": "COVER_LETTER",
  "targetId": "cl_01HZ...",
  "progress": {
    "current": 1,
    "total": 3,
    "message": "1번 문항을 첨삭하고 있습니다."
  },
  "attempt": 1,
  "maxAttempts": 2,
  "partialResult": null,
  "resultRef": null,
  "error": null,
  "createdAt": "2026-06-20T14:10:00",
  "completedAt": null
}
```

Job type:

```text
COVER_LETTER_REVIEW
COVER_LETTER_RE_REVIEW
KEYWORD_ANALYSIS
INTERVIEW_QUESTION_GENERATION
INTERVIEW_MESSAGE_FEEDBACK
```

Job status:

```text
PENDING
PROCESSING
COMPLETED
FAILED
CANCELED
```

LLM Job은 실패 시 서버에서 1회 자동 재시도한다. 최초 시도와 재시도를 포함해 `maxAttempts`는 2이다. 두 번 모두 실패하면 `FAILED` 상태가 된다.

LLM 출력 파싱 실패, 필수 필드 누락, 타입 불일치, 범위 위반처럼 서버가 기대한 결과 구조로 검증할 수 없는 응답도 LLM Job 실패로 처리한다. 이 경우 가능한 필드만 부분 저장하거나 서버가 임의 기본값으로 보정하지 않는다. 자동 재시도 1회 후에도 구조 검증에 실패하면 `FAILED`와 `LLM_OUTPUT_VALIDATION_FAILED`로 저장한다.

같은 자기소개서에 대해 진행 중인 LLM Job은 동시에 하나만 허용한다. `PENDING` 또는 `PROCESSING` 상태의 Job이 있으면 새 LLM Job 시작 요청은 `CONFLICT`와 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

첨삭 Job(`COVER_LETTER_REVIEW`, `COVER_LETTER_RE_REVIEW`)은 진행 중 생성된 텍스트를 문항별/필드별 partial result로 누적 저장한다. 이 값은 화면 재진입 시 지금까지 생성된 텍스트를 먼저 복구하고, 이후 SSE delta를 이어붙이기 위한 임시 결과다. 첨삭 Job이 아닌 LLM Job의 `partialResult`는 `null`이다.

MVP에서는 partial result를 서버 메모리에 저장한다. 서버 재시작, 프로세스 종료, 스케일아웃 환경에서는 진행 중 partial result가 유실될 수 있다. 이후 Redis 같은 외부 cache 저장소로 이전할 수 있도록 partial result 저장소는 교체 가능한 내부 인터페이스로 분리한다.

첨삭 Job이 완료되면 LLM 호출의 최종 accumulated output을 기준으로 `ReviewVersionQuestionResult`를 확정하고 `ReviewVersion`을 생성한다. partial result는 화면 복구용 캐시이므로, partial result 유실이 최종 첨삭 결과를 훼손하면 안 된다.

### KeywordAnalysis

키워드 분석 결과는 자기소개서별 최신 결과만 유지한다. `sourceReviewVersionId`는 최신 결과가 어떤 첨삭 버전을 기준으로 생성되었는지 기록한다.

`keywords`는 중요도 기준 상위 20개를 제공한다. `importance`는 1~100 범위의 정수다.

재첨삭 완료만으로 기존 키워드 분석 결과를 삭제하지 않는다. 키워드 분석 결과가 있는 상태에서 사용자가 `AI 키워드 재분석`을 실행하면 같은 `KeywordAnalysis` 리소스를 `PROCESSING`으로 전환하고, 가장 최근 `ReviewVersion`을 기준으로 다시 분석한다. 성공 시 기존 키워드 결과와 `sourceReviewVersionId`를 최신 분석 결과로 덮어쓴다.

상태:

```text
PROCESSING
COMPLETED
FAILED
```

```json
{
  "id": "ka_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "sourceReviewVersionId": "rv_01HZ...",
  "status": "COMPLETED",
  "keywords": [
    {
      "keyword": "백엔드",
      "importance": 95
    },
    {
      "keyword": "Spring",
      "importance": 88
    }
  ],
  "createdAt": "2026-06-20T15:00:00",
  "completedAt": "2026-06-20T15:00:30"
}
```

### InterviewSession

면접 세션은 최초 질문 세트를 생성할 때 기준이 된 첨삭 버전을 `initialSourceReviewVersionId`로 기록한다. 이 값은 세션의 시작 기준을 나타내는 메타데이터이며, 세션 안의 모든 질문이 같은 첨삭 버전을 기준으로 생성되었다는 뜻은 아니다.

사용자가 `새로운 질문 추가하기`를 실행하면 기존 면접 세션은 유지하고, 가장 최근 `ReviewVersion`을 기준으로 면접 질문 5개를 추가 생성한다. 질문마다 생성 기준 버전이 다를 수 있으므로 `InterviewQuestion.sourceReviewVersionId`에 각 질문의 기준 첨삭 버전을 기록한다.

상태:

```text
QUESTION_GENERATING
ACTIVE
FAILED
```

```json
{
  "id": "is_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "initialSourceReviewVersionId": "rv_01HZ...",
  "status": "ACTIVE",
  "createdAt": "2026-06-20T16:00:00"
}
```

### InterviewQuestion

AI 면접 질문은 세션 시작 시 5개 생성하고, 사용자가 `새로운 질문 추가하기`를 실행할 때마다 5개씩 추가 생성한다. 각 생성 단위는 자기소개서 기반 질문 3개와 기술 질문 2개로 구성한다.

```json
{
  "id": "iq_01HZ...",
  "interviewSessionId": "is_01HZ...",
  "sourceReviewVersionId": "rv_01HZ...",
  "order": 1,
  "type": "COVER_LETTER_BASED",
  "question": "프로젝트에서 맡은 역할을 더 구체적으로 설명해 주세요."
}
```

질문 type 후보:

```text
COVER_LETTER_BASED
TECHNICAL
```

꼬리질문은 `InterviewQuestion` type이 아니라 `InterviewMessage.followUpQuestion`으로 저장한다.

### InterviewThread

```json
{
  "id": "it_01HZ...",
  "interviewSessionId": "is_01HZ...",
  "interviewQuestionId": "iq_01HZ...",
  "status": "ACTIVE",
  "createdAt": "2026-06-20T16:05:00"
}
```

### InterviewMessage

사용자 답변 1개에 대해 assistant 메시지 1개를 생성한다. assistant 메시지는 피드백, 점수, 꼬리질문을 함께 포함하며, 꼬리질문을 별도 메시지로 분리 저장하지 않는다.

```json
{
  "id": "im_01HZ...",
  "threadId": "it_01HZ...",
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
  "score": {
    "overall": 78,
    "max": 100
  },
  "followUpQuestion": "그 API 설계에서 가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
  "createdAt": "2026-06-20T16:05:00"
}
```

role:

```text
USER
ASSISTANT
SYSTEM
```
