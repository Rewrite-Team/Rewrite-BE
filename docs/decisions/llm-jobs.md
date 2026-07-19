# LLM Job API Decisions

## Decision 001: LLM 작업은 비동기 Job 방식으로 처리한다

### 결정

첨삭, 재첨삭, 키워드 분석, 면접 질문 생성, 면접 답변 피드백 같은 LLM 기반 작업은 비동기 Job으로 처리한다.

클라이언트는 LLM 작업 요청 시 즉시 `jobId`를 받고, 이후 상태 조회 API 또는 스트리밍 API를 통해 진행 상태와 결과를 받는다.

API 형태:

```http
POST /cover-letters/{coverLetterId}/submit
POST /cover-letters/{coverLetterId}/review-versions
POST /cover-letters/{coverLetterId}/keyword-analysis
POST /cover-letters/{coverLetterId}/interviews
POST /interview-threads/{threadId}/messages

GET /llm-jobs/{jobId}
GET /llm-jobs/{jobId}/stream
```

### PRD 근거

- 자기소개서 등록 완료 후 목록에서 AI 첨삭 여부를 `첨삭 중`, `첨삭 완료`로 보여줘야 한다.
- AI 리포트는 스트리밍 형식으로 보여줘야 한다.
- 재첨삭 시 새 버전이 생성되어 저장되어야 한다.
- 키워드 분석과 AI 면접도 LLM 작업 결과를 화면에 저장된 형태로 보여준다.

### 고려한 대안

1. 요청 즉시 스트리밍 응답
   - LLM 요청 API가 바로 스트리밍 응답을 반환한다.
   - 구현 모델은 단순해 보이지만, 새로고침, 페이지 이동, 네트워크 끊김, 목록의 `첨삭 중` 상태 복구가 어렵다.

2. 비동기 Job + 상태 조회 + 스트리밍 이벤트
   - LLM 요청 API는 `jobId`를 반환한다.
   - 클라이언트는 Job 상태를 조회하거나 스트림에 연결해 결과를 받는다.
   - 작업 상태와 결과를 서버에 저장할 수 있어 목록, 상세, 실패 재시도, 버전 관리와 잘 맞는다.

### 선택 이유

PRD가 `첨삭 중`, `첨삭 완료`, 버전 히스토리, 분석 결과, 질문별 대화방처럼 장기 실행 작업과 저장된 결과를 전제로 하고 있으므로 비동기 Job 방식이 더 적합하다.

### 트레이드오프

- 장점
  - 긴 LLM 작업의 진행 상태를 안정적으로 표현할 수 있다.
  - 사용자가 화면을 이동하거나 새로고침해도 작업 상태를 복구할 수 있다.
  - 실패, 재시도, 부분 결과 저장, 버전 생성 정책을 명확히 만들 수 있다.

- 단점
  - `LlmJob` 리소스와 상태 전이 모델이 추가되어 API와 저장 구조가 복잡해진다.
  - 프론트엔드는 Job 생성, 상태 조회, 스트림 구독을 함께 처리해야 한다.



## Decision 005: LLM 스트리밍 프로토콜은 SSE를 사용한다

### 결정

LLM 작업의 스트리밍 응답은 SSE(Server-Sent Events)를 사용한다.

클라이언트는 비동기 LLM Job 생성 후 `GET /llm-jobs/{jobId}/stream`에 연결해 진행 이벤트와 토큰 delta를 수신한다.

API 형태:

```http
GET /llm-jobs/{jobId}/stream
Accept: text/event-stream
```

응답 Content-Type:

```http
Content-Type: text/event-stream
```

### PRD 근거

- AI 리포트는 스트리밍 형식으로 보여줘야 한다.
- AI 면접에서 사용자의 답변을 보고 LLM은 스트리밍 형식으로 답변을 수정해주고 꼬리질문을 이어간다.
- PRD의 실시간 요구는 대부분 서버가 LLM 결과를 클라이언트로 흘려보내는 단방향 스트리밍이다.

### 고려한 대안

1. SSE
   - 서버에서 클라이언트로 이벤트를 전송하는 단방향 스트리밍 방식이다.
   - LLM 응답 delta, 작업 진행률, 완료 이벤트를 표현하기 좋다.
   - 일반 HTTP 기반이라 구현과 운영이 비교적 단순하다.

2. WebSocket
   - 클라이언트와 서버가 양방향으로 메시지를 주고받을 수 있다.
   - 채팅 UI에는 자연스럽지만, 연결 관리, 인증, 재연결, 스케일아웃 처리가 더 복잡하다.

### 선택 이유

Rewrite의 스트리밍 요구는 대부분 LLM 작업 결과를 서버가 클라이언트로 보내는 흐름이다. 사용자의 답변 입력은 일반 `POST` API로 처리하고, LLM 피드백만 SSE로 수신하면 PRD 요구사항을 충족할 수 있다.

### 트레이드오프

- 장점
  - LLM 응답 스트리밍 구현이 단순하다.
  - Job 기반 상태 조회 API와 잘 맞는다.
  - HTTP 인프라와 브라우저 `EventSource`를 활용할 수 있다.

- 단점
  - 클라이언트에서 서버로 보내는 실시간 메시지에는 적합하지 않다.
  - 인증 헤더를 직접 넣기 어려운 브라우저 `EventSource` 제약을 고려해야 한다.
  - 양방향 실시간 기능이 필요해지면 WebSocket 도입을 재검토해야 한다.



## Decision 017: LLM Job은 실패 시 서버에서 1회 자동 재시도한다

### 결정

LLM Job은 LLM API 호출 실패나 일시적 네트워크 오류가 발생하면 서버에서 최대 1회 자동 재시도한다.

최초 시도와 재시도를 포함해 `maxAttempts`는 2이다. 두 번 모두 실패하면 Job은 `FAILED` 상태로 남고, 사용자가 같은 작업을 다시 시작해야 한다.

예상 Job 필드:

```json
{
  "attempt": 1,
  "maxAttempts": 2
}
```

### PRD 근거

- 자기소개서 첨삭, 키워드 분석, AI 면접은 LLM 응답에 의존한다.
- 사용자는 목록과 상세 화면에서 LLM 작업 상태를 확인한다.

