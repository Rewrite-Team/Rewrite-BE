## Project context

- 이 저장소는 Rewrite 서비스의 Java Spring Boot 백엔드 프로젝트다.
- 팀 작업용 저장소이므로 기존 문서, GitHub 템플릿, 테스트 기준을 우선한다.
- 문서 라우팅은 `docs/README.md`를 먼저 확인한다.
- 개발 진행 상태와 다음 작업은 `docs/status.md`를 기준으로 확인한다.
- 주요 제품/기능 요구사항은 `docs/requirements.md`를 기준으로 확인한다.
- API 계약과 구현 상태는 `docs/api/README.md`와 관련 도메인별 API 문서를 기준으로 확인한다.
- API 설계 결정과 트레이드오프는 `docs/decisions/README.md`와 관련 도메인별 결정 문서를 참고한다.

## Default workflow

- 작업 전 `docs/README.md`를 기준으로 필요한 문서만 읽고, 변경 범위를 짧게 정리한 뒤 진행한다.
- 사용자가 명시적으로 요청하지 않은 commit, branch 생성, push, PR 생성은 하지 않는다.
- 기존 패턴을 우선하고, 불필요한 새 추상화나 범위 밖 리팩터링은 피한다.
- 기능, 기획, API, 설계 결정, 아키텍처, 테스트 기준을 변경하면 `docs/README.md`의 Change Impact Matrix에 따라 관련 문서를 함께 갱신한다.
- 문서와 코드가 충돌하면 충돌 내용을 사용자에게 알리고, 어느 쪽을 기준으로 할지 확인한다.

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

- API를 추가하거나 변경하면 `docs/api/README.md`와 `docs/api/` 하위 관련 도메인 문서를 갱신한다.
- API 변경이 요구사항, 상태, 설계 결정에 영향을 주면 `docs/requirements.md`, `docs/status.md`, `docs/decisions/` 하위 관련 문서도 함께 갱신한다.
- API 상태는 `Planned`, `In Progress`, `Implemented`, `Verified`, `Deprecated` 중 하나로 관리한다.
- 요청 형식, 성공 응답, 에러 응답, validation, 관련 requirement ID를 함께 기록한다.

## Progress tracking rules

- 기능 진행 상태의 기준 문서는 `docs/status.md`다.
- API 진행 상태의 기준 문서는 `docs/api/README.md`다.
- 개발 이슈 범위는 요청 시점의 문서 상태와 실제 코드/테스트 상태를 확인한 뒤 정한다.
- 하나의 개발 이슈와 PR은 독립적으로 구현, 리뷰, 검증할 수 있는 작은 단위로 나눈다.
- 서로 다른 기능, 큰 리팩터링, 인프라 변경, 문서 정리는 가능한 한 별도 이슈와 PR로 분리한다.
- 이슈를 만들 때 가능한 경우 관련 requirement ID와 API ID를 연결한다.
- PR을 만들 때 관련 requirement ID, API ID, issue 번호를 본문에 포함한다.
- 테스트 통과와 리뷰 또는 사용자 승인이 확인되지 않은 기능은 `Verified`로 표시하지 않는다.
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
