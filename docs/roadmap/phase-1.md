# Phase 1 — 키오스크 루프 완성  🔶 진행 중

> [← 로드맵 인덱스](../ROADMAP.md) · [배경과 "왜"](../PRODUCT.md)
> 기준: 현재 `main` 코드 / 갱신: 2026-07-21

---

## 왜 지금인가

주방 화면이 없으면 주문 상태머신은 **아무도 호출하지 않는 코드**다. 재고(Phase 2)도 화면에서 품절이 보여야 의미가 있다. 여기서 제품이 처음으로 **끝에서 끝까지** 이어진다.

그리고 화면을 셋으로 나누는 일은 UI 정리가 아니라 **권한 경계를 만드는 일**이다. 그래서 이 단계에 Spring Security가 함께 들어간다 — 손님은 익명, 점주/바리스타는 인증. 키오스크에서 손님에게 로그인을 요구하는 건 제품 실패다.

---

## 현재 코드 상태

### ✅ 이미 있는 것

| 무엇 | 어디 |
| --- | --- |
| 주문 상태머신 (`CONFIRMED → IN_PROGRESS → READY → COMPLETED`, `CANCELLED`) | `order/entity/Order.java` — 전이 규칙을 엔티티가 소유, POJO 테스트 8개 |
| 상태 변경 API `PATCH /api/order/{orderId}/status` | `order/controller/OrderController.java:111` |
| 주문 조회 응답의 `orderId`·`orderNumber`·`status`·`orderTime`·`totalPrice` | `order/dto/OrderDto.java` `OrderSummary` |
| 점주/주방용 목록 API `GET /api/orders?status=` (FIFO 정렬) | `OrderController.java:87`, `OrderService.getOrdersByStatus` |
| 잘못된 전이 → `409` | `global/globalExceptionHandler/GlobalExceptionHandler.java` |

**주방 화면을 만들 수 있는 백엔드 재료는 다 있다.** 남은 건 인증과 화면이다.

### ⬜ 없는 것 — 조사로 확인

- **인증 코드가 한 줄도 없다.** `com.cafekiosk.auth` 패키지 자체가 없고, `build.gradle.kts`에 `spring-boot-starter-security`도 jjwt도 없다. `Owner` 엔티티도 없다.
  - 단, **설계 문서는 이미 있다** → [`docs/design/jwt-auth.md`](../design/jwt-auth.md). `JwtTokenProvider`·`OwnerPrincipal`·`JwtAuthenticationFilter`·`SecurityConfig`·`AuthService`의 책임 분담과 함정까지 적혀 있다. **이 Phase의 설계 정본으로 삼는다.**
- **인가가 요청 본문의 이메일 문자열 비교뿐이다.** `MenuService.modify`가 `menu.getEmail().equals(email)`, `deleteMenu`가 `deleteByIdAndEmail(...)`. 이메일만 알면 남의 메뉴를 수정·삭제할 수 있다. 주문 상태 변경 API는 아예 공개다.
- **손님이 자기 주문 상태를 볼 경로가 사실상 없다.** 유일한 조회가 `POST /api/order/list` + 이메일 — 조회인데 POST이고, 남의 이메일을 알면 남의 주문이 보인다.
- **프론트가 `src/app/page.tsx` 단일 컴포넌트 1230줄이다.** 손님용 상품 목록에 메뉴 추가·수정·삭제 버튼이 그대로 노출돼 있고, 삭제 권한 확인이 `window.prompt("삭제 권한 확인을 위해 이메일을 입력해주세요")`(`page.tsx:352`)다.
- **주문 완료가 `alert`다** (`page.tsx:194`). 대기번호를 크게 보여주는 화면이 없다.
- **주방용 BFF 라우트가 없다.** 현재 핸들러는 `api/menu`, `api/menu/[menuId]`, `api/order`, `api/order/history` 넷뿐이다.

---

