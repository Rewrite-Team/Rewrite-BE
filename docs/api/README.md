# API 문서 안내

HTTP 계약은 controller·DTO·`@RewriteApi`에서 생성한 OpenAPI와 Swagger UI에서 확인한다.
화면 흐름·API 간 복구 절차는 아래 보완 문서에 둔다.
명세 작성은 [명세 작성 기준](../README.md#openapi--swagger)을 따른다.

## Domain Routing

| Tag | API ID | 보완 흐름 | 설계 결정 |
|---|---|---|---|
| Auth | API-001~006 | [로그인·인증](common.md#로그인과-인증) | [auth](../decisions/auth.md) |
| CoverLetters | API-007~014, API-030 | [등록·목록·상세](cover-letters.md) | [cover-letters](../decisions/cover-letters.md) |
| ReviewVersions | API-017~019, API-024 | [첨삭 버전·최종 작성본](cover-letters.md#첨삭-버전과-최종-작성본) | [review-versions](../decisions/review-versions.md) |
| LLMJobs | API-015~016 | [Job·SSE](common.md#비동기-job과-sse) | [llm-jobs](../decisions/llm-jobs.md) |
| KeywordAnalysis | API-020~021 | [키워드](cover-letters.md#키워드-분석) | [keyword-analysis](../decisions/keyword-analysis.md) |
| Interviews | API-022~023, API-025~029 | [면접](interviews.md) | [interviews](../decisions/interviews.md) |

API-028은 Deprecated로 호출하지 않고 면접 질문 목록의 `threadId`를 사용한다.
