package com.cafekiosk.stock.lock;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 분산 락이 쓸 RedissonClient 를 만든다.
 *
 * ── 조건이 전략과 같아야 한다 ─────────────────────────────────────────────────
 *
 * 이 조건이 없으면 컨텍스트가 뜨는 순간 Redisson 이 Redis 에 접속을 시도하고,
 * Redis 가 없는 환경에서는 그대로 죽는다. CI 는 docker compose 를 띄우지 않으므로
 * 기본 전략인 비관적 락으로 도는 모든 통합 테스트가 그 환경이다.
 * 분산 락을 고른 사람만 Redis 를 요구받는 것이 맞다.
 *
 * ── global/config 가 아니라 여기 있는 이유 ───────────────────────────────────
 *
 * global 은 여러 feature 가 공유하는 것이 가는 자리인데 Redis 를 쓰는 곳은 지금 분산 락
 * 하나다. 그리고 전략을 더할 때 고치는 파일이 없어야 한다는 규칙에 따라, 조건은 한곳에
 * 모이지 않고 그 전략에 딸린 파일이 자기 것을 들고 있다. 나중에 Redis 를 캐시 같은
 * 다른 용도로도 쓰게 되면 그때 global 로 옮기고 조건을 푸는 것이 순서다.
 *
 * ── 스타터를 쓰지 않는다 ─────────────────────────────────────────────────────
 *
 * redisson-spring-boot-starter 는 Spring Boot 4.0 에 아직 대응하지 않는다.
 * 순수 클라이언트는 Spring 에 의존하지 않으므로 빈 하나를 직접 만들면 그만이고,
 * 대신 접속 주소와 종료 시점을 이 파일이 책임진다.
 */
@Configuration
@ConditionalOnProperty(
        name = "cafekiosk.stock.lock-strategy",
        havingValue = "distributed"
)
public class DistributedStockLockConfig {

    /**
     * 단일 노드로 붙는다. 클러스터나 센티널은 이 프로젝트가 재려는 것과 무관하다.
     *
     * destroyMethod 를 명시하는 이유는 컨텍스트가 내려갈 때 Netty 스레드가 남지 않게
     * 하기 위해서다. 테스트가 컨텍스트를 여러 개 띄우므로 이것이 새면 JVM 이 안 끝난다.
     *
     * 주소를 spring.data.redis.host 로 받지 않는다. 그 이름은 data-redis 스타터의
     * 자동 설정이 읽는 자리인데 이 프로젝트에는 그 스타터가 없다. 이름만 같고 읽는
     * 사람이 없으면, 값을 고쳤는데 아무 일도 일어나지 않는 상황을 언젠가 만든다.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${cafekiosk.stock.redis-address}") String address) {

        Config config = new Config();
        config.useSingleServer().setAddress(address);
        return Redisson.create(config);
    }
}