### 고려한 대안

1. 자동 재시도 없음
   - 실패 시 즉시 `FAILED` 상태로 남긴다.
   - 구현은 단순하지만 일시적 오류에도 사용자가 직접 다시 실행해야 한다.

2. 서버 자동 재시도 1회
   - 일시적 오류가 발생하면 서버가 한 번 더 시도한다.
   - 사용자에게 바로 실패를 노출하기 전에 복구 기회를 가진다.

3. 자동 재시도 + 사용자 수동 재시도 API
   - 가장 견고하지만 MVP API로는 복잡하다.

### 선택 이유

LLM API는 일시적 실패가 발생할 수 있다. 사용자에게 즉시 실패를 보여주기보다 서버가 1회 자동 재시도하는 것이 사용자 경험과 구현 복잡도 사이의 균형이 좋다.

### 트레이드오프

- 장점
  - 일시적 LLM 오류에 더 강하다.
  - 사용자가 직접 재실행해야 하는 상황을 줄인다.
  - 재시도 횟수를 1회로 제한해 비용 증가를 통제한다.

- 단점
  - 실패한 작업도 최대 한 번 추가 비용이 발생할 수 있다.
  - Job 상태 관리에 `attempt`, `maxAttempts`가 필요하다.
  - 재시도 중에는 사용자가 실패 여부를 즉시 알기 어렵다.



## Decision 022: 같은 자기소개서의 LLM Job은 동시에 하나만 실행한다

### 결정

같은 자기소개서에 대해 진행 중인 LLM Job은 동시에 하나만 허용한다.

진행 중 상태:

```text
PENDING
PROCESSING
```

진행 중인 Job이 있는 자기소개서에 대해 새 LLM Job을 시작하려고 하면 `CONFLICT` 에러를 반환한다.

대상 Job:

```text
COVER_LETTER_REVIEW
COVER_LETTER_RE_REVIEW
KEYWORD_ANALYSIS
INTERVIEW_INITIAL_QUESTION_GENERATION
INTERVIEW_ADDITIONAL_QUESTION_GENERATION
INTERVIEW_MESSAGE_FEEDBACK
```

에러:

```json
{
  "error": {
    "code": "LLM_JOB_ALREADY_RUNNING",
    "message": "이미 진행 중인 LLM 작업이 있습니다."
  }
}
```

### PRD 근거

- 첨삭, 키워드 분석, AI 면접은 모두 특정 자기소개서를 기반으로 동작한다.
- 첨삭 버전, 키워드 분석, 면접 질문은 자기소개서의 최신 상태 또는 특정 첨삭 버전에 의존한다.

### 고려한 대안

1. 같은 자기소개서 + 같은 Job type만 동시 실행 금지
   - 예: 첨삭 중 추가 첨삭은 막지만 키워드 분석은 허용한다.
   - 병렬 사용성은 좋지만 결과 기준이 복잡해질 수 있다.

2. 같은 자기소개서에 대해 모든 LLM Job 동시 실행 금지
   - 자기소개서 단위로 LLM 작업을 직렬화한다.
   - 데이터 정합성과 비용 통제가 쉽다.

3. 모든 동시 실행 허용
   - UX 제한은 적지만 중복 비용과 결과 충돌이 생길 수 있다.

### 선택 이유

MVP에서는 LLM 작업 결과의 기준이 명확한 것이 중요하다. 첨삭, 키워드 분석, 면접 질문 생성은 모두 자기소개서와 첨삭 버전에 의존하므로, 같은 자기소개서에 대해 LLM 작업을 직렬화하는 것이 안전하다.

### 트레이드오프

- 장점
  - 중복 LLM 비용을 줄인다.
  - 첨삭 버전, 키워드 분석, 면접 결과의 기준이 명확하다.
  - Job 상태 관리와 사용자 안내가 단순하다.

- 단점
  - 사용자가 같은 자기소개서에서 여러 AI 기능을 병렬로 실행할 수 없다.
  - 긴 첨삭 작업 중에는 키워드 분석이나 면접 시작이 대기해야 한다.
  - 향후 고급 사용자 경험을 위해 Job type별 병렬 허용을 재검토할 수 있다.



## Decision 024: SSE 재연결 시 전체 결과를 재조회한다

> Superseded by Decision 076. 첨삭 Job 재연결은 완료 문항 스냅샷을 먼저 전송하는 방식으로 변경한다.

### 결정

SSE 연결이 끊겼을 때 서버는 `Last-Event-ID` 기반 이어받기를 지원하지 않는다.

클라이언트는 SSE 연결이 끊기면 `GET /llm-jobs/{jobId}`로 Job 상태를 조회한다. Job이 완료된 상태라면 `resultRef`를 기준으로 결과 상세 API를 다시 조회한다.

복구 흐름:

```text
SSE 연결 끊김
-> GET /llm-jobs/{jobId}
-> status가 PROCESSING이면 stream 재연결
-> status가 COMPLETED이면 resultRef 기준 상세 API 조회
-> status가 FAILED 또는 CANCELED이면 상태 메시지 표시
```

### PRD 근거

- AI 리포트와 면접 피드백은 스트리밍 형식으로 보여준다.
- LLM 작업 결과는 첨삭 버전, 키워드 분석 결과, 면접 메시지로 저장된다.

### 고려한 대안

1. 재연결 시 전체 결과 재조회
   - SSE는 실시간 표시 채널로만 사용한다.
   - 신뢰 가능한 복구는 저장된 결과 API로 처리한다.

2. SSE `Last-Event-ID`로 이어받기
   - 끊긴 지점부터 이벤트를 재전송한다.
   - UX는 좋지만 이벤트 저장소와 재전송 로직이 필요하다.

### 선택 이유

MVP에서는 SSE 이벤트를 영속 저장하는 것보다, 최종 결과를 리소스로 저장하고 상세 API로 재조회하는 방식이 더 단순하고 안정적이다.

### 트레이드오프

- 장점
  - SSE 서버 구현이 단순하다.
  - 이벤트 저장소가 필요 없다.
  - 최종 결과 복구는 기존 상세 조회 API를 재사용할 수 있다.

