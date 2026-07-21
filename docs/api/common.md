# Common API Rules

## 설계 원칙

### LLM 작업 처리

첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백은 비동기 Job으로 처리한다.

LLM 작업 요청 API는 즉시 `jobId`를 반환한다. API-016을 지원하는 화면은 SSE를 우선 사용하고, API-015 Job 상태 조회 또는 도메인 조회 polling은 연결 복구와 fallback에 사용한다. Job 완료 후에는 해당 도메인 결과 조회 API를 다시 호출한다.

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

API-003 CSRF 토큰 조회는 access token 인증과 CSRF 헤더 없이 호출한다. 상태 변경 API에서 토큰 누락·만료·불일치를 확인하면 `403 CSRF_TOKEN_INVALID`를 반환한다. 프론트엔드는 API-003으로 토큰을 다시 받은 뒤 원래 요청을 한 번만 재시도하고, 같은 오류가 반복되면 재시도를 중단한다.

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

### 성공 응답

성공 응답은 공통 `data` envelope로 감싸지 않고 API별 응답 DTO를 최상위 JSON 객체로 직접 반환한다.

단일 리소스는 객체를 직접 반환하고, 목록은 `items` 배열을 포함하는 객체로 반환한다. 페이지네이션 목록은 `items`, `page`, `size`, `totalItems`, `totalPages`를 같은 최상위 객체에 포함한다.

무한 스크롤 목록은 cursor 기반으로 조회한다. 최초 요청에서는 `cursor`를 생략하고, 다음 목록이 있으면 응답의 불투명 문자열 `nextCursor`를 다음 요청의 `cursor`로 그대로 전달한다. 마지막 응답은 `nextCursor: null`을 반환한다. 프론트엔드는 cursor 내부 값을 해석하거나 생성하지 않는다.

nullable 필드는 값이 없을 때 필드를 생략하지 않고 `null`을 반환한다. 배열은 nullable로 사용하지 않고 값이 없으면 빈 배열 `[]`을 반환한다.

새 자기소개서 초안 생성은 `201 Created`를 반환한다. 일반 조회, 저장·수정·삭제와 기존 Job을 반환할 수 있는 LLM Job 시작 요청은 응답 객체와 함께 `200 OK`를 반환한다. SSE 연결은 `200 OK`, OAuth 흐름은 성공·실패 결과에 맞는 redirect 응답을 사용한다.

### 날짜 형식

서버 내부의 모든 시간 값은 `Instant`로 저장하고 처리한다.

API 응답 DTO로 변환할 때는 `ZoneId.of("Asia/Seoul")` 기준으로 변환한 `LocalDateTime`을 사용한다. 따라서 날짜/시간 응답 값은 timezone offset이 없는 ISO 8601 local date-time 문자열이다.

