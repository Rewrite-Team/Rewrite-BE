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
      "status": "REVIEWED",
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

목록 카드 클릭 시 프론트엔드는 `status`에 따라 이동 화면을 결정한다.

```text
DRAFT: 등록 step 화면
REVIEWING: AI 첨삭 진행 화면
REVIEWED: AI 첨삭 결과 화면
REVIEW_FAILED: AI 첨삭 실패 화면
```

`REVIEW_FAILED`의 실패 화면에서는 저장된 원본 입력값을 표시할 수 있지만 원본 수정은 제공하지 않는다. 사용자는 실패 화면에서 submit 재시도를 실행할 수 있다.

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

등록 step4 확인 화면과 AI 첨삭 페이지 상단 정보에서 사용한다.

```http
GET /cover-letters/{coverLetterId}
```

Response:

```json
{
  "id": "cl_01HZ...",
  "title": "2026 상반기 백엔드 개발자 자기소개서",
  "companyName": "Rewrite Corp",
  "positionTitle": "백엔드 개발자",
  "jobPostingUrl": "https://example.com/jobs/1",
  "preferences": "Spring Boot 경험, 대용량 트래픽 처리 경험 우대",
  "status": "DRAFT",
  "questions": [
    {
      "id": "clq_01HZ...",
      "order": 1,
      "question": "지원 동기를 작성해주세요.",
      "maxAnswerLength": 1000,
      "originalAnswer": "제가 지원한 이유는..."
    }
  ],
  "createdAt": "2026-06-20T14:00:00",
  "updatedAt": "2026-06-20T14:10:00",
  "latestReviewVersionId": null,
  "latestFirstReviewJob": null
}
```

`latestFirstReviewJob`은 최초 첨삭 Job(`type=COVER_LETTER_REVIEW`) 중 가장 최근 Job의 요약이다. 최초 첨삭을 아직 제출하지 않은 `DRAFT` 상태이면 `null`이다.

`REVIEWING` 상태에서는 진행 화면 복구를 위해 다음 형태로 포함한다.

```json
{
  "latestFirstReviewJob": {
    "jobId": "job_01HZ...",
    "status": "PROCESSING",
    "progress": {
      "current": 2,
      "total": 3,
      "message": "2번 문항을 첨삭하고 있습니다."
    },
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
    },
    "error": null,
    "createdAt": "2026-06-20T14:10:00",
    "completedAt": null
  }
}
```

프론트엔드는 `REVIEWING` 화면 재진입 시 `partialResult`를 먼저 렌더링하고, `latestFirstReviewJob.jobId`로 SSE에 연결한 뒤 이후 수신한 delta를 해당 문항/필드 뒤에 이어붙인다.

`REVIEWING` 상태인데 `partialResult`가 `null`이면 진행률과 상태 메시지만 먼저 표시하고 SSE에 연결한다. 이 경우 이미 생성된 텍스트는 복구하지 못하므로, SSE 연결 이후 수신한 delta 텍스트도 화면에 렌더링하지 않는다. 클라이언트는 SSE를 완료/실패 감지 용도로만 사용하고, 완료 후 `ReviewVersion` 상세를 조회해 온전한 최종 결과를 표시한다. Job 자체는 실패 처리하지 않는다.

`REVIEW_FAILED` 상태에서는 실패 화면 복구를 위해 `status`, `progress`, `error`, `createdAt`, `completedAt`을 포함한다. 실패 화면의 사용자 안내 문구는 `error.code`를 기준으로 프론트엔드가 매핑한다. `error.message`나 provider 원문은 그대로 노출하지 않는다. 실패한 Job의 `partialResult`는 최종 첨삭 결과가 아니므로 실패 화면에 표시하지 않는다.

`REVIEWED` 상태에서는 최초 첨삭 결과가 이미 `ReviewVersion`으로 생성되었으므로 `latestReviewVersionId`를 기준으로 결과 화면을 조회한다. `latestFirstReviewJob`은 화면 복구에 필요하지 않으므로 `null`로 내려도 된다.

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
