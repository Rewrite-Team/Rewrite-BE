# LLM Job API

## LLM Job API

### Job 상태 조회

```http
GET /llm-jobs/{jobId}
```

현재 구현 범위에서는 최초 첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백 Job이 생성 이후 비동기 worker로 실행될 수 있다. API-015는 API-016 연결·재연결 실패, 이벤트 유실과 새로고침 시 최종 상태를 복구하는 공통 polling fallback이다. 완성된 첨삭 문항 결과는 API-012와 API-016의 문항 단위 스냅샷으로 제공한다.

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

아직 구현되지 않았다. API-016은 최초 첨삭·재첨삭, 키워드 분석, 초기·추가 면접 질문 생성, 면접 답변 피드백 Job의 상태를 전달하는 공통 SSE API다. 이벤트는 공통 상태 `job.state`, 첨삭 문항 `review.questions`, 면접 피드백 `interview.feedback.delta` 세 종류만 사용한다.

클라이언트는 시작 API가 반환한 `jobId`로 연결한다. 연결 직후 서버는 모든 Job에 현재 상태를 담은 `job.state`를 먼저 전송한다. 첨삭 Job이면 이미 임시 저장된 완료 문항 결과를 `review.questions`로 이어서 전송한다. Job의 상태 또는 진행률이 바뀔 때마다 같은 `job.state` 구조를 다시 전송한다. 이 순서로 상세 조회와 SSE 연결 사이에 발생한 상태 변경과 완료 문항을 누락하지 않는다. Job이 최종 실패해도 `review.questions`와 API-012는 성공한 완료 문항 결과를 반환한다.

같은 `questionId` 이벤트가 중복될 수 있으므로 클라이언트는 `questionId`를 기준으로 결과를 upsert한다. 화면 정렬은 이벤트 도착 순서가 아니라 `order`를 사용한다.

서버는 연결을 이벤트 라우터에 먼저 등록한 뒤 스냅샷을 조회·전송하고, 그 사이 발생한 변경 이벤트는 스냅샷 전송이 끝날 때까지 연결별로 버퍼링했다가 발생 순서대로 전송한다. `Last-Event-ID` 기반 영속 이벤트 replay는 제공하지 않으며, 재연결할 때마다 최신 상태를 다시 전송한다. 진행 중인 면접 피드백 Job은 서버 메모리에 보관한 delta를 `sequence=1`부터 다시 전송한 뒤 새 delta를 이어서 전송한다. 연결 유지를 위한 heartbeat는 프론트엔드 데이터 계약에 포함되지 않는 SSE comment를 사용한다.

응답 Content-Type:

```http
Content-Type: text/event-stream
```

이벤트 예시:

```text
event: job.state
data: {"jobType":"COVER_LETTER_REVIEW","status":"PROCESSING","progress":{"current":1,"total":3,"message":"2번 문항을 첨삭하고 있습니다."},"resultRef":null,"error":null}

event: review.questions
data: {"items":[{"questionId":"clq_01HZ...","order":1,"aiReport":"직무 경험과 성과의 연결을 보강하는 것이 좋습니다.","rewrittenAnswer":"저는 백엔드 개발자로서...","rewrittenAnswerLength":320,"finalAnswer":"저는 백엔드 개발자로서...","finalAnswerLength":320}]}

event: review.questions
data: {"items":[{"questionId":"clq_01HY...","order":2,"aiReport":"성과 수치를 보강하는 것이 좋습니다.","rewrittenAnswer":"프로젝트에서 처리량을...","rewrittenAnswerLength":280,"finalAnswer":"프로젝트에서 처리량을...","finalAnswerLength":280}]}

event: job.state
data: {"jobType":"COVER_LETTER_REVIEW","status":"PROCESSING","progress":{"current":2,"total":3,"message":"2개 문항 첨삭이 완료되었습니다."},"resultRef":null,"error":null}

event: interview.feedback.delta
data: {"sequence":1,"contentDelta":"답변에서 API 설계 경험은 "}

event: job.state
data: {"jobType":"COVER_LETTER_REVIEW","status":"COMPLETED","progress":{"current":3,"total":3,"message":"첨삭이 완료되었습니다."},"resultRef":{"type":"REVIEW_VERSION","id":"rv_01HZ..."},"error":null}

event: job.state
data: {"jobType":"COVER_LETTER_REVIEW","status":"FAILED","progress":{"current":2,"total":3,"message":"첨삭에 실패했습니다."},"resultRef":null,"error":{"code":"LLM_PROVIDER_ERROR","message":"AI 처리에 실패했습니다."}}

event: job.state
data: {"jobType":"COVER_LETTER_REVIEW","status":"CANCELED","progress":{"current":2,"total":3,"message":"작업이 취소되었습니다."},"resultRef":null,"error":null}
```

