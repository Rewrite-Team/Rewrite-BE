# Cover Letter API

## Cover Letter API

등록 step1~step3의 원본 정보 수정 API는 `DRAFT` 상태에서만 사용할 수 있다. 제출 후에는 원본 자기소개서 정보, 우대사항, 질문, 원본 답변을 수정할 수 없고, AI 첨삭 화면의 최종 작성본만 저장할 수 있다.

최초 첨삭이 실패해 `REVIEW_FAILED` 상태가 된 경우에도 원본 정보 수정 API는 사용할 수 없다. `REVIEW_FAILED` 상태의 submit 재시도는 이미 저장된 원본 입력값으로 다시 첨삭을 요청한다.

`DRAFT`가 아닌 자기소개서에 원본 수정 API를 호출하면 `CONFLICT`와 `COVER_LETTER_NOT_DRAFT`를 반환한다.

### 내 자기소개서 목록 조회

```http
GET /cover-letters?page=1&size=9&displayStatus=REVIEWED
```

Query:

```text
page: 1 이상의 숫자
size: 기본 9, 최대 9
displayStatus: 선택, WRITING | REVIEWING | REVIEWED | REVIEW_FAILED
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

`displayStatus`는 메인 목록 카드와 query filter가 함께 사용하는 표시 상태이며 `WRITING | REVIEWING | REVIEWED | REVIEW_FAILED` 중 하나다. 현재 `PENDING` 또는 `PROCESSING`인 최초 첨삭이나 재첨삭 Job이 있으면 내부 `CoverLetter.status`와 관계없이 `REVIEWING`을 반환한다. 진행 중 Job이 없으면 초안은 `WRITING`, 성공한 최신 첨삭 상태는 `REVIEWED`, 최초 또는 재첨삭의 최신 시도가 실패한 상태는 `REVIEW_FAILED`로 계산한다. 목록 응답에는 내부 `CoverLetter.status`와 Job ID, type, status를 노출하지 않는다.

`title`, `companyName`, `positionTitle`은 기본 정보 저장 전이면 `null`이며, `latestReviewVersionId`는 성공한 첨삭 버전이 없으면 `null`이다. 나머지 목록 필드는 non-null이다.

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

연결 직후 현재 사용자의 soft delete되지 않은 모든 자기소개서 표시 상태를 하나의 스냅샷 이벤트로 먼저 전송하고, 이후 상태가 바뀐 자기소개서의 변경 이벤트를 전송한다. 내부 Job의 `PENDING → PROCESSING` 전환은 `displayStatus=REVIEWING`을 바꾸지 않으므로 별도 이벤트를 전송하지 않는다.

연결 직후 스냅샷:

```text
event: cover-letter.review-status.snapshot
data: {"items":[{"coverLetterId":"cl_01HZ...","displayStatus":"REVIEWING","latestReviewVersionId":null}]}
```

`items`는 항상 배열이며 자기소개서가 없으면 `[]`이다. `coverLetterId`와 `displayStatus`는 non-null이고, `latestReviewVersionId`는 성공한 첨삭 버전이 없으면 `null`이다.

연결 이후 단건 변경:

```text
event: cover-letter.review-status.changed
data: {"coverLetterId":"cl_01HZ...","displayStatus":"REVIEW_FAILED","latestReviewVersionId":"rv_01HZ..."}
```

프론트엔드는 스냅샷과 변경 이벤트 모두에서 `displayStatus`와 `latestReviewVersionId`를 목록 항목에 그대로 함께 반영한다. Job 상태를 조합하거나 자기소개서 상태를 추론하지 않는다.

목록 조회와 스트림 연결 사이에 Job이 종료되어도 스냅샷으로 최신 표시 상태를 복구한다. 최초 첨삭 실패와 재첨삭 실패는 모두 `displayStatus=REVIEW_FAILED`다. 최초 첨삭 실패는 `latestReviewVersionId=null`, 재첨삭 실패는 기존 성공 버전 ID를 유지하므로 프론트엔드는 실패 화면에서 이전 결과 접근 가능 여부를 구분할 수 있다.

이벤트는 현재 페이지에 표시된 항목으로 제한하지 않고 현재 사용자의 모든 자기소개서 상태를 전달하며, 프론트엔드는 현재 목록에 없는 `coverLetterId`를 무시한다.

서버는 사용자 연결을 이벤트 라우터에 먼저 등록한 뒤 스냅샷을 조회·전송하고, 그 사이 발생한 변경 이벤트는 스냅샷 전송이 끝날 때까지 연결별로 버퍼링했다가 발생 순서대로 전송한다. 이 스트림은 `Last-Event-ID` 영속 replay를 제공하지 않는다. 재연결하면 전체 현재 상태 스냅샷을 다시 받은 뒤 실시간 이벤트를 수신한다. 연결 유지를 위한 heartbeat가 필요하면 프론트엔드 데이터 계약에 포함되지 않는 SSE comment를 사용한다.

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

서버는 현재 사용자의 비어 있는 `DRAFT` 자기소개서를 생성하고 프론트엔드가 등록 1단계 화면으로 이동할 때 필요한 id만 반환한다. 기본 정보, 우대사항, 질문과 답변은 각 step 저장 API에서 입력받는다.

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
  "id": "cl_01HZ..."
}
```

