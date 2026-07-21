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
- API-001~030의 오류 계약은 COMMON의 인증·CSRF·서버 오류 복구와 각 도메인 문서의 화면별 최소 오류로 구분한다. 비동기 Job 실패는 HTTP 오류와 분리한다.
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
| API-001 | GET | `/auth/kakao/authorize` | Planned | REQ-008 | 성공 시 카카오, 실패 시 `KAKAO_LOGIN_FAILED` 로그인 화면 redirect 계약 확정 |
| API-002 | GET | `/auth/kakao/callback` | Planned | REQ-008 | 취소 `KAKAO_LOGIN_CANCELED`, 처리 실패 `KAKAO_LOGIN_FAILED` 로그인 화면 redirect 계약 확정 |
| API-003 | GET | `/auth/csrf-token` | Planned | REQ-008 | 인증 없는 토큰 발급, `INTERNAL_ERROR`와 공통 `CSRF_TOKEN_INVALID` 단일 재시도 계약 확정 |
| API-004 | POST | `/auth/refresh` | Planned | REQ-008 | refresh token rotation |
| API-005 | GET | `/user/me` | Planned | REQ-008 | 내 정보 조회 |
| API-006 | POST | `/auth/logout` | Planned | REQ-008 | 로그아웃 |
| API-007 | GET | `/cover-letters` | In Progress | REQ-003 | 화면 표시용 `displayStatus` 응답·필터 계약 확정, 구현 필요 |
| API-008 | POST | `/cover-letters` | In Progress | REQ-003 | 생성 응답을 `id` 단일 필드로 확정, 구현 변경 필요 |
| API-009 | PUT | `/cover-letters/{coverLetterId}/basic-info` | In Progress | REQ-004 | nullable 기본 정보 DRAFT 스냅샷과 `success` 응답 계약 확정, 구현 변경 필요 |
| API-010 | PUT | `/cover-letters/{coverLetterId}/preferences` | In Progress | REQ-004 | nullable 우대사항 DRAFT 스냅샷과 `success` 응답 계약 확정, 구현 변경 필요 |
| API-011 | PUT | `/cover-letters/{coverLetterId}/questions` | In Progress | REQ-004 | nullable 문항 DRAFT 전체 replace와 `success` 응답 계약 확정, 구현 변경 필요 |
| API-012 | GET | `/cover-letters/{coverLetterId}` | In Progress | REQ-003, REQ-004, REQ-005, REQ-006 | 미완성 DRAFT 복구, 상태 공통 상세와 실패 Job 부분 성공 문항 계약 확정, 구현 필요 |
| API-013 | DELETE | `/cover-letters/{coverLetterId}` | In Progress | REQ-003 | 삭제 응답을 `success` 단일 필드로 확정, 구현 변경 필요 |
| API-014 | POST | `/cover-letters/{coverLetterId}/submit` | In Progress | REQ-004, REQ-005 | DRAFT 필수값 최종 검증과 `displayStatus`, `jobId` 응답 계약 확정, 구현 변경 필요 |
| API-015 | GET | `/llm-jobs/{jobId}` | In Progress | REQ-005, REQ-009, REQ-010 | 복구용 경량 Job 상태 응답으로 확정, 구현 변경 필요 |
| API-016 | GET | `/llm-jobs/{jobId}/stream` | In Progress | REQ-005, REQ-006, REQ-009, REQ-010 | 동일 구조의 Job 상태, 첨삭 문항, 검증된 면접 피드백의 점진 전송·재연결 replay 계약 확정, 구현 필요 |
| API-017 | GET | `/cover-letters/{coverLetterId}/review-versions` | Implemented | REQ-006 | 성공한 첨삭 버전을 `createdAt` 오름차순으로 반환 |
| API-018 | GET | `/cover-letters/{coverLetterId}/review-versions/{versionId}` | In Progress | REQ-006 | 조회 구현됨, API-012와 공통 상세 응답 계약 전환 필요 |
| API-019 | PUT | `/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers` | In Progress | REQ-006 | `versionId` 동시성 검증을 유지하고 저장 응답을 `success` 단일 필드로 확정, 구현 변경 필요 |
| API-020 | POST | `/cover-letters/{coverLetterId}/keyword-analysis` | In Progress | REQ-009 | 요청 body 제거, `status`·`jobId` 응답과 동일 분석 중복 요청의 기존 Job 반환 계약 확정, 구현 변경 필요 |
| API-021 | GET | `/cover-letters/{coverLetterId}/keyword-analysis/latest` | In Progress | REQ-009 | 화면 메타데이터와 조건부 `jobId`, SSE 복구용 polling fallback 계약 확정, 구현 변경 필요 |
| API-022 | POST | `/cover-letters/{coverLetterId}/interviews` | In Progress | REQ-010 | 최신 버전 자동 선택, 기존 Job 멱등 반환과 공통 Job SSE 계약 확정, 구현 변경 필요 |
| API-023 | POST | `/interview-threads/{threadId}/messages` | In Progress | REQ-010 | `userMessageId`·`jobId` 응답과 API-016 실시간 피드백 delta 연결 계약 확정, 구현 변경 필요 |
| API-024 | POST | `/cover-letters/{coverLetterId}/review-versions` | In Progress | REQ-006 | 응답을 `displayStatus`, `jobId`로 단순화하고 동일 재첨삭 중복 요청 시 기존 Job 반환 계약 확정, 구현 변경 필요 |
| API-025 | GET | `/cover-letters/{coverLetterId}/interview` | In Progress | REQ-010 | 자기소개서 요약, 현재 세션과 초기·추가 질문 생성용 조건부 `jobId`, SSE 복구 계약 확정, 구현 변경 필요 |
| API-026 | GET | `/interviews/{interviewSessionId}/questions` | Implemented | REQ-010 | 최신 질문 우선 cursor 무한 스크롤과 화면에 필요한 질문 필드만 반환 |
| API-027 | POST | `/interviews/{interviewSessionId}/questions` | In Progress | REQ-010 | `jobId` 단일 응답, 동일 Job 멱등 반환과 공통 Job SSE 계약 확정, 구현 변경 필요 |
| API-028 | POST | `/interviews/{interviewSessionId}/threads` | Deprecated | REQ-010 | 질문 생성 시 thread를 함께 저장하므로 사용하지 않음 |
| API-029 | GET | `/interview-threads/{threadId}/messages` | In Progress | REQ-010 | 표시 문장·점수 중심 메시지와 조건부 `jobId` 응답 계약 확정, 구현 변경 필요 |
| API-030 | GET | `/cover-letters/stream` | Planned | REQ-003, REQ-005 | 사용자 단일 연결에서 전체 상태 스냅샷 후 단건 표시 상태 변경 SSE 제공 |
