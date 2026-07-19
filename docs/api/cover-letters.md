# Cover Letter API

## Cover Letter API

등록 step1~step3의 원본 정보 수정 API는 `DRAFT` 상태에서만 사용할 수 있다. 제출 후에는 원본 자기소개서 정보, 우대사항, 질문, 원본 답변을 수정할 수 없고, AI 첨삭 화면의 최종 작성본만 저장할 수 있다.

최초 첨삭이 실패해 `REVIEW_FAILED` 상태가 된 경우에도 원본 정보 수정 API는 사용할 수 없다. `REVIEW_FAILED` 상태의 submit 재시도는 이미 저장된 원본 입력값으로 다시 첨삭을 요청한다.

`DRAFT`가 아닌 자기소개서에 원본 수정 API를 호출하면 `CONFLICT`와 `COVER_LETTER_NOT_DRAFT`를 반환한다.

### 내 자기소개서 목록 조회

```http
GET /cover-letters?page=1&size=9&status=REVIEWED
```

Query:

```text
page: 1 이상의 숫자
size: 기본 9, 최대 9
status: 선택, DRAFT | REVIEWING | REVIEWED | REVIEW_FAILED
```

Response:

```json
{
  "items": [
    {
      "id": "cl_01HZ...",
      "title": "2026 상반기 백엔드 개발자 자기소개서",
      "companyName": "Rewrite Corp",
      "positionTitle": "백엔드 개발자",
      "displayStatus": "REVIEWING",
      "createdAt": "2026-06-20T14:00:00",
      "latestReviewVersionId": "rv_01HZ..."
    }
  ],
  "page": 1,
  "size": 9,
  "totalItems": 21,
  "totalPages": 3
}
```

`displayStatus`는 메인 목록 카드가 그대로 사용하는 표시 상태이며 `WRITING | REVIEWING | REVIEWED | REVIEW_FAILED` 중 하나다. 현재 `PENDING` 또는 `PROCESSING`인 최초 첨삭이나 재첨삭 Job이 있으면 내부 `CoverLetter.status`와 관계없이 `REVIEWING`을 반환한다. 진행 중 Job이 없으면 `DRAFT → WRITING`, `REVIEWED → REVIEWED`, `REVIEW_FAILED → REVIEW_FAILED`로 변환한다. 목록 응답에는 내부 `CoverLetter.status`와 Job ID, type, status를 노출하지 않는다. 기존 `status` query filter는 persistence 상태인 `DRAFT | REVIEWING | REVIEWED | REVIEW_FAILED` 의미를 유지한다.

목록 카드 클릭 시 프론트엔드는 `displayStatus`에 따라 이동 화면을 결정한다.

```text
WRITING: 등록 step 화면
REVIEWING: AI 첨삭 진행 화면
REVIEWED: AI 첨삭 결과 화면
REVIEW_FAILED: AI 첨삭 실패 화면
```

`REVIEW_FAILED`의 실패 화면에서는 저장된 원본 입력값을 표시할 수 있지만 원본 수정은 제공하지 않는다. 사용자는 실패 화면에서 submit 재시도를 실행할 수 있다.

### 내 자기소개서 첨삭 상태 스트림

메인 화면은 카드마다 연결하지 않고 현재 사용자 기준 SSE 연결 하나만 유지한다.

```http
GET /cover-letters/stream
Accept: text/event-stream
```

응답 Content-Type:

```http
Content-Type: text/event-stream
```

연결 직후 현재 사용자의 soft delete되지 않은 모든 자기소개서에 대해 현재 표시 상태 스냅샷을 하나씩 먼저 전송하고, 이후 최초 첨삭·재첨삭의 시작, 완료, 실패처럼 메인 화면에 필요한 변경만 전송한다. 내부 Job의 `PENDING → PROCESSING` 전환은 `displayStatus=REVIEWING`을 바꾸지 않으므로 별도 이벤트를 전송하지 않는다. `eventPhase`는 연결 직후 상태이면 `SNAPSHOT`, 이후 변경이면 `CHANGE`다.