`job.state.jobType`은 `COVER_LETTER_REVIEW | COVER_LETTER_RE_REVIEW | KEYWORD_ANALYSIS | INTERVIEW_INITIAL_QUESTION_GENERATION | INTERVIEW_ADDITIONAL_QUESTION_GENERATION | INTERVIEW_MESSAGE_FEEDBACK` 중 하나다. `job.state.status`는 `PENDING | PROCESSING | COMPLETED | FAILED | CANCELED` 중 하나다. `jobType`, `status`, `progress`, `resultRef`, `error` 필드는 항상 포함한다. `resultRef`는 완료 Job에서만 non-null이고 `error`는 실패 Job에서만 non-null이다. 완료·실패·취소는 별도 이벤트 이름을 만들지 않고 `status`로 구분한다.

`review.questions.items`는 항상 배열이며 완료 문항이 없으면 `[]`다. 연결 직후에는 지금까지 완료된 문항 전체를, 문항이 새로 완료되면 해당 문항 하나를 `items` 배열로 전송한다. 프론트엔드는 두 경우 모두 `questionId`로 upsert하고 `order`로 정렬한다. 문항 결과는 `questionId`, `order`, `aiReport`, `rewrittenAnswer`, `rewrittenAnswerLength`, `finalAnswer`, `finalAnswerLength`를 사용한다. 문항 완료 후 갱신된 진행률은 이어지는 `job.state`로 전달한다.

면접 답변 피드백 Job은 OpenAI 응답 전체를 받은 뒤 구조와 필수 값을 먼저 검증한다. 검증이 성공하면 완성된 `content`를 화면 표시용 문자열 조각으로 나누고, 기존과 같이 `interview.feedback.delta`의 `sequence`와 `contentDelta`로 점진 전송한다. 조각 크기와 경계는 서버 구현 세부사항이며 프론트엔드는 이에 의존하지 않는다. 검증이 실패하면 delta를 전송하지 않고 Job을 실패 처리한다.

`sequence`는 Job 안에서 1부터 증가한다. 서버는 `PROCESSING` 동안 이미 전송한 delta를 메모리에 보관하고, 재연결 시 `job.state` 다음에 1번부터 replay한 뒤 새 delta를 이어서 전송한다. 클라이언트는 마지막으로 반영한 `sequence` 이하를 무시하고 나머지 `contentDelta`를 순서대로 이어 붙인다. 이 버퍼는 화면 복구용 임시 데이터로 영속 저장하지 않으며 Job이 종료되면 제거한다. 서버 재시작 등으로 버퍼를 복구할 수 없으면 진행 상태만 표시하고, 최종 ASSISTANT `content`와 `score`는 `job.state.status=COMPLETED` 이후 API-029를 다시 조회해 확정한다.

Job ID는 path와 중복되므로 이벤트 데이터에 포함하지 않는다. 생명주기 단계별 이벤트를 나누지 않고 모든 상태를 `job.state` 하나로 전달한다.

