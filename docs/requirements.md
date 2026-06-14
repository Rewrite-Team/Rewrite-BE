# Rewrite Requirements

이 문서는 Rewrite 제품 요구사항의 기준 문서다.

진행 상태와 남은 작업은 `docs/status.md`에서 추적한다.
API 계약과 API별 구현 상태는 `docs/api/README.md`와 `docs/api/` 하위 도메인 문서에서 추적한다.
설계 결정과 트레이드오프는 `docs/decisions/README.md`와 `docs/decisions/` 하위 도메인 문서에서 추적한다.

## Product Goal

Rewrite는 AI를 활용해 자기소개서 첨삭, 자기소개서 키워드 분석, 자기소개서 기반 AI 면접 연습을 제공한다.

초기 MVP는 대규모 운영 최적화보다 API 계약, 저장 정책, 실패 복구 흐름을 먼저 안정화하는 것을 목표로 한다.

## Requirement Index

| ID | Requirement | Domain | Priority | API Documents |
|---|---|---|---|---|
| REQ-001 | 공통 예외 응답 기반 | Common | High | `docs/api/common.md` |
| REQ-002 | 개발용 현재 사용자 Provider | Common/Auth | High | `docs/api/common.md`, `docs/api/auth.md` |
| REQ-003 | 자기소개서 기본 CRUD | Cover Letters | High | `docs/api/cover-letters.md` |
| REQ-004 | 자기소개서 등록 step 저장 | Cover Letters | High | `docs/api/cover-letters.md` |
| REQ-005 | 자기소개서 제출과 LLM Job 생성 | Cover Letters/LLM Jobs | High | `docs/api/cover-letters.md`, `docs/api/llm-jobs.md` |
| REQ-006 | 첨삭 버전 조회와 최종 작성본 저장 | Review Versions | High | `docs/api/review-versions.md` |
| REQ-007 | DB/JPA 전환 | Persistence | High | 내부 persistence 변경 |
| REQ-008 | 실제 인증 경계 | Auth | Medium | `docs/api/auth.md` |
| REQ-009 | 키워드 분석 | Keyword Analysis | Medium | `docs/api/keyword-analysis.md`, `docs/api/llm-jobs.md` |
| REQ-010 | AI 면접 | Interviews | Medium | `docs/api/interviews.md`, `docs/api/llm-jobs.md` |

## REQ-001: 공통 예외 응답 기반

### Goal

모든 API가 예측 가능한 공통 에러 응답 형식을 사용한다.

### Rules

- 예측 가능한 비즈니스 오류는 `BusinessException`과 `ErrorCode`로 표현한다.
- API 에러 응답은 `ErrorResponse` 형식을 사용한다.
- validation 실패는 `VALIDATION_ERROR`로 응답한다.
- provider 원문 에러 메시지나 다른 사용자의 리소스 존재 여부를 사용자-facing 응답에 노출하지 않는다.

### Acceptance Criteria

- 공통 예외 처리기가 validation 오류와 비즈니스 오류를 일관된 JSON 형식으로 변환한다.
- 테스트에서 대표 validation 오류, 비즈니스 오류, 예상하지 못한 서버 오류 응답을 검증한다.

## REQ-002: 개발용 현재 사용자 Provider

### Goal

실제 OAuth 구현 전에도 인증이 필요한 API의 소유자 검증 경계를 개발하고 테스트할 수 있다.

### Rules

- 현재 로그인 사용자를 조회하는 경계는 `CurrentUserProvider`로 둔다.
- 개발 단계에서는 고정된 개발용 사용자를 반환하는 구현체를 사용한다.
- 실제 인증 도입 시 controller/service의 소유자 검증 흐름을 유지하고 provider 구현체를 교체한다.

### Acceptance Criteria

- service는 user id를 직접 생성하지 않고 현재 사용자 provider를 통해 조회한다.
- 개발용 provider는 테스트에서 예측 가능한 사용자 정보를 반환한다.

## REQ-003: 자기소개서 기본 CRUD

### Goal

사용자는 자기소개서를 만들고, 목록에서 확인하고, 상세로 진입하고, 삭제할 수 있다.

### User Flow

- 사용자는 내 자기소개서 목록에서 자기소개서 업로드 흐름을 시작한다.
- 목록은 한 페이지에 최대 9개의 자기소개서를 카드 형태로 제공한다.
- 목록 카드에는 회사명, 직무명, 자기소개서 제목, 작성 또는 첨삭 상태, 등록일이 표시된다.
- 자기소개서 카드를 선택하면 해당 자기소개서의 AI 첨삭 상세 화면으로 이동한다.
- 사용자는 자기소개서를 삭제할 수 있다.

