# Conventions

## Java and Spring

- Java 21을 사용한다.
- Spring Boot 4 기반으로 구현한다.
- 의존성 주입은 constructor injection을 사용한다.
- field injection은 사용하지 않는다.
- Lombok 사용은 기존 프로젝트 설정을 따른다.

## Naming

- Controller class: `*Controller`
- Service class: `*Service`
- Repository class/interface: `*Repository`
- Request DTO: `*Request`
- Response DTO: `*Response`
- enum: 의미가 드러나는 단수형 이름을 사용한다.

## Package Naming

- 패키지는 소문자로 작성한다.
- 기능 패키지는 도메인/기능 이름을 기준으로 둔다.
- 여러 기능에서 공유하지 않는 코드는 `global`로 올리지 않는다.

## DTO Rules

- controller는 request/response DTO를 사용한다.
- entity, 내부 model, provider 원문 응답을 API 응답으로 직접 반환하지 않는다.
- request DTO에는 validation 기준을 명시한다.
- response DTO는 API 문서의 필드명과 일치해야 한다.

## API Conventions

- API path에는 `/api` prefix를 붙이지 않는다.
- Content-Type은 기본적으로 `application/json`을 사용한다.
- 스트리밍 API는 SSE를 사용한다.
- 날짜/시간 응답은 `Asia/Seoul` 기준 `LocalDateTime` 문자열을 사용한다.
- 인증이 필요한 리소스는 현재 로그인 사용자의 소유자인지 검증한다.
- 리소스가 없거나 현재 사용자의 소유가 아니면 사용자-facing API에서는 `NOT_FOUND`를 반환한다.

## Error Handling

- 예측 가능한 비즈니스 오류는 `BusinessException`과 `ErrorCode`로 표현한다.
- API 에러 응답은 `ErrorResponse` 형식을 사용한다.
- validation 실패는 `VALIDATION_ERROR`로 응답한다.
- provider 원문 에러 메시지를 사용자-facing 응답에 그대로 노출하지 않는다.
- 다른 사용자의 리소스 존재 여부를 노출하지 않는다.
- 예외는 실제 복구, 상태 전이, 사용자 응답 변환 또는 경계 로깅 책임이 있는 계층에서만 처리한다.
- 가능한 한 복구하거나 분류할 수 있는 구체적인 예외를 잡고, 예상하지 못한 오류를 provider 오류나 비즈니스 오류로 오분류하지 않는다.
- 예상하지 못한 오류는 사용자-facing 응답에서 내부 정보를 숨기되 원인과 운영 로그를 보존한다.
- 요구사항, 설계 결정이나 재현 가능한 실패 시나리오가 없는 fallback과 retry를 추가하지 않는다.
- 같은 책임의 검증을 여러 계층에서 불필요하게 반복하지 않는다. 외부 입력·provider 응답 경계의 계약과 영속화할 도메인 불변식처럼 책임이 다른 검증은 각 경계에서 독립적으로 보장한다.

## OpenAPI / Swagger

- Swagger UI를 개발자가 보는 핵심 API 문서로 사용하고, controller·DTO·`@RewriteApi`에서 코드 우선 OpenAPI 명세를 생성한다.
- `@RewriteApi` 설명은 사용 목적, 사용 화면, 호출 시점, 주요 동작, 성공 후 처리, 오류 순서로 작성한다.
- summary는 `API-009 · 기본 정보 저장` 형식, tag는 도메인 기준으로 작성한다.
- `@ApiError`에는 실제 발생 가능한 오류 코드, 발생 조건과 프론트엔드 처리 방법만 기록한다. 같은 HTTP 상태의 복수 오류는 named example로 구분한다.
- `VALIDATION_ERROR`의 대표 detail은 실제 서비스가 반환할 수 있는 field와 reason을 함께 기록한다. OAuth redirect 오류는 JSON `ErrorResponse`가 아닌 `@ApiRedirectError`로 구분한다.
- 인증 API를 포함한 운영 profile OpenAPI는 활성 API 29개를 모두 포함해야 하고, API-028은 Deprecated path로 노출하지 않는다.
- 공통 `401`, `403`, `500`과 API별 오류는 `ErrorResponse` schema를 사용하고, 비동기 Job 실패는 HTTP 오류와 분리한다.
- `x-rewrite-*` 확장은 Swagger 설명과 Notion 동기화에 사용할 구조화된 메타데이터로 유지한다.
- `docs/api/`는 API 상태, 라우팅과 복잡한 교차 API 흐름을 보완하며 Notion은 보조 동기화 문서로 사용한다.
- Springdoc/OpenAPI 의존성 추가는 별도 이슈 또는 명시적인 사용자 요청으로 진행한다.

## Git

- 하나의 issue와 PR은 독립적으로 구현, 리뷰, 검증할 수 있는 작은 단위로 유지한다.
- 기능 추가, 리팩터링, 인프라 변경, 문서 정리는 가능한 한 같은 PR에 섞지 않는다.
- commit 메시지 제목은 `:{emoji}: type: 한글 요약` 형식을 사용한다. 예: `:sparkles: feat: 공통 에러 응답 형식 구현`
- 필요한 경우 빈 줄 뒤에 변경 이유와 상세 내용을 commit body로 짧게 추가한다.
