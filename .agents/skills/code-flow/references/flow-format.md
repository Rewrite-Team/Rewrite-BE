# Flow format v1

JSON의 필수 필드와 선택 필드는 아래와 같다. 명시하지 않은 필드는 거부한다. 위치·크기·HTML을 데이터에 넣지 않는다.

| 객체 | 필수 필드 | 선택 필드 |
|---|---|---|
| flow | `version: 1`, `title`, `entry`, `sources`, `calls`, `omitted`, `uncertain` | `description` |
| source | `id`, `path`, `start`, `end` | 없음 |
| call | `id`, `class`, `method`, `source`, `steps`, `returns` | `note`, `tx` |
| tx | `id`, `label` | 없음 |

`source`와 `entry`·`target`은 해당 배열의 ID를 참조한다. `path`는 저장소 루트 기준 POSIX 상대 경로다. `start`·`end`는 1부터 시작하는 양끝 포함 줄 번호다. 실제 발췌와 해시는 렌더러가 읽으므로 모델에 붙이지 않는다. `class`·`method`는 화면 표기, `returns`는 정상 반환 또는 예외 전파를 포함한 호출 종료 설명이다. `omitted`·`uncertain`은 문자열 배열이며 빈 배열도 가능하다.

## steps

```json
{"type":"call","target":"child-invocation","source":"call-site"}
{"type":"async","target":"worker-invocation","source":"dispatch-evidence","label":"이벤트 처리","trigger":"AFTER_COMMIT + @Async"}
{"type":"note","text":"프레임워크 내부를 축약","source":"evidence"}
{"type":"return","text":"기존 결과로 조기 반환","source":"evidence"}
{"type":"throw","text":"예외가 호출자로 전파","source":"evidence"}
{"type":"branch","label":"조건","source":"evidence","cases":[
  {"when":"성공","steps":[]},
  {"when":"실패","steps":[{"type":"throw","text":"실패 예외","source":"evidence"}]}
]}
```

위 코드는 각 단계 모양을 보여주는 개별 객체다. `steps`에는 이 객체들을 배열로 넣는다. `return`·`throw`는 그 경로 배열의 마지막 단계다. 분기 뒤 공통 단계가 있으면 조기 종료 경로에서 실행되지 않는다는 점이 명확하도록 계속되는 case 안에 넣는다. 모든 경로가 끝나는 분기 뒤에 호출을 직렬로 붙이지 않는다. 렌더러가 Java 제어 흐름을 추론해 잘못된 모델을 고치지는 않는다.

- 호출 트리는 `entry`에서 연결되고 각 호출 ID는 한 번만 참조된다. 서로 다른 경로에서 같은 메서드를 호출해도 ID를 나눈다. 재귀는 유한 단계까지만 펼친 뒤 생략을 기록한다.
- `call.source`는 선언·구현, `step.source`는 호출부·조건·트리거의 근거다. 여러 파일 근거가 필요하면 `sources`에 추가하고 `note`로 연결한다.
- `branch.cases`는 대안들을 세로로 나열한다. 모든 대안이 순차 실행된다는 뜻이 아니다. 예외 catch를 개요로 표시할 때는 실패 전 부분 실행을 생략했다고 적는다.
- `async`는 기본 접힘이며 버튼으로 오른쪽에 펼친다. 인과 연결만 표현한다. 동기 join 대기가 있다면 호출자에 근거 노트를 추가한다. 두 스레드의 시간 정렬이나 join 화살표는 v1 범위 밖이다.
- `tx`는 그 호출과 동기 하위 호출을 감싸는 예상 범위다. 참여 메서드마다 새 TX를 만들지 않는다. 중첩 REQUIRES_NEW의 외부 TX 중단은 노트로 설명한다. v1은 suspension/savepoint를 별도 도형으로 모델링하지 않는다.

크기 제한: 소스·호출 각각 300개, 총 단계 1500개, 호출 깊이 24, 분기 깊이 16, case 2~20개. 모델/소스 파일은 각각 2 MB 이하. 그보다 크면 읽을 수 있는 범위로 축약한다.

## 최소 모델

```json
{
  "version": 1,
  "title": "Controller 진입점",
  "entry": "root",
  "sources": [{"id": "entry-code", "path": "src/main/java/example/ExampleController.java", "start": 1, "end": 10}],
  "calls": [{"id": "root", "class": "ExampleController", "method": "get()", "source": "entry-code", "steps": [], "returns": "Response DTO"}],
  "omitted": ["하위 처리의 개요만 표시"],
  "uncertain": ["실행을 계측하지 않은 정적 해석"]
}
```

실제 경로·줄 번호로 바꾼 뒤 검증한다. 저장소 기반의 완전한 예제는 `examples/`에서 확인한다.