`id`는 non-null이다. 생성 직후 항상 같은 내부 `DRAFT` 상태와 등록 화면에서 사용하지 않는 `createdAt`은 응답하지 않는다.

Error Response:

인증과 CSRF 오류는 `docs/api/common.md`의 공통 처리 규칙을 따른다. 요청 본문이 없으므로 API-008에 별도 validation 또는 도메인 오류는 없다.

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

등록 step1의 현재 입력 상태를 임시저장한다. 별도 임시저장 API를 두지 않고 이 API를 전체 replace 방식의 DRAFT 스냅샷 저장에 사용한다. 누락·`null`·trim 후 빈 문자열인 필드는 `null`로 저장하며, 프론트엔드는 자동 저장 시 step1 폼 전체를 전송한다.

Request:

```json
{
  "title": "2026 상반기 백엔드 개발자 자기소개서",
  "companyName": null,
  "positionTitle": null,
  "jobPostingUrl": null
}
```

Validation:

```text
coverLetter.status는 DRAFT여야 한다.
title: 선택, nullable, 값이 있으면 trim 후 Unicode code point 기준 최대 50자
companyName: 선택, nullable, 값이 있으면 trim 후 Unicode code point 기준 최대 30자
positionTitle: 선택, nullable, 값이 있으면 trim 후 Unicode code point 기준 최대 30자
jobPostingUrl: 선택, nullable, 값이 있으면 trim 후 최대 500자 및 URL 형식
```

서버는 문자열의 앞뒤 공백을 제거한다. 누락·`null`·trim 후 빈 문자열은 미입력값인 `null`로 저장하고, 값이 있으면 최대 길이와 URL 형식을 검증한다. 필수값 완성 여부는 API-014 제출 시 최종 검증한다.

