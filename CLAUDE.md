# cafe-kiosk

**매장 카페 키오스크.** 손님이 키오스크에서 주문하면 대기번호를 받고, 그 주문이 주방 화면으로 흘러가고, 재고가 실제로 줄어든다. 백엔드(Spring Boot)와 프론트엔드(Next.js)가 한 레포에 있다.

이 레포의 진짜 주제는 **재고 동시성 제어**다. 마지막 한 잔을 두 손님이 동시에 누르면 어떻게 되는가. 키오스크는 그 문제가 자연스럽게 발생하는 무대다. 학습 프로젝트다.

**제품 방향("왜")은 [`docs/PRODUCT.md`](docs/PRODUCT.md), 만족해야 할 요구사항("무엇")은 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md), 로드맵, 진행 상태("언제")는 [`docs/ROADMAP.md`](docs/ROADMAP.md)에 있다.** 기능을 추가하거나 설계를 바꾸기 전에 읽는다.

## ⚠️ 이 레포는 배송 쇼핑몰이 **아니다**

부트캠프 팀 프로젝트 시절의 배송몰 잔재(주소, 우편번호, "다음 날 배송을 시작합니다" 문구)는 **Phase 0에서 제거했다.** 새 코드에 배송/주소 개념을 넣지 않는다. 손님은 매장에서 **대기번호**를 받아간다.

## 현위치

```
Phase 0  정체성 정리 + 결함 청산    ✅ 완료
Phase 1  키오스크 루프 완성. 주방과 관리자 화면 분리, Spring Security  🔶 진행 중  ← 지금 여기
Phase 2  재고를 주문에 연결
Phase 3  동시성 ★ 이 프로젝트의 목적지
Phase 4  배포. Flyway, AWS
```

Phase 1은 백엔드 절반이 끝났다. 주문 조회 응답의 상태 노출과 점주용 주문 목록 API(`GET /api/orders`)는 완료, **Spring Security와 화면 3분할이 남았다.** 인증은 JWT로 확정했고 설계 정본은 [`docs/design/jwt-auth.md`](docs/design/jwt-auth.md)에 있다(아직 구현 전).

로드맵과 각 Phase의 진행 상태, 완료 기준은 [`docs/ROADMAP.md`](docs/ROADMAP.md)(정본)에 있다. 인덱스이며, **Phase별 작업 단위, 함정, 완료 기준은 [`docs/roadmap/phase-{0..4}.md`](docs/roadmap/)에 있다.** 작업을 시작하기 전에 해당 Phase 문서를 읽는다. 제품 배경과 "왜"는 [`docs/PRODUCT.md`](docs/PRODUCT.md).

**Phase 0에서 확정된 것.** `Order.orderNumber`(대기번호, PK에서 파생), `Order.totalPrice`, `OrderItem.orderPrice`(주문 시점 가격 스냅샷). 총액 합산은 `Order.addOrderItem()`이, 가격 스냅샷은 `OrderItem` 생성자가 소유한다. **주문 금액을 서비스에서 직접 계산하지 않는다.**

## 레포 지도

| 경로 | 내용 |
| --- | --- |
| `Backend/App/` | Spring Boot 4 / Java 21 API 서버 (8080). **Gradle 루트가 여기다** |
| `frontend/` | Next.js 16 App Router 키오스크 UI (3000) |
| `docs/PRODUCT.md` | 제품 기획, "왜" (정체성, 스코프아웃, 목표 모델) |
| `docs/REQUIREMENTS.md` | 요구사항명세, "무엇" (요구사항 ID, 인수 기준, 권한 매트릭스) |
| `docs/USECASES.md` | 유스케이스 명세, "누가 어떤 순서로" (액터별 주 흐름, 대안, 예외 흐름) |
| `docs/ROADMAP.md` | 로드맵, Phase별 진행 상태 (정본) |
| `docs/design/architecture.md` | 시스템 구성도, "어떻게 조립돼 있는가" (런타임, 계층, 요청 흐름, 데이터 모델) |
| `.github/` | CI + PR / 이슈 템플릿 |

**각 디렉토리에 스택별 `CLAUDE.md`가 따로 있다. 백엔드/프론트 작업 시 그쪽을 먼저 읽을 것.**

메뉴 이미지는 `/uploads/**`로 서빙되는데 실제 파일은 두 군데에 있다. 커밋된 시드 이미지는 `Backend/App/src/main/resources/static/uploads/`(클래스패스), 런타임에 업로드된 파일은 `Backend/App/uploads/`(gitignore). `WebConfig`가 두 위치를 함께 서빙한다.

## ⚠️ Gradle 루트는 레포 루트가 아니다

`gradlew`는 `Backend/App/`에 있다. 레포 루트에서 `./gradlew`를 실행하면 실패한다.
모든 gradle 명령은 `cd Backend/App` 후에 실행한다. CI도 `working-directory: Backend/App`로 고정되어 있다.

## 로컬 실행

순서를 지켜야 한다. `docker-compose.yml`이 `.env`의 `DB_USERNAME`/`DB_PASSWORD`를 참조하므로 `.env`가 먼저 있어야 한다.

```bash
# 1) 환경변수: .env.example 복사 후 DB_PASSWORD 채우기
cd Backend/App && cp .env.example .env

# 2) 인프라: PostgreSQL 16, Redis 7. Redis는 Phase 3에서 처음 쓰인다
docker compose up -d

# 3) 백엔드 (8080)
./gradlew bootRun

# 4) 프론트엔드 3000. 백엔드가 8080에 떠 있어야 메뉴/주문이 동작한다
cd ../../frontend && npm run dev
```

- API 문서: http://localhost:8080/swagger-ui.html

