# LLM Job API

## LLM Job API

### Job 상태 조회

```http
GET /llm-jobs/{jobId}
```

현재 구현 범위에서는 최초 첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백 Job이 생성 이후 비동기 worker로 실행될 수 있다. Job 상태 응답의 `partialResult`는 호환을 위해 유지하지만 첨삭 진행 화면 복구에는 사용하지 않으며 `null`을 반환한다. 완성된 첨삭 문항 결과는 API-012와 API-016의 문항 단위 스냅샷으로 제공한다.

`targetType=COVER_LETTER`인 Job은 연결된 자기소개서의 owner와 soft delete 상태를 기준으로 접근 권한을 검증한다. 존재하지 않는 Job, 다른 사용자 소유 자기소개서에 연결된 Job, 삭제된 자기소개서에 연결된 Job은 모두 `NOT_FOUND`를 반환한다.

Response:

```json
{
  "id": "job_01HZ...",
  "type": "COVER_LETTER_REVIEW",
  "status": "PROCESSING",
  "targetType": "COVER_LETTER",
  "targetId": "cl_01HZ...",
  "progress": {
    "current": 1,
    "total": 3,
    "message": "1번 문항을 첨삭하고 있습니다."
  },
  "attempt": 1,
  "maxAttempts": 2,
  "partialResult": null,
  "resultRef": null,
  "error": null,
  "createdAt": "2026-06-20T14:10:00",
  "completedAt": null
}
```

완료 응답 예시:

```json
{
  "id": "job_01HZ...",
  "type": "COVER_LETTER_REVIEW",
  "status": "COMPLETED",
  "targetType": "COVER_LETTER",
  "targetId": "cl_01HZ...",
  "progress": {
    "current": 3,
    "total": 3,
    "message": "첨삭이 완료되었습니다."
  },
  "attempt": 1,
  "maxAttempts": 2,
  "resultRef": {
    "type": "REVIEW_VERSION",
    "id": "rv_01HZ..."
  },
  "error": null,
  "createdAt": "2026-06-20T14:10:00",
  "completedAt": "2026-06-20T14:11:00"
}
```

실패 응답 예시:

```json
{
  "id": "job_01HZ...",
  "type": "COVER_LETTER_REVIEW",
  "status": "FAILED",
  "targetType": "COVER_LETTER",
  "targetId": "cl_01HZ...",
  "progress": {
    "current": 0,
    "total": 3,
    "message": "LLM 첨삭에 실패했습니다."
  },
  "attempt": 1,
  "maxAttempts": 2,
  "resultRef": null,
  "error": {
    "code": "LLM_PROVIDER_ERROR",
    "message": "LLM 응답 생성에 실패했습니다."
  },
  "createdAt": "2026-06-20T14:10:00",
  "completedAt": "2026-06-20T14:12:00"
}
```

### Job 스트림

```http
GET /llm-jobs/{jobId}/stream
```

아직 구현되지 않았다. API-016은 최초 첨삭과 재첨삭의 완성된 문항 결과를 문항 단위로 전달하고 Job 완료·실패를 알리는 SSE API다.

클라이언트는 먼저 API-012로 전체 질문과 첨삭 입력을 조회한 뒤 `reviewJob.jobId`로 연결한다. 연결 직후 서버는 이미 임시 저장된 완료 문항 결과를 `review.question.completed` 이벤트로 먼저 전송하고, 그 다음 실시간 이벤트를 전달한다. 이 순서로 상세 조회와 SSE 연결 사이에 완료된 문항도 누락되지 않는다.

같은 `questionId` 이벤트가 중복될 수 있으므로 클라이언트는 `questionId`를 기준으로 결과를 upsert한다. 화면 정렬은 이벤트 도착 순서가 아니라 `order`를 사용한다.

서버는 `Last-Event-ID` 기반 영속 이벤트 replay를 제공하지 않는다. 재연결할 때마다 현재 영속화된 완료 문항 스냅샷을 다시 전송한다.

응답 Content-Type:

```http
Content-Type: text/event-stream
```

이벤트 예시:

```text
event: job.started
data: {"jobId":"job_01HZ...","type":"COVER_LETTER_REVIEW"}

event: review.question.completed
data: {"jobId":"job_01HZ...","questionId":"clq_01HZ...","order":1,"aiReport":"직무 경험과 성과의 연결을 보강하는 것이 좋습니다.","rewrittenAnswer":"저는 백엔드 개발자로서..."}

event: job.completed
data: {"jobId":"job_01HZ...","resultRef":{"type":"REVIEW_VERSION","id":"rv_01HZ..."}}
```

문항 이벤트는 `aiReport`와 `rewrittenAnswer`가 모두 완성되고 검증된 시점에 한 번 전송한다. 필드별 또는 토큰별 delta 이벤트는 제공하지 않는다. 최초 첨삭과 재첨삭에 같은 이벤트 계약을 사용한다.

권장 이벤트:

```text
job.started
job.progress
review.question.completed
interview.message.delta
interview.message.completed
job.completed
job.failed
```
