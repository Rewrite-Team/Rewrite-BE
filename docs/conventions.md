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

## OpenAPI / Swagger

- Markdown API 계약은 `docs/api/README.md`와 `docs/api/` 하위 도메인 문서에서 관리한다.
- Swagger/OpenAPI는 구현된 API 확인용 문서로 사용한다.
- OpenAPI annotation을 추가할 때는 controller 동작, DTO, error response와 일치시킨다.
- Springdoc/OpenAPI 의존성 추가는 별도 이슈 또는 명시적인 사용자 요청으로 진행한다.

## Git

- 하나의 issue와 PR은 독립적으로 구현, 리뷰, 검증할 수 있는 작은 단위로 유지한다.
- 기능 추가, 리팩터링, 인프라 변경, 문서 정리는 가능한 한 같은 PR에 섞지 않는다.
- commit 메시지 제목은 `:{emoji}: type: 한글 요약` 형식을 사용한다. 예: `:sparkles: feat: 공통 에러 응답 형식 구현`
- 필요한 경우 빈 줄 뒤에 변경 이유와 상세 내용을 commit body로 짧게 추가한다.
