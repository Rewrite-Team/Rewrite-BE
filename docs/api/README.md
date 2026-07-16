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
| `rewrite-api-documentation.xlsx` | 전체 API 목록, 요청·응답 필드, 에러·검증, 구현 여부, API별 최근 변경 일시 |

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

## Excel API 문서 관리

`rewrite-api-documentation.xlsx`는 Git에서 관리하는 기준 원본이고, Google Drive의 기존 Excel 파일은 공유용 미러다. API를 추가, 삭제하거나 path, request, response, error, validation, 상태 또는 구현 여부를 변경하면 같은 작업에서 Markdown 계약과 Excel 문서를 함께 갱신한다. Drive 반영은 `../codex-workflow.md`의 동기화 절차를 따른다.

- 프론트엔드 구현과 디버깅에 필요한 API별 기능, 실제 요청·응답 예시, 필드 의미와 제약, 정확한 HTTP 상태와 오류 코드, 인증·CSRF 요구사항, 구현 여부를 유지한다. 소스 경로, 중복 예시와 내부 설계 설명은 제외한다.
- 목차와 각 API 상세 시트에는 `Asia/Seoul` 기준 `최근 변경일`과 `최근 변경 시각`을 기록하고, API 계약이나 문서 내용이 바뀌면 갱신한다.
- 첫 시트는 전체 API 목차로 유지하고 이후에는 API ID별 상세 시트를 하나씩 사용한다. 목차의 API ID와 `상세 보기`, 각 상세 시트의 목차 링크는 내부 링크로 연결한다.
- 회색 계열을 기본 색상으로 사용하고, 표 본문에는 행별 줄무늬나 상태별 컬러 배경을 사용하지 않는다. 제목, 섹션 헤더, 표 헤더와 코드 예시는 회색 명도 차이로 구분한다.
- 각 상세 시트에는 필드 설명과 별도로 실제 HTTP 요청과 성공 응답 형식을 유지한다. path, query, header, cookie, body, HTTP status, content type과 JSON 또는 SSE 본문은 계약과 일치해야 한다.
- 오류 표에는 `400 Bad Request`처럼 실제 HTTP 상태를 기록한다. validation 제약은 요청 필드 표에 한 번만 기록하고 오류 표에는 `VALIDATION_ERROR` 한 행만 둔다.
- 계약에만 있고 아직 구현되지 않은 인증·CSRF 오류는 `계약 정의·미구현`으로 표시한다.
- 인쇄 설정은 A4 가로, 너비 1페이지 맞춤으로 유지하고 HTTP 코드 예시가 페이지 경계에서 잘리지 않게 한다.
- 변경 후 모든 시트를 렌더링하여 잘림과 가독성을 확인하고 주요 범위와 수식 오류를 검증한다.

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
| API-007 | GET | `/cover-letters` | In Progress | REQ-003 | `activeReviewJob`을 포함하는 내 자기소개서 목록 계약 확정, 구현 필요 |
| API-008 | POST | `/cover-letters` | Implemented | REQ-003 | 자기소개서 초안 생성 |
| API-009 | PUT | `/cover-letters/{coverLetterId}/basic-info` | Implemented | REQ-004 | 등록 step1 저장 |
| API-010 | PUT | `/cover-letters/{coverLetterId}/preferences` | Implemented | REQ-004 | 등록 step2 저장 |
| API-011 | PUT | `/cover-letters/{coverLetterId}/questions` | Implemented | REQ-004 | 등록 step3 저장 |
| API-012 | GET | `/cover-letters/{coverLetterId}` | Planned | REQ-003 | 자기소개서 상세 |
| API-013 | DELETE | `/cover-letters/{coverLetterId}` | Implemented | REQ-003 | 자기소개서 soft delete |
| API-014 | POST | `/cover-letters/{coverLetterId}/submit` | In Progress | REQ-005 | 첨삭 Job 생성 구현됨, 문항별 병렬 처리와 임시 결과 저장 전환 필요 |
| API-015 | GET | `/llm-jobs/{jobId}` | Implemented | REQ-005 | LLM Job 상태 조회 |
| API-016 | GET | `/llm-jobs/{jobId}/stream` | Planned | REQ-005 | SSE 스트리밍 |
| API-017 | GET | `/cover-letters/{coverLetterId}/review-versions` | Implemented | REQ-006 | 첨삭 버전 목록 |
| API-018 | GET | `/cover-letters/{coverLetterId}/review-versions/{versionId}` | In Progress | REQ-006 | 조회 구현됨, API-012와 공통 상세 응답 계약 전환 필요 |
| API-019 | PUT | `/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers` | Implemented | REQ-006 | 최종 작성본 일괄 저장 |
| API-020 | POST | `/cover-letters/{coverLetterId}/keyword-analysis` | Implemented | REQ-009 | 키워드 분석 시작/재분석 |
| API-021 | GET | `/cover-letters/{coverLetterId}/keyword-analysis/latest` | In Progress | REQ-009 | 조회 구현됨, 자기소개서·분석 기준 버전 요약 응답 추가 필요 |
| API-022 | POST | `/cover-letters/{coverLetterId}/interviews` | Implemented | REQ-010 | 면접 세션과 초기 질문 생성 Job 생성 |
| API-023 | POST | `/interview-threads/{threadId}/messages` | Implemented | REQ-010 | USER 답변 저장, 피드백 Job 실행, ASSISTANT 피드백 메시지 저장 |
| API-024 | POST | `/cover-letters/{coverLetterId}/review-versions` | In Progress | REQ-006 | 재첨삭 Job 생성 구현됨, 문항별 병렬 처리와 임시 결과 저장 전환 필요 |
| API-025 | GET | `/cover-letters/{coverLetterId}/interview` | Implemented | REQ-010 | 현재 면접 세션 조회 |
| API-026 | GET | `/interviews/{interviewSessionId}/questions` | Implemented | REQ-010 | 유형 구분 없는 자기소개서 기반 면접 질문 목록 |
| API-027 | POST | `/interviews/{interviewSessionId}/questions` | Implemented | REQ-010 | 최신 첨삭 버전 기준 자기소개서 기반 면접 질문과 thread 1개 추가 생성 |
| API-028 | POST | `/interviews/{interviewSessionId}/threads` | Deprecated | REQ-010 | 질문 생성 시 thread를 함께 저장하므로 사용하지 않음 |
| API-029 | GET | `/interview-threads/{threadId}/messages` | Implemented | REQ-010 | 대화 메시지 조회 |
| API-030 | GET | `/cover-letters/stream` | Planned | REQ-003, REQ-005 | 사용자 단일 연결 기반 자기소개서 첨삭 상태 SSE |
