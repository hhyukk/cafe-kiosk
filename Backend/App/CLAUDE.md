# Backend (Spring Boot)

Spring Boot 4.0.0 / Java 21 / PostgreSQL 16 / JPA / Lombok.
**이 디렉토리가 Gradle 루트다.** 모든 gradle 명령은 여기서 실행한다.

포매터·린터 설정은 없다 (Checkstyle/Spotless 미도입). 주변 코드 스타일에 맞춘다.

제품 방향("왜")은 [`docs/PRODUCT.md`](../../docs/PRODUCT.md), 로드맵·진행 상태는 [`docs/ROADMAP.md`](../../docs/ROADMAP.md)에 있다. 배송몰 시절의 잔재(`Order.address`/`postcode`)는 **Phase 0에서 제거했다** — 새 코드에 배송 개념을 넣지 않는다. 손님은 `Order.orderNumber`(대기번호)를 받아간다.

## 패키지 구조 — 레이어별이 아니라 기능(feature)별

```
com.cafekiosk
├── menu/     controller · service · repository · entity · dto
├── order/    controller · service · repository · entity · dto · exception
├── stock/    entity · repository        (service/controller 없음 — 아래 참고)
├── file/     controller
└── global/   횡단 관심사만
      ├── config/WebConfig
      ├── globalExceptionHandler/GlobalExceptionHandler
      ├── initData/BaseInitData
      ├── jpa/entity/BaseEntity
      ├── rsData/RsData
      └── springDoc/SpringDocConfig
```

새 도메인을 추가할 땐 이 패턴을 따른다 — 최상위에 feature 패키지를 만들고 그 안에 레이어를 둔다. 여러 feature가 공유하는 것만 `global/`로 간다.

## ⚠️ `stock/`은 아직 죽은 도메인이다

`Stock` 엔티티와 리포지토리는 있지만 **`OrderService`가 `Stock`을 한 번도 참조하지 않는다.** 그래서 **재고가 0이어도 주문이 무한히 들어간다.** 감소·증가 메서드조차 없다.

이건 미완성이지 설계 의도가 아니다. **재고 차감은 Phase 2에서 `OrderService`가 하게 된다** — 그때 `Stock.decrease(int)`가 추가되고, 재고 부족은 `OutOfStockException` → `409`가 된다. 그리고 Phase 3에서 이 차감 로직이 **동시성 제어(비관적 락 → 낙관적 락 → Redisson 분산 락)의 무대**가 된다. 이게 이 프로젝트의 목적지다.

`docker-compose.yml`의 Redis도 그때 처음 쓰인다. 지금은 컨테이너만 떠 있고 `build.gradle.kts`에 의존성조차 없다.

## 응답 · 예외

**`RsData<T>`** (`global/rsData/RsData`) — `record RsData<T>(String resultCode, String message, T data)`. 신규 API 응답은 이걸로 감싼다.

> 기존 `MenuController` 일부는 raw `String` / `List<>`를 반환해 일관성이 깨져 있다. 새 코드에서 따라하지 말 것.

**예외 → HTTP 매핑은 `GlobalExceptionHandler`에서** 한다. 컨트롤러에서 try/catch로 상태코드를 만들지 않는다.
예: `InvalidOrderStatusTransitionException` → `409 CONFLICT`.

## 불변식은 엔티티가 소유한다

주문 상태 전이 규칙은 `Order` 엔티티가 소유한다 — `startPreparing()` / `markReady()` / `complete()` / `cancel()`. 잘못된 전이면 엔티티가 `InvalidOrderStatusTransitionException`을 던진다.

전이 규칙을 서비스 레이어로 빼지 말 것. 서비스는 엔티티를 조회해서 위 메서드를 호출하기만 한다.

**금액도 마찬가지다.** 주문 총액은 `Order.addOrderItem()`으로만 늘어나고, 가격 스냅샷은 `OrderItem` 생성자가 메뉴에서 직접 복사한다. 서비스가 `totalPrice += price * count`를 계산하거나 호출자가 스냅샷 가격을 넘겨주는 식으로 짜지 않는다 — 총액이 아이템과 어긋날 수 있는 경로 자체를 없애는 게 목적이다.

**대기번호(`Order.orderNumber`)는 PK에서 파생한다** — `assignOrderNumber()`를 INSERT 이후에 호출한다. PK는 DB가 채번하므로 전역 유일하고 단조 증가한다. "오늘 주문 수 + 1" 같은 방식으로 바꾸지 말 것. 조회와 삽입 사이에 번호가 겹치는 경쟁이 생기는데, 동시성은 Phase 3에서 **재고**를 대상으로 의도적으로 다룰 주제이지 대기번호에서 실수로 만들 문제가 아니다.

