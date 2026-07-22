# Phase 1: 키오스크 루프 완성  🔶 진행 중

> [← 로드맵 인덱스](../ROADMAP.md), [배경과 "왜"](../PRODUCT.md)
> 기준: 현재 `main` 코드 / 갱신: 2026-07-22

---

## 왜 지금인가

주방 화면이 없으면 주문 상태머신은 **아무도 호출하지 않는 코드**다. 재고(Phase 2)도 화면에서 품절이 보여야 의미가 있다. 여기서 제품이 처음으로 **끝에서 끝까지** 이어진다.

그리고 화면을 셋으로 나누는 일은 UI 정리가 아니라 **권한 경계를 만드는 일**이다. 그래서 이 단계에 Spring Security가 함께 들어간다. 손님은 익명, 점주/바리스타는 인증. 키오스크에서 손님에게 로그인을 요구하는 건 제품 실패다.

---

## 현재 코드 상태

### ✅ 이미 있는 것

| 무엇 | 어디 |
| --- | --- |
| 주문 상태머신 (`CONFIRMED → IN_PROGRESS → READY → COMPLETED`, `CANCELLED`) | `order/entity/Order.java`. 전이 규칙을 엔티티가 소유, POJO 테스트 8개 |
| 상태 변경 API `PATCH /api/order/{orderId}/status` | `order/controller/OrderController.java:111` |
| 주문 조회 응답의 `orderId`, `orderNumber`, `status`, `orderTime`, `totalPrice` | `order/dto/OrderDto.java` `OrderSummary` |
| 점주/주방용 목록 API `GET /api/orders?status=` (FIFO 정렬) | `OrderController.java:87`, `OrderService.getOrdersByStatus` |
| 잘못된 전이 → `409` | `global/globalExceptionHandler/GlobalExceptionHandler.java` |

**주방 화면을 만들 수 있는 백엔드 재료는 다 있다.** 남은 건 인증과 화면이다.

### ⬜ 없는 것: 조사로 확인

- **인증 코드가 한 줄도 없다.** `com.cafekiosk.auth` 패키지 자체가 없고, `build.gradle.kts`에 `spring-boot-starter-security`도 jjwt도 없다. `Owner` 엔티티도 없다.
  - 단, **설계 문서는 이미 있다** → [`docs/design/jwt-auth.md`](../design/jwt-auth.md). `JwtTokenProvider`, `OwnerPrincipal`, `JwtAuthenticationFilter`, `SecurityConfig`, `AuthService`의 책임 분담과 함정까지 적혀 있다. **이 Phase의 설계 정본으로 삼는다.**
- **인가가 요청 본문의 이메일 문자열 비교뿐이다.** `MenuService.modify`가 `menu.getEmail().equals(email)`, `deleteMenu`가 `deleteByIdAndEmail(...)`. 이메일만 알면 남의 메뉴를 수정, 삭제할 수 있다. 주문 상태 변경 API는 아예 공개다.
- **손님이 자기 주문 상태를 볼 경로가 사실상 없다.** 유일한 조회가 `POST /api/order/list`와 이메일이다. 조회인데 POST이고, 남의 이메일을 알면 남의 주문이 보인다.
- **프론트가 `src/app/page.tsx` 단일 컴포넌트 1230줄이다.** 손님용 상품 목록에 메뉴 추가, 수정, 삭제 버튼이 그대로 노출돼 있고, 삭제 권한 확인이 `window.prompt("삭제 권한 확인을 위해 이메일을 입력해주세요")`(`page.tsx:352`)다.
- **주문 완료가 `alert`다** (`page.tsx:194`). 대기번호를 크게 보여주는 화면이 없다.
- **주방용 BFF 라우트가 없다.** 현재 핸들러는 `api/menu`, `api/menu/[menuId]`, `api/order`, `api/order/history` 넷뿐이다.

---

## 소유권 모델: 이 Phase에서 확정한 것

**`Owner` 엔티티를 신설하되 `Menu.email: String`은 그대로 둔다.**

인가는 "인증된 `ROLE_OWNER`면 통과"로 단순화한다. `Menu.email`은 **"누가 등록했는가"의 기록**으로 남고, 더 이상 권한 판단에 쓰이지 않는다.