- 단점
  - 스트리밍 중간 지점부터 정확히 이어받을 수 없다.
  - 연결이 끊겼다가 다시 붙으면 일부 토큰이 중복 표시되거나 누락될 수 있어 프론트가 전체 결과 재조회로 화면을 동기화해야 한다.



## Decision 033: 키워드 분석과 면접 세션은 Job 실패 시 FAILED 상태로 저장한다

### 결정

키워드 분석과 면접 질문 생성은 작업 시작 시 결과 리소스를 먼저 생성하고, LLM Job 결과에 따라 리소스 상태를 갱신한다.

키워드 분석:

```text
분석 시작: KeywordAnalysis(status=PROCESSING)
분석 성공: KeywordAnalysis(status=COMPLETED)
분석 실패: KeywordAnalysis(status=FAILED)
```

AI 면접:

```text
면접 시작: InterviewSession(status=QUESTION_GENERATING)
질문 생성 성공: InterviewSession(status=ACTIVE)
질문 생성 실패: InterviewSession(status=FAILED)
```

기존 `ACTIVE` 면접 세션에서 `새로운 질문 추가하기`로 질문 추가 생성 Job을 실행하는 경우에는 예외적으로 `InterviewSession.status`를 `ACTIVE`로 유지한다. 추가 질문 생성 실패는 `LlmJob.status=FAILED`와 `LlmJob.error`로만 표현하고, 기존 질문과 대화방은 그대로 유지한다.

LLM Job도 별도로 `COMPLETED` 또는 `FAILED` 상태를 가진다.

### PRD 근거

- 키워드 분석은 버튼을 누르면 LLM이 분석을 시작하고 결과를 보내준다.
- 모의면접 시작 버튼을 누르면 LLM이 예상 질문 리스트를 생성한다.
- 새로운 질문 추가하기 버튼을 누르면 기존 면접 세션에 예상 질문 리스트를 추가 생성한다.
- LLM 작업은 비동기 Job으로 처리하기로 결정했다.

### 고려한 대안

1. Job만 `FAILED`로 남기고 결과 리소스는 만들지 않음
   - 실패 결과 리소스가 남지 않는다.
   - 하지만 화면이 Job 상태를 별도로 추적해야 하고, 면접 시작 실패 시 세션 상태 기준이 모호하다.

2. 작업 시작 시 결과 리소스를 만들고 실패 시 리소스도 `FAILED`로 저장
   - 화면이 결과 리소스 상태만 봐도 진행/실패를 알 수 있다.
   - 실패 상태가 도메인 리소스에 남는다.

### 선택 이유

키워드 분석과 면접 세션은 사용자가 화면에서 직접 진입하는 리소스다. Job 상태만으로 실패를 표현하면 화면 복구와 상태 표시가 복잡해진다. 따라서 결과 리소스에도 진행/성공/실패 상태를 저장한다.

단, 질문 추가 생성은 이미 사용 가능한 면접 세션에 질문을 덧붙이는 작업이다. 이 Job 실패를 세션 `FAILED`로 전파하면 기존 면접 진행 상태까지 실패처럼 보이므로, 추가 질문 생성 실패는 Job 실패로만 남긴다.

### 트레이드오프

- 장점
  - 화면이 리소스 상태만으로 진행/실패를 표현할 수 있다.
  - 새로고침 후에도 실패 상태를 복구하기 쉽다.
  - Job과 결과 리소스의 관계가 명확하다.

- 단점
  - 실패한 결과 리소스가 저장소에 남는다.
  - 실패 리소스를 다시 시도할 때 대체할지 삭제할지 추가 정책이 필요할 수 있다.



## Decision 034: FAILED 키워드 분석과 면접 세션은 같은 리소스로 재시도한다

### 결정

키워드 분석 또는 면접 질문 생성이 실패해 결과 리소스가 `FAILED` 상태가 된 경우, 사용자는 같은 기능을 다시 실행할 수 있다.

재시도 시 새 결과 리소스를 만들지 않고 기존 `FAILED` 리소스의 상태를 `PROCESSING` 또는 `QUESTION_GENERATING`으로 되돌린다.

키워드 분석:

```text
KeywordAnalysis(status=FAILED)
-> POST /cover-letters/{coverLetterId}/keyword-analysis
-> 같은 keywordAnalysisId로 PROCESSING 전환
```

AI 면접:

```text
InterviewSession(status=FAILED)
-> POST /cover-letters/{coverLetterId}/interviews
-> 같은 interviewSessionId로 QUESTION_GENERATING 전환
```

기존 `ACTIVE` 면접 세션에서 질문 추가 생성 Job만 실패한 경우 `InterviewSession.status`는 `FAILED`가 아니므로 이 재시도 정책을 적용하지 않는다. 사용자는 `POST /interviews/{interviewSessionId}/questions`를 다시 호출해 새 질문 추가 Job을 시작한다.

### PRD 근거

- 키워드 분석과 AI 면접은 사용자가 버튼을 눌러 LLM 작업을 시작하는 기능이다.
- LLM 작업은 일시적으로 실패할 수 있고, 서버 자동 재시도 1회 후에도 실패할 수 있다.
- 질문 추가 생성은 기존 면접 세션을 유지한 채 별도 LLM Job으로 실패/재시도한다.

### 고려한 대안

1. `FAILED` 리소스가 있으면 재시도 허용, 같은 리소스를 다시 `PROCESSING`으로 전환
   - 사용자가 실패 후 다시 시도할 수 있다.
   - 키워드 분석 최신 결과 1개, 자기소개서당 면접 세션 1개 정책과 일관된다.

2. `FAILED` 리소스가 있으면 재시도 불가
   - 상태는 단순하지만 일시적 장애 후 사용자가 복구할 방법이 없다.

3. `FAILED` 리소스는 유지하고 새 리소스 생성
   - 실패 이력이 남지만, 최신 결과 1개/세션 1개 정책과 충돌한다.

### 선택 이유

Rewrite는 키워드 분석 결과를 자기소개서별 하나만 유지하고, 면접 세션도 자기소개서당 하나만 유지하기로 했다. 따라서 실패 후 재시도도 같은 리소스를 재사용하는 것이 가장 일관적이다.

