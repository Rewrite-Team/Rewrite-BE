# Documentation Guide

이 디렉터리는 Rewrite 백엔드 개발에 필요한 기준 문서를 보관한다.

Codex는 모든 문서를 매번 읽지 않는다. 먼저 이 문서를 보고 작업 유형에 맞는 문서만 읽는다.

## Start Here

- 진행 현황 확인: `docs/status.md`
- 제품 요구사항 확인: `docs/requirements.md`
- API 계약 확인: `docs/api/README.md`

## Document Map

| Document | Purpose | Read When |
|---|---|---|
| `status.md` | 기능 진행 상태, 남은 작업, 다음 권장 작업 | 진행 현황 확인, 다음 이슈 생성 |
| `requirements.md` | `REQ-*` 기준 제품 요구사항과 검증 기준 | 기능 범위 확인 |
| `api/README.md` | API 상태표와 도메인별 API 문서 라우팅 | API 구현/수정 |
| `api/*.md` | 도메인별 API 계약, 요청/응답, 에러 | 특정 도메인 API 구현/수정 |
| `decisions/README.md` | API와 persistence 설계 결정 인덱스 및 도메인별 결정 문서 라우팅 | 설계 이유 확인, 결정 변경 검토 |
| `decisions/*.md` | 도메인별 API, persistence 설계 결정과 트레이드오프 | 특정 도메인 또는 persistence 설계 결정 확인 |
| `architecture.md` | 패키지 구조와 계층 책임 | 새 패키지/계층 추가 |
| `conventions.md` | Java/Spring/API/예외 처리 규칙 | 코드 작성/리뷰 |
| `testing.md` | 테스트 전략과 검증 명령 | 테스트 추가/수정 |
| `codex-workflow.md` | Codex issue/PR/commit/progress 절차 | GitHub issue/PR/commit 생성, 진행 점검 |

## Routing Rules

- 기능 구현 전에는 `status.md`, 관련 `requirements.md`의 `REQ-*` 섹션, 관련 `api/` 도메인 문서만 읽는다.
- API를 추가하거나 변경할 때는 `api/README.md`의 상태표와 관련 `api/` 도메인 문서를 갱신한다.
- 설계 결정의 배경이 필요할 때만 `decisions/README.md`와 관련 `decisions/` 도메인 또는 persistence 문서를 읽는다.
- 다음 이슈를 만들 때는 `status.md`, 관련 `requirements.md`, 관련 `api/` 도메인 문서, 실제 코드/테스트 상태, `.github/ISSUE_TEMPLATE/`를 함께 확인한다.
- PR을 만들 때는 `.github/pull_request_template.md`와 변경 diff를 함께 확인한다.
- commit을 만들 때는 `codex-workflow.md`, `conventions.md`, 변경 diff를 함께 확인한다.

## Change Impact Matrix

기능, 기획, 구현, API 계약, 설계 결정이 바뀌면 아래 표를 기준으로 문서를 함께 갱신한다.

| Change Type | Required Updates | Check Before Done |
|---|---|---|
| 제품 요구사항 또는 화면 흐름 변경 | `requirements.md`, 필요 시 `status.md`, 관련 `api/`, 관련 `decisions/` | 관련 `REQ-*`의 Goal/Rules/Acceptance Criteria가 새 동작과 일치하는가 |
| 새 기능 구현 시작 | `status.md`, 필요 시 `api/README.md` API Status | feature/API 상태가 `In Progress`로 반영됐는가 |
| 기능 구현 완료 | `status.md`, 관련 `api/`, 테스트 | feature/API 상태가 `Implemented`이고 검증 전 `Verified`가 아닌가 |
| 기능 검증 완료 | `status.md`, `api/README.md` | `Verification Evidence`에 테스트/리뷰/승인 근거가 남았는가 |
| API 추가, 삭제, path/request/response/error/validation 변경 | `api/README.md`, 관련 `api/*.md`, 필요 시 `requirements.md`, `decisions/`, `status.md` | API ID, Related Requirement, 상세 명세, 상태가 모두 일치하는가 |
| 설계 결정 또는 트레이드오프 변경 | `decisions/README.md`, 관련 `decisions/*.md`, 영향받는 `requirements.md`/`api/`/`status.md` | Decision Index의 Related REQ/API와 Status가 맞는가 |
| 아키텍처, 패키지 구조, persistence 전략 변경 | `architecture.md`, 필요 시 `decisions/`, `status.md`, `testing.md` | 코드 구조와 문서의 패키지/계층 규칙이 일치하는가 |
| 테스트 전략, 검증 명령, coverage 기준 변경 | `testing.md`, 필요 시 `status.md` Verification Evidence | PR에서 실행한 검증 명령과 문서 기준이 일치하는가 |
| GitHub issue 또는 PR 생성 | `status.md` Related Issue/PR | 관련 REQ/API/Decision이 issue 또는 PR 본문에 연결됐는가 |
| 문서 구조 또는 작업 절차 변경 | `README.md`, `docs/README.md`, `codex-workflow.md`, 필요 시 `AGENTS.md` | 구식 문서 경로나 중복 기준 문서가 남지 않았는가 |

## Completion Checklist

작업 완료 또는 PR 초안 작성 전에는 다음을 확인한다.

- 관련 `REQ-*`, API ID, Decision ID를 식별했다.
- 코드 변경과 문서 상태가 충돌하지 않는다.
- 변경 유형에 맞는 문서를 Change Impact Matrix 기준으로 갱신했다.
- 상태를 올렸다면 `Verification Evidence`를 함께 갱신했다.
- 실행한 테스트 또는 실행하지 못한 이유를 기록했다.
