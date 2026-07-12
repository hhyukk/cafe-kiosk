# Backend (Spring Boot)

Spring Boot 4.0.0 / Java 21 / PostgreSQL 16 / JPA / Lombok.
**이 디렉토리가 Gradle 루트다.** 모든 gradle 명령은 여기서 실행한다.

포매터·린터 설정은 없다 (Checkstyle/Spotless 미도입). 주변 코드 스타일에 맞춘다.

## 패키지 구조 — 레이어별이 아니라 기능(feature)별

```
com.cafekiosk
├── menu/     controller · service · repository · entity · dto
├── order/    controller · service · repository · entity · dto · exception
├── stock/    entity · repository        (service/controller 아직 없음)
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

## 응답 · 예외

**`RsData<T>`** (`global/rsData/RsData`) — `record RsData<T>(String resultCode, String message, T data)`. 신규 API 응답은 이걸로 감싼다.

> 기존 `MenuController` 일부는 raw `String` / `List<>`를 반환해 일관성이 깨져 있다. 새 코드에서 따라하지 말 것.

**예외 → HTTP 매핑은 `GlobalExceptionHandler`에서** 한다. 컨트롤러에서 try/catch로 상태코드를 만들지 않는다.
예: `InvalidOrderStatusTransitionException` → `409 CONFLICT`.

## 상태 머신은 엔티티 안에

주문 상태 전이 규칙은 `Order` 엔티티가 소유한다 — `startPreparing()` / `markReady()` / `complete()` / `cancel()`. 잘못된 전이면 엔티티가 `InvalidOrderStatusTransitionException`을 던진다.

전이 규칙을 서비스 레이어로 빼지 말 것. 서비스는 엔티티를 조회해서 위 메서드를 호출하기만 한다.

`OrderStatus`: `PENDING`(예약, 미사용) → `CONFIRMED` → `IN_PROGRESS` → `READY` → `COMPLETED`, 그리고 `CANCELLED`.

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

## API 문서

springdoc-openapi. 서버 기동 후 http://localhost:8080/swagger-ui.html

API 경로 컨벤션이 통일되어 있지 않다 — `OrderController`는 메서드마다 풀패스(`/api/order`), `MenuController`는 클래스 레벨 `@RequestMapping` + `/modify/{id}` 같은 동사형 경로를 쓴다. 새 컨트롤러는 클래스 레벨 `@RequestMapping` + REST 스타일(동사 대신 HTTP 메서드) 쪽을 따르는 게 좋다.