## 소유권 모델 — 이 Phase에서 확정한 것

**`Owner` 엔티티를 신설하되 `Menu.email: String`은 그대로 둔다.**

인가는 "인증된 `ROLE_OWNER`면 통과"로 단순화한다. `Menu.email`은 **"누가 등록했는가"의 기록**으로 남고, 더 이상 권한 판단에 쓰이지 않는다.

`Menu.owner` FK로 전환하지 않는 이유 — 1인 매장 키오스크에서 메뉴별 소유자 분리는 실익이 없고, `MenuService`/`MenuController`/DTO/`BaseInitData`/테스트까지 연쇄 수정이 발생한다. 이 레포의 주제인 동시성에서 멀어지는 비용이다.

---

## 작업 단위

PR 하나 = 표 한 행. **`#3`과 `#4`는 사실상 한 몸이다**(아래 함정 참고).

| # | PR | 건드리는 파일 | 선행 |
| --- | --- | --- | --- |
| 1 | `feat: Owner 엔티티와 점주 계정 시드 추가` | 신설 `owner/entity/Owner`·`owner/repository/OwnerRepository`, `global/initData/BaseInitData` | — |
| 2 | `feat: JWT 토큰 발급·검증 컴포넌트 추가` | 신설 `auth/jwt/JwtTokenProvider`·`auth/OwnerPrincipal`, `build.gradle.kts`, `application.yml`, `.env.example` | 1 |
| 3 | `feat: Spring Security 도입하고 점주 API 보호` | 신설 `global/config/SecurityConfig`·`auth/JwtAuthenticationFilter`·`auth/service/AuthService`·`auth/controller/AuthController`, `global/config/WebConfig` | 2 |
| 4 | `test: 기존 통합 테스트를 인증 체계에 맞게 수정` | `menu/controller/MenuControllerTest`, `order/controller/OrderControllerTest` | 3 |
| 5 | `refactor: 메뉴 API를 REST 경로와 RsData로 통일` | `MenuController`, `MenuService`, `menu/dto/*`, 프론트 `api/menu/*` | 3 |
| 6 | `feat: 대기번호로 주문 상태를 조회하는 API 추가` | `OrderController`, `OrderService`, `OrderRepository` | — |
| 7 | `feat: 손님 키오스크 화면 정리 및 주문 완료 화면 추가` | `frontend/src/app/page.tsx`(축소), 신설 주문완료·조회 화면 | 6 |
| 8 | `feat: 주방 화면 추가 (/kitchen)` | 신설 `frontend/src/app/kitchen/page.tsx`, BFF `api/orders/*` | 3, 7 |
| 9 | `feat: 관리자 화면 추가 (/admin)` | 신설 `frontend/src/app/admin/page.tsx`, 로그인 화면, 토큰 보관 | 5, 8 |

`#6`은 백엔드 단독이라 `#1~#5`와 병행 가능하다. 나머지는 표의 선행 순서를 지킨다.

### PR별 메모

**#1 — Owner 엔티티**
`Owner(email, passwordHash)`. `BaseInitData`에 점주 1명을 시드하되, **비밀번호는 `BCryptPasswordEncoder`로 해싱해서 넣는다**(평문 금지). `BaseInitData`의 가드는 지금 `menuRepository.count()`를 보는데, Owner를 심는 블록은 `ownerRepository.count()`로 따로 가드한다 — Phase 0에서 "가드가 세는 대상과 심는 대상이 어긋나면 매 기동 재실행된다"로 한 번 물린 곳이다.

**#2 — JWT**
[`docs/design/jwt-auth.md`](../design/jwt-auth.md)가 이 PR의 설계 정본이다. `claims.get("id", Number.class).longValue()` 함정, `Keys.hmacShaKeyFor`의 최소 32byte 요구, 클레임에 민감정보 금지 — 전부 그 문서에 이미 있다. **문서를 다시 쓰지 말고 그대로 구현한다.**
`build.gradle.kts`에 jjwt를 추가할 때 **사유 주석을 남긴다**(PR 템플릿 요구사항). `application.yml`에 `jwt.secret`(`.env`에서 주입)·`jwt.access-expiration: 3600000`.