서버는 `jobPostingUrl`의 앞뒤 공백을 제거한 뒤 URL 형식을 검증하고, 공백이 제거된 값을 저장한다. `jobPostingUrl`이 누락되거나 `null`이거나 trim 후 빈 문자열이면 `null`로 저장한다. `jobPostingUrl`은 URL 형식만 검증한다. 백엔드는 해당 URL이 실제로 접근 가능한지, 로그인 없이 열리는지, 채용공고가 만료되지 않았는지는 검증하지 않는다.

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
  "success": true
}
```

프론트엔드는 성공 응답을 임시저장 완료로 처리한다. 다음 단계 이동 여부는 프론트엔드의 현재 입력 검증과 사용자 동작으로 결정하며, 저장한 기본 정보와 내부 자기소개서 상태는 응답하지 않는다.

Error Codes:

```text
400 VALIDATION_ERROR: 입력된 필드의 최대 길이 또는 URL 형식 validation 실패
401 UNAUTHORIZED: 인증되지 않은 요청
404 NOT_FOUND: 자기소개서가 없거나 현재 사용자가 소유하지 않은 경우
409 COVER_LETTER_NOT_DRAFT: 대상 자기소개서가 DRAFT가 아닌 경우
```

### 채용 우대사항 저장

```http
PUT /cover-letters/{coverLetterId}/preferences
```

등록 step2의 현재 입력 상태를 임시저장한다. 별도 임시저장 API를 두지 않고 이 API를 전체 replace 방식의 DRAFT 스냅샷 저장에 사용한다. 요청의 `preferences`가 누락·`null`·trim 후 빈 문자열이면 `null`로 저장한다.

Request:

```json
{
  "preferences": null
}
```

Validation:

```text
coverLetter.status는 DRAFT여야 한다.
preferences: 선택, nullable, 값이 있으면 trim 후 Unicode code point 기준 최대 3000자
```

서버는 `preferences`의 앞뒤 공백을 제거한다. 누락·`null`·trim 후 빈 문자열은 `null`로 저장하고, 값이 있으면 최대 길이를 검증한다. 필수값 완성 여부는 API-014 제출 시 최종 검증한다.

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
  "success": true
}
```

프론트엔드는 성공 응답을 임시저장 완료로 처리한다. 다음 단계 이동 여부는 프론트엔드의 현재 입력 검증과 사용자 동작으로 결정하며, 저장한 우대사항과 내부 자기소개서 상태는 응답하지 않는다.

Error Codes:

```text
400 VALIDATION_ERROR: 입력된 preferences의 최대 길이 validation 실패
401 UNAUTHORIZED: 인증되지 않은 요청
404 NOT_FOUND: 자기소개서가 없거나 현재 사용자가 소유하지 않은 경우
409 COVER_LETTER_NOT_DRAFT: 대상 자기소개서가 DRAFT가 아닌 경우
```

### 질문과 답변 저장

등록 step3의 현재 문항 입력 상태를 임시저장한다.