`changeType`은 `SNAPSHOT`에서 `null`이고, `CHANGE`에서 `REVIEW_STARTED | REVIEW_COMPLETED | REVIEW_FAILED` 중 하나다. `displayStatus` 계산 규칙은 API-007 목록 응답과 같다.

```text
event: cover-letter.review-status.changed
data: {"eventPhase":"CHANGE","coverLetterId":"cl_01HZ...","displayStatus":"REVIEWED","latestReviewVersionId":"rv_01HZ...","changeType":"REVIEW_FAILED"}
```

프론트엔드는 스냅샷과 변경 이벤트 모두에서 목록 항목의 `displayStatus`와 `latestReviewVersionId`를 그대로 함께 갱신한다. Job 상태를 조합하거나 자기소개서 상태를 추론하지 않는다. `CHANGE`에서는 `changeType`으로 성공·실패 알림을 처리한다.

따라서 목록 조회와 스트림 연결 사이에 Job이 종료되어도 `SNAPSHOT`으로 최신 표시 상태를 복구한다. 재첨삭 실패 시 기존 첨삭 결과 접근을 유지하도록 `displayStatus=REVIEWED`와 기존 `latestReviewVersionId`를 반환하고, 실시간 `CHANGE` 이벤트의 `changeType=REVIEW_FAILED`로 실패 알림을 명시한다. 최초 첨삭 실패는 `displayStatus=REVIEW_FAILED`, `latestReviewVersionId=null`, `changeType=REVIEW_FAILED`다.

이벤트는 현재 페이지에 표시된 항목으로 제한하지 않고 현재 사용자의 모든 자기소개서 상태를 전달하며, 프론트엔드는 현재 목록에 없는 `coverLetterId`를 무시한다.

서버는 사용자 연결을 이벤트 라우터에 먼저 등록한 뒤 스냅샷을 조회·전송하고, 그 사이 발생한 변경 이벤트는 스냅샷 전송이 끝날 때까지 연결별로 버퍼링했다가 발생 순서대로 전송한다. 이 스트림은 `Last-Event-ID` 영속 replay를 제공하지 않는다. 재연결하면 전체 현재 상태 스냅샷을 다시 받은 뒤 실시간 이벤트를 수신한다.

### 자기소개서 생성

자기소개서 작성 플로우를 시작할 때 빈 초안을 생성한다.

```http
POST /cover-letters
```

Related Requirement:

```text
REQ-003
```

Request:

```text
No request body.
```

서버는 현재 사용자의 비어 있는 `DRAFT` 자기소개서를 생성하고 id를 반환한다. 기본 정보, 우대사항, 질문과 답변은 각 step 저장 API에서 입력받는다.

Validation:

```text
요청 본문이 없으므로 request body validation은 없다.
현재 사용자 식별은 공통 인증 경계 또는 개발용 CurrentUserProvider를 통해 수행한다.
```

Success Status:

```text
201 Created
```

Response:

```json
{
  "id": "cl_01HZ...",
  "status": "DRAFT",
  "createdAt": "2026-06-20T14:00:00"
}
```

Error Response:

공통 인증 도입 후 인증되지 않은 요청은 `UNAUTHORIZED`를 반환한다.
개발 단계에서는 `DevCurrentUserProvider`를 사용하므로 이 API 자체에서 별도 인증 실패를 발생시키지 않는다.

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다.",
    "details": []
  }
}
```

### 기본 정보 저장

```http
PUT /cover-letters/{coverLetterId}/basic-info
```

등록 step1의 기본 정보를 전체 replace 방식으로 저장한다. 요청 본문은 step1 기본 정보 전체를 포함해야 하며, 서버는 요청 본문 값으로 기존 기본 정보를 교체한다.

Request:

```json
{
  "title": "2026 상반기 백엔드 개발자 자기소개서",
  "companyName": "Rewrite Corp",
  "positionTitle": "백엔드 개발자",
  "jobPostingUrl": "https://example.com/jobs/1"
}
```

Validation:

```text
coverLetter.status는 DRAFT여야 한다.
title: 필수, trim 후 Unicode code point 기준 1~50자
companyName: 필수, trim 후 Unicode code point 기준 1~30자
positionTitle: 필수, trim 후 Unicode code point 기준 1~30자
jobPostingUrl: 선택, trim 후 최대 500자, URL 형식
```

서버는 `title`, `companyName`, `positionTitle`의 앞뒤 공백을 제거한 뒤 길이를 검증하고, 공백이 제거된 값을 저장한다. trim 후 빈 문자열이면 `VALIDATION_ERROR`를 반환한다.

서버는 `jobPostingUrl`의 앞뒤 공백을 제거한 뒤 URL 형식을 검증하고, 공백이 제거된 값을 저장한다. `jobPostingUrl`이 누락되거나 trim 후 빈 문자열이면 `null`로 저장한다. `jobPostingUrl`은 URL 형식만 검증한다. 백엔드는 해당 URL이 실제로 접근 가능한지, 로그인 없이 열리는지, 채용공고가 만료되지 않았는지는 검증하지 않는다.

Conflict Response:

```json
{
  "error": {
    "code": "COVER_LETTER_NOT_DRAFT",
    "message": "제출된 자기소개서의 원본 정보는 수정할 수 없습니다.",
    "details": []
  }
}
```

Response:

```json
{
  "id": "cl_01HZ...",
  "title": "2026 상반기 백엔드 개발자 자기소개서",
  "companyName": "Rewrite Corp",
  "positionTitle": "백엔드 개발자",
  "jobPostingUrl": "https://example.com/jobs/1",
  "status": "DRAFT",
  "updatedAt": "2026-06-20T14:05:00"
}
```

### 채용 우대사항 저장

```http
PUT /cover-letters/{coverLetterId}/preferences
```

등록 step2의 채용 우대사항을 전체 replace 방식으로 저장한다. 요청의 `preferences` 값이 기존 우대사항 전체 텍스트를 대체한다.

Request:

```json
{
  "preferences": "Spring Boot 경험, 대용량 트래픽 처리 경험 우대"
}
```

Validation:

```text
coverLetter.status는 DRAFT여야 한다.
preferences: 필수, trim 후 Unicode code point 기준 1~3000자
```

서버는 `preferences`의 앞뒤 공백을 제거한 뒤 길이를 검증하고, 공백이 제거된 값을 저장한다. trim 후 빈 문자열이면 `VALIDATION_ERROR`를 반환한다.

Conflict Response:

```json
{
  "error": {
    "code": "COVER_LETTER_NOT_DRAFT",
    "message": "제출된 자기소개서의 원본 정보는 수정할 수 없습니다.",
    "details": []
  }
}
```

Response:

```json
{
  "id": "cl_01HZ...",
  "preferences": "Spring Boot 경험, 대용량 트래픽 처리 경험 우대",
  "status": "DRAFT",
  "updatedAt": "2026-06-20T14:08:00"
}
```

### 질문과 답변 저장

등록 step3에서 문항 전체를 저장한다.

이 API는 전체 replace 방식이다. 요청의 `questions` 배열이 해당 자기소개서의 최종 문항 목록이 되며, 이전에 임시저장되어 있던 문항 중 요청에 포함되지 않은 문항은 삭제된다.

```http
PUT /cover-letters/{coverLetterId}/questions
```

Request:

```json
{
  "questions": [
    {
      "question": "지원 동기를 작성해주세요.",
      "maxAnswerLength": 1000,
      "originalAnswer": "제가 지원한 이유는..."
    },
    {
      "question": "직무 관련 경험을 작성해주세요.",
      "maxAnswerLength": 1500,
      "originalAnswer": "저는 프로젝트에서..."
    }
  ]
}
```

Validation:

```text
coverLetter.status는 DRAFT여야 한다.
questions: 1개 이상, 제품 정책상 최대 개수 제한 없음
questions[].question: 필수, trim 후 Unicode code point 기준 1~300자
questions[].maxAnswerLength: 필수, 100~5000
questions[].originalAnswer: 필수, trim 후 Unicode code point 기준 1~5000자
```

서버는 요청 `questions` 배열의 순서를 기준으로 문항 `order`를 1부터 재부여해 저장한다. 클라이언트는 요청에서 `order`를 보내지 않는다. 응답의 `order`는 서버가 부여한 저장 순서다.

문항 개수에는 API validation 상한을 두지 않는다. 단, HTTP request body size, DB 저장 한계, LLM provider context limit 같은 인프라/운영 한계는 별도로 적용될 수 있다. 문항 수가 많아 LLM 입력 한계를 초과하면 첨삭 Job은 `FAILED`가 될 수 있다.

서버는 `questions[].question`과 `questions[].originalAnswer`의 앞뒤 공백을 제거한 뒤 길이를 검증하고, 공백이 제거된 값을 저장한다. trim 후 빈 문자열이면 `VALIDATION_ERROR`를 반환한다.

Response:

```json
{
  "coverLetterId": "cl_01HZ...",
  "questions": [
    {
      "id": "clq_01HZ...",
      "order": 1,
      "question": "지원 동기를 작성해주세요.",
      "maxAnswerLength": 1000,
      "originalAnswer": "제가 지원한 이유는..."
    }
  ],
  "updatedAt": "2026-06-20T14:10:00"
}
```

Validation Error Response:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "details": [
      {
        "field": "questions[0].question",
        "reason": "질문을 입력해야 합니다."
      },
      {
        "field": "questions[0].maxAnswerLength",
        "reason": "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
      },
      {
        "field": "questions[0].originalAnswer",
        "reason": "답변을 입력해야 합니다."
      }
    ]
  }
}
```