### Rules

- 목록은 현재 로그인 사용자의 자기소개서만 반환한다.
- 삭제는 soft delete로 처리한다.
- 삭제된 자기소개서와 하위 리소스는 사용자-facing API에서 `NOT_FOUND`로 응답한다.
- 임시저장된 자기소개서도 목록에 노출한다.

### Acceptance Criteria

- 목록 조회는 page, size, status 필터를 지원한다.
- 생성 API는 비어 있는 자기소개서 초안을 만들고 id를 반환한다.
- 상세 조회는 기본 정보, 문항, 최신 첨삭 진행 상태를 반환한다.
- 삭제 후 목록과 상세 조회에서 삭제된 자기소개서가 노출되지 않는다.

## REQ-004: 자기소개서 등록 step 저장

### Goal

사용자는 자기소개서 등록 중 기본 정보, 우대사항, 질문과 답변을 단계별로 임시저장할 수 있다.

### Step Fields

| Step | Fields |
|---|---|
| Basic Info | 자기소개서 제목 최대 50자, 회사명 최대 30자, 직무명 최대 30자, 공고 링크 최대 500자 |
| Preferences | 채용 우대사항 최대 3000자 |
| Questions | 질문 최대 300자, 최대 글자 수 100-5000자, 답변 최대 5000자, 문항 추가 가능 |

### Rules

- 각 step 저장 API는 화면의 현재 상태를 전체 replace 방식으로 저장한다.
- 문자열 입력은 앞뒤 공백을 trim한 뒤 검증하고 저장한다.
- 빈 공고 링크는 `null`로 저장한다.
- 공고 링크는 URL 형식만 검증하고 외부 사이트 접근 가능 여부는 검증하지 않는다.
- 질문 배열의 순서는 서버가 요청 배열 순서대로 재부여한다.
- 제출 후 원본 자기소개서의 기본 정보, 우대사항, 질문과 답변은 수정할 수 없다.

### Acceptance Criteria

- step별 validation 오류는 필드별 `details`로 반환한다.
- step3 저장 시 요청에 없는 기존 문항은 삭제된다.
- 저장된 step 데이터는 상세 조회에서 이어서 작성 가능한 형태로 복구된다.

## REQ-005: 자기소개서 제출과 LLM Job 생성

### Goal

사용자는 작성 완료한 자기소개서를 제출하고, 서버는 최초 AI 첨삭을 비동기 LLM Job으로 시작한다.

### User Flow

- 사용자는 등록 확인 화면에서 작성 내용을 확인한다.
- 사용자가 완료 버튼을 누르면 자기소개서가 목록에 추가되고 첨삭 중 상태로 표시된다.
- 클라이언트는 반환된 `jobId`로 Job 상태 조회 또는 SSE 스트림에 연결한다.

### Review Criteria

LLM 첨삭은 다음 기준을 참고한다.

- STAR 방식으로 작성되었는가
- 구체적으로 작성되었는가
- 우대사항에 적합하게 작성되었는가
- 직무 키워드에 적합하게 작성되었는가
- 맞춤법을 잘 지켰는가
- 문장이 자연스러운가
- 중복 표현은 없는가
- 글자 수를 준수하였는가

### Rules

- 제출 시 필수 step 데이터가 누락되면 `VALIDATION_ERROR`와 필드별 `details`를 반환한다.
- 최초 첨삭 진행 중 중복 제출하면 새 Job을 만들지 않고 기존 진행 중 Job을 반환한다.
- 같은 자기소개서에 대해 진행 중인 LLM Job은 하나만 허용한다.
- 최초 첨삭 실패 시 자기소개서 상태는 `REVIEW_FAILED`가 된다.
- `REVIEW_FAILED` 상태에서는 submit 재호출로 최초 첨삭을 재시도할 수 있다.

### Acceptance Criteria

- 제출 성공 시 자기소개서 상태와 LLM Job 상태가 함께 갱신된다.
- Job 상태 조회와 SSE 스트림으로 진행 상태를 확인할 수 있다.
- LLM provider 실제 호출 전에도 skeleton Job 흐름을 테스트할 수 있다.

## REQ-006: 첨삭 버전 조회와 최종 작성본 저장

### Goal

