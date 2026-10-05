# 자기소개서·첨삭·키워드 흐름

필드·길이·HTTP 오류 예시는 Swagger UI를 확인한다.
제품 기준은 [REQ-003~006·009](../requirements.md), 설계 근거는 [자기소개서](../decisions/cover-letters.md)·[첨삭 버전](../decisions/review-versions.md)·[키워드](../decisions/keyword-analysis.md) 결정에 둔다.
인증·오류 복구·Job SSE는 [공통 흐름](common.md)을 따른다.

## 등록과 임시저장

1. API-008로 빈 `WRITING` 초안을 만들고 반환된 id로 등록 1단계에 진입한다.
2. API-009~011로 현재 step 전체 입력을 replace 방식으로 임시저장한다.
3. API-012로 step1~4의 저장 상태를 복구한다.
4. step4 완료 버튼에서 API-014로 필수값을 최종 검증하고 최초 첨삭을 시작한다.

| step | 저장 API | 입력 |
|---|---|---|
| 1 | API-009 | 기본 정보 전체 폼 |
| 2 | API-010 | `preferences` |
| 3 | API-011 | `questions` 전체 목록 |

- 별도 임시저장 API는 두지 않으며 `WRITING`에서만 원본을 수정한다.
- 문자열은 trim 후 누락·`null`·빈 값이면 `null`로 저장하고 입력된 값의 길이·형식·범위만 검증한다.
- 문항 목록 누락·`null`·`[]`는 전체 삭제이며 요청에서 빠진 이전 문항도 삭제한다.
- 요청 배열 순서로 `order`를 1부터 부여하며 클라이언트는 `order`를 보내지 않는다.
- 문항 수의 제품·API validation 상한은 없지만 body·DB·LLM context 등 운영 한계는 적용될 수 있다.
- `jobPostingUrl`은 길이·URL 형식만 검증하고 접근 가능·무로그인 접근·공고 만료를 확인하지 않는다.
- 저장 성공은 임시저장 완료이며 다음 step 이동은 화면 검증·사용자 동작으로 결정한다.
- API-014는 필수 기본 정보·우대사항·1개 이상 문항과 각 문항 필수값을 검증하는 최종 경계다.
- 제출 후에는 실패 상태여도 원본을 수정하지 않으며 최신 성공 버전의 최종 작성본만 편집한다.
- 자동저장 중 `COVER_LETTER_NOT_WRITING`이면 대기 요청을 폐기하고 API-012로 현재 화면을 복구한다.
- 저장·제출 validation의 `details[].field`를 문항 입력 또는 최초 오류 step에 연결한다.

## 목록과 상태 스트림

관련 API: 목록 API-007, 사용자 상태 SSE API-030.

| `displayStatus` | 카드 이동 화면 |
|---|---|
| `WRITING` | 등록 step |
| `REVIEWING` | 첨삭 진행 |
| `REVIEWED` | 첨삭 결과 |
| `REVIEW_FAILED` | 실패·재시도 |

- 현재 페이지의 모든 상태를 함께 조회하며 별도 상태 필터를 사용하지 않는다.
- 기본 정보 저장 전 제목·회사·직무는 `null`, 성공 버전이 없으면 `latestReviewedVersionId=null`이다.
- 잘못된 query는 `page=1`, `size=9`로 정규화해 한 번 조회하고 전체 페이지를 넘은 page는 빈 목록으로 처리한다.
- 메인 화면은 카드별 연결 대신 사용자 SSE 하나를 유지한다.
- 연결 직후 삭제되지 않은 모든 자기소개서 스냅샷을 받고 이후 단건 변경을 받는다.

```text
event: cover-letter.review-status.snapshot
data: {"items":[{"coverLetterId":"cl_01HZ...","displayStatus":"REVIEWING","latestReviewedVersionId":null}]}

event: cover-letter.review-status.changed
data: {"coverLetterId":"cl_01HZ...","displayStatus":"REVIEW_FAILED","latestReviewedVersionId":"rv_01HZ..."}
```

