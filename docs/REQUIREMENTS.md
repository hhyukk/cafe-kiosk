# cafe-kiosk 요구사항명세서

> 이 문서는 **"무엇을 만족해야 완성인가"**를 담는다 — 검증 가능한 형태의 요구사항 목록과 인수 기준.
> **"왜 그렇게 정했는가"**는 [`docs/PRODUCT.md`](PRODUCT.md), **"언제·지금 어디"**는 [`docs/ROADMAP.md`](ROADMAP.md), **"어떻게 코드를 만지는가"**는 각 디렉토리의 `CLAUDE.md`에 있다.
>
> 기준: 현재 `main` 코드 / 최초 작성: 2026-07-21

---

## 1. 이 문서의 역할

이 레포의 문서는 세 개가 한 벌이다. **같은 내용을 두 곳에 적지 않는다** — 중복되면 둘 다 썩는다.

| 문서 | 답하는 질문 | 정본인 것 |
| --- | --- | --- |
| [`PRODUCT.md`](PRODUCT.md) | **왜** 이걸 만드나 | 정체성 진단, 스코프아웃의 이유, 설계 원칙의 근거 |
| **`REQUIREMENTS.md`** (이 문서) | **무엇을** 만족해야 하나 | 요구사항 ID, 인수 기준, 권한 매트릭스, 검증 책임 |
| [`USECASES.md`](USECASES.md) | **누가 어떤 순서로** 쓰나 | 액터별 주 흐름, 대안·예외 흐름, 사전·사후조건 |
| [`ROADMAP.md`](ROADMAP.md) + [`roadmap/phase-*.md`](roadmap/) | **언제** 하나 · 지금 어디 | 진행 상태, PR 단위, 함정, 작업 순서 |

**"어떻게 조립돼 있는가"는 [`design/architecture.md`](design/architecture.md)에 있다** — 런타임 구성, 계층, 요청 흐름, 데이터 모델 관계. 이 문서가 "무엇을 만족해야 하나"를 진술하면, 그쪽은 그 진술이 **어느 화살표에 걸리는지**를 그린다.

**진행 상태의 정본은 여전히 ROADMAP이다.** 이 문서의 ✅/🔶/⬜ 표기는 작성 시점의 **스냅샷**이며, 둘이 어긋나면 ROADMAP과 실제 코드를 따른다. 이 문서가 소유하는 것은 **"무엇을 만족해야 하는가"라는 진술 자체**이지 그 달성 여부가 아니다.

