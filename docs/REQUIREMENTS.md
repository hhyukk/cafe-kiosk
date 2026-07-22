# cafe-kiosk 요구사항명세서

> 이 문서는 **"무엇을 만족해야 완성인가"**를 담는다. 검증 가능한 형태의 요구사항 목록과 인수 기준.
> **"왜 그렇게 정했는가"**는 [`docs/PRODUCT.md`](PRODUCT.md), **"언제, 지금 어디"**는 [`docs/ROADMAP.md`](ROADMAP.md), **"어떻게 코드를 만지는가"**는 각 디렉토리의 `CLAUDE.md`에 있다.
>
> 기준: **프로젝트 완성 시점** / 최초 작성: 2026-07-21, 완성 기준 전환: 2026-07-22

---

## 1. 이 문서의 역할

이 레포의 문서는 네 개가 한 벌이다. **같은 내용을 두 곳에 적지 않는다.** 중복되면 둘 다 썩는다.

| 문서 | 답하는 질문 | 정본인 것 |
| --- | --- | --- |
| [`PRODUCT.md`](PRODUCT.md) | **왜** 이걸 만드나 | 정체성 진단, 스코프아웃의 이유, 설계 원칙의 근거 |
| **`REQUIREMENTS.md`** (이 문서) | **무엇을** 만족해야 하나 | 요구사항 ID, 인수 기준, 권한 매트릭스, 검증 책임 |
| [`USECASES.md`](USECASES.md) | **누가 어떤 순서로** 쓰나 | 액터별 주 흐름, 대안, 예외 흐름, 사전, 사후조건 |
| [`ROADMAP.md`](ROADMAP.md) + [`roadmap/phase-*.md`](roadmap/) | **언제** 하나, 지금 어디 | 진행 상태, PR 단위, 함정, 작업 순서 |

**"어떻게 조립돼 있는가"는 [`design/architecture.md`](design/architecture.md)에 있다.** 런타임 구성, 계층, 요청 흐름, 데이터 모델 관계. 이 문서가 "무엇을 만족해야 하나"를 진술하면, 그쪽은 그 진술이 **어느 화살표에 걸리는지**를 그린다.

### 이 문서는 진행 상태를 담지 않는다

**완성된 시스템을 기준으로 쓴다.** 어느 요구사항이 지금 만족되고 있는지, 무엇이 남았는지는 [`ROADMAP.md`](ROADMAP.md)와 [`roadmap/phase-*.md`](roadmap/)가 **단독으로** 소유한다.

이 문서에 상태 열을 두지 않는 이유는 하나다. 상태는 코드가 움직일 때마다 거짓이 되는데 요구사항 진술은 그렇지 않다. 둘을 한 표에 두면 표 전체의 신뢰도가 상태의 신선도에 묶인다. **요구사항이 만족됐는지 궁금하면 ROADMAP을 보고, 무엇을 만족해야 하는지 궁금하면 여기를 본다.**

### 표기 규약

**ID 체계.** 기능은 `FR-{도메인}-{번호}`, 비기능은 `NFR-{분류}-{번호}`.

| 도메인 | 범위 | | 분류 | 범위 |
| --- | --- | --- | --- | --- |
| `KSK` | 키오스크, 손님 주문 흐름 | | `CON` | 동시성 |
| `ORD` | 주문, 상태머신, 금액 | | `SEC` | 보안 |
| `MNU` | 메뉴 | | `DATA` | 데이터 무결성 |
| `STK` | 재고 | | `OPS` | 운영, 배포 |
| `AUTH` | 인증, 인가 | | `TEST` | 품질, 검증 |
| `KIT` | 주방(POS) 화면 | | `UX` | 사용성 |
| `ADM` | 관리자 화면 | | | |
| `FILE` | 이미지 업로드 | | | |

**번호는 재사용하지 않는다.** 폐기된 요구사항은 번호를 비우지 말고 "폐기" 행으로 남긴다. 커밋 메시지와 PR이 ID로 이 문서를 참조하기 때문이다.

| 열 | 값 |
| --- | --- |
| **우선** | 필수 / 권장 / 선택 |
| **근거** | 요구사항이 사는 위치. 백엔드는 **파일 경로 + 메서드명까지만** 적고 **줄 번호는 적지 않는다.** 코드가 움직이면 즉시 거짓이 된다. 프론트는 파일이 아니라 **라우트 경로**로 적는다. 화면은 파일보다 경로가 오래 산다 |

백엔드 경로는 `Backend/App/src/main/java/com/cafekiosk/` 이하를, 프론트 BFF 경로는 `frontend/src/` 이하를 생략해 적는다.

---

## 2. 용어

| 용어 | 정의 |
| --- | --- |
| **대기번호** (`orderNumber`) | 손님이 받아가는 번호. 주문 PK에서 파생한 4자리 문자열. 카운터에서 이 번호로 음료를 내준다. 배송 추적번호가 아니다 |
| **가격 스냅샷** (`orderPrice`) | 주문이 성립한 시점의 메뉴 가격. 이후 메뉴 가격이 바뀌어도 과거 주문 금액은 변하지 않는다 |
| **재고** (`Stock.quantity`) | 메뉴별 현재 남은 수량. 이력 테이블은 두지 않는다. 현재 수량만 다룬다 |
| **점주 / 바리스타** | 제품상으로는 다른 역할이지만, **인증상으로는 `ROLE_OWNER` 하나다** (§3) |
| **BFF** | 프론트의 `src/app/api/*` Route Handler. 브라우저와 백엔드 사이의 유일한 통로이며 **토큰의 보관처**이기도 하다 |
| **CONFIRMED** | "결제까지 끝난 상태". 실결제는 스코프아웃이므로 결제 성공을 가정하고 이 상태로 주문이 생성된다 |
| **COMPLETED** (픽업완료) | **그 주문의 종료**를 뜻한다. 손님이 실제로 받아갔는지와는 무관하다. 오지 않은 손님의 주문도 바리스타가 이 상태로 눌러 정리한다 (§10) |
| **마지막 한 잔** | 이 프로젝트의 목표 시나리오. 재고가 1 남은 메뉴를 두 손님이 동시에 주문했을 때 정확히 한 명만 성공하는 것 |

---

## 3. 액터와 시스템 경계

| 액터 | 인증 | 쓰는 화면 | 하는 일 |
| --- | --- | --- | --- |
| **손님** | **없음 (익명)** | `/` 키오스크, `/order/{orderNumber}` 주문 조회 | 메뉴 조회, 주문, 대기번호로 자기 주문 상태 조회 |
| **바리스타** | `ROLE_OWNER` | `/kitchen` 주방 | 주문을 `제조중 → 준비완료 → 픽업완료`로 전이, 취소 |
| **점주** | `ROLE_OWNER` | `/admin` 관리자 | 메뉴 CRUD, 재고 조정, 이미지 업로드, 지난 주문 조회 |

로그인 화면은 `/login`이며 액터가 아니라 **경유지**다. 인증이 필요한 화면에 인증 없이 들어가면 여기로 보낸다.

**액터는 셋인데 역할(Role)은 둘이다.** 바리스타와 점주를 하나의 `ROLE_OWNER`로 묶는 것은 의도된 단순화다. 1인 매장 기준이고, 역할을 쪼개면 이 레포의 주제인 동시성에서 멀어진다. 근거는 [`roadmap/phase-1.md` 소유권 모델](roadmap/phase-1.md).