### 트레이드오프

- 장점
  - 사용자가 일시적 실패에서 복구할 수 있다.
  - 리소스 개수가 늘어나지 않는다.
  - 기존 “하나만 유지” 정책과 일관된다.

- 단점
  - 이전 실패의 상세 이력은 리소스에서 덮어써질 수 있다.
  - 실패 이력 분석이 필요하면 Job 로그를 별도로 봐야 한다.



## Decision 061: 첨삭 진행 중 화면 복구는 partial text 저장 방식으로 처리한다

> Superseded by Decision 075. 필드별 partial text 대신 완성된 문항 결과를 임시 영속 저장한다.

### 결정

첨삭 Job(`COVER_LETTER_REVIEW`, `COVER_LETTER_RE_REVIEW`)은 진행 중 생성된 텍스트를 문항별/필드별 partial result로 누적 저장한다.

프론트엔드는 `REVIEWING` 화면에 중간 재진입하거나 새로고침한 경우, Job 상태 조회 또는 자기소개서 상세 조회의 `partialResult`를 먼저 렌더링한다. 이후 `jobId`로 SSE에 연결하고, 새로 수신한 delta를 기존 partial text 뒤에 이어붙인다.

적용 API:

```http
GET /cover-letters/{coverLetterId}
GET /llm-jobs/{jobId}
GET /llm-jobs/{jobId}/stream
```

Partial result 형태:

```json
{
  "partialResult": {
    "questions": [
      {
        "questionId": "clq_01HZ_1",
        "order": 1,
        "aiReport": "지원 동기는 구체적이지만 직무 경험과의 연결이 더 필요합니다.",
        "rewrittenAnswer": "저는 백엔드 개발자로서..."
      },
      {
        "questionId": "clq_01HZ_2",
        "order": 2,
        "aiReport": "프로젝트 경험의 문제 상황은 잘 드러나지만",
        "rewrittenAnswer": ""
      }
    ]
  }
}
```

SSE는 `Last-Event-ID` 기반 이벤트 replay를 지원하지 않는다. 이미 생성된 텍스트는 SSE replay가 아니라 `partialResult`로 복구한다.

첨삭 Job이 완료되면 partial result를 최종 `ReviewVersionQuestionResult`로 확정하고 `ReviewVersion`을 생성한다. 실패한 Job의 partial result는 최종 첨삭 결과가 아니므로 결과 화면처럼 취급하지 않는다.

### PRD 근거

- AI 리포트는 스트리밍 형식으로 보여줘야 한다.
- LLM 작업은 비동기 Job으로 처리하며, 사용자는 화면을 벗어났다가 다시 들어올 수 있다.
- 진행 중 화면에 중간 재진입했을 때 이미 생성된 문장 앞부분이 사라지면 스트리밍 UI가 깨진다.

### 고려한 대안

1. 중간 진입 시 progress만 보여주고 이후 delta만 스트리밍
   - 구현은 가장 단순하다.
   - 하지만 문장 앞부분 없이 뒤쪽 delta만 보일 수 있어 UI 품질이 낮다.
   - 이를 피하려면 중간 진입 사용자는 delta 텍스트를 숨겨야 하므로 스트리밍 경험이 제한된다.

2. Partial text 저장
   - 지금까지 생성된 텍스트를 먼저 보여주고 이후 delta를 이어붙일 수 있다.
   - 새로고침/재진입 후에도 스트리밍 UI가 자연스럽다.
   - 이벤트 replay보다 구현 범위가 현실적이다.

3. SSE 이벤트 replay 지원
   - 이벤트 단위로 가장 정확하게 복구할 수 있다.
   - 하지만 이벤트 로그 저장, event id 관리, replay 범위 관리가 필요해 구현 복잡도가 크다.

### 선택 이유

사용자가 첨삭 진행 중 화면에 다시 들어왔을 때 “이미 생성된 문장 + 이후 생성되는 문장”이 자연스럽게 이어져야 한다. partial text 저장 방식은 이 UX를 제공하면서도 SSE 이벤트 replay보다 구현 부담이 낮다.

최종 결과는 기존처럼 `ReviewVersion`에 저장하되, 진행 중에는 partial result를 화면 복구용 임시 결과로 사용한다.

### 트레이드오프

- 장점
  - 새로고침이나 중간 재진입 후에도 생성된 텍스트를 복구할 수 있다.
  - 문장 일부만 갑자기 보이는 문제를 피할 수 있다.
  - SSE replay 없이도 스트리밍 UI를 자연스럽게 이어갈 수 있다.
  - 완료 시 기존 `ReviewVersionQuestionResult` 구조로 확정할 수 있다.

- 단점
  - 진행 중 partial result 저장소가 필요하다.
  - delta마다 저장하면 쓰기 부하가 커질 수 있어 저장 주기나 buffering 정책이 필요하다.
  - 실패한 Job의 partial result를 사용자에게 어떻게 보여줄지 별도 UX 정책이 필요할 수 있다.



## Decision 062: MVP에서는 partial result를 서버 메모리에 저장하고 이후 cache 저장소로 이전한다

> Superseded by Decision 075. 첨삭 진행 결과는 완료된 문항 단위로 DB에 임시 저장한다.

### 결정

MVP에서는 첨삭 Job의 `partialResult`를 서버 메모리에 저장한다.

이후 Redis 같은 외부 cache 저장소로 이전할 수 있도록 partial result 저장소는 교체 가능한 내부 인터페이스로 분리한다.

적용 대상:

```text
COVER_LETTER_REVIEW partialResult
COVER_LETTER_RE_REVIEW partialResult
```

MVP 한계:

```text
서버 재시작: 진행 중 partialResult 유실 가능
프로세스 종료: 진행 중 partialResult 유실 가능
스케일아웃: 요청이 다른 인스턴스로 가면 partialResult 조회 불가 가능
```

Job이 완료되면 LLM 호출의 최종 accumulated output을 기준으로 `ReviewVersionQuestionResult`를 확정하고 DB에 저장한다. 완료된 첨삭 결과는 서버 메모리에 저장된 partial result에 의존하지 않는다.

### PRD 근거

