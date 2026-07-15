# Codex Workflow

이 문서는 Codex가 이 저장소에서 작업할 때 따르는 절차를 정의한다.
코드/문서 결과물의 형식 기준은 `docs/conventions.md`를 따른다.

## 일반 작업 절차

1. `AGENTS.md`를 확인한다.
2. `docs/README.md`를 확인한다.
3. 작업 유형에 필요한 `docs/` 하위 문서만 읽는다.
4. 수정 전 실제 코드와 테스트 상태를 확인한다.
5. 변경 범위는 사용자 요청과 선택한 이슈 slice 안으로 제한한다.
6. 변경 유형에 맞춰 `docs/README.md`의 Change Impact Matrix를 확인한다.
7. 완료를 말하기 전에 관련 검증 명령을 실행하거나, 실행하지 못한 이유를 명확히 남긴다.

## 문서 갱신 체크

작업 완료 전 다음을 확인한다.

1. 관련 `REQ-*`, API ID, Decision ID를 식별했다.
2. 요구사항 변경은 `docs/requirements.md`에 반영했다.
3. API 계약 또는 상태 변경은 `docs/api/README.md`와 관련 `docs/api/` 도메인 문서에 반영했다.
4. 설계 결정 변경은 `docs/decisions/README.md`와 관련 `docs/decisions/` 도메인 문서에 반영했다.
5. 구현 상태 변경은 `docs/status.md`와 `Verification Evidence`에 반영했다.
6. 아키텍처 또는 테스트 기준 변경은 `docs/architecture.md` 또는 `docs/testing.md`에 반영했다.
7. 어떤 문서를 갱신하지 않았다면, 변경 영향이 없다고 판단한 이유를 최종 응답이나 PR 본문에 남긴다.

## Excel API 문서 Google Drive 동기화

`docs/api/rewrite-api-documentation.xlsx`는 Git에서 관리하는 기준 원본이다. 아래 Google Drive Excel 파일은 프론트엔드 공유용 미러로 사용한다.

- File ID: `15eS_Q4x5kAZHkQhkwNFk08Zt3wCkxo8W`
- URL: `https://docs.google.com/spreadsheets/d/15eS_Q4x5kAZHkQhkwNFk08Zt3wCkxo8W/edit`
- MIME type: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

Codex가 Excel API 문서를 수정할 때는 다음 순서로 처리한다.

1. 로컬 Excel 문서를 갱신한다.
2. 모든 시트를 렌더링하고 주요 범위, 수식 오류, 잘림과 가독성을 검증한다.
3. Google Drive의 기존 File ID에 로컬 Excel 파일 바이트를 덮어쓴다.
4. Drive metadata를 다시 조회해 File ID와 MIME type이 유지되고 수정 시각이 갱신됐는지 확인한다.
5. 새 Drive 파일을 만들거나 네이티브 Google Sheet로 변환하지 않는다.
6. 인증 또는 업로드가 실패하면 로컬 문서 검증 결과와 Drive 동기화 실패를 분리해 보고한다.

## 승인 게이트

- 사용자가 명시적으로 요청하지 않은 commit, branch 생성, push, GitHub issue 생성, PR 생성은 하지 않는다.
- issue, PR, commit은 전체 초안을 먼저 보여주고 사용자의 명시적 승인을 받은 뒤 진행한다.
- 요청이 애매하면 git 또는 GitHub 상태를 변경하기 전에 확인한다.

## GitHub Issue 생성

사용자가 GitHub issue 생성을 요청하면 다음 순서로 진행한다.

1. `docs/status.md`에서 현재 feature status와 다음 slice 후보를 확인한다.
2. `docs/requirements.md`에서 관련 `REQ-*` 섹션을 확인한다.
3. 관련 `docs/api/` 도메인 문서와 `docs/decisions/` 도메인 문서를 필요한 만큼 확인한다.
4. 실제 코드와 테스트를 확인해 문서 상태와 구현 상태를 분리한다.
5. `.github/ISSUE_TEMPLATE/` 하위 템플릿을 확인한다.
6. 독립적으로 구현, 리뷰, 검증 가능한 작은 범위를 선택한다.
7. 기능 추가, 리팩터링, 인프라 변경, 문서 정리가 섞이면 별도 issue로 분리한다.
8. 가장 적합한 issue 템플릿으로 제목과 본문 전체 초안을 작성한다.
9. 사용자에게 초안을 보여주고 승인받은 뒤에만 issue를 생성한다.

