## Project context

- 이 저장소는 Rewrite 서비스의 Java Spring Boot 백엔드 프로젝트다.
- 팀 작업용 저장소이므로 기존 문서, GitHub 템플릿, 테스트 기준을 우선한다.
- 문서 라우팅은 `docs/README.md`를 먼저 확인한다.
- 개발 진행 상태와 다음 작업은 `docs/status.md`를 기준으로 확인한다.
- 주요 제품/기능 요구사항은 `docs/requirements.md`를 기준으로 확인한다.
- API 계약과 구현 상태는 `docs/api/README.md`와 관련 도메인별 API 문서를 기준으로 확인한다.
- API와 persistence 설계 결정 및 트레이드오프는 `docs/decisions/README.md`와 관련 결정 문서를 참고한다.

## Default workflow

- 작업 전 `docs/README.md`를 기준으로 필요한 문서만 읽고, 변경 범위를 짧게 정리한 뒤 진행한다.
- 사용자가 명시적으로 요청하지 않은 commit, branch 생성, push, PR 생성은 하지 않는다.
- issue, PR, commit, branch, push, 진행 현황 보고처럼 Codex 작업 절차와 관련된 요청은 `docs/codex-workflow.md`를 먼저 확인한다.
- 기존 패턴을 우선하고, 불필요한 새 추상화나 범위 밖 리팩터링은 피한다.
- 기능, 기획, API, 설계 결정, 아키텍처, 테스트 기준을 변경하면 `docs/README.md`의 Change Impact Matrix에 따라 관련 문서를 함께 갱신한다.
- 문서와 코드가 충돌하면 충돌 내용을 사용자에게 알리고, 어느 쪽을 기준으로 할지 확인한다.

## Codex engineering principles

- Think Before Coding: 코드 작성 전에 사용자의 실제 목표, 현재 상태, 가정, 불확실성과 완료 조건을 정리한다. 요청에 포함된 해결책도 하나의 가설로 보고 더 단순하거나 안전한 대안이 있는지 검토한다. 제품 동작, 공개 계약 또는 작업 범위를 의미 있게 바꿀 수 있는 모호성은 가능한 해석과 trade-off를 제시하고 사용자에게 확인한다. 결과에 영향을 주지 않는 작은 구현 세부사항은 기존 패턴과 가장 단순한 선택을 기준으로 판단한다.
- Simplicity First: 확인된 요구사항과 완료 조건을 충족하는 가장 단순하고 명확한 접근을 우선한다. 요청이나 계약에 없는 기능, 추상화, 설정화, fallback, retry 또는 미래 확장성을 추가하지 않는다. 신뢰성, 보안 또는 데이터 정합성을 위한 처리는 관련 요구사항, 설계 결정이나 구체적인 실패 시나리오를 근거로 추가한다.
- Surgical Changes: 변경은 요청과 확인된 영향 범위 안으로 제한한다. 관련 없는 리팩터링, 스타일 변경, 주석 수정, 포맷팅, 파일 재구성 또는 동작 변경을 섞지 않는다. 범위 밖 문제는 임의로 수정하지 않고 별도로 보고하며, 이번 변경으로 새롭게 사용되지 않게 된 코드와 import만 정리한다.
- Goal-Driven Execution: 넓거나 추상적인 요청을 검증 가능한 목표와 수용 기준으로 변환한 뒤 작업한다. 구현 활동 자체가 아니라 사용자 흐름, 보존할 동작, 관련 테스트, 문서 반영과 검증 근거를 기준으로 완료를 판단한다.
- Change Completeness: 구현 전에 코드, 테스트, API 계약, 요구사항, 설계 결정, 상태 문서와 설정에 대한 변경 영향을 확인한다. 구현 후에는 예상 영향 범위와 실제 diff를 대조하고, 식별된 각 영향 대상을 반영하거나 반영하지 않은 이유를 근거와 함께 기록한다.
- Risk-Based Review: 메인 Codex가 작업 전체와 파일 수정을 책임진다. 위험도나 판단 불확실성이 높은 작업에서만 읽기 전용 서브 에이전트를 독립 리뷰어로 사용하고, 여러 에이전트가 같은 작업 트리를 동시에 수정하게 하지 않는다. 세부 기준은 `docs/codex-workflow.md`를 따른다.

## Architecture rules

- 기능 중심 패키지 구조를 사용한다.
- 전역 `controller`, `service`, `repository` 패키지를 만들지 않는다.
- 기능 패키지 안에 필요한 경우 `controller`, `service`, `repository`, `entity`, `dto`, `client`, `config` 하위 패키지를 둔다.
- 현재 저장소의 공통 패키지는 `global`을 사용한다. 공통 예외, 공통 응답, 공통 설정처럼 횡단 관심사만 둔다.
- 비즈니스 로직은 controller가 아니라 service에 둔다.
- API 요청/응답에는 명시적인 DTO를 사용하고, persistence/entity 모델을 직접 노출하지 않는다.
- transaction boundary는 service 계층에 둔다.

## Build and test

- 일반 변경 검증은 `./gradlew test`를 실행한다.
- 큰 변경이나 완료 전 검증은 `./gradlew check` 또는 프로젝트에 정의된 동등한 검증 명령을 실행한다.
- 새 동작이나 변경된 동작에는 테스트를 추가하거나 갱신한다.
- 검증 명령을 실행하지 못했으면 최종 응답에 이유를 명확히 적는다.