`Menu.owner` FK로 전환하지 않는 이유는 이렇다. 1인 매장 키오스크에서 메뉴별 소유자 분리는 실익이 없고, `MenuService`/`MenuController`/DTO/`BaseInitData`/테스트까지 연쇄 수정이 발생한다. 이 레포의 주제인 동시성에서 멀어지는 비용이다.

---

## 작업 단위

PR 하나 = 표 한 행. **`#3`과 `#4`는 사실상 한 몸이다**(아래 함정 참고).

**`요구사항` 열이 이 Phase가 소화하는 것의 정본이다.** ID의 진술 자체는 [`REQUIREMENTS.md §5`](../REQUIREMENTS.md), 검증 수단은 그 문서 §11에 있다.

| # | PR | 요구사항 | 건드리는 파일 | 선행 |
| --- | --- | --- | --- | --- |
| 1 | `feat: Owner 엔티티와 점주 계정 시드 추가` | FR-AUTH-02 | 신설 `owner/entity/Owner`, `owner/repository/OwnerRepository`, `global/initData/BaseInitData` | 없음 |
| 2 | `feat: JWT 토큰 발급과 검증 컴포넌트 추가` | FR-AUTH-01, FR-AUTH-03<br>FR-AUTH-07, FR-AUTH-10<br>NFR-SEC-01, NFR-SEC-05 | 신설 `auth/jwt/JwtTokenProvider`, `auth/OwnerPrincipal`, `build.gradle.kts`, `application.yml`, `.env.example` | 1 |
| 3 | `feat: Spring Security 도입하고 점주 API 보호` | FR-AUTH-04, FR-AUTH-05<br>FR-AUTH-06, FR-AUTH-08<br>FR-MNU-02, FR-KIT-06, FR-FILE-04<br>NFR-SEC-04, NFR-SEC-06 | 신설 `global/config/SecurityConfig`, `auth/JwtAuthenticationFilter`, `auth/service/AuthService`, `auth/controller/AuthController`, `global/config/WebConfig` | 2 |
| 4 | `test: 기존 통합 테스트를 인증 체계에 맞게 수정` | **AC-12**<br>NFR-TEST-02, NFR-TEST-05 | `menu/controller/MenuControllerTest`, `order/controller/OrderControllerTest` | 3 |
| 5 | `refactor: 메뉴 API를 REST 경로와 RsData로 통일` | FR-MNU-03, FR-MNU-05, FR-MNU-07<br>FR-MNU-08, FR-MNU-09, FR-MNU-01 | `MenuController`, `MenuService`, `menu/dto/*`, `menu/entity/Menu`, 프론트 `api/menu/*` | 3 |
| 6 | `feat: 대기번호로 주문 상태를 조회하는 API 추가` | FR-KSK-06, FR-KSK-09, FR-KSK-11<br>FR-ORD-08, **AC-13** | `OrderController`, `OrderService`, `OrderRepository` | 없음 |
| 7 | `feat: 손님 키오스크 화면 정리 및 주문 완료 화면 추가` | FR-KSK-04, FR-KSK-05, FR-KSK-10<br>FR-ADM-03, NFR-UX-02, NFR-UX-04 | `frontend/src/app/page.tsx`를 축소, 신설 주문완료, 조회 화면 | 6 |
| 8 | `feat: 주방 화면 추가 (/kitchen)` | FR-KIT-01, FR-KIT-02, FR-KIT-03<br>FR-KIT-04, FR-KIT-05<br>NFR-UX-01, NFR-UX-03, **AC-14** | 신설 `frontend/src/app/kitchen/page.tsx`, BFF `api/orders/*` | 3, 7 |
| 9 | `feat: 이미지 업로드를 BFF로 옮긴다` | FR-FILE-04, FR-FILE-05, FR-AUTH-09 | 신설 `frontend/src/app/api/upload/route.ts`, `page.tsx:285`, `page.tsx:1003` | 3 |
| 10 | `feat: 관리자 화면 추가 (/admin)` | FR-ADM-01, FR-ADM-03<br>FR-ADM-04, FR-ADM-05<br>FR-AUTH-09, FR-AUTH-11, FR-AUTH-12 | 신설 `frontend/src/app/admin/page.tsx`, 로그인 화면, 신설 BFF `api/auth/login`, `api/auth/logout` | 5, 8, 9 |
| 11 | `feat: 주문 아이템에 메뉴 이름 스냅샷 추가` | FR-ORD-14, FR-MNU-06<br>**AC-01** | `order/entity/OrderItem`, `OrderService.toItemDTO`, `OrderControllerTest` | 없음 |