```json
{
  "createdAt": "2026-06-20T14:00:00"
}
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

`details`는 항상 배열로 반환하며 상세 정보가 없으면 빈 배열 `[]`을 사용한다. 프론트엔드는 `HTTP 상태 + error.code`를 분기 기준으로 사용하고, 사용자 문구는 `error.code` 기준으로 매핑한다.

프론트엔드는 공통 오류를 중앙 interceptor에서 처리한다.

- 인증 필요 API의 `401 UNAUTHORIZED`: API-004를 single-flight로 한 번만 호출하고 대기 중인 원 요청을 각각 한 번만 재시도한다. API-004가 `401`이거나 재시도한 원 요청이 다시 `401`이면 로그인 화면으로 이동하며 갱신을 반복하지 않는다.
- 상태 변경 API의 `403 CSRF_TOKEN_INVALID`: API-003으로 토큰을 다시 받은 뒤 원 요청을 한 번만 재시도한다. 같은 오류가 반복되면 중단한다.
- 네트워크 오류와 예상하지 못한 `5xx`: 공통 일시 오류를 표시한다. API별로 명시하지 않은 상태 변경 요청은 중복 실행 위험 때문에 자동 재전송하지 않는다.
- 개별 API 문서에는 해당 화면에서 별도 분기가 필요한 validation, 리소스 없음, 상태 충돌 오류만 기록한다. 공통 `401`, `403`, `5xx`를 기계적으로 반복하지 않는다.
- 프론트엔드는 서버 `error.message`를 그대로 노출하지 않고 알려진 `error.code`를 사용자 문구에 매핑한다. 알 수 없는 코드는 공통 오류 문구를 사용한다.

공통 오류 코드와 HTTP 상태:

| HTTP 상태 | 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---:|---|---|---|
| 400 Bad Request | `VALIDATION_ERROR` | 요청 필드의 형식·길이·범위 위반 | `details[].field`에 해당하는 입력 항목에 `reason`을 표시한다. |
| 401 Unauthorized | `UNAUTHORIZED` | 인증 필요 API에서 access token이 없거나 만료됨 | API-004를 single-flight로 한 번 호출하고 원 요청을 한 번만 재시도한다. 실패하면 로그인 화면으로 이동한다. |
| 403 Forbidden | `CSRF_TOKEN_INVALID` | 상태 변경 요청의 CSRF 토큰 누락·만료·불일치 | API-003으로 토큰을 재발급하고 원래 상태 변경 요청을 한 번만 재시도한다. |
| 404 Not Found | `NOT_FOUND` | 리소스 없음·비소유·삭제 | 대상이 없거나 접근할 수 없음을 안내하고 해당 도메인의 이전 화면으로 이동한다. |
| 409 Conflict | `CONFLICT` | 요청 시점의 리소스 상태가 작업 조건과 맞지 않음 | API별로 명시된 현재 상태 조회를 수행하고 가능한 화면으로 전환한다. |
| 409 Conflict | `LLM_JOB_ALREADY_RUNNING` | 같은 자기소개서에서 다른 종류의 AI Job이 진행 중 | 다른 AI 작업이 진행 중임을 안내하고 새 요청을 중단한다. 오류 응답에 `jobId`가 없으면 기존 Job 복구를 가정하지 않는다. |
| 409 Conflict | `COVER_LETTER_NOT_DRAFT` | DRAFT가 아닌 자기소개서에 임시저장을 요청함 | 자동저장을 중단하고 상세를 재조회해 읽기 전용 또는 현재 상태 화면으로 전환한다. |
| 409 Conflict | `REVIEW_VERSION_NOT_LATEST` | 최신 버전이 아닌 첨삭 버전에 최종 답변 저장을 요청함 | 자동 재전송하지 않고 최신 버전과 상세를 다시 조회한다. |
| 500 Internal Server Error | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 | 공통 일시 오류를 표시하며 상태 변경 요청을 자동 재전송하지 않는다. |

`CSRF_TOKEN_INVALID`는 모든 `POST`, `PUT`, `PATCH`, `DELETE`에 적용되는 공통 오류다. 프론트엔드는 서버의 `error.message`를 그대로 표시하지 않고 CSRF 토큰 재발급과 단일 재시도 흐름을 사용한다.

LLM Job의 `error.code`는 프론트엔드 사용자 메시지 매핑 기준으로 사용한다. 프론트엔드는 provider 원문이나 `error.message`를 그대로 사용자에게 노출하지 않고, `error.code`별 사용자 친화 문구를 표시한다.

공개 LLM Job error code는 프론트엔드의 다음 행동이 달라지는 최소 집합만 사용한다. timeout, provider 장애·요청 제한, 출력 형식 검증 실패처럼 프론트 처리가 같은 원인은 서버 로그에서는 구분하되 공개 응답에서는 `LLM_PROVIDER_ERROR`로 정규화한다.

```text
LLM_PROVIDER_ERROR
LLM_CONTEXT_LENGTH_EXCEEDED
LLM_CONTENT_FILTERED
```

권장 사용자 메시지:

```text
LLM_PROVIDER_ERROR: AI 처리에 실패했습니다. 잠시 후 다시 시도해주세요.
LLM_CONTEXT_LENGTH_EXCEEDED: 입력 내용이 너무 길어 AI가 처리하지 못했습니다.
LLM_CONTENT_FILTERED: 입력 내용 또는 생성 결과를 처리할 수 없습니다. 내용을 수정한 뒤 다시 시도해주세요.
```

프론트엔드가 알 수 없는 LLM Job `error.code`를 받은 경우에도 `error.message`를 그대로 표시하지 않는다. `LLM_PROVIDER_ERROR`와 같은 기본 AI 처리 실패 문구를 표시한다.

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
DRAFT 임시저장: question, maxAnswerLength, originalAnswer는 nullable
제출 시 question: trim 후 Unicode code point 기준 1~300자
제출 시 maxAnswerLength: 100~5000
제출 시 originalAnswer: trim 후 Unicode code point 기준 1~5000자
```

서버는 `question`과 `originalAnswer`의 앞뒤 공백을 제거한다. DRAFT 저장에서는 누락·`null`·trim 후 빈 문자열을 `null`로 저장하고, 값이 있으면 최대 길이와 숫자 범위를 검증한다. API-014 제출 시에는 모든 필드의 필수값과 최소 길이·범위를 최종 검증한다.

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
INTERVIEW_INITIAL_QUESTION_GENERATION
INTERVIEW_ADDITIONAL_QUESTION_GENERATION
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

