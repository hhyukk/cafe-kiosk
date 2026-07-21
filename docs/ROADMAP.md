# cafe-kiosk 로드맵

> **이 문서가 진행 상태의 정본이다.** "무엇을·왜"의 배경은 [`docs/PRODUCT.md`](PRODUCT.md), "어떻게 코드를 만지나"는 각 디렉토리의 `CLAUDE.md`에 있다.
>
> 기준: 현재 `main` 코드 / 갱신: 2026-07-21

이 프로젝트의 주제는 한 줄이다 — **재고 동시성 제어.** *마지막 한 잔을 두 손님이 동시에 누르면 정확히 한 명만 성공한다.* 카페 키오스크는 그 문제가 가장 자연스럽게 벌어지는 무대다. 아래 Phase는 **"이게 없으면 다음이 성립하지 않는다"** 순서이며, 건너뛰지 않는다.

## 현위치

```
Phase 0  정체성 정리 + 결함 청산    ✅ 완료
Phase 1  키오스크 루프 완성          🔶 진행 중   ← 지금 여기
Phase 2  재고를 주문에 연결          ⬜ 예정
Phase 3  동시성 ★ 목적지            ⬜ 예정
Phase 4  배포                       ⬜ 예정
```

**현재: Phase 1 진행 중.** 백엔드의 주문 조회 API(상태·목록)는 끝났고, Spring Security와 화면 3분할이 남았다.

---

## Phase 0 — 정체성 정리 + 결함 청산  ✅ 완료

배송몰 잔재와 매장 키오스크 모델이 뒤섞인 상태를 끝내고, 시간이 갈수록 고치기 어려워지는 결함(특히 가격 스냅샷 부재)을 먼저 청산했다.

**한 것**
- 도메인: `Order`에서 `address`/`postcode` 제거. `Order.orderNumber`(대기번호, PK 파생 `String.format("%04d", id)`)·`Order.totalPrice`·`OrderItem.orderPrice`(주문 시점 가격 스냅샷) 추가.
- 총액 합산은 `Order.addOrderItem()`, 가격 스냅샷은 `OrderItem` 생성자가 소유 — 서비스가 금액을 계산하지 않는다.
- 알려진 결함 6건 청산: `createMenu` `@Valid` 누락, `OrderControllerTest`의 `@BeforeEach` 두 개(`setup`/`setUp`), `BaseInitData` 가드가 Customer를 세던 문제, `starter-validation` 중복 선언, 가격 스냅샷 부재, 죽은 mock 라우트(`api/products`).
- 프론트: 주소·우편번호 입력과 배송 안내 문구 제거, 주문 완료 시 대기번호 표시.
- 덤: Next 16 라우트 핸들러 `params` 시그니처가 옛 형태라 `npm run build`가 실패하던 것 수정(CI가 프론트를 안 돌려 드러나지 않았음 — Phase 4에서 CI에 넣는다).

**완료 기준 (달성)**
- 메뉴 가격을 15,000 → 99,000원으로 올려도 과거 주문 금액이 그대로다(테스트로 증명).
- `./gradlew test` 통과, 레포 전체에 "배송"이라는 단어가 없다.

---

## Phase 1 — 키오스크 루프 완성  🔶 진행 중

주방 화면이 없으면 상태머신은 아무도 호출하지 않는 코드다. 여기서 제품이 처음으로 **끝에서 끝까지** 이어진다.

**✅ 완료 (현재 코드 반영)**
- 주문 조회 응답에 `orderId`·`status`·`orderNumber`·`totalPrice`·`orderTime` 노출 (`order/dto/OrderDto.OrderSummary`).
- 점주/주방용 전체 주문 목록 API 신설 — `GET /api/orders?status=IN_PROGRESS` (상태 없으면 전체, FIFO 정렬).

**⬜ 남음**
- **Spring Security 도입** — 현재 인증이 아예 없다.
  - 손님: **익명**(대기번호만 있으면 됨. 키오스크에서 로그인 요구는 제품 실패).
  - 점주/바리스타: 인증 필수, `/kitchen`·`/admin` API 보호.
  - 지금의 "요청 본문 이메일 문자열 비교" 인가는 **폐기**.
- 프론트: `page.tsx` 단일 컴포넌트를 세 라우트(`/` 손님 · `/kitchen` 바리스타 · `/admin` 점주)로 분해.
- **주문 완료 화면** — 대기번호를 크게 표시(지금은 `alert`가 끝).
- 주방 화면은 우선 **폴링(3초)**. WebSocket은 Phase 3 이후 선택(동시성이 먼저다).

**완료 기준**
손님이 `/`에서 주문 → 대기번호를 받고, 그 주문이 `/kitchen`에 뜨고, 바리스타가 `준비완료`로 넘기는 흐름이 **브라우저에서 실제로 돈다.**

