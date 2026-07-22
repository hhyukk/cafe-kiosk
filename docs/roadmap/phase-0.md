# Phase 0: 정체성 정리 + 결함 청산  ✅ 완료

> [← 로드맵 인덱스](../ROADMAP.md), [배경과 "왜"](../PRODUCT.md)
> 기준: 현재 `main` 코드 / 갱신: 2026-07-22

---

## 왜 이게 먼저였나

새 기능을 얹기 전에, 코드가 **배송 쇼핑몰과 매장 키오스크 사이에서 어느 쪽도 아닌 상태**를 끝내야 했다. 백엔드 상태머신은 매장 모델(`CONFIRMED → IN_PROGRESS → READY → COMPLETED`)인데 프론트는 주소, 우편번호를 받고 "다음 날 배송을 시작합니다"를 띄우고 있었다.

그리고 **가격 스냅샷 부재는 시간이 갈수록 고치기 어려워지는 종류의 결함**이었다. 주문 데이터가 쌓인 뒤엔 소급 마이그레이션이 필요해진다. 그래서 기능이 아니라 이것부터 했다.

정체성 진단의 전문은 [`PRODUCT.md §1`](../PRODUCT.md)에 있다.

---

## 한 것

여기서 충족된 요구사항은 **FR-ORD-05, FR-ORD-06, FR-ORD-07, FR-ORD-09, FR-MNU-06, NFR-DATA-03**이고 인수 기준으로는 **AC-01, AC-02, AC-04**다. ID의 진술 자체는 [`REQUIREMENTS.md §5`](../REQUIREMENTS.md)에 있다.

### 도메인

- `Order`에서 `address`, `postcode` **제거**
- `Order.orderNumber` 추가. 손님이 받아가는 **대기번호**. PK에서 파생하며 `String.format("%04d", id)` 형태다. `assignOrderNumber()`를 INSERT 이후에 호출한다. FR-ORD-07, NFR-DATA-03
- `Order.totalPrice` 추가. 주문 시점 총액. FR-ORD-06
- `OrderItem.orderPrice` 추가. **주문 시점 가격 스냅샷.** FR-ORD-05, FR-ORD-09, FR-MNU-06

**소유권을 확정했다.** 총액 합산은 `Order.addOrderItem()`이, 가격 스냅샷은 `OrderItem` 생성자가 소유한다. **서비스가 금액을 계산하지 않는다.** 총액이 아이템과 어긋날 수 있는 경로 자체를 없애는 게 목적이다.

대기번호를 PK에서 파생시킨 이유도 같은 맥락이다. "오늘 주문 수 + 1"은 조회와 삽입 사이에 경쟁이 생겨 번호가 겹칠 수 있다. **동시성은 Phase 3에서 재고를 대상으로 의도적으로 다룰 주제이지, 대기번호에서 실수로 만들 문제가 아니다.**

### 결함 6건 청산

| 결함 | 왜 위험했나 |
| --- | --- |
| `createMenu`의 `@Valid` 누락 | DTO의 `@NotBlank`/`@Email`이 전부 무력화. null이 통과해 DB 제약 위반 500 |
| `OrderControllerTest`의 `@BeforeEach` 두 개 (`setup()` / `setUp()`) | 이름만 대소문자 차이. JUnit 5는 **둘 다 실행하고 순서를 보장하지 않는다.** 매 테스트 메뉴가 중복 생성됨. 병합 사고 흔적 |
| `BaseInitData` 가드가 `customerRepository.count()` | 정작 Customer를 안 만드니 count가 항상 0 → 매 기동 재실행. `ddl-auto: create` 덕에 안 드러났지만 **`update`로 바꾸는 순간 메뉴가 무한 증식**한다 |
| `starter-validation` 중복 선언 | |
| 가격 스냅샷 부재 | 메뉴 가격을 고치면 **과거 주문 금액이 소급 변경**된다 |
| 죽은 mock 라우트 `api/products/route.ts` | 하드코딩 원두 배열을 반환하는데 아무도 호출하지 않는다 |

### 프론트

- 주소, 우편번호 입력과 배송 안내 문구 제거
- 주문 완료 시 대기번호 표시
- **덤**: Next 16 라우트 핸들러의 `params` 시그니처가 옛 형태라 `npm run build`가 실패하던 것 수정. **CI가 프론트를 안 돌려서 드러나지 않았다.** 이 교훈이 Phase 4의 "CI에 프론트 추가"로 이어진다

---

## 시작 시점에 이미 충족돼 있던 요구사항

