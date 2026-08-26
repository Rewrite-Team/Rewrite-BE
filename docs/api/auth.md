# Auth API

## Auth API

### 카카오 로그인 시작

```http
GET /auth/kakao/authorize
```

Description:

```text
사용자를 카카오 OAuth 인증 페이지로 리다이렉트한다.
```

Response:

```http
302 Found
Set-Cookie: oauth_login_nonce=...; HttpOnly; Secure; SameSite=Lax; Path=/auth/kakao; Max-Age=300
Location: https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&state=...
```

`oauth_login_nonce`는 로그인 시작 브라우저와 OAuth `state`를 결합하기 위한 5분 수명의 임시 Cookie다. JavaScript에서 읽지 않으며 callback 성공·취소·실패 시 만료한다.

로그인 시작 처리에 실패하면 JSON 오류를 반환하지 않고 프론트엔드 로그인 화면으로 리다이렉트한다.

```http
302 Found
Location: https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED
```

프론트엔드는 `KAKAO_LOGIN_FAILED`이면 로그인 실패 안내를 표시하고 로그인 버튼을 다시 활성화한다. 자동 재시도는 하지 않으며, 알 수 없는 `error` 값도 같은 일반 로그인 실패 안내로 처리한다. 백엔드 오류 메시지나 내부 설정 정보는 사용자에게 노출하지 않는다.

### 카카오 OAuth callback

```http
GET /auth/kakao/callback?code={authorizationCode}&state={state}
Cookie: oauth_login_nonce=...
```

사용자가 카카오 로그인 또는 동의를 취소하면 카카오는 다음 형태로 callback을 호출한다.

```http
GET /auth/kakao/callback?error=access_denied&error_description=...&state={state}
Cookie: oauth_login_nonce=...
```

`code`와 `error` 중 하나만 전달되어야 하며 `state`와 `oauth_login_nonce` Cookie는 두 경우 모두 필수다. `error_description`은 선택값이며 프론트엔드에 전달하지 않는다. 백엔드는 state와 nonce를 로그인 시작 시 저장한 해시와 비교하고 5분 TTL 안에서 한 번만 소비한다.

Description:

```text
백엔드가 카카오 authorization code를 받아 카카오 token 교환과 사용자 정보 조회를 수행한다.
로그인 성공 시 Rewrite 서비스용 인증 cookie를 설정하고 프론트엔드로 리다이렉트한다.
```

Success Response:

```http
302 Found
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1800
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=1209600
Set-Cookie: oauth_login_nonce=; HttpOnly; Secure; SameSite=Lax; Path=/auth/kakao; Max-Age=0
Location: https://rewrite.example.com
```

Failure Response:

```http
302 Found
Set-Cookie: oauth_login_nonce=; HttpOnly; Secure; SameSite=Lax; Path=/auth/kakao; Max-Age=0
Location: https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED
```

사용자가 카카오 로그인 또는 동의를 취소한 경우:

```http
302 Found
Set-Cookie: oauth_login_nonce=; HttpOnly; Secure; SameSite=Lax; Path=/auth/kakao; Max-Age=0
Location: https://rewrite.example.com/login?error=KAKAO_LOGIN_CANCELED
```

`KAKAO_LOGIN_CANCELED`이면 프론트엔드는 “카카오 로그인이 취소되었습니다.”를 표시한다. state·nonce 누락/불일치/만료/재사용, authorization code 교환 실패, 카카오 사용자 조회 또는 사용자 저장 실패는 `KAKAO_LOGIN_FAILED`로 처리한다. 두 경우 모두 로그인 버튼을 다시 활성화하고 자동 재시도하지 않는다. 알 수 없는 오류 코드는 일반 로그인 실패로 처리하며, 카카오 `error_description`, state 검증 원인과 백엔드 오류 메시지는 노출하지 않는다.

### CSRF 토큰 조회

상태 변경 요청에 사용할 CSRF 토큰을 조회한다.
access token 인증과 CSRF 헤더 없이 호출할 수 있다. access token이 만료되고 프론트엔드 메모리의 CSRF 토큰이 사라진 경우에도 새 토큰을 받아 API-004 토큰 갱신을 호출할 수 있어야 한다.

```http
GET /auth/csrf-token
```

Response:

```json
{
  "csrfToken": "csrf-token-value"
}
```