각 Phase 문서의 "완료 기준" 체크박스는 이 문서의 요구사항 ID로 흡수돼 있다 — 대응표는 [§11 추적표](#11-추적표)에 있다.

### 표기 규약

**ID 체계** — `FR-{도메인}-{번호}` (기능) / `NFR-{분류}-{번호}` (비기능)

| 도메인 | 범위 | | 분류 | 범위 |
| --- | --- | --- | --- | --- |
| `KSK` | 키오스크 · 손님 주문 흐름 | | `CON` | 동시성 |
| `ORD` | 주문 · 상태머신 · 금액 | | `SEC` | 보안 |
| `MNU` | 메뉴 | | `DATA` | 데이터 무결성 |
| `STK` | 재고 | | `OPS` | 운영 · 배포 |
| `AUTH` | 인증 · 인가 | | `TEST` | 품질 · 검증 |
| `KIT` | 주방(POS) 화면 | | `UX` | 사용성 |
| `ADM` | 관리자 화면 | | | |
| `FILE` | 이미지 업로드 | | | |

**번호는 재사용하지 않는다.** 폐기된 요구사항은 번호를 비우지 말고 "폐기" 행으로 남긴다 — 커밋 메시지와 PR이 ID로 이 문서를 참조하기 때문이다.

| 열 | 값 |
| --- | --- |
| **우선** | 필수 / 권장 / 선택 |
| **상태** | ✅ 구현됨 · 🔶 부분 구현 · ⬜ 미구현 |
| **Phase** | 이 요구사항을 충족시키는(또는 명문화하는) Phase |
| **근거** | 요구사항이 실제로 구현된 위치. **줄 번호는 적지 않는다** — 코드가 움직이면 즉시 거짓이 되므로 파일 경로 + 메서드명까지만 적는다. ✅ 표기에는 반드시 근거가 있어야 한다 |

백엔드 경로는 `Backend/App/src/main/java/com/cafekiosk/` 이하를, 프론트 경로는 `frontend/src/` 이하를 생략해 적는다.

---

## 2. 용어

| 용어 | 정의 |
| --- | --- |
| **대기번호** (`orderNumber`) | 손님이 받아가는 번호. 주문 PK에서 파생한 4자리 문자열. 카운터에서 이 번호로 음료를 내준다. 배송 추적번호가 아니다 |
| **가격 스냅샷** (`orderPrice`) | 주문이 성립한 시점의 메뉴 가격. 이후 메뉴 가격이 바뀌어도 과거 주문 금액은 변하지 않는다 |
| **재고** (`Stock.quantity`) | 메뉴별 현재 남은 수량. 이력 테이블은 두지 않는다 — 현재 수량만 다룬다 |
| **점주 / 바리스타** | 제품상으로는 다른 역할이지만, **인증상으로는 `ROLE_OWNER` 하나다** (§3) |
| **BFF** | 프론트의 `src/app/api/*` Route Handler. 브라우저와 백엔드(8080) 사이의 유일한 통로 |
| **CONFIRMED** | "결제까지 끝난 상태". 실결제는 스코프아웃이므로 결제 성공을 가정하고 이 상태로 주문이 생성된다 |
| **마지막 한 잔** | 이 프로젝트의 목표 시나리오 — 재고가 1 남은 메뉴를 두 손님이 동시에 주문했을 때 정확히 한 명만 성공하는 것 |

---

## 3. 액터와 시스템 경계

| 액터 | 인증 | 쓰는 화면 | 하는 일 |
| --- | --- | --- | --- |
| **손님** | **없음 (익명)** | `/` 키오스크 | 메뉴 조회, 주문, 대기번호로 자기 주문 상태 조회 |
| **바리스타** | `ROLE_OWNER` | `/kitchen` 주방 | 주문을 `제조중 → 준비완료 → 픽업완료`로 전이 |
| **점주** | `ROLE_OWNER` | `/admin` 관리자 | 메뉴 CRUD, 재고 조정, 이미지 업로드 |

**액터는 셋인데 역할(Role)은 둘이다.** 바리스타와 점주를 하나의 `ROLE_OWNER`로 묶는 것은 의도된 단순화다 — 1인 매장 기준이고, 역할을 쪼개면 이 레포의 주제(동시성)에서 멀어진다. 근거는 [`roadmap/phase-1.md` 소유권 모델](roadmap/phase-1.md).

**손님에게 로그인을 요구하는 것은 제품 실패로 간주한다.** 회원가입·마이페이지는 영구 스코프아웃이다(§10).

**외부 시스템은 없다.** PG(결제대행)를 연동하지 않으므로 시스템 경계 바깥과 주고받는 것이 없다.

---

## 4. 제약과 가정

| # | 제약 / 가정 |
| --- | --- |
| C-01 | **단일 매장 · 키오스크 1대**를 가정한다. 매장 다중화, 키오스크 다대는 요구사항이 아니다 |
| C-02 | 기술 스택은 고정이다 — Spring Boot 4.0 / Java 21 / PostgreSQL 16 / JPA, Next.js 16 App Router / React 19 / TypeScript 5 / Tailwind 4 |
| C-03 | **결제는 모킹한다.** 주문 생성이 곧 결제 완료(`CONFIRMED`)다 |
| C-04 | **브라우저는 백엔드(8080)를 직접 호출하지 않는다.** 모든 요청은 BFF Route Handler를 거친다 |
| C-05 | `dev` 프로필은 기동 시마다 스키마를 드롭·재생성하고(`ddl-auto: create`) `BaseInitData`가 메뉴 3개 + 재고 **100 / 50 / 3**을 시드한다. 로컬 데이터 유실은 버그가 아니다. Phase 4에서 Flyway로 바뀐다 |
| C-06 | 통합 테스트는 Testcontainers PostgreSQL 위에서 돈다. **테스트 실행에 Docker 데몬이 필요하다** |
| C-07 | 업로드 이미지는 로컬 디스크(`./uploads`)에 저장된다. 오브젝트 스토리지 이전 여부는 Phase 4에서 결론 낸다 |
| C-08 | 이 레포는 **학습 프로젝트**다. 처리량·가용성보다 **정확성과 설명 가능성**이 우선한다 |

---

## 5. 기능 요구사항

### 5-1. FR-KSK — 키오스크 (손님)

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-KSK-01 | 손님은 **로그인·회원가입 없이** 메뉴를 조회하고 주문할 수 있다 | 필수 | ✅ | 1 | `order/controller/OrderController.createOrder` |
| FR-KSK-02 | 주문 시 손님에게 요구하는 정보는 **이메일 하나뿐**이다. 주소·우편번호 등 배송 정보를 받지 않는다 | 필수 | ✅ | 0 | `order/dto/OrderDto.CreateRequest` |
| FR-KSK-03 | 주문이 성립하면 **대기번호와 결제 금액**을 반환한다 | 필수 | ✅ | 0 | `OrderDto.CreateResponse` |
| FR-KSK-04 | 대기번호는 **전용 완료 화면에 크게** 표시된다 (`alert` 아님) | 필수 | ⬜ | 1 | 미구현 — 현재 `app/page.tsx`의 `handleCheckout`이 `alert` |
| FR-KSK-05 | 손님 화면에 **메뉴 추가·수정·삭제 UI가 노출되지 않는다** | 필수 | ⬜ | 1 | 미구현 — `app/page.tsx`에 관리자 모달이 함께 있다 |
| FR-KSK-06 | 손님은 받은 **대기번호로 자기 주문의 현재 상태**를 조회할 수 있다 | 필수 | ⬜ | 1 | 미구현 — `GET /api/orders/{orderNumber}` 신설 예정 |
| FR-KSK-07 | 한 주문의 **총 수량은 1~100개**다. 벗어나면 400 | 필수 | ✅ | 0 | `OrderController.createOrder`, `app/api/order/route.ts` |
| FR-KSK-08 | **품절 메뉴는 품절로 표시**되고 장바구니에 담을 수 없다 | 필수 | ⬜ | 2 | 미구현 |
| FR-KSK-09 | 손님은 **남의 주문 내역을 조회할 수 없다** | 필수 | ⬜ | 1→4 | 미구현 — 현재 `POST /api/order/list`는 이메일만 알면 남의 주문이 보인다 |
| FR-KSK-10 | 주문 화면·완료 화면·조회 화면의 텍스트는 한국어다 | 권장 | ✅ | — | `app/page.tsx` |

### 5-2. FR-ORD — 주문 · 상태머신 · 금액

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-ORD-01 | 주문 상태는 `CONFIRMED → IN_PROGRESS → READY → COMPLETED` 순으로만 전이한다 | 필수 | ✅ | 0 | `order/entity/Order` (`startPreparing`/`markReady`/`complete`) |
| FR-ORD-02 | 주문은 `CONFIRMED` 또는 `IN_PROGRESS`에서만 `CANCELLED`로 갈 수 있다 | 필수 | ✅ | 0 | `Order.cancel` |
| FR-ORD-03 | **허용되지 않은 전이는 409로 거부되고 상태가 바뀌지 않는다** | 필수 | ✅ | 0 | `order/exception/InvalidOrderStatusTransitionException` → `global/globalExceptionHandler/GlobalExceptionHandler` |
| FR-ORD-04 | **상태 전이 규칙은 `Order` 엔티티가 소유한다.** 서비스·컨트롤러가 `status`를 직접 대입하지 않는다 | 필수 | ✅ | 0 | `Order`, `order/service/OrderService.changeStatus` |
| FR-ORD-05 | 주문 생성 시 각 아이템의 **주문 시점 가격을 스냅샷**한다 | 필수 | ✅ | 0 | `order/entity/OrderItem` 생성자 |
| FR-ORD-06 | **주문 총액은 아이템 스냅샷 소계의 합과 항상 일치한다.** 총액은 `Order.addOrderItem()`으로만 늘어난다 | 필수 | ✅ | 0 | `Order.addOrderItem`, `OrderItem.getSubtotal` |
| FR-ORD-07 | 대기번호는 **PK에서 파생**되어 전역 유일하고 단조 증가한다. "오늘 주문 수 + 1" 방식을 쓰지 않는다 | 필수 | ✅ | 0 | `Order.assignOrderNumber` |
| FR-ORD-08 | 주문 조회 응답은 `orderId`·`orderNumber`·`status`·`orderTime`·`totalPrice`·`items`를 포함한다 | 필수 | ✅ | 1 | `OrderDto.OrderSummary` |
| FR-ORD-09 | 주문 조회·목록의 금액은 **항상 스냅샷 가격**이며 현재 메뉴 가격을 읽지 않는다 | 필수 | ✅ | 0 | `OrderService.toItemDTO` |
| FR-ORD-10 | 존재하지 않는 메뉴 ID로 주문하면 400 | 필수 | ✅ | 0 | `OrderService.createOrder` → `GlobalExceptionHandler` |
| FR-ORD-11 | 주문이 취소되면 **차감된 재고가 복구된다** | 필수 | ⬜ | 2 | 미구현 — FR-STK-05와 한 몸 |
| FR-ORD-12 | 주문 생성은 **원자적**이다 — 아이템 중 하나라도 실패하면 주문 전체가 성립하지 않는다 | 필수 | 🔶 | 2 | 트랜잭션은 있으나(`OrderService.createOrder`) 실패 경로가 재고 연결 전까지는 사실상 없다 |

> **설계 소유권** — 상태 전이와 금액 계산은 **엔티티가 소유한다.** 서비스는 엔티티를 조회해 메서드를 호출할 뿐이다. 총액이 아이템과 어긋날 수 있는 **경로 자체를 없애는 것**이 목적이다. (`Backend/App/CLAUDE.md`)

### 5-3. FR-MNU — 메뉴

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-MNU-01 | 누구나(익명 포함) 메뉴 목록을 조회할 수 있다 | 필수 | ✅ | 1 | `menu/controller/MenuController.getMenus` |
| FR-MNU-02 | **점주만** 메뉴를 등록·수정·삭제할 수 있다 | 필수 | 🔶 | 1 | 요청 본문 이메일 비교뿐 — `menu/service/MenuService.modify`, `MenuService.deleteMenu` |
| FR-MNU-03 | 메뉴 가격은 **0원 이상 10,000,000원 이하**다. 벗어나면 400 | 필수 | ✅ | 0 | `MenuController.createMenu`, `app/api/menu/route.ts` |
| FR-MNU-04 | 메뉴 등록·수정 시 이름·카테고리·가격은 필수이며 서버가 검증한다 | 필수 | ✅ | 0 | `menu/dto/CreateMenuRequestDto`, `MenuDto.MenuModifyRequest` + `@Valid` |
| FR-MNU-05 | 존재하지 않는 메뉴를 수정·삭제하면 **404**다 | 필수 | 🔶 | 1 | `MenuController.modifyMenu`가 `Optional.get()`이라 `NoSuchElementException`이 우연히 404로 매핑된다. **의도한 처리로 바꿔야 한다** |
| FR-MNU-06 | **메뉴 가격을 바꿔도 과거 주문 금액은 변하지 않는다** | 필수 | ✅ | 0 | FR-ORD-05/09가 보장. 회귀 테스트 `order/controller/OrderControllerTest` |
| FR-MNU-07 | 메뉴 API 경로는 REST 스타일(동사 없음)이고 응답은 `RsData<T>`로 통일된다 | 권장 | ⬜ | 1 | 미구현 — 현재 `/modify/{id}`, `/delete/{menu_id}` + raw `String` 응답 |
| FR-MNU-08 | `Menu.email`은 **"누가 등록했는가"의 기록**일 뿐이며 권한 판단에 쓰이지 않는다 | 필수 | ⬜ | 1 | 미구현 — 현재 인가에 쓰이고 있다 |

### 5-4. FR-STK — 재고

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-STK-01 | 메뉴마다 재고가 **1:1**로 존재한다 | 필수 | ✅ | — | `stock/entity/Stock` |
| FR-STK-02 | **주문 생성 시 주문 수량만큼 재고를 차감한다** | 필수 | ⬜ | 2 | 미구현 — `OrderService`가 `Stock`을 참조하지 않는다 |
| FR-STK-03 | 재고가 부족하면 **주문 전체가 실패하고 부분 차감이 남지 않는다.** 409로 응답한다 | 필수 | ⬜ | 2 | 미구현 |
| FR-STK-04 | **재고 증감 규칙은 `Stock` 엔티티가 소유한다** — `decrease`/`increase`가 스스로 부족을 판단해 던진다. 서비스가 `if (quantity < count)`를 검사하지 않는다 | 필수 | ⬜ | 2 | 미구현 — `Stock`에 증감 메서드 자체가 없다 |
| FR-STK-05 | 주문이 취소되면 그 주문의 수량만큼 재고가 복구된다. **`CANCELLED` 전이가 성공했을 때만** 복구한다 | 필수 | ⬜ | 2 | 미구현 |
| FR-STK-06 | **재고는 어떤 경로로도 음수가 되지 않는다** — 동시 요청에서도 마찬가지다 | 필수 | ⬜ | 2→3 | 미구현. 동시성 보장은 NFR-CON-01 |
| FR-STK-07 | 메뉴 조회 응답에 **재고 수량과 품절 여부**가 포함된다 | 필수 | ⬜ | 2 | 미구현 — `MenuDto.MenuListResponse`에 재고 정보가 없다 |
| FR-STK-08 | 점주는 재고를 조정(보충)할 수 있다 | 필수 | ⬜ | 2 | 미구현 — `stock/`에 service·controller가 없다 |
| FR-STK-09 | 재고 **이력**은 관리하지 않는다. 현재 수량만 다룬다 | — | — | — | 의도적 비요구사항 |

### 5-5. FR-AUTH — 인증 · 인가

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-AUTH-01 | 점주는 이메일·비밀번호로 로그인해 **Access 토큰**을 받는다 | 필수 | ⬜ | 1 | 미구현 — 설계 정본 [`design/jwt-auth.md`](design/jwt-auth.md) |
| FR-AUTH-02 | **비밀번호는 해시로만 저장한다.** 시드 계정도 평문 금지 | 필수 | ⬜ | 1 | 미구현 — `Owner` 엔티티 자체가 없다 |
| FR-AUTH-03 | 인증은 **stateless JWT(HS256)**이며 서버 세션을 두지 않는다 | 필수 | ⬜ | 1 | 미구현 |
| FR-AUTH-04 | **점주 전용 API를 토큰 없이 호출하면 401**이다 | 필수 | ⬜ | 1 | 미구현 — 현재 `PATCH /api/order/{id}/status`, `POST /api/upload/image`는 완전 공개 |
| FR-AUTH-05 | **인가를 요청 본문의 이메일 문자열 비교로 하지 않는다** | 필수 | ⬜ | 1 | 미구현 — 현재 `MenuService`가 그렇게 한다 |
| FR-AUTH-06 | **손님 엔드포인트는 인증을 요구하지 않는다** — 인증 도입이 손님 흐름을 막으면 안 된다 | 필수 | ⬜ | 1 | 미구현 |
| FR-AUTH-07 | 토큰 클레임에 **민감정보(비밀번호 등)를 담지 않는다.** 식별자·역할만 담는다 | 필수 | ⬜ | 1 | 미구현 — 근거 [`design/jwt-auth.md`](design/jwt-auth.md) |
| FR-AUTH-08 | 바리스타와 점주는 **`ROLE_OWNER` 하나**로 인증한다 | 필수 | ⬜ | 1 | 미구현 — §3 |
| FR-AUTH-09 | **토큰은 BFF가 붙인다.** 브라우저가 백엔드에 직접 토큰을 보내지 않는다 | 필수 | ⬜ | 1 | 미구현 — C-04의 연장 |
| FR-AUTH-10 | 토큰 만료 후에는 재로그인이 필요하다. Refresh 토큰은 두지 않는다 | 권장 | ⬜ | 1 | 의도적 단순화 — [`design/jwt-auth.md` §10](design/jwt-auth.md) |

### 5-6. FR-KIT — 주방(POS) 화면

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-KIT-01 | 바리스타는 `/kitchen`에서 **들어온 주문 목록**을 본다 | 필수 | ⬜ | 1 | 미구현 — 화면 없음 |
| FR-KIT-02 | 주문 목록은 **주문 시각 오름차순(FIFO)**이다 — 먼저 들어온 주문을 먼저 만든다 | 필수 | ✅ | 1 | `OrderService.getOrdersByStatus`, `OrderRepository.findAllByOrderByOrderTimeAsc` |
| FR-KIT-03 | 목록을 상태로 필터링할 수 있고, **결과가 비어도 200 + 빈 배열**이다 | 필수 | ✅ | 1 | `OrderController.getOrders` |
| FR-KIT-04 | 바리스타는 주문을 `제조중 → 준비완료 → 픽업완료`로 전이시킨다 | 필수 | 🔶 | 1 | API만 존재 — `OrderController.changeStatus`. 화면 없음 |
| FR-KIT-05 | 새 주문이 **3초 이내**에 주방 화면에 나타난다 (폴링) | 필수 | ⬜ | 1 | 미구현. WebSocket은 비요구사항(§10) |
| FR-KIT-06 | 주방 화면과 그 API는 **인증된 점주만** 접근할 수 있다 | 필수 | ⬜ | 1 | 미구현 — FR-AUTH-04의 적용 |

### 5-7. FR-ADM — 관리자 화면

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-ADM-01 | 점주는 `/admin`에서 로그인 후 메뉴를 등록·수정·삭제한다 | 필수 | ⬜ | 1 | 미구현 |
| FR-ADM-02 | 점주는 `/admin`에서 재고를 조정한다 | 필수 | ⬜ | 2 | 미구현 |
| FR-ADM-03 | **관리자 기능은 손님 화면에 노출되지 않는다** | 필수 | ⬜ | 1 | 미구현 — FR-KSK-05와 한 몸 |
| FR-ADM-04 | 권한 확인에 `window.prompt`로 이메일을 묻는 방식을 쓰지 않는다 | 필수 | ⬜ | 1 | 미구현 — 현재 `app/page.tsx`가 그렇게 한다 |

### 5-8. FR-FILE — 이미지 업로드

| ID | 요구사항 | 우선 | 상태 | Phase | 근거 |
| --- | --- | --- | --- | --- | --- |
| FR-FILE-01 | 점주는 메뉴 이미지를 업로드하고 그 URL을 메뉴에 붙일 수 있다 | 필수 | ✅ | — | `file/controller/FileUploadController` |
| FR-FILE-02 | **이미지 파일만**, **5MB 이하**만 허용한다. 위반 시 400 | 필수 | ✅ | — | `FileUploadController.uploadImage` |
| FR-FILE-03 | 저장 파일명은 UUID로 생성해 충돌·덮어쓰기를 막는다 | 필수 | ✅ | — | `FileUploadController.uploadImage` |
| FR-FILE-04 | **업로드는 점주 전용이다** | 필수 | ⬜ | 1 | 미구현 — 현재 누구나 서버 디스크에 파일을 쓸 수 있다 |
| FR-FILE-05 | 업로드 요청도 **BFF를 거친다** | 필수 | ⬜ | 4 | 미구현 — 현재 브라우저가 8080을 직접 호출한다(C-04 위반) |
| FR-FILE-06 | 커밋된 시드 이미지(클래스패스)와 런타임 업로드 파일(파일시스템)이 **모두 `/uploads/**`로 서빙**된다 | 필수 | ✅ | — | `global/config/WebConfig.addResourceHandlers` |
| FR-FILE-07 | 저장되는 이미지 URL은 **호스트를 포함하지 않는다.** `/uploads/{파일명}` 형태의 상대경로이며, DB에 배포 환경 주소가 박히지 않는다 | 필수 | ✅ | 1 | `FileUploadController.uploadImage`, `global/initData/BaseInitData.work1` |

> **FR-FILE-07은 코드가 아니라 데이터에 박히는 결함이라 따로 세웠다.** `page.tsx`의 `uploadImage`는 백엔드가 돌려준 URL을 그대로 메뉴 등록에 실어 보내므로, 그 값이 **`Menu.imgUrl` 컬럼에 저장된다.** NFR-OPS-02는 코드의 하드코딩만 세기 때문에 그것을 0곳으로 만들어도 이미 저장된 행은 손대지 못한다 — 통과하고도 배포하면 이미지가 깨진다.
>
> **해결하면서 브라우저가 `img` 태그로 8080을 직접 때리던 경로도 함께 사라졌다.** 상대경로가 되는 순간 `next.config.ts`의 `/uploads/:path*` rewrite가 그 요청을 받아 백엔드로 넘긴다. 그전까지 이 rewrite는 절대 URL만 오가는 탓에 **한 번도 타지 않는 죽은 설정**이었다. 구조는 [`design/architecture.md §3-1`](design/architecture.md#3-1-브라우저--백엔드-경로가-셋이다).
>
> Phase 4가 아니라 **Phase 1에서 처리했다.** 애초에 Phase 4로 잡았던 이유는 기존 행을 고치려면 마이그레이션이 필요하고 그 수단이 Flyway라서였는데, `dev`는 `ddl-auto: create`라 기동마다 시드를 다시 심고 **배포된 DB가 아직 없다.** 고칠 데이터가 없는 지금이 가장 싸다. 나중에 하면 마이그레이션을 한 번 더 써야 한다.

---

## 6. 인수 기준

표의 진술만으로 검증 방법이 모호한 **핵심 요구사항 14개**만 Given/When/Then으로 상세화한다. 여기 없는 요구사항은 표의 진술로 충분하다.

### AC-01 · 가격 스냅샷 회귀 (FR-ORD-05, FR-ORD-09, FR-MNU-06)
```
Given  15,000원짜리 메뉴를 1개 주문했다 (주문 금액 15,000원)
When   점주가 그 메뉴 가격을 99,000원으로 수정한다
Then   과거 주문의 조회 금액은 여전히 15,000원이다
And    새로 담는 주문에는 99,000원이 적용된다
```
> 이 레포의 **회귀 방지선**이다. `getOrderList`가 `orderItem.getOrderPrice()` 대신 `getMenu().getMenuPrice()`를 읽는 순간 깨진다.

### AC-02 · 총액 일치 (FR-ORD-06)
```
Given  임의의 주문
When   주문을 조회한다
Then   totalPrice == Σ(items[i].orderPrice × items[i].count) 이다
```

### AC-03 · 상태 전이 위반 (FR-ORD-03)
```
Given  상태가 CONFIRMED 인 주문
When   PATCH /api/order/{id}/status 로 READY 를 요청한다   (IN_PROGRESS 를 건너뛴다)
Then   409 로 거부되고
And    주문 상태는 여전히 CONFIRMED 다
```

### AC-04 · 대기번호 유일성 (FR-ORD-07)
```
Given  주문이 N건 생성됐다
When   모든 주문의 orderNumber 를 모은다
Then   중복이 없고, 생성 순서대로 단조 증가한다
```

### AC-05 · 주문 수량 경계 (FR-KSK-07)
```
Given  아무 메뉴
When   총 수량 0개 또는 101개로 주문한다
Then   400 이고 주문이 생성되지 않는다
When   총 수량 1개 또는 100개로 주문한다
Then   주문이 생성된다
```

### AC-06 · 재고 부족 시 전체 실패 (FR-STK-03, FR-ORD-12)
```
Given  재고가 3개인 메뉴가 있고, 재고가 넉넉한 메뉴 A·B 가 있다
When   [A 1개, B 1개, 재고3짜리 4개] 를 한 주문으로 요청한다
Then   409 로 거부되고
And    A 와 B 의 재고도 차감되지 않았다   ← 부분 차감 금지
And    재고3짜리 메뉴의 재고는 여전히 3이다
```

### AC-07 · 취소 시 재고 복구 (FR-STK-05, FR-ORD-11)
```
Given  재고 3인 메뉴를 3개 주문해 재고가 0이 됐다
When   그 주문을 CANCELLED 로 전이시킨다
Then   전이가 성공하고
And    재고가 3으로 복구된다
```

### AC-08 · 완료된 주문은 취소도 복구도 없다 (FR-STK-05, FR-ORD-02)
```
Given  상태가 COMPLETED 인 주문
When   CANCELLED 로 전이를 시도한다
Then   409 이고
And    재고가 복구되지 않는다              ← 이중 복구 금지
```

### AC-09 · ★ 마지막 한 잔 (NFR-CON-01)
```
Given  재고가 정확히 3개인 메뉴
When   서로 다른 10개 스레드가 CountDownLatch 로 동시에 1개씩 주문한다
Then   정확히 3건이 성공하고
And    정확히 7건이 409 로 거부되고
And    최종 재고가 정확히 0이다              (음수도, 1 이상도 아니다)
```
> **이 프로젝트의 목적지다.** 비관적 락 · 낙관적 락 · Redisson 분산 락 **세 전략 각각에서** 통과해야 한다(NFR-CON-03). 그리고 **락이 없던 버전에서 이 테스트가 실패했다는 사실이 커밋 히스토리에 남아 있어야 한다**(NFR-CON-02) — 해결책부터 배우지 않는 것이 이 Phase의 학습 설계다.

### AC-10 · 데드락 회피 (NFR-CON-04)
```
Given  메뉴 X 와 메뉴 Y, 둘 다 재고가 넉넉하다
When   주문 A 는 [X, Y] 순으로, 주문 B 는 [Y, X] 순으로 동시에 들어온다
Then   교착 없이 둘 다 완료된다 (락은 menuId 오름차순으로 잡힌다)
```

### AC-11 · 동시 첫 주문의 고객 생성 경쟁 (NFR-CON-06)
```
Given  DB 에 등록된 적 없는 이메일
When   그 이메일로 동시에 10건을 주문한다
Then   Customer 유니크 제약 위반(500)이 발생하지 않고
And    재고 로직까지 정상적으로 도달한다
```
> 재고를 보러 갔다가 엉뚱한 곳에서 막히는 함정이다. 어떻게 해결했는지(별도 트랜잭션 / upsert / 테스트 회피)와 **고른 이유**를 문서에 남긴다.

### AC-12 · 인증 경계 (FR-AUTH-04, FR-AUTH-06)
```
Given  토큰이 없다
When   GET /api/orders · PATCH /api/order/{id}/status · POST /api/menus · POST /api/upload/image 를 호출한다
Then   전부 401 이다
When   POST /api/order · GET /api/menu · GET /api/orders/{orderNumber} 를 호출한다
Then   정상 동작한다                          ← 손님 흐름은 인증 도입에 영향받지 않는다
```
> **"안 되니까 전부 permitAll"로 도망가지 않는다.** 401을 확인하는 테스트가 이 요구사항의 실제 산출물이다.

### AC-13 · 손님은 자기 주문만 본다 (FR-KSK-06, FR-KSK-09)
```
Given  손님 A 의 대기번호와 손님 B 의 대기번호
When   손님 A 가 자기 대기번호로 조회한다
Then   자기 주문의 상태·금액·아이템이 보인다
When   요청 본문에 남의 이메일을 넣어 주문 내역을 조회하는 경로를 찾는다
Then   그런 경로가 존재하지 않는다
```

### AC-14 · 끝에서 끝까지 (FR-KSK-04, FR-KIT-01, FR-KIT-05)
```
Given  백엔드와 프론트가 떠 있다
When   손님이 / 에서 로그인 없이 주문한다
Then   대기번호가 화면에 크게 뜬다 (alert 아님)
And    3초 안에 그 주문이 /kitchen 에 나타난다
When   바리스타가 제조중 → 준비완료로 넘긴다
Then   손님의 주문 조회 화면 상태가 바뀐다
```
> **브라우저에서 실제로 돌아가는 것**까지가 인수 조건이다. CI가 프론트를 검증하지 않으므로(NFR-TEST-01) 수동 확인 + PR 스크린샷이 유일한 증거다.

---

## 7. 비기능 요구사항

### 7-1. NFR-CON — 동시성 ★

| ID | 요구사항 | 우선 | 상태 | Phase |
| --- | --- | --- | --- | --- |
| NFR-CON-01 | 재고 N인 메뉴에 동시 주문 M건(M > N)이 들어오면 **정확히 N건 성공, M−N건 409, 최종 재고 0**이다 | 필수 | ⬜ | 3 |
| NFR-CON-02 | 락이 없던 버전에서 위 테스트가 **실패한다는 사실이 커밋 히스토리에 남는다** | 필수 | ⬜ | 3 |
| NFR-CON-03 | **비관적 락 · 낙관적 락 · Redisson 분산 락 세 전략 각각**에서 NFR-CON-01을 만족한다 | 필수 | ⬜ | 3 |
| NFR-CON-04 | 다중 메뉴 주문이 서로 반대 순서로 들어와도 **데드락이 발생하지 않는다** (`menuId` 오름차순 락) | 필수 | ⬜ | 3 |
| NFR-CON-05 | 낙관적 락 재시도는 **롤백된 트랜잭션 밖, 새 트랜잭션에서** 이뤄진다. 이를 위해 **컨트롤러에 `@Transactional`이 없어야 한다** | 필수 | ⬜ | 2→3 |
| NFR-CON-06 | 같은 이메일의 동시 첫 주문에서 `Customer` 유니크 제약 위반이 발생하지 않는다 | 필수 | ⬜ | 3 |
| NFR-CON-07 | 세 전략의 트레이드오프가 **실측 수치와 함께** 문서화된다 — "경쟁이 잦으면 비관적, 드물면 낙관적"을 말이 아니라 숫자로 | 필수 | ⬜ | 3 |

> **여기서 목표는 정확성이지 처리량이 아니다.** 부하 테스트 도구(k6, Gatling)는 요구사항이 아니다(§10).

### 7-2. NFR-SEC — 보안

| ID | 요구사항 | 우선 | 상태 | Phase |
| --- | --- | --- | --- | --- |
| NFR-SEC-01 | **시크릿을 커밋하지 않는다** — `jwt.secret`, `DB_PASSWORD`는 `.env`/배포 시크릿으로만 주입하고 `.env.example`에는 키 이름만 둔다 | 필수 | 🔶 | 1 |
| NFR-SEC-02 | **개인정보가 로그에 남지 않는다** — 바인딩 파라미터 TRACE 로깅은 프로덕션에서 끈다 (현재 손님 이메일이 로그에 찍힌다) | 필수 | ⬜ | 4 |
| NFR-SEC-03 | CORS 허용 출처는 명시적이며 배포 도메인은 환경변수로 받는다 (현재 `localhost:3000` 하드코딩) | 필수 | 🔶 | 4 |
| NFR-SEC-04 | CSRF는 stateless JSON API이므로 끄되, **끈 이유를 `SecurityConfig`에 주석으로 남긴다** | 필수 | ⬜ | 1 |
| NFR-SEC-05 | HS256 서명 키는 **32byte 이상**이어야 한다 | 필수 | ⬜ | 1 |
| NFR-SEC-06 | 인가 판단은 **서버가 검증한 신원**에 근거한다. 클라이언트가 보낸 식별자를 신뢰하지 않는다 | 필수 | ⬜ | 1 |

### 7-3. NFR-DATA — 데이터 무결성

| ID | 요구사항 | 우선 | 상태 | Phase |
| --- | --- | --- | --- | --- |
| NFR-DATA-01 | **불변식은 엔티티가 소유한다.** 상태 전이(`Order`), 금액(`Order`/`OrderItem`), 재고(`Stock`) 모두 — 서비스가 규칙을 검사하지 않는다 | 필수 | 🔶 | 2 |
| NFR-DATA-02 | 주문 생성과 재고 차감은 **한 트랜잭션**이다 (all-or-nothing) | 필수 | ⬜ | 2 |
| NFR-DATA-03 | 대기번호는 유일하다 (`@Column(unique = true)` + PK 파생) | 필수 | ✅ | 0 |
| NFR-DATA-04 | 재고는 음수가 될 수 없다 — 단일 스레드에서는 Phase 2가, 동시 요청에서는 Phase 3이 보장한다 | 필수 | ⬜ | 2→3 |

### 7-4. NFR-OPS — 운영 · 배포

| ID | 요구사항 | 우선 | 상태 | Phase |
| --- | --- | --- | --- | --- |
| NFR-OPS-01 | 스키마는 **Flyway 마이그레이션**으로 관리되고 `ddl-auto`는 폐기된다 | 필수 | ⬜ | 4 |
| NFR-OPS-02 | 레포 전체에 `localhost:8080` 하드코딩이 **0곳**이다 (현재 9곳, 전부 프론트) | 필수 | ⬜ | 4 |
| NFR-OPS-03 | 프로필이 `dev` / `test` / `prod`로 분리되고, 활성 프로필은 환경변수로 정해진다 | 필수 | 🔶 | 4 |
| NFR-OPS-04 | 배포된 환경에서 AC-14(끝에서 끝까지)가 그대로 동작한다 | 필수 | ⬜ | 4 |
| NFR-OPS-05 | 업로드 파일의 영속성 전략(로컬 디스크 유지 vs 오브젝트 스토리지)을 **결정하고 문서에 남긴다** | 권장 | ⬜ | 4 |

### 7-5. NFR-TEST — 품질 · 검증

| ID | 요구사항 | 우선 | 상태 | Phase |
| --- | --- | --- | --- | --- |
| NFR-TEST-01 | CI가 백엔드 테스트를 돌린다. **프론트 lint·build도 CI에 포함된다** | 필수 | 🔶 | 4 |
| NFR-TEST-02 | 통합 테스트는 `support/AbstractIntegrationTest`를 상속한다. `@Testcontainers`/`@Container`를 개별 테스트에 붙이지 않는다 | 필수 | ✅ | — |
| NFR-TEST-03 | **동시성 테스트에 `@Transactional`을 붙이지 않는다.** `ExecutorService`로 서비스를 직접 호출하고, 뒷정리는 `@AfterEach`가 한다 | 필수 | ⬜ | 3 |
| NFR-TEST-04 | 동시성 테스트는 **커넥션 풀 크기를 명시**하고 스레드 수와의 관계를 주석으로 남긴다 — 풀 고갈을 락 문제로 오해하지 않기 위해 | 필수 | ⬜ | 3 |
| NFR-TEST-05 | 기능을 바꾸면 단위/통합 테스트를 함께 추가한다 (PR 템플릿 요구사항) | 필수 | ✅ | — |
| NFR-TEST-06 | 순수 로직(상태 전이, 재고 증감)은 Spring 컨텍스트 없이 POJO 단위 테스트로 검증한다 | 권장 | 🔶 | 2 |

### 7-6. NFR-UX

| ID | 요구사항 | 우선 | 상태 | Phase |
| --- | --- | --- | --- | --- |
| NFR-UX-01 | 주방 화면은 **폴링 3초**로 갱신한다. WebSocket은 쓰지 않는다 | 필수 | ⬜ | 1 |
| NFR-UX-02 | UI 텍스트·오류 메시지는 한국어다 | 권장 | ✅ | — |
| NFR-UX-03 | BFF는 백엔드 오류 응답의 `message`/`msg`/`error` 중 무엇이 오든 **`{ message }` 형태로 정규화**해 내려준다 | 권장 | ✅ | — |

---

## 8. 데이터 요구사항

목표 상태(Phase 0~3 완료 시점)의 도메인 모델. **핵심 열은 "소유자"다** — 이 값을 누가 바꿀 수 있는가.

| 엔티티 | 필드 | 불변식 | **소유자 (변경 권한)** | 상태 |
| --- | --- | --- | --- | --- |
| **Order** | `orderNumber` | 유일, PK 파생, 한 번만 발급 | `Order.assignOrderNumber()` — INSERT 이후 1회 | ✅ |
| | `totalPrice` | 아이템 스냅샷 소계의 합과 항상 일치 | `Order.addOrderItem()` **만** | ✅ |
| | `status` | 정해진 전이만 허용 | `Order`의 전이 메서드 **만** | ✅ |
| | `orderTime` | 생성 시 고정 | 생성자 | ✅ |
| | ~~`address`, `postcode`~~ | — | **제거됨** (배송몰 잔재, Phase 0) | ✅ |
| **OrderItem** | `orderPrice` | 주문 시점 메뉴 가격, 이후 불변 | `OrderItem` 생성자가 메뉴에서 복사 | ✅ |
| | `count` | 생성 시 고정 | 생성자 | ✅ |
| **Customer** | `email` | 유일 | 생성자. **회원이 아니라 익명 주문 주체**다 | ✅ |
| **Menu** | `menuName`,`menuPrice`,`imgUrl`,`category` | 가격 0~10,000,000 | `Menu.modify()` (점주만 호출 가능) | ✅ |
| | `email` | — | **등록자 기록일 뿐. 권한 판단에 쓰지 않는다** | ⬜ (FR-MNU-08) |
| **Stock** | `quantity` | **음수가 될 수 없다** | `Stock.decrease()` / `Stock.increase()` **만** | ⬜ |
| | `version` | 낙관적 락용 | JPA (`@Version`) | ⬜ |
| **Owner** | `email` | 유일 | 생성자 | ⬜ |
| | `passwordHash` | **평문 저장 금지** | `BCryptPasswordEncoder`를 거친 값만 | ⬜ |

**시드 데이터(dev 전용)** — 메뉴 3개와 재고 **100 / 50 / 3**. 마지막 값이 3인 것은 우연이 아니라 **AC-09(마지막 한 잔)의 재현 조건**이다. Owner 시드가 추가될 때는 `ownerRepository.count()`로 **따로 가드한다** — 가드가 세는 대상과 심는 대상이 어긋나면 매 기동 재실행되는 사고가 Phase 0에서 이미 한 번 있었다.

---

## 9. 인터페이스 요구사항

### 9-1. API — 현재 → 목표

| 현재 | 목표 | 인증 | 응답 | 비고 |
| --- | --- | --- | --- | --- |
| `POST /api/order` | 유지 | 익명 | `RsData<T>` | 대기번호 반환 |
| `POST /api/order/list` (본문 이메일) | **폐기** | — | — | 조회인데 POST, 남의 이메일이면 남의 주문이 보인다 (Phase 4) |
| — | `GET /api/orders/{orderNumber}` | 익명 | `RsData<T>` | 신설 — 손님이 자기 주문만 조회 (Phase 1) |
| `GET /api/orders?status=` | 유지 | **OWNER** | `RsData<T>` ✅ | FIFO 정렬, 빈 결과도 200 |
| `PATCH /api/order/{orderId}/status` | 유지 | **OWNER** | `RsData<T>` | 잘못된 전이는 409 |
| `GET /api/menu` | `GET /api/menus` | 익명 | `RsData<T>` | 재고·품절 여부 추가 (Phase 2) |
| `POST /api/menu` | `POST /api/menus` | **OWNER** | `RsData<T>` | 현재 raw `String` 응답 |
| `PUT /api/menu/modify/{id}` | `PUT /api/menus/{id}` | **OWNER** | `RsData<T>` | 동사형 경로 폐기 |
| `DELETE /api/menu/delete/{menu_id}` | `DELETE /api/menus/{id}` | **OWNER** | `RsData<T>` | 본문 이메일 필드 폐기 |
| — | `POST /api/auth/login` | 익명 | `RsData<T>` | 신설 (Phase 1) |
| — | 재고 조정 API | **OWNER** | `RsData<T>` | 신설 (Phase 2) |
| `POST /api/upload/image` | 유지 | **OWNER** | — | 현재 완전 공개 |

> **응답 포맷은 `RsData<T>`로 통일한다.** 현재는 `RsData` / raw `String` / raw record 3종이 혼재한다. 신규 API는 예외 없이 `RsData<T>`다.
> **예외 → HTTP 매핑은 `GlobalExceptionHandler`가 한다.** 컨트롤러에서 try/catch로 상태코드를 만들지 않는다.

### 9-2. 권한 매트릭스

| 엔드포인트 | 익명(손님) | `ROLE_OWNER` |
| --- | :---: | :---: |
| `POST /api/order` | ✅ | ✅ |
| `GET /api/orders/{orderNumber}` | ✅ | ✅ |
| `GET /api/menus` | ✅ | ✅ |
| `POST /api/auth/login` | ✅ | ✅ |
| `/uploads/**`, `/swagger-ui/**`, `/v3/api-docs/**` | ✅ | ✅ |
| `GET /api/orders` (전체 목록) | **401** | ✅ |
| `PATCH /api/order/{id}/status` | **401** | ✅ |
| `POST` / `PUT` / `DELETE /api/menus**` | **401** | ✅ |
| 재고 조정 | **401** | ✅ |
| `POST /api/upload/image` | **401** | ✅ |

**CORS는 `PATCH`를 반드시 허용해야 한다.** 현재 `WebConfig`의 `allowedMethods`에 `PATCH`가 빠져 있어, 그대로 두면 주방 화면의 상태 변경이 preflight에서 막힌다.

### 9-3. 검증 책임 — 어느 쪽이 정본인가

같은 규칙이 백엔드와 BFF 양쪽에 구현돼 있다. **백엔드가 정본이고, BFF는 UX용 조기 차단이다.** 둘이 어긋나면 백엔드를 따르며, 규칙을 바꿀 때는 **백엔드를 먼저 고치고 BFF를 맞춘다.**

| 규칙 | 정본 (백엔드) | 조기 차단 (BFF/클라이언트) |
| --- | --- | --- |
| 주문 총 수량 1~100 | `OrderController.createOrder` | `app/api/order/route.ts` |
| 메뉴 가격 0~10,000,000 | `MenuController.createMenu` | `app/api/menu/route.ts` |
| 업로드 이미지만 · 5MB 이하 | `FileUploadController.uploadImage` | `app/page.tsx` (파일 선택 시) |
| 이메일 형식 | `@Email` (DTO) | `app/page.tsx`의 정규식 |

> **BFF 검증을 지우면 UX가 나빠질 뿐 보안이 뚫리지는 않는다. 백엔드 검증을 지우면 뚫린다.** 이 방향을 헷갈리지 않는다.

### 9-4. BFF 규약

- 브라우저는 백엔드(8080)를 **직접 호출하지 않는다** (C-04). 현재 이미지 업로드 2곳이 이 규약을 어기고 있다
- **토큰은 BFF가 붙인다** (FR-AUTH-09)
- 백엔드 API를 바꾸면 **BFF 핸들러도 같이 고친다.** 백엔드만 고치면 프론트가 조용히 깨진다
- 필드명 변환 규칙은 **핸들러마다 다르다** (`api/menu`는 snake_case→camelCase, `api/order`는 그대로 통과). 고치기 전에 해당 파일을 읽는다

---

## 10. 비요구사항 (스코프 아웃)

**안 하는 이유를 남기는 것도 명세다.** 상세 근거는 [`PRODUCT.md §5`](PRODUCT.md).

| 안 하는 것 | 한 줄 이유 | 영구/유예 |
| --- | --- | --- |
| 실결제(PG) 연동 | 주제는 동시성이지 결제가 아니다. `CONFIRMED`를 "결제까지 끝난 상태"로 정의하고 모킹 | **영구** |
| 손님 회원가입 · 마이페이지 | 매장 키오스크에 회원 개념은 제품적으로 틀렸다. 손님은 익명 | **영구** |
| 배송 · 주소 · 우편번호 | 이 레포는 배송 쇼핑몰이 아니다. Phase 0에서 제거했다 | **영구** |
| 다국어(i18n) | 학습 주제와 무관 | **영구** |
| WebSocket 실시간 푸시 | 주방은 폴링 3초로 충분하다 | 유예 (Phase 3 이후 선택) |
| 부하 테스트 도구(k6, Gatling) | Phase 3에서 필요한 것은 **정확성**이지 처리량이 아니다 | **영구** |
| 분산 트랜잭션 / Saga | 서비스가 하나뿐이라 무대가 없다 | **영구** |
| Redis 캐싱 | 이 프로젝트의 Redis는 **분산 락** 용도다. 캐싱은 다른 주제 | **영구** |
| 재고 이력 테이블 | 현재 수량만 다룬다 (FR-STK-09) | **영구** |
| 모니터링 / APM · 무중단 배포 | 학습 프로젝트에 과하다 | **영구** |
| 메뉴별 소유자 분리(`Menu.owner` FK) | 1인 매장에서 실익이 없고 연쇄 수정 비용만 크다 | **영구** |

---

## 11. 추적표

각 Phase 문서의 **완료 기준 체크박스**가 어느 요구사항으로 흡수됐는지, 그리고 무엇으로 검증하는지.

### Phase 0 (완료)

| 완료 기준 | 요구사항 | 검증 |
| --- | --- | --- |
| 메뉴 가격을 올려도 과거 주문 금액이 그대로다 | FR-ORD-05, FR-ORD-09, FR-MNU-06 / **AC-01** | `order/controller/OrderControllerTest` (가격 스냅샷 회귀 테스트) |
| `./gradlew test` 통과 | NFR-TEST-05 | CI |
| 레포 전체에 "배송"이 없다 | FR-KSK-02 | `grep` |
| — | FR-ORD-01, FR-ORD-02, FR-ORD-03, FR-ORD-04 / **AC-03** | `order/entity/OrderTest` (POJO 8개) |
| — | FR-ORD-07, NFR-DATA-03 / **AC-04** | `OrderControllerTest` |
| — | FR-ORD-06 / **AC-02** | `OrderTest`, `OrderControllerTest` |
| — | FR-KSK-03, FR-KSK-07 / **AC-05** | `OrderControllerTest` |
| — | FR-ORD-10, FR-MNU-03, FR-MNU-04 | `OrderControllerTest`, `menu/controller/MenuControllerTest` |

### Phase 1

| 완료 기준 | 요구사항 | 검증 |
| --- | --- | --- |
| 손님이 로그인 없이 주문하고 대기번호가 크게 뜬다 | FR-KSK-01, FR-KSK-04 | 수동 (브라우저 + PR 스크린샷) |
| 3초 안에 `/kitchen`에 나타나고 상태 변경이 손님 화면에 반영된다 | FR-KIT-01, FR-KIT-05, FR-KSK-06 / **AC-14** | 수동 |
| 토큰 없이 점주 API 호출 → 401 | FR-AUTH-04, FR-AUTH-06 / **AC-12** | 신설 통합 테스트 (최소 2개) |
| "요청 본문 이메일로 권한 확인"하는 코드가 사라졌다 | FR-AUTH-05, FR-MNU-08 | `grep` + 코드 리뷰 |
| `./gradlew test` 통과 (401/403 검증 포함) | NFR-TEST-01 | CI |
| 브라우저에서 실제로 돈다 | **AC-14** | 수동 + 스크린샷 |
| — | FR-KIT-02, FR-KIT-03 | `OrderControllerTest` (구현됨) |
| — | FR-KSK-09 / **AC-13** | 신설 테스트 |
| — | NFR-SEC-04, NFR-SEC-05 | 코드 리뷰 (`SecurityConfig` 주석) |
| — | FR-AUTH-01, FR-AUTH-02, FR-AUTH-03 | 로그인 통합 테스트 (성공/실패 · 해시 저장 확인) |
| — | FR-AUTH-07, FR-AUTH-08, FR-AUTH-10 | 코드 리뷰 (`JwtTokenProvider` 클레임) |
| — | FR-AUTH-09, NFR-SEC-01, NFR-SEC-06 | 코드 리뷰 (BFF 토큰 주입, `.env.example`) |
| — | FR-MNU-01, FR-MNU-02, FR-MNU-05, FR-MNU-07 | `MenuControllerTest` (401 + 404 + `RsData` 포맷) |
| — | FR-ORD-08 | `OrderControllerTest` (구현됨) |
| — | FR-KIT-04, FR-KIT-06, NFR-UX-01 | 수동 (`/kitchen` 폴링 3초) |
| — | FR-KSK-05, FR-ADM-01, FR-ADM-03, FR-ADM-04 | 수동 + PR 스크린샷 (화면 3분할) |
| — | FR-FILE-04 | 신설 통합 테스트 (토큰 없이 업로드 → 401) |
| — | FR-FILE-07 | `file/controller/FileUploadControllerTest` — 업로드 응답이 `/uploads/{UUID}.{확장자}` 인지 확인. 추가로 `menu.img_url`에 `http`로 시작하는 행이 0건 |

### Phase 2

| 완료 기준 | 요구사항 | 검증 |
| --- | --- | --- |
| 재고 3짜리를 4개 주문 → 409, 재고 3 그대로 (부분 차감 없음) | FR-STK-02, FR-STK-03 / **AC-06** | 신설 통합 테스트 |
| 3개 주문 성공 시 재고 0, 화면에 품절 표시 | FR-STK-07, FR-KSK-08 | 통합 테스트 + 수동 |
| 취소하면 재고가 3으로 복구된다 | FR-STK-05, FR-ORD-11 / **AC-07** | 신설 통합 테스트 |
| `COMPLETED` 취소 시도 → 409, 재고 복구 없음 | FR-ORD-02 / **AC-08** | 신설 통합 테스트 |
| 컨트롤러에 `@Transactional`이 하나도 없다 | NFR-CON-05 | `grep` |
| `Backend/App/CLAUDE.md`의 "죽은 도메인" 절이 갱신됐다 | — | 문서 리뷰 |
| `./gradlew test` 통과 | NFR-TEST-01 | CI |
| — | FR-STK-04, NFR-DATA-01, NFR-TEST-06 | `stock/entity/StockTest` (POJO) |
| — | FR-STK-08, FR-ADM-02 | 통합 테스트 + 수동 |
| — | FR-ORD-12, NFR-DATA-02 / **AC-06** | 신설 통합 테스트 (아이템 일부 실패 시 전체 롤백) |

### Phase 3 ★

| 완료 기준 | 요구사항 | 검증 |
| --- | --- | --- |
| 재고 3 / 동시 10 → 3건 성공, 7건 409, 최종 재고 0 (세 전략 각각) | NFR-CON-01, NFR-CON-03, FR-STK-06, NFR-DATA-04 / **AC-09** | 동시성 테스트 (`ExecutorService`, MockMvc 미사용) |
| 락 없던 버전에서 실패했다는 것이 커밋 히스토리에 있다 | NFR-CON-02 | `git log` |
| 다중 메뉴 데드락 시나리오 테스트가 있고 통과한다 | NFR-CON-04 / **AC-10** | 동시성 테스트 |
| `Customer` 생성 경쟁 처리 방식이 이유와 함께 문서에 있다 | NFR-CON-06 / **AC-11** | 문서 + 테스트 |
| Redis가 처음으로 실제 사용된다 | NFR-CON-03 | 코드 리뷰 |
| 세 전략의 트레이드오프가 실측 수치와 함께 정리됐다 | NFR-CON-07 | 문서 리뷰 |
| — | NFR-TEST-03, NFR-TEST-04 | 코드 리뷰 |

### Phase 4

| 완료 기준 | 요구사항 | 검증 |
| --- | --- | --- |
| `prod` 프로필에서 SQL·바인딩 파라미터가 로그에 안 찍힌다 | NFR-SEC-02, NFR-OPS-03 | 수동 기동 확인 |
| `localhost:8080` 하드코딩 0곳 | NFR-OPS-02 | `grep` |
| 브라우저가 8080을 직접 호출하는 경로가 없다 | FR-FILE-05 (C-04) | `grep` + 네트워크 탭 |
| CI가 백엔드 테스트 + 프론트 lint/build를 돌린다 | NFR-TEST-01 | CI 로그 |
| `ddl-auto` 없이 Flyway로 스키마가 만들어진다 | NFR-OPS-01 | 기동 확인 |
| 배포 환경에서 Phase 1 흐름이 그대로 돈다 | NFR-OPS-04 / **AC-14** | 수동 |
| — | FR-KSK-09 (`POST /api/order/list` 폐기) | `grep` |
| — | NFR-SEC-03, NFR-OPS-05 | 코드 리뷰 + 문서 |

### 상시 — 특정 Phase에 속하지 않는 것

이미 만족하고 있으며 **깨지지 않게 지키는 것**이 요구사항인 항목들. Phase 완료 기준에는 나타나지 않는다.

| 요구사항 | 검증 |
| --- | --- |
| FR-STK-01 (메뉴↔재고 1:1) | `stock/repository/StockRepositoryIntegrationTest` |
| FR-FILE-01, FR-FILE-02, FR-FILE-03 (업로드 형식·크기) | 수동 (자동 테스트 없음 — 부채). 파일명 UUID 규칙만 `FileUploadControllerTest`가 곁다리로 검증한다 |
| FR-FILE-06 (`/uploads/**` 이중 서빙) | 수동 (시드 이미지가 clone 직후에도 보이는지) |
| FR-KSK-10, NFR-UX-02 (한국어 UI) | 코드 리뷰 |
| NFR-UX-03 (BFF 오류 메시지 정규화) | 코드 리뷰 (BFF 핸들러) |
| NFR-TEST-02 (`AbstractIntegrationTest` 상속 규약) | 코드 리뷰 — `@Testcontainers`/`@Container` 직접 사용 금지 |
| FR-STK-09 (재고 이력 없음) | 비요구사항 — 이력 테이블이 생기면 이 문서를 먼저 고친다 |

---

> 요구사항을 추가·변경할 때는 **ID를 새로 부여하고 §11 추적표에도 행을 추가한다.** 표에만 있고 추적표에 없는 요구사항은 검증 계획이 없다는 뜻이다.