별도 임시저장 API를 두지 않고 이 API를 전체 replace 방식의 DRAFT 스냅샷 저장에 사용한다. 요청의 `questions` 배열이 해당 자기소개서의 현재 문항 목록이 되며, 이전에 임시저장되어 있던 문항 중 요청에 포함되지 않은 문항은 삭제된다. `questions`가 누락·`null`·빈 배열이면 현재 문항을 모두 삭제한다.

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
      "originalAnswer": null
    }
  ]
}
```

Validation:

```text
coverLetter.status는 DRAFT여야 한다.
questions: 선택, nullable, 빈 배열 허용, 제품 정책상 최대 개수 제한 없음
questions[]: null이 아닌 객체
questions[].question: 선택, nullable, 값이 있으면 trim 후 Unicode code point 기준 최대 300자
questions[].maxAnswerLength: 선택, nullable, 값이 있으면 100~5000
questions[].originalAnswer: 선택, nullable, 값이 있으면 trim 후 Unicode code point 기준 최대 5000자
```

서버는 요청 `questions` 배열의 순서를 기준으로 문항 `order`를 1부터 재부여해 저장한다. 클라이언트는 요청에서 `order`를 보내지 않는다.

문항 개수에는 API validation 상한을 두지 않는다. 단, HTTP request body size, DB 저장 한계, LLM provider context limit 같은 인프라/운영 한계는 별도로 적용될 수 있다. 문항 수가 많아 LLM 입력 한계를 초과하면 첨삭 Job은 `FAILED`가 될 수 있다.

서버는 `questions[].question`과 `questions[].originalAnswer`의 앞뒤 공백을 제거한다. 누락·`null`·trim 후 빈 문자열은 `null`로 저장하고, 값이 있으면 최대 길이를 검증한다. `maxAnswerLength`도 값이 있을 때만 범위를 검증한다. 문항 및 문항 필드의 필수값 완성 여부는 API-014 제출 시 최종 검증한다.

Response:

```json
{
  "success": true
}
```

프론트엔드는 성공 응답을 임시저장 완료로 처리한다. 제출 확인 단계 이동 여부는 프론트엔드의 현재 입력 검증과 사용자 동작으로 결정한다. 서버가 부여한 문항 ID와 순서, 저장한 질문·답변과 수정 시각은 응답하지 않는다.

Error Codes:

```text
400 VALIDATION_ERROR: 문항 객체가 null이거나 입력된 문항 필드의 최대 길이·범위 validation 실패
401 UNAUTHORIZED: 인증되지 않은 요청
404 NOT_FOUND: 자기소개서가 없거나 현재 사용자가 소유하지 않은 경우
409 COVER_LETTER_NOT_DRAFT: 대상 자기소개서가 DRAFT가 아닌 경우
```

Validation Error Response:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다.",
    "details": [
      {
        "field": "questions[0].maxAnswerLength",
        "reason": "최대 답변 글자 수는 100자 이상 5000자 이하여야 합니다."
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

등록 step1~step4의 임시저장 복구 화면, 최초·재첨삭 진행 화면, 최신 첨삭 결과 화면에서 공통으로 사용한다. `DRAFT`, `REVIEWING`, `REVIEW_FAILED`, `REVIEWED` 모든 상태에서 같은 응답 구조를 반환한다.

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
    "jobPostingUrl": null,
    "preferences": "Spring Boot 경험, 대용량 트래픽 처리 경험 우대",
    "displayStatus": "REVIEW_FAILED"
  },
  "reviewVersion": {
    "id": "rv_01HZ...",
    "version": "v0.1",
    "isLatest": true,
    "requestInstruction": null,
    "createdAt": "2026-06-20T14:11:00"
  },
  "reviewJob": {
    "id": "job_01HZ...",
    "status": "FAILED",
    "progress": {
      "current": 1,
      "total": 3,
      "message": "첨삭에 실패했습니다."
    },
    "error": {
      "code": "LLM_PROVIDER_ERROR",
      "message": "AI 처리에 실패했습니다."
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
WRITING: reviewVersion=null, reviewJob=null. 현재 임시저장된 기본 정보와 nullable 원본 문항을 반환한다.
최초 첨삭 REVIEWING: reviewVersion=null, 진행 중 reviewJob과 현재 Job 문항 스냅샷을 반환한다.
재첨삭 REVIEWING: 이전 최신 성공 reviewVersion, 진행 중 reviewJob과 현재 Job 문항 스냅샷을 반환한다.
최초 첨삭 REVIEW_FAILED: reviewVersion=null, 실패한 reviewJob과 실패한 최신 Job 문항 스냅샷을 반환한다.
재첨삭 REVIEW_FAILED: 이전 최신 성공 reviewVersion, 실패한 reviewJob과 실패한 최신 Job 문항 스냅샷을 반환한다.
REVIEWED: 최신 성공 reviewVersion과 해당 버전 questions를 반환하고 reviewJob=null이다.
```

`coverLetter.displayStatus`는 API-007과 같은 `WRITING | REVIEWING | REVIEWED | REVIEW_FAILED`를 사용한다. `title`, `companyName`, `positionTitle`, `jobPostingUrl`, `preferences`는 등록 단계에 따라 `null`일 수 있다.

`reviewVersion`은 API-012에서 최신 성공 버전, API-018에서 URL로 선택한 버전이다. `version`은 `v0.1`, `v0.2` 형식의 문자열이다. `requestInstruction`은 최초 첨삭 버전이면 `null`이고 나머지 필드는 non-null이다.

`reviewJob`은 진행 중이거나 가장 최근 실패한 첨삭 Job 요약이다. `id`, `status`, `progress.current`, `progress.total`, `progress.message`는 non-null이다. 진행 중에는 `error=null`, 실패하면 `error.code`와 `error.message`를 반환한다. 완료된 Job은 `reviewVersion`으로 표현하므로 `reviewJob=null`이다. API-018의 `reviewJob`은 항상 `null`이다.

