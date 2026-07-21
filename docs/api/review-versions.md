# Review Version API

## Review Version API

### 첨삭 버전 목록 조회

```http
GET /cover-letters/{coverLetterId}/review-versions
```

첨삭 버전 목록에는 성공적으로 생성된 `ReviewVersion`만 포함한다. 진행 중이거나 실패한 첨삭 시도는 목록에 포함하지 않는다.

응답의 `isLatest`는 저장 필드가 아니라 `ReviewVersion.id == CoverLetter.latestReviewVersionId` 여부로 계산한 파생 필드다.

목록은 프론트엔드가 버전 히스토리를 과거부터 최신 순서로 바로 표시할 수 있도록 `createdAt` 오름차순으로 반환한다. 성공한 첨삭 버전이 없으면 `items`는 빈 배열 `[]`이다. 페이지네이션은 제공하지 않는다.

Response:

```json
{
  "items": [
    {
      "id": "rv_01HY...",
      "version": "v0.1",
      "isLatest": false,
      "createdAt": "2026-06-20T14:11:00"
    },
    {
      "id": "rv_01HZ...",
      "version": "v0.2",
      "isLatest": true,
      "createdAt": "2026-06-20T14:31:00"
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
  "coverLetter": {
    "id": "cl_01HZ...",
    "title": "2026 상반기 백엔드 개발자 자기소개서",
    "companyName": "Rewrite Corp",
    "positionTitle": "백엔드 개발자",
    "jobPostingUrl": "https://example.com/jobs/1",
    "preferences": "Spring Boot 경험, 대용량 트래픽 처리 경험 우대",
    "displayStatus": "REVIEWED"
  },
  "reviewVersion": {
    "id": "rv_01HZ...",
    "version": "v0.1",
    "isLatest": true,
    "requestInstruction": null,
    "createdAt": "2026-06-20T14:11:00"
  },
  "reviewJob": null,
  "questions": [
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

API-018은 API-012 자기소개서 상세와 동일한 최상위 응답 구조를 사용한다. `coverLetter.displayStatus`는 자기소개서의 현재 표시 상태이고, 선택한 버전의 결과를 `reviewVersion`과 `questions`에 반환한다. 과거 성공 버전에는 진행 Job이 없으므로 `reviewJob`은 항상 `null`이다.

응답의 `reviewVersion.isLatest`는 저장 필드가 아니라 `ReviewVersion.id == CoverLetter.latestReviewVersionId` 여부로 계산한 파생 필드다.

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
requestInstruction: 선택·nullable, trim 후 Unicode code point 기준 최대 1000자
자기소개서 상태는 REVIEWED여야 한다.
최신 성공 ReviewVersion이 존재해야 한다.
```

Request body 전체를 생략하거나 `{}`, `{"requestInstruction": null}`, 빈 문자열 또는 공백 문자열을 보내면 추가 재첨삭 요구사항이 없는 것으로 처리한다.

Response:

```json
{
  "displayStatus": "REVIEWING",
  "jobId": "job_01HZ..."
}
```

재첨삭 진행 중에도 내부 `CoverLetter.status`는 `REVIEWED`를 유지하지만 프론트엔드 표시 상태는 `displayStatus=REVIEWING`이다. 클라이언트는 `jobId`로 API-016에 연결한다.

동일한 자기소개서의 재첨삭 Job이 이미 `PENDING` 또는 `PROCESSING`이면 새 Job을 만들지 않고 기존 Job의 같은 성공 응답을 반환한다. 중복 요청의 `requestInstruction`은 기존 Job에 반영하지 않는다. 키워드 분석이나 면접처럼 다른 종류의 LLM Job이 진행 중이면 `LLM_JOB_ALREADY_RUNNING`을 반환한다.

재첨삭 Job은 요청 transaction에서 요청 시점의 최신 `ReviewVersion`을 `requestRef`로 고정하고, 그 버전의 문항별 `finalAnswer`를 임시 문항 입력 스냅샷으로 함께 저장한다. 진행 상세의 `originalAnswer`에도 실제 입력으로 고정된 `finalAnswer`를 반환한다. 새 버전의 `requestInstruction`에는 Job 생성 시 저장한 재첨삭 요구사항을 기록한다.

worker는 전체 자기소개서 문맥과 대상 문항을 입력으로 문항별 OpenAI 호출을 병렬 실행한다. provider 오류나 출력 검증 실패가 발생한 문항만 1회 재시도한다. 새 Job 시작 시 이전 버전의 AI 필드는 새 진행 결과로 복사하지 않는다. 각 문항의 `aiReport`와 `rewrittenAnswer`가 모두 완성되면 임시 결과를 저장하고 해당 문항 하나를 담은 `review.questions` 이벤트로 전달한 뒤 갱신된 진행률의 `job.state`를 전송한다. `finalAnswer` 초깃값은 새 `rewrittenAnswer`와 같다.

