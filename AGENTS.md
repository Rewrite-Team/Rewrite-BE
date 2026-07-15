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
- API를 추가, 삭제하거나 path, request, response, error, validation, 상태 또는 구현 여부를 변경하면 같은 작업에서 `docs/api/rewrite-api-documentation.xlsx`도 반드시 갱신한다.
- `docs/api/rewrite-api-documentation.xlsx`는 기준 원본이고, Google Drive 파일 `15eS_Q4x5kAZHkQhkwNFk08Zt3wCkxo8W`는 공유용 미러다.
- Excel API 문서를 로컬에서 갱신하고 검증한 뒤에는 Google Drive의 기존 Excel 파일에 바이트를 덮어써서 같은 파일 ID와 URL을 유지한다. 새 Drive 파일이나 네이티브 Google Sheet를 만들지 않는다.
- Drive 동기화 후 파일 ID, Excel MIME type, 수정 시각을 다시 확인한다. 인증 또는 업로드 실패 시 동기화 완료로 보고하지 않고 최종 응답에 실패 이유를 명시한다.
- Excel API 문서는 프론트엔드 구현과 디버깅에 필요한 정보만 유지한다. API별 기능, 실제 요청·응답 예시, 요청·응답 필드의 의미와 제약, 정확한 HTTP 상태와 오류 코드, 인증/CSRF 요구사항, 구현 여부를 포함하고 소스 경로·중복 예시·내부 설계 설명은 제외한다.
- Excel API 문서의 목차와 각 API 상세 시트에는 `Asia/Seoul` 기준 `최근 변경일`과 `최근 변경 시각`을 기록하고, API 계약이나 문서 내용이 바뀐 작업에서 해당 시각을 갱신한다.
- Excel API 문서의 첫 시트는 전체 API 목차로 유지하고, 이후에는 API ID별로 하나의 상세 시트를 사용한다. 여러 API를 한 상세 시트에 합치지 않는다.
- 첫 시트의 API ID와 `상세 보기`는 해당 API 상세 시트로 이동하는 내부 링크를 제공하고, 각 상세 시트 상단에는 첫 시트로 돌아가는 내부 링크를 유지한다.
- Excel API 문서는 회색 계열을 기본 색상으로 사용하고, 표 본문에 행별 줄무늬 색상이나 상태별 컬러 배경을 사용하지 않는다. 제목, 섹션 헤더, 표 헤더와 코드 예시는 회색의 명도 차이로만 구분한다.
- 각 API 상세 시트에는 필드 설명과 별도로 실제 HTTP 요청 형식과 성공 응답 형식을 코드 예시로 유지한다. path/query/header/cookie/body, HTTP status, content type과 JSON 또는 SSE 본문이 계약과 일치해야 한다.
- 오류 표의 HTTP 열에는 `4xx/5xx`, `요청 검증` 같은 범주를 쓰지 않고 `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`처럼 실제 상태를 기록한다. validation 제약은 요청 필드 표에 한 번만 기록하고 오류 표에는 `VALIDATION_ERROR` 한 행만 둔다.
- 계약에는 있으나 아직 구현되지 않은 인증·CSRF 오류는 `계약 정의·미구현`으로 명시하여 현재 동작으로 오해하지 않게 한다.
- Excel API 문서의 인쇄 설정은 A4 가로, 너비 1페이지 맞춤으로 유지하고, HTTP 코드 예시는 한 행이 페이지 경계에서 잘리지 않도록 렌더링한다.
- Excel API 문서를 갱신한 뒤에는 모든 시트를 렌더링해 잘림과 가독성을 확인하고, 주요 범위와 수식 오류를 검증한다.
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
