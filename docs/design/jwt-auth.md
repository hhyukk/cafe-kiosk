# 점주 인증 설계: JwtTokenProvider

> ⚠️ **이 문서는 구현 설명서가 아니라 [Phase 1](../roadmap/phase-1.md)의 설계 정본이다.**
> 작성 시점에 대응 코드는 **아직 없다.** `com.cafekiosk.auth` 패키지 자체가 존재하지 않고,
> `build.gradle.kts`에 jjwt 의존성도 없다. Phase 1에서 이 문서대로 구현한다.
> (구현이 끝나면 이 경고를 지운다.)

> 위치(예정): `com.cafekiosk.auth.jwt.JwtTokenProvider`
> 역할: **Access 토큰 발급/검증 전용 컴포넌트**. 점주(Owner) 식별자를 JWT 클레임에 담아 HS256으로 서명하고, 들어오는 토큰의 서명, 만료를 검증한다.

---

## 1. 한 줄 요약

JWT를 "만드는 쪽(`createAccessToken`)"과 "검증하고 까보는 쪽(`validate`, `getPrincipal`)"을 한 클래스에 모은 **stateless 인증의 핵심 유틸**. DB를 건드리지 않고 토큰 자체의 서명/만료만으로 신원을 판단한다.

---

## 2. 전체 코드 흐름 한눈에

```
[로그인]  AuthService.login()
            └─> createAccessToken(ownerId, email)  ──> JWT 문자열 발급 ──> BFF 가 보관

[요청]    BFF 가 Authorization: Bearer <JWT> 헤더를 붙여 요청
            └─> JwtAuthenticationFilter
                  ├─> validate(token)        // 서명과 만료 OK?
                  └─> getPrincipal(token)     // 클레임에서 OwnerPrincipal 복원
                        └─> SecurityContext 에 ROLE_OWNER 인증 세팅
```

발급은 `AuthService`에서, 검증은 `JwtAuthenticationFilter`에서 이 클래스를 호출한다. JwtTokenProvider 자신은 **누가 부르는지 모른다.** 토큰을 다루는 순수 책임만 가진다.

