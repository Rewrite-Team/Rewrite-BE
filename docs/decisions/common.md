# Common API Decisions

## Decision 041: 글자 수는 Unicode code point 기준으로 계산한다

### 결정

문자열 길이 제한과 API 응답의 `*Length` 필드는 Unicode code point 수를 기준으로 계산한다.

적용 대상:

```text
originalAnswer
rewrittenAnswer
finalAnswer
originalAnswerLength
rewrittenAnswerLength
finalAnswerLength
```

최종 작성본은 Decision 040에 따라 앞뒤 공백을 trim한 뒤, trim된 문자열의 Unicode code point 수가 `1~5000`자인지 검증한다.

프론트엔드의 실시간 글자 수 표시도 같은 기준을 사용한다.

### PRD 근거

- PRD는 자기소개서 답변과 최종 작성본에 글자 수를 표시한다고 설명한다.
- API는 `originalAnswerLength`, `rewrittenAnswerLength`, `finalAnswerLength`를 응답한다.
- 프론트엔드 표시값과 백엔드 검증값이 일치해야 사용자 혼란이 적다.

### 고려한 대안

1. Unicode code point 기준
   - 한글, 영문, 숫자 중심 텍스트에서 사용자가 기대하는 글자 수와 잘 맞는다.
   - UTF-16 surrogate pair로 표현되는 문자도 2개가 아니라 1개 code point로 계산할 수 있다.

2. UTF-16 length 기준
   - JavaScript `string.length`와 맞추기 쉽다.
   - 하지만 일부 이모지나 특수문자에서 사용자가 보는 글자 수와 어긋난다.

3. Grapheme cluster 기준
   - 사용자가 눈으로 보는 문자 단위에 가장 가깝다.
   - 백엔드와 프론트엔드 구현 일치가 더 어렵고, MVP에는 복잡도가 높다.

### 선택 이유

Rewrite의 주요 입력은 한글 자기소개서 텍스트다. Unicode code point 기준은 한글/영문 중심의 일반 텍스트에서 직관적인 글자 수에 가깝고, UTF-16 기준보다 특수문자 처리도 덜 어긋난다. Grapheme cluster 기준은 더 정확하지만 MVP의 복잡도 대비 이점이 크지 않다.

### 트레이드오프

- 장점
  - 서버 검증과 응답의 길이 기준이 명확하다.
  - JavaScript UTF-16 length보다 사용자 인식에 더 가까운 경우가 많다.
  - 프론트엔드와 백엔드가 같은 기준을 공유할 수 있다.

- 단점
  - 프론트엔드는 `string.length`를 그대로 쓰면 안 되고 code point 기준 계산을 사용해야 한다.
  - 결합 문자나 복합 이모지에서는 사용자가 보는 글자 수와 완전히 같지는 않을 수 있다.
  - 완전한 시각적 문자 기준이 필요하면 향후 grapheme cluster 기준으로 재검토해야 한다.



## Decision 069: 모든 사용자 리소스 접근은 소유자 검증 후 비소유 리소스는 NOT_FOUND로 응답한다

### 결정

인증이 필요한 모든 사용자 리소스 접근은 현재 로그인 사용자의 소유자인지 검증한다.

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

중첩 리소스는 상위 리소스와의 관계도 함께 검증한다.

예:

```text
reviewVersionId는 해당 coverLetterId에 속해야 한다.
interviewQuestionId는 해당 interviewSessionId에 속해야 한다.
threadId는 해당 사용자의 면접 세션에 속해야 한다.
jobId는 현재 사용자의 리소스를 target으로 해야 한다.
```

리소스가 존재하지 않거나 현재 사용자의 소유가 아니면 `NOT_FOUND`를 반환한다.

다른 사용자의 리소스 존재 여부를 노출하지 않기 위해 소유자 불일치에도 `FORBIDDEN`이 아니라 `NOT_FOUND`를 사용한다.

### PRD 근거

- 사용자는 로그인 후 자신의 자기소개서, 첨삭 결과, 키워드 분석, AI 면접 데이터만 조회하고 수정해야 한다.
- 자기소개서와 면접 기록은 사용자 개인 데이터다.
- URL의 ID를 바꾸어 다른 사용자의 리소스 존재 여부를 추측할 수 없어야 한다.

### 고려한 대안

1. 비소유 리소스는 `NOT_FOUND`
   - 리소스 존재 여부를 숨길 수 있다.
   - ID enumeration 공격에 더 안전하다.
   - 존재하지 않는 리소스와 권한 없는 리소스를 같은 방식으로 처리한다.

2. 비소유 리소스는 `FORBIDDEN`
   - 권한 문제라는 의미는 명확하다.
   - 하지만 해당 ID의 리소스가 존재한다는 사실을 노출할 수 있다.

### 선택 이유

Rewrite의 주요 리소스는 사용자 개인 데이터다. API path에 포함된 ID를 바꿔 호출했을 때 `FORBIDDEN`과 `NOT_FOUND`를 구분해 주면 공격자가 리소스 존재 여부를 추측할 수 있다. 따라서 비소유 리소스는 존재하지 않는 것처럼 `NOT_FOUND`로 응답한다.

### 트레이드오프

- 장점
  - 다른 사용자의 리소스 존재 여부를 숨길 수 있다.
  - 모든 사용자 리소스 접근 규칙이 단순해진다.
  - 보안 기본값이 강하다.

