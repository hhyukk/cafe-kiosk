# Phase 0 — 정체성 정리 + 결함 청산  ✅ 완료

> [← 로드맵 인덱스](../ROADMAP.md) · [배경과 "왜"](../PRODUCT.md)

---

## 왜 이게 먼저였나

새 기능을 얹기 전에, 코드가 **배송 쇼핑몰과 매장 키오스크 사이에서 어느 쪽도 아닌 상태**를 끝내야 했다. 백엔드 상태머신은 매장 모델(`CONFIRMED → IN_PROGRESS → READY → COMPLETED`)인데 프론트는 주소·우편번호를 받고 "다음 날 배송을 시작합니다"를 띄우고 있었다.

그리고 **가격 스냅샷 부재는 시간이 갈수록 고치기 어려워지는 종류의 결함**이었다 — 주문 데이터가 쌓인 뒤엔 소급 마이그레이션이 필요해진다. 그래서 기능이 아니라 이것부터 했다.

정체성 진단의 전문은 [`PRODUCT.md §1`](../PRODUCT.md)에 있다.

---

## 한 것

### 도메인

- `Order`에서 `address`/`postcode` **제거**
- `Order.orderNumber` 추가 — 손님이 받아가는 **대기번호**. PK에서 파생(`String.format("%04d", id)`)하며 `assignOrderNumber()`를 INSERT 이후에 호출한다
- `Order.totalPrice` 추가 — 주문 시점 총액
- `OrderItem.orderPrice` 추가 — **주문 시점 가격 스냅샷**

**소유권을 확정했다** — 총액 합산은 `Order.addOrderItem()`이, 가격 스냅샷은 `OrderItem` 생성자가 소유한다. **서비스가 금액을 계산하지 않는다.** 총액이 아이템과 어긋날 수 있는 경로 자체를 없애는 게 목적이다.

대기번호를 PK에서 파생시킨 이유도 같은 맥락이다 — "오늘 주문 수 + 1"은 조회와 삽입 사이에 경쟁이 생겨 번호가 겹칠 수 있다. **동시성은 Phase 3에서 재고를 대상으로 의도적으로 다룰 주제이지, 대기번호에서 실수로 만들 문제가 아니다.**

### 결함 6건 청산

| 결함 | 왜 위험했나 |
| --- | --- |
| `createMenu`의 `@Valid` 누락 | DTO의 `@NotBlank`/`@Email`이 전부 무력화. null이 통과해 DB 제약 위반 500 |
| `OrderControllerTest`의 `@BeforeEach` 두 개 (`setup()` / `setUp()`) | 이름만 대소문자 차이. JUnit 5는 **둘 다 실행하고 순서를 보장하지 않는다** — 매 테스트 메뉴가 중복 생성됨. 병합 사고 흔적 |
| `BaseInitData` 가드가 `customerRepository.count()` | 정작 Customer를 안 만드니 count가 항상 0 → 매 기동 재실행. `ddl-auto: create` 덕에 안 드러났지만 **`update`로 바꾸는 순간 메뉴가 무한 증식**한다 |
| `starter-validation` 중복 선언 | |
| 가격 스냅샷 부재 | 메뉴 가격을 고치면 **과거 주문 금액이 소급 변경**된다 |
| 죽은 mock 라우트 `api/products/route.ts` | 하드코딩 원두 배열을 반환하는데 아무도 호출하지 않는다 |

### 프론트

- 주소·우편번호 입력과 배송 안내 문구 제거
- 주문 완료 시 대기번호 표시
- **덤**: Next 16 라우트 핸들러의 `params` 시그니처가 옛 형태라 `npm run build`가 실패하던 것 수정. **CI가 프론트를 안 돌려서 드러나지 않았다** — 이 교훈이 Phase 4의 "CI에 프론트 추가"로 이어진다

---

## 완료 기준 (달성)

- [x] 메뉴 가격을 15,000 → 99,000원으로 올려도 **과거 주문 금액이 그대로다** — `OrderControllerTest`의 가격 스냅샷 테스트가 증명한다. 이 테스트는 이 레포의 회귀 방지선이다(`getOrderList`가 `orderItem.getOrderPrice()` 대신 `getMenu().getMenuPrice()`를 읽는 순간 잡아낸다)
- [x] `./gradlew test` 통과
- [x] 레포 전체에 "배송"이라는 단어가 없다

---

## 여기서 남긴 것

| 남긴 결함 | 청산 시점 |
| --- | --- |
| `localhost:8080` 하드코딩 9곳 | [Phase 4](phase-4.md) — 배포 시점에 한꺼번에 |
| 재고가 주문과 연결돼 있지 않음 | [Phase 2](phase-2.md) |
| 인가가 요청 본문 이메일 문자열 비교뿐 | [Phase 1](phase-1.md) |