여러 issue 템플릿이 모두 가능하면 사용자 요청이 명확한 경우를 제외하고 어떤 템플릿을 사용할지 확인한다.

## Pull Request 생성

사용자가 PR 생성을 요청하면 다음 순서로 진행한다.

1. `.github/pull_request_template.md`를 확인한다.
2. 현재 브랜치와 변경 파일을 확인한다.
3. 관련 issue 번호, requirement ID, API ID를 확인한다.
4. 변경 파일이 하나의 작고 일관된 리뷰 단위인지 확인한다.
5. 관련 없는 변경이 섞여 있으면 PR 초안 작성 전에 분리 필요성을 설명한다.
6. Change Impact Matrix 기준으로 필요한 문서가 diff에 포함됐는지 확인한다.
7. 템플릿에 맞춰 PR 제목과 본문 전체 초안을 작성한다.
8. 사용자에게 초안을 보여주고 승인받은 뒤에만 PR을 생성한다.

## Commit 생성

사용자가 commit 생성을 요청하면 다음 순서로 진행한다.

1. 현재 브랜치와 변경 파일을 확인한다.
2. diff를 확인해 실제 commit 범위를 이해한다.
3. 필요한 검증 명령을 실행하거나, 이미 실행된 검증 결과를 확인한다.
4. commit 메시지 전체 초안을 작성한다.
5. 사용자에게 commit 메시지 초안을 보여준다.
6. 사용자 승인 후에만 commit을 생성한다.

commit 메시지 제목 형식:

```text
:sparkles: feat: 공통 에러 응답 형식 구현
```

형식은 `:{emoji}: type: 한글 요약`을 사용한다.
상세 설명이 필요하면 빈 줄 뒤에 변경 이유와 내용을 짧게 적는다.

메시지를 작성했다고 바로 commit하지 않는다.
commit 메시지 승인도 issue, PR 초안 승인과 같은 게이트로 처리한다.

## 다음 개발 Issue 계획

사용자가 "다음에 개발할 이슈 만들어줘"처럼 요청하면 다음 순서로 진행한다.

1. `docs/status.md`에서 feature status와 `Next Issue Slice Candidates`를 확인한다.
2. `docs/requirements.md`에서 관련 `REQ-*` 섹션을 확인한다.
3. `docs/api/README.md`와 관련 `docs/api/` 도메인 문서에서 API 상태와 계약을 확인한다.
4. 설계 배경이 필요하면 `docs/decisions/README.md`와 관련 `docs/decisions/` 도메인 문서를 확인한다.
5. 실제 코드와 테스트를 확인해 문서 상태와 구현 상태를 분리한다.
6. 독립적으로 완료하고 검증할 수 있는 작은 issue 범위를 선택한다.
7. 관련 없는 기능, 리팩터링, 인프라 변경, 문서 정리를 섞지 않는다.
8. 명확히 더 적합한 템플릿이 없으면 `.github/ISSUE_TEMPLATE/feature_request.md`로 issue 초안을 작성한다.
9. 사용자 승인을 받은 뒤에만 issue를 생성한다.

## 진행 현황 확인

진행 현황 확인 요청을 받으면 다음 순서로 진행한다.

1. `docs/status.md`를 확인한다.
2. 제품 범위가 필요하면 `docs/requirements.md`를 확인한다.
3. API 상태가 필요하면 `docs/api/README.md`와 관련 `docs/api/` 도메인 문서를 확인한다.
4. 가능한 경우 GitHub issue와 PR 상태를 확인한다.
5. 구현 수준 검증이 필요하면 실제 코드와 테스트를 확인한다.
6. 완료, 진행 중, 막힌 항목, 남은 작업을 구분해 보고한다.

문서상 상태와 실제 코드/테스트 확인 결과는 항상 분리해서 말한다.

예시:

```text
문서상 REQ-003은 Planned입니다.
코드 확인 결과 CoverLetter 패키지는 아직 없습니다.
따라서 자기소개서 기본 CRUD는 미구현 상태로 보는 것이 맞습니다.
```
