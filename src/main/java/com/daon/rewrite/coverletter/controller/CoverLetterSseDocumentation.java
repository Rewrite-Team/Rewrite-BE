package com.daon.rewrite.coverletter.controller;

final class CoverLetterSseDocumentation {

    static final String REVIEW_STATUS_EXAMPLE = """
            event: cover-letter.review-status.snapshot
            data: {"items":[{"coverLetterId":"cover_letter_01","displayStatus":"REVIEWING","latestReviewedVersionId":null}]}

            event: cover-letter.review-status.changed
            data: {"coverLetterId":"cover_letter_01","displayStatus":"REVIEWED","latestReviewedVersionId":"review_version_01"}

            event: heartbeat
            data: {}

            """;

    static final String REVIEW_STATUS_BEHAVIOR = """
            연결 직후 삭제되지 않은 모든 자기소개서의 스냅샷을 보내고 이후 단건 변경을 전달한다.
            재연결하면 최신 전체 스냅샷으로 복구한다.
            영속 replay와 별도 삭제 이벤트는 제공하지 않는다.

            | 이벤트 | 의미와 처리 |
            |---|---|
            | `cover-letter.review-status.snapshot` | 연결 직후 전체 상태를 `items` 배열로 전달한다. 자기소개서가 없으면 `items=[]`다. |
            | `cover-letter.review-status.changed` | 새로 관측되거나 `displayStatus`·`latestReviewedVersionId`가 달라진 자기소개서 한 건을 전달한다. |
            | `heartbeat` | 연결 상태를 확인하는 빈 객체 `{}`다. 자기소개서 상태를 변경하지 않는다. |

            상태 변화가 없어도 기본 15초 주기로 heartbeat를 전달한다. 전송 주기는 서버 설정에 따라 바뀔 수 있다.
            아래 SSE 예시는 응답 스키마의 예시와 같은 원문이다.

            ```text
            """ + REVIEW_STATUS_EXAMPLE + "```\n";

    private CoverLetterSseDocumentation() {
    }
}