Conflict Response:

```json
{
  "error": {
    "code": "COVER_LETTER_NOT_DRAFT",
    "message": "제출 이후에는 원본 자기소개서를 수정할 수 없습니다.",
    "details": []
  }
}
```

### 자기소개서 상세 조회

등록 step4 확인 화면, 최초·재첨삭 진행 화면, 최신 첨삭 결과 화면에서 공통으로 사용한다. `DRAFT`, `REVIEWING`, `REVIEW_FAILED`, `REVIEWED` 모든 상태에서 같은 응답 구조를 반환한다.

```http
GET /cover-letters/{coverLetterId}
```

Response:

```json
{
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "2026 상반기 백엔드 개발자 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자",
    "jobPostingUrl": "https://example.com/jobs/1",
    "preferences": "Spring Boot 경험, 대용량 트래픽 처리 경험 우대",
    "status": "REVIEWING"
  },
  "reviewVersion": null,
  "reviewJob": {
    "jobId": "job_01HZ...",
    "type": "COVER_LETTER_REVIEW",
    "status": "PROCESSING",
    "progress": {
      "current": 1,
      "total": 3,
      "message": "1개 문항의 첨삭이 완료되었습니다."
    }
  },
  "questions": [
    {
      "questionResultId": null,
      "questionId": "clq_01HZ...",
      "order": 1,
      "question": "지원 동기를 작성해주세요.",
      "maxAnswerLength": 1000,
      "originalAnswer": "제가 지원한 이유는...",
      "originalAnswerLength": 530,
      "aiReport": "직무 경험과 지원 동기의 연결을 보강하는 것이 좋습니다.",
      "rewrittenAnswer": "저는 백엔드 개발자로서...",
      "rewrittenAnswerLength": 820,
      "finalAnswer": "저는 백엔드 개발자로서...",
      "finalAnswerLength": 820
    },
    {
      "questionResultId": null,
      "questionId": "clq_01HY...",
      "order": 2,
      "question": "문제를 해결한 경험을 작성해주세요.",
      "maxAnswerLength": 1000,
      "originalAnswer": "프로젝트에서 발생한 문제를...",
      "originalAnswerLength": 610,
      "aiReport": null,
      "rewrittenAnswer": null,
      "rewrittenAnswerLength": null,
      "finalAnswer": null,
      "finalAnswerLength": null
    }
  ]
}
```