사용자는 AI 첨삭 결과를 버전별로 확인하고, 최신 버전의 최종 작성본을 일괄 저장할 수 있다.

### User Flow

- AI 첨삭 화면은 회사명, 직무, 자기소개서 제목, 공고 링크를 표시한다.
- 각 문항은 원본 질문, 원본 답변, AI 리포트, AI 수정본, 최종 작성본을 표시한다.
- 프론트엔드는 원본 답변과 AI 수정본을 비교해 diff를 표시한다.
- 사용자는 `AI첨삭 다시받기`로 새 첨삭 버전을 요청할 수 있다.
- 사용자는 최신 첨삭 버전에서 문항별 최종 작성본을 수정하고 저장한다.
- 버전 히스토리는 `v0.1` 같은 라벨과 기록 시각, 최신 여부를 표시한다.

### Rules

- ReviewVersion은 성공한 첨삭 또는 재첨삭 결과에 대해서만 생성한다.
- 최종 작성본 저장만으로는 새 버전을 생성하지 않는다.
- 최종 작성본은 최신 ReviewVersion에서만 수정할 수 있다.
- 최종 작성본 저장 payload는 전체 문항을 항상 포함한다.
- 최종 작성본은 빈 문자열로 저장할 수 없다.
- 재첨삭 요구사항은 최대 1000자다.

### Acceptance Criteria

- 버전 목록과 버전 상세 조회가 최신 버전 여부를 제공한다.
- 최종 작성본 저장은 모든 문항을 검증하고 한 번에 반영한다.
- 최신 버전이 아닌 버전에 저장을 시도하면 계약된 오류를 반환한다.

## REQ-007: DB/JPA 전환

### Goal

API-008 자기소개서 초안 생성의 공개 API 계약은 유지하되, 현재 in-memory persistence 구현을 DB/JPA 기반으로 교체한 뒤 남은 자기소개서 CRUD를 확장한다.

### Rules

- API-008 자기소개서 초안 생성까지는 in-memory repository로 API 계약과 기본 service/controller 흐름을 확인한 상태로 본다.
- DB/JPA 전환 이슈는 API-008의 path, status code, response body 계약을 유지하면서 내부 persistence 구현을 DB/JPA로 교체하는 작업을 포함한다.
- API-007 목록 조회, API-012 상세 조회, API-013 soft delete, 등록 step 저장 API는 pagination, owner filter, deletedAt 필터, 정렬, transaction boundary 영향을 받으므로 DB/JPA 전환 이후 구현한다.
- DB/JPA 전환은 기존 API 계약을 유지하면서 persistence 구현을 교체하는 별도 이슈로 진행한다.
- 초기 DB/JPA 전환 범위는 JPA entity/repository, DB driver, 테스트 가능한 DB 설정, transaction 검증까지로 제한한다.
- Flyway와 migration versioning은 초기 DB/JPA 전환 범위에 포함하지 않고, 스키마 변경 이력 관리가 필요한 시점에 별도 이슈로 검토한다.

### Acceptance Criteria

- 기존 API-008 응답 계약은 DB/JPA 전환 후에도 유지된다.
- 선택한 DB 설정에서 애플리케이션 프로세스 재시작 후에도 저장 데이터가 보존된다.
- repository 테스트와 transaction 검증이 추가된다.
- Flyway 의존성, migration 파일, migration 검증은 이번 전환의 완료 기준에 포함하지 않는다.
- 기존 API 계약은 유지된다.

## REQ-008: 실제 인증 경계

### Goal

사용자는 카카오 OAuth로 로그인하고, Rewrite는 HttpOnly Cookie 기반 인증을 사용한다.

### User Flow

- 사용자는 카카오 로그인으로 로그인한다.
- 로그인 후 사용자의 닉네임과 프로필 사진을 화면 우측 상단에 표시할 수 있다.
- 클라이언트는 인증이 필요한 요청에 cookie를 포함한다.

### Rules

- 백엔드는 카카오 OAuth callback을 직접 처리한다.
- access token은 30분, refresh token은 14일 동안 유효하다.
- refresh token rotation을 사용한다.
- 상태 변경 요청에는 CSRF 토큰을 포함한다.
- JavaScript에서는 token 값을 직접 읽지 않는다.

### Acceptance Criteria

- 로그인 시작, OAuth callback, CSRF 토큰 조회, 토큰 갱신, 내 정보 조회, 로그아웃 API가 계약대로 동작한다.
- 인증이 필요한 리소스는 현재 사용자의 소유자인지 검증한다.

