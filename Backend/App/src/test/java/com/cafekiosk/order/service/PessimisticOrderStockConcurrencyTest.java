package com.cafekiosk.order.service;

import com.cafekiosk.support.ConcurrencyResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 비관적 락으로 같은 시나리오를 돌린다. 세 전략 비교표의 첫 행이다.
 *
 * ── 어노테이션이 하나도 없는 것이 이 클래스의 내용이다 ────────────────────────────
 *
 * cafekiosk.stock.lock-strategy 가 application.yml 에서 이미 pessimistic 이고,
 * 값이 없어도 PessimisticStockLockStrategy 가 matchIfMissing 으로 뜬다.
 * 그래서 여기서 프로퍼티를 다시 지정할 필요가 없다.
 *
 * 값이 같으니 명시해 두면 읽는 사람에게 친절하지 않겠느냐는 생각이 들지만 그러면 안 된다.
 * @TestPropertySource 가 붙는 순간 값이 같아도 Spring 컨텍스트 캐시 키가 달라져
 * 컨텍스트가 하나 더 뜬다. 아무것도 붙이지 않으면 이 클래스는 다른 통합 테스트들과
 * 기본 컨텍스트를 공유하고, 새로 뜨는 컨텍스트는 낙관적 락 쪽 하나로 끝난다.
 *
 * 이름이 그 친절함을 대신한다. 클래스명이 Pessimistic 이라고 말하고 있고,
 * 어느 전략이 기본값인지는 application.yml 의 주석이 소유한다.
 */
public class PessimisticOrderStockConcurrencyTest extends AbstractOrderStockConcurrencyTest {

    /**
     * 비관적 락에서는 재시도가 한 번도 일어나지 않는다.
     *
     * 행을 잠근 채로 읽으므로 뒤에 온 트랜잭션은 앞사람이 커밋한 뒤에야 값을 본다.
     * 들고 있는 버전이 언제나 최신이라 Stock.version 이 어긋날 창이 없다.
     * 기다리는 비용은 소요시간에 들어가지 재시도 횟수에 들어가지 않는다.
     *
     * 이 0 이 낙관적 락 쪽 숫자와 나란히 놓일 때 두 전략의 성격 차이가 드러난다.
     * 경쟁이 잦으면 비관적, 드물면 낙관적이라는 말의 근거가 이 두 줄이다. NFR-CON-07.
     */
    @Override
    protected void 재시도를_검사한다(ConcurrencyResult 결과) {
        assertThat(결과.재시도())
                .as("잠근 채로 읽으므로 버전이 어긋날 창이 없다. %s", 결과.요약())
                .isZero();
    }
}