- AI 리포트는 스트리밍 형식으로 보여줘야 한다.
- MVP 단계에서는 첨삭 진행 중 화면 복구 UX를 빠르게 구현해야 한다.
- 최종 첨삭 결과는 `ReviewVersion`으로 저장되므로, partial result는 진행 중 화면 복구용 임시 데이터다.

### 고려한 대안

1. 서버 메모리에 저장 후 향후 cache 저장소로 이전
   - MVP 구현이 가장 빠르다.
   - 별도 인프라 없이 partial result UX를 검증할 수 있다.
   - 이후 Redis 등으로 저장소를 교체할 수 있게 설계해야 한다.

2. 처음부터 Redis 같은 cache 저장소에 저장
   - 진행 중 데이터 복구 안정성이 좋다.
   - 서버 재시작과 스케일아웃에 강하다.
   - 하지만 MVP 초기 인프라와 운영 복잡도가 증가한다.

3. DB에 저장
   - 유실 가능성이 가장 낮다.
   - 하지만 delta 누적 저장에 따른 쓰기 부하와 cleanup 정책 부담이 크다.

### 선택 이유

partial result는 최종 데이터가 아니라 진행 중 스트리밍 화면 복구용 임시 데이터다. MVP에서는 서버 메모리에 저장해 구현 범위를 줄이고, 사용성이 검증되면 Redis 같은 외부 cache 저장소로 이전하는 것이 현실적이다.

다만 저장소 교체를 쉽게 하기 위해 비즈니스 로직이 서버 메모리 구현체에 직접 의존하지 않도록 내부 인터페이스를 둔다.

### 트레이드오프

- 장점
  - MVP 구현 속도가 빠르다.
  - 별도 cache 인프라 없이 partial result UX를 검증할 수 있다.
  - 저장소 인터페이스를 분리하면 Redis 전환 비용을 줄일 수 있다.

- 단점
  - 서버 재시작이나 프로세스 종료 시 진행 중 partial result가 유실될 수 있다.
  - 스케일아웃 환경에서는 같은 Job의 요청이 같은 인스턴스로 라우팅되지 않으면 복구가 불안정하다.
  - 운영 환경으로 확장하기 전 Redis 같은 외부 cache 저장소 전환이 필요하다.



## Decision 063: PROCESSING 상태에서 partialResult가 없으면 delta 텍스트를 숨기고 완료/실패만 감지한다

> Superseded by Decision 076. 첨삭 SSE는 delta가 아니라 완성된 문항 결과를 전송한다.

### 결정

`LlmJob.status=PROCESSING`인데 `partialResult`가 `null`이면 클라이언트는 진행률과 상태 메시지만 표시하고 SSE에 재연결한다.

이 경우 이미 생성된 텍스트는 복구하지 못하므로, SSE 연결 이후 수신한 delta 텍스트도 화면에 렌더링하지 않는다.

클라이언트는 SSE를 완료/실패 이벤트 감지 용도로만 사용한다. 완료되면 `resultRef.type`을 확인하고 해당 결과 API를 조회해 온전한 최종 결과를 표시한다.

Job 자체는 실패 처리하지 않는다.

적용 API:

```http
GET /cover-letters/{coverLetterId}
GET /llm-jobs/{jobId}
GET /llm-jobs/{jobId}/stream
```

표시 정책:

```text
PROCESSING + partialResult 있음:
  partialResult 렌더링 후 SSE 재연결

PROCESSING + partialResult null:
  progress 렌더링 후 SSE 재연결
  이미 생성된 텍스트 복구 생략
  delta 텍스트 렌더링 생략
  완료 후 resultRef.type 기준 결과 API 조회
```

### PRD 근거

- LLM 작업은 비동기 Job으로 진행되며, 사용자는 진행 중 화면에 다시 들어올 수 있다.
- MVP에서는 partial result를 서버 메모리에 저장하기로 했으므로 유실 가능성이 있다.
- partial result 유실은 화면 복구 데이터 유실이지, LLM Job 실패 자체는 아니다.

### 고려한 대안

1. `partialResult=null`이면 progress만 표시하고 SSE에 재연결하되 delta 텍스트도 렌더링
   - Job 상태를 훼손하지 않는다.
   - 이후 새로 수신되는 delta와 완료/실패 이벤트는 계속 받을 수 있다.
   - 하지만 문장 앞부분 없이 뒤쪽 delta만 보일 수 있어 UX가 어색하다.

2. `partialResult=null`이면 delta 텍스트는 숨기고 progress와 완료/실패만 감지
   - 문장 일부만 보이는 문제를 피할 수 있다.
   - 완료 후 `resultRef.type` 기준 결과 API 조회로 온전한 최종 결과를 표시할 수 있다.
   - 하지만 진행 중 스트리밍 텍스트 UX는 제공하지 못한다.

3. `partialResult=null`이면 Job을 실패 처리하고 재시도 유도
   - 유실 상황을 명확하게 보이게 할 수 있다.
   - 하지만 실제 LLM 작업은 정상 진행 중일 수 있으므로 도메인 상태를 잘못 망가뜨릴 위험이 크다.

### 선택 이유

partial result는 진행 중 화면 복구를 위한 임시 데이터다. 서버 메모리 저장소 특성상 유실될 수 있지만, 이것이 LLM Job 실패를 의미하지는 않는다. 따라서 Job은 계속 유지하고, 클라이언트는 progress를 표시한 뒤 SSE에 다시 연결하는 것이 가장 안전하다.

다만 이어붙일 기준 텍스트가 없는 상태에서 delta를 렌더링하면 문장 앞부분 없이 뒤쪽만 보일 수 있다. 그러므로 `partialResult=null`인 재진입 화면에서는 delta 텍스트를 숨기고, 완료 후 `resultRef.type` 기준 결과 API 조회로 온전한 최종 결과를 보여준다.

### 트레이드오프

- 장점
  - 실제 진행 중인 Job을 잘못 실패 처리하지 않는다.
  - 완료/실패 이벤트를 계속 받을 수 있다.
  - 앞부분 없는 문장 일부가 화면에 보이는 문제를 피할 수 있다.
  - MVP의 서버 메모리 저장 한계를 단순하게 처리할 수 있다.