**손님에게 로그인을 요구하는 것은 제품 실패로 간주한다.** 회원가입, 마이페이지는 영구 스코프아웃이다(§10).

**외부 시스템은 없다.** PG(결제대행)를 연동하지 않으므로 시스템 경계 바깥과 주고받는 것이 없다.

---

## 4. 제약과 가정

| # | 제약 / 가정 |
| --- | --- |
| C-01 | **단일 매장, 키오스크 1대**를 가정한다. 매장 다중화, 키오스크 다대는 요구사항이 아니다 |
| C-02 | 기술 스택은 고정이다. Spring Boot 4.0 / Java 21 / PostgreSQL 16 / JPA / Redis 7, Next.js 16 App Router / React 19 / TypeScript 5 / Tailwind 4 |
| C-03 | **결제는 모킹한다.** 주문 생성이 곧 결제 완료(`CONFIRMED`)다 |
| C-04 | **브라우저는 백엔드를 직접 호출하지 않는다.** 모든 요청은 BFF Route Handler를 거친다 |
| C-05 | 스키마는 **Flyway 마이그레이션**이 소유한다. `dev` 프로필에서만 `BaseInitData`가 메뉴 3개 + 재고 **100 / 50 / 3**과 점주 계정 1개를 시드한다. 마지막 재고가 3인 것은 우연이 아니라 AC-09의 재현 조건이다 |
| C-06 | 통합 테스트는 Testcontainers PostgreSQL 위에서 돈다. **테스트 실행에 Docker 데몬이 필요하다** |
| C-07 | 업로드 이미지는 **로컬 디스크(`./uploads`)에 저장한다.** 오브젝트 스토리지로 옮기지 않으며, 그 대가로 **인스턴스가 재생성되면 런타임 업로드 이미지가 사라진다.** 커밋된 시드 이미지는 클래스패스에 있어 항상 살아남으므로 데모는 깨지지 않는다. 근거는 NFR-OPS-05 |
| C-08 | 이 레포는 **학습 프로젝트**다. 처리량, 가용성보다 **정확성과 설명 가능성**이 우선한다 |

---

## 5. 기능 요구사항

### 5-1. FR-KSK: 키오스크, 손님이 쓰는 화면

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-KSK-01 | 손님은 **로그인, 회원가입 없이** 메뉴를 조회하고 주문할 수 있다 | 필수 | `order/controller/OrderController.createOrder` |
| FR-KSK-02 | 주문 시 손님에게 요구하는 정보는 **이메일 하나뿐**이다. 주소, 우편번호 등 배송 정보를 받지 않는다 | 필수 | `order/dto/OrderDto.CreateRequest` |
| FR-KSK-03 | 주문이 성립하면 **대기번호와 결제 금액**을 반환한다 | 필수 | `OrderDto.CreateResponse` |
| FR-KSK-04 | 대기번호는 **전용 완료 화면에 크게** 표시된다. `alert`를 쓰지 않는다 | 필수 | `/order/{orderNumber}` |
| FR-KSK-05 | 손님 화면에 **메뉴 추가, 수정, 삭제 UI가 노출되지 않는다** | 필수 | `/`. 관리자 기능은 `/admin`에만 있다 |
| FR-KSK-06 | 손님은 받은 **대기번호로 자기 주문의 현재 상태**를 조회할 수 있다 | 필수 | `OrderController.getOrderByNumber`, `/order/{orderNumber}` |
| FR-KSK-07 | 한 주문의 **총 수량은 1~100개**다. 벗어나면 400 | 필수 | `OrderController.createOrder`, `app/api/order/route.ts` |
| FR-KSK-08 | **품절 메뉴는 품절로 표시**되고 장바구니에 담을 수 없다 | 필수 | `/`, `MenuDto.MenuListResponse`의 `soldOut` |
| FR-KSK-09 | 손님은 **남의 주문 내역을 조회할 수 없다.** 이메일로 주문을 통째로 조회하는 경로는 존재하지 않는다 | 필수 | 대기번호 단건 조회만 공개. §9-1 |
| FR-KSK-10 | 주문 화면, 완료 화면, 조회 화면의 텍스트는 한국어다 | 권장 | `/`, `/order/{orderNumber}` |

### 5-2. FR-ORD: 주문, 상태머신, 금액

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-ORD-01 | 주문 상태는 `CONFIRMED → IN_PROGRESS → READY → COMPLETED` 순으로만 전이한다 | 필수 | `order/entity/Order` (`startPreparing`/`markReady`/`complete`) |
| FR-ORD-02 | 주문은 `CONFIRMED` 또는 `IN_PROGRESS`에서만 `CANCELLED`로 갈 수 있다 | 필수 | `Order.cancel` |
| FR-ORD-03 | **허용되지 않은 전이는 409로 거부되고 상태가 바뀌지 않는다** | 필수 | `order/exception/InvalidOrderStatusTransitionException` → `global/globalExceptionHandler/GlobalExceptionHandler` |
| FR-ORD-04 | **상태 전이 규칙은 `Order` 엔티티가 소유한다.** 서비스, 컨트롤러가 `status`를 직접 대입하지 않는다 | 필수 | `Order`, `order/service/OrderService.changeStatus` |
| FR-ORD-05 | 주문 생성 시 각 아이템의 **주문 시점 가격을 스냅샷**한다 | 필수 | `order/entity/OrderItem` 생성자 |
| FR-ORD-06 | **주문 총액은 아이템 스냅샷 소계의 합과 항상 일치한다.** 총액은 `Order.addOrderItem()`으로만 늘어난다 | 필수 | `Order.addOrderItem`, `OrderItem.getSubtotal` |
| FR-ORD-07 | 대기번호는 **PK에서 파생**되어 전역 유일하고 단조 증가한다. "오늘 주문 수 + 1" 방식을 쓰지 않는다 | 필수 | `Order.assignOrderNumber` |
| FR-ORD-08 | 주문 조회 응답은 `orderId`, `orderNumber`, `status`, `orderTime`, `totalPrice`, `items`를 포함한다 | 필수 | `OrderDto.OrderSummary` |
| FR-ORD-09 | 주문 조회, 목록의 금액은 **항상 스냅샷 가격**이며 현재 메뉴 가격을 읽지 않는다 | 필수 | `OrderService.toItemDTO` |
| FR-ORD-10 | 존재하지 않는 메뉴 ID로 주문하면 400 | 필수 | `OrderService.createOrder` → `GlobalExceptionHandler` |
| FR-ORD-11 | 주문이 취소되면 **차감된 재고가 복구된다** | 필수 | `OrderService.changeStatus`. FR-STK-05와 한 몸 |
| FR-ORD-12 | 주문 생성은 **원자적**이다. 아이템 중 하나라도 실패하면 주문 전체가 성립하지 않는다 | 필수 | `OrderService.createOrder`의 트랜잭션 경계 |
| FR-ORD-13 | 상태는 다섯 개뿐이다. `CONFIRMED`, `IN_PROGRESS`, `READY`, `COMPLETED`, `CANCELLED`. **장바구니 미제출을 뜻하는 상태를 두지 않는다** | 필수 | `order/entity/OrderStatus` |

