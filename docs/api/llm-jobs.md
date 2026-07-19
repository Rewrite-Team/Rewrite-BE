# LLM Job API

## LLM Job API

### Job 상태 조회

```http
GET /llm-jobs/{jobId}
```

현재 구현 범위에서는 최초 첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백 Job이 생성 이후 비동기 worker로 실행될 수 있다. API-015는 SSE 재연결·이벤트 유실과 비-SSE Job의 상태 복구에 사용하는 경량 조회 API다. 완성된 첨삭 문항 결과는 API-012와 API-016의 문항 단위 스냅샷으로 제공한다.

`targetType=COVER_LETTER`인 Job은 연결된 자기소개서의 owner와 soft delete 상태를 기준으로 접근 권한을 검증한다. 존재하지 않는 Job, 다른 사용자 소유 자기소개서에 연결된 Job, 삭제된 자기소개서에 연결된 Job은 모두 `NOT_FOUND`를 반환한다.

Response:

```json
{
  "status": "PROCESSING",
  "progress": {
    "current": 1,
    "total": 3,
    "message": "1번 문항을 첨삭하고 있습니다."
  },
  "resultRef": null,
  "error": null
}
```

완료 응답 예시:

```json
{
  "status": "COMPLETED",
  "progress": {
    "current": 3,
    "total": 3,
    "message": "첨삭이 완료되었습니다."
  },
  "resultRef": {
    "type": "REVIEW_VERSION",
    "id": "rv_01HZ..."
  },
  "error": null
}
```

실패 응답 예시:

```json
{
  "status": "FAILED",
  "progress": {
    "current": 0,
    "total": 3,
    "message": "LLM 첨삭에 실패했습니다."
  },
  "resultRef": null,
  "error": {
    "code": "LLM_PROVIDER_ERROR",
    "message": "LLM 응답 생성에 실패했습니다."
  }
}
```

`status`는 `PENDING | PROCESSING | COMPLETED | FAILED | CANCELED` 중 하나다. `progress`와 그 하위 필드는 항상 non-null이다. `resultRef`는 결과 리소스가 확정된 완료 Job에서만 non-null이고, `error`는 실패한 Job에서만 non-null이다.

Job ID는 path와 중복되므로 응답하지 않는다. Job type, target, 내부 재시도 횟수, partial result와 생성·완료 시각도 프론트엔드 복구 흐름에서 사용하지 않으므로 응답하지 않는다.

### Job 스트림

```http
GET /llm-jobs/{jobId}/stream
```

아직 구현되지 않았다. API-016은 최초 첨삭·재첨삭, 키워드 분석, 초기·추가 면접 질문 생성, 면접 답변 피드백 Job의 현재 상태와 완료·실패를 전달하는 공통 SSE API다. 첨삭 Job에는 완성된 문항 결과를 문항 단위로, 면접 답변 피드백 Job에는 생성 중인 문장을 delta로 추가 전달한다.

클라이언트는 시작 API가 반환한 `jobId`로 연결한다. 연결 직후 서버는 모든 Job에 현재 상태를 담은 `job.snapshot`을 먼저 전송한다. 첨삭 Job이면 이미 임시 저장된 완료 문항 결과를 `review.questions.snapshot`으로 이어서 전송한 뒤 실시간 이벤트를 전달한다. 이 순서로 상세 조회와 SSE 연결 사이에 발생한 상태 변경과 완료 문항을 누락하지 않는다. Job이 최종 실패해도 첨삭 스냅샷과 API-012는 성공한 완료 문항 결과를 반환한다.

같은 `questionId` 이벤트가 중복될 수 있으므로 클라이언트는 `questionId`를 기준으로 결과를 upsert한다. 화면 정렬은 이벤트 도착 순서가 아니라 `order`를 사용한다.

서버는 연결을 이벤트 라우터에 먼저 등록한 뒤 스냅샷을 조회·전송하고, 그 사이 발생한 변경 이벤트는 스냅샷 전송이 끝날 때까지 연결별로 버퍼링했다가 발생 순서대로 전송한다. `Last-Event-ID` 기반 영속 이벤트 replay는 제공하지 않으며, 재연결할 때마다 최신 스냅샷을 다시 전송한다. 연결 유지를 위한 heartbeat는 프론트엔드 데이터 계약에 포함되지 않는 SSE comment를 사용한다.

응답 Content-Type:

```http
Content-Type: text/event-stream
```

이벤트 예시:

```text
event: job.snapshot
data: {"status":"PROCESSING","progress":{"current":1,"total":3,"message":"2번 문항을 첨삭하고 있습니다."},"resultRef":null,"error":null}

event: review.questions.snapshot
data: {"items":[{"questionId":"clq_01HZ...","order":1,"aiReport":"직무 경험과 성과의 연결을 보강하는 것이 좋습니다.","rewrittenAnswer":"저는 백엔드 개발자로서...","rewrittenAnswerLength":320,"finalAnswer":"저는 백엔드 개발자로서...","finalAnswerLength":320}]}

event: review.question.completed
data: {"questionId":"clq_01HY...","order":2,"aiReport":"성과 수치를 보강하는 것이 좋습니다.","rewrittenAnswer":"프로젝트에서 처리량을...","rewrittenAnswerLength":280,"finalAnswer":"프로젝트에서 처리량을...","finalAnswerLength":280,"progress":{"current":2,"total":3,"message":"2개 문항 첨삭이 완료되었습니다."}}

event: interview.feedback.delta
data: {"sequence":1,"contentDelta":"답변에서 API 설계 경험은 "}

event: job.completed
data: {"resultRef":{"type":"REVIEW_VERSION","id":"rv_01HZ..."}}

event: job.failed
data: {"progress":{"current":2,"total":3,"message":"첨삭에 실패했습니다."},"error":{"code":"REVIEW_FAILED","message":"첨삭 처리에 실패했습니다."}}
```

`job.snapshot.status`는 `PENDING | PROCESSING | COMPLETED | FAILED | CANCELED` 중 하나다. `progress`와 그 하위 필드는 항상 non-null이다. `resultRef`는 결과 리소스가 확정된 완료 Job에서만 non-null이며 `error`는 실패한 Job에서만 non-null이다. `review.questions.snapshot.items`는 완료된 문항이 없으면 빈 배열이다.

스냅샷과 문항 완료 이벤트의 문항 결과는 `questionId`, `order`, `aiReport`, `rewrittenAnswer`, `rewrittenAnswerLength`, `finalAnswer`, `finalAnswerLength`를 사용한다. 문항 이벤트는 `aiReport`와 `rewrittenAnswer`가 모두 완성되고 검증된 시점에 한 번 전송하며, `progress`를 함께 반환한다. 필드별 또는 토큰별 delta 이벤트는 제공하지 않는다. 최초 첨삭과 재첨삭에 같은 이벤트 계약을 사용한다.

면접 답변 피드백 Job은 `interview.feedback.delta`로 `sequence`와 `contentDelta`를 전송한다. `sequence`는 Job 안에서 1부터 증가하며 클라이언트는 중복 이벤트를 무시하고 순서대로 `contentDelta`를 이어 붙인다. delta는 현재 SSE 연결의 표시 효과만 위한 임시 데이터이며 서버가 영속 저장하거나 `Last-Event-ID`로 replay하지 않는다. 최종 ASSISTANT `content`와 `score`는 `job.completed` 이후 API-029를 다시 조회해 확정한다.

Job ID는 path와 중복되므로 이벤트 데이터에 포함하지 않는다. Job 종류도 연결한 Job으로 이미 결정되므로 포함하지 않는다. `job.started`와 별도 `job.progress` 이벤트는 사용하지 않고, 연결 시점 상태는 `job.snapshot`으로 전달한다. 첨삭 진행률 변경은 `review.question.completed`에 포함한다.

이미 종료된 Job에 연결하면 `job.snapshot`의 `status`, `resultRef` 또는 `error`로 최종 상태를 전달한 뒤 연결을 종료한다. 실시간 처리 중 종료된 경우에는 `job.completed` 또는 `job.failed`를 전송하고 연결을 종료한다. 완료 `resultRef.type`은 Job에 따라 `REVIEW_VERSION`, `KEYWORD_ANALYSIS`, `INTERVIEW_SESSION`, `INTERVIEW_QUESTION`, `INTERVIEW_MESSAGE` 중 하나다.

키워드 분석과 초기·추가 면접 질문 생성은 중간 도메인 이벤트를 제공하지 않는다. `job.completed`를 받으면 키워드 분석은 API-021을, 초기 면접 질문 생성은 API-025와 API-026을, 추가 면접 질문 생성은 API-026을, 면접 답변 피드백은 API-029를 다시 조회한다. SSE 연결 또는 재연결에 실패하면 도메인 조회 또는 API-015 polling으로 최종 상태를 복구한다.

면접 피드백 생성 중 재연결하면 이전 delta를 복구하지 않는다. 클라이언트는 불완전한 뒷부분만 이어서 표시하지 않고 진행 상태만 보여준 뒤, 완료 시 API-029의 저장된 전체 assistant 메시지로 교체한다. `job.failed`를 받으면 현재 연결에서 조합한 임시 문장을 제거하고 실패 상태를 표시한다.