- 스냅샷의 `items`는 항상 배열이며 없으면 `[]`다.
- 두 이벤트 모두 상태·최신 성공 버전을 그대로 반영하며 Job 상태로 화면 상태를 추론하지 않는다.
- 현재 목록에 없는 id는 무시하고, `PENDING → PROCESSING`만으로는 별도 표시 상태 이벤트를 만들지 않는다.
- 연결 등록 → 스냅샷 → 그 사이 버퍼링한 변경 순서를 지키며 영속 replay 없이 재연결 시 최신 스냅샷을 받는다.
- heartbeat는 SSE comment를 사용한다.
- `onerror`면 연결을 닫고 일반 인증 요청에서 single-flight 갱신 후 새 스트림을 만든다.
- 인증 복구 실패는 로그인으로 이동하고 스트림 장애가 지속되면 API-007로 복구한다.

## 상세와 부분 결과

API-012는 현재 작성·진행·실패·최신 결과 화면, API-018은 히스토리에서 선택한 버전을 조회한다.
두 API는 같은 최상위 구조를 사용한다.

| 현재 상태(API-012) | `reviewVersion` | `reviewJob` | 문항 |
|---|---|---|---|
| `WRITING` | `null` | `null` | nullable 원본 |
| 최초·재첨삭 `REVIEWING` | 현재 진행 버전 | 진행 Job | 현재 Job 입력·완료 문항 |
| 최초·재첨삭 `REVIEW_FAILED` | 현재 실패 버전 | 실패 Job | 성공한 문항의 읽기 전용 부분 결과 |
| `REVIEWED` | 최신 성공 버전 | `null` | 해당 버전 결과 |

- 최초 첨삭 입력 스냅샷 생성 전에는 제출한 원본 문항을 반환한다.
- 최초 첨삭 버전의 `requestInstruction`은 `null`이다.
- `reviewJob`은 진행 중이면 `error=null`, 실패하면 `error.code`·`error.message`를 반환한다.
- AI와 무관한 문항 필드는 그대로 반환하고 현재 Job에서 완료되지 않은 AI 필드는 `null`이다.
- 재첨삭의 `originalAnswer`는 입력으로 고정한 이전 최신 성공 버전의 `finalAnswer`다.
- 이전 버전의 AI 필드를 새 진행 결과로 복사하지 않으며 완료 문항의 `finalAnswer` 초깃값은 새 `rewrittenAnswer`다.
- 원본·진행·실패 문항의 `questionResultId`는 `null`, 성공 확정 문항은 버전 결과 ID다.
- `questions`는 항상 배열이며 `WRITING`의 원본 필드는 미입력 시 nullable, 제출 성공 이후 원본은 non-null이다.
- 확정 버전의 AI·최종 작성본은 non-null이며 결과가 없는 문항은 `null`이다.
- 최초 첨삭 실패는 최신 성공 버전이 없고 재첨삭 실패는 기존 성공 버전을 유지한다.
- API-018의 진행·실패 버전도 완료 문항만 읽기 전용으로 반환하고 최종 작성본 저장은 허용하지 않는다.
- API-018의 `coverLetter.displayStatus`는 자기소개서 현재 상태이며 `reviewVersion`·`questions`는 선택한 버전이다.
- 선택한 성공 버전은 `reviewJob=null`, 진행·실패 버전은 해당 버전의 Job 요약을 반환한다.

## 최초 첨삭과 재첨삭

| 요청 시 상태 | 시작·재시도 |
|---|---|
| `WRITING` | API-014 최초 제출 |
| 최초 실패(`latestReviewedVersionId=null`) | 저장한 원본으로 API-014 재시도 |
| 재첨삭 실패(기존 성공 버전 존재) | API-024 재첨삭 재시도 |
| 동일 최초/재첨삭 Job이 `PENDING`·`PROCESSING` | 해당 시작 API는 기존 Job 성공 응답 반환 |
| API-014에 이미 `REVIEWED`인 자기소개서 | 새 Job 없이 `REVIEWED`, `jobId=null` 반환 |
| 다른 종류의 Job 진행 중 | `LLM_JOB_ALREADY_RUNNING`, 자동 재시도하지 않음 |

