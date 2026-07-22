# Phase 4: 배포  ⬜ 예정

> [← 로드맵 인덱스](../ROADMAP.md), [배경과 "왜"](../PRODUCT.md)
> 기준: 현재 `main` 코드 / 갱신: 2026-07-22

---

## 왜 지금인가

**스키마가 안정된 뒤에 마이그레이션을 도입해야 의미가 있다.** Phase 0~3에서 엔티티가 계속 바뀌므로(`orderNumber`, `orderPrice`, `Owner`, `Stock.version`…) 그때까진 `ddl-auto: create`가 오히려 편하다. 그래서 Flyway를 **맨 뒤로** 미뤘다.

이 Phase는 **스키마가 "버려도 되는 것"에서 자산으로 바뀌는 지점**이다. 그리고 지금까지 미뤄 둔 부채를 한꺼번에 청산하는 자리이기도 하다.

---

## 작업 단위

**`요구사항` 열이 이 Phase가 소화하는 것의 정본이다.** ID의 진술 자체는 [`REQUIREMENTS.md §7`](../REQUIREMENTS.md), 검증 수단은 그 문서 §11에 있다.

| # | PR | 요구사항 | 핵심 |
| --- | --- | --- | --- |
| 1 | `chore: 프로덕션 금지 설정 정리` | NFR-SEC-01, NFR-SEC-02<br>NFR-SEC-03, NFR-OPS-03 | TRACE 로깅 제거, `prod` 프로필 신설, 배포 시크릿 분리, CORS 출처 환경변수화 |
| 2 | `refactor: 프론트 백엔드 주소를 환경변수로 통일` | NFR-OPS-02 | `NEXT_PUBLIC_API_BASE_URL`. `localhost:8080` **9곳. `next.config.ts` rewrite 포함** |
| 3 | `ci: 프론트엔드를 CI에 추가` | NFR-TEST-01 | `npm ci && npm run lint && npm run build` |
| 4 | `refactor: 남은 API 부채 청산` | FR-KSK-09, FR-ORD-13, FR-ORD-15 | `POST /api/order/list` 폐기, N+1 fetch join, `OrderStatus.PENDING` 삭제, 주문 API 응답을 `RsData`로 |
| 5 | `feat: Flyway 도입하고 ddl-auto 폐기` | NFR-OPS-01 | 스키마를 자산으로 |
| 6 | `ci: AWS 배포 파이프라인 구성` | NFR-OPS-04, **AC-14** | CD |

**NFR-OPS-05와 C-07은 결정이 끝난 항목이라 PR이 없다.** 업로드 파일을 로컬 디스크에 두고 인스턴스 재생성 시 유실을 감수한다는 진술이다. 오브젝트 스토리지로 옮기지 않는다.

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
- `spring.profiles.active: dev` 하드코딩을 환경변수로 옮기고 `prod` 프로필을 신설한다
- `show-sql: true`도 `prod`에서 끈다
- **`jwt.secret`과 `DB_PASSWORD`를 배포 환경의 시크릿 관리로 옮긴다.** `.env`를 프로덕션에 그대로 쓰지 않는다. `.env.example`에는 키 이름만 남는다. NFR-SEC-01
- **CORS `allowedOrigins`의 `http://localhost:3000` 하드코딩을 환경변수로 받는다.** Phase 1에서 `SecurityConfig`로 옮겨간 뒤에도 값은 그대로다. NFR-SEC-03

> **`server.base-url`은 여기서 할 일이 아니다.** 커밋 `4f81c28`에서 이미 청산됐다. `application.yml`에 제거 사유 주석만 남아 있고, `FileUploadController`는 `"/uploads/" + 파일명` 상대경로를 반환하며 `BaseInitData`의 시드도 상대경로다. **FR-FILE-07은 충족된 상태다.**
>
> 이 항목은 원래 이 PR의 작업이었는데 `REQUIREMENTS.md`를 쓰다가 발견해서 Phase 1 도중에 먼저 고쳤다. 코드가 아니라 **DB 행에 박히는 결함**이라 미룰수록 되돌리기 어려워지는 종류였다. 이미 저장된 `menu.img_url`을 나중에 일괄 수정하는 것보다 지금 막는 쪽이 싸다는 판단이었다.

**#2 프론트 주소 환경변수화**

지금 `http://localhost:8080`이 **9곳**에 박혀 있고 `process.env`나 `NEXT_PUBLIC_*` 사용은 0회다. 내역은 이렇다.

| 어디 | 몇 곳 |
| --- | --- |
| BFF 라우트 핸들러 `api/menu`, `api/menu/[menuId]`, `api/order`, `api/order/history` | 6 |
| `next.config.ts:8`의 `/uploads/:path*` rewrite | 1 |
| `page.tsx:285`, `page.tsx:1003`의 이미지 업로드 | 2 |

**이 표는 지금 기준이고 Phase 4 시작 시점에는 달라진다.** 아래 두 방향으로 움직인다.