이미 종료된 Job에 연결하면 최종 상태의 `job.state`를 전달한다. 첨삭 Job은 `review.questions`까지 전송한 뒤 연결을 종료한다. 실시간 처리 중 종료된 경우에도 최종 `job.state`를 전송하고 연결을 종료한다. `CANCELED`이면 부분 결과를 폐기하고 원래 도메인 화면이나 목록을 재조회한다. 완료 `resultRef.type`은 Job에 따라 `REVIEW_VERSION`, `KEYWORD_ANALYSIS`, `INTERVIEW_SESSION`, `INTERVIEW_QUESTION`, `INTERVIEW_MESSAGE` 중 하나다.

키워드 분석과 초기·추가 면접 질문 생성은 중간 도메인 이벤트를 제공하지 않는다. `job.state.status=COMPLETED`이면 키워드 분석은 API-021을, 초기 면접 질문 생성은 API-025와 API-026을, 추가 면접 질문 생성은 API-026을, 면접 답변 피드백은 API-029를 다시 조회한다. SSE 연결 또는 재연결에 실패하면 도메인 조회 또는 API-015 polling으로 최종 상태를 복구한다.

면접 피드백 생성 중 재연결하면 서버가 메모리에 보관한 이전 delta를 1번부터 replay한다. 클라이언트는 `sequence`로 중복을 제거해 기존 문장 뒤에 새 delta를 이어 붙이고, 완료 시 API-029의 저장된 전체 assistant 메시지로 교체한다. replay 버퍼가 없으면 불완전한 뒷부분을 표시하지 않고 진행 상태만 유지한다. `job.state.status=FAILED`이면 현재 연결에서 조합한 임시 문장을 제거하고 실패 상태를 표시한다.

HTTP 오류와 Job 실패는 구분한다. API-015의 `status=FAILED`와 API-016의 `job.state.status=FAILED`는 정상 `200` 조회 또는 정상 SSE 연결에서 받은 비동기 작업 결과이며 HTTP `ErrorResponse`가 아니다.

공개 `job.state.error.code`는 다음 세 값만 사용한다.

| 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---|---|---|
| `LLM_PROVIDER_ERROR` | timeout, provider 장애·요청 제한, 출력 형식 검증 실패 등 사용자가 구분해 처리할 필요가 없는 AI 실패 | 공통 AI 처리 실패 안내와 기능별 수동 재시도를 제공한다. |
| `LLM_CONTEXT_LENGTH_EXCEEDED` | 입력 문맥이 처리 가능한 길이를 초과함 | 입력이 너무 길다는 안내를 표시하고 자동 재시도하지 않는다. |
| `LLM_CONTENT_FILTERED` | 입력 또는 생성 결과를 처리할 수 없음 | 내용을 수정하라는 안내를 표시하고 자동 재시도하지 않는다. |

### API-015~016 HTTP 오류 처리

COMMON의 인증과 예상하지 못한 서버 오류 처리를 기본으로 적용한다.

| API | HTTP 상태 | 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---|---:|---|---|---|
| API-015 | 404 | `NOT_FOUND` | Job 없음, 비소유 자기소개서 또는 삭제된 자기소개서에 연결된 Job | polling을 종료하고 원래 도메인 화면 또는 목록을 다시 조회한다. |
| API-016 | 404 | `NOT_FOUND` | Job 없음, 접근 불가 또는 삭제된 자기소개서에 연결된 Job | 스트림 재연결을 중단하고 API-015 또는 도메인 조회로 최종 확인한다. |

브라우저 SSE 클라이언트가 초기 연결의 HTTP body를 읽지 못할 수 있으므로 API-016 `onerror`에서 오류 코드를 직접 추측하지 않는다. API-015 또는 도메인 조회를 호출해 인증 실패, 대상 없음과 진행 상태를 확인하고, 연결 장애가 지속되면 polling으로 전환한다.