> **토큰을 보관하는 주체는 브라우저가 아니라 BFF다.** FR-AUTH-11. Next.js Route Handler가 발급받은 토큰을 `httpOnly` 쿠키에 심고, 이후 요청마다 그 쿠키를 읽어 헤더로 바꿔 붙인다. 브라우저 JS는 토큰 문자열에 닿지 못한다. 이 클래스 입장에서는 달라지는 것이 없지만, **누가 토큰을 들고 있느냐가 §10의 결론을 바꾼다.**
>
> 이 흐름이 시스템 전체 어디에 놓이는지는 [`architecture.md §5-4`](architecture.md#5-4-인증된-요청)에 있다.

---

## 3. 필드와 생성자

```java
private final SecretKey key;
private final long accessExpirationMillis;

public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-expiration}") long accessExpirationMillis
) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpirationMillis = accessExpirationMillis;
}
```

| 항목 | 설명 |
|------|------|
| `key` | 서명/검증에 쓰는 **대칭키(SecretKey)**. HS256은 발급과 검증에 같은 키를 쓴다(대칭키 방식). |
| `accessExpirationMillis` | 토큰 유효 기간(밀리초). `application.yml` 기본값 `3600000` = 1시간. |
| `@Value("${jwt.secret}")` | `application.yml`의 `jwt.secret` 주입. 기본값은 `dev-only-...` (환경변수 `JWT_SECRET`로 override). |

### 왜 `Keys.hmacShaKeyFor(...)`를 쓰나
- HS256(HMAC-SHA256) 알고리즘은 **최소 256bit = 32byte** 길이의 키를 요구한다.
- 짧은 문자열을 그냥 키로 쓰면 jjwt가 `WeakKeyException`을 던진다.
- `application.yml`의 dev 기본 시크릿이 32byte 이상이라 이 요구치를 만족한다.
- 운영에서는 반드시 `JWT_SECRET` 환경변수로 충분히 긴 랜덤 값을 주입해야 한다. 시크릿을 커밋하지 않는다는 규칙이 `CLAUDE.md`에 있다.

> 생성자에서 키를 **한 번만 만들어 재사용**한다. 매 요청마다 키를 새로 생성하지 않으므로 비용이 낮다. `@Component`라 싱글톤으로 관리된다.

---

## 4. 토큰 발급: `createAccessToken`

```java
public String createAccessToken(Long ownerId, String email) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessExpirationMillis);

    return Jwts.builder()
            .subject(email)            // sub 클레임 = 이메일
            .claim("id", ownerId)      // 커스텀 클레임: 점주 PK
            .claim("role", "OWNER")    // 커스텀 클레임: 역할
            .issuedAt(now)             // iat
            .expiration(expiry)        // exp
            .signWith(key)             // HS256 서명
            .compact();                // 최종 문자열로 직렬화
}
```

### 발급되는 JWT 구조
JWT는 `헤더.페이로드.서명` 세 부분을 `.`으로 이은 Base64URL 문자열이다.

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiJ... . 3x8s_Signature...
   ↑ Header                ↑ Payload(Claims)     ↑ Signature
```

**Payload(Claims)에 담기는 내용:**

| 클레임 | 값 | 의미 |
|--------|-----|------|
| `sub` | email | 토큰 주체(점주 이메일) |
| `id` | ownerId | 점주 PK (DB 조회 없이 식별) |
| `role` | `"OWNER"` | 역할 정보 |
| `iat` | 발급 시각 | issued at |
| `exp` | 만료 시각 | expiration (now + 1시간) |

> ⚠️ Payload는 **암호화가 아니라 Base64 인코딩**일 뿐이다. 누구나 디코딩해 내용을 볼 수 있다. 따라서 **비밀번호 같은 민감정보는 절대 클레임에 넣지 않는다.** 여기서는 식별자(id/email/role)만 담아 안전하다. 서명은 "내용을 숨기는 것"이 아니라 "내용이 위조되지 않았음을 보장"하는 용도다.

---

## 5. 토큰 검증: `validate`

```java
public boolean validate(String token) {
    try {
        parse(token);
        return true;
    } catch (JwtException | IllegalArgumentException e) {
        return false;
    }
}
```

- `parse(token)`가 예외 없이 통과하면 **유효한 토큰** → `true`.
- 다음 경우는 모두 `JwtException` 계열로 잡혀 `false`:
  - **서명 불일치**(위조/변조) → `SignatureException`
  - **만료** → `ExpiredJwtException`
  - **형식 오류**(JWT 구조가 아님) → `MalformedJwtException`
  - **null/빈 문자열** → `IllegalArgumentException`
- 예외를 boolean으로 흡수하므로, 호출하는 필터는 단순히 `if (validate(token))`만 보면 된다.

---

## 6. 신원 복원: `getPrincipal`

```java
public OwnerPrincipal getPrincipal(String token) {
    Claims claims = parse(token);
    // JSON 숫자는 작은 값일 때 Integer 로 역직렬화될 수 있으므로 Number 로 받아 longValue 로 변환
    Number id = claims.get("id", Number.class);
    return new OwnerPrincipal(id.longValue(), claims.getSubject());
}
```

- 검증된 토큰의 클레임에서 `id`와 `subject(email)`를 꺼내 **`OwnerPrincipal` record**로 만든다.
- 이 principal이 `JwtAuthenticationFilter`에서 `SecurityContext`에 들어가고, 컨트롤러에서 `@AuthenticationPrincipal OwnerPrincipal owner`로 주입받아 쓴다.

### 왜 `Number`로 받고 `.longValue()`를 호출하나 (중요한 함정)
- JWT 클레임은 내부적으로 **JSON**으로 직렬화된다.
- jjwt가 JSON 숫자를 역직렬화할 때, **값이 `Integer` 범위(약 21억)면 `Integer`, 넘으면 `Long`**으로 만든다.
- 따라서 `claims.get("id", Long.class)`로 바로 받으면, ownerId가 작을 때 `Integer → Long` 캐스팅 실패로 **`ClassCastException`**이 터질 수 있다.
- `Number`(Integer/Long의 공통 부모)로 받은 뒤 `longValue()`로 변환하면 값 크기와 무관하게 안전하다.

---

## 7. 내부 헬퍼: `parse`

```java
private Claims parse(String token) {
    return Jwts.parser()
            .verifyWith(key)            // 같은 대칭키로 서명 검증
            .build()
            .parseSignedClaims(token)   // 서명과 만료 검증 + Claims 추출
            .getPayload();
}
```

- `validate`와 `getPrincipal`이 공유하는 핵심 로직.
- `parseSignedClaims`는 **서명 검증과 만료 검증을 동시에** 수행하고, 실패 시 적절한 `JwtException`을 던진다.
- `private`이라 외부에 노출되지 않는다. 검증은 항상 `validate`/`getPrincipal`을 통해서만.

---

## 8. 설계 포인트 정리

| 결정 | 이유 |
|------|------|
| **stateless** (검증 시 DB 미조회) | 토큰 서명, 만료만으로 신원 보장. 세션 저장소가 필요 없어 수평 확장에 유리. |
| **HS256 (대칭키)** | 단일 서버/단일 발급자 구조에서 충분. 키 하나로 발급, 검증. (다중 서비스가 검증만 해야 하면 RS256 비대칭키 고려) |
| **검증 예외를 boolean으로 흡수** | 호출부(필터)가 예외 처리 부담 없이 분기. |
| **`Number` → `longValue`** | JSON 역직렬화의 Integer/Long 함정 회피. |
| **발급/검증 책임만 가짐** | 인증 흐름(필터), 인가 정책(SecurityConfig)과 분리. SRP 준수. |

---

## 9. 협력 클래스 빠른 참조

| 클래스 | 관계 |
|--------|------|
| `AuthService` | 로그인 성공 시 `createAccessToken` 호출 → 토큰 발급 |
| `JwtAuthenticationFilter` | 매 요청마다 `validate` + `getPrincipal` 호출 → SecurityContext 세팅 |
| `OwnerPrincipal` | `getPrincipal`이 반환하는 record (`id`, `email`) |
| `SecurityConfig` | 필터 등록 + URL별 인가 규칙(점주 API는 ROLE_OWNER 요구) |
| `application.yml` | `jwt.secret`, `jwt.access-expiration` 설정 주입 |

---

## 10. 현재 한계 / 향후 개선 여지

- **Refresh 토큰 없음**: 현재는 Access 토큰(1시간) 단일. 만료 시 재로그인 필요. 추후 Refresh 토큰 + 재발급 엔드포인트 도입 여지. FR-AUTH-10의 의도적 단순화다.
- **토큰 무효화는 쿠키 삭제로 대신한다**: stateless 특성상 발급된 토큰은 만료 전까지 유효하다는 사실 자체는 변하지 않는다. 다만 토큰을 브라우저가 아니라 **BFF가 `httpOnly` 쿠키로 들고 있으므로**(FR-AUTH-11), 쿠키를 지우면 그 토큰을 다시 보낼 주체가 사라진다. **Redis 블랙리스트 없이 실질 로그아웃이 성립한다.** FR-AUTH-12.
  - 남는 한계는 하나다. **유출된 토큰은 만료까지 유효하다.** 쿠키 삭제는 정상 로그아웃을 처리할 뿐 탈취된 토큰을 무효화하지 못한다. 그 시나리오까지 막으려면 블랙리스트가 필요하고, 이 프로젝트에서는 만료 1시간으로 감수한다.
- **role이 `"OWNER"` 하드코딩**: 역할이 늘어나면 Owner 엔티티의 role 필드로 분리 고려. 현재는 바리스타와 점주를 하나의 역할로 묶는 것이 의도된 설계다. FR-AUTH-08.