재첨삭 진행 또는 실패 시 `originalAnswer`는 해당 Job 입력으로 확정한 이전 최신 `ReviewVersion`의 `finalAnswer`다. 새 작업을 시작할 때 이전 버전의 AI 필드를 복사하지 않으며, 현재 Job에서 완료된 문항만 `aiReport`, `rewrittenAnswer`, 길이 필드와 `finalAnswer`를 채운다. 완료된 임시 결과의 `finalAnswer` 초깃값은 `rewrittenAnswer`와 같다.

최초·재첨삭 Job이 최종 실패해도 현재 Job에서 성공한 문항의 AI 필드는 그대로 반환하고, 실패하거나 완료되지 않은 문항의 AI 필드는 `null`로 반환한다. AI와 무관한 문항 필드는 모든 문항에서 그대로 반환한다.

`questions`는 항상 배열이며 문항이 없으면 `[]`이다. `questionResultId`는 확정된 `ReviewVersion` 문항 결과 ID이며 원본 또는 임시 Job 문항에서는 `null`이다. `WRITING`에서는 임시저장 상태에 따라 `question`, `maxAnswerLength`, `originalAnswer`, `originalAnswerLength`가 `null`일 수 있다. 제출이 성공한 이후의 원본 문항 필드는 모두 non-null이다. AI·최종 작성본 필드는 결과가 없는 문항에서 `null`이고, 확정된 버전 결과에서는 모두 non-null이다.

`REVIEWED` 상태에서도 최신 결과를 보기 위해 API-018을 다시 호출하지 않는다. API-018은 버전 히스토리에서 선택한 특정 버전을 조회할 때만 사용한다.

Error Codes:

```text
401 UNAUTHORIZED: 인증되지 않은 요청
404 NOT_FOUND: 자기소개서가 없거나 현재 사용자가 소유하지 않은 경우
```

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

```text
No request body.
```

Response:

```json
{
  "displayStatus": "REVIEWING",
  "jobId": "job_01HZ..."
}
```

`displayStatus`는 `REVIEWING | REVIEWED` 중 하나이며 non-null이다. 새 Job을 생성하거나 기존 진행 중 Job을 반환하면 `REVIEWING`과 non-null `jobId`를 반환한다. 이미 첨삭이 완료되었으면 `REVIEWED`와 `jobId=null`을 반환한다.

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

모든 문항 task가 종료된 뒤 모든 임시 결과가 성공하면 최종 `ReviewVersion`과 문항별 결과로 한 transaction에서 확정하고 `CoverLetter.status`를 `REVIEWED`로 변경한다. 최종 실패하면 `LlmJob.status`는 `FAILED`, 최초 첨삭의 `CoverLetter.status`는 `REVIEW_FAILED`가 된다. 실패 Job에서 성공한 임시 문항 결과는 API-012에서 읽기 전용 부분 결과로 반환한다.

이미 최초 첨삭 Job이 `PENDING` 또는 `PROCESSING` 상태이면 새 Job을 만들지 않고 기존 진행 중 Job을 반환한다.

이미 `REVIEWED` 상태이면 새 Job을 만들지 않고 기존 최신 `ReviewVersion` 정보를 반환한다.