> **설계 소유권.** 상태 전이와 금액 계산은 **엔티티가 소유한다.** 서비스는 엔티티를 조회해 메서드를 호출할 뿐이다. 총액이 아이템과 어긋날 수 있는 **경로 자체를 없애는 것**이 목적이다. (`Backend/App/CLAUDE.md`)
>
> **FR-ORD-13은 코드 정리가 아니라 제품 모델의 진술이다.** 장바구니는 브라우저 안에서만 존재하고 서버는 그것을 모른다. UC-01의 성공 사후조건이 *"서버 상태는 아무것도 바뀌지 않는다"*인 이유다. 서버에 "미제출 장바구니" 상태가 있으면 그 조건이 거짓이 되고, 다음 사람이 장바구니를 서버에 저장하는 방향으로 읽는다.

### 5-3. FR-MNU: 메뉴

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-MNU-01 | 누구나(익명 포함) 메뉴 목록을 조회할 수 있다 | 필수 | `menu/controller/MenuController.getMenus` |
| FR-MNU-02 | **인증된 점주만** 메뉴를 등록, 수정, 삭제할 수 있다 | 필수 | `global/config/SecurityConfig`의 `hasRole("OWNER")` |
| FR-MNU-03 | 메뉴 가격은 **0원 이상 10,000,000원 이하**다. 벗어나면 400 | 필수 | `MenuController.createMenu`, `app/api/menu/route.ts` |
| FR-MNU-04 | 메뉴 등록, 수정 시 이름, 카테고리, 가격은 필수이며 서버가 검증한다 | 필수 | `menu/dto/CreateMenuRequestDto`, `MenuDto.MenuModifyRequest` + `@Valid` |
| FR-MNU-05 | 존재하지 않는 메뉴를 수정, 삭제하면 **404**다 | 필수 | `MenuService.modify`, `MenuService.deleteMenu` |
| FR-MNU-06 | **메뉴 가격을 바꿔도 과거 주문 금액은 변하지 않는다** | 필수 | FR-ORD-05/09가 보장. 회귀 테스트 `order/controller/OrderControllerTest` |
| FR-MNU-07 | 메뉴 API 경로는 REST 스타일이고 응답은 `RsData<T>`로 통일된다 | 권장 | `MenuController`. §9-1, §9-5 |
| FR-MNU-08 | `Menu.email`은 **"누가 등록했는가"의 기록**일 뿐이며 권한 판단에 쓰이지 않는다. 값은 인증된 점주에게서 서버가 채운다 | 필수 | `menu/entity/Menu`, `MenuService.create` |

### 5-4. FR-STK: 재고

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-STK-01 | 메뉴마다 재고가 **1:1**로 존재한다 | 필수 | `stock/entity/Stock` |
| FR-STK-02 | **주문 생성 시 주문 수량만큼 재고를 차감한다** | 필수 | `OrderService.createOrder` → `stock/service/StockService` |
| FR-STK-03 | 재고가 부족하면 **주문 전체가 실패하고 부분 차감이 남지 않는다.** 409로 응답한다 | 필수 | `stock/exception/OutOfStockException` → `GlobalExceptionHandler` |
| FR-STK-04 | **재고 증감 규칙은 `Stock` 엔티티가 소유한다.** `decrease`/`increase`가 스스로 부족을 판단해 던진다. 서비스가 `if (quantity < count)`를 검사하지 않는다 | 필수 | `Stock.decrease`, `Stock.increase` |
| FR-STK-05 | 주문이 취소되면 그 주문의 수량만큼 재고가 복구된다. **`CANCELLED` 전이가 성공했을 때만** 복구한다 | 필수 | `OrderService.changeStatus` |
| FR-STK-06 | **재고는 어떤 경로로도 음수가 되지 않는다.** 동시 요청에서도 마찬가지다 | 필수 | `Stock.decrease` + NFR-CON-01 |
| FR-STK-07 | 메뉴 조회 응답에 **재고 수량과 품절 여부**가 포함된다 | 필수 | `menu/dto/MenuDto.MenuListResponse` |
| FR-STK-08 | 점주는 재고를 조정할 수 있다 | 필수 | `stock/controller/StockController` |
| FR-STK-09 | 재고 **이력**은 관리하지 않는다. 현재 수량만 다룬다 | 없음 | 의도적 비요구사항. §10 |

### 5-5. FR-AUTH: 인증, 인가

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-AUTH-01 | 점주는 이메일, 비밀번호로 로그인해 **Access 토큰**을 받는다 | 필수 | `auth/controller/AuthController`, `auth/service/AuthService`. 설계 정본 [`design/jwt-auth.md`](design/jwt-auth.md) |
| FR-AUTH-02 | **비밀번호는 해시로만 저장한다.** 시드 계정도 평문 금지 | 필수 | `owner/entity/Owner`, `global/initData/BaseInitData` |
| FR-AUTH-03 | 인증은 **stateless JWT(HS256)**이며 서버 세션을 두지 않는다 | 필수 | `auth/jwt/JwtTokenProvider` |
| FR-AUTH-04 | **점주 전용 API를 토큰 없이 호출하면 401**이다 | 필수 | `SecurityConfig` |
| FR-AUTH-05 | **인가를 요청 본문의 이메일 문자열 비교로 하지 않는다** | 필수 | `auth/JwtAuthenticationFilter` → `SecurityContext` |
| FR-AUTH-06 | **손님 엔드포인트는 인증을 요구하지 않는다.** 인증 도입이 손님 흐름을 막으면 안 된다 | 필수 | `SecurityConfig`의 `permitAll`. §9-2 |
| FR-AUTH-07 | 토큰 클레임에 **민감정보를 담지 않는다.** 식별자, 역할만 담는다 | 필수 | `JwtTokenProvider.createAccessToken`. [`design/jwt-auth.md`](design/jwt-auth.md) |
| FR-AUTH-08 | 바리스타와 점주는 **`ROLE_OWNER` 하나**로 인증한다 | 필수 | `auth/OwnerPrincipal`. §3 |
| FR-AUTH-09 | **토큰은 BFF가 붙인다.** 브라우저가 백엔드에 직접 토큰을 보내지 않는다 | 필수 | `app/api/**` Route Handler. C-04의 연장 |
| FR-AUTH-10 | 토큰 만료 후에는 재로그인이 필요하다. Refresh 토큰은 두지 않는다 | 권장 | 의도적 단순화. [`design/jwt-auth.md` §10](design/jwt-auth.md) |
| FR-AUTH-11 | 토큰은 **`httpOnly` 쿠키로 BFF가 보관하며 브라우저 JS에 노출되지 않는다.** `localStorage`에 두지 않는다 | 필수 | `app/api/auth/login/route.ts` |
| FR-AUTH-12 | **로그아웃은 BFF가 쿠키를 지우는 것으로 성립한다.** 백엔드에 무효화 엔드포인트를 두지 않는다 | 필수 | `app/api/auth/logout/route.ts` |

> **FR-AUTH-11이 C-04의 진짜 이유다.** 브라우저가 토큰 문자열을 들고 있으면 `Authorization` 헤더를 직접 만들고 싶어지고, 그 순간 BFF를 우회할 동기가 생긴다. 토큰을 `httpOnly` 쿠키에 가두면 우회할 방법 자체가 없어지고 XSS로 새지도 않는다.
>
> **FR-AUTH-12는 stateless의 한계를 우회한다.** 발급된 토큰은 만료까지 유효하다는 사실은 변하지 않지만, 브라우저에서 쿠키가 사라지면 그 토큰을 다시 보낼 주체가 없다. Redis 블랙리스트 없이 실질 로그아웃이 성립하는 이유다.

