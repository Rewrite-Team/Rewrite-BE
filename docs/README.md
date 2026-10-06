# 문서 안내

이 문서는 기준 문서의 역할과 상세 작업 규칙을 관리한다.
상세 기준은 [작업 원칙](#작업-원칙)·[API와 오류](#api와-오류)·[문서 작성](#문서-작성)·[Git·GitHub와 승인](#gitgithub와-승인) 중 필요한 섹션만 확인한다.

## 기준 문서

| 문서 | 관리 대상 |
|---|---|
| [requirements](requirements.md) | 제품 동작·화면 흐름·REQ 수용 기준 |
| [API 안내](api/README.md) | 인증/Job·자기소개서·면접 보완 흐름 |
| [erd](erd.md) | 테이블·관계·nullable/FK·soft delete·persistence 정책 |
| [decisions](decisions/README.md) | 설계 이유·대안·트레이드오프·결정 이력 |
| [deployment](deployment.md) | OCI 구성·수동 배포·복구·운영 검증 |

- HTTP 계약은 Swagger UI와 생성 OpenAPI를 기준으로 사용한다.
- API 보완 문서는 여러 API의 화면·상태·복구 흐름을 관리하고 HTTP 예시·schema를 중복하지 않는다.
- 제품 범위·설계 근거·영속 구조는 각각 요구사항·결정·ERD에서 관리한다.

## 작업 원칙

- 기본 규칙은 [AGENTS.md](../AGENTS.md#기본-규칙)를 따른다.
- 요청의 해결책도 기존 패턴의 더 단순한 대안과 비교한다.
- 답변을 기다리는 동안에는 답변에 의존하지 않는 조사·검증을 진행한다.
- 작은 구현 세부사항은 기존 패턴에 따라 판단한다.
- 새 기능·추상화·설정·fallback·retry는 요구사항·결정·구체적 실패 시나리오가 있을 때만 추가한다.
- 범위 밖 문제는 보고하고 이번 변경으로 불필요해진 코드·import만 정리한다.
- OpenAPI와 문서가 충돌하면 실제 구현과 승인된 제품·설계 결정을 대조해 정정한다.

## API와 오류

- API path에는 `/api` prefix를 붙이지 않는다.
- Content-Type은 기본적으로 `application/json`을 사용한다.
- 스트리밍 API는 SSE를 사용한다.
- 날짜/시간 응답은 `Asia/Seoul` 기준 `LocalDateTime` 문자열을 사용한다.
- 인증이 필요한 리소스는 현재 로그인 사용자의 소유자인지 검증한다.
- 리소스가 없거나 현재 사용자의 소유가 아니면 사용자-facing API에서는 `NOT_FOUND`를 반환한다.

### Error Handling

- 예측 가능한 비즈니스 오류는 `BusinessException`과 `ErrorCode`로 표현한다.
- API 에러 응답은 `ErrorResponse` 형식을 사용한다.
- validation 실패는 `VALIDATION_ERROR`로 응답한다.
- provider 원문 에러 메시지를 사용자-facing 응답에 그대로 노출하지 않는다.
- 다른 사용자의 리소스 존재 여부를 노출하지 않는다.
- 예외는 실제 복구, 상태 전이, 사용자 응답 변환 또는 경계 로깅 책임이 있는 계층에서만 처리한다.
- 가능한 한 복구하거나 분류할 수 있는 구체적인 예외를 잡고, 예상하지 못한 오류를 provider 오류나 비즈니스 오류로 오분류하지 않는다.
- 예상하지 못한 오류는 사용자-facing 응답에서 내부 정보를 숨기되 원인과 운영 로그를 보존한다.
- 요구사항, 설계 결정이나 재현 가능한 실패 시나리오가 없는 fallback과 retry를 추가하지 않는다.
- 같은 책임의 검증을 여러 계층에서 불필요하게 반복하지 않는다.
- 외부 입력·provider 응답 경계의 계약과 영속화할 도메인 불변식은 각 경계에서 독립적으로 보장한다.

### OpenAPI / Swagger

- Swagger UI를 개발자가 보는 핵심 API 문서로 사용하고, controller·DTO·`@RewriteApi`에서 코드 우선 OpenAPI 명세를 생성한다.
- 명세 우선 방식으로 server stub을 생성하지 않는다.
- `@RewriteApi` 설명은 사용 목적, 사용 화면, 호출 시점, 주요 동작, 성공 후 처리, 오류 순서로 작성한다.
- 설명에서 내부 persistence·transaction·worker 구조는 제외한다.
- summary는 `API-009 · 기본 정보 저장` 형식으로 작성한다.
- API ID는 `x-rewrite-api-id`에도 유지한다.
- `operationId`는 고유한 의미 기반 camelCase 이름을 사용한다.
- tag는 공백 없는 영문 도메인 이름으로 작성한다.
- 실제 JSON에 항상 존재하는 속성은 응답 스키마에서 `required`로 표시한다.
- 값이 `null`일 수 있는 속성은 required와 nullable을 함께 표시한다.
- `@ApiError`에는 실제 발생 가능한 오류 코드·발생 조건·프론트엔드 처리 방법만 기록한다.
- 같은 HTTP 상태의 복수 오류는 named example로 구분한다.
- `VALIDATION_ERROR`의 대표 detail은 실제 서비스가 반환할 수 있는 field와 reason을 함께 기록한다.
- OAuth redirect 오류는 JSON `ErrorResponse`로 문서화하지 않고 `@ApiRedirectError`로 구분한다.
- 인증 API를 포함한 운영 profile OpenAPI는 활성 API 29개를 모두 포함해야 하고, API-028은 Deprecated path로 노출하지 않는다.
- 공통 `401`, `403`, `500`과 API별 오류는 `ErrorResponse` schema를 사용하고, 비동기 Job 실패는 HTTP 오류와 분리한다.
- `x-rewrite-*` 확장은 Swagger 설명에 사용할 구조화된 메타데이터로 유지한다.
- `api/`는 라우팅·교차 API 흐름을 보완한다.
- 명세와 controller·DTO·오류의 일치는 OpenAPI 통합 테스트로 확인한다.
- Springdoc/OpenAPI 의존성 추가는 별도 이슈 또는 명시적인 사용자 요청으로 진행한다.
- Springdoc/OpenAPI 설정 추가도 같은 기준을 따른다.

## 문서 작성

- 적용 대상·조건·행동·결과를 명확히 쓴다.
- 조건·예외가 있으면 문장이나 항목 앞에 쓴다.
- 한 문장에는 하나의 규칙을 쓴다.
- 여러 규칙·절차·상태는 세로 목록으로 나눈다.
- 비교·대응 관계는 표로 정리한다.
- 불필요한 `A가 아니라 B` 대조는 적용되는 동작·규칙을 직접 쓰는 문장으로 바꾼다.
- 범위·안전·승인 구분에 필요한 금지·제한은 유지한다.
- 대상이 불명확하면 화면·주체·설정 이름을 명시한다.
- 판단에 도움이 없는 설명·반복은 줄이고 상세 규칙은 기준 문서로 연결한다.
- 표현을 줄이거나 나눌 때 수치·조건·예외·설계 상태·승인·권한의 의미를 보존한다.
- 제안·확정·구현·검증 완료를 구분하고 미확정 사항을 확인된 동작처럼 쓰지 않는다.
- 같은 조건에서 측정한 수치만 비교하고 측정하지 않은 항목은 미측정으로 표시한다.

## Git·GitHub와 승인

- 명시적으로 요청하지 않은 branch 생성·commit·push·issue·PR 생성은 하지 않는다.
- 기존 issue·PR 제목과 branch 형식을 유지하고 본문은 해당 템플릿의 필요한 항목만 작성한다.
- commit 제목은 `:{emoji}: type: 한글 요약`을 유지한다. 예: `:sparkles: feat: 공통 에러 응답 형식 구현`
- 제목만으로 이유가 불분명하면 빈 줄 뒤에 이유·핵심 내용을 body로 쓴다.
- issue·PR·commit은 제목·본문 전체 초안을 보여주고 별도 명시적 승인 후 생성한다.
- 생성 요청·범위 확인은 전체 초안 승인을 대신하지 않으며 승인된 동일 초안은 다시 승인받지 않는다.
- 요청이 모호하면 Git/GitHub 상태를 바꾸기 전에 확인한다.
- 하나의 issue·PR·commit은 일관된 작은 구현·리뷰·검증 단위로 유지한다.
- 다른 기능·큰 리팩터링·인프라·문서 정리는 가능한 한 별도 issue·PR로 나눈다.

### 이슈용 브랜치

- 브랜치 생성 요청을 받으면 `gh issue develop`로 GitHub에 브랜치를 생성하고 해당 이슈의 Development 항목에 연결한다.
- 시작 브랜치를 별도로 지정하지 않으면 원격 `dev`를 사용하고, 이름은 기존 `<종류>/#<이슈 번호>` 형식을 유지한다.
- 로컬에 같은 이름의 브랜치가 없으면 다음 명령으로 생성·연결한 원격 브랜치를 체크아웃한다.

```bash
gh issue develop <이슈 번호> --base dev --name '<브랜치 이름>' --checkout
```

- 같은 이름의 로컬 브랜치가 이미 있으면 `--checkout`을 생략해 GitHub 생성·연결을 먼저 수행한다. 이후 원격 브랜치를 fetch하고 기존 로컬 브랜치의 upstream으로 설정한 뒤 해당 브랜치로 전환하며 사용자 변경을 보존한다.
- 생성 후 `gh issue develop <이슈 번호> --list`로 이슈 연결을 확인하고 로컬 upstream이 해당 원격 브랜치인지 확인한다.
- PR 병합 후 브랜치 자동 삭제는 저장소의 `Automatically delete head branches` 설정에 따른다. 이슈 연결만으로 자동 삭제를 보장하지 않는다. 상세 조건은 [GitHub 안내](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/configuring-pull-request-merges/managing-the-automatic-deletion-of-branches)를 따른다.

### 이슈·PR·commit 작성

| 작업 | 초안 전 확인 |
|---|---|
| 이슈 | 실제 코드·테스트·관련 REQ/API/Decision, `.github/ISSUE_TEMPLATE/` |
| PR | `.github/pull_request_template.md`, 브랜치·최종 diff·관련 issue |
| commit | 브랜치·diff, 하나의 일관된 변경 범위와 메시지의 일치 |

- 이슈는 목적·대상·수행 내용·완료 기준을 사용자와 확정하고 템플릿 선택이 모호하면 확인한다.
- 명확한 요청은 요약·초안을 함께 제시하고 이미 지정·확인한 내용·작은 작성 세부사항은 반복 질문하지 않는다.
- 이슈에는 할 작업·기대 결과·확인 가능한 완료 기준을 쓴다.
- 조사·설계 이슈에는 질문·결과물·결정할 사항을 명시한다.
- 이슈에는 필요한 조건·범위·상태와 실제 관련 REQ/API/Decision·issue를 명시한다.
- PR에는 최종 diff의 실제 변경·변경 후 동작을 쓴다.
- PR의 기본 항목은 작업 내용과 관련 이슈다.
- PR 본문에는 REQ/API/Decision ID를 나열하지 않는다.
- 문서·오탈자·링크 정리 PR에는 설계·영향·제외 범위·남은 위험을 추가하지 않는다.
- 문서·오탈자·링크 정리 PR에는 문서 반영 보고·확인 결과·검증 명령·검증 생략 이유를 추가하지 않는다.
- 코드·공개 계약·데이터 변경 PR에는 리뷰 판단에 필요한 설계 선택·영향·검증 내용만 짧게 덧붙인다.
- PR에서는 해당하지 않는 항목을 생략하고 빈 항목이나 `없음`·`해당 없음`으로 채우지 않는다.
- 반복·대화 이력·작업 과정 나열은 생략한다.
- 범위가 바뀌면 사용자와 확정하고 제목·본문을 최종 결과에 맞춘다.
- 무관한 변경이 섞이면 분리 필요성을 설명한다.

## 지침 갱신

- 구조·명령·절차 변경이나 반복 오류가 있으면 코드·설정·검증·사용자 결정에 따라 완료 전 갱신한다.
- 일회성 진행 상황·미확정 설계를 상시 규칙으로 만들지 않는다.
- 상세 기준은 해당 문서에서 관리하고 낡은 중복·경로·명령·상하위 지침 충돌을 확인한다.
- 승인·권한 정책 변경은 먼저 사용자에게 확인한다.