`#6`과 `#11`은 백엔드 단독이라 `#1~#5`와 병행 가능하다. 나머지는 표의 선행 순서를 지킨다.

이 Phase에서 소화하지 않는 요구사항은 다른 Phase가 가져간다. 재고 계열은 [Phase 2](phase-2.md), 동시성은 [Phase 3](phase-3.md), 배포와 남은 부채는 [Phase 4](phase-4.md)다.

### PR별 메모

**#1 Owner 엔티티**
`Owner(email, passwordHash)`. `BaseInitData`에 점주 1명을 시드하되, **비밀번호는 `BCryptPasswordEncoder`로 해싱해서 넣는다**(평문 금지). `BaseInitData`의 가드는 지금 `menuRepository.count()`를 보는데, Owner를 심는 블록은 `ownerRepository.count()`로 따로 가드한다. Phase 0에서 "가드가 세는 대상과 심는 대상이 어긋나면 매 기동 재실행된다"로 한 번 물린 곳이다.

**#2 JWT**
[`docs/design/jwt-auth.md`](../design/jwt-auth.md)가 이 PR의 설계 정본이다. `claims.get("id", Number.class).longValue()` 함정, `Keys.hmacShaKeyFor`의 최소 32byte 요구, 클레임에 민감정보 금지. 전부 그 문서에 이미 있다. **문서를 다시 쓰지 말고 그대로 구현한다.**
`build.gradle.kts`에 jjwt를 추가할 때 **사유 주석을 남긴다**(PR 템플릿 요구사항). `application.yml`에 `jwt.secret`(`.env`에서 주입), `jwt.access-expiration: 3600000`.

**#3 SecurityConfig**

반드시 포함해야 하는 것:

```
csrf.disable()                          // stateless JSON API. 안 끄면 프론트의 모든 쓰기 요청이 403
sessionCreationPolicy(STATELESS)
.cors(withDefaults())                   // 아래 CORS 이관과 세트
addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
exceptionHandling(e -> e.authenticationEntryPoint(...))   // 없으면 401이 아니라 403이 나간다
```

**마지막 줄을 빠뜨리면 `AC-12`가 통째로 실패한다.** 폼 로그인도 HTTP Basic도 쓰지 않으므로 Spring Security에 등록된 진입점이 없고, 그러면 인증 없는 요청이 **403으로 돌아간다.** FR-AUTH-04가 401을 요구하는 것은 취향이 아니라 BFF가 로그인 화면으로 보낼지를 그 코드로 판단하기 때문이다. 진입점은 `RsData` 형태의 401 본문을 직접 써서 내려준다.

| 접근 | 대상 |
| --- | --- |
| `permitAll` | `POST /api/order`, `GET /api/menu**`, `GET /api/orders/{orderNumber}`, `POST /api/auth/login`, `/uploads/**`, `/swagger-ui/**`, `/v3/api-docs/**` |
| `hasRole("OWNER")` | `GET /api/orders`, `PATCH /api/order/*/status`, 메뉴 쓰기(`POST`/`PUT`/`DELETE`), `POST /api/upload/image` |

**CORS를 `WebConfig`에서 `SecurityConfig`의 `CorsConfigurationSource` 빈으로 옮긴다.** 두 가지 이유다. (1) Security 필터체인은 `WebMvcConfigurer`의 CORS 설정보다 앞서 돌기 때문에 `.cors()` 연결 없이는 preflight가 401이 된다. (2) 지금 `WebConfig.addCorsMappings`의 `allowedMethods`에 **`PATCH`가 빠져 있어서**(`GET, POST, PUT, DELETE, OPTIONS`) 그대로 두면 주방 화면의 상태 변경이 preflight에서 막힌다. `/uploads/**` 정적 리소스 핸들러는 `WebConfig`에 그대로 남긴다.

