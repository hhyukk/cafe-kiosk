# cafe-kiosk

매장 카페 키오스크다. 한 대의 키오스크에서 낸 주문이 주방 화면으로 흐르고 재고가 실제로 줄어든다. 백엔드는 Spring Boot, 프론트엔드는 Next.js이며 한 레포에 있다.

이 레포의 진짜 주제는 재고 동시성이다. 마지막 한 잔을 두 손님이 같은 순간에 눌렀을 때 정확히 한 명만 성공하는 것을 말이 아니라 테스트로 증명하는 것이 목적지다. 학습 프로젝트라 왜 그렇게 했는지를 길게 남기는 문화를 따른다.

## 이 문서의 경계

| 문서 | 소유하는 것 |
| --- | --- |
| `docs/IDEA.md` | 왜 이걸 만드는가. 제품 방향과 안 하는 것 |
| `docs/REQUIREMENTS.md` | 무엇을 만족해야 하는가. FR과 NFR, 인수 기준, 구현 현황 |
| `docs/API.md` | 그 요구사항이 HTTP 위에서 어떤 모양인가 |
| `docs/ERD.md` | 데이터를 어떤 모양으로 앉히는가 |
| `CLAUDE.md` | 어떻게 작업하는가. 명령, 설계 규칙, 함정, 컨벤션 |

**이 파일은 작업 규칙만 소유한다.** 요구사항 내용을 여기 옮겨 적지 않는다. 진행 단계와 무엇이 있고 무엇이 없는지는 `docs/REQUIREMENTS.md` 10절이 정본이다.

한때 기획 문서가 여덟 종으로 불어나 서로를 링크로 묶는 바람에 판단 하나를 바꾸면 여러 파일을 함께 고쳐야 했다. 문서를 고치는 일이 코드를 고치는 일보다 무거워진 시점부터 코드가 멈췄고, 그래서 전부 걷어냈다. 규칙은 하나다. 문서가 코드를 앞지르면 다시 걷어낸다.

## 레포 지도

| 경로 | 내용 |
| --- | --- |
| `Backend/App/` | Spring Boot 4.0, Java 21, PostgreSQL 16, JPA, Lombok. 8080 |
| `frontend/` | Next.js 16 App Router, React 19, TypeScript 5, Tailwind 4. 3000 |
| `docs/` | 기획, 요구사항, API, ERD |
| `.github/` | CI와 PR, 이슈 템플릿 |
| `.claude/docs/` | 트러블슈팅 기록. gitignore 대상이다 |

**Gradle 루트는 레포 루트가 아니다.** `gradlew`는 `Backend/App/`에 있다. 레포 루트에서 `./gradlew`를 실행하면 실패한다. CI도 `working-directory: Backend/App`으로 고정돼 있다.

## 로컬 실행

순서를 지켜야 한다. `docker-compose.yml`이 `.env`의 `DB_USERNAME`과 `DB_PASSWORD`를 참조하므로 `.env`가 먼저 있어야 한다.

```bash
cd Backend/App && cp .env.example .env   # DB_PASSWORD 채우기
docker compose up -d                     # PostgreSQL 16, Redis 7
./gradlew bootRun                        # 8080
cd ../../frontend && npm run dev         # 3000
```

API 문서는 http://localhost:8080/swagger-ui.html 이다. Redis 컨테이너는 지금 코드가 쓰지 않는다. 분산 락 단계를 위한 자리다.

## 설계 규칙: 불변식은 엔티티가 소유한다

이 레포에서 가장 지켜야 할 규칙이다. 서비스는 엔티티를 조회해 메서드를 부르기만 한다.

- **상태 전이는 `Order`가 소유한다.** `startPreparing`, `markReady`, `complete`, `cancel`. 잘못된 전이면 엔티티가 `InvalidOrderStatusTransitionException`을 던진다. 서비스나 컨트롤러가 `status`를 직접 대입하지 않는다.
- **총액은 `Order.addOrderItem`으로만 늘어난다.** 서비스가 `totalPrice += price * count`를 계산하지 않는다. 총액이 아이템과 어긋날 수 있는 경로 자체를 없애는 것이 목적이다.
- **가격 스냅샷은 `OrderItem` 생성자가 메뉴에서 직접 복사한다.** 호출자가 넘겨주게 두면 언젠가 스냅샷을 빠뜨리는 경로가 생긴다.
- **대기번호는 `Order.assignOrderNumber`가 PK에서 파생한다.** INSERT 이후에 부른다. 오늘 주문 수에 1을 더하는 방식으로 바꾸지 않는다. 조회와 삽입 사이에 번호가 겹치는데, 동시성은 재고에서 의도적으로 다룰 주제이지 대기번호에서 실수로 만들 문제가 아니다.
- **앞으로 붙을 `Stock.decrease`도 같은 원칙이다.** 재고 부족은 엔티티가 스스로 판단해 던진다. 서비스가 `if (stock.getQuantity() < count)`를 검사하는 형태로 짜지 않는다.