**[`REQUIREMENTS.md`](../REQUIREMENTS.md)는 완성된 시스템을 진술하므로, 그중 일부는 이 프로젝트를 다시 잡기 전부터 이미 돌아가고 있었다.** 아래는 어느 Phase의 작업 항목도 아니다. 로드맵에서 빠뜨린 것이 아니라 **이미 되어 있어서 할 일이 없는 것**이라는 뜻으로 여기 적어둔다.

| 요구사항 | 어디서 이미 충족되나 |
| --- | --- |
| FR-KSK-01, FR-KSK-02, FR-KSK-03 | 손님이 로그인 없이 이메일만으로 주문하고 대기번호와 금액을 받는다. 주소 제거와 대기번호는 Phase 0이 마무리했다 |
| FR-KSK-07 / **AC-05** | 총 수량 1~100 검증이 `OrderController`와 BFF `api/order/route.ts` 양쪽에 있다 |
| FR-MNU-01, FR-MNU-03, FR-MNU-04 | 익명 메뉴 조회, 가격 범위, 필수 필드 검증. `@Valid` 누락은 Phase 0이 고쳤다 |
| FR-ORD-01, FR-ORD-02, FR-ORD-03, FR-ORD-04 / **AC-03** | 주문 상태머신. 전이 규칙을 `Order` 엔티티가 소유하고 `OrderTest` 8개가 지킨다. **이 레포에서 가장 잘 설계된 부분이다** |
| FR-ORD-10 | 없는 메뉴 ID로 주문하면 400 |
| FR-FILE-01, FR-FILE-02, FR-FILE-03 | 업로드, 이미지 5MB 제한, UUID 파일명 |
| FR-FILE-06 | 클래스패스 시드 이미지와 런타임 업로드 파일의 이중 서빙. `WebConfig.addResourceHandlers` |
| FR-FILE-08 | 업로드 파일을 삭제하지 않는다. **부작위 요구사항이라 만들 코드가 없다** |

**FR-STK-01과 NFR-TEST-02, NFR-TEST-06도 같은 성격이다.** `Menu ↔ Stock` 1:1과 `AbstractIntegrationTest` 상속 규약, POJO 단위 테스트는 이미 자리를 잡고 있다. 다만 이 셋은 뒤 Phase에서 대상이 늘어나므로 그쪽 표에도 실려 있다.

> 이 절이 있는 이유는 **추적을 양방향으로 닫기 위해서다.** 요구사항 ID를 `roadmap/`에서 `grep`했을 때 아무 데도 안 나오면, 그것이 "빠뜨린 것"인지 "이미 된 것"인지 구분할 방법이 없다.

---

## 완료 기준 (달성)

- [x] 메뉴 가격을 15,000 → 99,000원으로 올려도 **과거 주문 금액이 그대로다.** `OrderControllerTest`의 가격 스냅샷 테스트가 증명한다. 이 테스트는 이 레포의 회귀 방지선이다(`getOrderList`가 `orderItem.getOrderPrice()` 대신 `getMenu().getMenuPrice()`를 읽는 순간 잡아낸다)
- [x] `./gradlew test` 통과
- [x] 레포 전체에 "배송"이라는 단어가 없다

---

## 여기서 남긴 것

| 남긴 결함 | 청산 시점 |
| --- | --- |
| `localhost:8080` 하드코딩 9곳 | [Phase 4](phase-4.md). 배포 시점에 한꺼번에 |
| 재고가 주문과 연결돼 있지 않음 | [Phase 2](phase-2.md) |
| 인가가 요청 본문 이메일 문자열 비교뿐 | [Phase 1](phase-1.md) |

---

## Phase 밖에서 처리한 것

**커밋 `4f81c28` `fix: 이미지 URL에 호스트를 붙이지 않도록 수정`.** FR-FILE-07을 충족시킨 작업이고, 원래 [Phase 4](phase-4.md)의 몫이었는데 [`REQUIREMENTS.md`](../REQUIREMENTS.md)를 쓰다가 발견해 Phase 1 도중에 먼저 고쳤다.

**미루지 않은 이유는 가격 스냅샷과 같다.** 코드가 아니라 **DB 행에 박히는 결함**이라 시간이 갈수록 되돌리기 어려워진다. 코드의 하드코딩은 `grep`으로 걷어낼 수 있지만 이미 저장된 `menu.img_url`은 그렇지 않다. 지금은 `ddl-auto: create`라 매 기동 드롭되지만, Phase 4에서 Flyway가 들어오는 순간 그 데이터가 자산이 된다.

로드맵 순서를 어긴 것을 기록으로 남긴다. **순서를 지키는 것보다 되돌릴 수 없게 되는 것을 막는 쪽이 우선이라는 판단이었다.**