**#4 테스트 복구**
손님 엔드포인트 테스트는 수정 없이 통과해야 한다. 점주 엔드포인트 테스트에만 `Authorization: Bearer` 헤더 또는 `@WithMockUser(roles = "OWNER")`를 붙인다.
**"안 되니까 전부 permitAll"로 도망가지 않는다.** 토큰 없이 점주 API를 호출하면 401이라는 것을 확인하는 테스트를 최소 2개 추가한다. 그게 이 PR의 실제 산출물이다.

**#5 메뉴 API 정리**
[`PRODUCT.md §7`](../PRODUCT.md)의 목표를 여기서 소화한다.

- `PUT /api/menu/modify/{id}` → `PUT /api/menus/{id}`
- `DELETE /api/menu/delete/{menu_id}` → `DELETE /api/menus/{id}`
- `MenuService.modify`의 `menu.getEmail().equals(email)` 분기와 `deleteByIdAndEmail`, `DeleteMenuRequestDto`의 이메일 필드는 **폐기한다.** 인가는 필터가 한다
- **메뉴 등록 시 `Menu.email`을 요청 본문에서 받지 않고 `SecurityContext`의 `OwnerPrincipal`에서 채운다.** FR-MNU-08. 이 필드는 "누가 등록했는가"의 기록으로만 남는데, 값을 클라이언트가 준다면 기록으로서도 믿을 수 없다. 인가에서 손을 떼는 것과 값의 출처를 서버로 옮기는 것은 **다른 작업이고 둘 다 해야 한다**
- raw `String` / raw `List<>` 응답을 `RsData<T>`로 통일
- **메뉴 삭제를 판매중지로 바꾼다.** FR-MNU-09. `Menu`에 판매중지 필드와 전용 메서드를 두고 목록 조회는 판매중인 것만 내려준다. 행을 실제로 지우면 `OrderItem`이 그 메뉴를 참조하고 있어 과거 주문이 무너진다. Phase 2에서 `Stock`이 1:1로 매달리면 같은 문제가 하나 더 생긴다. **이미 판매중지된 메뉴를 또 지우는 요청은 404가 아니라 멱등 성공이다**
- **가격 경계를 등록과 수정에서 같게 만든다.** FR-MNU-03. 지금 `createMenu`는 0원을 허용하고 `MenuModifyRequest`는 `@Positive`라 0원을 거부한다. 요구사항은 0원 이상이므로 `@PositiveOrZero`와 상한 검증을 DTO에 선언하고, **컨트롤러 안의 `if`문 검증은 걷어낸다.** 검증 위반은 예외로 흘려보내 `GlobalExceptionHandler`가 받는다

`frontend/CLAUDE.md`가 경고하듯 **BFF 핸들러(`api/menu/route.ts`, `api/menu/[menuId]/route.ts`)를 같이 고쳐야 한다.** 백엔드만 고치면 프론트가 조용히 깨진다.

**#6 대기번호 조회**
`GET /api/orders/{orderNumber}` 신설. 손님이 받은 대기번호로 자기 주문 상태만 확인한다. `OrderRepository.findByOrderNumber` 추가.
**신설이므로 처음부터 `RsData<T>`로 만든다.** 기존 주문 API 두 곳의 raw record 응답은 FR-ORD-15로 Phase 4에서 함께 교체한다.
**응답에 손님 이메일을 넣지 않는다.** FR-KSK-11. 대기번호는 단조 증가라 열거가 쉬워서, 응답에 개인 식별정보가 있으면 번호를 세는 것만으로 손님 명부가 된다. 열거 자체를 막지 않기로 한 판단은 `REQUIREMENTS.md §10`에 있다.
`POST /api/order/list`(이메일 기반)는 관리자 화면이 옮겨갈 때까지 유예하고 **Phase 4에서 폐기**한다.