상태별 응답 정책:

```text
DRAFT: reviewVersion=null, reviewJob=null. 저장된 전체 질문과 원본 답변을 반환한다.
REVIEWING: reviewVersion=null, 진행 중 최초 첨삭 reviewJob과 전체 질문을 반환한다. 완료된 임시 문항 결과만 AI 필드를 채운다.
REVIEW_FAILED: reviewVersion=null, reviewJob=null. 임시 문항 결과는 숨기고 AI 필드를 모두 null로 반환한다.
REVIEWED: 최신 ReviewVersion 전체 결과를 reviewVersion과 questions에 직접 반환한다.
REVIEWED + 재첨삭 진행: reviewVersion=null, 새 재첨삭 reviewJob을 반환하고 새 작업의 임시 결과를 questions에 표시한다.
```

재첨삭 진행 시 `originalAnswer`는 새 Job의 입력으로 확정한 이전 최신 `ReviewVersion`의 `finalAnswer`다. 새 작업을 시작할 때 이전 버전의 `aiReport`, `rewrittenAnswer`, `finalAnswer`를 복사하지 않으며, 완료된 문항부터 새 값으로 채운다.

`questionResultId`는 `ReviewVersion`으로 확정된 문항 결과 ID다. 진행 중 임시 결과는 내부 staging ID를 노출하지 않고 `null`을 반환한다. 첨삭 결과가 완성된 문항의 `finalAnswer` 초깃값은 `rewrittenAnswer`와 같다.

`REVIEWED` 상태에서도 최신 결과를 보기 위해 API-018을 다시 호출하지 않는다. API-018은 버전 히스토리에서 선택한 특정 버전을 조회할 때만 사용한다.

### 자기소개서 제출 및 최초 AI 첨삭 요청

등록 step4의 완료 버튼에서 사용한다.

```http
POST /cover-letters/{coverLetterId}/submit
```

Success Status:

```text
200 OK
```

Request:

```json
{}
```

Response:

```json
{
  "coverLetterId": "cl_01HZ...",
  "status": "REVIEWING",
  "jobId": "job_01HZ...",
  "latestReviewVersionId": null
}
```

Validation:

```text
coverLetter.status가 DRAFT 또는 REVIEW_FAILED이면 새 최초 첨삭 Job을 생성할 수 있다.
coverLetter.status가 REVIEWING이면 기존 진행 중 Job을 반환한다.
coverLetter.status가 REVIEWED이면 기존 최신 첨삭 결과 정보를 반환한다.
title, companyName, positionTitle, preferences, questions가 모두 저장되어 있어야 한다.
questions는 1개 이상이어야 한다.
각 question은 question, maxAnswerLength, originalAnswer를 가져야 한다.
```

`REVIEW_FAILED` 상태에서 다시 submit을 호출하면 저장된 원본 입력값으로 새 최초 첨삭 Job을 생성하고 `CoverLetter.status`를 `REVIEWING`으로 전환한다. 이때 원본 정보 수정 API는 계속 사용할 수 없다.

`REVIEW_FAILED` 상태의 사용자 수동 재시도에는 제품 도메인상 횟수 제한을 두지 않는다. 단, LLM 비용과 남용 방지를 위한 rate limit, 사용자 quota, 운영 정책은 별도로 적용할 수 있다.

