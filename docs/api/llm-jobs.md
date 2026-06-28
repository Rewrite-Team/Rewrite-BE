# LLM Job API

## LLM Job API

### Job 상태 조회

```http
GET /llm-jobs/{jobId}
```

현재 구현 범위에서는 최초 첨삭 Job이 submit 이후 비동기 worker로 실행될 수 있다. `partialResult`는 아직 서버 메모리/cache 저장소가 없으므로 항상 `null`이다.

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

아직 구현되지 않았다. API-016 SSE stream은 partial result 저장소와 스트리밍 이벤트 발행이 준비되는 후속 이슈에서 구현한다.

SSE는 LLM 작업의 실시간 표시 채널이다. 서버는 `Last-Event-ID` 기반 이벤트 replay를 지원하지 않는다.

연결이 끊기면 클라이언트는 `GET /llm-jobs/{jobId}`로 상태를 다시 조회한다.

```text
status가 PROCESSING이면 partialResult를 먼저 렌더링한 뒤 stream에 다시 연결한다.
status가 PROCESSING이고 partialResult가 null이면 progress만 표시하고 stream에 다시 연결하되 delta 텍스트는 렌더링하지 않는다.
status가 COMPLETED이면 resultRef 기준으로 결과 상세 API를 조회한다.
status가 FAILED 또는 CANCELED이면 상태 메시지를 표시한다.
```

`PROCESSING` 상태에서 재연결하는 경우 이미 생성된 텍스트는 SSE replay로 받지 않고, Job 상태 조회 또는 자기소개서 상세 조회의 `partialResult`로 복구한다. SSE 재연결 이후 수신한 delta만 기존 partial text 뒤에 이어붙인다.

메모리 저장소 유실 등으로 `partialResult`가 `null`이면 이미 생성된 텍스트 복구는 생략한다. 클라이언트는 `progress`를 표시하고 SSE 재연결 이후 수신한 delta 텍스트는 숨긴다. `job.completed` 이벤트 또는 상태 조회에서 `COMPLETED`를 확인하면 `resultRef.type`을 확인하고 해당 결과 API를 조회해 온전한 최종 결과를 표시한다. 예를 들어 `REVIEW_VERSION`은 첨삭 버전 상세, `KEYWORD_ANALYSIS`는 최신 키워드 분석 결과, `INTERVIEW_SESSION` 또는 `INTERVIEW_QUESTION`은 면접 세션/질문 목록, `INTERVIEW_MESSAGE`는 대화 메시지를 조회한다.

응답 Content-Type:

```http
Content-Type: text/event-stream
```

이벤트 예시:

```text
event: job.started
data: {"jobId":"job_01HZ...","type":"COVER_LETTER_REVIEW"}

event: review.question.started
data: {"questionId":"clq_01HZ...","order":1}

event: review.question.delta
data: {"questionId":"clq_01HZ...","field":"aiReport","delta":"직무 경험을"}

event: review.question.completed
data: {"questionResultId":"rvqr_01HZ...","questionId":"clq_01HZ..."}

event: job.completed
data: {"jobId":"job_01HZ...","resultRef":{"type":"REVIEW_VERSION","id":"rv_01HZ..."}}
```

권장 이벤트:

```text
job.started
job.progress
review.question.started
review.question.delta
review.question.completed
interview.message.delta
interview.message.completed
job.completed
job.failed
```
