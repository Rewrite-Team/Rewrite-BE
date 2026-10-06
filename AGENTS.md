# AGENTS.md

Rewrite 서비스의 Java Spring Boot 백엔드다. 팀의 기존 문서·GitHub 템플릿·테스트 기준을 따른다.

## 문서 안내

작업에 해당하는 문서·섹션만 읽는다.

| 작업 | 기준 |
|---|---|
| 로컬 실행·profile·CI | [실행 안내](README.md#quick-start)·[실행 프로필](README.md#실행-프로필)·[CI](README.md#테스트와-ci) |
| 작업 원칙 | [작업 원칙](docs/README.md#작업-원칙) |
| API 문서화·오류 처리 | [API와 오류](docs/README.md#api와-오류) |
| 문서 작성·GitHub | [문서 작성](docs/README.md#문서-작성)·[Git·GitHub와 승인](docs/README.md#gitgithub와-승인) |
| 제품 요구사항·화면 흐름 | [requirements](docs/requirements.md) |
| HTTP 계약 | controller·DTO·`@RewriteApi`에서 생성한 OpenAPI와 Swagger UI |
| 인증·Job·자기소개서·면접의 교차 API 흐름 | [API 안내](docs/api/README.md)와 연결된 보완 문서 |
| DB/JPA·schema·migration | [ERD](docs/erd.md) |
| 설계 이유·대안·결정 이력 | [Decision Index](docs/decisions/README.md#decision-index)와 해당 결정 |
| OCI·배포·복구 | [배포 가이드](docs/deployment.md) |
| Java 호출·TX·비동기 흐름 | [code-flow 스킬](.agents/skills/code-flow/SKILL.md) |

## 기본 규칙

- 현재 브랜치·사용자 변경·하위 `AGENTS.md`를 확인하고 목표·범위·완료 조건을 정리한다.
- 기존 패턴의 가장 단순한 변경을 선택하고 범위 밖 리팩터링·스타일·주석·포맷팅·파일 재구성·동작 변경을 섞지 않는다.
- 사용자 변경·미추적 파일을 보존하고 무관한 파일을 덮어쓰거나 commit에 포함하지 않는다.
- 의미 있는 범위·동작·계약이 모호하면 해석·trade-off·영향을 설명하고 답변 후 진행한다.
- 공개 API 계약 변경이나 기준의 모호·누락은 변경안·영향을 설명하고 사용자 승인 후 진행한다.
- 명시적으로 요청하지 않은 branch·commit·push·issue·PR 생성은 하지 않는다.
- 이슈용 브랜치는 GitHub에서 생성해 해당 이슈에 연결한 뒤 로컬에서 체크아웃한다. 절차는 [이슈용 브랜치](docs/README.md#이슈용-브랜치)를 따른다.
- issue·PR·commit은 전체 초안의 별도 명시적 승인 후 생성하며 동일 초안은 재승인받지 않는다.
- Docker·Compose·Testcontainers는 해당 작업에서 명시적으로 요청한 경우에만 사용한다.
- 일반 로컬 검증에서 Docker를 시작하지 않으며 Docker 의존 검증은 비의존 테스트로 좁힌다.
- 새 동작은 테스트를 추가·갱신하고 일반 검증은 `./gradlew test`, 완료 전은 `./gradlew check`를 따른다.
- 문서는 조건·주체·행동·결과를 명확히 쓰고 중복 상세는 기준 문서로 연결한다.
- 문서 축소 시 수치·조건·예외·승인·권한의 의미를 보존한다.
