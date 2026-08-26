# Auth API Decisions

## Decision 008: 인증 토큰은 HttpOnly Cookie로 전달한다

### 결정

카카오 로그인 성공 후 백엔드는 Rewrite 서비스용 access token과 refresh token을 발급하고, 이를 JSON body가 아니라 HttpOnly Cookie로 클라이언트에 전달한다.

프론트엔드는 토큰 값을 직접 읽지 않고, 인증이 필요한 API 요청에 cookie가 포함되도록 요청한다.

응답 헤더:

```http
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=...
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=...
```

프론트엔드 요청 전제:

```text
credentials: include
```

### PRD 근거

- 로그인은 카카오 로그인으로 진행한다.
- 사용자의 닉네임과 프로필 사진을 가져와 화면 우측 상단에서 보여준다.
- PRD에는 외부 API 클라이언트나 모바일 앱 지원 요구가 없다.

### 고려한 대안

1. JSON body로 토큰 반환
   - 로그인 응답에 `accessToken`, `refreshToken`을 JSON으로 내려준다.
   - 프론트엔드가 토큰 저장과 `Authorization: Bearer` 헤더 첨부를 담당한다.
   - API 테스트와 모바일/웹 공통 사용은 쉽지만, 브라우저 저장 위치에 따라 XSS로 토큰이 탈취될 위험이 커진다.

2. HttpOnly Cookie로 토큰 전달
   - 백엔드가 `Set-Cookie`로 토큰을 내려준다.
   - 프론트엔드 JavaScript는 토큰 값을 직접 읽을 수 없다.
   - XSS 상황에서 토큰 탈취 위험을 줄일 수 있지만, CORS, SameSite, CSRF 정책을 함께 설계해야 한다.

### 선택 이유

Rewrite는 현재 PRD 기준으로 브라우저 기반 웹 제품이다. 웹 서비스에서는 JavaScript가 토큰을 직접 읽고 저장하는 구조보다 HttpOnly Cookie 기반 인증이 토큰 탈취 위험을 줄이는 데 유리하다.

### 트레이드오프

- 장점
  - JavaScript에서 토큰 값을 읽을 수 없어 XSS로 인한 토큰 탈취 위험을 줄인다.
  - 브라우저 요청에 인증 정보가 자동 포함되므로 클라이언트 토큰 저장 로직이 단순해진다.
  - refresh token을 더 제한된 path에 둘 수 있다.

- 단점
  - 프론트엔드와 백엔드 도메인이 다르면 CORS와 cookie 설정을 정확히 맞춰야 한다.
  - CSRF 방어 정책을 별도로 고려해야 한다.
  - 모바일 앱이나 외부 API 클라이언트를 지원할 경우 별도 인증 전달 방식이 필요할 수 있다.



## Decision 009: 카카오 OAuth callback은 백엔드가 직접 처리한다

### 결정

카카오 OAuth callback은 프론트엔드가 아니라 백엔드가 직접 받는다.

프론트엔드는 사용자를 백엔드의 로그인 시작 URL로 이동시키고, 백엔드는 카카오 인증 URL로 리다이렉트한다. 카카오 인증 완료 후 카카오는 백엔드 callback URL로 `code`를 전달한다. 백엔드는 code를 access token으로 교환하고, 카카오 사용자 정보를 조회한 뒤 Rewrite 서비스용 인증 cookie를 설정하고 프론트엔드로 리다이렉트한다.

로그인 시작 시 백엔드는 256-bit OAuth `state`와 별도의 256-bit 브라우저 nonce를 생성한다. 원문은 각각 카카오 redirect query와 5분 수명의 `oauth_login_nonce` HttpOnly Cookie로 전달하고, 서버에는 두 값의 해시와 만료 시각만 저장한다. callback은 state와 브라우저 nonce가 모두 일치할 때만 state를 원자적으로 한 번 소비한다. 이를 통해 state URL이 다른 브라우저에서 재생되는 로그인 CSRF를 차단한다.

API 형태:

```http
GET /auth/kakao/authorize
GET /auth/kakao/callback?code=...&state=...
```

로그인 시작 응답:

```http
Set-Cookie: oauth_login_nonce=...; HttpOnly; Secure; SameSite=Lax; Path=/auth/kakao; Max-Age=300
Location: https://kauth.kakao.com/oauth/authorize?...&state=...
```

callback 성공 시 응답:

```http
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=...
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=...
Set-Cookie: oauth_login_nonce=; HttpOnly; Secure; SameSite=Lax; Path=/auth/kakao; Max-Age=0
Location: https://rewrite.example.com
```

로그인 시작 또는 callback 처리 실패 시 응답:

```http
HTTP/1.1 302 Found
Location: https://rewrite.example.com/login?error=KAKAO_LOGIN_FAILED
```

사용자가 카카오 로그인 또는 동의를 취소하면 카카오는 `error=access_denied` callback을 호출하고, 백엔드는 다음과 같이 변환한다.

```http
HTTP/1.1 302 Found
Location: https://rewrite.example.com/login?error=KAKAO_LOGIN_CANCELED
```

OAuth 브라우저 이동 흐름에서는 JSON `ErrorResponse` 대신 프론트엔드 로그인 화면으로 리다이렉트하고 `error` query로 실패 코드를 전달한다. 프론트엔드는 `KAKAO_LOGIN_CANCELED`에 취소 안내를, `KAKAO_LOGIN_FAILED`와 알 수 없는 오류 코드에 일반 로그인 실패 안내를 표시한다. 모든 실패에서 로그인 버튼을 다시 활성화하고 자동 재시도하지 않는다. 카카오 `error_description`, state 검증 원인, 내부 오류 메시지와 설정 정보는 노출하지 않는다.

### PRD 근거

- 로그인은 카카오 로그인으로 진행한다.
- 사용자의 닉네임과 프로필 사진을 가져와 화면 우측 상단에서 보여준다.

### 고려한 대안

1. 프론트엔드가 authorization code를 받고 백엔드에 전달
   - 프론트엔드가 카카오 callback route에서 `code`를 받은 뒤 백엔드 로그인 API에 전달한다.
   - SPA 라우팅과는 잘 맞지만, 프론트엔드가 OAuth code 처리 흐름을 알아야 한다.

2. 백엔드가 카카오 callback을 직접 처리
   - 백엔드가 code 교환, 사용자 정보 조회, cookie 설정, 프론트엔드 리다이렉트를 모두 처리한다.
   - OAuth 민감 로직과 cookie 설정 책임이 백엔드에 모인다.

### 선택 이유

Rewrite는 인증 토큰을 HttpOnly Cookie로 전달하기로 결정했다. 토큰 발급과 cookie 설정 주체가 백엔드이므로 OAuth callback 처리도 백엔드에 모으는 것이 흐름과 책임 분리가 더 명확하다.

### 트레이드오프

- 장점
  - OAuth code 교환과 사용자 정보 조회 로직이 백엔드에만 존재한다.
  - HttpOnly Cookie 설정과 로그인 완료 리다이렉트를 한 곳에서 처리할 수 있다.
  - 프론트엔드는 로그인 시작 URL로 이동하고 로그인 후 내 정보 조회만 수행하면 된다.

- 단점
  - 백엔드 callback URL과 프론트엔드 redirect URL을 환경별로 정확히 설정해야 한다.
  - SPA 내부 callback route를 활용하는 방식보다 서버 라우팅 설정이 조금 더 필요하다.
  - 브라우저 redirect 오류 계약과 일반 JSON `ErrorResponse` 계약을 구분해 관리해야 한다.



## Decision 018: Cookie 인증은 SameSite=Lax와 CSRF 토큰을 함께 사용한다

### 결정

HttpOnly Cookie 기반 인증의 CSRF 방어는 `SameSite=Lax`와 CSRF 토큰을 함께 사용한다.

서버는 CSRF 토큰 조회 API를 제공하고, 프론트엔드는 상태 변경 요청에 `X-CSRF-Token` 헤더를 포함한다.

CSRF 토큰 조회 API는 access token 인증과 CSRF 헤더 없이 호출할 수 있다. access token이 만료되고 프론트엔드 메모리의 CSRF 토큰이 사라진 경우에도 새 토큰을 받아 refresh 요청을 보낼 수 있어야 하기 때문이다.

상태 변경 요청 범위:

```text
POST
PUT
PATCH
DELETE
```

API 형태:

```http
GET /auth/csrf-token
```

예상 요청 헤더:

```http
X-CSRF-Token: csrf-token-value
```

상태 변경 요청의 CSRF 토큰이 누락·만료·불일치하면 `403 CSRF_TOKEN_INVALID`를 반환한다. 프론트엔드는 API-003으로 토큰을 재발급한 뒤 원래 요청을 한 번만 재시도하고, 같은 오류가 반복되면 중단한다. API-003 자체가 `500 INTERNAL_ERROR`로 실패하면 한 번만 재요청하고 다시 실패할 때 상태 변경 기능을 막고 새로고침을 안내한다.