**이 원칙은 상태 전이에만 해당하는 게 아니라 이 레포의 설계 규칙이다.** Phase 2에서 추가될 `Stock.decrease(int)`도 마찬가지로 **엔티티가 스스로 재고 부족을 판단해서 던진다.** 서비스가 `if (stock.getQuantity() < count)`를 검사하는 식으로 짜지 않는다.

`OrderStatus`: `PENDING`(예약, 미사용) → `CONFIRMED` → `IN_PROGRESS` → `READY` → `COMPLETED`, 그리고 `CANCELLED`.

> `CONFIRMED`는 "결제까지 끝난 상태"로 정의한다. **실결제(PG) 연동은 의도적으로 스코프 아웃했다** — 이유는 `docs/PRODUCT.md` 참고.

## 주문 조회 응답 · 주문 목록 API (Phase 1에서 채움 — 완료)

주문 조회 응답(`OrderDto.OrderSummary`)에 `orderId`·`orderNumber`·`status`·`orderTime`·`totalPrice`가 모두 노출된다. 점주/주방용 전체 주문 목록 API도 있다 — `GET /api/orders?status=IN_PROGRESS`(상태 없으면 전체, `orderTime` 오름차순 FIFO).

이걸로 주방 화면(`/kitchen`)을 만들 수 있게 됐다. **Phase 1의 남은 작업은 Spring Security 도입과 프론트 화면 3분할이다** — `docs/ROADMAP.md` 참고.

## ⚠️ dev 프로필은 DB를 매번 초기화한다

`application.yml`에 `ddl-auto: create`가 걸려 있다. **백엔드를 재시작할 때마다 스키마가 드롭·재생성되고 `BaseInitData`가 시드를 다시 심는다.** 로컬에서 만든 주문 데이터가 사라졌다면 버그가 아니라 이것 때문이다.

설정은 `application.yml`(공통, `.env` import) / `application-dev.yml`(기본 활성) / `application-test.yml`로 나뉜다.

## 테스트

**통합 테스트는 `support/AbstractIntegrationTest`를 상속한다.** 상속만 하면 PostgreSQL Testcontainer에 연결된 Spring 컨텍스트가 준비된다.

- **`@Testcontainers` / `@Container`를 붙이지 말 것.** 이 조합은 테스트 클래스가 바뀔 때마다 컨테이너를 재시작해서, `@DynamicPropertySource`가 잡아둔 포트를 무효화한다. 그래서 베이스 클래스는 static 블록으로 컨테이너를 직접 띄워 JVM당 1개만 유지한다. (자세한 이유는 그 파일 주석에 있다.)
- `@AutoConfigureMockMvc`, `@Transactional`은 베이스에 없다. 필요한 서브클래스가 각자 선언한다.
- 상태 전이 같은 순수 로직은 Spring 컨텍스트 없이 POJO 단위 테스트로 쓴다 — `order/entity/OrderTest` 참고.
- JUnit 5 + AssertJ. `@DisplayName`은 한국어 문장, 메서드명은 `should_정상흐름_전이는_성공한다()` 스타일.

```bash
./gradlew test    # Docker 데몬이 떠 있어야 한다 (Testcontainers)
```

`OrderControllerTest`에 있던 **`@BeforeEach` 두 개**(`setup()` / `setUp()` — 이름만 대소문자 차이)는 Phase 0에서 하나로 합쳤다. JUnit 5는 둘 다 실행하고 순서를 보장하지 않아 매 테스트마다 메뉴가 중복 생성되고 있었다.

**가격 스냅샷 테스트**(`OrderControllerTest`)는 이 레포의 회귀 방지선이다 — 메뉴 가격을 올린 뒤 과거 주문 금액이 그대로인지 확인한다. `getOrderList`가 `orderItem.getOrderPrice()` 대신 `getMenu().getMenuPrice()`를 읽는 순간 이 테스트가 잡아낸다.

Phase 3에서 쓸 **동시성 테스트**는 `ExecutorService`로 서비스를 직접 호출한다(MockMvc를 거치지 않는다). `AbstractIntegrationTest`가 `@AutoConfigureMockMvc`를 베이스에 두지 않는 이유가 이것이다.

## API 문서

springdoc-openapi. 서버 기동 후 http://localhost:8080/swagger-ui.html

API 경로 컨벤션이 통일되어 있지 않다 — `OrderController`는 메서드마다 풀패스(`/api/order`), `MenuController`는 클래스 레벨 `@RequestMapping` + `/modify/{id}` 같은 동사형 경로를 쓴다. 새 컨트롤러는 클래스 레벨 `@RequestMapping` + REST 스타일(동사 대신 HTTP 메서드) 쪽을 따르는 게 좋다.
