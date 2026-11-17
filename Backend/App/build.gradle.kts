plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.cafekiosk"
version = "0.0.1-SNAPSHOT"
description = "App"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    // 점주 인증. 필터 체인과 BCrypt 는 security 스타터가, Bearer 토큰 검증은 리소스 서버 스타터가 맡는다.
    // 리소스 서버는 인가 서버 없이도 이 서버가 직접 발급한 JWT 를 검증할 수 있고, 발급에 쓰는
    // NimbusJwtEncoder 도 이 스타터가 끌어오는 spring-security-oauth2-jose 에 들어 있다.
    // 둘 다 Spring Boot BOM 이 버전을 관리한다.
    // jjwt 를 쓰지 않는 이유는 둘이다. BOM 밖이라 Redisson 처럼 버전을 직접 박아야 하고, 헤더에서
    // 토큰을 꺼내 서명과 만료를 검증하는 필터를 프레임워크가 이미 주는데 그것을 손으로 다시 짜게 된다.
    // 이름은 Spring Boot 4.0 의 것이다. 예전 이름 spring-boot-starter-oauth2-resource-server 는 deprecated 다.
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // 분산 락. redisson-spring-boot-starter 는 Spring Boot 4.0 에 아직 대응하지 않아
    // 순수 클라이언트만 넣는다. RedissonClient 빈은 DistributedStockLockConfig 가 직접 만든다.
    // Spring Boot BOM 의 관리 대상이 아니라 버전을 직접 적는다.
    // 4.7.0 은 netty-bom 4.2.16.Final 위에 서 있어 Spring Boot 4.0 이 관리하는 Netty 와
    // 같은 4.2 계열이다. 3.x 를 쓰면 Netty 4.1 을 끌어와 버전이 갈린다.
    implementation("org.redisson:redisson:4.7.0")

    // 테스트용 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")

    // Testcontainers. 버전은 Spring Boot BOM이 관리 (Spring Boot 4.0은 Testcontainers 2.x 사용)
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// IntelliJ에서 Gradle로 실행 시 워킹 디렉토리가 프로젝트 루트로 잡혀
// spring.config.import의 file:.env 경로를 못 찾는 문제 방지.
// bootRun이 아닌 IntelliJ 자체 생성 태스크(JavaExec)로 실행되는 경우도 커버하기 위해 withType으로 적용
tasks.withType<JavaExec> {
    workingDir = project.projectDir
}
