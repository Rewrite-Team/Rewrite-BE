# Development Status

이 문서는 Rewrite 백엔드의 진행 현황과 남은 작업을 추적하는 기준 문서다.

상세 제품 요구사항은 `docs/requirements.md`를 확인한다.
API 계약과 API별 상태는 `docs/api/README.md`와 `docs/api/` 하위 도메인 문서를 확인한다.

## Status Values

- `Planned`: 아직 시작하지 않음
- `In Progress`: 구현 중
- `Implemented`: 코드 구현 완료
- `Verified`: 관련 검증이 통과하고 리뷰 또는 사용자 승인 중 하나가 확인됨
- `Deferred`: 후순위로 미룸
- `Removed`: 현재 범위에서 제외

## Feature Roadmap

| ID | Feature | Status | Priority | Related APIs | Related Issue/PR | Verification Evidence | Notes |
|---|---|---|---|---|---|---|---|
| REQ-001 | 공통 예외 응답 기반 | Verified | High | 공통 에러 응답 | [#2](https://github.com/Rewrite-Team/Rewrite-BE/issues/2), [PR #3](https://github.com/Rewrite-Team/Rewrite-BE/pull/3) | `GlobalExceptionHandlerTest`, `./gradlew test` | `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`, `ErrorResponse` 구현됨 |
| REQ-002 | 개발용 현재 사용자 Provider | Verified | High | 인증 필요 API 공통 | [#4](https://github.com/Rewrite-Team/Rewrite-BE/issues/4), [PR #6](https://github.com/Rewrite-Team/Rewrite-BE/pull/6) | `DevCurrentUserProviderTest`, `./gradlew test` | `CurrentUserProvider`, `DevCurrentUserProvider` 구현됨 |
| REQ-003 | 자기소개서 기본 CRUD | In Progress | High | API-007, API-008, API-012, API-013, API-030 | [#5](https://github.com/Rewrite-Team/Rewrite-BE/issues/5), [#18](https://github.com/Rewrite-Team/Rewrite-BE/issues/18), [#28](https://github.com/Rewrite-Team/Rewrite-BE/issues/28), [#30](https://github.com/Rewrite-Team/Rewrite-BE/issues/30), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90), [PR #15](https://github.com/Rewrite-Team/Rewrite-BE/pull/15) | `CoverLetterServiceTest`, `CoverLetterControllerTest`, `CoverLetterRepositoryTest`, `./gradlew test`, `./gradlew check` | API-007 `displayStatus`, API-008 `id`, API-013 `success` 단일 응답, API-012 상태 공통 상세와 API-030 사용자 단일 SSE 계약을 확정했으며 구현 필요 |
| REQ-004 | 자기소개서 등록 step 저장 | In Progress | High | API-009, API-010, API-011, API-012, API-014 | [#32](https://github.com/Rewrite-Team/Rewrite-BE/issues/32), [#34](https://github.com/Rewrite-Team/Rewrite-BE/issues/34), [#36](https://github.com/Rewrite-Team/Rewrite-BE/issues/36), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | `CoverLetterServiceTest`, `CoverLetterControllerTest`, `CoverLetterQuestionRepositoryTest`, `GlobalExceptionHandlerTest`, `./gradlew test`, `./gradlew check` | 기존 step 저장 구현 완료. API-009~011을 nullable DRAFT 임시저장으로 전환하고 API-012 미완성 데이터 복구, API-014 최종 필수값 검증 계약을 확정했으며 DTO·service·persistence 구현 변경 필요 |
| REQ-005 | 자기소개서 제출과 LLM Job 생성 | In Progress | High | API-014, API-015, API-016 | [#38](https://github.com/Rewrite-Team/Rewrite-BE/issues/38), [#40](https://github.com/Rewrite-Team/Rewrite-BE/issues/40), [#42](https://github.com/Rewrite-Team/Rewrite-BE/issues/42), [#44](https://github.com/Rewrite-Team/Rewrite-BE/issues/44), [#48](https://github.com/Rewrite-Team/Rewrite-BE/issues/48), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#88](https://github.com/Rewrite-Team/Rewrite-BE/issues/88), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | `CoverLetterRepositoryTest`, `CoverLetterServiceTest`, `CoverLetterControllerTest`, `LlmJobRepositoryTest`, `LlmJobServiceTest`, `LlmJobControllerTest`, `ReviewJobQuestionResultRepositoryTest`, `ReviewVersionServiceTest`, `OpenAiReviewClientTest`, `FirstReviewJobWorkerTest`, `ReReviewJobWorkerTest`, `FirstReviewJobEventIntegrationTest`, `./gradlew test`, `./gradlew check` | API-016 `jobType` 공통 스냅샷·종료 이벤트와 첨삭 문항 이벤트, HTTP와 분리한 최소 공개 Job 실패 코드 계약을 확정했으며 구현 변경 필요 |
| REQ-006 | 첨삭 버전 조회와 최종 작성본 저장 | In Progress | High | API-017, API-018, API-019, API-024 | [#42](https://github.com/Rewrite-Team/Rewrite-BE/issues/42), [#44](https://github.com/Rewrite-Team/Rewrite-BE/issues/44), [#48](https://github.com/Rewrite-Team/Rewrite-BE/issues/48), [#50](https://github.com/Rewrite-Team/Rewrite-BE/issues/50), [#52](https://github.com/Rewrite-Team/Rewrite-BE/issues/52), [#54](https://github.com/Rewrite-Team/Rewrite-BE/issues/54), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#88](https://github.com/Rewrite-Team/Rewrite-BE/issues/88), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | `ReviewVersionRepositoryTest`, `ReviewJobQuestionResultRepositoryTest`, `ReviewVersionServiceTest`, `ReviewVersionQueryServiceTest`, `ReviewVersionCommandServiceTest`, `ReviewVersionControllerTest`, `OpenAiReviewClientTest`, `FirstReviewJobWorkerTest`, `ReReviewJobWorkerTest`, `FirstReviewJobEventIntegrationTest`, `./gradlew test`, `./gradlew check` | API-017은 성공 버전을 `createdAt` 오름차순으로 반환하도록 구현·계약 변경. API-019 `success` 응답과 `versionId` 동시성 검증, API-024 표시 상태·Job ID 응답과 동일 재첨삭 중복 요청의 기존 Job 반환 계약을 확정했으며 API-018/019/024 구현 변경 필요 |
| REQ-007 | DB/JPA 전환 | Implemented | High | API-008 내부 persistence, persistence 내부 변경 | [#22](https://github.com/Rewrite-Team/Rewrite-BE/issues/22), [#24](https://github.com/Rewrite-Team/Rewrite-BE/issues/24), [#25](https://github.com/Rewrite-Team/Rewrite-BE/issues/25), [PR #26](https://github.com/Rewrite-Team/Rewrite-BE/pull/26) | `CoverLetterRepositoryTest`, `CoverLetterServiceTest`, `CoverLetterControllerTest`, `./gradlew test`, `./gradlew check` | API-008 공개 계약은 유지하고 `cover_letters` persistence를 DB/JPA로 전환. H2 file 로컬 DB, H2 in-memory 테스트 DB 사용. Flyway는 별도 이슈로 분리 |
| REQ-008 | 실제 인증 경계 | Planned | Medium | API-001 - API-006 | - | - | API-001·002 로그인 오류 redirect, API-003 인증 없는 CSRF 발급, API-004 single-flight 갱신과 API-006 멱등 로그아웃 오류 계약 확정 |
| REQ-009 | 키워드 분석 | In Progress | Medium | API-016, API-020, API-021 | [#56](https://github.com/Rewrite-Team/Rewrite-BE/issues/56), [#58](https://github.com/Rewrite-Team/Rewrite-BE/issues/58), [#60](https://github.com/Rewrite-Team/Rewrite-BE/issues/60), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | `KeywordAnalysisRepositoryTest`, `KeywordAnalysisKeywordRepositoryTest`, `KeywordAnalysisServiceTest`, `KeywordAnalysisControllerTest`, `OpenAiKeywordAnalysisClientTest`, `KeywordAnalysisJobWorkerTest`, `KeywordAnalysisJobEventIntegrationTest`, `LlmJobRepositoryTest`, `./gradlew test`, `./gradlew check` | API-020/021 기존 구현 완료. 최신 버전 자동 선택과 기존 Job 반환, 공통 SSE 연결, API-021 조건부 `jobId`와 polling fallback 계약을 확정했으며 구현 변경 필요 |
| REQ-010 | AI 면접 | In Progress | Medium | API-016, API-022, API-023, API-025 - API-027, API-029 | [#62](https://github.com/Rewrite-Team/Rewrite-BE/issues/62), [#64](https://github.com/Rewrite-Team/Rewrite-BE/issues/64), [#66](https://github.com/Rewrite-Team/Rewrite-BE/issues/66), [#68](https://github.com/Rewrite-Team/Rewrite-BE/issues/68), [#70](https://github.com/Rewrite-Team/Rewrite-BE/issues/70), [#72](https://github.com/Rewrite-Team/Rewrite-BE/issues/72), [#74](https://github.com/Rewrite-Team/Rewrite-BE/issues/74), [#76](https://github.com/Rewrite-Team/Rewrite-BE/issues/76), [#78](https://github.com/Rewrite-Team/Rewrite-BE/issues/78), [#80](https://github.com/Rewrite-Team/Rewrite-BE/issues/80), [#82](https://github.com/Rewrite-Team/Rewrite-BE/issues/82), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | `InterviewSessionRepositoryTest`, `InterviewQuestionRepositoryTest`, `InterviewThreadRepositoryTest`, `InterviewMessageRepositoryTest`, `InterviewServiceTest`, `InterviewMessageServiceTest`, `InterviewControllerTest`, `InterviewMessageControllerTest`, `OpenAiInterviewQuestionGenerationClientTest`, `OpenAiInterviewMessageFeedbackClientTest`, `InterviewQuestionGenerationJobWorkerTest`, `InterviewQuestionGenerationJobEventIntegrationTest`, `InterviewMessageFeedbackJobWorkerTest`, `InterviewMessageFeedbackJobEventIntegrationTest`, `LlmJobRepositoryTest`, `./gradlew test`, `./gradlew check` | API-026 최신 질문 우선 cursor 무한 스크롤 구현 완료. API-022·023·025·027의 단순 응답 및 SSE 복구 계약과 API-029 표시 문장·점수·조건부 `jobId` 계약은 구현 변경 필요. API-025는 화면 표시용 자기소개서 요약을 함께 반환한다. API-023은 고정 `status`를 제거하고 메시지·Job ID만 반환한다. |

## Open Maintenance Work

제품 기능 요구사항은 아니지만 현재 열려 있는 저장소 운영/문서/설정 작업이다.

| Issue | Scope | Status | Notes |
|---|---|---|---|
| [#16](https://github.com/Rewrite-Team/Rewrite-BE/issues/16) | Backend 문서 구조 및 개발 규칙 정리 | Open | 현재 문서 구조와 작업 규칙 정리 범위 |
| [#46](https://github.com/Rewrite-Team/Rewrite-BE/issues/46) | Codecov 및 PR CI 리포트 개선 | Open | Codecov informational status, JaCoCo 수치와 Codecov 상세 링크를 포함한 고정 CI 댓글 구성 |
| [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84) | AI 첨삭 실시간 조회 및 키워드 화면 API 계약 개편 | In Progress | 문서 계약 확정 후 구현 이슈를 작은 단위로 분리 |
| [#86](https://github.com/Rewrite-Team/Rewrite-BE/issues/86) | AI 개발 하네스 및 검증 루프 고도화 | In Progress | 단순한 개발 원칙, 위험도 기반 독립 리뷰, 변경 영향 완결성과 Issue·Commit·PR 품질 기준 정리 |

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
| 자기소개서·버전 공통 상세 조회 | REQ-003, REQ-006 | API-012, API-018 | 상태 공통 상세 모델과 선택 버전 상세 응답 구현 |
| 공통 Job SSE와 도메인 이벤트 | REQ-005, REQ-006, REQ-009, REQ-010 | API-016 | 동일 구조의 `job.state`, 배열 upsert 방식 `review.questions`, 검증된 면접 피드백의 `sequence` 기반 점진 전송·메모리 replay 구현 |
| 메인 첨삭 상태 실시간 갱신 | REQ-003, REQ-005 | API-007, API-030 | `displayStatus` 목록 필터·응답과 전체 스냅샷·단건 변경 SSE 구현 |
| 키워드 화면 메타데이터와 Job 복구 응답 | REQ-009 | API-021 | 자기소개서·분석 기준 버전 요약과 조건부 `jobId` 추가 |

## Update Rules

- 기능 구현을 시작하면 관련 feature status를 `In Progress`로 바꾼다.
- 코드 구현이 끝났지만 검증/리뷰가 끝나지 않았으면 `Implemented`로 둔다.
- 관련 검증이 통과하고 리뷰 또는 사용자 승인 중 하나가 확인된 뒤에만 `Verified`로 바꾼다. 테스트가 적용되지 않는 변경은 대체 검증 근거를 기록한다.
- `Implemented` 또는 `Verified`로 변경할 때는 `Verification Evidence`에 실행한 검증과 리뷰 또는 사용자 승인 근거를 기록한다.
- API 상태 변경은 `docs/api/README.md`의 `API Status`도 함께 갱신한다.
- 기능 상태를 추적하는 issue가 생성되면 관련 항목의 `Related Issue/PR`에 번호나 링크를 기록한다. 단순 bug, chore, 문서 전달 작업과 PR 번호는 필요할 때만 기록한다.
- 요구사항, API 계약, 설계 결정이 바뀌면 `docs/README.md`의 Change Impact Matrix에 따라 관련 문서를 함께 갱신한다.
- 다음 개발 후보가 완료되었거나 더 이상 적절하지 않으면 `Next Issue Slice Candidates`를 정리한다.