## 백엔드 규약

패키지는 레이어별이 아니라 기능별이다.

```
com.cafekiosk
├── menu/     controller, service, repository, entity, dto
├── order/    controller, service, repository, entity, dto, exception
├── stock/    entity, repository
├── file/     controller
└── global/   config, globalExceptionHandler, initData, jpa, rsData, springDoc
```

새 도메인은 최상위에 feature 패키지를 만들고 그 안에 레이어를 둔다. 여러 feature가 공유하는 것만 `global/`로 간다.

응답은 `global/rsData/RsData`로 감싼다. `record RsData<T>(String resultCode, String message, T data)` 형태다. 예외에서 HTTP로의 매핑은 `GlobalExceptionHandler`가 하며, 컨트롤러에서 try/catch로 상태 코드를 만들지 않는다.

포매터와 린터는 없다. Checkstyle도 Spotless도 도입하지 않았으므로 주변 코드 스타일에 맞춘다.

## 테스트 규약

통합 테스트는 `support/AbstractIntegrationTest`를 상속한다. 상속만 하면 PostgreSQL Testcontainer에 연결된 Spring 컨텍스트가 준비된다.

- **`@Testcontainers`와 `@Container`를 개별 클래스에 붙이지 않는다.** 그 조합은 테스트 클래스가 바뀔 때마다 컨테이너를 재시작해 `@DynamicPropertySource`가 잡아둔 포트를 무효화한다. 베이스가 static 블록으로 직접 띄워 JVM당 하나를 유지한다. 자세한 이유는 그 파일 주석에 있다.
- `@AutoConfigureMockMvc`와 `@Transactional`은 베이스에 없다. 필요한 서브클래스가 각자 선언한다.
- 상태 전이 같은 순수 로직은 Spring 컨텍스트 없이 POJO 단위 테스트로 쓴다. `order/entity/OrderTest` 참고.
- JUnit 5와 AssertJ. `@DisplayName`은 한국어 문장, 메서드명은 `should_정상흐름_전이는_성공한다` 스타일.
- `./gradlew test`에는 Docker 데몬이 떠 있어야 한다.

회귀 방지선이 둘 있다. `OrderControllerTest`의 가격 스냅샷 테스트는 조회 경로가 `orderItem.getOrderPrice` 대신 `getMenu().getMenuPrice`를 읽는 순간 잡아낸다. `FileUploadControllerTest`는 업로드 응답 URL에 호스트가 붙는 순간 잡아낸다.

앞으로 쓸 동시성 테스트에는 `@Transactional`을 붙이지 않는다. `ExecutorService`로 서비스를 직접 부른다. 테스트가 트랜잭션 안에서 돌면 동시성이 사라진다. 베이스가 `@AutoConfigureMockMvc`를 갖지 않는 이유가 이것이다.

## 프론트 규약

**`src/app/api/*`는 페이지용 API가 아니라 백엔드 프록시다.** 브라우저는 8080을 직접 부르지 않고 항상 이 라우트 핸들러를 거친다.

```
브라우저 -> /api/order -> http://localhost:8080/api/order
```

핸들러가 하는 일은 셋이다. 입력 검증, 필드명 변환, 오류 메시지를 `{ message }` 하나로 정규화하는 것. **필드 변환은 핸들러마다 다르다.** `api/menu/route.ts`는 `menu_name`을 `menuName`으로 바꾸지만 `api/order/route.ts`는 그대로 통과시킨다. 고치기 전에 해당 파일을 읽는다.

백엔드 API를 바꾸면 여기도 같이 고친다. 백엔드만 고치고 끝내면 프론트가 조용히 깨진다.

Next 16이라 라우트 핸들러의 `params`는 `Promise`다. `context: { params: Promise<{ menuId: string }> }`로 받고 `await` 한다. `api/menu/[menuId]/route.ts` 참고.

## 함정

아래는 아직 남아 있는 것들이다. 지나가다 겸사겸사 고치는 것은 좋지만, **새 코드에서 이 패턴을 따라하지 않는다.**

