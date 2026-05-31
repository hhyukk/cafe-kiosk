package com.back.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 베이스 클래스.
 * extends AbstractIntegrationTest 만으로 PostgreSQL 컨테이너에 연결된 Spring 컨텍스트가 준비된다.
 *
 * ── 어노테이션 ──────────────────────────────────────────────────────────────────
 *
 * @ActiveProfiles("test")
 *   application-test.yml 을 로드한다. 서브클래스에서 반복 선언 불필요.
 *
 * @SpringBootTest(webEnvironment = RANDOM_PORT)
 *   실제 Tomcat 서버를 랜덤 포트로 띄운다.
 *   @AutoConfigureMockMvc 와 함께 사용하면 MockMvc 가 WebApplicationContext 를 통해
 *   in-process 로 연결되므로 handler() 어서션도 그대로 동작한다.
 *
 * ── Singleton 컨테이너 패턴 ──────────────────────────────────────────────────────
 *
 * static 필드 + static 블록으로 컨테이너를 직접 시작한다.
 *
 * static 인 이유:
 *   자바에서 static 필드는 클래스가 JVM에 로드될 때 딱 한 번 초기화된다.
 *   이 클래스를 상속한 테스트 클래스가 몇 개든 컨테이너는 JVM 당 1개만 뜬다.
 *
 * @Testcontainers + @Container 어노테이션을 쓰지 않은 이유:
 *   해당 조합은 JUnit 5 extension 이 컨테이너 생명주기를 관리하는데,
 *   테스트 클래스가 바뀔 때마다 컨테이너를 중단시킨다.
 *   두 번째 클래스 실행 시 @DynamicPropertySource 로 고정된 포트가 무효화되어 연결 실패.
 *   어노테이션 없이 직접 start() 를 호출하면 JUnit extension 이 관여하지 않아
 *   JVM 이 살아있는 동안 컨테이너가 유지된다.
 *
 * 컨테이너 종료:
 *   Testcontainers 가 내부적으로 Ryuk 사이드카 컨테이너를 띄워서
 *   JVM 종료 시 자동으로 정리한다. 직접 stop() 호출 불필요.
 *
 * ── @DynamicPropertySource ────────────────────────────────────────────────────
 *
 * Testcontainers 는 PostgreSQL 포트를 랜덤으로 배정하므로 application-test.yml 에 하드코딩 불가.
 * @DynamicPropertySource 는 Spring 컨텍스트 생성 직전에 실행되어
 * 컨테이너가 배정받은 실제 URL/포트를 datasource 설정에 주입한다.
 * postgres::getJdbcUrl 형태(Supplier)를 사용하는 이유는 컨테이너가 완전히 기동된 후
 * URL 이 확정된 다음에 값을 읽어가도록 하기 위해서다.
 *
 * ── @AutoConfigureMockMvc / @Transactional 을 여기에 두지 않는 이유 ──────────────
 *
 * 모든 통합 테스트가 MockMvc 를 필요로 하지 않는다.
 * 예) Week 3 동시성 테스트는 ExecutorService 로 서비스를 직접 호출하므로 MockMvc 불필요.
 * 공통 필요 사항만 베이스 클래스에 두고, 각자 필요한 것은 서브클래스에서 선언한다.
 *
 * ── 전체 실행 흐름 ─────────────────────────────────────────────────────────────
 *
 * JVM 시작
 *   └─ AbstractIntegrationTest 클래스 로드
 *        └─ static 블록 실행 → postgres:16 컨테이너 기동 (포트: 랜덤, 예: 49152)
 *
 * 테스트 실행 (MenuControllerTest, OrderControllerTest, ...)
 *   └─ @DynamicPropertySource 실행
 *        └─ spring.datasource.url = jdbc:postgresql://localhost:49152/test
 *   └─ Spring 컨텍스트 생성 (위 URL 로 연결)
 *   └─ 각 테스트 실행 (@Transactional → 롤백)
 *
 * JVM 종료
 *   └─ Ryuk 이 postgres:16 컨테이너 자동 삭제
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres;

    static {
        postgres = new PostgreSQLContainer<>("postgres:16");
        postgres.start();
    }

    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
