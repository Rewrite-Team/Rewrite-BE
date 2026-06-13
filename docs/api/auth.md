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
Location: https://kauth.kakao.com/oauth/authorize?client_id=...&redirect_uri=...&response_type=code&state=...
```

### 카카오 OAuth callback

```http
GET /auth/kakao/callback?code={authorizationCode}&state={state}
```

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
Location: https://rewrite.example.com
```

Failure Response:

```http
302 Found
Location: https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED
```

### CSRF 토큰 조회

상태 변경 요청에 사용할 CSRF 토큰을 조회한다.

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

### 토큰 갱신

refresh token cookie를 사용해 access token과 refresh token을 갱신한다. Refresh token rotation을 적용하므로 성공 시 기존 refresh token은 폐기된다.

```http
POST /auth/refresh
```

Request:

```json
{}
```

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
    "message": "유효하지 않은 refresh token입니다."
  }
}
```

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

### 로그아웃

```http
POST /auth/logout
```

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