LLM 출력 파싱 실패, 필수 필드 누락, 타입 불일치, 범위 위반처럼 서버가 기대한 결과 구조로 검증할 수 없는 응답도 LLM Job 실패로 처리한다. 이 경우 가능한 필드만 부분 저장하거나 서버가 임의 기본값으로 보정하지 않는다. 자동 재시도 1회 후에도 구조 검증에 실패하면 `FAILED`로 저장하고, 공개 오류 코드는 프론트 처리가 같은 `LLM_PROVIDER_ERROR`로 정규화한다. 내부 로그와 관측 정보에는 출력 검증 실패 원인을 별도로 보존한다.

같은 자기소개서에 대해 진행 중인 LLM Job은 동시에 하나만 허용한다. `PENDING` 또는 `PROCESSING` 상태의 Job이 있으면 새 LLM Job 시작 요청은 `CONFLICT`와 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

최초 면접 질문 생성 Job은 `type=INTERVIEW_INITIAL_QUESTION_GENERATION`, 추가 면접 질문 생성 Job은 `type=INTERVIEW_ADDITIONAL_QUESTION_GENERATION`으로 구분한다. 두 Job 모두 `requestRef.type=REVIEW_VERSION`, `requestRef.id=생성 기준 첨삭 버전 id`를 저장한다. 추가 생성 Job의 `progress.total`은 1이며 완료 결과는 생성된 `INTERVIEW_QUESTION`을 가리킨다.

첨삭 Job(`COVER_LETTER_REVIEW`, `COVER_LETTER_RE_REVIEW`)은 문항별 호출을 병렬 실행하고, 문항의 `aiReport`와 `rewrittenAnswer`가 모두 완성된 결과만 Job과 연결된 임시 문항 결과로 영속 저장한다. API-012는 이 완료 문항을 진행 상세에 포함하고 API-016은 같은 결과를 `review.questions.items`로 전송한다.

Job 상태 조회는 복구에 필요한 상태, 진행률, 결과 참조와 오류만 반환한다. 필드별 partial text와 토큰별 첨삭 delta는 저장하거나 전송하지 않는다.

모든 문항이 성공하면 임시 결과를 `ReviewVersionQuestionResult`로 확정하고 `ReviewVersion`을 생성한다. 최종 실패한 Job은 새 버전을 만들지 않지만, 성공한 임시 문항 결과는 API-012에서 읽기 전용 부분 결과로 반환한다.

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

사용자가 `새로운 질문 추가하기`를 실행하면 기존 면접 세션은 유지하고, 가장 최근 `ReviewVersion`을 기준으로 면접 질문 1개를 추가 생성한다. 질문마다 생성 기준 버전이 다를 수 있으므로 `InterviewQuestion.sourceReviewVersionId`에 각 질문의 기준 첨삭 버전을 기록한다.

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

AI 면접 질문은 세션 시작 시 5개 생성하고, 사용자가 `새로운 질문 추가하기`를 실행할 때마다 1개씩 추가 생성한다. 모든 질문은 자기소개서 최종 작성본을 기반으로 생성하며 질문 종류를 구분하지 않는다.

```json
{
  "id": "iq_01HZ...",
  "interviewSessionId": "is_01HZ...",
  "sourceReviewVersionId": "rv_01HZ...",
  "order": 1,
  "question": "프로젝트에서 맡은 역할을 더 구체적으로 설명해 주세요.",
  "threadId": "it_01HZ..."
}
```

꼬리질문은 별도 `InterviewQuestion`이 아니라 `InterviewMessage.followUpQuestion`으로 저장한다.

모든 면접 질문은 생성 시 질문별 `InterviewThread`와 1:1로 연결되며 `threadId`는 필수값이다.

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

최초 면접 질문은 `InterviewQuestion.question`으로 표시하고 message로 중복 저장하지 않는다. 사용자가 답변을 전송한 시점부터 `USER` 메시지를 저장하며, 사용자 답변 1개에 대해 assistant 메시지 1개를 생성한다. assistant 메시지는 피드백, 점수, 꼬리질문을 함께 포함하며, 꼬리질문을 별도 메시지로 분리 저장하지 않는다.

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
  "score": 78,
  "followUpQuestion": "그 API 설계에서 가장 중요하게 고려한 트레이드오프는 무엇이었나요?",
  "createdAt": "2026-06-20T16:05:00"
}
```

role:

```text
USER
ASSISTANT
```