- `page.tsx`의 두 곳은 [Phase 1 `#9`](phase-1.md)에서 BFF로 옮겨가며 사라진다. 원래 이 PR이 하려던 일이었는데, 인증을 붙이는 순간 업로드가 401이 되는 것이 밝혀져 Phase 1로 당겼다
- 대신 Phase 1이 `api/orders/*`, `api/auth/login`, `api/auth/logout`, `api/upload`를 새로 만든다. **새 핸들러도 일단 기존 패턴을 따르므로 하드코딩이 그만큼 늘어난다**

그래서 **작업 전에 다시 센다.** 완료 기준은 개수가 아니라 0곳이다.

**#3 CI에 프론트 추가**

현재 `.github/workflows/ci.yml`은 `./gradlew test`만 돌린다. **프론트는 lint조차 안 돈다.**

Phase 0에서 Next 16 라우트 핸들러의 `params` 시그니처가 옛 형태라 `npm run build`가 깨져 있었는데 **CI가 잡지 못했다.** 같은 일이 또 일어난다.

**#4 남은 API 부채**

- **`POST /api/order/list`를 폐기한다.** 조회인데 POST이고, 남의 이메일을 알면 남의 주문이 보인다. Phase 1 #6이 `GET /api/orders/{orderNumber}`로 대체 경로를 이미 만들어 뒀다. 관리자 화면이 쓰던 이메일 기반 조회는 `GET /api/orders`(점주 전용)로 흡수
- **`OrderService.getOrdersByStatus`의 N+1이 남아 있다.** 코드에 *"주방 활성 주문은 소량이라 Phase 1에서는 허용한다. fetch join 최적화는 이후 Phase로 미룬다"*라고 적어 둔 부채. `Menu ↔ Stock` 1:1도 Phase 2에서 같은 표시를 해뒀다. 함께 청산
- **`OrderStatus.PENDING`을 삭제한다.** 진입 경로가 없는 예약 값이고, `FR-ORD-13`이 상태를 다섯 개로 못박았다. 요구사항이 정본이므로 여기서 결론을 다시 낼 자리가 아니다. enum 상수와 함께 그 값을 설명하는 주석도 지운다. 장바구니는 브라우저 안에만 있고 서버는 그것을 모른다는 것이 이 제품의 모델이다
- **주문 API 응답을 `RsData<T>`로 바꾼다.** `FR-ORD-15`. `OrderDto.CreateResponse`와 `ChangeStatusResponse`가 raw record로 남아 있는 마지막 두 곳이다. `§9-5`의 응답 규약이 예외 없이 성립하게 만드는 작업이고, **BFF 핸들러도 같이 고쳐야 한다.** 응답 껍데기가 바뀌면 프론트가 조용히 깨진다

**#5 Flyway**

`ddl-auto: create` 폐기. 초기 마이그레이션은 **Phase 3까지 완료된 스키마**를 기준으로 뜬다.
`BaseInitData`(`@Profile("dev")`)와 Flyway 시드의 역할 분담을 정한다. 스키마는 Flyway, 개발용 더미 데이터는 `BaseInitData`.
`application-test.yml`의 `create-drop`은 Testcontainers 기준이라 유지할지 Flyway로 통일할지 결정한다.

---

## 함정

- **`ddl-auto: create`를 끄는 순간 `BaseInitData`의 가드가 진짜로 일한다.** 지금은 매 기동마다 DB가 드롭돼서 가드가 사실상 무의미했다. Phase 0에서 이 가드가 세는 대상(Customer)과 심는 대상(Menu)이 어긋나 있던 걸 고쳤는데, **그 수정이 여기서 처음으로 실제 효과를 낸다.** Phase 1에서 Owner 시드를 추가했다면 그쪽 가드도 함께 확인한다.
- **시크릿 분리와 CORS 출처 환경변수화는 함정이 아니라 `#1`의 작업이다.** 위 PR 메모로 올렸다. 함정 절에만 적어두면 읽고 지나치되 산출물이 남지 않는다.
- **파일 업로드가 로컬 디스크 `./uploads`다.** 인스턴스가 재생성되면 런타임에 올린 이미지가 사라진다. **이건 결정이 끝났다.** NFR-OPS-05와 C-07이 오브젝트 스토리지로 옮기지 않고 유실을 감수한다고 못박았다. 커밋된 시드 이미지는 클래스패스에서 서빙되므로 데모는 깨지지 않는다.
- **CD는 원래부터 이 Phase 소관이었다.** `.github/workflows/ci.yml` 주석에 *"CD는 이 워크플로 범위 밖이다. AWS 인프라가 준비되는 Week 4에 별도 구성"*이라고 적혀 있다.

---

## 완료 기준

- [ ] `prod` 프로필로 기동했을 때 SQL, 바인딩 파라미터가 로그에 찍히지 않는다
- [ ] `jwt.secret`과 `DB_PASSWORD`가 `.env`가 아니라 배포 시크릿에서 오고, CORS 허용 출처가 환경변수다
- [ ] 레포 전체에 `localhost:8080` 하드코딩이 **0곳**이다
- [ ] 브라우저가 8080을 직접 호출하는 경로가 없다. BFF 규약 완전 준수. Phase 1에서 업로드를 옮겼으므로 여기서는 **회귀 확인**이다
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