클라이언트는 `POST`, `PUT`, `PATCH`, `DELETE` 요청에 다음 헤더를 포함한다.

```http
X-CSRF-Token: csrf-token-value
```

CSRF 토큰 생성에 실패하면 `500 INTERNAL_ERROR`를 반환한다. 프론트엔드는 API-003을 한 번만 자동 재시도하고 다시 실패하면 상태 변경 기능을 막은 뒤 새로고침 안내를 표시한다.

상태 변경 API가 토큰 누락·만료·불일치로 `403 CSRF_TOKEN_INVALID`를 반환하면 API-003을 다시 호출하고 원래 요청을 한 번만 재시도한다. 재시도도 실패하면 반복하지 않고 일반 보안 오류 안내를 표시한다.

### 토큰 갱신

refresh token cookie를 사용해 access token과 refresh token을 갱신한다. Refresh token rotation을 적용하므로 성공 시 기존 refresh token은 폐기된다.

```http
POST /auth/refresh
```

Request body는 없다. access token은 필요하지 않고 `refresh_token` Cookie와 `X-CSRF-Token` 헤더를 사용한다.

Response Header:

```http
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1800
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=1209600
```

Response:

```json
{
  "success": true
}
```

Failure:

```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "인증이 필요합니다.",
    "details": []
  }
}
```

프론트엔드는 동시에 여러 요청에서 access token 만료를 감지해도 API-004를 하나만 호출하고 나머지 요청은 같은 결과를 기다린다. 갱신 성공 후 원 요청을 각각 한 번만 재시도한다. refresh token 누락·만료·위조·폐기·재사용은 모두 `401 UNAUTHORIZED`로 처리하며 세부 보안 원인을 노출하지 않는다. 서버는 `401` 응답에서 access token과 refresh token Cookie를 모두 만료시킨다.

토큰 회전 또는 저장 중 `500 INTERNAL_ERROR`가 발생하면 프론트엔드는 Cookie와 사용자 상태를 임의로 지우거나 자동 재시도하지 않고 일시 오류를 안내한다.

### 내 정보 조회

```http
GET /user/me
```

Response:

```json
{
  "id": "user_01HZ...",
  "nickname": "홍길동",
  "profileImageUrl": "https://...",
  "provider": "KAKAO",
  "createdAt": "2026-06-20T14:00:00"
}
```

`profileImageUrl`은 카카오 프로필 이미지가 없는 사용자에게 `null`이며, 나머지 필드는 non-null이다. `provider`는 현재 `KAKAO`를 사용한다.

### 로그아웃

```http
POST /auth/logout
```

로그아웃은 멱등하게 처리한다. access token 또는 refresh token Cookie가 없거나 만료되었어도 `200 OK`를 반환하고 두 Cookie의 만료 헤더를 내려 프론트엔드 분기를 만들지 않는다. 전달된 refresh token이 유효하면 서버 저장소에서도 폐기한다.

Response Header:

```http
Set-Cookie: access_token=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0
Set-Cookie: refresh_token=; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=0
```

Response:

```json
{
  "success": true
}
```

### API-004~006 오류 처리

COMMON의 인증·CSRF·서버 오류 처리 규칙을 기본으로 적용하고, 아래에는 API별 행동이 다른 오류만 기록한다.

| API | HTTP 상태 | 오류 코드 | 발생 조건 | 프론트엔드 처리 |
|---|---:|---|---|---|
| API-004 | 401 | `UNAUTHORIZED` | refresh token 누락·만료·위조·폐기·재사용 | 인증 상태를 정리하고 로그인 화면으로 이동하며 refresh를 반복하지 않는다. |
| API-004 | 500 | `INTERNAL_ERROR` | 토큰 회전 또는 저장 실패 | Cookie를 임의로 지우지 않고 일시 오류를 표시하며 자동 재시도하지 않는다. |
| API-005 | - | `API별 오류 없음` | 인증 실패는 COMMON `UNAUTHORIZED`로 처리 | 공통 갱신 후 한 번 재시도하고 실패하면 로그인 화면으로 이동한다. |
| API-006 | - | `API별 오류 없음` | 인증 Cookie가 없어도 멱등 성공 | 성공 후 로컬 사용자 상태를 정리하고 로그인 화면으로 이동한다. |