- 단점
  - 유실 전에 생성된 텍스트는 완료 전까지 복구할 수 없다.
  - `partialResult=null`인 재진입 화면에서는 진행 중 스트리밍 텍스트 UX를 제공하지 못한다.
  - Redis 같은 외부 cache 저장소로 이전하기 전까지 재진입 UX가 불완전할 수 있다.



## Decision 064: 실패한 첨삭 Job의 partialResult는 실패 화면에 표시하지 않는다

> Superseded by Decision 082. 실패한 첨삭 Job에서도 성공한 임시 문항 결과를 API-012에 포함한다.

### 결정

`LlmJob.status=FAILED`인 첨삭 Job의 `partialResult`는 사용자-facing 실패 화면에 표시하지 않는다.

실패 화면에서는 실패 상태, 실패 메시지, 재시도 버튼을 중심으로 보여준다.

적용 화면:

```text
REVIEW_FAILED: AI 첨삭 실패 화면
```

적용 데이터:

```text
latestFirstReviewJob.status
latestFirstReviewJob.progress
latestFirstReviewJob.error
latestFirstReviewJob.createdAt
latestFirstReviewJob.completedAt
```

`latestFirstReviewJob.partialResult`가 남아 있더라도 실패 화면에는 렌더링하지 않는다.

### PRD 근거

- 실패한 첨삭 Job은 성공한 `ReviewVersion`을 생성하지 않는다.
- partial result는 진행 중 화면 복구용 임시 데이터다.
- 실패 화면의 목적은 실패 사유 안내와 재시도 유도다.

### 고려한 대안

1. 실패한 Job의 partialResult를 표시하지 않음
   - 미완성/부정확한 첨삭 결과를 사용자에게 노출하지 않는다.
   - 실패 화면의 역할이 명확하다.
   - 성공 결과와 실패 중간 결과가 섞이지 않는다.

2. 실패한 Job의 partialResult를 읽기 전용으로 표시
   - 사용자가 어디까지 생성됐는지 볼 수 있다.
   - 하지만 실패한 중간 결과를 완성된 첨삭으로 오해할 수 있다.
   - 중간 결과 품질을 보장하기 어렵다.

### 선택 이유

실패한 Job의 partial result는 최종 첨삭 결과가 아니다. 사용자에게 노출하면 미완성 결과를 신뢰하거나 복사해 사용할 수 있어 UX와 품질 측면에서 위험하다. 따라서 실패 화면에는 실패 정보와 재시도 동작만 제공한다.

### 트레이드오프

- 장점
  - 실패한 중간 결과를 완성 결과처럼 오해하는 문제를 막을 수 있다.
  - 실패 화면의 목적이 명확해진다.
  - 성공 결과는 항상 `ReviewVersion`으로만 제공된다는 규칙이 유지된다.

- 단점
  - 사용자는 실패 전까지 어느 정도 생성됐는지 볼 수 없다.
  - 디버깅용 partial 내용 확인은 운영 로그나 내부 도구에 의존해야 한다.

## Decision 085: Job 상태 조회는 복구에 필요한 필드만 반환한다

### 결정

API-015 Job 상태 조회는 `status`, `progress`, `resultRef`, `error`만 반환한다. Job ID는 path와 중복되므로 제외하고 type, target, 내부 재시도 횟수, partial result와 생성·완료 시각도 공개 응답에서 제외한다.

API-015는 실시간 진행의 주 경로가 아니라 SSE 재연결·이벤트 유실과 별도 SSE가 없는 키워드 분석·면접 Job의 상태 복구 경로로 유지한다.

### 선택 이유

최초·재첨삭은 API-012와 API-016으로 진행 화면을 구성하지만, 면접 추가 질문과 답변 피드백 등은 Job의 최종 실패 상태를 확인할 공통 fallback이 필요하다. 복구에 사용하지 않는 내부 필드를 제거하면 Job 구현과 프론트엔드 계약의 결합을 줄일 수 있다.

### 트레이드오프

- 장점: 모든 LLM Job에 공통 복구 경로를 유지하면서 응답을 최소화한다.
- 단점: 운영용 Job 메타데이터가 필요하면 별도 내부 조회가 필요하다.



## Decision 065: 실패 화면의 사용자 메시지는 LLM Job error.code 기준으로 매핑한다

> Superseded by Decision 074. 이번 공통 상세 계약에서는 실패 화면 전용 error 응답을 다루지 않는다.

### 결정

`REVIEW_FAILED` 실패 화면의 사용자 안내 문구는 `latestFirstReviewJob.error.code`를 기준으로 프론트엔드가 매핑한다.

`error.message`나 LLM provider 원문 메시지는 사용자에게 그대로 노출하지 않는다.

적용 화면:

```text
REVIEW_FAILED: AI 첨삭 실패 화면
```

적용 데이터:

```json
{
  "error": {
    "code": "LLM_PROVIDER_TIMEOUT",
    "message": "LLM provider request timed out."
  }
}
```

대표 LLM Job error code:

```text
LLM_PROVIDER_TIMEOUT
LLM_PROVIDER_UNAVAILABLE
LLM_PROVIDER_RATE_LIMITED
LLM_CONTEXT_LENGTH_EXCEEDED
LLM_CONTENT_FILTERED
LLM_PROVIDER_ERROR
```

권장 사용자 메시지:

```text
LLM_PROVIDER_TIMEOUT: AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.
LLM_PROVIDER_UNAVAILABLE: AI 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.
LLM_PROVIDER_RATE_LIMITED: 요청이 일시적으로 많아 처리하지 못했습니다. 잠시 후 다시 시도해주세요.
LLM_CONTEXT_LENGTH_EXCEEDED: 입력 내용이 너무 길어 AI가 처리하지 못했습니다.
LLM_CONTENT_FILTERED: 입력 내용 또는 생성 결과가 처리 정책에 맞지 않아 첨삭에 실패했습니다.
LLM_PROVIDER_ERROR: AI 첨삭에 실패했습니다. 잠시 후 다시 시도해주세요.
```

### PRD 근거