## REQ-009: 키워드 분석

### Goal

사용자는 첨삭된 자기소개서의 핵심 키워드를 분석하고, 최신 분석 결과를 확인하거나 재분석할 수 있다.

### User Flow

- 키워드 분석 첫 화면에는 키워드 분석 시작 버튼이 있다.
- 분석 결과는 워드 클라우드와 키워드별 중요도 지표로 표시된다.
- 사용자는 `AI 키워드 재분석`으로 최신 첨삭 버전을 기준으로 다시 분석할 수 있다.

### Rules

- 키워드 중요도는 1-100 범위의 정수다.
- 키워드 분석 결과는 자기소개서별 최신 결과만 유지한다.
- 재분석은 새 리소스를 만들지 않고 기존 최신 결과를 갱신한다.
- 재첨삭 완료 시 기존 키워드 분석 결과는 삭제한다.
- Job 실패 시 키워드 분석 리소스는 `FAILED` 상태로 저장한다.

### Acceptance Criteria

- 분석 시작 또는 재분석 API는 LLM Job을 반환한다.
- 최신 결과 조회는 진행 중, 실패, 완료 상태를 구분해 반환한다.
- 완료 결과는 상위 20개 키워드를 제공한다.

## REQ-010: AI 면접

### Goal

사용자는 첨삭된 자기소개서를 바탕으로 예상 면접 질문을 생성하고, 질문별 대화방에서 답변 피드백과 꼬리질문을 받을 수 있다.

### User Flow

- AI 면접 첫 화면에는 모의면접 시작 버튼이 있다.
- 모의면접 시작 시 LLM이 자기소개서를 바탕으로 예상 질문 5개를 생성한다.
- 사용자는 새로운 질문 5개를 추가 생성할 수 있다.
- 사용자가 예상 질문을 선택하면 해당 질문의 대화방이 열린다.
- 사용자가 답변을 전송하면 LLM은 점수, 피드백, 꼬리질문을 생성한다.
- 다른 질문을 선택하면 질문별 독립 대화방에서 대화가 이어진다.

### Rules

- 자기소개서당 AI 면접 세션은 하나만 유지한다.
- 재첨삭 후에도 기존 AI 면접 세션은 유지한다.
- 질문 추가 생성은 가장 최근 첨삭 버전을 기준으로 한다.
- 면접 답변은 최대 2000자다.
- 면접 점수는 단일 1-100점 보조 지표로 제공한다.
- 면접 꼬리질문은 피드백과 같은 assistant 메시지에 저장한다.
- AI 면접 답변 피드백은 완료 후 assistant 메시지만 저장한다.

### Acceptance Criteria

- 현재 면접 세션 조회, 면접 시작, 질문 목록 조회, 질문 추가 생성, 질문별 대화방 생성/조회, 메시지 조회, 사용자 답변 전송 API가 계약대로 동작한다.
- 질문별 대화방은 서로 독립된 메시지 기록을 가진다.
- 답변 전송 성공 시 사용자 메시지와 assistant 피드백 메시지가 조회 가능하다.

## Operating Assumptions

- 주요 사용자는 브라우저 기반 웹 클라이언트 사용자다.
- 일반 CRUD 요청은 짧은 동기 HTTP 요청으로 처리한다.
- AI 첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백은 수 초에서 수 분까지 걸릴 수 있는 장기 작업이다.
- 장기 LLM 작업은 비동기 Job과 SSE로 처리한다.
- 같은 자기소개서에 대한 진행 중 LLM Job은 하나만 허용한다.
- 한 자기소개서 안에서 첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백을 동시에 실행하지 않는다.
- DB/JPA 전환 전 in-memory repository에 저장된 데이터와 진행 중 LLM partial result는 서버 재시작 시 유실될 수 있다.
- DB/JPA 전환 후 일반 CRUD 데이터는 선택한 DB 저장소에 보존하고, 진행 중 LLM partial result는 별도 cache 저장소 도입 전까지 서버 메모리에 둘 수 있다.
- 진행 중 LLM partial result를 서버 재시작 또는 scale-out 상황에서도 유지하려면 Redis 같은 외부 cache 저장소가 필요하다.
- LLM 비용과 남용 방지를 위해 사용자별 rate limit, quota, Job 재시도 정책을 운영 설정으로 둘 수 있어야 한다.
- 인증 cookie, CORS, CSRF, SameSite 설정은 프론트엔드 배포 도메인과 함께 검증해야 한다.
