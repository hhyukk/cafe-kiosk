# Phase 4: 배포  ⬜ 예정

> [← 로드맵 인덱스](../ROADMAP.md), [배경과 "왜"](../PRODUCT.md)

---

## 왜 지금인가

**스키마가 안정된 뒤에 마이그레이션을 도입해야 의미가 있다.** Phase 0~3에서 엔티티가 계속 바뀌므로(`orderNumber`, `orderPrice`, `Owner`, `Stock.version`…) 그때까진 `ddl-auto: create`가 오히려 편하다. 그래서 Flyway를 **맨 뒤로** 미뤘다.

이 Phase는 **스키마가 "버려도 되는 것"에서 자산으로 바뀌는 지점**이다. 그리고 지금까지 미뤄 둔 부채를 한꺼번에 청산하는 자리이기도 하다.

---

## 작업 단위

| # | PR | 핵심 |
| --- | --- | --- |
| 1 | `chore: 프로덕션 금지 설정 정리` | TRACE 로깅 제거, `prod` 프로필 신설, `server.base-url` 환경변수화 |
| 2 | `refactor: 프론트 백엔드 주소를 환경변수로 통일` | `NEXT_PUBLIC_API_BASE_URL`. `localhost:8080` **9곳** + `next.config.ts` rewrite |
| 3 | `ci: 프론트엔드를 CI에 추가` | `npm ci && npm run lint && npm run build` |
| 4 | `refactor: 남은 API 부채 청산` | `POST /api/order/list` 폐기, N+1 fetch join, `OrderStatus.PENDING` 결론 |
| 5 | `feat: Flyway 도입하고 ddl-auto 폐기` | 스키마를 자산으로 |
| 6 | `ci: AWS 배포 파이프라인 구성` | CD |

### PR별 메모

**#1 프로덕션 금지 설정**

`application.yml`에 지금 이런 게 있다:

```yaml
logging:
  level:
    org.hibernate.orm.jdbc.bind: TRACE      # ← 바인딩 파라미터가 전부 로그에 찍힌다
    org.hibernate.orm.jdbc.extract: TRACE
    org.springframework.transaction.interceptor: TRACE
```

**바인딩 파라미터 로깅은 프로덕션 금지다.** 손님 이메일이 그대로 로그에 남는다. `dev` 프로필로 내리고 `prod`에서는 끈다.

같이 처리할 것:
- `spring.profiles.active: dev` 하드코딩 → 환경변수로 (`prod` 프로필 신설)
- `server.base-url: http://localhost:8080` 하드코딩 → 환경변수. 지금 이대로 배포하면 **업로드한 이미지 URL이 `localhost:8080`으로 나간다**(`FileUploadController`가 이 값으로 URL을 만든다)
- `show-sql: true`도 `prod`에서 끈다

**#2 프론트 주소 환경변수화**

`http://localhost:8080`이 **9곳**에 박혀 있고 `process.env`/`NEXT_PUBLIC_*` 사용은 0회다. 대상:
- BFF 라우트 핸들러 전부 (`api/menu`, `api/menu/[menuId]`, `api/order`, `api/order/history`, Phase 1에서 추가된 `api/orders/*`)
- `next.config.ts`의 `/uploads/:path*` rewrite
- **BFF 규약 위반이 2곳 있다.** 이미지 업로드(`page.tsx:285`, `page.tsx:1003`)가 Route Handler를 거치지 않고 브라우저에서 8080을 직접 호출한다. 환경변수로 바꾸는 김에 **BFF를 거치도록 고친다.** 백엔드 CORS 설정에 의존하는 상태를 없앤다

**#3 CI에 프론트 추가**

현재 `.github/workflows/ci.yml`은 `./gradlew test`만 돌린다. **프론트는 lint조차 안 돈다.**

Phase 0에서 Next 16 라우트 핸들러의 `params` 시그니처가 옛 형태라 `npm run build`가 깨져 있었는데 **CI가 잡지 못했다.** 같은 일이 또 일어난다.

**#4 남은 API 부채**

