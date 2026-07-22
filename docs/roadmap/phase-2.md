# Phase 2: 재고를 주문에 연결  ⬜ 예정

> [← 로드맵 인덱스](../ROADMAP.md), [배경과 "왜"](../PRODUCT.md)
> 기준: 현재 `main` 코드 / 갱신: 2026-07-22

---

## 왜 지금인가

**Phase 3(동시성)의 무대를 세우는 단계다.** 재고 차감이 없으면 깨뜨릴 것도 없다.

그리고 재고는 화면에서 품절이 보여야 의미가 있으므로, 화면 3분할이 끝난 Phase 1 다음이 자리다.

---

## 현재 코드 상태

- `stock/entity/Stock`은 **엔티티와 리포지토리만 있는 죽은 도메인**이다. `service`도 `controller`도 없고, `decrease`/`increase` 메서드조차 없다. 필드는 `menu`(1:1)와 `quantity`뿐.
- **`OrderService`가 `Stock`을 단 한 번도 참조하지 않는다.** 그래서 **재고가 0이어도 주문이 무한히 들어간다.**
- `StockRepository.findByMenuId(Long)`는 이미 있다. 통합 테스트도 2개 있다.
- 메뉴 조회 응답(`MenuDto.MenuListResponse`)에 **재고 정보가 전혀 없다.** 프론트가 품절을 표시할 방법이 없다.
- `BaseInitData`의 시드 재고는 **100 / 50 / 3**이다. 마지막 "브라질 산토스"만 3개인 건 우연이 아니라 **Phase 3의 경쟁 상황을 재현하기 좋은 값**이다.
- **컨트롤러 메서드에 `@Transactional`이 일곱 곳 붙어 있다.** `OrderController`의 `createOrder:44`, `orderList:64`, `getOrders:88`, `changeStatus:112`와 `MenuController`의 `getMenus:36`, `createMenu:85`, `deleteMenu:101`. 이 Phase의 첫 PR에서 전부 걷어낸다. **조회 메서드인 `orderList`와 `getMenus`를 빠뜨리기 쉽다.** 완료 기준이 "하나도 없다"이므로 일곱 곳을 다 세야 한다.

---

## 작업 단위

**`요구사항` 열이 이 Phase가 소화하는 것의 정본이다.** ID의 진술 자체는 [`REQUIREMENTS.md §5`](../REQUIREMENTS.md), 검증 수단은 그 문서 §11에 있다.

| # | PR | 요구사항 | 건드리는 파일 | 선행 |
| --- | --- | --- | --- | --- |
| 1 | `refactor: 컨트롤러의 @Transactional 제거하고 트랜잭션 경계를 서비스로 내린다` | NFR-CON-05 | `OrderController`, `MenuController` | 없음 |
| 2 | `feat: Stock에 재고 증감 메서드 추가` | FR-STK-04, FR-STK-06<br>NFR-DATA-01, NFR-DATA-04, NFR-TEST-06 | `stock/entity/Stock`, 신설 `stock/exception/OutOfStockException`, 신설 `stock/entity/StockTest` | 없음 |
| 3 | `feat: 주문 생성 시 재고를 차감한다` | FR-STK-02, FR-STK-03, FR-ORD-12<br>NFR-DATA-02, **AC-06** | `order/service/OrderService`, 신설 `stock/service/StockService`, `GlobalExceptionHandler`, `Backend/App/CLAUDE.md` | 1, 2 |
| 4 | `feat: 주문 취소 시 재고를 복구한다` | FR-STK-05, FR-ORD-11<br>**AC-07**, **AC-08** | `OrderService.changeStatus` | 3 |
| 5 | `feat: 메뉴 조회 응답에 재고와 품절 여부 노출` | FR-STK-07, FR-KSK-08 | `menu/dto/MenuDto`, `MenuController`, 프론트 `page.tsx` 품절 표시 | 3 |
| 6 | `feat: 점주 재고 조정 API 추가` | FR-STK-08, FR-ADM-02 | 신설 `stock/controller/StockController`, 프론트 `/admin` | 3, 5 |

**FR-STK-01은 이미 충족돼 있다.** `Menu ↔ Stock` 1:1 관계와 `StockRepositoryIntegrationTest` 2개가 그것이다. **FR-STK-09는 의도적 비요구사항**이라 소화할 PR이 없다. 재고 이력을 만들지 않는다는 진술이지 작업이 아니다.

### PR별 메모

**#1 이 Phase에서 가장 중요한 PR이다**

컨트롤러의 `@Transactional`은 트랜잭션 경계를 컨트롤러까지 끌어올린다. 그러면 서비스의 `@Transactional`은 **바깥 트랜잭션에 합류**할 뿐 자기 경계를 갖지 못한다.

Phase 3의 낙관적 락 재시도는 **트랜잭션이 롤백된 뒤 새 트랜잭션에서** 다시 시도해야 성립하는데, 컨트롤러가 트랜잭션을 쥐고 있으면 그게 구조적으로 불가능하다. **재고를 붙이기 전에 걷어낸다.** 나중에 하면 재고 로직까지 함께 흔들어야 한다.

이 PR은 순수 리팩터링이라 기존 테스트가 그대로 통과해야 한다. 통과하지 않으면 **암묵적으로 컨트롤러 트랜잭션에 기대고 있던 코드가 있다는 뜻**이므로, 그걸 찾는 게 이 PR의 수확이다. 대표적으로 뷰 렌더링 시점의 lazy 로딩이 그렇다.