### 5-6. FR-KIT: 주방 화면

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-KIT-01 | 바리스타는 `/kitchen`에서 **들어온 주문 목록**을 본다 | 필수 | `/kitchen` |
| FR-KIT-02 | 주문 목록은 **주문 시각 오름차순(FIFO)**이다. 먼저 들어온 주문을 먼저 만든다 | 필수 | `OrderService.getOrdersByStatus`, `OrderRepository.findAllByOrderByOrderTimeAsc` |
| FR-KIT-03 | 목록을 상태로 필터링할 수 있고, **결과가 비어도 200 + 빈 배열**이다 | 필수 | `OrderController.getOrders`. §9-5 |
| FR-KIT-04 | 바리스타는 주문을 `제조중 → 준비완료 → 픽업완료`로 전이시킨다 | 필수 | `OrderController.changeStatus`, `/kitchen` |
| FR-KIT-05 | 새 주문이 **3초 이내**에 주방 화면에 나타난다 | 필수 | `/kitchen` 폴링. NFR-UX-01 |
| FR-KIT-06 | 주방 화면과 그 API는 **인증된 점주만** 접근할 수 있다 | 필수 | `SecurityConfig`. FR-AUTH-04의 적용 |

### 5-7. FR-ADM: 관리자 화면

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-ADM-01 | 점주는 `/admin`에서 로그인 후 메뉴를 등록, 수정, 삭제한다 | 필수 | `/admin` |
| FR-ADM-02 | 점주는 `/admin`에서 재고를 조정한다 | 필수 | `/admin` |
| FR-ADM-03 | **관리자 기능은 손님 화면에 노출되지 않는다** | 필수 | FR-KSK-05와 한 몸 |
| FR-ADM-04 | 권한 확인에 `window.prompt`로 이메일을 묻는 방식을 쓰지 않는다. **삭제 확인은 실수 방지이지 권한 확인이 아니다** | 필수 | `/admin`. 인가는 서버가 한다(FR-AUTH-05) |
| FR-ADM-05 | 점주는 `/admin`에서 **지난 주문을 상태별로 조회한다.** 완료, 취소된 주문을 포함한다 | 필수 | `/admin`, `OrderController.getOrders` |

> **FR-ADM-05는 주방 화면과 액터가 다르다.** `/kitchen`은 *지금 만들 것*을 보는 작업 화면이라 활성 주문만 다룬다. 지난 주문을 되짚는 것은 점주의 관리 행위이므로 `/admin`이 소유한다. API는 신설하지 않고 `GET /api/orders`를 그대로 쓴다.
>
> **이 요구사항이 없으면 이메일 기반 주문 조회를 폐기할 수 없다.** 폐기의 전제가 되는 대체 경로가 바로 이것이다. FR-KSK-09.

### 5-8. FR-FILE: 이미지 업로드

| ID | 요구사항 | 우선 | 근거 |
| --- | --- | --- | --- |
| FR-FILE-01 | 점주는 메뉴 이미지를 업로드하고 그 URL을 메뉴에 붙일 수 있다 | 필수 | `file/controller/FileUploadController` |
| FR-FILE-02 | **이미지 파일만**, **5MB 이하**만 허용한다. 위반 시 400 | 필수 | `FileUploadController.uploadImage` |
| FR-FILE-03 | 저장 파일명은 UUID로 생성해 충돌, 덮어쓰기를 막는다 | 필수 | `FileUploadController.uploadImage` |
| FR-FILE-04 | **업로드는 점주 전용이다** | 필수 | `SecurityConfig` |
| FR-FILE-05 | 업로드 요청도 **BFF를 거친다** | 필수 | `app/api/upload/route.ts`. C-04 |
| FR-FILE-06 | 커밋된 시드 이미지(클래스패스)와 런타임 업로드 파일(파일시스템)이 **모두 `/uploads/**`로 서빙**된다 | 필수 | `global/config/WebConfig.addResourceHandlers` |
| FR-FILE-07 | 저장되는 이미지 URL은 **호스트를 포함하지 않는다.** `/uploads/{파일명}` 형태의 상대경로이며, DB에 배포 환경 주소가 박히지 않는다 | 필수 | `FileUploadController.uploadImage`, `global/initData/BaseInitData.work1` |
| FR-FILE-08 | **업로드된 이미지 파일은 삭제하지 않는다.** 메뉴를 지워도 파일은 남는다 | 필수 | 의도된 보존. 아래 참고 |

> **FR-FILE-07은 코드가 아니라 데이터에 박히는 결함이라 따로 세웠다.** 업로드 응답의 URL이 그대로 `Menu.imgUrl` 컬럼에 저장되므로, 여기서 호스트를 붙이면 배포 환경에서 이미지가 전부 깨진다. 코드의 하드코딩은 `grep`으로 걷어낼 수 있지만 이미 저장된 행은 그렇지 않다. 그래서 요구사항을 "BFF를 거쳐라"가 아니라 **"호스트를 저장하지 마라"**로 세웠다. 상대경로가 되는 순간 `next.config.ts`의 `/uploads/:path*` rewrite가 그 요청을 받아 백엔드로 넘긴다. 구조는 [`design/architecture.md`](design/architecture.md)의 런타임 구성에 있다.
>
> **FR-FILE-08은 방치가 아니라 판단이다.** 메뉴를 삭제해도 그 메뉴가 담긴 **과거 주문 내역은 남는다**(FR-MNU-06). 그 주문을 조회할 때 이미지가 필요하므로, 파일을 지우는 쪽이 오히려 데이터를 깨뜨린다. 메뉴 저장을 취소해서 어느 메뉴에도 붙지 않은 파일이 디스크에 남는 것은 이 규칙의 부작용이며, 정리 배치를 만들지 않고 감수한다. §10.

---

## 6. 인수 기준

표의 진술만으로 검증 방법이 모호한 **핵심 요구사항 14개**만 Given/When/Then으로 상세화한다. 여기 없는 요구사항은 표의 진술로 충분하다.

### AC-01, 가격 스냅샷 회귀 (FR-ORD-05, FR-ORD-09, FR-MNU-06)
```
Given  15,000원짜리 메뉴를 1개 주문했다 (주문 금액 15,000원)
When   점주가 그 메뉴 가격을 99,000원으로 수정한다
Then   과거 주문의 조회 금액은 여전히 15,000원이다
And    새로 담는 주문에는 99,000원이 적용된다
```
> 이 레포의 **회귀 방지선**이다. `getOrderList`가 `orderItem.getOrderPrice()` 대신 `getMenu().getMenuPrice()`를 읽는 순간 깨진다.

### AC-02, 총액 일치 (FR-ORD-06)
```
Given  임의의 주문
When   주문을 조회한다
Then   totalPrice == Σ(items[i].orderPrice × items[i].count) 이다
```

### AC-03, 상태 전이 위반 (FR-ORD-03)
```
Given  상태가 CONFIRMED 인 주문
When   PATCH /api/order/{id}/status 로 READY 를 요청한다   (IN_PROGRESS 를 건너뛴다)
Then   409 로 거부되고
And    주문 상태는 여전히 CONFIRMED 다
```

### AC-04, 대기번호 유일성 (FR-ORD-07)
```
Given  주문이 N건 생성됐다
When   모든 주문의 orderNumber 를 모은다
Then   중복이 없고, 생성 순서대로 단조 증가한다
```

