# Spring 호출 해석

소스를 읽을 때 아래 판단을 확인하고, 근거를 확보하지 못하면 미확정으로 표시한다.

- annotation만 보고 TX가 시작된다고 단정하지 않는다. 클래스/메서드 annotation, 실제 빈 구현체, 프록시를 통한 외부 호출 여부와 propagation을 함께 읽는다. 같은 클래스의 self-invocation은 일반적인 proxy 모드에서 새로운 advice 경계가 아니다.
- 기존 TX 안의 REQUIRED 호출은 참여다. 별도 TX 박스를 추가하지 않는다. 기존 TX 없이 프록시로 진입한 REQUIRED는 새로운 경계다. readOnly·REQUIRES_NEW·rollbackFor가 흐름에 영향을 주면 표기한다.
- Java 메서드 본문 반환과 프록시의 commit 완료를 구분한다. commit 시 예외도 가능하다. 모든 예외가 항상 rollback된다고 일반화하지 말고 실제 rollback 규칙과 catch 위치를 읽는다.
- `publishEvent`는 그 자체로 비동기가 아니다. listener의 `@Async`, `@TransactionalEventListener` phase, async 활성화와 executor 구성을 확인한다. AFTER_COMMIT이면 이벤트 발행 위치에 연결하되 실행 트리거는 커밋 이후라고 적는다.
- Thread-bound TX는 별도 async 스레드로 전파되지 않는다. worker 내부의 프록시 호출에 새 TX가 있다면 그 경계를 표시한다. 요청과 worker의 완료 순서는 보장된 인과관계 이외에 추정하지 않는다.
- worker의 입력 준비 TX → 외부 LLM 호출 → 결과 저장 TX를 분리해 읽는다. 실패 기록 TX는 이미 커밋된 요청 TX를 되돌리지 않는다. catch가 감싸는 호출 범위를 확인한다.
- 인터페이스 호출은 구현체·프로필·조건부 빈을 확인한다. repository 구현 내부나 표준 라이브러리는 의미 있는 입출력으로 축약하고, 생략이 TX 부재를 뜻하지 않게 설명한다.
- 필터·AOP·인증, 데이터베이스 잠금, event 재시도, SSE·polling처럼 진입점 밖의 동작은 요청 범위와 관련성이 있을 때 연결한다. 다른 HTTP 요청을 같은 동기 호출 스택에 이어 붙이지 않는다.