재첨삭 Job이 완료되어 새 `ReviewVersion`이 생성되어도 기존 키워드 분석 결과는 삭제하지 않는다. 최신 첨삭 버전 기준 키워드 분석이 필요하면 사용자가 `AI 키워드 재분석`을 실행해 기존 `KeywordAnalysis`를 갱신한다.

재첨삭 Job 시작 시점에는 `ReviewVersion`을 만들지 않는다. 모든 문항 task가 종료된 뒤 모든 임시 결과가 성공했을 때만 새 `ReviewVersion`과 문항별 첨삭 결과로 한 transaction에서 확정한다. Job이 실패하면 새 `ReviewVersion`은 생성하지 않고, 기존 최신 버전은 그대로 유지한다. 실패 Job에서 성공한 임시 문항 결과는 API-012에서 읽기 전용 부분 결과로 반환하며 실패하거나 완료되지 않은 문항의 AI 필드는 `null`이다.

Conflict Response:

```json
{
  "error": {
    "code": "LLM_JOB_ALREADY_RUNNING",
    "message": "이미 진행 중인 LLM 작업이 있습니다."
  }
}
```

자기소개서가 `REVIEWED`가 아니거나 최신 성공 버전이 없으면 `CONFLICT`를 반환한다. 존재하지 않거나 다른 사용자 소유이거나 삭제된 자기소개서는 `NOT_FOUND`, `requestInstruction`이 1000자를 초과하면 `VALIDATION_ERROR`와 `details`를 반환한다.

### 최종 작성본 일괄 저장

최신 첨삭 버전에 포함된 모든 문항의 최종 작성본을 한 번에 저장한다.

과거 `ReviewVersion`은 히스토리 열람용으로만 사용하고 최종 작성본을 수정할 수 없다.

path의 `versionId`는 사용자가 열어 둔 첨삭 버전과 저장 시점의 최신 버전이 같은지 검증하는 낙관적 동시성 값으로 사용한다. 재첨삭 완료로 최신 버전이 바뀐 뒤 오래 열린 이전 화면에서 저장하면 `REVIEW_VERSION_NOT_LATEST`를 반환한다.

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
  "success": true
}
```

저장된 답변을 다시 동기화해야 하면 API-012 또는 API-018을 조회한다.

### API-017~019, API-024 오류 처리

COMMON의 인증·CSRF·서버 오류 처리를 기본으로 적용하고, 아래에는 화면별 분기가 필요한 오류만 기록한다.

| API | HTTP 상태 | 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---|---:|---|---|---|
| API-017 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 대상 없음 안내 후 자기소개서 목록으로 이동한다. 성공 버전이 없는 경우는 `200`과 `items: []`다. |
| API-018 | 404 | `NOT_FOUND` | 자기소개서·첨삭 버전 없음, 비소유·삭제, 버전이 자기소개서에 속하지 않음 | 버전 목록 또는 자기소개서 목록으로 이동한다. 과거 성공 버전 조회는 정상 기능이다. |
| API-019 | 400 | `VALIDATION_ERROR` | 문항 누락·중복·불일치, 빈 답변 또는 길이 위반 | `details[].field`를 문항 입력이나 전체 `answers` 오류에 연결한다. |
| API-019 | 404 | `NOT_FOUND` | 자기소개서·버전 없음, 비소유·삭제 | 사용자가 입력한 값을 화면에 유지한 채 대상 없음 안내 후 이전 화면으로 이동한다. |
| API-019 | 409 | `REVIEW_VERSION_NOT_LATEST` | 열린 버전이 더 이상 최신 버전이 아님 | 자동 재전송하지 않고 API-012 또는 API-018로 최신 데이터를 조회한다. |
| API-024 | 400 | `VALIDATION_ERROR` | `requestInstruction`이 1000자 초과 | 입력란에 `details[].reason`을 표시한다. |
| API-024 | 404 | `NOT_FOUND` | 자기소개서 없음·비소유·삭제 | 대상 없음 안내 후 목록으로 이동한다. |
| API-024 | 409 | `CONFLICT` | 자기소개서가 `REVIEWED`가 아니거나 최신 성공 버전이 없음 | API-012를 재조회해 현재 상태 화면으로 전환한다. |
| API-024 | 409 | `LLM_JOB_ALREADY_RUNNING` | 재첨삭 외 다른 AI Job이 진행 중 | 다른 AI 작업이 진행 중임을 안내하고 요청을 중단한다. 오류 응답에 기존 `jobId`가 없으므로 Job 복구를 가정하지 않는다. |

동일한 재첨삭 Job에 대한 중복 요청은 오류가 아니라 기존 `jobId`를 담은 `200 OK`다.