- **`POST /api/order/list`를 폐기한다.** 조회인데 POST이고, 남의 이메일을 알면 남의 주문이 보인다. Phase 1 #6이 `GET /api/orders/{orderNumber}`로 대체 경로를 이미 만들어 뒀다. 관리자 화면이 쓰던 이메일 기반 조회는 `GET /api/orders`(점주 전용)로 흡수
- **`OrderService.getOrdersByStatus`의 N+1이 남아 있다.** 코드에 *"주방 활성 주문은 소량이라 Phase 1에서는 허용한다. fetch join 최적화는 이후 Phase로 미룬다"*라고 적어 둔 부채. `Menu ↔ Stock` 1:1도 Phase 2에서 같은 표시를 해뒀다. 함께 청산
- **`OrderStatus.PENDING`은** 진입 경로가 없는 예약 값이다. **유지할지 삭제할지 결론을 낸다.** 남긴다면 왜 남기는지 주석에 적는다

**#5 Flyway**

`ddl-auto: create` 폐기. 초기 마이그레이션은 **Phase 3까지 완료된 스키마**를 기준으로 뜬다.
`BaseInitData`(`@Profile("dev")`)와 Flyway 시드의 역할 분담을 정한다. 스키마는 Flyway, 개발용 더미 데이터는 `BaseInitData`.
`application-test.yml`의 `create-drop`은 Testcontainers 기준이라 유지할지 Flyway로 통일할지 결정한다.

---

## 함정

- **`ddl-auto: create`를 끄는 순간 `BaseInitData`의 가드가 진짜로 일한다.** 지금은 매 기동마다 DB가 드롭돼서 가드가 사실상 무의미했다. Phase 0에서 이 가드가 세는 대상(Customer)과 심는 대상(Menu)이 어긋나 있던 걸 고쳤는데, **그 수정이 여기서 처음으로 실제 효과를 낸다.** Phase 1에서 Owner 시드를 추가했다면 그쪽 가드도 함께 확인한다.
- **`.env`를 프로덕션에 그대로 쓰지 않는다.** `jwt.secret`, `DB_PASSWORD`는 배포 환경의 시크릿 관리로 옮긴다.
- **CORS `allowedOrigins`가 `http://localhost:3000` 하드코딩이다**(Phase 1에서 `SecurityConfig`로 옮겨간 뒤에도). 배포 도메인을 환경변수로 받는다.
- **파일 업로드가 로컬 디스크(`./uploads`)다.** 인스턴스가 재생성되면 업로드 이미지가 사라진다. S3로 옮길지, 아니면 "학습 프로젝트라 감수한다"고 명시할지 **결정하고 문서에 남긴다.**
- **CD는 원래부터 이 Phase 소관이었다.** `.github/workflows/ci.yml` 주석에 *"CD는 이 워크플로 범위 밖이다. AWS 인프라가 준비되는 Week 4에 별도 구성"*이라고 적혀 있다.

---

## 완료 기준

- [ ] `prod` 프로필로 기동했을 때 SQL, 바인딩 파라미터가 로그에 찍히지 않는다
- [ ] 레포 전체에 `localhost:8080` 하드코딩이 **0곳**이다
- [ ] 브라우저가 8080을 직접 호출하는 경로가 없다 (BFF 규약 완전 준수)
- [ ] CI가 백엔드 테스트 + 프론트 lint/build를 모두 돌린다
- [ ] `ddl-auto`가 없고 Flyway 마이그레이션으로 스키마가 만들어진다
- [ ] 배포된 환경에서 [Phase 1의 완료 기준](phase-1.md)(주문 → 주방 → 준비완료)이 그대로 돈다

---

## 여기서 하지 않는 것

| 안 하는 것 | 왜 |
| --- | --- |
| 다국어 | 학습 주제와 무관. [`PRODUCT.md §5`](../PRODUCT.md) |
| 실결제(PG) 연동 | 영구 스코프아웃. `CONFIRMED`를 "결제까지 끝난 상태"로 정의 |
| 모니터링/APM | 여기까지 오면 그 다음 주제. 로드맵 밖 |
| 무중단 배포 | 학습 프로젝트에 과하다 |