- 단점
  - 클라이언트는 실제 미존재와 권한 부족을 구분할 수 없다.
  - 운영/디버깅에서는 내부 로그로 권한 실패 원인을 별도로 확인해야 한다.
  - 일부 관리자 기능이 생기면 별도 권한 모델과 응답 정책이 필요하다.



## Decision 071: 날짜/시간 응답은 Asia/Seoul 기준 LocalDateTime으로 반환한다

### 결정

서버 내부의 모든 시간 값은 `Instant`로 저장하고 처리한다.

API 응답 DTO로 변환할 때는 `ZoneId.of("Asia/Seoul")` 기준으로 변환한 `LocalDateTime`을 사용한다.

따라서 사용자-facing API의 날짜/시간 응답 값은 timezone offset이 없는 ISO 8601 local date-time 문자열로 반환한다.

예:

```json
{
  "createdAt": "2026-06-20T14:00:00",
  "updatedAt": "2026-06-20T14:30:00"
}
```

적용 대상:

```text
createdAt
updatedAt
submittedAt
deletedAt
completedAt
```

화면 표기 형식은 프론트엔드에서 변환한다.

### 고려한 대안

1. API 응답도 `Instant` 또는 offset 포함 문자열로 반환
   - 절대 시각 의미가 응답에 그대로 남는다.
   - 하지만 프론트엔드가 모든 화면에서 한국 시간 변환을 반복해야 한다.

2. 서버 내부는 `Instant`, 응답 DTO는 `Asia/Seoul` 기준 `LocalDateTime`
   - 저장/비즈니스 로직은 절대 시각 기준으로 안정적으로 처리할 수 있다.
   - 프론트엔드는 화면 표시용 한국 시간 값을 바로 받을 수 있다.
   - 응답 문자열 자체에는 offset 정보가 없으므로 API 공통 규칙에서 기준 zone을 명시해야 한다.

### 선택 이유

시간 계산과 저장은 `Instant`를 사용해 서버 내부 기준을 명확히 유지하고, 사용자 화면은 한국 시간 기준으로 표시한다. API 응답 DTO에서 `Asia/Seoul` 기준 `LocalDateTime`으로 변환하면 프론트엔드 표시 요구사항과 서버 내부 시간 처리 원칙을 함께 만족할 수 있다.

### 트레이드오프

- 장점
  - 서버 내부 시간 처리는 절대 시각 기준으로 일관된다.
  - 프론트엔드는 한국 시간 기준 값을 바로 사용해 화면 표기만 변환하면 된다.
  - API 예시의 timestamp 포맷이 `LocalDateTime`과 일치한다.

- 단점
  - 응답 값만 보면 offset 정보가 없으므로 API 문서의 공통 규칙을 따라야 한다.
  - 향후 다국가 시간대 지원이 필요하면 사용자별 zone 변환 정책을 다시 결정해야 한다.

## Decision 097: 프론트엔드 오류 계약은 공통 복구와 화면별 최소 도메인 오류로 구분한다

### 결정

프론트엔드가 모든 API에서 동일하게 처리하는 인증, CSRF와 예상하지 못한 서버 오류는 COMMON 계약과 중앙 interceptor에서 처리한다. 개별 API는 validation 입력 표시, 대상 없음 이동, 상태 충돌처럼 해당 화면의 행동이 달라지는 오류만 기록한다.

`401 UNAUTHORIZED`는 API-004 single-flight 갱신 후 원 요청을 한 번만 재시도하고, `403 CSRF_TOKEN_INVALID`는 API-003 재발급 후 원 요청을 한 번만 재시도한다. 반복 실패에서는 재시도를 중단한다. API별로 별도 허용하지 않은 상태 변경 요청은 네트워크 또는 `5xx`에서 자동 재전송하지 않는다.

비동기 Job의 `FAILED`와 `job.failed`는 HTTP 오류가 아니라 정상 조회·스트림에서 받은 작업 결과로 분리한다. 공개 Job 오류 코드는 프론트 동작이 다른 `LLM_PROVIDER_ERROR`, `LLM_CONTEXT_LENGTH_EXCEEDED`, `LLM_CONTENT_FILTERED`만 사용한다. timeout, provider 장애·요청 제한과 출력 형식 검증 실패는 내부 로그에서는 구분하되 공개 응답에서는 `LLM_PROVIDER_ERROR`로 정규화한다.

### 선택 이유

공통 오류를 모든 API 페이지에 반복하면 프론트엔드가 화면별 처리와 interceptor 처리를 중복 구현하기 쉽다. 반대로 화면 행동이 다른 오류를 일반 `CONFLICT`나 `INTERNAL_ERROR`로만 표현하면 복구 방법을 결정할 수 없다. 공통 복구와 도메인 분기를 나누면 오류 코드 수를 늘리지 않으면서도 각 코드가 구체적인 프론트 행동에 대응한다.

### 트레이드오프

- 장점
  - 인증과 CSRF 재시도 루프를 한 곳에서 차단할 수 있다.
  - 개별 API 오류 표가 실제 화면 분기에 집중한다.
  - 비동기 실패와 HTTP 실패를 혼동하지 않는다.
- 단점
  - 프론트엔드는 개별 API 페이지와 COMMON 페이지를 함께 따라야 한다.
  - 서버 내부의 세부 provider 실패 원인은 사용자-facing 오류 코드만으로 구분할 수 없다.
