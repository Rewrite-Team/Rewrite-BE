---
name: code-flow
description: Java Spring의 메서드 또는 HTTP 진입점부터 호출·반환·트랜잭션·비동기 분기를 소스 근거와 함께 탐색하는 독립 HTML과 Markdown을 만든다. 코드 호출 흐름을 시각화하거나 기존 흐름 문서를 현재 소스로 갱신할 때 사용한다.
---

# Code Flow

사용자가 지정한 진입점부터 코드 읽기의 뿌리를 유지하는 호출 지도를 만든다. AI는 코드를 읽어 구조화된 데이터를 작성하고, 공통 렌더러는 배치와 상호작용을 담당한다. 전체 Java 파서나 실행 계측기가 아니다.

## 작업 흐름

1. 저장소의 `AGENTS.md`와 문서 라우팅을 확인하고 대상 메서드/API를 찾는다. 오버로드·구현체·프로필에 따라 진입점이 여러 개면 범위를 확인한다. 명확하면 바로 진행한다.
2. 실제 호출부부터 하위 메서드, 반환 DTO, 분기·예외, 트랜잭션 및 이벤트·executor 설정을 읽는다. [Spring 해석 기준](references/spring.md)을 적용한다. API 문서는 목적과 계약 확인에 사용하고 내부 호출의 근거는 소스로 남긴다.
3. [데이터 형식](references/flow-format.md)에 맞춰 `build/code-flow/<name>.flow.json`을 작성한다. 예제는 구조 참고용이다. 현재 소스를 다시 읽고 작성하며 예제의 줄 번호를 그대로 재사용하지 않는다.
   - 한 `call`은 클래스가 아닌 **한 번의 메서드 호출**이다. 같은 클래스 재호출에도 별도 ID를 준다.
   - 정상·조기 반환·실패 경로를 `branch`로 한 화면에 담는다. 반복·재귀·프레임워크 내부는 의미를 유지하는 노트로 축약한다.
   - 동기 부모는 자식 반환까지 유지된다. 비동기는 시작 원인에 연결하되 별도 스택이며, 호출자의 TX를 복사하지 않는다.
   - 모든 호출·단계에 소스 범위를 연결한다. 실행에 영향을 주는 설정도 `sources`에 포함한다. 확인하지 못한 구현체·조건·선후관계는 `uncertain`, 축약은 `omitted`에 적는다.
4. 아래 명령으로 검증하고 HTML·Markdown·소스 스냅샷을 함께 내보낸다. `SKILL_DIR`는 이 파일이 있는 디렉터리다. Python 3.9+와 Git만 필요하다.

```bash
SKILL_DIR=.agents/skills/code-flow
python3 "$SKILL_DIR/scripts/code_flow.py" validate --repo . --input build/code-flow/<name>.flow.json
python3 "$SKILL_DIR/scripts/code_flow.py" render --repo . --input build/code-flow/<name>.flow.json --out build/code-flow/<name>
python3 "$SKILL_DIR/scripts/code_flow.py" check --repo . --snapshot build/code-flow/<name>.snapshot.json
```

5. 근거와 의미를 다시 대조한다. 가능하면 HTML을 브라우저에서 열어 부모 박스, 반환, 같은 화면의 분기, 비동기 확장과 코드 선택을 확인한다. 구조 검증이 호출 의미의 정확성을 보장한다고 말하지 않는다. 결과 파일 링크와 생략·미확정 범위를 사용자에게 전달한다.

사용 예: `$code-flow KeywordAnalysisController.startKeywordAnalysis 흐름 보여줘`.

## 출력과 갱신

- 공통 `assets/`와 `scripts/code_flow.py`를 사용한다. API별 HTML·좌표를 따로 작성하지 않는다.
- HTML은 코드 발췌, CSS, JS를 포함하며 서버·CDN·AI 없이 파일을 열어 탐색할 수 있다. Markdown은 구조와 소스 근거를 제공하고 HTML에 연결한다. 일반 Markdown 뷰어 안에서 HTML 상호작용까지 실행되지는 않는다.
- 스냅샷에는 생성 시각, Git HEAD, 모델 해시, 읽은 **전체 파일**의 SHA-256이 들어간다. `check`는 기록된 파일의 변경·삭제만 검출한다. 새 파일, 읽지 않은 설정, 분석 오류를 검출하거나 자동 갱신하지 않는다.
- 소스가 바뀌면 해당 호출과 영향을 받는 경로를 다시 읽고 모델·줄 번호를 수정한 뒤 재생성한다. 해시만 갱신해 오래된 해석을 최신으로 표시하지 않는다.
- 산출물은 `build/` 아래에 두어 Git에 자동 포함되지 않게 한다. 공유 파일에 실제 소스 발췌가 포함됨을 알리고, 비밀값이 있는 설정 파일은 포함하지 않는다. 소스나 제품 문서를 변경하는 작업은 별도 요청 범위다.

스킬 자체를 수정할 때는 [검증 절차](references/validation.md)를 따른다. 동기·동일 클래스 예제는 [API-003](references/examples/api-003.flow.json), TX·비동기 예제는 [API-020](references/examples/api-020.flow.json)이다.