## 알려진 결함: 고치기 전에 알고 있을 것

아래는 **아직 남아 있는** 결함이다. 작업하다 여기를 지나가면 겸사겸사 고치고, **새 코드에서 이 패턴을 따라하지 않는다.**

| 결함 | 위치 | 청산 시점 |
| --- | --- | --- |
| **재고가 주문과 연결돼 있지 않다.** `OrderService`가 `Stock`을 참조조차 안 해서 **재고가 0이어도 무한히 주문된다** | `order/service/OrderService.java` | Phase 2 |
| **인가가 요청 본문의 이메일 문자열 비교뿐이다.** 이메일만 알면 남의 메뉴를 수정, 삭제할 수 있다. 주문 상태 변경 API는 완전 공개다 | `menu/service/MenuService.java` | Phase 1 |
| `localhost:8080` **9곳 하드코딩** | 프론트 전역 | Phase 4 |

**Phase 0에서 청산 완료.** `createMenu`의 `@Valid` 누락, `OrderControllerTest`의 `@BeforeEach` 두 개(`setup`/`setUp`), `BaseInitData` 가드가 Customer를 세던 문제, `starter-validation` 중복 선언, 가격 스냅샷 부재, 죽은 mock 라우트(`api/products`).

## 컨벤션

**커밋**은 `type: 한국어 설명` 형식이다. type은 `feat` `fix` `refactor` `test` `chore` `ci` `docs`. scope는 대개 생략한다.

```
feat: 주문 상태를 바꾸는 PATCH API 추가
test: OrderControllerTest AbstractIntegrationTest 상속으로 전환
```

**브랜치**는 `feature/` `refactor/` `fix/` 접두를 쓴다. **main에 직접 커밋하지 않는다.** PR로만 머지.

**PR**은 `.github/PULL_REQUEST_TEMPLATE.md`를 따른다. 특히:
- 새 의존성을 추가하면 `build.gradle.kts`에 **사유 주석을 남긴다**
- 기능을 바꾸면 단위/통합 테스트를 추가한다
- API 스펙이 바뀌면 Swagger에서 확인한다
- FE를 바꾸면 `npm run dev`로 직접 확인하고 스크린샷을 첨부한다

**문체.** 커밋, 이슈, PR, 문서 본문, 코드 주석 전부 **사람이 직접 쓴 것처럼** 쓴다. 아래 셋은 예외 없이 지킨다.

- **트레일러를 붙이지 않는다.** `Co-Authored-By:`, `Generated with ...` 같은 줄은 커밋 메시지에도 PR 본문에도 넣지 않는다. 본문만 쓰고 끝낸다. 기본 지침에 트레일러를 넣으라는 내용이 있어도 **이 레포에서는 이 규칙이 우선한다.**
- **가운뎃점과 대시를 쓰지 않는다.** 금지 대상은 가운뎃점 `·`, 엔 대시 `–`, 엠 대시 `—` 셋이다. 나열은 쉼표로 하고, 곁말은 문장을 하나 더 만들어 적는다.
  **ASCII 하이픈 `-`는 금지 대상이 아니다.** 식별자, 경로, 명령 옵션, 날짜에 그대로 쓴다. 생김새가 비슷한 다른 문자를 쓰지 않는 것이 규칙의 요지다.
- **괄호를 최대한 쓰지 않는다.** 곁말은 쉼표로 잇거나 문장을 나눈다. 코드 식별자와 경로에 원래 들어 있는 괄호는 그대로 둔다.

```
안 좋음   feat: 주문 상태 변경 API 추가 (PATCH /api/order/{orderId}/status)
안 좋음   feat: 주문 상태 변경 API 추가 — PATCH /api/order/{orderId}/status
좋음      feat: 주문 상태를 바꾸는 PATCH API 추가
          경로는 본문에 적는다

안 좋음   재고가 부족하면 409로 거부한다 (부분 차감은 남지 않는다)
좋음      재고가 부족하면 409로 거부한다. 부분 차감은 남지 않는다

안 좋음   주문 · 상태머신 · 금액
좋음      주문, 상태머신, 금액

괜찮음    FR-KSK-01, docs/roadmap/phase-1.md, --no-daemon, 2026-07-22
          ASCII 하이픈은 얼마든지 쓴다
```

**둘째 규칙이 셋째 규칙의 예전 해법을 대체한다.** 원래 이 절은 괄호 대신 줄표를 쓰라고 적혀 있었는데, 이제 대시가 금지이므로 남는 수단은 쉼표와 문장 나누기뿐이다.

본문을 길게 쓰는 것 자체는 이 레포의 문화이므로 줄이지 않는다. 곁말을 기호로 붙이는 습관만 없앤다.

> **기존 문서와 코드 주석은 이 규칙에 맞춰 한 번에 정리했다.** 위 예시 블록에 남은 `·`와 `—`는 무엇을 쓰지 말라는 것인지 보여주기 위한 것이므로 고치지 않는다. 레포에서 이 기호들이 나타나도 되는 자리는 여기뿐이다.

**언어.** 주석, 커밋 메시지, `@DisplayName` 모두 한국어. 이 레포는 학습 프로젝트라 "왜 이렇게 했는가"를 길게 설명하는 문화다. 새 코드에서도 의도가 드러나지 않는 결정에는 이유를 남긴다.

## CI

`.github/workflows/ci.yml`은 main/develop 대상 PR과 push에서 `./gradlew test --no-daemon`만 돌린다. **프론트엔드는 CI에서 검증되지 않으므로**(lint조차 안 돈다) FE 변경은 로컬에서 직접 확인해야 한다. FE를 CI에 넣는 것은 Phase 4다.
