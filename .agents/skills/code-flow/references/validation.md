# 스킬 변경 검증

생성·검증 도구는 Python 3.9+ 표준 라이브러리와 Git을 사용한다. 스킬 자체를 수정했다면 저장소 루트에서 실행한다.

```bash
SKILL_DIR=.agents/skills/code-flow
python3 -m unittest discover -s "$SKILL_DIR/scripts/tests" -v
python3 "$SKILL_DIR/scripts/code_flow.py" validate --repo . --input "$SKILL_DIR/references/examples/api-003.flow.json"
python3 "$SKILL_DIR/scripts/code_flow.py" validate --repo . --input "$SKILL_DIR/references/examples/api-020.flow.json"
python3 "$SKILL_DIR/scripts/code_flow.py" render --repo . --input "$SKILL_DIR/references/examples/api-003.flow.json" --out build/code-flow/api-003
python3 "$SKILL_DIR/scripts/code_flow.py" render --repo . --input "$SKILL_DIR/references/examples/api-020.flow.json" --out build/code-flow/api-020
python3 "$SKILL_DIR/scripts/code_flow.py" check --repo . --snapshot build/code-flow/api-020.snapshot.json
```

단위 검증은 참조 무결성, 파일 범위, 소스 변경·삭제, HTML 삽입 방지와 내보내기를 확인한다. `validate`/`render`는 성공 시 0, 입력 오류 시 2다. `check`는 기록 파일 일치 시 0, 변경·삭제 시 1, 입력 오류 시 2다.

브라우저 자동 검증은 개발 환경에 설치된 Node.js와 Playwright를 사용한다. Playwright가 모듈 검색 경로에 있어야 하며, 설치된 Chrome을 쓰려면 `CHROME_BIN`을 해당 실행 파일 경로로 설정한다. 이 의존성은 HTML을 생성하거나 열 때 필요하지 않다.

```bash
node .agents/skills/code-flow/scripts/tests/viewer.cjs build/code-flow
```

브라우저 검증은 부모 유지, 동일 클래스 호출, 근거 코드 선택, 비동기 확장·접기, LLM 호출의 TX 외부 배치, 좁은 화면과 외부 요청 부재를 확인하고 같은 출력 디렉터리에 스크린샷을 만든다. 스크린샷에서도 클래스·메서드, 화살표, 대기 영역, 분기 라벨의 겹침을 확인한다.

Codex의 `skill-creator`가 제공되는 환경에서는 해당 스킬의 `scripts/quick_validate.py`로 이 스킬 폴더의 메타데이터도 검사한다. 저장소 완료 검증인 `./gradlew test`, `./gradlew check`는 별도로 실행한다.