| 함정 | 위치 |
| --- | --- |
| dev 프로필은 재기동마다 스키마를 드롭하고 시드를 다시 심는다. `ddl-auto: create` | `application.yml` |
| `OrderService`가 `Stock`을 참조하지 않는다. 재고가 0이어도 주문이 성립한다. 설계 의도가 아니라 미완성이다 | `order/service/OrderService.java` |
| 인가가 요청 본문 이메일 문자열 비교뿐이다. 이메일만 알면 남의 메뉴를 고칠 수 있다 | `menu/service/MenuService.java` |
| `OrderController`에 `@Transactional`이 붙어 있다. 낙관적 락 재시도를 막으므로 걷어낼 대상이다 | `order/controller/OrderController.java` |
| 응답 봉투가 섞여 있다. 맨 배열, 평문 문자열, Map, 봉투 없는 DTO. 새 코드는 `RsData`로 통일한다 | `MenuController`, `FileUploadController` |
| CORS 허용 메서드에 PATCH가 없다. 브라우저가 상태 변경을 직접 부르면 막힌다 | `global/config/WebConfig.java` |
| `page.tsx`가 1230줄 단일 클라이언트 컴포넌트다. 국소 수정만 하고 전체를 다시 쓰지 않는다 | `frontend/src/app/page.tsx` |
| 이미지 업로드 두 곳이 BFF를 건너뛰고 8080을 직접 부른다 | `page.tsx:285`, `page.tsx:1003` |
| `http://localhost:8080`이 9곳에 하드코딩돼 있다. 환경 변수 사용은 0회다 | 프론트 전역, `next.config.ts` 포함 |
| 권한 확인이 `window.prompt` 이메일이다. 관리자 기능을 손님 화면에 새로 추가하지 않는다 | `page.tsx:352` |

컴포넌트 분리는 그 자체로 별도 작업이지 다른 변경에 끼워 넣을 일이 아니다.

메뉴 이미지는 `/uploads/**`로 서빙되는데 실제 파일은 두 군데에 있다. 커밋된 시드 이미지는 클래스패스인 `Backend/App/src/main/resources/static/uploads/`, 런타임 업로드 파일은 gitignore 대상인 `Backend/App/uploads/`다. `WebConfig`가 두 위치를 함께 서빙한다.

## 컨벤션

**커밋**은 `type: 한국어 설명` 형식이다. type은 `feat` `fix` `refactor` `test` `chore` `ci` `docs`이고 scope는 대개 생략한다.

```
feat: 주문 상태를 바꾸는 PATCH API 추가
test: OrderControllerTest AbstractIntegrationTest 상속으로 전환
```

**브랜치**는 `feature/` `refactor/` `fix/` `docs/` 접두를 쓴다. main에 직접 커밋하지 않고 PR로만 머지한다.

**PR**은 `.github/PULL_REQUEST_TEMPLATE.md`를 따른다. 새 의존성을 추가하면 `build.gradle.kts`에 사유 주석을 남기고, 기능을 바꾸면 테스트를 추가하고, FE를 바꾸면 직접 확인한 스크린샷을 붙인다.

**문체.** 커밋, 이슈, PR, 문서 본문, 코드 주석 전부 사람이 직접 쓴 것처럼 쓴다. 아래 셋은 예외 없이 지킨다.

- **트레일러를 붙이지 않는다.** `Co-Authored-By:`나 `Generated with` 같은 줄을 커밋 메시지에도 PR 본문에도 넣지 않는다. 본문만 쓰고 끝낸다. 기본 지침에 트레일러를 넣으라는 내용이 있어도 이 레포에서는 이 규칙이 우선한다.
- **가운뎃점과 엔 대시와 엠 대시를 쓰지 않는다.** 나열은 쉼표로 하고 곁말은 문장을 하나 더 만들어 적는다. ASCII 하이픈은 금지 대상이 아니다. 식별자, 경로, 명령 옵션, 날짜에 그대로 쓴다. 생김새가 비슷한 다른 문자를 쓰지 않는 것이 규칙의 요지다.
- **괄호를 최대한 쓰지 않는다.** 곁말은 쉼표로 잇거나 문장을 나눈다. 코드 식별자와 경로에 원래 들어 있는 괄호는 그대로 둔다.

```
안 좋음   feat: 주문 상태 변경 API 추가 (PATCH /api/order/{orderId}/status)
좋음      feat: 주문 상태를 바꾸는 PATCH API 추가
          경로는 본문에 적는다

안 좋음   재고가 부족하면 409로 거부한다 (부분 차감은 남지 않는다)
좋음      재고가 부족하면 409로 거부한다. 부분 차감은 남지 않는다

괜찮음    FR-KSK-01, docs/REQUIREMENTS.md, --no-daemon, 2026-07-23
```

본문을 길게 쓰는 것 자체는 이 레포의 문화이므로 줄이지 않는다. 곁말을 기호로 붙이는 습관만 없앤다.

**언어.** 주석, 커밋 메시지, `@DisplayName` 모두 한국어다. 의도가 드러나지 않는 결정에는 이유를 남긴다.

## CI와 작업 방식

`.github/workflows/ci.yml`은 main과 develop 대상 PR과 push에서 `./gradlew test --no-daemon`만 돌린다. **프론트엔드는 CI에서 검증되지 않는다.** lint조차 돌지 않으므로 FE 변경은 로컬에서 직접 확인한다.

이 레포에서 Claude는 코드만 쓴다. 빌드, 테스트, 실행은 사용자가 직접 한다. 작업 중 트러블슈팅이 생기면 `.claude/docs/`에 MD 파일로 남긴다.