API-009~011이 미완성 DRAFT를 저장할 수 있으므로 API-014를 필수값 완성 여부의 단일 최종 검증 경계로 사용한다. 필수 step 데이터가 누락된 경우 `VALIDATION_ERROR`를 반환하고, `details`에 누락된 field와 reason을 포함하며 Job은 생성하지 않는다.

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
  "displayStatus": "REVIEWING",
  "jobId": "job_existing_01HZ..."
}
```

Already Reviewed Response:

```json
{
  "displayStatus": "REVIEWED",
  "jobId": null
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
  "success": true
}
```

`success`는 non-null이며 성공 응답에서 항상 `true`다. 내부 soft delete 시각과 취소한 Job 정보는 응답하지 않는다.

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

진행 중 LLM Job을 `CANCELED`로 전환하는 동작은 계약에 포함되며 현재 삭제 service에 구현 변경이 필요하다.

### API-007~014, API-030 오류 처리

COMMON의 `UNAUTHORIZED`, `CSRF_TOKEN_INVALID`, 예상하지 못한 `5xx` 처리를 기본으로 적용한다. 아래에는 화면에서 별도 분기가 필요한 오류만 기록한다.

| API | HTTP 상태 | 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---|---:|---|---|---|
| API-007 | 400 | `VALIDATION_ERROR` | `page < 1`, `size`가 1~9 범위를 벗어남, 알 수 없는 `displayStatus` | query를 `page=1`, `size=9`, 필터 없음으로 정규화한 뒤 한 번 다시 조회한다. 전체 페이지를 넘은 정상 page는 빈 목록으로 처리한다. |
| API-008 | - | `API별 오류 없음` | 별도 입력과 도메인 분기 없음 | 공통 오류 처리만 적용한다. |
| API-009 | 400 | `VALIDATION_ERROR` | 입력된 기본 정보의 최대 길이 또는 URL 형식 위반 | `details[].field`와 `reason`을 해당 입력에 표시한다. |
| API-009 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 자동저장을 중단하고 목록으로 이동한다. |
| API-009 | 409 | `COVER_LETTER_NOT_DRAFT` | 자기소개서가 `DRAFT`가 아님 | 대기 중 자동저장을 폐기하고 API-012를 재조회해 읽기 전용 또는 현재 상태 화면으로 전환한다. |
| API-010 | 400 | `VALIDATION_ERROR` | `preferences` 최대 길이 위반 | 해당 입력에 `details[].reason`을 표시한다. |
| API-010 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 자동저장을 중단하고 목록으로 이동한다. |
| API-010 | 409 | `COVER_LETTER_NOT_DRAFT` | 자기소개서가 `DRAFT`가 아님 | 대기 중 자동저장을 폐기하고 API-012를 재조회한다. |
| API-011 | 400 | `VALIDATION_ERROR` | null 문항 객체 또는 문항 필드 길이·범위 위반 | `details[].field`의 `questions[index].field`를 해당 문항 입력에 연결한다. |
| API-011 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 자동저장을 중단하고 목록으로 이동한다. |
| API-011 | 409 | `COVER_LETTER_NOT_DRAFT` | 자기소개서가 `DRAFT`가 아님 | 대기 중 자동저장을 폐기하고 API-012를 재조회한다. |
| API-012 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 대상이 없거나 접근할 수 없음을 안내하고 목록으로 이동한다. |
| API-013 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·이미 삭제 | 목록에서 대상을 제거하고 목록 화면을 유지한다. 이미 없는 대상이므로 실패 토스트는 표시하지 않는다. |
| API-014 | 400 | `VALIDATION_ERROR` | 제출 필수값 누락 또는 길이·범위 위반 | `details[].field`를 등록 step에 매핑하고 최초 오류가 있는 단계로 이동한다. |
| API-014 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 대상 없음 안내 후 목록으로 이동한다. |
| API-014 | 409 | `LLM_JOB_ALREADY_RUNNING` | 최초 첨삭 외 다른 AI Job이 진행 중 | 다른 AI 작업이 진행 중임을 안내하고 제출 화면을 유지하며 자동 재시도하지 않는다. 동일 최초 첨삭 중복 요청은 오류가 아니라 기존 `jobId`를 담은 성공이다. |
| API-030 | - | `API별 오류 없음` | 네트워크 또는 SSE 연결 종료 | 재연결하고 실패가 지속되면 API-007을 재조회해 상태를 복구한다. Job 실패는 `displayStatus=REVIEW_FAILED`로 처리한다. |
