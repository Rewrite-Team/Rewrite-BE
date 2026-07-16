# Design Decisions

이 디렉터리는 Rewrite API와 persistence 설계 결정을 도메인별로 관리한다.

PRD: `../requirements.md`

API 계약: `../api/README.md`

## Document Map

| Document | Purpose |
|---|---|
| `common.md` | 공통 정책과 전역 API 결정 |
| `auth.md` | 인증과 보안 결정 |
| `cover-letters.md` | 자기소개서 작성, 제출, 삭제 결정 |
| `review-versions.md` | AI 첨삭 버전과 최종 작성본 결정 |
| `llm-jobs.md` | LLM Job, SSE, partial result, 실패 처리 결정 |
| `keyword-analysis.md` | 키워드 분석 결정 |
| `interviews.md` | AI 면접 결정 |
| `persistence.md` | DB/JPA 전환과 Flyway 후순위 결정 |

## Decision Index

| ID | Title | File | Related REQ | Related API | Status |
|---|---|---|---|---|---|
| Decision 001 | LLM 작업은 비동기 Job 방식으로 처리한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 002 | 버전 히스토리는 AI 첨삭 결과를 기준으로 관리한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 003 | 임시저장 자기소개서도 목록에 노출한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 004 | AI 첨삭 결과는 질문별로 독립 저장한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 005 | LLM 스트리밍 프로토콜은 SSE를 사용한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 006 | 최종 작성본은 전체 문항을 일괄 저장한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 007 | 키워드 분석 결과는 자기소개서별 최신 결과만 유지한다 | `keyword-analysis.md` | REQ-009 | API-020 - API-021 | Active |
| Decision 008 | 인증 토큰은 HttpOnly Cookie로 전달한다 | `auth.md` | REQ-008 | API-001 - API-006 | Active |
| Decision 009 | 카카오 OAuth callback은 백엔드가 직접 처리한다 | `auth.md` | REQ-008 | API-001 - API-006 | Active |
| Decision 010 | 면접 답변마다 점수, 피드백, 꼬리질문을 모두 생성한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 011 | 면접 점수는 단일 1~100점 보조 지표로 제공한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 012 | 재첨삭 요구사항은 최대 1000자로 제한한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 013 | AI 리포트는 단일 문자열로 제공한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 014 | Diff는 프론트엔드에서 계산한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 015 | 첨삭 버전 라벨은 v0.1부터 순차 증가한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 016 | 자기소개서 삭제는 Soft delete로 처리한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 017 | LLM Job은 실패 시 서버에서 1회 자동 재시도한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 018 | Cookie 인증은 SameSite=Lax와 CSRF 토큰을 함께 사용한다 | `auth.md` | REQ-008 | API-001 - API-006 | Active |
| Decision 019 | Access token은 30분, Refresh token은 14일이며 refresh token rotation을 사용한다 | `auth.md` | REQ-008 | API-001 - API-006 | Active |
| Decision 020 | AI 면접 질문은 최초 5개, 추가 요청마다 1개를 자기소개서 기반으로 생성한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 021 | 키워드 분석 결과는 상위 20개를 제공한다 | `keyword-analysis.md` | REQ-009 | API-020 - API-021 | Active |
| Decision 022 | 같은 자기소개서의 LLM Job은 동시에 하나만 실행한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 023 | 제출 후 원본 자기소개서는 수정할 수 없다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 024 | SSE 재연결 시 전체 결과를 재조회한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 076 |
| Decision 025 | 삭제된 자기소개서 복구 API는 제공하지 않는다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 026 | 자기소개서당 AI 면접 세션은 하나만 유지한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 027 | 재첨삭 후에도 기존 AI 면접 세션은 유지한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 028 | 재첨삭 완료 시 기존 키워드 분석 결과를 유지한다 | `keyword-analysis.md` | REQ-009 | API-020 - API-021 | Active |
| Decision 029 | 키워드 분석 결과 화면에서 재분석을 제공한다 | `keyword-analysis.md` | REQ-009 | API-020 - API-021 | Active |
| Decision 030 | 면접 답변은 최대 2000자로 제한한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 031 | 면접 꼬리질문은 피드백과 같은 assistant 메시지에 저장한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 032 | 최초 첨삭 실패만 CoverLetter.status를 REVIEW_FAILED로 변경한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 033 | 키워드 분석과 면접 세션은 Job 실패 시 FAILED 상태로 저장한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 034 | FAILED 키워드 분석과 면접 세션은 같은 리소스로 재시도한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 035 | 공고 링크는 URL 형식만 검증한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 036 | ReviewVersion은 성공한 첨삭 결과만 생성한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 037 | 최종 작성본은 최신 ReviewVersion에서만 수정할 수 있다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 038 | 최종 작성본 저장 payload는 전체 문항을 항상 포함한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 039 | 최종 작성본은 빈 문자열로 저장할 수 없다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 040 | 최종 작성본은 앞뒤 공백을 trim한 뒤 검증하고 저장한다 | `review-versions.md` | REQ-006 | API-017 - API-019, API-024 | Active |
| Decision 041 | 글자 수는 Unicode code point 기준으로 계산한다 | `common.md` | REQ-001, REQ-002 | 공통 API 정책 | Active |
| Decision 042 | 원본 답변도 앞뒤 공백을 trim한 뒤 검증하고 저장한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 043 | 질문 문구도 앞뒤 공백을 trim한 뒤 검증하고 저장한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 044 | 자기소개서 문항 개수에는 제품 정책상 상한을 두지 않는다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 045 | 문항 order는 서버가 요청 배열 순서대로 재부여한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 046 | 문항 저장 API는 전체 replace 방식으로 동작한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 047 | step1 기본 정보 저장은 PUT 전체 replace로 처리한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 048 | step2 우대사항 저장은 PUT 전체 replace로 처리한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 049 | 우대사항은 빈 문자열로 저장할 수 없다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 050 | step1 기본 정보 문자열은 앞뒤 공백을 trim한 뒤 검증하고 저장한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 051 | 공고 링크는 앞뒤 공백을 trim한 뒤 URL 형식을 검증하고 저장한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 052 | 빈 공고 링크는 null로 저장한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 053 | 제출 시 누락된 step 데이터는 VALIDATION_ERROR와 details로 반환한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 054 | 제출 중복 호출 시 기존 진행 중 Job을 반환한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 055 | REVIEW_FAILED 상태에서는 submit 재호출로 최초 첨삭을 재시도할 수 있다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 056 | REVIEWED 상태에서 submit 재호출 시 기존 최신 첨삭 결과 정보를 반환한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 057 | REVIEW_FAILED 상태에서도 원본 수정 API는 허용하지 않는다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 058 | REVIEW_FAILED 상태의 사용자 수동 재시도에는 제품 도메인상 횟수 제한을 두지 않는다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 059 | REVIEW_FAILED 항목 클릭 시 AI 첨삭 실패 화면으로 이동한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 060 | 자기소개서 상세 응답에 최근 최초 첨삭 Job 요약을 포함한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Superseded by Decision 074 |
| Decision 061 | 첨삭 진행 중 화면 복구는 partial text 저장 방식으로 처리한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 075 |
| Decision 062 | MVP에서는 partial result를 서버 메모리에 저장하고 이후 cache 저장소로 이전한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 075 |
| Decision 063 | PROCESSING 상태에서 partialResult가 없으면 delta 텍스트를 숨기고 완료/실패만 감지한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 076 |
| Decision 064 | 실패한 첨삭 Job의 partialResult는 실패 화면에 표시하지 않는다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 075 |
| Decision 065 | 실패 화면의 사용자 메시지는 LLM Job error.code 기준으로 매핑한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 074 |
| Decision 066 | 알 수 없는 LLM Job error.code는 기본 fallback 메시지로 표시한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Superseded by Decision 074 |
| Decision 067 | AI 면접 답변 피드백은 완료 후 assistant 메시지만 저장한다 | `interviews.md` | REQ-010 | API-022 - API-023, API-025 - API-029 | Active |
| Decision 068 | LLM 출력 파싱 또는 구조 검증 실패는 Job 실패로 처리한다 | `llm-jobs.md` | REQ-005, REQ-009, REQ-010 | API-015 - API-016, LLM 시작 API | Active |
| Decision 069 | 모든 사용자 리소스 접근은 소유자 검증 후 비소유 리소스는 NOT_FOUND로 응답한다 | `common.md` | REQ-001, REQ-002 | 공통 API 정책 | Active |
| Decision 070 | 삭제된 자기소개서와 하위 리소스는 사용자-facing API에서 모두 NOT_FOUND로 응답한다 | `cover-letters.md` | REQ-003, REQ-004, REQ-005 | API-007 - API-014 | Active |
| Decision 071 | 날짜/시간 응답은 Asia/Seoul 기준 LocalDateTime으로 반환한다 | `common.md` | REQ-001, REQ-002 | 공통 API 정책 | Active |
| Decision 072 | 남은 자기소개서 CRUD 확장 전 DB/JPA 전환을 선행한다 | `persistence.md` | REQ-003, REQ-007 | API-007 - API-013 | Active |
| Decision 073 | 면접 질문과 질문별 대화방은 함께 생성한다 | `interviews.md` | REQ-010 | API-022, API-026, API-028 | Active |
| Decision 074 | 자기소개서 상세와 첨삭 버전 상세는 같은 응답 구조를 사용한다 | `cover-letters.md` | REQ-003, REQ-005, REQ-006 | API-012, API-018 | Active |
| Decision 075 | 완성된 첨삭 문항 결과는 Job 완료 전 임시 영속 저장한다 | `review-versions.md` | REQ-005, REQ-006 | API-012, API-014, API-016, API-024 | Active |
| Decision 076 | 첨삭 SSE는 완성된 문항 단위 이벤트와 연결 시 스냅샷을 제공한다 | `llm-jobs.md` | REQ-005, REQ-006 | API-016 | Active |
| Decision 077 | 메인 첨삭 상태는 사용자 단일 SSE 연결로 갱신한다 | `cover-letters.md` | REQ-003, REQ-005 | API-007, API-030 | Active |
| Decision 078 | 재첨삭은 이전 최신 최종 작성본을 입력으로 새 결과를 생성한다 | `review-versions.md` | REQ-006 | API-012, API-024 | Active |
| Decision 079 | 최신 키워드 분석 응답은 화면용 자기소개서와 기준 버전 요약을 포함한다 | `keyword-analysis.md` | REQ-009 | API-021 | Active |