### PRD 근거

- 로그인은 카카오 로그인으로 진행한다.
- 인증 토큰은 HttpOnly Cookie로 전달하기로 결정했다.
- 자기소개서 생성, 수정, 삭제, LLM 작업 시작 등 상태 변경 API가 존재한다.

### 고려한 대안

1. `SameSite=Lax`만 사용
   - 구현이 단순하다.
   - 하지만 Cookie 기반 인증에서 상태 변경 요청에 대한 명시적 CSRF 검증이 없다.

2. `SameSite=Lax` + CSRF 토큰
   - 상태 변경 요청에 `X-CSRF-Token`을 요구한다.
   - Cookie 기반 인증에서 더 명확한 CSRF 방어를 제공한다.

3. `SameSite=Strict`
   - 더 강한 cookie 전송 제한을 제공한다.
   - OAuth redirect나 외부 진입 UX에서 문제가 생길 수 있다.

### 선택 이유

Rewrite는 HttpOnly Cookie 기반 인증을 사용한다. Cookie는 브라우저가 자동으로 요청에 포함하므로, 상태 변경 API에는 CSRF 토큰을 요구하는 것이 실무적으로 더 안전하다.

### 트레이드오프

- 장점
  - Cookie 기반 인증의 CSRF 위험을 줄인다.
  - 상태 변경 요청에 대한 보안 경계가 명확하다.
  - `SameSite=Lax`를 유지해 OAuth redirect와 일반 진입 UX를 해치지 않는다.

- 단점
  - CSRF 토큰 조회 API와 프론트엔드 헤더 첨부 로직이 필요하다.
  - 토큰 만료나 갱신 실패에 대한 클라이언트 처리가 필요하다.
  - 상태 변경 요청의 단일 재시도와 반복 실패 방지 로직이 필요하다.
  - API 테스트 시 `X-CSRF-Token` 헤더를 함께 준비해야 한다.



## Decision 019: Access token은 30분, Refresh token은 14일이며 refresh token rotation을 사용한다

### 결정

Rewrite 인증 토큰 만료 시간은 다음과 같이 설정한다.

```text
access_token: 30분
refresh_token: 14일
```

Refresh 요청이 성공하면 서버는 새 access token과 새 refresh token을 모두 발급한다. 기존 refresh token은 폐기한다. 이를 통해 refresh token rotation을 적용한다.

API 형태:

```http
POST /auth/refresh
```

응답 헤더:

```http
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=1800
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/auth; Max-Age=1209600
```

### PRD 근거

- 로그인은 카카오 로그인으로 진행한다.
- 인증 토큰은 HttpOnly Cookie로 전달하기로 결정했다.
- 사용자는 로그인 후 자기소개서, 첨삭, 키워드 분석, AI 면접 기능을 지속적으로 사용한다.

### 고려한 대안

1. Access 30분, Refresh 14일, rotation 없음
   - 구현이 단순하다.
   - 하지만 refresh token 탈취나 재사용 탐지에 약하다.

2. Access 30분, Refresh 14일, refresh token rotation
   - 보안과 UX의 균형이 좋다.
   - refresh 시마다 기존 refresh token을 폐기하므로 재사용 탐지와 세션 보호에 유리하다.

3. Access 15분, Refresh 7일, refresh token rotation
   - 더 보수적인 보안 정책이다.
   - 하지만 refresh 빈도와 재로그인 가능성이 늘어 UX 관리 부담이 커진다.

### 선택 이유

Rewrite는 브라우저 기반 웹 서비스이고 HttpOnly Cookie 인증을 사용한다. Access token은 짧게 유지하되, refresh token은 14일로 두어 사용자가 자주 재로그인하지 않게 한다. Refresh token rotation을 적용해 탈취나 재사용 위험을 줄인다.

### 트레이드오프

- 장점
  - Access token 노출 위험 시간을 30분으로 제한한다.
  - Refresh token rotation으로 재사용 탐지가 가능하다.
  - 14일 유지 기간으로 일반적인 웹 서비스 UX를 해치지 않는다.

- 단점
  - 서버가 refresh token 저장소와 폐기 상태를 관리해야 한다.
  - 동시 refresh 요청이 발생하면 race condition 처리가 필요하다.
  - API 테스트와 클라이언트 구현에서 refresh 실패 처리를 고려해야 한다.