**#7~#10 프론트 3분할**
`page.tsx`는 1230줄 단일 `"use client"` 컴포넌트다. `frontend/CLAUDE.md`대로 **전체를 다시 쓰지 않는다.** 손님 영역만 `/`에 남기고, 관리자 영역인 메뉴 추가, 수정, 삭제 모달을 `/admin`으로 잘라내는 방식으로 쪼갠다.
주방 화면은 **폴링 3초**. WebSocket은 Phase 3 이후 선택이다. 동시성이 먼저다.
BFF 규약을 지킨다. 브라우저가 8080을 직접 부르지 않고, **토큰은 BFF가 붙인다.**

**#9 업로드 BFF: `#3`을 머지했으면 반드시 따라와야 하는 PR이다**

지금 이미지 업로드는 `page.tsx:285`와 `page.tsx:1003`에서 **브라우저가 8080을 직접 호출한다.** BFF를 거치지 않는 두 곳이 여기다.

`#3`이 `POST /api/upload/image`를 `hasRole("OWNER")`로 잠그는 순간 이 경로는 **401이 된다.** 토큰은 `httpOnly` 쿠키에 있어서(FR-AUTH-11) 브라우저 JS가 읽을 수 없고, 읽을 수 없으니 `Authorization` 헤더를 만들 수도 없다. **토큰을 붙일 수 있는 주체는 BFF뿐이다.**

그래서 `app/api/upload/route.ts`를 신설하고 `page.tsx`의 두 곳이 그리로 가게 바꾼다. `multipart/form-data`를 그대로 넘겨야 하므로 `FormData`를 재구성하지 말고 `request.body`를 전달한다.

**이 PR을 빠뜨리면 `/admin`에서 이미지를 올릴 수 없는 채로 Phase 2, 3을 지나간다.** `AC-14`는 손님 흐름이라 이 결함을 잡아내지 못한다.

**#11 이름 스냅샷: `#5`보다 먼저 머지하는 편이 낫다**

가격은 `OrderItem` 생성자가 메뉴에서 복사해 두는데 이름은 조회할 때마다 현재 메뉴에서 읽는다. 그래서 **메뉴 이름을 고치면 과거 주문의 표시가 소급 변경된다.** 금액은 그대로라 가격 스냅샷 회귀 테스트가 잡지 못하고, 영수증에서 이름만 조용히 바뀐다.

`#5`가 판매중지를 도입하면 이 구멍이 더 커진다. 팔지 않는 메뉴를 담은 주문을 조회할 때 이름을 어디서 읽을 것인가. 그래서 이름 스냅샷을 먼저 깔아두는 편이 낫다.

`AC-01`의 회귀 테스트에 이름 검증을 함께 넣는다. **금액만 보는 테스트는 이 회귀를 통과시킨다.**

**#10 로그인과 로그아웃은 한 쌍이다**

`app/api/auth/login/route.ts`가 백엔드에서 받은 토큰을 `httpOnly` 쿠키에 심고(FR-AUTH-11), `app/api/auth/logout/route.ts`가 그 쿠키를 지운다(FR-AUTH-12). **로그아웃은 백엔드 엔드포인트가 아니다.** 발급된 토큰은 만료까지 유효하지만, 쿠키가 사라지면 그걸 다시 보낼 주체가 없어서 실질 로그아웃이 성립한다. Redis 블랙리스트가 필요 없는 이유다.

**지난 주문 조회 화면도 이 PR이다.** FR-ADM-05. 점주가 완료, 취소된 주문을 상태별로 되짚는 화면이고, API는 신설하지 않고 `#8`이 이미 쓰는 `GET /api/orders`를 그대로 쓴다. 주방 화면이 *지금 만들 것*만 보는 작업 화면인 것과 액터가 다르다.

**이 화면이 없으면 [Phase 4](phase-4.md)에서 `POST /api/order/list`를 폐기할 수 없다.** 이메일 기반 조회를 쓰던 관리자 기능이 갈 곳이 여기다. 폐기의 전제를 만드는 것이 이 작업이다.

---

## 함정