**조회 메서드를 빠뜨리지 않는다.** `OrderController.orderList`와 `MenuController.getMenus`에도 붙어 있다. 쓰기 메서드만 훑으면 두 곳이 남고, 그러면 완료 기준을 만족하지 못한다.

**#2 재고 차감은 엔티티가 소유한다**

```java
public void decrease(int count) { ... }   // 부족하면 스스로 OutOfStockException
public void increase(int count) { ... }
```

`Backend/App/CLAUDE.md`가 명시하는 이 레포의 설계 규칙이다. **서비스가 `if (stock.getQuantity() < count)`를 검사하지 않는다.** `Order.startPreparing()`이 상태 전이 규칙을 스스로 지키는 것과 같은 원칙이다.

테스트는 Spring 컨텍스트 없이 POJO 단위 테스트로 쓴다. `order/entity/OrderTest`가 그 스타일의 본보기다.

**#3 차감 연결**

`OutOfStockException` → `409 CONFLICT`를 `GlobalExceptionHandler`에 매핑한다. `InvalidOrderStatusTransitionException`이 이미 같은 자리에 있으니 그 옆에 붙인다.

`order` 패키지가 `stock`을 호출하는 방향이 생긴다. `stock/service/StockService`를 신설하고 `OrderService`가 그걸 호출한다. `OrderService`가 `StockRepository`를 직접 잡지 않는다.

**이 PR에서 `Backend/App/CLAUDE.md`도 같이 고친다.** "⚠️ `stock/`은 아직 죽은 도메인이다" 절이 이 PR로 거짓이 된다.

**#4 취소 복구**

`changeStatus(orderId, CANCELLED)`에서 주문 아이템을 순회하며 재고를 되돌린다. **`CANCELLED`로의 전이가 성공했을 때만** 복구한다. 전이 검증은 여전히 `Order.cancel()`이 하고, 그게 던지면 복구도 일어나지 않아야 한다.

`COMPLETED` 이후 취소는 `Order.cancel()`이 이미 막고 있으므로 이중 복구는 발생하지 않는다. **그 사실을 테스트로 확인한다.**

**#5 품절 표시**

메뉴 목록 응답에 `stockQuantity`와 `soldOut`을 넣는다. 프론트는 품절 배지 + 담기 버튼 비활성.
`Menu ↔ Stock`이 1:1 lazy라 목록 조회에서 N+1이 생긴다. 메뉴는 수가 적어 지금은 허용하되, **주석으로 남긴다**(`getOrdersByStatus`가 이미 같은 방식으로 부채를 표시해 뒀다).

**#6 재고 조정**

점주가 재고를 채워 넣는 API. `ROLE_OWNER` 전용이다(Phase 1에서 만든 인가를 그대로 쓴다). 응답은 `RsData<T>`.

**최종 수량이 아니라 바꿀 양을 받는다.** FR-STK-08. 최종 수량을 받으면 `Stock`에 수량을 대입하는 세 번째 경로가 생기고 그 경로만 음수 검사를 따로 해야 하는데, 그건 FR-STK-04와 정면으로 부딪힌다. 델타로 받으면 `#2`에서 만든 `increase`, `decrease`를 그대로 쓰고 음수 금지가 한 곳에서만 지켜진다. 결과가 음수가 되는 조정은 `OutOfStockException`을 타고 **409**로 나간다.

점주 화면이 실사 수량을 다루고 싶다면 **화면이 차이를 계산해서 보낸다.** 불변식을 지키는 쪽은 서버다.

---

## 함정

- **PR #1을 건너뛰면 Phase 3에서 되돌아와야 한다.** 순서를 지킨다.
- **재고 부족 시 주문 전체가 실패해야 한다.** 아이템 5개 중 3번째에서 재고가 모자라면 앞의 2개 차감도 롤백돼야 한다. 서비스 트랜잭션이 하나면 자동으로 되지만, **테스트로 확인한다.**
- **`dev` 프로필은 재시작마다 DB를 드롭한다**(`ddl-auto: create`). 재고를 다 소진시켜 놓고 재시작하면 시드값(100/50/3)으로 돌아온다. 버그가 아니다.
- **여기서 통과하는 건 단일 스레드 테스트다.** 재고 3개에 4개 주문이 거절되는 걸 확인해도 **동시성은 하나도 해결되지 않았다.** 스레드를 열 개로 늘리는 순간 같은 코드가 무너진다. 그게 Phase 3의 출발점이다.

---

## 완료 기준

- [ ] 재고 3개짜리 메뉴를 4개 주문 → **409**, 재고는 3 그대로 (부분 차감 없음)
- [ ] 3개 주문에 성공하면 재고 0, 키오스크 화면에 **품절 표시**
- [ ] 그 주문을 취소하면 재고가 3으로 복구된다
- [ ] `COMPLETED` 주문을 취소 시도하면 409이고, 재고는 **복구되지 않는다**
- [ ] 컨트롤러에 `@Transactional`이 하나도 없다. 시작 시점 기준 일곱 곳이다
- [ ] `Backend/App/CLAUDE.md`의 "죽은 도메인" 절이 갱신됐다
- [ ] `./gradlew test` 통과

---

## 여기서 하지 않는 것

| 안 하는 것 | 왜 |
| --- | --- |
| 락 (비관적/낙관적/분산) | **Phase 3.** 먼저 깨지는 걸 봐야 한다 |
| `Stock.version` 필드 | **Phase 3 #3.** 낙관적 락을 실제로 도입할 때 |
| 재고 이력 테이블 | 학습 주제(동시성)와 무관. 현재 수량만 다룬다 |
| 메뉴 목록 N+1 최적화 | **Phase 4.** 메뉴 수가 적어 지금은 부채로 표시만 |