새 Job이 생성되면 submit transaction commit 이후 최초 첨삭 worker가 비동기로 실행된다. worker는 전체 자기소개서 문맥과 대상 문항을 입력으로 각 문항 호출을 병렬 실행하고, provider 오류나 출력 검증 실패가 발생한 문항만 1회 재시도한다. 문항의 `aiReport`와 `rewrittenAnswer`가 모두 완성되면 임시 문항 결과를 영속 저장하고 `review.question.completed` 이벤트를 전송한다.

모든 문항 task가 종료된 뒤 모든 임시 결과가 성공하면 최종 `ReviewVersion`과 문항별 결과로 한 transaction에서 확정하고 `CoverLetter.status`를 `REVIEWED`로 변경한다. 최종 실패하면 `LlmJob.status`는 `FAILED`, `CoverLetter.status`는 `REVIEW_FAILED`가 된다. 실패 Job의 임시 결과는 내부 진단을 위해 보존하되 사용자-facing 상세 응답에서 숨긴다.

이미 최초 첨삭 Job이 `PENDING` 또는 `PROCESSING` 상태이면 새 Job을 만들지 않고 기존 진행 중 Job을 반환한다.

이미 `REVIEWED` 상태이면 새 Job을 만들지 않고 기존 최신 `ReviewVersion` 정보를 반환한다.

필수 step 데이터가 누락된 경우 `VALIDATION_ERROR`를 반환하고, `details`에 누락된 field와 reason을 포함한다.

Validation Error Response:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "입력값이 올바르지 않습니다.",
    "details": [
      {
        "field": "preferences",
        "reason": "채용 우대사항을 입력해야 합니다."
      },
      {
        "field": "questions",
        "reason": "질문과 답변을 1개 이상 입력해야 합니다."
      }
    ]
  }
}
```

다른 종류의 LLM Job이 이미 해당 자기소개서에서 `PENDING` 또는 `PROCESSING` 상태이면 `409 Conflict`와 `LLM_JOB_ALREADY_RUNNING`을 반환한다. 동일한 최초 첨삭 Job에 대한 중복 submit은 예외로 처리하지 않고 기존 Job을 반환한다.

Already Reviewing Response:

```json
{
  "coverLetterId": "cl_01HZ...",
  "status": "REVIEWING",
  "jobId": "job_existing_01HZ...",
  "latestReviewVersionId": null
}
```

Already Reviewed Response:

```json
{
  "coverLetterId": "cl_01HZ...",
  "status": "REVIEWED",
  "jobId": null,
  "latestReviewVersionId": "rv_01HZ..."
}
```

### 자기소개서 삭제

자기소개서는 soft delete로 삭제한다. 삭제 시 `deletedAt`을 기록하고, 진행 중인 LLM Job이 있으면 `CANCELED`로 전환한다.

삭제된 자기소개서는 사용자 화면에서 복구할 수 없다. Soft delete는 내부 안전장치와 운영 보존 용도다.

삭제 이후 해당 자기소개서 및 하위 첨삭/키워드 분석/면접/Job 리소스에 대한 사용자-facing API 접근은 모두 `NOT_FOUND`를 반환한다.

```http
DELETE /cover-letters/{coverLetterId}
```

Validation:

```text
현재 사용자 소유이고 아직 삭제되지 않은 자기소개서만 삭제할 수 있다.
존재하지 않는 자기소개서, 다른 사용자 소유 자기소개서, 이미 삭제된 자기소개서는 모두 NOT_FOUND를 반환한다.
```

Response:

```json
{
  "success": true,
  "deletedAt": "2026-06-20T15:00:00"
}
```

Not Found Response:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "리소스를 찾을 수 없습니다.",
    "details": []
  }
}
```

현재 구현에서는 `llm_jobs` persistence가 아직 없으므로 진행 중 LLM Job 취소 연결은 Job skeleton 구현 이후 반영한다.
