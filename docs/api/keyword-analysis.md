# Keyword Analysis API

## Keyword Analysis API

### 키워드 분석 시작 또는 재분석

```http
POST /cover-letters/{coverLetterId}/keyword-analysis
```

Request body는 없다. 서버는 호출 시점의 최신 성공 첨삭 버전과 해당 버전의 문항별 `finalAnswer`를 분석 입력으로 사용한다.

키워드 분석 결과가 없으면 새 `KeywordAnalysis`를 생성하고 LLM Job을 시작한다.

키워드 분석 결과가 이미 있으면 같은 `keywordAnalysisId`를 재사용해 `PROCESSING`으로 전환하고 새 LLM Job을 시작한다. 이 흐름은 결과 화면의 `AI 키워드 재분석` 버튼에서 사용한다.

`KEYWORD_ANALYSIS` LLM Job은 커밋 이후 비동기 worker에서 실행된다. worker는 분석 기준 첨삭 버전의 문항별 최종 작성본을 입력으로 사용하고, 완료 시 최신 키워드 결과를 저장한다.

Response:

```json
{
  "status": "PROCESSING",
  "jobId": "job_01HZ..."
}
```

Validation:

```text
coverLetter.status는 REVIEWED여야 한다.
최신 성공 ReviewVersion이 존재해야 한다.
분석 결과 keywords는 최대 20개다.
keywords[].importance는 1~100 범위의 정수다.
기존 KeywordAnalysis가 있으면 같은 리소스를 재사용한다.
```

동일한 키워드 분석 Job이 이미 `PENDING` 또는 `PROCESSING`이면 새 Job을 만들지 않고 기존 Job의 같은 성공 응답을 반환한다. 키워드 분석 외 다른 종류의 LLM Job이 진행 중이면 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

Conflict Response:

```json
{
  "error": {
    "code": "LLM_JOB_ALREADY_RUNNING",
    "message": "이미 진행 중인 AI 작업이 있습니다."
  }
}
```

기존 결과 재분석 또는 FAILED 상태 재시도 응답:

```json
{
  "status": "PROCESSING",
  "jobId": "job_01HZ..."
}
```

클라이언트는 응답의 `jobId`로 API-016 공통 Job SSE에 연결한다. `job.completed`를 받으면 API-021을 다시 조회하고, `job.failed`를 받으면 실패 화면을 표시한다. SSE 연결 또는 재연결에 실패한 경우에는 API-021을 polling해 최종 상태를 복구한다.

### 최신 키워드 분석 조회

```http
GET /cover-letters/{coverLetterId}/keyword-analysis/latest
```

`status`는 `NOT_STARTED`, `PROCESSING`, `COMPLETED`, `FAILED` 중 하나다.
`keywords`는 완료 상태에서만 분석 결과를 담고, 그 외 상태에서는 빈 배열이다.
`coverLetter`는 모든 상태에서 반환한다. `sourceReviewVersion`은 분석 기준이 확정된 `PROCESSING`, `COMPLETED`, `FAILED`에서 반환하고, 분석 전 `NOT_STARTED`에서는 `null`이다. 화면에 필요하지 않은 첨삭 버전 생성 시각은 반환하지 않는다.

`jobId`는 `PROCESSING`에서 현재 실행 중인 Job ID, `FAILED`에서 가장 최근 실패한 Job ID를 반환한다. `NOT_STARTED`, `COMPLETED`에서는 `null`이다.

새로고침 후 `status=PROCESSING`이면 응답의 `jobId`로 API-016에 다시 연결한다. SSE 연결에 실패한 동안에는 이 API를 polling하고, `COMPLETED` 또는 `FAILED`가 되면 중단한다.

키워드 분석 결과가 없는 경우:

```json
{
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "2026 상반기 백엔드 개발자 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자"
  },
  "sourceReviewVersion": null,
  "status": "NOT_STARTED",
  "jobId": null,
  "keywords": []
}
```

진행 중인 키워드 분석 결과가 있는 경우:

```json
{
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "2026 상반기 백엔드 개발자 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자"
  },
  "sourceReviewVersion": {
    "id": "rv_01HZ...",
    "version": "v0.2"
  },
  "status": "PROCESSING",
  "jobId": "job_01HZ...",
  "keywords": []
}
```

Response:

```json
{
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "2026 상반기 백엔드 개발자 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자"
  },
  "sourceReviewVersion": {
    "id": "rv_01HZ...",
    "version": "v0.2"
  },
  "status": "COMPLETED",
  "jobId": null,
  "keywords": [
    {
      "keyword": "백엔드",
      "importance": 95
    },
    {
      "keyword": "Spring",
      "importance": 88
    }
  ]
}
```

실패한 키워드 분석 결과가 있는 경우:

```json
{
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "2026 상반기 백엔드 개발자 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자"
  },
  "sourceReviewVersion": {
    "id": "rv_01HZ...",
    "version": "v0.2"
  },
  "status": "FAILED",
  "jobId": "job_01HZ...",
  "keywords": []
}
```
