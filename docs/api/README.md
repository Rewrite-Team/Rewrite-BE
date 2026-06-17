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
| Cover Letters | API-007 - API-014 | `cover-letters.md` | `../decisions/cover-letters.md` |
| Review Versions | API-017 - API-019, API-024 | `review-versions.md` | `../decisions/review-versions.md` |
| LLM Jobs | API-015 - API-016 | `llm-jobs.md` | `../decisions/llm-jobs.md` |
| Keyword Analysis | API-020 - API-021 | `keyword-analysis.md` | `../decisions/keyword-analysis.md` |
| Interviews | API-022 - API-023, API-025 - API-029 | `interviews.md` | `../decisions/interviews.md` |
| Common Rules | 공통 인증, 에러, 시간, 도메인 모델 | `common.md` | `../decisions/common.md` |

## API 구현 상태 추적

이 문서는 Rewrite 백엔드 API 계약과 구현 상태의 기준 문서다.

기능 요구사항과 제품 범위는 `../requirements.md`를 함께 확인한다.
API 설계 결정과 트레이드오프는 `../decisions/README.md`를 함께 확인한다.

### Status Values

- `Planned`: 아직 시작하지 않음
- `In Progress`: 구현 중
- `Implemented`: 코드 구현 완료
- `Verified`: 테스트와 리뷰 또는 사용자 승인이 확인됨
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
| API-007 | GET | `/cover-letters` | Implemented | REQ-003 | 내 자기소개서 목록 |
| API-008 | POST | `/cover-letters` | Implemented | REQ-003 | 자기소개서 초안 생성 |
| API-009 | PUT | `/cover-letters/{coverLetterId}/basic-info` | Planned | REQ-004 | 등록 step1 저장 |
| API-010 | PUT | `/cover-letters/{coverLetterId}/preferences` | Planned | REQ-004 | 등록 step2 저장 |
| API-011 | PUT | `/cover-letters/{coverLetterId}/questions` | Planned | REQ-004 | 등록 step3 저장 |
| API-012 | GET | `/cover-letters/{coverLetterId}` | Planned | REQ-003 | 자기소개서 상세 |
| API-013 | DELETE | `/cover-letters/{coverLetterId}` | Planned | REQ-003 | 자기소개서 soft delete |
| API-014 | POST | `/cover-letters/{coverLetterId}/submit` | Planned | REQ-005 | 첨삭 Job 생성 |
| API-015 | GET | `/llm-jobs/{jobId}` | Planned | REQ-005 | LLM Job 상태 조회 |
| API-016 | GET | `/llm-jobs/{jobId}/stream` | Planned | REQ-005 | SSE 스트리밍 |
| API-017 | GET | `/cover-letters/{coverLetterId}/review-versions` | Planned | REQ-006 | 첨삭 버전 목록 |
| API-018 | GET | `/cover-letters/{coverLetterId}/review-versions/{versionId}` | Planned | REQ-006 | 첨삭 버전 상세 |
| API-019 | PUT | `/cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers` | Planned | REQ-006 | 최종 작성본 일괄 저장 |
| API-020 | POST | `/cover-letters/{coverLetterId}/keyword-analysis` | Planned | REQ-009 | 키워드 분석 시작/재분석 |
| API-021 | GET | `/cover-letters/{coverLetterId}/keyword-analysis/latest` | Planned | REQ-009 | 최신 키워드 분석 조회 |
| API-022 | POST | `/cover-letters/{coverLetterId}/interviews` | Planned | REQ-010 | 면접 세션/질문 생성 |
| API-023 | POST | `/interview-threads/{threadId}/messages` | Planned | REQ-010 | 면접 답변 전송 |
| API-024 | POST | `/cover-letters/{coverLetterId}/review-versions` | Planned | REQ-006 | AI 첨삭 다시받기 |
| API-025 | GET | `/cover-letters/{coverLetterId}/interview` | Planned | REQ-010 | 현재 면접 세션 조회 |
| API-026 | GET | `/interviews/{interviewSessionId}/questions` | Planned | REQ-010 | 면접 질문 목록 |
| API-027 | POST | `/interviews/{interviewSessionId}/questions` | Planned | REQ-010 | 면접 질문 추가 생성 |
| API-028 | POST | `/interviews/{interviewSessionId}/threads` | Planned | REQ-010 | 질문별 대화방 생성/조회 |
| API-029 | GET | `/interview-threads/{threadId}/messages` | Planned | REQ-010 | 대화 메시지 조회 |
