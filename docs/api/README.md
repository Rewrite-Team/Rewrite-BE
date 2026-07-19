# API Documentation

이 디렉터리는 Rewrite 백엔드 API 계약과 구현 상태를 도메인별로 관리한다.

PRD: `../requirements.md`

설계 결정: `../decisions/README.md`

## Document Map

| Document | Purpose |
|---|---|
| `common.md` | 공통 설계 원칙, 공통 규칙, 도메인 모델 |
| `auth.md` | 인증 API |
| `cover-letters.md` | 자기소개서 API |
| `review-versions.md` | AI 첨삭 버전 API |
| `llm-jobs.md` | LLM Job 상태와 스트림 API |
| `keyword-analysis.md` | 키워드 분석 API |
| `interviews.md` | AI 면접 API |

## Domain Routing

| Domain | APIs | API Document | Decision Document |
|---|---|---|---|
| Auth | API-001 - API-006 | `auth.md` | `../decisions/auth.md` |
| Cover Letters | API-007 - API-014, API-030 | `cover-letters.md` | `../decisions/cover-letters.md` |
| Review Versions | API-017 - API-019, API-024 | `review-versions.md` | `../decisions/review-versions.md` |
| LLM Jobs | API-015 - API-016 | `llm-jobs.md` | `../decisions/llm-jobs.md` |
| Keyword Analysis | API-020 - API-021 | `keyword-analysis.md` | `../decisions/keyword-analysis.md` |
| Interviews | API-022 - API-023, API-025 - API-029 | `interviews.md` | `../decisions/interviews.md` |
| Common Rules | 공통 인증, 에러, 시간, 도메인 모델 | `common.md` | `../decisions/common.md` |

## Notion API 문서 동기화

Git 저장소의 Markdown 문서와 실제 코드는 API 계약과 구현 상태의 기준 원본이다. Notion `Rewrite API (자동 동기화)` 데이터베이스는 프론트엔드 개발자가 보는 동기화 문서이며, Notion 직접 수정으로 저장소를 덮어쓰지 않는다.

- API를 추가, 삭제하거나 path, request, response, error, validation, 상태 또는 구현 여부를 변경하면 같은 작업에서 Markdown 계약과 Notion을 `API ID`로 갱신한다.
- 데이터베이스 속성은 `Name`, `Method`, `Path`, `상태`, `사용 화면`, `설명`, `API ID`를 사용한다. `API ID`는 숨김 고유 키이며 제목은 `API-007 · 내 자기소개서 목록` 형식으로 작성한다.
- `상태`는 `구현 중`, `구현 완료`, Deprecated API에만 `사용 안 함`을 사용한다. `사용 화면`은 공통, 로그인, 자기소개서 목록, 자기소개서 등록, 첨삭 진행, 첨삭 결과, 키워드 분석, AI 면접 중 필요한 값을 모두 지정한다.
- 상세 페이지는 호출 요약, Endpoint, Request, Success Response, Error Handling, Frontend Behavior, 필요한 조건부 섹션, 참고 정보 순서로 구성한다. 실제 HTTP 요청과 성공 JSON 예시는 필수이며 JSON 문법을 검증한다.
- Request와 Success Response에는 필드 경로, 타입, 필수 여부, nullable 조건과 설명을 표로 기록한다. 배열 내부 필드는 `items[].id`처럼 펼친다.
- Error Handling은 HTTP 상태, 오류 코드, 발생 조건과 프론트엔드 처리 방법을 표로 기록한다.
- 오류 코드가 계약에 정의되지 않았으면 빈 값, `-`, `계약 참조`를 사용하지 않고 `오류 코드 미정` 또는 `API별 오류 없음`으로 명시한다. 비동기 Job 실패 상태는 HTTP 오류 응답과 구분한다.
- Cookie, CSRF, SSE, redirect, polling, 페이지네이션, nullable·enum·화면 상태 처리는 프론트엔드에 영향을 주는 범위에서 명확히 기록한다. 내부 구현·persistence·worker 구조는 제외한다.
- 공통 오류 JSON 구조는 공통 페이지에서 한 번 정의하고, 개별 페이지에는 실제 발생 가능한 오류만 기록한다. 자세한 갱신 절차는 `../codex-workflow.md`를 따른다.

## API 구현 상태 추적

이 문서는 Rewrite 백엔드 API 계약과 구현 상태의 기준 문서다.

기능 요구사항과 제품 범위는 `../requirements.md`를 함께 확인한다.
API 설계 결정과 트레이드오프는 `../decisions/README.md`를 함께 확인한다.

### Status Values

- `Planned`: 아직 시작하지 않음
- `In Progress`: 구현 중
- `Implemented`: 코드 구현 완료
- `Verified`: 관련 검증이 통과하고 리뷰 또는 사용자 승인 중 하나가 확인됨
- `Deprecated`: 더 이상 사용하지 않음

