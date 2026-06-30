# Development Status

이 문서는 Rewrite 백엔드의 진행 현황과 남은 작업을 추적하는 기준 문서다.

상세 제품 요구사항은 `docs/requirements.md`를 확인한다.
API 계약과 API별 상태는 `docs/api/README.md`와 `docs/api/` 하위 도메인 문서를 확인한다.

## Status Values

- `Planned`: 아직 시작하지 않음
- `In Progress`: 구현 중
- `Implemented`: 코드 구현 완료
- `Verified`: 테스트와 리뷰 또는 사용자 승인이 확인됨
- `Deferred`: 후순위로 미룸
- `Removed`: 현재 범위에서 제외

## Feature Roadmap

| ID | Feature | Status | Priority | Related APIs | Related Issue/PR | Verification Evidence | Notes |
|---|---|---|---|---|---|---|---|
| REQ-001 | 공통 예외 응답 기반 | Verified | High | 공통 에러 응답 | [#2](https://github.com/Rewrite-Team/Rewrite-BE/issues/2), [PR #3](https://github.com/Rewrite-Team/Rewrite-BE/pull/3) | `GlobalExceptionHandlerTest`, `./gradlew test` | `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`, `ErrorResponse` 구현됨 |
| REQ-002 | 개발용 현재 사용자 Provider | Verified | High | 인증 필요 API 공통 | [#4](https://github.com/Rewrite-Team/Rewrite-BE/issues/4), [PR #6](https://github.com/Rewrite-Team/Rewrite-BE/pull/6) | `DevCurrentUserProviderTest`, `./gradlew test` | `CurrentUserProvider`, `DevCurrentUserProvider` 구현됨 |
| REQ-003 | 자기소개서 기본 CRUD | In Progress | High | API-007, API-008, API-012, API-013 | [#5](https://github.com/Rewrite-Team/Rewrite-BE/issues/5), [#18](https://github.com/Rewrite-Team/Rewrite-BE/issues/18), [#28](https://github.com/Rewrite-Team/Rewrite-BE/issues/28), [#30](https://github.com/Rewrite-Team/Rewrite-BE/issues/30), [PR #15](https://github.com/Rewrite-Team/Rewrite-BE/pull/15) | `CoverLetterServiceTest`, `CoverLetterControllerTest`, `CoverLetterRepositoryTest`, `./gradlew test`, `./gradlew check` | API-007 현재 사용자 자기소개서 목록 조회, API-008 자기소개서 초안 생성, API-013 soft delete 계약 구현됨. 진행 중 Job cancel은 `llm_jobs` 구현 이후 연결 |
| REQ-004 | 자기소개서 등록 step 저장 | Implemented | High | API-009, API-010, API-011 | [#32](https://github.com/Rewrite-Team/Rewrite-BE/issues/32), [#34](https://github.com/Rewrite-Team/Rewrite-BE/issues/34), [#36](https://github.com/Rewrite-Team/Rewrite-BE/issues/36) | `CoverLetterServiceTest`, `CoverLetterControllerTest`, `CoverLetterQuestionRepositoryTest`, `GlobalExceptionHandlerTest`, `./gradlew test`, `./gradlew check` | API-009 등록 step1 기본 정보 저장, API-010 등록 step2 채용 우대사항 저장, API-011 등록 step3 질문과 답변 저장 계약 구현됨 |
| REQ-005 | 자기소개서 제출과 LLM Job 생성 | In Progress | High | API-014, API-015, API-016 | [#38](https://github.com/Rewrite-Team/Rewrite-BE/issues/38), [#40](https://github.com/Rewrite-Team/Rewrite-BE/issues/40), [#42](https://github.com/Rewrite-Team/Rewrite-BE/issues/42), [#44](https://github.com/Rewrite-Team/Rewrite-BE/issues/44), [#48](https://github.com/Rewrite-Team/Rewrite-BE/issues/48) | `CoverLetterRepositoryTest`, `CoverLetterServiceTest`, `CoverLetterControllerTest`, `LlmJobRepositoryTest`, `LlmJobServiceTest`, `LlmJobControllerTest`, `ReviewVersionServiceTest`, `OpenAiFirstReviewClientTest`, `FirstReviewJobWorkerTest`, `FirstReviewJobEventIntegrationTest`, `./gradlew test`, `./gradlew check` | API-014 제출과 PENDING 최초 첨삭 Job 생성, API-015 Job 상태 조회, 최초 첨삭 성공 결과 완료 경계, OpenAI 최초 첨삭 client, after-commit 비동기 worker 연결 구현됨. API-016 SSE, partial result, 자동 재시도는 후속 이슈로 분리 |
| REQ-006 | 첨삭 버전 조회와 최종 작성본 저장 | In Progress | High | API-017, API-018, API-019, API-024 | [#42](https://github.com/Rewrite-Team/Rewrite-BE/issues/42), [#44](https://github.com/Rewrite-Team/Rewrite-BE/issues/44), [#48](https://github.com/Rewrite-Team/Rewrite-BE/issues/48), [#50](https://github.com/Rewrite-Team/Rewrite-BE/issues/50) | `ReviewVersionRepositoryTest`, `ReviewVersionServiceTest`, `ReviewVersionQueryServiceTest`, `ReviewVersionControllerTest`, `OpenAiFirstReviewClientTest`, `FirstReviewJobWorkerTest`, `./gradlew test`, `./gradlew check` | `ReviewVersion`, 문항별 결과 persistence, 최초 첨삭 성공 저장 경계와 OpenAI 문항별 첨삭 결과 client 및 worker 연결, API-017/018 첨삭 버전 목록/상세 조회 구현됨. API-019, API-024는 후속 범위 |
| REQ-007 | DB/JPA 전환 | Implemented | High | API-008 내부 persistence, persistence 내부 변경 | [#22](https://github.com/Rewrite-Team/Rewrite-BE/issues/22), [#24](https://github.com/Rewrite-Team/Rewrite-BE/issues/24), [#25](https://github.com/Rewrite-Team/Rewrite-BE/issues/25), [PR #26](https://github.com/Rewrite-Team/Rewrite-BE/pull/26) | `CoverLetterRepositoryTest`, `CoverLetterServiceTest`, `CoverLetterControllerTest`, `./gradlew test`, `./gradlew check` | API-008 공개 계약은 유지하고 `cover_letters` persistence를 DB/JPA로 전환. H2 file 로컬 DB, H2 in-memory 테스트 DB 사용. Flyway는 별도 이슈로 분리 |
| REQ-008 | 실제 인증 경계 | Planned | Medium | API-001 - API-006 | - | - | 카카오 OAuth, cookie, CSRF |
| REQ-009 | 키워드 분석 | Planned | Medium | API-020, API-021 | - | - | LLM Job 기반 |
| REQ-010 | AI 면접 | Planned | Medium | API-022, API-023, API-025 - API-029 | - | - | LLM Job/SSE 기반 |

## Open Maintenance Work

제품 기능 요구사항은 아니지만 현재 열려 있는 저장소 운영/문서/설정 작업이다.

| Issue | Scope | Status | Notes |
|---|---|---|---|
| [#16](https://github.com/Rewrite-Team/Rewrite-BE/issues/16) | Backend 문서 구조 및 개발 규칙 정리 | Open | 현재 문서 구조와 작업 규칙 정리 범위 |
| [#46](https://github.com/Rewrite-Team/Rewrite-BE/issues/46) | Codecov 및 PR CI 리포트 개선 | Open | Codecov informational status, JaCoCo 수치와 Codecov 상세 링크를 포함한 고정 CI 댓글 구성 |

## Current Recommended Next Work

다음 개발 이슈는 요청 시점의 문서 상태와 실제 코드/테스트 상태를 함께 확인한 뒤 정한다.
하나의 이슈는 독립적으로 구현, 리뷰, 검증할 수 있는 작은 범위로 잡는다.
서로 다른 기능, 큰 리팩터링, 인프라 변경, 문서 정리는 가능한 한 별도 이슈로 분리한다.

이슈 작성 시 확인 기준:

- `docs/status.md`의 feature status
- 관련 `docs/requirements.md` 섹션
- 관련 `docs/api/` 도메인 문서의 API 상태와 계약
- 필요한 경우 `docs/decisions/` 도메인 문서의 설계 결정
- 실제 코드와 테스트 구현 상태
- `.github/ISSUE_TEMPLATE/` 하위 템플릿

## Next Issue Slice Candidates

다음 이슈를 만들 때는 아래 후보 중 하나를 선택하고, 실제 코드/테스트 상태를 다시 확인한 뒤 범위를 확정한다.

| Candidate | Related REQ | Related APIs | Suggested Scope |
|---|---|---|---|
| 최종 작성본 일괄 저장 API | REQ-006 | API-019 | 최신 `ReviewVersion`의 모든 문항 최종 작성본을 검증 후 한 번에 저장 |
| LLM Job 자동 재시도 | REQ-005, REQ-006 | API-014 이후 내부 실행 | provider/output 실패 시 Decision 017 기준 1회 자동 재시도 처리 |

## Update Rules

- 기능 구현을 시작하면 관련 feature status를 `In Progress`로 바꾼다.
- 코드 구현이 끝났지만 검증/리뷰가 끝나지 않았으면 `Implemented`로 둔다.
- 테스트 통과와 리뷰 또는 사용자 승인이 확인된 뒤에만 `Verified`로 바꾼다.
- `Implemented` 또는 `Verified`로 변경할 때는 `Verification Evidence`에 테스트, 리뷰, 사용자 승인, 또는 확인한 근거를 기록한다.
- API 상태 변경은 `docs/api/README.md`의 `API Status`도 함께 갱신한다.
- issue 또는 PR이 생성되면 `Related Issue/PR`에 번호나 링크를 기록한다.
- 요구사항, API 계약, 설계 결정이 바뀌면 `docs/README.md`의 Change Impact Matrix에 따라 관련 문서를 함께 갱신한다.
- 다음 개발 후보가 완료되었거나 더 이상 적절하지 않으면 `Next Issue Slice Candidates`를 정리한다.