- 필수값 validation 실패 시 Job을 만들지 않는다.
- 새 최초·재첨삭 Job과 다음 버전을 같은 transaction에서 생성하고 자기소개서를 `REVIEWING`으로 전환한다.
- provider·출력 검증 실패 문항만 1회 재시도하며 완료 문항 저장 → `review.questions` → 갱신된 `job.state` 순서로 전달한다.
- 모두 성공하면 결과를 확정해 `REVIEWED`, 최종 실패하면 버전을 유지하고 Job은 `FAILED`, 자기소개서는 `REVIEW_FAILED`로 저장한다.
- 실패 Job의 성공 문항은 API-012·018로 조회하며 재첨삭 실패 시 기존 최신 성공 버전은 유지한다.
- 수동 첨삭 재시도의 제품 횟수 제한은 없지만 비용·남용 방지 rate limit·quota 등 운영 정책은 적용될 수 있다.
- 시작 API의 성공 후 AI 실패는 HTTP 오류로 처리하지 않고 SSE·상세의 상태로 처리한다.
- 재첨삭은 요청 시점의 최신 성공 버전·문항별 `finalAnswer`를 입력으로 고정한다.
- 선택 요구사항 `requestInstruction`은 body 생략·`{}`·`null`·빈/공백 문자열을 모두 없음으로 처리한다.
- 중복 재첨삭 요청의 요구사항은 기존 Job에 반영하지 않는다.
- 재첨삭 완료만으로 기존 키워드 결과를 삭제하지 않는다.

## 첨삭 버전과 최종 작성본

관련 API: 목록 API-017, 선택 버전 API-018, 일괄 저장 API-019.

- Job 시작마다 `v0.1`, `v0.2`처럼 증가하는 표시 라벨을 부여하며 semantic versioning 의미는 없다.
- 실패·취소 버전을 보존하고 Job 연결이 없는 기존 버전은 완료 버전으로 읽는다.
- 상태는 연결된 Job에서 읽으며 `isLatest`는 최신 시도, `isLatestReviewed`는 최신 성공 버전이다.
- 버전 목록은 `createdAt` 오름차순이며 이력이 없으면 `[]`, 페이지네이션은 없다.
- 최종 작성본 저장만으로 새 버전을 만들지 않는다.
- 최신 성공 버전의 모든 문항을 일괄 저장하며 부분·중복·불일치 문항은 validation 실패다.
- 열린 `versionId`가 저장 시 최신 성공 버전과 다르거나 진행·실패 버전이면 `REVIEW_VERSION_NOT_LATEST`다.
- 오류 시 입력을 유지하고 자동 재전송 없이 최신 상세·버전을 재조회한다.
- 저장 후 답변을 동기화해야 하면 API-012 또는 API-018을 조회한다.
- `aiReport`는 표시용 단일 문자열이며 평가 기준은 [REQ-005](../requirements.md#req-005-자기소개서-제출과-llm-job-생성)에 둔다.
- diff는 프론트엔드에서 `originalAnswer`와 `rewrittenAnswer`를 비교해 계산한다.

## 키워드 분석

관련 API: 시작·재분석 API-020, 최신 결과 API-021.

- 최신 성공 버전의 `finalAnswer`로 분석하며 새 결과는 생성하고 기존·실패 결과는 같은 리소스를 재사용한다.
- 재분석은 `AI 키워드 재분석` 버튼에서 실행하며 성공 시 결과·기준 버전을 최신 값으로 덮어쓴다.
- 결과는 중요도 상위 최대 20개, `importance`는 1~100 정수다.
- 동일 분석이 `PENDING`·`PROCESSING`이면 기존 Job을 반환하고 다른 Job이면 충돌이다.
- API-020의 `jobId`로 API-016에 연결하고 완료 시 API-021을 조회한다.
- 실패 시 기존 결과와 섞어 표시하지 않고 API-020 수동 재시도를 제공한다.
- SSE 장애 시 API-021을 polling해 완료·실패에 도달하면 중단한다.

| API-021 `status` | `keywords` | `sourceReviewVersion` | `jobId` |
|---|---|---|---|
| `NOT_STARTED` | `[]` | `null` | `null` |
| `PROCESSING` | `[]` | 기준 버전 | 현재 Job |
| `COMPLETED` | 결과 | 기준 버전 | `null` |
| `FAILED` | `[]` | 기준 버전 | 가장 최근 실패 Job |

- `coverLetter`는 항상 반환하며 등록 중 제목·회사·직무는 nullable, 분석 가능한 첨삭 완료 상태에서는 non-null이다.
- 새로고침 후 `PROCESSING`이면 받은 `jobId`로 스트림에 다시 연결한다.

## 삭제

API-013은 soft delete와 진행 중 Job 취소를 함께 처리한다.
삭제한 자기소개서·하위 리소스는 `NOT_FOUND`이며 사용자 복구 API는 제공하지 않는다.
삭제 요청의 `NOT_FOUND`는 목록에서 대상을 제거하고 실패 토스트 없이 목록 화면을 유지한다.