### AC-05, 주문 수량 경계 (FR-KSK-07)
```
Given  아무 메뉴
When   총 수량 0개 또는 101개로 주문한다
Then   400 이고 주문이 생성되지 않는다
When   총 수량 1개 또는 100개로 주문한다
Then   주문이 생성된다
```

### AC-06, 재고 부족 시 전체 실패 (FR-STK-03, FR-ORD-12)
```
Given  재고가 3개인 메뉴가 있고, 재고가 넉넉한 메뉴 A와 B 가 있다
When   [A 1개, B 1개, 재고3짜리 4개] 를 한 주문으로 요청한다
Then   409 로 거부되고
And    A 와 B 의 재고도 차감되지 않았다   ← 부분 차감 금지
And    재고3짜리 메뉴의 재고는 여전히 3이다
```

### AC-07, 취소 시 재고 복구 (FR-STK-05, FR-ORD-11)
```
Given  재고 3인 메뉴를 3개 주문해 재고가 0이 됐다
When   그 주문을 CANCELLED 로 전이시킨다
Then   전이가 성공하고
And    재고가 3으로 복구된다
```

### AC-08, 완료된 주문은 취소도 복구도 없다 (FR-STK-05, FR-ORD-02)
```
Given  상태가 COMPLETED 인 주문
When   CANCELLED 로 전이를 시도한다
Then   409 이고
And    재고가 복구되지 않는다              ← 이중 복구 금지
```

### AC-09, ★ 마지막 한 잔 (NFR-CON-01)
```
Given  재고가 정확히 3개인 메뉴
When   서로 다른 10개 스레드가 CountDownLatch 로 동시에 1개씩 주문한다
Then   정확히 3건이 성공하고
And    정확히 7건이 409 로 거부되고
And    최종 재고가 정확히 0이다              (음수도, 1 이상도 아니다)
```
> **이 프로젝트의 목적지다.** 비관적 락, 낙관적 락, Redisson 분산 락 **세 전략 각각에서** 통과해야 한다(NFR-CON-03). 그리고 **락이 없던 버전에서 이 테스트가 실패했다는 사실이 커밋 히스토리에 남아 있어야 한다**. NFR-CON-02가 그것이다. 해결책부터 배우지 않는 것이 이 프로젝트의 학습 설계다.

### AC-10, 데드락 회피 (NFR-CON-04)
```
Given  메뉴 X 와 메뉴 Y, 둘 다 재고가 넉넉하다
When   주문 A 는 [X, Y] 순으로, 주문 B 는 [Y, X] 순으로 동시에 들어온다
Then   교착 없이 둘 다 완료된다 (락은 menuId 오름차순으로 잡힌다)
```

### AC-11, 동시 첫 주문의 고객 생성 경쟁 (NFR-CON-06)
```
Given  DB 에 등록된 적 없는 이메일
When   그 이메일로 동시에 10건을 주문한다
Then   Customer 유니크 제약 위반(500)이 발생하지 않고
And    재고 로직까지 정상적으로 도달한다
```
> 재고를 보러 갔다가 엉뚱한 곳에서 막히는 함정이다. 어떻게 해결했는지와 **고른 이유**를 문서에 남긴다.

### AC-12, 인증 경계 (FR-AUTH-04, FR-AUTH-06)
```
Given  토큰이 없다
When   GET /api/orders, PATCH /api/order/{id}/status, POST /api/menus, POST /api/upload/image 를 호출한다
Then   전부 401 이다
When   POST /api/order, GET /api/menus, GET /api/orders/{orderNumber} 를 호출한다
Then   정상 동작한다                          ← 손님 흐름은 인증 도입에 영향받지 않는다
```
> **"안 되니까 전부 permitAll"로 도망가지 않는다.** 401을 확인하는 테스트가 이 요구사항의 실제 산출물이다.

### AC-13, 손님은 자기 주문만 본다 (FR-KSK-06, FR-KSK-09)
```
Given  손님 A 의 대기번호와 손님 B 의 대기번호
When   손님 A 가 자기 대기번호로 조회한다
Then   자기 주문의 상태, 금액, 아이템이 보인다
When   요청 본문에 남의 이메일을 넣어 주문 내역을 조회하는 경로를 찾는다
Then   그런 경로가 존재하지 않는다
```

### AC-14, 끝에서 끝까지 (FR-KSK-04, FR-KIT-01, FR-KIT-05)
```
Given  백엔드와 프론트가 떠 있다
When   손님이 / 에서 로그인 없이 주문한다
Then   대기번호가 화면에 크게 뜬다 (alert 아님)
And    3초 안에 그 주문이 /kitchen 에 나타난다
When   바리스타가 제조중 → 준비완료로 넘긴다
Then   손님의 주문 조회 화면 상태가 바뀐다
```
> **브라우저에서 실제로 돌아가는 것**까지가 인수 조건이다. CI가 프론트의 동작까지 검증하지는 않으므로 수동 확인 + PR 스크린샷이 유일한 증거다.

---

## 7. 비기능 요구사항

### 7-1. NFR-CON: 동시성 ★

| ID | 요구사항 | 우선 |
| --- | --- | --- |
| NFR-CON-01 | 재고 N인 메뉴에 동시 주문 M건(M > N)이 들어오면 **정확히 N건 성공, M-N건 409, 최종 재고 0**이다 | 필수 |
| NFR-CON-02 | 락이 없던 버전에서 위 테스트가 **실패한다는 사실이 커밋 히스토리에 남는다** | 필수 |
| NFR-CON-03 | **비관적 락, 낙관적 락, Redisson 분산 락 세 전략 각각**에서 NFR-CON-01을 만족한다 | 필수 |
| NFR-CON-04 | 다중 메뉴 주문이 서로 반대 순서로 들어와도 **데드락이 발생하지 않는다** (`menuId` 오름차순 락) | 필수 |
| NFR-CON-05 | 낙관적 락 재시도는 **롤백된 트랜잭션 밖, 새 트랜잭션에서** 이뤄진다. 이를 위해 **컨트롤러에 `@Transactional`이 없다** | 필수 |
| NFR-CON-06 | 같은 이메일의 동시 첫 주문에서 `Customer` 유니크 제약 위반이 발생하지 않는다 | 필수 |
| NFR-CON-07 | 세 전략의 트레이드오프가 **실측 수치와 함께** 문서화된다. "경쟁이 잦으면 비관적, 드물면 낙관적"을 말이 아니라 숫자로 | 필수 |

> **여기서 목표는 정확성이지 처리량이 아니다.** 부하 테스트 도구는 요구사항이 아니다(§10).

### 7-2. NFR-SEC: 보안

| ID | 요구사항 | 우선 |
| --- | --- | --- |
| NFR-SEC-01 | **시크릿을 커밋하지 않는다.** `jwt.secret`, `DB_PASSWORD`는 배포 시크릿으로만 주입하고 `.env.example`에는 키 이름만 둔다 | 필수 |
| NFR-SEC-02 | **개인정보가 로그에 남지 않는다.** 바인딩 파라미터 TRACE 로깅은 `prod`에서 끈다 | 필수 |
| NFR-SEC-03 | CORS 허용 출처는 명시적이며 배포 도메인은 환경변수로 받는다 | 필수 |
| NFR-SEC-04 | CSRF는 stateless JSON API이므로 끄되, **끈 이유를 `SecurityConfig`에 주석으로 남긴다** | 필수 |
| NFR-SEC-05 | HS256 서명 키는 **32byte 이상**이어야 한다 | 필수 |
| NFR-SEC-06 | 인가 판단은 **서버가 검증한 신원**에 근거한다. 클라이언트가 보낸 식별자를 신뢰하지 않는다 | 필수 |