- 최초 첨삭 실패 후 사용자는 실패 화면에서 재시도할 수 있어야 한다.
- 실패 화면은 사용자에게 다음 행동을 알려줘야 한다.
- LLM provider의 원문 에러는 사용자에게 적합한 문구가 아닐 수 있다.

### 고려한 대안

1. 사용자용 일반 메시지만 표시
   - 가장 안전하고 단순하다.
   - 하지만 timeout, rate limit, 입력 길이 초과 같은 원인을 구분해 안내할 수 없다.

2. `error.code`별 사용자 친화 메시지 표시
   - 실패 원인에 따라 더 정확한 안내를 제공할 수 있다.
   - provider 원문을 숨기면서도 운영상 분류가 가능하다.
   - error code taxonomy를 관리해야 한다.

3. 원본 `error.message` 그대로 표시
   - 구현은 단순하다.
   - 하지만 기술적인 문구, provider 내부 메시지, 불안정한 원문이 사용자에게 노출될 수 있다.

### 선택 이유

실패 화면은 사용자에게 이해 가능한 원인과 다음 행동을 알려주는 화면이어야 한다. `error.code` 기반 매핑을 사용하면 provider 원문 노출 위험을 줄이면서도 실패 유형별로 더 정확한 문구를 보여줄 수 있다.

### 트레이드오프

- 장점
  - 사용자에게 더 적절한 실패 안내를 제공할 수 있다.
  - provider 원문이나 내부 기술 메시지를 숨길 수 있다.
  - 운영 로그와 사용자 화면의 실패 유형을 같은 code로 연결할 수 있다.

- 단점
  - error code taxonomy를 유지해야 한다.
  - 프론트엔드가 code별 문구 매핑을 관리해야 한다.
  - 새로운 실패 유형이 생기면 기본 fallback 문구와 코드 추가 정책이 필요하다.



## Decision 066: 알 수 없는 LLM Job error.code는 기본 fallback 메시지로 표시한다

> Superseded by Decision 074. 이번 공통 상세 계약에서는 실패 화면 전용 error 응답을 다루지 않는다.

### 결정

프론트엔드가 알 수 없는 LLM Job `error.code`를 받은 경우, `error.message`를 그대로 표시하지 않는다.

대신 기본 fallback 메시지를 표시한다.

기본 fallback 메시지:

```text
AI 첨삭에 실패했습니다. 잠시 후 다시 시도해주세요.
```

적용 화면:

```text
REVIEW_FAILED: AI 첨삭 실패 화면
```

### PRD 근거

- 실패 화면은 사용자에게 이해 가능한 실패 안내와 재시도 동작을 제공해야 한다.
- provider 원문이나 내부 에러 메시지는 사용자에게 적합하지 않을 수 있다.
- 새로운 실패 유형이 생겨도 사용자-facing 화면은 안전하게 동작해야 한다.

### 고려한 대안

1. 기본 fallback 메시지 표시
   - 알 수 없는 code에서도 사용자에게 안전한 문구를 보여줄 수 있다.
   - `error.message` 원문 미노출 정책과 일관된다.
   - 새 code가 추가되기 전에도 화면이 깨지지 않는다.

2. `error.message` 표시
   - 더 구체적인 정보를 보여줄 수 있다.
   - 하지만 provider 원문, 내부 기술 메시지, 민감한 내용이 노출될 수 있다.

### 선택 이유

알 수 없는 `error.code`는 프론트엔드가 사용자 친화 문구를 검증하지 않은 상태다. 이때 `error.message`를 그대로 표시하면 이전 결정의 원문 미노출 원칙이 깨진다. 따라서 안전한 기본 fallback 메시지로 처리한다.

### 트레이드오프

- 장점
  - 알 수 없는 실패 유형에서도 안전하게 사용자 안내를 제공할 수 있다.
  - provider 원문 노출 위험을 줄인다.
  - 프론트엔드 매핑 누락으로 인한 화면 품질 저하를 막는다.

- 단점
  - 구체적인 실패 원인을 사용자에게 알려주지 못한다.
  - 새로운 error code가 추가되어도 프론트 매핑 누락을 사용자가 구분하기 어렵다.
  - 운영상 매핑 누락을 감지하는 로그/모니터링이 필요할 수 있다.



## Decision 068: LLM 출력 파싱 또는 구조 검증 실패는 Job 실패로 처리한다

### 결정

LLM 응답이 서버가 기대한 결과 구조를 만족하지 못하면 해당 LLM Job은 실패로 처리한다.

실패 처리 대상:

```text
JSON 파싱 실패
필수 필드 누락
필드 타입 불일치
값 범위 위반
문항 수 또는 questionId 매핑 불일치
지원하지 않는 enum 값
```

이 경우 가능한 필드만 부분 저장하지 않는다.

서버가 누락 필드를 빈 문자열, 기본 문구, 기본 점수 등으로 임의 보정하지 않는다.

자동 재시도 정책:

```text
1차 LLM 출력 구조 검증 실패
-> 서버 자동 재시도 1회
-> 재시도 결과도 구조 검증 실패
-> LlmJob.status=FAILED
-> error.code=LLM_OUTPUT_VALIDATION_FAILED
```

적용 Job type:

```text
COVER_LETTER_REVIEW
COVER_LETTER_RE_REVIEW
KEYWORD_ANALYSIS
INTERVIEW_INITIAL_QUESTION_GENERATION
INTERVIEW_ADDITIONAL_QUESTION_GENERATION
INTERVIEW_MESSAGE_FEEDBACK
```

### PRD 근거

- LLM 결과는 자기소개서 첨삭, 키워드 분석, 면접 질문, 면접 피드백처럼 사용자 화면에 직접 표시되는 핵심 데이터다.
- 첨삭 결과와 면접 피드백은 필수 필드를 전제로 화면이 구성된다.
- 구조가 깨진 결과를 저장하면 이후 상세 조회, 버전 히스토리, 키워드 분석, 면접 화면까지 연쇄적으로 불안정해진다.

### 고려한 대안

1. 파싱/검증 실패도 LLM Job 실패로 처리
   - 깨진 결과를 저장하지 않는다.
   - 자동 재시도 1회로 일시적인 출력 실패를 복구할 수 있다.
   - 결과 저장 계약이 안정적이다.

