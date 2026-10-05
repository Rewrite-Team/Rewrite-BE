# 공통 API·인증·Job 흐름

HTTP 경로·요청·응답·validation·오류 예시는 Swagger UI를 기준으로 확인한다.
이 문서는 여러 API에 걸친 인증 복구와 SSE 사용 흐름을 보완한다.
구현·명세 작성 기준은 [명세 작성 기준](../README.md#openapi--swagger), 모델·영속 구조는 [ERD](../erd.md)를 따른다.

## 공통 처리

- 인증 요청은 `credentials: include`를 사용하며 JavaScript에서 HttpOnly token을 읽지 않는다.
- 리소스 소유권·중첩 관계를 검증하고, 없음·비소유·soft delete 리소스와 그 하위 리소스는 `NOT_FOUND`로 처리한다.
- 응답은 API별 DTO를 최상위 객체로 반환하며 공통 `data` envelope를 사용하지 않는다.
- nullable 필드는 생략하지 않고 `null`, 값이 없는 배열은 `[]`로 반환한다.
- 서버 시간은 `Instant`, 응답은 `Asia/Seoul`의 offset 없는 ISO 8601 `LocalDateTime`이다.
- 화면에서는 목록 등록일 `2026.06.20`, 버전 기록 `2026.05.20 14:00`처럼 변환한다.
- 문자열 제한·`*Length`·프론트엔드 글자 수는 Unicode code point 기준이다.
- cursor 최초 요청은 생략하고 다음 요청은 받은 `nextCursor`를 그대로 사용한다.
- 마지막 `nextCursor`는 `null`이며 클라이언트에서 cursor를 해석·생성하지 않는다.

### 오류 복구

프론트엔드는 중앙 interceptor에서 `HTTP 상태 + error.code`로 분기한다.
서버 `error.message`·provider 원문은 노출하지 않고 알려진 code를 사용자 문구로 매핑한다.
알 수 없는 code는 공통 오류 문구를 사용한다.

| 오류 | 처리 |
|---|---|
| `VALIDATION_ERROR` | `details[].field`와 `reason`을 입력 항목에 연결 |
| 인증 필요 API의 `UNAUTHORIZED` | API-004 single-flight 1회 → 원 요청 각각 1회 재시도; refresh 또는 원 요청이 다시 401이면 로그인으로 이동하고 반복 중단 |
| `CSRF_TOKEN_INVALID` | API-003 재발급 → 원 요청 1회 재시도; 반복 실패 시 중단 |
| `NOT_FOUND` | 대상 없음 안내 후 해당 도메인의 이전 화면으로 이동 |
| `CONFLICT` | 현재 상태를 재조회해 가능한 화면·동작으로 전환 |
| `LLM_JOB_ALREADY_RUNNING` | 새 요청 중단; 오류에 `jobId`가 없으면 기존 Job 복구를 가정하지 않음 |
| 네트워크·예상하지 못한 `5xx` | 일시 오류 안내; API에서 명시하지 않은 상태 변경 요청은 자동 재전송하지 않음 |

## 로그인과 인증

관련 API: API-001~006. 설계 근거: [인증 결정](../decisions/auth.md), 제품 기준: [REQ-008](../requirements.md#req-008-실제-인증-경계).

### 카카오 로그인

1. API-001로 카카오 인가 화면에 이동한다.
2. 백엔드가 API-002 callback의 state·브라우저 nonce를 검증한다.
3. 성공하면 인증 Cookie를 발급하고 target의 `/writing`으로 이동한다.
4. 실패·취소하면 target의 `/login?error={code}`로 redirect한다.

| 프론트엔드 | API-001 `target` | 로그인 결과 origin |
|---|---|---|
| 로컬 | `local` | `http://localhost:3000` |
| 운영 | `production` 또는 생략 | `https://rewrite-coverletters.site` |

- 임의 URL을 target으로 받지 않으며 선택한 target은 state에 결합한다.
- callback은 운영 백엔드 `https://api.rewrite-coverletters.site/auth/kakao/callback` 하나를 사용한다.
- `code`와 `error` 중 하나만 전달하고 두 경우 모두 state·nonce가 필요하다.
- 저장한 해시와 비교해 5분 안에 한 번만 소비하며 callback 성공·취소·실패 모두 nonce를 만료시킨다.
- 로그인 시작 실패와 callback 오류는 JSON `ErrorResponse` 대신 redirect로 처리한다.
- 취소 code `KAKAO_LOGIN_CANCELED`는 “카카오 로그인이 취소되었습니다.”를 표시한다.
- state·nonce 누락/불일치/만료/재사용, code 교환·카카오 사용자 조회·저장 실패는 `KAKAO_LOGIN_FAILED`로 처리한다.
- 취소·실패 모두 버튼을 다시 활성화하고 자동 재시도하지 않는다.
- 알 수 없는 code는 일반 로그인 실패로 처리하며 `error_description`·내부 검증 원인·오류 메시지는 노출하지 않는다.

### Cookie와 사용자 상태

모든 Cookie는 `HttpOnly; Secure`를 사용한다.

| Cookie | Path | SameSite | 수명 |
|---|---|---|---|
| `oauth_login_nonce` | `/auth/kakao` | `Lax` | 5분(300초) |
| `access_token` | `/` | `None` | 30분(1800초) |
| `refresh_token` | `/auth` | `None` | 14일(1209600초) |

- localhost에서 운영 API로 요청하는 Cookie가 차단되면 개발 브라우저에서 `api.rewrite-coverletters.site`의 서드파티 Cookie를 허용해야 한다.
- API-005로 사용자 상태를 반영하며 현재 provider는 `KAKAO`다.
- 카카오 프로필 이미지가 없으면 `profileImageUrl=null`, 나머지 사용자 필드는 non-null이다.

### CSRF·토큰 갱신·로그아웃

- API-003은 access token·CSRF 헤더 없이 호출하고 `POST`, `PUT`, `PATCH`, `DELETE`에는 `X-CSRF-Token`을 보낸다.
- API-003의 `500 INTERNAL_ERROR`는 자동 재시도 1회 후 다시 실패하면 상태 변경 기능을 막고 새로고침을 안내한다.
- API-004는 access token·body 없이 refresh Cookie와 CSRF 헤더를 사용한다.
- 동시에 만료된 요청은 한 갱신 결과를 기다리며 성공 시 각각 1회 재시도한다.
- 갱신 성공 시 두 token을 새로 발급하고 이전 refresh token을 폐기한다.
- refresh 누락·만료·위조·폐기·재사용은 모두 `401 UNAUTHORIZED`이며 두 인증 Cookie를 만료시킨다.
- 갱신 중 `500 INTERNAL_ERROR`는 Cookie·사용자 상태를 임의로 지우거나 자동 재시도하지 않는다.
- 로그아웃 요청 시 진행 중인 API-004와 `Set-Cookie` 반영을 기다린 뒤 API-006을 호출한다.
- 로그아웃을 시작한 뒤 새 갱신이나 대기 중 원 요청의 재시도를 시작하지 않는다.
- API-006은 인증 Cookie가 없거나 만료돼도 멱등 `200 OK`이며 두 Cookie를 만료시킨다.
- 유효한 refresh token은 서버에서도 폐기하고 성공 후 로컬 사용자 상태를 정리해 로그인 화면으로 이동한다.

## 비동기 Job과 SSE

관련 API: 상태 복구 API-015, 공통 스트림 API-016. 근거는 [Job 결정](../decisions/llm-jobs.md)을 따른다.

- 최초·재첨삭, 키워드, 초기·추가 면접 질문, 답변 피드백은 시작 API의 `jobId`로 추적한다.
- 같은 자기소개서에서 `PENDING`·`PROCESSING` Job은 하나만 허용한다.
- 동일 작업의 중복 요청은 도메인 규칙에 따라 기존 Job을 반환하며 다른 종류는 `LLM_JOB_ALREADY_RUNNING`으로 처리한다.
- 서버 자동 재시도는 1회이고 두 번 모두 실패하면 `FAILED`다.
- 출력 구조·필수 값·타입·범위 검증 실패도 Job 실패이며 임의 기본값이나 일부 필드 저장으로 보정하지 않는다.
- API-015는 상태·진행률·결과 참조·오류만 반환하며 SSE 장애·이벤트 유실·새로고침 복구에 사용한다.
- `status=FAILED`는 정상 조회·SSE에서 받은 작업 결과이며 HTTP `ErrorResponse`와 구분한다.

### 연결·스냅샷·이벤트

1. API-016에 연결하면 `job.state`를 먼저 받는다.
2. 첨삭이면 완료 문항 스냅샷 `review.questions`를 받는다.
3. 새 문항 완료 시 문항 결과를 먼저 저장하고 해당 문항 이벤트 → 갱신된 `job.state` 순서로 받는다.
4. 상태·진행률 변화는 같은 `job.state`로 받고 종료 후 결과 API를 재조회한다.

```text
event: job.state
data: {"jobType":"COVER_LETTER_REVIEW","status":"PROCESSING","progress":{"current":1,"total":3,"message":"첨삭 중"},"resultRef":null,"error":null}

event: review.questions
data: {"items":[{"questionId":"clq_01HZ...","order":1,"aiReport":"성과를 보강해주세요.","rewrittenAnswer":"수정 답변","rewrittenAnswerLength":5,"finalAnswer":"수정 답변","finalAnswerLength":5}]}

event: interview.feedback.delta
data: {"sequence":1,"contentDelta":"답변에서 API 설계 경험은 "}
```

- `job.state`는 `jobType`, `status`, `progress`, `resultRef`, `error`를 항상 포함한다.
- `progress`와 그 하위 필드는 항상 non-null이다.
- `jobType`은 `COVER_LETTER_REVIEW`, `COVER_LETTER_RE_REVIEW`, `KEYWORD_ANALYSIS`, `INTERVIEW_INITIAL_QUESTION_GENERATION`, `INTERVIEW_ADDITIONAL_QUESTION_GENERATION`, `INTERVIEW_MESSAGE_FEEDBACK` 중 하나다.
- `status`는 `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELED` 중 하나다.
- 완료 `resultRef.type`은 `REVIEW_VERSION`, `KEYWORD_ANALYSIS`, `INTERVIEW_SESSION`, `INTERVIEW_QUESTION`, `INTERVIEW_MESSAGE` 중 하나다.
- `resultRef`는 결과가 확정된 완료 Job에서만 non-null이고 그 외에는 `null`이다.
- `error`는 실패 Job에서만 non-null이고 그 외에는 `null`이다.
- 완료·실패·취소는 이벤트 이름 대신 `COMPLETED`, `FAILED`, `CANCELED`로 구분한다.
- `review.questions.items`는 스냅샷이면 전체 완료 문항, 새 완료이면 해당 문항 하나, 없으면 `[]`다.
- 문항은 `questionId`로 upsert하고 `order`로 정렬하며 최종 실패해도 성공 문항은 읽기 전용으로 유지한다.
- 서버는 라우터 등록 후 스냅샷을 전송하고 그 사이 변경을 버퍼링해 발생 순서대로 전달한다.
- `Last-Event-ID` 영속 replay는 제공하지 않으며 heartbeat는 SSE comment다.
- 이미 종료된 Job에 연결하면 최종 상태를 전달하고 첨삭이면 문항 스냅샷까지 전달한 뒤 연결을 종료한다.
- 실시간 처리 중 종료되면 최종 `job.state`를 전송하고 연결을 종료한다.
- `CANCELED`이면 부분 결과를 폐기하고 원래 화면·목록을 재조회한다.

### 면접 피드백 replay

- 전체 OpenAI 응답을 검증한 뒤 완성된 `content`를 조각으로 전송하며 실제 token 스트리밍은 제공하지 않는다.
- 검증 실패 시 delta를 보내지 않는다.
- 조각 경계는 서버 세부사항이며 클라이언트는 `sequence` 순서로 `contentDelta`를 이어 붙인다.
- `sequence`는 Job별로 1부터 증가한다.
- `PROCESSING` 동안 delta를 메모리에 보관하고 재연결 시 `job.state` 다음에 `sequence=1`부터 replay한다.
- 이미 반영한 sequence는 무시하며 버퍼는 영속 저장하지 않고 Job 종료 시 제거한다.
- 버퍼가 없으면 불완전한 뒷부분을 표시하지 않고 진행 상태만 유지한다.
- 완료 후 API-029의 저장된 assistant `content`·`score`로 임시 문장을 교체한다.
- 실패 시 임시 문장을 제거하고 실패 상태를 표시한다.

### 완료·오류 복구

| Job | 완료 후 조회 |
|---|---|
| 최초·재첨삭 | API-012, 선택 버전은 API-018 |
| 키워드 분석 | API-021 |
| 초기 면접 질문 | API-025와 API-026 |
| 추가 면접 질문 | API-026 |
| 면접 답변 피드백 | API-029 |

- 키워드·초기/추가 면접 질문 생성은 중간 도메인 이벤트를 제공하지 않는다.
- SSE `onerror`에서 HTTP 오류를 추측하지 않고 API-015·도메인 조회로 인증·대상·진행 상태를 확인한다.
- 장애가 지속되면 API-015 또는 도메인 polling으로 전환하며 `NOT_FOUND`면 polling·재연결을 중단한다.
- 공개 Job 오류는 아래 3개만 사용하고 알 수 없는 code는 기본 AI 실패 문구로 처리한다.
- 내부 실패 원인은 로그에 보존하며 provider 원문·`error.message`를 그대로 노출하지 않는다.

| Job 오류 | 화면 처리 |
|---|---|
| `LLM_PROVIDER_ERROR` | timeout·provider 장애/제한·출력 검증 실패: AI 실패 안내와 도메인별 수동 재시도 |
| `LLM_CONTEXT_LENGTH_EXCEEDED` | 입력 문맥이 너무 길다는 안내, 자동 재시도 금지 |
| `LLM_CONTENT_FILTERED` | 내용 수정 안내, 자동 재시도 금지 |
