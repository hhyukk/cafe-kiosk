# cafe-kiosk 로드맵

> **이 문서가 진행 상태의 정본이다.** 여기는 **인덱스**이고, 각 Phase의 작업 단위·함정·완료 기준은 [`docs/roadmap/`](roadmap/)에 있다.
>
> "무엇을·왜"의 배경은 [`docs/PRODUCT.md`](PRODUCT.md), "어떻게 코드를 만지나"는 각 디렉토리의 `CLAUDE.md`에 있다.
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

**현재: Phase 1 진행 중.** 백엔드의 주문 조회 API(상태·목록)는 끝났고, **Spring Security와 화면 3분할이 남았다.**

| Phase | 한 줄 목표 | 상태 | 상세 |
| --- | --- | --- | --- |
| **0** | 배송몰 잔재를 걷어내고 가격 스냅샷을 세운다 | ✅ 완료 | [phase-0.md](roadmap/phase-0.md) |
| **1** | 주문이 주방까지 흘러가고, 화면이 권한별로 나뉜다 | 🔶 진행 중 | [phase-1.md](roadmap/phase-1.md) |
| **2** | `Stock`이 살아나 주문이 재고를 실제로 깎는다 | ⬜ 예정 | [phase-2.md](roadmap/phase-2.md) |
| **3** | 동시 주문에서 재고가 깨지는 걸 증명하고 세 가지 락으로 고친다 | ⬜ 예정 | [phase-3.md](roadmap/phase-3.md) |
| **4** | 스키마가 자산이 되고 배포된다 | ⬜ 예정 | [phase-4.md](roadmap/phase-4.md) |

**왜 이 순서인가**는 [`PRODUCT.md §4`](PRODUCT.md)에 있다. 요약하면 — 주방 화면이 없으면 상태머신은 아무도 호출하지 않는 코드고(1), 재고 차감이 없으면 깨뜨릴 것도 없으며(2), 그 깨짐을 고치는 것이 이 프로젝트의 목적지다(3). 스키마가 계속 바뀌는 동안 마이그레이션을 도입해봐야 의미가 없어서 배포를 맨 뒤로 뒀다(4).

---

## 스코프 아웃 — 의도적으로 안 하는 것

상세 이유는 [`PRODUCT.md §5`](PRODUCT.md).

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
| 인가가 요청 본문 이메일 문자열 비교뿐 · 주문 상태 변경 API는 완전 공개 | `menu/service/MenuService.java` | [Phase 1](roadmap/phase-1.md) |
| CORS `allowedMethods`에 `PATCH`가 없다 — 주방 화면의 상태 변경이 preflight에서 막힌다 | `global/config/WebConfig.java` | [Phase 1](roadmap/phase-1.md) |
| 재고가 주문과 연결돼 있지 않다 — 재고 0이어도 무한 주문 | `order/service/OrderService.java` | [Phase 2](roadmap/phase-2.md) |
| 컨트롤러 메서드에 `@Transactional` — 낙관적 락 재시도가 구조적으로 불가능해진다 | `OrderController`, `MenuController` | [Phase 2](roadmap/phase-2.md) |
| `findByEmail().orElseGet(save)` + `Customer.email` unique — 동시 첫 주문에서 제약 위반 | `order/service/OrderService.java` | [Phase 3](roadmap/phase-3.md) |
| `getOrdersByStatus`의 N+1 | `order/service/OrderService.java` | [Phase 4](roadmap/phase-4.md) |
| 바인딩 파라미터 `TRACE` 로깅 — 손님 이메일이 로그에 남는다 | `application.yml` | [Phase 4](roadmap/phase-4.md) |
| `localhost:8080` 9곳 하드코딩 · BFF 규약 위반 2곳 | 프론트 전역 | [Phase 4](roadmap/phase-4.md) |