2. 가능한 필드만 저장
   - 일부 결과라도 사용자에게 보여줄 수 있다.
   - 하지만 화면마다 null/부분 결과 처리가 필요하고, 결과 품질이 불안정해진다.

3. 서버가 fallback으로 임의 보정
   - 화면은 덜 깨질 수 있다.
   - 하지만 LLM이 생성하지 않은 내용을 서버가 만든 결과처럼 저장하게 된다.

### 선택 이유

LLM 출력은 사용자에게 직접 보이는 결과 데이터다. 구조가 깨진 응답을 부분 저장하거나 임의 보정하면 데이터 신뢰성이 떨어지고, 화면별 예외 처리가 급격히 늘어난다. 따라서 결과 구조 검증에 실패하면 Job 실패로 처리하고, 기존 자동 재시도 정책으로 한 번 더 복구를 시도하는 것이 안전하다.

### 트레이드오프

- 장점
  - 저장된 결과의 구조와 품질이 안정된다.
  - 프론트엔드가 부분 결과 예외를 처리하지 않아도 된다.
  - LLM 출력 계약 위반을 운영상 명확히 감지할 수 있다.

- 단점
  - 일부 유효한 내용이 있어도 사용자에게 제공하지 않는다.
  - LLM 출력 형식이 자주 흔들리면 실패율이 높아질 수 있다.
  - 프롬프트와 파서, schema validation 품질 관리가 중요해진다.

## Decision 076: 첨삭 SSE는 완성된 문항 단위 이벤트와 연결 시 스냅샷을 제공한다

### 결정

API-016은 최초 첨삭과 재첨삭에 공통으로 연결 직후 `job.snapshot`과 `review.questions.snapshot`을 순서대로 전송한다. 공통 스냅샷은 현재 Job `status`, `progress`, 완료 결과 `resultRef` 또는 실패 `error`를 포함하고, 첨삭 문항 스냅샷은 완료 문항 `items`를 포함한다.

문항의 `aiReport`와 `rewrittenAnswer`가 모두 완성된 시점에는 `review.question.completed` 이벤트 하나를 전송한다. 이벤트에는 완성된 문항 결과와 갱신된 `progress`를 포함하며, 필드별 또는 토큰별 delta 이벤트는 제공하지 않는다. Job 종료는 `job.completed` 또는 `job.failed`로 전달한다.

서버는 연결을 먼저 등록하고 스냅샷 전송 중 발생한 변경 이벤트를 버퍼링한 뒤 이어서 전송한다. 이벤트는 중복될 수 있으며 클라이언트는 `questionId`로 upsert하고 `order`로 정렬한다. `Last-Event-ID` 기반 영속 replay는 제공하지 않고 재연결할 때 최신 스냅샷을 다시 전송한다. 연결 유지는 SSE comment heartbeat를 사용한다.

Job ID는 path에 있으므로 이벤트 데이터에 포함하지 않는다. 연결 시점 상태는 스냅샷으로 전달하므로 `job.started`를 사용하지 않으며 진행률은 문항 완료 이벤트에 포함하므로 별도 `job.progress`도 사용하지 않는다.

### 선택 이유

AI 리포트와 수정본은 한 문항의 완결된 결과로 함께 사용한다. 문항 단위 전송은 불완전한 필드 조합을 피하고, 상태와 완료 문항을 함께 담은 연결 직후 스냅샷은 상세 조회와 SSE 연결 사이의 이벤트 누락과 종료 이벤트 유실을 복구한다.

### 트레이드오프

- 장점: 프론트엔드 상태 병합이 단순하고 새로고침·재연결 복구가 안정적이다.
- 단점: 토큰 스트리밍처럼 글자가 생성되는 즉시 보이는 효과는 제공하지 않는다.

## Decision 090: API-016은 첨삭·키워드 분석·면접 Job의 공통 SSE다

### 결정

API-016은 최초 첨삭·재첨삭, 키워드 분석, 초기·추가 면접 질문 생성, 면접 답변 피드백에 공통으로 사용한다. 모든 Job은 연결 직후 `job.snapshot`을 전송하고, 첨삭 Job은 `review.questions.snapshot`과 `review.question.completed`를 추가로 전송한다. 면접 답변 피드백 Job은 `interview.feedback.delta`로 1부터 증가하는 `sequence`와 `contentDelta`를 전송한다. 키워드 분석과 초기·추가 면접 질문 생성은 중간 도메인 이벤트 없이 `job.completed` 또는 `job.failed`만 전달한다.

완료 `resultRef.type`은 Job에 따라 `REVIEW_VERSION`, `KEYWORD_ANALYSIS`, `INTERVIEW_SESSION`, `INTERVIEW_QUESTION`, `INTERVIEW_MESSAGE`를 사용한다. 키워드 분석 완료 후 API-021을, 초기 면접 질문 생성 완료 후 API-025와 API-026을, 추가 면접 질문 생성 완료 후 API-026을, 면접 피드백 완료 후 API-029를 다시 조회한다. 도메인 조회 응답은 진행 중이거나 최근 실패한 Job ID를 제공해 새로고침 후 SSE에 다시 연결할 수 있게 하며, SSE 연결 실패 시 polling fallback으로 사용한다.

면접 피드백 delta는 현재 연결의 실시간 표시 효과만 제공하며 영속 저장하거나 replay하지 않는다. 재연결 시 이전 delta가 유실되면 이후 delta의 불완전한 뒷부분을 표시하지 않고 진행 상태만 보여준 뒤, 완료된 전체 assistant 메시지를 API-029로 조회한다.

### 선택 이유

중간 결과가 없는 작업도 시작 직후 완료 시점을 실시간으로 알릴 필요가 있다. 하나의 Job SSE 계약을 재사용하면 도메인별 스트림 API를 추가하지 않고도 시작, 새로고침 복구, 완료 후 결과 재조회를 같은 흐름으로 통일할 수 있다.

### 트레이드오프

- 장점: 비동기 시작 API와 프론트엔드의 Job 추적 흐름이 일관된다.
- 단점: 공통 이벤트와 첨삭 전용 이벤트를 구분해 처리해야 하며, 연결 실패에 대비한 도메인 polling fallback도 유지해야 한다.