- **Security를 추가하는 순간 기존 통합 테스트 22개가 한꺼번에 죽는다.** `MenuControllerTest` 3개 + `OrderControllerTest` 19개가 전부 MockMvc로 돈다. Swagger UI도 같이 막힌다. `#3`을 머지하고 `#4`를 나중에 하면 그 사이 CI가 빨갛다. **두 PR을 연달아 처리하거나 하나로 합친다.**
- **`spring-boot-starter-security`는 CSRF가 기본 ON이다.** 안 끄면 프론트의 모든 `POST`/`PUT`/`DELETE`/`PATCH`가 403이 된다. 껐다는 사실과 이유(stateless JSON API)를 `SecurityConfig`에 주석으로 남긴다.
- **`WebConfig`의 CORS `allowedMethods`에 `PATCH`가 없다.** 위 `#3` 메모 참고.
- **`jwt.secret`을 커밋하지 않는다.** `.env`로 빼고 `.env.example`엔 키 이름만 넣는다. HS256은 32byte 이상을 요구한다.
- **`Menu.email`은 남지만 더 이상 인가에 쓰이지 않는다.** 이 사실을 `Menu` 엔티티에 주석으로 남긴다. 안 남기면 다음에 이 필드를 보는 사람이 다시 권한 판단에 쓴다.
- **`/api/upload/image`를 잠그기만 하고 BFF를 안 만들면 점주 화면이 깨진다.** 지금은 누구나 서버 디스크에 파일을 쓸 수 있어서 잠그는 것이 맞지만, 잠그는 `#3`과 토큰을 붙이는 `#9`는 **한 몸이다.** 둘 사이에 `/admin`을 손대면 이미지 업로드가 401로 돌아온다. 화면 분리에 묻혀 빠뜨리기 쉬운 엔드포인트다.
- **`dev` 프로필은 재시작마다 DB를 드롭한다**(`ddl-auto: create`). 시드 점주 계정도 매번 새로 생성된다. 로컬에서 로그인이 안 되면 버그가 아니라 이것일 수 있다.

---

## 완료 기준

- [ ] 손님이 `/`에서 **로그인 없이** 주문하고, 대기번호가 화면에 크게 뜬다. `alert`가 아니다
- [ ] 그 주문이 3초 안에 `/kitchen`에 나타나고, 바리스타가 `제조중 → 준비완료`로 넘기면 손님 조회 화면의 상태가 바뀐다
- [ ] 토큰 없이 `GET /api/orders`, `PATCH /api/order/{id}/status`, `POST /api/menus`, `POST /api/upload/image` → **401**. 403이면 진입점을 안 붙인 것이다
- [ ] 메뉴를 삭제해도 그 메뉴가 담긴 **과거 주문 조회가 깨지지 않는다.** 손님 목록에서만 사라진다
- [ ] **점주가 `/admin`에서 이미지를 올리면 성공한다.** 브라우저 네트워크 탭에서 8080을 직접 부르는 요청이 없다
- [ ] 점주가 `/admin`에서 로그아웃하면 쿠키가 지워지고, 이후 점주 API 호출이 401이다
- [ ] 점주가 `/admin`에서 완료, 취소된 지난 주문을 상태별로 조회할 수 있다
- [ ] 레포 전체에서 "요청 본문 이메일로 권한을 확인"하는 코드가 사라졌다
- [ ] `./gradlew test` 통과. 401, 403 검증 테스트 포함
- [ ] 위 흐름이 **브라우저에서 실제로 돈다.** PR에 스크린샷 첨부

---

## 여기서 하지 않는 것

| 안 하는 것 | 언제 / 왜 |
| --- | --- |
| 재고 차감 | **Phase 2.** 지금은 재고가 0이어도 주문이 들어간다. 알고 있는 상태로 넘어간다 |
| WebSocket 실시간 푸시 | 폴링 3초로 충분하다. Phase 3 이후 선택 |
| 컨트롤러 `@Transactional` 제거 | **Phase 2 PR #1.** 낙관적 락 재시도의 선행조건이지만 인증과 섞으면 PR이 커진다 |
| `getOrdersByStatus`의 N+1 | **Phase 4.** 주방 활성 주문은 소량이라 지금은 허용 |
| `localhost:8080` 하드코딩 | **Phase 4.** 새 BFF 핸들러도 일단 기존 패턴을 따른다 |
| 손님 회원가입 | 영구 스코프아웃. [`PRODUCT.md §5`](../PRODUCT.md) |