### 7-3. NFR-DATA: 데이터 무결성

| ID | 요구사항 | 우선 |
| --- | --- | --- |
| NFR-DATA-01 | **불변식은 엔티티가 소유한다.** 상태 전이(`Order`), 금액(`Order`/`OrderItem`), 재고(`Stock`) 모두 그렇다. 서비스가 규칙을 검사하지 않는다 | 필수 |
| NFR-DATA-02 | 주문 생성과 재고 차감은 **한 트랜잭션**이다 (all-or-nothing) | 필수 |
| NFR-DATA-03 | 대기번호는 유일하다 (`@Column(unique = true)` + PK 파생) | 필수 |
| NFR-DATA-04 | 재고는 음수가 될 수 없다. 단일 스레드에서는 `Stock.decrease`가, 동시 요청에서는 락이 보장한다 | 필수 |

### 7-4. NFR-OPS: 운영, 배포

| ID | 요구사항 | 우선 |
| --- | --- | --- |
| NFR-OPS-01 | 스키마는 **Flyway 마이그레이션**으로 관리되고 `ddl-auto`를 쓰지 않는다 | 필수 |
| NFR-OPS-02 | 레포 전체에 `localhost:8080` 하드코딩이 **0곳**이다. 백엔드 주소는 환경변수로 주입한다 | 필수 |
| NFR-OPS-03 | 프로필이 `dev` / `test` / `prod`로 분리되고, 활성 프로필은 환경변수로 정해진다 | 필수 |
| NFR-OPS-04 | 배포된 환경에서 AC-14(끝에서 끝까지)가 그대로 동작한다 | 필수 |
| NFR-OPS-05 | 업로드 파일은 **로컬 디스크에 두고 인스턴스 재생성 시 유실을 감수한다.** 오브젝트 스토리지로 옮기지 않는다 | 권장 |

> **NFR-OPS-05는 결정이 끝난 항목이다.** S3를 붙이면 자격증명, 버킷 정책, SDK 의존성이 들어오는데, 거기서 배우는 것이 이 프로젝트의 주제인 동시성과 무관하다. 대가는 명확하다. **런타임에 올린 이미지는 인스턴스가 재생성되면 사라진다.** 다만 커밋된 시드 이미지는 클래스패스에서 서빙되므로(FR-FILE-06) 데모가 깨지지는 않는다. 감수하는 대신 그 사실을 C-07에 명시해 뒀다.

### 7-5. NFR-TEST: 품질, 검증

| ID | 요구사항 | 우선 |
| --- | --- | --- |
| NFR-TEST-01 | CI가 **백엔드 테스트와 프론트 lint, build를 모두** 돌린다 | 필수 |
| NFR-TEST-02 | 통합 테스트는 `support/AbstractIntegrationTest`를 상속한다. `@Testcontainers`/`@Container`를 개별 테스트에 붙이지 않는다 | 필수 |
| NFR-TEST-03 | **동시성 테스트에 `@Transactional`을 붙이지 않는다.** `ExecutorService`로 서비스를 직접 호출하고, 뒷정리는 `@AfterEach`가 한다 | 필수 |
| NFR-TEST-04 | 동시성 테스트는 **커넥션 풀 크기를 명시**하고 스레드 수와의 관계를 주석으로 남긴다. 풀 고갈을 락 문제로 오해하지 않기 위해 | 필수 |
| NFR-TEST-05 | 기능을 바꾸면 단위/통합 테스트를 함께 추가한다 | 필수 |
| NFR-TEST-06 | 순수 로직(상태 전이, 재고 증감)은 Spring 컨텍스트 없이 POJO 단위 테스트로 검증한다 | 권장 |

### 7-6. NFR-UX

| ID | 요구사항 | 우선 |
| --- | --- | --- |
| NFR-UX-01 | 주방 화면은 **폴링 3초**로 갱신한다. WebSocket은 쓰지 않는다 | 필수 |
| NFR-UX-02 | UI 텍스트, 오류 메시지는 한국어다 | 권장 |
| NFR-UX-03 | BFF는 백엔드 오류 응답의 `message`/`msg`/`error` 중 무엇이 오든 **`{ message }` 형태로 정규화**해 내려준다 | 권장 |
| NFR-UX-04 | **손님 주문 조회 화면은 폴링하지 않는다.** 손님이 다시 조회할 때 갱신된다 | 권장 |

> **NFR-UX-04는 부하가 아니라 제품 모델의 문제다.** 키오스크는 공용 단말이라 손님이 화면 앞에 계속 서 있지 않는다. 주문을 마친 손님은 자리를 비켜야 하고 다음 손님이 그 화면을 쓴다. 머물지 않는 화면을 폴링하는 것은 의미가 없다. 주방 화면(NFR-UX-01)과 정반대의 상황이라 규칙도 반대다.

---

## 8. 데이터 요구사항

도메인 모델. **핵심 열은 "소유자"다.** 이 값을 누가 바꿀 수 있는가.

| 엔티티 | 필드 | 불변식 | **소유자 (변경 권한)** |
| --- | --- | --- | --- |
| **Order** | `orderNumber` | 유일, PK 파생, 한 번만 발급 | `Order.assignOrderNumber()`. INSERT 이후 1회 |
| | `totalPrice` | 아이템 스냅샷 소계의 합과 항상 일치 | `Order.addOrderItem()` **만** |
| | `status` | 정해진 전이만 허용. 값은 다섯 개 | `Order`의 전이 메서드 **만** |
| | `orderTime` | 생성 시 고정 | 생성자 |
| **OrderItem** | `orderPrice` | 주문 시점 메뉴 가격, 이후 불변 | `OrderItem` 생성자가 메뉴에서 복사 |
| | `count` | 생성 시 고정 | 생성자 |
| **Customer** | `email` | 유일 | 생성자. **회원이 아니라 익명 주문 주체**다 |
| **Menu** | `menuName`,`menuPrice`,`imgUrl`,`category` | 가격 0~10,000,000, `imgUrl`은 호스트 없는 상대경로 | `Menu.modify()` |
| | `email` | 없음 | **등록자 기록일 뿐. 권한 판단에 쓰지 않는다** |
| **Stock** | `quantity` | **음수가 될 수 없다** | `Stock.decrease()` / `Stock.increase()` **만** |
| | `version` | 낙관적 락용 | JPA (`@Version`) |
| **Owner** | `email` | 유일 | 생성자 |
| | `passwordHash` | **평문 저장 금지** | `BCryptPasswordEncoder`를 거친 값만 |

**시드 데이터는 `dev` 프로필 전용이다.** 메뉴 3개와 재고 **100 / 50 / 3**, 그리고 점주 계정 1개. 마지막 재고 값이 3인 것은 우연이 아니라 **AC-09(마지막 한 잔)의 재현 조건**이다. 시드 블록마다 **자기가 심는 대상을 세는 가드**를 붙인다. 가드가 세는 대상과 심는 대상이 어긋나 매 기동 시드가 재실행되는 사고가 이 레포에서 실제로 한 번 있었다.

