# Review Version API

## Review Version API

### 첨삭 버전 목록 조회

```http
GET /cover-letters/{coverLetterId}/review-versions
```

첨삭 버전 목록에는 성공적으로 생성된 `ReviewVersion`만 포함한다. 진행 중이거나 실패한 첨삭 시도는 목록에 포함하지 않는다.

응답의 `isLatest`는 저장 필드가 아니라 `ReviewVersion.id == CoverLetter.latestReviewVersionId` 여부로 계산한 파생 필드다.

Response:

```json
{
  "items": [
    {
      "id": "rv_01HZ...",
      "version": "v0.2",
      "isLatest": true,
      "createdAt": "2026-06-20T14:31:00"
    },
    {
      "id": "rv_01HY...",
      "version": "v0.1",
      "isLatest": false,
      "createdAt": "2026-06-20T14:11:00"
    }
  ]
}
```

### 첨삭 버전 상세 조회

```http
GET /cover-letters/{coverLetterId}/review-versions/{versionId}
```

Response:

```json
{
  "id": "rv_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "version": "v0.1",
  "isLatest": true,
  "requestInstruction": null,
  "createdAt": "2026-06-20T14:11:00",
  "questionResults": [
    {
      "questionResultId": "rvqr_01HZ...",
      "questionId": "clq_01HZ...",
      "order": 1,
      "question": "지원 동기를 작성해주세요.",
      "maxAnswerLength": 1000,
      "originalAnswer": "제가 지원한 이유는...",
      "originalAnswerLength": 530,
      "aiReport": "STAR 관점에서 상황과 과제는 드러나지만 행동과 결과가 약합니다. 성과 수치를 추가하면 더 설득력 있습니다. 우대사항 중 Spring 경험과의 연결이 부족하므로 백엔드, API, 장애 대응 키워드를 보강하는 것이 좋습니다.",
      "rewrittenAnswer": "저는 백엔드 개발자로서...",
      "rewrittenAnswerLength": 820,
      "finalAnswer": "저는 백엔드 개발자로서...",
      "finalAnswerLength": 810
    }
  ]
}
```

응답의 `isLatest`는 저장 필드가 아니라 `ReviewVersion.id == CoverLetter.latestReviewVersionId` 여부로 계산한 파생 필드다.

### AI 첨삭 다시받기

```http
POST /cover-letters/{coverLetterId}/review-versions
```

Request:

```json
{
  "requestInstruction": "직무 키워드를 더 강조하고 문장을 자연스럽게 다듬어주세요."
}
```

Validation:

```text
requestInstruction: 선택, 최대 1000자
같은 자기소개서에 PENDING 또는 PROCESSING 상태의 LLM Job이 없어야 한다.
자기소개서 상태는 REVIEWED여야 한다.
```

Response:

```json
{
  "jobId": "job_01HZ...",
  "coverLetterId": "cl_01HZ...",
  "coverLetterStatus": "REVIEWED",
  "jobStatus": "PENDING"
}
```

재첨삭 진행 중에도 `CoverLetter.status`는 `REVIEWED`를 유지한다. 재첨삭 진행 상태는 `jobStatus`와 Job 조회/스트림 API로 확인한다.

재첨삭 Job은 최신 `ReviewVersion`의 문항별 `finalAnswer`를 새 첨삭 입력 원본으로 사용한다. 새 버전의 `requestInstruction`에는 Job 생성 시 저장한 재첨삭 요구사항을 기록한다.

재첨삭 Job이 완료되어 새 `ReviewVersion`이 생성되어도 기존 키워드 분석 결과는 삭제하지 않는다. 최신 첨삭 버전 기준 키워드 분석이 필요하면 사용자가 `AI 키워드 재분석`을 실행해 기존 `KeywordAnalysis`를 갱신한다.

재첨삭 Job 시작 시점에는 `ReviewVersion`을 만들지 않는다. Job이 성공적으로 완료된 경우에만 새 `ReviewVersion`과 문항별 첨삭 결과를 생성한다. Job이 실패하면 새 `ReviewVersion`은 생성하지 않고, 기존 최신 버전은 그대로 유지한다.

Conflict Response:

```json
{
  "error": {
    "code": "LLM_JOB_ALREADY_RUNNING",
    "message": "이미 진행 중인 LLM 작업이 있습니다."
  }
}
```

### 최종 작성본 일괄 저장

최신 첨삭 버전에 포함된 모든 문항의 최종 작성본을 한 번에 저장한다.

과거 `ReviewVersion`은 히스토리 열람용으로만 사용하고 최종 작성본을 수정할 수 없다.

```http
PUT /cover-letters/{coverLetterId}/review-versions/{versionId}/final-answers
```

Request:

```json
{
  "answers": [
    {
      "questionResultId": "rvqr_01HZ...",
      "finalAnswer": "저는 백엔드 개발자로서..."
    },
    {
      "questionResultId": "rvqr_01HY...",
      "finalAnswer": "저는 프로젝트에서..."
    }
  ]
}
```

Validation:

```text
versionId는 해당 자기소개서의 최신 ReviewVersion이어야 한다.
answers: 첨삭 버전에 포함된 모든 questionResultId를 포함해야 한다.
answers[].questionResultId: 필수
answers[].finalAnswer: 필수, trim 후 Unicode code point 기준 1~5000자
```

부분 저장은 지원하지 않는다. `answers`에 첨삭 버전의 일부 문항만 포함되거나, 존재하지 않는 `questionResultId`가 포함되거나, 중복된 `questionResultId`가 포함되면 `VALIDATION_ERROR`를 반환한다.

서버는 `finalAnswer`의 앞뒤 공백을 제거한 뒤 Unicode code point 수로 길이를 검증하고, 공백이 제거된 값을 저장한다. trim 후 빈 문자열이면 `VALIDATION_ERROR`를 반환한다.

Conflict Response:

```json
{
  "error": {
    "code": "REVIEW_VERSION_NOT_LATEST",
    "message": "최신 첨삭 버전의 최종 작성본만 수정할 수 있습니다."
  }
}
```

Response:

```json
{
  "coverLetterId": "cl_01HZ...",
  "reviewVersionId": "rv_01HZ...",
  "questionResults": [
    {
      "questionResultId": "rvqr_01HZ...",
      "finalAnswer": "저는 백엔드 개발자로서...",
      "finalAnswerLength": 810
    },
    {
      "questionResultId": "rvqr_01HY...",
      "finalAnswer": "저는 프로젝트에서...",
      "finalAnswerLength": 920
    }
  ],
  "updatedAt": "2026-06-20T14:40:00"
}
```