## API documentation rules

- API와 관련된 설계, 구현, 수정, 리뷰 또는 문서화 작업을 시작할 때마다 코드나 저장소 문서를 변경하기 전에 관련 `API ID`의 Notion `Rewrite API (자동 동기화)` 페이지를 먼저 조회한다. 새 API는 사용할 `API ID`로 기존 페이지 존재 여부를 먼저 확인한다. Notion 페이지를 조회할 수 없으면 작업을 진행하지 않고 사용자에게 알린다.
- API를 추가하거나 변경하면 `docs/api/README.md`와 `docs/api/` 하위 관련 도메인 문서를 갱신한다.
- Git 저장소의 Markdown 문서와 실제 코드는 API 계약과 구현 상태의 기준 원본이다.
- API를 추가, 삭제하거나 path, request, response, error, validation, 상태 또는 구현 여부를 변경하면 같은 작업에서 Notion `Rewrite API (자동 동기화)` 데이터베이스도 갱신한다.
- Notion은 프론트엔드 개발자가 보는 동기화 문서이며, `API ID`를 고유 키로 사용한다. Notion에서 직접 수정한 내용은 저장소로 역동기화하지 않는다.
- Notion 페이지에는 프론트엔드 구현에 영향을 주는 호출 방법, 요청·응답, validation, 오류 처리, 화면 상태·polling·SSE·redirect·Cookie·CSRF만 기록한다. 내부 Service, Repository, Entity, DB, transaction, worker 구조는 기록하지 않는다.
- Notion의 Request와 Success Response에는 필드 경로, 타입, 필수 여부, nullable 조건과 설명을 표로 기록한다. 배열 내부 필드는 `items[].id`처럼 펼친다.
- Notion의 Error Handling은 HTTP 상태, 오류 코드, 발생 조건과 프론트엔드 처리 방법을 표로 기록한다.
- 오류 코드가 계약에 정의되지 않았으면 빈 값, `-`, `계약 참조`를 사용하지 않고 `오류 코드 미정` 또는 `API별 오류 없음`으로 명시한다. 비동기 Job 실패 상태를 HTTP 오류 응답과 혼합하지 않는다.
- Notion 갱신 절차와 검증 기준은 `docs/codex-workflow.md`를 따른다. 동기화에 실패하면 저장소 문서 검증과 Notion 동기화 실패를 분리해 보고한다.
- API 변경이 요구사항, 상태, 설계 결정에 영향을 주면 `docs/requirements.md`, `docs/status.md`, `docs/decisions/` 하위 관련 문서도 함께 갱신한다.

## Progress tracking rules

- 기능 진행 상태의 기준 문서는 `docs/status.md`다.
- API 진행 상태의 기준 문서는 `docs/api/README.md`다.
- 개발 이슈 범위는 요청 시점의 문서 상태와 실제 코드/테스트 상태를 확인한 뒤 정한다.
- 하나의 개발 이슈와 PR은 독립적으로 구현, 리뷰, 검증할 수 있는 작은 단위로 나눈다.
- 서로 다른 기능, 큰 리팩터링, 인프라 변경, 문서 정리는 가능한 한 별도 이슈와 PR로 분리한다.
- 이슈를 만들 때 가능한 경우 관련 requirement ID, API ID와 Decision ID를 연결한다.
- PR을 만들 때 관련 requirement ID, API ID, Decision ID와 issue 번호를 본문에 포함한다.
- 관련 검증이 통과하고 리뷰 또는 사용자 승인 중 하나가 확인된 기능만 `Verified`로 표시한다. 테스트가 적용되지 않는 변경은 대체 검증 근거를 기록한다.
- 상태를 `Implemented` 또는 `Verified`로 변경할 때는 `Verification Evidence`를 함께 갱신한다.
- 진행 현황을 요청받으면 문서 상태와 실제 코드/테스트 확인 결과를 분리해서 보고한다.

## GitHub issue and pull request rules

- GitHub issue 생성 요청을 받으면 먼저 `.github/ISSUE_TEMPLATE/` 하위 템플릿을 확인한다.
- 요청과 가장 잘 맞는 issue 템플릿을 선택하되, 애매하면 사용자에게 어떤 템플릿을 사용할지 확인한다.
- issue 제목과 본문 전체 초안을 사용자에게 보여준다.
- 사용자가 명시적으로 승인하기 전에는 issue를 생성하지 않는다.
- GitHub PR 생성 요청을 받으면 먼저 `.github/pull_request_template.md`를 확인한다.
- 현재 브랜치, 변경 파일, 관련 이슈를 확인한 뒤 PR 제목과 본문 전체 초안을 사용자에게 보여준다.
- 사용자가 명시적으로 승인하기 전에는 PR을 생성하지 않는다.

## OpenAPI / Swagger management

- 현재 프로젝트의 API 계약 기준은 `docs/api/README.md`와 `docs/api/` 하위 도메인 문서다.
- Swagger/OpenAPI는 실제 구현된 controller/DTO 기준의 확인 문서로만 사용한다.
- 명세 우선 방식으로 server stub을 생성하지 않는다. 코드 우선 생성과 Markdown 계약 문서를 병행한다.
- OpenAPI annotation을 추가하는 경우 controller 동작, DTO, 에러 응답과 일치해야 한다.
- Springdoc/OpenAPI 의존성이나 설정을 추가하는 경우 별도 이슈 또는 명시적 사용자 요청에 따라 진행한다.