---

## Phase 2 — 재고를 주문에 연결  ⬜ 예정

Phase 3(동시성)의 무대를 세우는 단계. 지금 `Stock` 엔티티는 있지만 `OrderService`가 참조하지 않아 **재고가 0이어도 무한히 주문된다.**

**무엇을**
- `Stock.decrease(int)` / `increase(int)` — **감소 로직은 엔티티가 소유한다**(상태 전이를 `Order`가 소유하는 것과 같은 원칙). 서비스가 `if (quantity < count)`를 검사하지 않는다.
- `OrderService.createOrder`에서 재고 차감, 주문 취소 시 복구.
- 재고 부족 → `OutOfStockException` → `409 CONFLICT`.
- 키오스크 화면에 품절 표시.

**완료 기준**
재고 3개짜리 메뉴를 4개 주문하면 거절된다. (단, 여기서 통과하는 건 **단일 스레드 테스트** — 이게 Phase 3의 출발점이다.)

---

## Phase 3 — 동시성 ★ 이 프로젝트의 클라이맥스  ⬜ 예정

여기가 목적지다. 앞의 모든 Phase는 이 무대를 세우기 위한 것이었다. **순서가 곧 학습 내용이다.**

1. **깨지는 걸 먼저 증명한다.** `ExecutorService`로 재고 3개에 동시 주문 10건 → 재고가 **음수로 떨어지는** 테스트를 작성한다. 해결책부터 배우지 않는다. (`AbstractIntegrationTest`가 MockMvc 없이 서비스를 직접 호출하도록 설계된 이유가 이것이다.)
2. **비관적 락** (`@Lock(PESSIMISTIC_WRITE)`) → 위 테스트가 통과한다.
3. **낙관적 락** (`@Version` + 재시도)와 비교 → 경쟁이 잦으면 비관적, 드물면 낙관적임을 **말이 아니라 테스트로 확인**하고 문서화한다.
4. **Redisson 분산 락** → 서버 2대에서 왜 DB 락만으로 부족한지. **여기서 `docker-compose.yml`의 Redis가 처음 쓰인다.**

**완료 기준**
동시 주문 10건 × 재고 3개 → **정확히 3건 성공, 7건 `409`, 재고 정확히 0.** 세 락 전략 각각에 대해 이 테스트가 통과하고, 트레이드오프가 문서에 정리돼 있다.

---

## Phase 4 — 배포  ⬜ 예정

스키마가 안정된 뒤에 마이그레이션을 도입한다. Phase 0~3에서 엔티티가 계속 바뀌므로 그때까진 `ddl-auto: create`가 오히려 편하다.

**무엇을**
- `ddl-auto: create` 폐기 → **Flyway** 도입 (스키마가 "버려도 되는 것"에서 자산으로 바뀐다).
- `prod` 프로필 신설 (현재 `spring.profiles.active: dev` 하드코딩).
- `server.base-url` 환경변수화 (지금은 하드코딩이라 배포 시 이미지 URL이 `localhost:8080`으로 나간다).
- SQL 바인딩 파라미터 `TRACE` 로깅 제거 (프로덕션 금지).
- 프론트 `NEXT_PUBLIC_API_BASE_URL` 환경변수화 — `localhost:8080` **9곳** 청산.
- **CI에 프론트엔드 추가** (현재 CI는 백엔드 테스트만 돌아 FE는 lint조차 안 된다).
- AWS 배포 + CD.

---

## 스코프 아웃 — 의도적으로 안 하는 것

상세 이유는 [`docs/PRODUCT.md` §5](PRODUCT.md).

| 안 하는 것 | 한 줄 이유 |
| --- | --- |
| 실결제(PG) 연동 | 주제는 동시성이지 결제가 아니다. `OrderStatus.CONFIRMED`를 "결제까지 끝난 상태"로 정의하고 모킹. |
| 회원가입 / 마이페이지 | 키오스크 손님은 익명이어야 한다. `Customer`는 회원이 아니라 익명 주문 주체. |
| WebSocket 실시간 푸시 | 주방 화면은 폴링 3초로 충분. 동시성(Phase 3) 이후 여유가 있으면 선택. |
| 다국어(i18n) | 학습 주제와 무관. |

---

## 남은 결함 추적 (현재 코드 기준)

| 결함 | 위치 | 청산 시점 |
| --- | --- | --- |
| 재고가 주문과 연결돼 있지 않다 — 재고 0이어도 무한 주문 | `order/service/OrderService.java` | Phase 2 |
| 인가가 요청 본문 이메일 문자열 비교뿐 · 주문 상태 변경 API는 완전 공개 | `menu/service/MenuService.java` | Phase 1 |
| `localhost:8080` 9곳 하드코딩 | 프론트 전역 | Phase 4 |
