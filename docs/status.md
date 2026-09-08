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
| REQ-003 | 자기소개서 기본 CRUD | Implemented | High | API-007, API-008, API-012, API-013, API-030 | [#5](https://github.com/Rewrite-Team/Rewrite-BE/issues/5), [#18](https://github.com/Rewrite-Team/Rewrite-BE/issues/18), [#28](https://github.com/Rewrite-Team/Rewrite-BE/issues/28), [#30](https://github.com/Rewrite-Team/Rewrite-BE/issues/30), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90), [#92](https://github.com/Rewrite-Team/Rewrite-BE/issues/92), [PR #15](https://github.com/Rewrite-Team/Rewrite-BE/pull/15) | API 계약 독립 리뷰, SSE 동시성 재검토, `./gradlew clean check` | API-012 공통 상세와 API-030 사용자 단일 SSE까지 구현했다. |
| REQ-004 | 자기소개서 등록 step 저장 | Implemented | High | API-009, API-010, API-011, API-012, API-014 | [#32](https://github.com/Rewrite-Team/Rewrite-BE/issues/32), [#34](https://github.com/Rewrite-Team/Rewrite-BE/issues/34), [#36](https://github.com/Rewrite-Team/Rewrite-BE/issues/36), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | API 계약 독립 리뷰, `./gradlew clean check` | nullable WRITING 임시저장, 제출 검증, API-012 미완성 데이터 복구를 구현했다. |
| REQ-005 | 자기소개서 제출과 LLM Job 생성 | Implemented | High | API-014, API-015, API-016 | [#38](https://github.com/Rewrite-Team/Rewrite-BE/issues/38), [#40](https://github.com/Rewrite-Team/Rewrite-BE/issues/40), [#42](https://github.com/Rewrite-Team/Rewrite-BE/issues/42), [#44](https://github.com/Rewrite-Team/Rewrite-BE/issues/44), [#48](https://github.com/Rewrite-Team/Rewrite-BE/issues/48), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#88](https://github.com/Rewrite-Team/Rewrite-BE/issues/88), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | SSE 동시성 독립 리뷰와 재검토, `./gradlew clean check` | API-015 경량 상태 조회와 API-016 공통 Job SSE를 구현했다. |
| REQ-006 | 첨삭 버전 조회와 최종 작성본 저장 | Implemented | High | API-017, API-018, API-019, API-024 | [#42](https://github.com/Rewrite-Team/Rewrite-BE/issues/42), [#44](https://github.com/Rewrite-Team/Rewrite-BE/issues/44), [#48](https://github.com/Rewrite-Team/Rewrite-BE/issues/48), [#50](https://github.com/Rewrite-Team/Rewrite-BE/issues/50), [#52](https://github.com/Rewrite-Team/Rewrite-BE/issues/52), [#54](https://github.com/Rewrite-Team/Rewrite-BE/issues/54), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#88](https://github.com/Rewrite-Team/Rewrite-BE/issues/88), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | API 계약 독립 리뷰, `./gradlew clean check` | API-018 공통 상세, 최종 답변 저장, 재첨삭 흐름을 구현했다. |
| REQ-007 | DB/JPA 전환 | Implemented | High | API-008 내부 persistence, persistence 내부 변경 | [#22](https://github.com/Rewrite-Team/Rewrite-BE/issues/22), [#24](https://github.com/Rewrite-Team/Rewrite-BE/issues/24), [#25](https://github.com/Rewrite-Team/Rewrite-BE/issues/25), [#108](https://github.com/Rewrite-Team/Rewrite-BE/issues/108), [PR #26](https://github.com/Rewrite-Team/Rewrite-BE/pull/26) | 기존 repository/controller 테스트, `./gradlew clean check` | API 계약을 유지하고 H2 local/test와 PostgreSQL prod profile을 분리했다. Flyway는 후속 이슈로 분리 |
| REQ-008 | 실제 인증 경계 | Implemented | Medium | API-001 - API-006 | [#94](https://github.com/Rewrite-Team/Rewrite-BE/issues/94), [#96](https://github.com/Rewrite-Team/Rewrite-BE/issues/96), [#98](https://github.com/Rewrite-Team/Rewrite-BE/issues/98) | 인증·CSRF 독립 리뷰, PR #97 병합, `AuthIntegrationTest`, `TokenRefreshControllerTest`, `LogoutControllerTest`, `RefreshTokenTest`, `CsrfTokenServiceTest`, `RestKakaoClientTest`, `KakaoLoginServiceTest`, `./gradlew clean check` | API-001~006을 모두 구현했다. API-006은 멱등 로그아웃과 유효 refresh token 폐기를 구현하고 refresh·logout 직렬화 계약을 반영했다. |
| REQ-009 | 키워드 분석 | Implemented | Medium | API-016, API-020, API-021 | [#56](https://github.com/Rewrite-Team/Rewrite-BE/issues/56), [#58](https://github.com/Rewrite-Team/Rewrite-BE/issues/58), [#60](https://github.com/Rewrite-Team/Rewrite-BE/issues/60), [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | API 계약·persistence 독립 리뷰, `./gradlew clean check` | 최신 성공 버전 기반 분석과 API-021 화면 메타데이터·Job 복구 응답을 구현했다. |
| REQ-010 | AI 면접 | Implemented | Medium | API-016, API-022, API-023, API-025 - API-027, API-029 | [#62](https://github.com/Rewrite-Team/Rewrite-BE/issues/62), [#64](https://github.com/Rewrite-Team/Rewrite-BE/issues/64), [#66](https://github.com/Rewrite-Team/Rewrite-BE/issues/66), [#68](https://github.com/Rewrite-Team/Rewrite-BE/issues/68), [#70](https://github.com/Rewrite-Team/Rewrite-BE/issues/70), [#72](https://github.com/Rewrite-Team/Rewrite-BE/issues/72), [#74](https://github.com/Rewrite-Team/Rewrite-BE/issues/74), [#76](https://github.com/Rewrite-Team/Rewrite-BE/issues/76), [#78](https://github.com/Rewrite-Team/Rewrite-BE/issues/78), [#80](https://github.com/Rewrite-Team/Rewrite-BE/issues/80), [#82](https://github.com/Rewrite-Team/Rewrite-BE/issues/82), [#90](https://github.com/Rewrite-Team/Rewrite-BE/issues/90) | API 계약·SSE·persistence 독립 리뷰, `./gradlew clean check` | 질문 생성, 대화, 피드백 delta와 API-025·029 Job 복구 응답까지 구현했다. |

## Open Maintenance Work

제품 기능 요구사항은 아니지만 현재 열려 있는 저장소 운영/문서/설정 작업이다.

| Issue | Scope | Status | Notes |
|---|---|---|---|
| [#16](https://github.com/Rewrite-Team/Rewrite-BE/issues/16) | Backend 문서 구조 및 개발 규칙 정리 | Open | 현재 문서 구조와 작업 규칙 정리 범위 |
| [#46](https://github.com/Rewrite-Team/Rewrite-BE/issues/46) | Codecov 및 PR CI 리포트 개선 | Open | Codecov informational status, JaCoCo 수치와 Codecov 상세 링크를 포함한 고정 CI 댓글 구성 |
| [#84](https://github.com/Rewrite-Team/Rewrite-BE/issues/84) | AI 첨삭 실시간 조회 및 키워드 화면 API 계약 개편 | In Progress | 문서 계약 확정 후 구현 이슈를 작은 단위로 분리 |
| [#86](https://github.com/Rewrite-Team/Rewrite-BE/issues/86) | AI 개발 하네스 및 검증 루프 고도화 | In Progress | 단순한 개발 원칙, 위험도 기반 독립 리뷰, 변경 영향 완결성과 Issue·Commit·PR 품질 기준 정리 |
| [#102](https://github.com/Rewrite-Team/Rewrite-BE/issues/102) | Swagger UI API 계약 문서화 기반 구축 | Verified | 활성 API 29개 `@RewriteApi`, 오류 example과 OAuth redirect 문서화 적용. 전수 coverage·운영 프로필 OpenAPI 테스트, Swagger UI 시각 검증, 독립 리뷰, `./gradlew check` 통과 |
| [#105](https://github.com/Rewrite-Team/Rewrite-BE/issues/105) | 코드 호출 흐름 시각화 스킬과 공통 뷰어 구축 | Verified | 2026-09-06: 저장소 스킬·공통 뷰어·API-003/020 예제 구현. Python 10개, 스킬 형식·소스 해시·Chrome 동작/시각 검증, 독립 사용 리뷰 및 수정 재검증 통과. `./gradlew test` 53개·`./gradlew check` 통과. 제품·API·TX 동작 변경 없음 |

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

REQ-008의 API-001~006을 모두 구현했다. API-006 리뷰 또는 사용자 승인까지 확인되면 REQ-008을 `Verified`로 전환한다.

## Update Rules

- 기능 구현을 시작하면 관련 feature status를 `In Progress`로 바꾼다.
- 코드 구현이 끝났지만 검증/리뷰가 끝나지 않았으면 `Implemented`로 둔다.
- 관련 검증이 통과하고 리뷰 또는 사용자 승인 중 하나가 확인된 뒤에만 `Verified`로 바꾼다. 테스트가 적용되지 않는 변경은 대체 검증 근거를 기록한다.
- `Implemented` 또는 `Verified`로 변경할 때는 `Verification Evidence`에 실행한 검증과 리뷰 또는 사용자 승인 근거를 기록한다.
- API 상태 변경은 `docs/api/README.md`의 `API Status`도 함께 갱신한다.
- 기능 상태를 추적하는 issue가 생성되면 관련 항목의 `Related Issue/PR`에 번호나 링크를 기록한다. 단순 bug, chore, 문서 전달 작업과 PR 번호는 필요할 때만 기록한다.
- 요구사항, API 계약, 설계 결정이 바뀌면 `docs/README.md`의 Change Impact Matrix에 따라 관련 문서를 함께 갱신한다.
- 다음 개발 후보가 완료되었거나 더 이상 적절하지 않으면 `Next Issue Slice Candidates`를 정리한다.