---

## 9. 인터페이스 요구사항

### 9-1. API

| 메서드, 경로 | 인증 | 하는 일 |
| --- | --- | --- |
| `POST /api/auth/login` | 익명 | 점주 로그인. Access 토큰 발급 |
| `POST /api/order` | 익명 | 주문 생성. 대기번호와 결제 금액 반환 |
| `GET /api/orders/{orderNumber}` | 익명 | **대기번호 단건 조회.** 손님이 자기 주문만 본다 |
| `GET /api/menus` | 익명 | 메뉴 목록. 재고 수량과 품절 여부 포함 |
| `GET /api/orders?status=` | **OWNER** | 주문 목록. `orderTime` 오름차순 FIFO, 상태 필터 |
| `PATCH /api/order/{orderId}/status` | **OWNER** | 상태 전이. 잘못된 전이는 409 |
| `POST /api/menus` | **OWNER** | 메뉴 등록 |
| `PUT /api/menus/{id}` | **OWNER** | 메뉴 수정 |
| `DELETE /api/menus/{id}` | **OWNER** | 메뉴 삭제 |
| `PATCH /api/stocks/{menuId}` | **OWNER** | 재고 조정 |
| `POST /api/upload/image` | **OWNER** | 이미지 업로드. 호스트 없는 상대경로 반환 |

**로그아웃은 백엔드 엔드포인트가 아니다.** BFF의 `app/api/auth/logout/route.ts`가 쿠키를 지우는 것으로 끝난다. FR-AUTH-12.

**이메일을 본문에 실어 주문 내역을 통째로 조회하는 경로는 존재하지 않는다.** 손님은 대기번호 단건 조회만 쓰고, 점주는 `GET /api/orders`를 쓴다. FR-KSK-09, FR-ADM-05.

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
| `PATCH /api/stocks/{menuId}` | **401** | ✅ |
| `POST /api/upload/image` | **401** | ✅ |

**CORS 허용 메서드에 `PATCH`가 포함돼야 한다.** 빠지면 주방 화면의 상태 변경과 관리자 화면의 재고 조정이 preflight에서 막힌다. CORS는 `SecurityConfig`의 `CorsConfigurationSource`가 소유한다. Security 필터체인이 `WebMvcConfigurer`의 CORS 설정보다 앞서 돌기 때문이다.

### 9-3. 검증 책임: 어느 쪽이 정본인가

같은 규칙이 백엔드와 BFF 양쪽에 구현돼 있다. **백엔드가 정본이고, BFF는 UX용 조기 차단이다.** 둘이 어긋나면 백엔드를 따르며, 규칙을 바꿀 때는 **백엔드를 먼저 고치고 BFF를 맞춘다.**

| 규칙 | 정본 (백엔드) | 조기 차단 (BFF/클라이언트) |
| --- | --- | --- |
| 주문 총 수량 1~100 | `OrderController.createOrder` | `app/api/order/route.ts` |
| 메뉴 가격 0~10,000,000 | `MenuController.createMenu` | `app/api/menu/route.ts` |
| 업로드 이미지만, 5MB 이하 | `FileUploadController.uploadImage` | `app/api/upload/route.ts` + 파일 선택 시 |
| 이메일 형식 | `@Email` (DTO) | 키오스크 화면의 정규식 |

> **BFF 검증을 지우면 UX가 나빠질 뿐 보안이 뚫리지는 않는다. 백엔드 검증을 지우면 뚫린다.** 이 방향을 헷갈리지 않는다.

### 9-4. BFF 규약

- 브라우저는 백엔드를 **직접 호출하지 않는다** (C-04). 이미지 업로드와 이미지 표시를 포함해 예외가 없다
- **토큰은 BFF가 `httpOnly` 쿠키로 보관하고 요청마다 헤더로 바꿔 붙인다** (FR-AUTH-09, FR-AUTH-11)
- 백엔드 API를 바꾸면 **BFF 핸들러도 같이 고친다.** 백엔드만 고치면 프론트가 조용히 깨진다
- 필드명 변환 규칙은 **핸들러마다 다르다.** 고치기 전에 해당 파일을 읽는다

### 9-5. 응답 규약

| 규칙 | 내용 |
| --- | --- |
| **응답 포맷** | 모든 API 응답은 `RsData<T>`다. raw `String`이나 raw record를 반환하지 않는다 |
| **예외 → HTTP** | `GlobalExceptionHandler`가 독점한다. 컨트롤러에서 try/catch로 상태코드를 만들지 않는다 |
| **목록 조회** | 결과가 비어도 **200 + 빈 배열**이다. 404가 아니다 |
| **단건 조회** | 대상이 없으면 **404**다. 단건에는 "빈 목록"이라는 개념 자체가 없다 |

주요 매핑은 이렇다. `NoSuchElementException` → 404, 검증 실패 계열 → 400, `InvalidOrderStatusTransitionException`과 `OutOfStockException` → **409**.

---

## 10. 비요구사항 (스코프 아웃)

**안 하는 이유를 남기는 것도 명세다.** 상세 근거는 [`PRODUCT.md §5`](PRODUCT.md).

| 안 하는 것 | 한 줄 이유 | 영구/유예 |
| --- | --- | --- |
| 실결제(PG) 연동 | 주제는 동시성이지 결제가 아니다. `CONFIRMED`를 "결제까지 끝난 상태"로 정의하고 모킹 | **영구** |
| 손님 회원가입, 마이페이지 | 매장 키오스크에 회원 개념은 제품적으로 틀렸다. 손님은 익명 | **영구** |
| 배송, 주소, 우편번호 | 이 레포는 배송 쇼핑몰이 아니다 | **영구** |
| 다국어(i18n) | 학습 주제와 무관 | **영구** |
| **손님이 스스로 주문을 취소하는 경로** | 대기번호만으로 취소하게 하면 **남의 대기번호로 남의 주문을 취소할 수 있다.** 대기번호는 PK 파생이라 단조 증가해 추측이 쉽고, 익명 손님 모델과 정면으로 부딪힌다. 손님은 카운터에서 말로 취소하고 바리스타가 누른다 | **영구** |
| **픽업하지 않은 주문의 폐기 상태** | 상태를 하나 더 만들면 전이 규칙, 테스트, 화면이 전부 늘어나는데 얻는 것은 통계뿐이다. 바리스타가 픽업완료로 눌러 정리한다. §2의 `COMPLETED` 정의 | **영구** |
| **고아 이미지 정리 배치** | 파일을 지우면 과거 주문 조회가 깨진다. 붙지 않은 파일이 남는 것은 그 규칙의 부작용으로 감수한다. FR-FILE-08 | **영구** |
| **오브젝트 스토리지** | 자격증명, 버킷 정책, SDK 의존성이 들어오는데 배우는 것이 주제와 무관하다. 로컬 디스크를 쓰고 유실을 감수한다. NFR-OPS-05 | **영구** |
| 재고 이력 테이블 | 현재 수량만 다룬다. FR-STK-09 | **영구** |
| 부하 테스트 도구(k6, Gatling) | 동시성에서 필요한 것은 **정확성**이지 처리량이 아니다 | **영구** |
| 분산 트랜잭션 / Saga | 서비스가 하나뿐이라 무대가 없다 | **영구** |
| Redis 캐싱 | 이 프로젝트의 Redis는 **분산 락** 용도다. 캐싱은 다른 주제 | **영구** |
| 모니터링 / APM, 무중단 배포 | 학습 프로젝트에 과하다 | **영구** |
| 메뉴별 소유자 분리(`Menu.owner` FK) | 1인 매장에서 실익이 없고 연쇄 수정 비용만 크다 | **영구** |
| WebSocket 실시간 푸시 | 주방은 폴링 3초로 충분하다 | 유예 |
| Refresh 토큰 | 만료 시 재로그인으로 충분하다. FR-AUTH-10 | 유예 |