### API Status

| ID | Method | Path | Status | Related Requirement | Notes |
|---|---|---|---|---|---|
| API-001 | GET | `/auth/kakao/authorize` | Planned | REQ-008 | 카카오 로그인 시작 |
| API-002 | GET | `/auth/kakao/callback` | Planned | REQ-008 | 카카오 OAuth callback |
| API-003 | GET | `/auth/csrf-token` | Planned | REQ-008 | 상태 변경 요청용 CSRF 토큰 |
| API-004 | POST | `/auth/refresh` | Planned | REQ-008 | refresh token rotation |
| API-005 | GET | `/user/me` | Planned | REQ-008 | 내 정보 조회 |
| API-006 | POST | `/auth/logout` | Planned | REQ-008 | 로그아웃 |
| API-007 | GET | `/cover-letters` | In Progress | REQ-003 | 화면 표시용 `displayStatus`를 포함하는 목록 계약 확정, 구현 필요 |
| API-008 | POST | `/cover-letters` | Implemented | REQ-003 | 자기소개서 초안 생성 |
| API-009 | PUT | `/cover-letters/{coverLetterId}/basic-info` | Implemented | REQ-004 | 등록 step1 저장 |
| API-010 | PUT | `/cover-letters/{coverLetterId}/preferences` | Implemented | REQ-004 | 등록 step2 저장 |
| API-011 | PUT | `/cover-letters/{coverLetterId}/questions` | Implemented | REQ-004 | 등록 step3 저장 |
| API-012 | GET | `/cover-letters/{coverLetterId}` | Planned | REQ-003 | 자기소개서 상세 |
| API-013 | DELETE | `/cover-letters/{coverLetterId}` | Implemented | REQ-003 | 자기소개서 soft delete |
| API-014 | POST | `/cover-letters/{coverLetterId}/submit` | Implemented | REQ-005 | 최초 첨삭 Job 생성, 문항별 병렬 호출·1회 재시도, 입력/완료 staging 저장과 전체 성공 시 버전 확정 구현됨 |
| API-015 | GET | `/llm-jobs/{jobId}` | Implemented | REQ-005 | LLM Job 상태 조회 |
| API-016 | GET | `/llm-jobs/{jobId}/stream` | Planned | REQ-005 | SSE 스트리밍 |
| API-017 | GET | `/cover-letters/{coverLetterId}/review-versions` | Implemented | REQ-006 | 첨삭 버전 목록 |
| API-018 | GET | `/cover-letters/{coverLetterId}/review-versions/{versionId}` | In Progress | REQ-006 | 조회 구현됨, API-012와 공통 상세 응답 계약 전환 필요 |
| API-019 | PUT | `/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers` | Implemented | REQ-006 | 최종 작성본 일괄 저장 |
| API-020 | POST | `/cover-letters/{coverLetterId}/keyword-analysis` | Implemented | REQ-009 | 키워드 분석 시작/재분석 |
| API-021 | GET | `/cover-letters/{coverLetterId}/keyword-analysis/latest` | In Progress | REQ-009 | 조회 구현됨, 자기소개서·분석 기준 버전 요약 응답 추가 필요 |
| API-022 | POST | `/cover-letters/{coverLetterId}/interviews` | Implemented | REQ-010 | 면접 세션과 초기 질문 생성 Job 생성 |
| API-023 | POST | `/interview-threads/{threadId}/messages` | Implemented | REQ-010 | USER 답변 저장, 피드백 Job 실행, ASSISTANT 피드백 메시지 저장 |
| API-024 | POST | `/cover-letters/{coverLetterId}/review-versions` | Implemented | REQ-006 | 요청 시점 최신 버전·최종 작성본 입력 고정, 문항별 병렬 호출·1회 재시도와 staging 기반 버전 확정 구현됨 |
| API-025 | GET | `/cover-letters/{coverLetterId}/interview` | Implemented | REQ-010 | 현재 면접 세션 조회 |
| API-026 | GET | `/interviews/{interviewSessionId}/questions` | Implemented | REQ-010 | 유형 구분 없는 자기소개서 기반 면접 질문 목록 |
| API-027 | POST | `/interviews/{interviewSessionId}/questions` | Implemented | REQ-010 | 최신 첨삭 버전 기준 자기소개서 기반 면접 질문과 thread 1개 추가 생성 |
| API-028 | POST | `/interviews/{interviewSessionId}/threads` | Deprecated | REQ-010 | 질문 생성 시 thread를 함께 저장하므로 사용하지 않음 |
| API-029 | GET | `/interview-threads/{threadId}/messages` | Implemented | REQ-010 | 대화 메시지 조회 |
| API-030 | GET | `/cover-letters/stream` | Planned | REQ-003, REQ-005 | 사용자 단일 연결 기반 목록 표시 상태 SSE, `changeType`으로 시작·완료·실패 명시 |
