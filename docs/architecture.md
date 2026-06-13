# Backend Architecture

## Package Strategy

이 프로젝트는 일반적인 Spring 계층형 구조를 사용하되, 패키지는 기능 중심으로 구성한다.

기본 형태:

```text
src/main/java/com/daon/rewrite/
- global/
- auth/
- coverletter/
- llmjob/
- reviewversion/
- keywordanalysis/
- interview/
```

각 기능 패키지는 필요한 계층만 가진다.

```text
<feature>/
- controller/
- service/
- repository/
- entity/
- dto/
- client/
- config/
```

전역 `controller`, `service`, `repository` 패키지는 만들지 않는다.

## Current Packages

```text
com.daon.rewrite.global
```

공통 예외, 공통 응답, 공통 설정처럼 여러 기능에서 사용하는 횡단 관심사를 둔다.

```text
com.daon.rewrite.auth
```

현재 사용자 식별과 인증 경계를 담당한다. 현재는 개발용 `DevCurrentUserProvider`가 있으며, 실제 인증 도입 시 같은 경계를 유지하면서 구현체를 교체한다.

## Layer Responsibilities

### Controller

- HTTP 요청과 응답을 담당한다.
- request DTO validation을 수행한다.
- 비즈니스 로직을 직접 구현하지 않는다.
- entity나 내부 모델을 API 응답으로 직접 노출하지 않는다.

### Service

- 비즈니스 로직과 use case를 담당한다.
- transaction boundary를 둔다.
- owner 검증, 상태 전이, soft delete 정책을 적용한다.
- repository와 외부 client를 조합한다.

### Repository

- 저장소 접근을 담당한다.
- in-memory 단계에서는 Java collection 기반 저장소를 사용한다.
- JPA 전환은 별도 이슈에서 진행한다.

### Entity / Model

- 저장 또는 도메인 상태를 표현한다.
- API DTO에 직접 노출하지 않는다.

### DTO

- API 요청/응답 경계를 표현한다.
- validation annotation은 request DTO에 둔다.
- API 문서의 요청/응답 예시와 이름이 어긋나지 않게 관리한다.

## Dependency Direction

일반적인 의존 방향:

```text
controller -> service -> repository -> model/entity
```

역방향 의존은 피한다.

## Scope Rules

- in-memory 단계에서는 JPA, Flyway, DB driver를 도입하지 않는다.
- DB/JPA/Flyway는 in-memory 흐름을 검증한 뒤 별도 이슈에서 도입한다.
- LLM provider 실제 호출은 skeleton API와 상태 모델이 안정된 뒤 별도 이슈로 진행한다.
