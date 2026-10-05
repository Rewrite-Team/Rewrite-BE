# AI 면접 흐름

HTTP 계약·필드·validation·오류 예시는 Swagger UI를 확인한다.
제품 기준은 [REQ-010](../requirements.md#req-010-ai-면접), 설계 근거는 [면접 결정](../decisions/interviews.md)에 둔다.
인증·오류 복구·SSE·피드백 replay는 [공통 흐름](common.md)을 따른다.

## 세션과 질문 생성

화면 진입 시 API-025로 자기소개서 요약·현재 세션을 확인한다.
자기소개서당 세션 하나를 유지하며 재첨삭 후에도 기존 질문·대화를 보존한다.

| 세션 상태 | 화면·동작 |
|---|---|
| 없음(`interviewSession=null`) | 정상 `200`, API-022로 시작 |
| `QUESTION_GENERATING` | 초기 질문 진행 화면 |
| `ACTIVE` | 질문 목록·기존 대화 |
| `FAILED` | 초기 질문 실패 안내, API-022로 같은 세션 재시도 |

- 초기 질문은 최신 성공 버전의 최종 작성본에서 경험·역할·행동·성과·문제 해결·의사결정을 바탕으로 5개 생성한다.
- 질문 종류를 구분하지 않으며 질문과 thread는 같은 transaction에서 1:1로 생성한다.
- API-022에 기존 `QUESTION_GENERATING`이면 새 Job 없이 기존 Job을, `ACTIVE`이면 기존 세션과 `jobId=null`을 반환한다.
- 추가 질문은 `ACTIVE` 세션에서 API-027로 요청 시 최신 성공 버전 기준 1개와 thread 1개를 생성한다.
- 세션의 초기 기준 버전은 `initialSourceReviewVersionId`, 각 질문의 기준은 `sourceReviewVersionId`로 내부 추적한다.
- 추가 생성은 기존 질문을 참고하며 trim 후 중복되거나 정확히 1개가 아니면 출력 검증 실패다.
- 기존 마지막 다음 `order`를 부여하고 기존 질문·대화는 변경하지 않는다.
- 추가 생성 실패는 세션 `ACTIVE`를 유지하고 새 질문·thread를 저장하지 않는다.
- 동일 추가 Job이 `PENDING`·`PROCESSING`이면 기존 Job을 반환하며 다른 종류의 진행 Job은 `LLM_JOB_ALREADY_RUNNING`이다.

| API-025 상태·질문 Job | `interviewSession.jobId` |
|---|---|
| `QUESTION_GENERATING` | 실행 중이거나 실패한 초기 질문 Job |
| `ACTIVE` + 추가 Job이 `PENDING`·`PROCESSING`·`FAILED` | 해당 Job |
| `ACTIVE` + 처리할 질문 Job 없음 | `null` |

- API-025는 자기소개서의 현재 상태와 관계없이 기존 세션을 조회하며 답변 피드백 Job은 포함하지 않는다.
- 자기소개서 요약은 항상 반환하고 등록 중 제목·회사·직무는 nullable, 면접 시작 가능한 상태는 non-null이다.
- 새로고침 후 `jobId`로 API-016에 연결하고 장애 시 API-015를 polling한다.
- 완료 시 초기 질문은 API-025·026, 추가 질문은 API-026을 조회한다.
- 실패 시 초기 질문은 API-022, 추가 질문은 API-027 수동 재시도를 제공한다.
- API-025 polling은 현재 세션·복구할 Job을 찾는 용도다.

## 질문 목록과 대화 진입

API-026은 최신 질문부터 `order` 내림차순의 cursor 목록으로 반환한다.

- 처음은 cursor 없이 조회하고 다음은 받은 `nextCursor`로 목록 뒤에 추가한다.
- 추가 생성 완료 후 첫 묶음을 다시 조회해 `id` 기준으로 새 질문을 목록 앞에 병합한다.
- 질문이 없거나 생성 중이면 정상 `200`, `items=[]`, `nextCursor=null`이다.
- 빈 목록으로 진행·실패를 판단하지 않고 API-025·015 상태를 확인한다.
- cursor·size validation 실패 시 목록 추가를 중단하고 서버 cursor만 사용한다.
- 질문별 `threadId`는 필수이며 질문만 있고 thread가 없으면 데이터 불변식 위반이다.
- API-028은 Deprecated이고 호출하지 않으며 API-026의 `threadId`로 API-029에 진입한다.
- 최초 면접 질문은 API-026의 `question`으로 표시하고 `ASSISTANT` 메시지로 중복 저장하지 않는다.
- 대상 없음·비소유·삭제는 면접·대화 화면을 종료하고 API-025로 다시 확인한다.

## 답변과 피드백

1. API-029로 thread 메시지와 처리할 피드백 `jobId`를 조회한다.
2. API-023으로 답변을 전송하고 저장된 `userMessageId`·`jobId`를 받는다.
3. API-016의 검증된 피드백 delta를 표시한다.
4. 완료 시 API-029를 재조회해 저장된 assistant 문장·점수로 확정한다.

- 메시지는 `createdAt`, `id` 오름차순이고 새 thread의 빈 `items`는 정상이다.
- 공개 `content`는 피드백·꼬리질문을 연결한 전체 문장이다.
- USER의 `score`는 `null`, ASSISTANT는 1~100 정수이며 max·항목별 점수는 제공하지 않는다.
- 내부 `feedback`·`followUpQuestion`은 다음 LLM 문맥에 유지하고 공개 응답에는 포함하지 않는다.
- API-029의 `jobId`는 해당 thread의 `PENDING`·`PROCESSING`·`FAILED` 피드백 Job이며 완료·대상 없음이면 `null`이다.
- 메시지는 페이지네이션하지 않으며 새로고침 후 Job이 있으면 공통 SSE 복구를 따른다.
- 답변은 trim 후 저장하고 해당 답변까지의 질문·대화 이력을 순서대로 LLM에 전달한다.
- 사용자 답변은 Unicode code point 기준 최대 2000자다.
- 답변당 assistant 하나를 저장하고 꼬리질문을 별도 메시지로 분리하지 않는다.
- 다른 진행 Job 때문에 답변이 충돌하면 USER 메시지는 저장되지 않으며 완료 후 사용자가 다시 전송한다.
- 피드백이 실패하면 USER 메시지는 유지하고 assistant를 저장하지 않으며 자동 재처리 API를 가정하지 않는다.
- 이벤트가 반복되어도 assistant를 중복 생성하지 않는다.
- `details[field=content].reason`은 답변 입력에 연결하고 질문·피드백 Job 실패는 HTTP 오류와 구분한다.