**#3 — SecurityConfig**

반드시 포함해야 하는 것:

```
csrf.disable()                          // stateless JSON API. 안 끄면 프론트의 모든 쓰기 요청이 403
sessionCreationPolicy(STATELESS)
.cors(withDefaults())                   // 아래 CORS 이관과 세트
addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

| 접근 | 대상 |
| --- | --- |
| `permitAll` | `POST /api/order`, `GET /api/menu**`, `GET /api/orders/{orderNumber}`, `POST /api/auth/login`, `/uploads/**`, `/swagger-ui/**`, `/v3/api-docs/**` |
| `hasRole("OWNER")` | `GET /api/orders`, `PATCH /api/order/*/status`, 메뉴 쓰기(`POST`/`PUT`/`DELETE`), `POST /api/upload/image` |

**CORS를 `WebConfig`에서 `SecurityConfig`의 `CorsConfigurationSource` 빈으로 옮긴다.** 두 가지 이유다 — (1) Security 필터체인은 `WebMvcConfigurer`의 CORS 설정보다 앞서 돌기 때문에 `.cors()` 연결 없이는 preflight가 401이 된다. (2) 지금 `WebConfig.addCorsMappings`의 `allowedMethods`에 **`PATCH`가 빠져 있어서**(`GET, POST, PUT, DELETE, OPTIONS`) 그대로 두면 주방 화면의 상태 변경이 preflight에서 막힌다. `/uploads/**` 정적 리소스 핸들러는 `WebConfig`에 그대로 남긴다.

**#4 — 테스트 복구**
손님 엔드포인트 테스트는 수정 없이 통과해야 한다. 점주 엔드포인트 테스트에만 `Authorization: Bearer` 헤더 또는 `@WithMockUser(roles = "OWNER")`를 붙인다.
**"안 되니까 전부 permitAll"로 도망가지 않는다.** 토큰 없이 점주 API를 호출하면 401이라는 것을 확인하는 테스트를 최소 2개 추가한다 — 그게 이 PR의 실제 산출물이다.

**#5 — 메뉴 API 정리**
[`PRODUCT.md §7`](../PRODUCT.md)의 목표를 여기서 소화한다.

- `PUT /api/menu/modify/{id}` → `PUT /api/menus/{id}`
- `DELETE /api/menu/delete/{menu_id}` → `DELETE /api/menus/{id}`
- `MenuService.modify`의 `menu.getEmail().equals(email)` 분기와 `deleteByIdAndEmail`, `DeleteMenuRequestDto`의 이메일 필드는 **폐기** — 인가는 필터가 한다
- raw `String` / raw `List<>` 응답을 `RsData<T>`로 통일

`frontend/CLAUDE.md`가 경고하듯 **BFF 핸들러(`api/menu/route.ts`, `api/menu/[menuId]/route.ts`)를 같이 고쳐야 한다.** 백엔드만 고치면 프론트가 조용히 깨진다.

**#6 — 대기번호 조회**
`GET /api/orders/{orderNumber}` 신설. 손님이 받은 대기번호로 자기 주문 상태만 확인한다. `OrderRepository.findByOrderNumber` 추가.
`POST /api/order/list`(이메일 기반)는 관리자 화면이 옮겨갈 때까지 유예하고 **Phase 4에서 폐기**한다.

**#7~#9 — 프론트 3분할**
`page.tsx`는 1230줄 단일 `"use client"` 컴포넌트다. `frontend/CLAUDE.md`대로 **전체를 다시 쓰지 않는다.** 손님 영역만 `/`에 남기고, 관리자 영역(메뉴 추가/수정/삭제 모달)을 `/admin`으로 잘라내는 방식으로 쪼갠다.
주방 화면은 **폴링 3초**. WebSocket은 Phase 3 이후 선택이다 — 동시성이 먼저다.
BFF 규약을 지킨다: 브라우저가 8080을 직접 부르지 않고, **토큰은 BFF가 붙인다.**

---

## 함정

- **Security를 추가하는 순간 기존 통합 테스트 23개가 한꺼번에 죽는다** — `MenuControllerTest` 3개 + `OrderControllerTest` 20개가 전부 MockMvc로 돈다. Swagger UI도 같이 막힌다. `#3`을 머지하고 `#4`를 나중에 하면 그 사이 CI가 빨갛다. **두 PR을 연달아 처리하거나 하나로 합친다.**
- **`spring-boot-starter-security`는 CSRF가 기본 ON이다.** 안 끄면 프론트의 모든 `POST`/`PUT`/`DELETE`/`PATCH`가 403이 된다. 껐다는 사실과 이유(stateless JSON API)를 `SecurityConfig`에 주석으로 남긴다.
- **`WebConfig`의 CORS `allowedMethods`에 `PATCH`가 없다.** 위 `#3` 메모 참고.
- **`jwt.secret`을 커밋하지 않는다.** `.env`로 빼고 `.env.example`엔 키 이름만 넣는다. HS256은 32byte 이상을 요구한다.
- **`Menu.email`은 남지만 더 이상 인가에 쓰이지 않는다.** 이 사실을 `Menu` 엔티티에 주석으로 남긴다 — 안 남기면 다음에 이 필드를 보는 사람이 다시 권한 판단에 쓴다.
- **`/api/upload/image`도 점주 전용이다.** 지금은 누구나 서버 디스크에 파일을 쓸 수 있다. 화면 분리에 묻혀 빠뜨리기 쉬운 엔드포인트다.
- **`dev` 프로필은 재시작마다 DB를 드롭한다**(`ddl-auto: create`). 시드 점주 계정도 매번 새로 생성된다 — 로컬에서 로그인이 안 되면 버그가 아니라 이것일 수 있다.

---

## 완료 기준

- [ ] 손님이 `/`에서 **로그인 없이** 주문하고, 대기번호가 화면에 크게 뜬다 (`alert` 아님)
- [ ] 그 주문이 3초 안에 `/kitchen`에 나타나고, 바리스타가 `제조중 → 준비완료`로 넘기면 손님 조회 화면의 상태가 바뀐다
- [ ] 토큰 없이 `GET /api/orders` · `PATCH /api/order/{id}/status` · `POST /api/menus` · `POST /api/upload/image` → **401**
- [ ] 레포 전체에서 "요청 본문 이메일로 권한을 확인"하는 코드가 사라졌다
- [ ] `./gradlew test` 통과 (401/403 검증 테스트 포함)
- [ ] 위 흐름이 **브라우저에서 실제로 돈다** — PR에 스크린샷 첨부

---

## 여기서 하지 않는 것

| 안 하는 것 | 언제 / 왜 |
| --- | --- |
| 재고 차감 | **Phase 2.** 지금은 재고가 0이어도 주문이 들어간다 — 알고 있는 상태로 넘어간다 |
| WebSocket 실시간 푸시 | 폴링 3초로 충분하다. Phase 3 이후 선택 |
| 컨트롤러 `@Transactional` 제거 | **Phase 2 PR #1.** 낙관적 락 재시도의 선행조건이지만 인증과 섞으면 PR이 커진다 |
| `getOrdersByStatus`의 N+1 | **Phase 4.** 주방 활성 주문은 소량이라 지금은 허용 |
| `localhost:8080` 하드코딩 | **Phase 4.** 새 BFF 핸들러도 일단 기존 패턴을 따른다 |
| 손님 회원가입 | 영구 스코프아웃 — [`PRODUCT.md §5`](../PRODUCT.md) |