---

## 11. 추적표

**각 요구사항을 무엇으로 검증하는가.** 진행 상태가 아니라 **검증 수단**의 목록이다.

**표에만 있고 여기에 없는 요구사항은 검증 계획이 없다는 뜻이다.** 요구사항을 추가하면 이 표에도 행을 추가한다.

| 요구사항 | 검증 수단 |
| --- | --- |
| FR-KSK-01, FR-KSK-02, FR-KSK-03, FR-KSK-07 / **AC-05** | `order/controller/OrderControllerTest` |
| FR-KSK-04, FR-KSK-05, FR-KSK-10 | 수동 + PR 스크린샷 |
| FR-KSK-06, FR-KSK-09 / **AC-13** | `OrderControllerTest`. 대기번호 단건 조회 + 이메일 조회 경로 부재 |
| FR-KSK-08 | 통합 테스트 + 수동 |
| FR-ORD-01 ~ 04, FR-ORD-13 / **AC-03** | `order/entity/OrderTest` (POJO) |
| FR-ORD-05, FR-ORD-06, FR-ORD-09 / **AC-01**, **AC-02** | `OrderTest`, `OrderControllerTest`. 가격 스냅샷 회귀 |
| FR-ORD-07, NFR-DATA-03 / **AC-04** | `OrderControllerTest` |
| FR-ORD-08 | `OrderControllerTest` |
| FR-ORD-10 | `OrderControllerTest` |
| FR-ORD-11, FR-STK-05 / **AC-07**, **AC-08** | 통합 테스트. 취소 복구와 이중 복구 금지 |
| FR-ORD-12, NFR-DATA-02 / **AC-06** | 통합 테스트. 아이템 일부 실패 시 전체 롤백 |
| FR-MNU-01, FR-MNU-02, FR-MNU-05, FR-MNU-07 | `menu/controller/MenuControllerTest`. 401 + 404 + `RsData` 포맷 |
| FR-MNU-03, FR-MNU-04 | `MenuControllerTest` |
| FR-MNU-06 | `OrderControllerTest` (AC-01과 동일) |
| FR-MNU-08 | 코드 리뷰. `Menu.email`이 인가 분기에 쓰이지 않는지 `grep` |
| FR-STK-01 | `stock/repository/StockRepositoryIntegrationTest` |
| FR-STK-02, FR-STK-03 / **AC-06** | 통합 테스트. 부분 차감 없음 |
| FR-STK-04, NFR-DATA-01, NFR-TEST-06 | `stock/entity/StockTest` (POJO) |
| FR-STK-06, NFR-DATA-04 | `StockTest` + 동시성 테스트 |
| FR-STK-07, FR-STK-08, FR-ADM-02 | 통합 테스트 + 수동 |
| FR-AUTH-01 ~ 03 | 로그인 통합 테스트. 성공/실패, 해시 저장 확인 |
| FR-AUTH-04, FR-AUTH-06 / **AC-12** | 통합 테스트. 토큰 없이 점주 API 호출 시 401 |
| FR-AUTH-05, NFR-SEC-06 | `grep` + 코드 리뷰. 요청 본문 이메일로 권한을 확인하는 코드가 없다 |
| FR-AUTH-07, FR-AUTH-08, FR-AUTH-10 | 코드 리뷰. `JwtTokenProvider` 클레임 |
| FR-AUTH-09, FR-AUTH-11, FR-AUTH-12 | 코드 리뷰 + 수동. 브라우저 개발자도구에서 토큰이 보이지 않는다 |
| FR-KIT-01, FR-KIT-05 / **AC-14** | 수동. `/kitchen` 폴링 3초 |
| FR-KIT-02, FR-KIT-03 | `OrderControllerTest`. FIFO 정렬 + 빈 배열 200 |
| FR-KIT-04, FR-KIT-06 | `OrderControllerTest` + 수동 |
| FR-ADM-01, FR-ADM-03, FR-ADM-04, FR-ADM-05 | 수동 + PR 스크린샷. 화면 3분할 |
| FR-FILE-01 ~ 03 | 수동. 파일명 UUID 규칙은 `file/controller/FileUploadControllerTest` |
| FR-FILE-04 | 통합 테스트. 토큰 없이 업로드 시 401 |
| FR-FILE-05, NFR-OPS-02 | `grep` + 브라우저 네트워크 탭. 백엔드를 직접 부르는 경로가 없다 |
| FR-FILE-06 | 수동. 시드 이미지가 clone 직후에도 보인다 |
| FR-FILE-07 | `FileUploadControllerTest`. 응답이 `/uploads/{UUID}.{확장자}` 형태. 추가로 `menu.img_url`에 `http`로 시작하는 행이 0건 |
| FR-FILE-08 | 코드 리뷰. 메뉴 삭제 경로에 파일 삭제가 없다 |
| NFR-CON-01, NFR-CON-03 / **AC-09** | 동시성 테스트. `ExecutorService`, MockMvc 미사용, 세 전략 각각 |
| NFR-CON-02 | `git log` |
| NFR-CON-04 / **AC-10** | 동시성 테스트. 다중 메뉴 반대 순서 |
| NFR-CON-05 | `grep`. 컨트롤러에 `@Transactional`이 없다 |
| NFR-CON-06 / **AC-11** | 동시성 테스트 + 문서 |
| NFR-CON-07 | 문서 리뷰. 실측 수치 표 |
| NFR-SEC-01, NFR-SEC-03 | 코드 리뷰. `.env.example`에 키 이름만, CORS 출처가 환경변수 |
| NFR-SEC-02 | 수동. `prod` 기동 시 바인딩 파라미터가 로그에 없다 |
| NFR-SEC-04, NFR-SEC-05 | 코드 리뷰. `SecurityConfig` 주석과 키 길이 |
| NFR-OPS-01 | 기동 확인. `ddl-auto` 없이 Flyway로 스키마가 만들어진다 |
| NFR-OPS-03 | 수동 기동 확인 |
| NFR-OPS-04 / **AC-14** | 배포 환경에서 수동 |
| NFR-OPS-05, C-07 | 문서 리뷰 |
| NFR-TEST-01 | CI 로그. 백엔드 테스트 + 프론트 lint/build |
| NFR-TEST-02, NFR-TEST-03, NFR-TEST-04 | 코드 리뷰 |
| NFR-TEST-05 | PR 리뷰 |
| NFR-UX-01, NFR-UX-04 | 수동. 주방은 갱신되고 손님 조회는 갱신되지 않는다 |
| NFR-UX-02 | 코드 리뷰 |
| NFR-UX-03 | 코드 리뷰. BFF 핸들러 |

---

> 요구사항을 추가, 변경할 때는 **ID를 새로 부여하고 §11 추적표에도 행을 추가한다.**
> **이 문서는 완성된 시스템을 진술한다.** 지금 어디까지 됐는지는 [`ROADMAP.md`](ROADMAP.md)가 소유한다.
