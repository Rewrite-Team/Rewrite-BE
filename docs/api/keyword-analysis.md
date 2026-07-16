# Keyword Analysis API

## Keyword Analysis API

### 키워드 분석 시작 또는 재분석

```http
POST /cover-letters/{coverLetterId}/keyword-analysis
```

Request:

```json
{
  "sourceReviewVersionId": "rv_01HZ..."
}
```

`sourceReviewVersionId`를 생략하면 최신 첨삭 버전을 기준으로 분석한다.

키워드 분석 결과가 없으면 새 `KeywordAnalysis`를 생성하고 LLM Job을 시작한다.

키워드 분석 결과가 이미 있으면 같은 `keywordAnalysisId`를 재사용해 `PROCESSING`으로 전환하고 새 LLM Job을 시작한다. 이 흐름은 결과 화면의 `AI 키워드 재분석` 버튼에서 사용한다.

재분석 요청에서는 `sourceReviewVersionId`를 보내지 않는다. 서버는 항상 `CoverLetter.latestReviewVersionId`를 기준으로 다시 분석한다.

`KEYWORD_ANALYSIS` LLM Job은 커밋 이후 비동기 worker에서 실행된다. worker는 분석 기준 첨삭 버전의 문항별 최종 작성본을 입력으로 사용하고, 완료 시 최신 키워드 결과를 저장한다.

Response:

```json
{
  "jobId": "job_01HZ...",
  "keywordAnalysisId": "ka_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "status": "PROCESSING"
}
```

Validation:

```text
coverLetter.status는 REVIEWED여야 한다.
sourceReviewVersionId가 있으면 해당 자기소개서의 첨삭 버전이어야 한다.
분석 결과 keywords는 최대 20개다.
keywords[].importance는 1~100 범위의 정수다.
같은 자기소개서에 PENDING 또는 PROCESSING 상태의 LLM Job이 없어야 한다.
기존 KeywordAnalysis가 있으면 같은 리소스를 재사용한다.
```

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
  "jobId": "job_01HZ...",
  "keywordAnalysisId": "ka_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "status": "PROCESSING"
}
```

### 최신 키워드 분석 조회

```http
GET /cover-letters/{coverLetterId}/keyword-analysis/latest
```

`status`는 `NOT_STARTED`, `PROCESSING`, `COMPLETED`, `FAILED` 중 하나다.
`keywords`는 완료 상태에서만 분석 결과를 담고, 그 외 상태에서는 빈 배열이다.
`coverLetter`는 모든 상태에서 반환한다. `sourceReviewVersion`은 분석 기준이 확정된 `PROCESSING`, `COMPLETED`, `FAILED`에서 반환하고, 분석 전 `NOT_STARTED`에서는 `null`이다. 화면에 필요하지 않은 첨삭 버전 생성 시각은 반환하지 않는다.

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
  "keywords": []
}
```
