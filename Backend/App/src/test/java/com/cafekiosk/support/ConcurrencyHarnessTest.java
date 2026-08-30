package com.cafekiosk.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시 실행 하네스 자체를 고정하는 테스트다. 재고를 건드리지 않는다.
 *
 * ── 왜 이 테스트가 재고 동시성 테스트보다 먼저 필요한가 ───────────────────────────
 *
 * 다음 단계에서 할 일은 락이 없는 코드에 동시 주문을 던져 재고가 깨지는 것을 눈으로 보는
 * 일이다. 그런데 그때 나오는 빨간불이 락이 없어서인지 하네스가 실제로는 스레드를 겹쳐
 * 돌리지 못해서인지 구분할 수 없으면, 그 관찰은 아무것도 증명하지 못한다.
 * 더 나쁜 경우도 있다. 하네스가 순차로 돌면 락이 없는데도 초록이 나오고,
 * 그러면 없는 락이 있다고 믿은 채로 다음 단계로 넘어간다.
 *
 * 커넥션 풀 크기를 재고 코드보다 먼저 박은 것과 같은 이유다. 원인을 잘못 짚는 길을 미리 막는다.
 *
 * 하네스가 재시도까지 집계하기 시작했으므로 그 집계도 여기서 고정한다.
 * 낙관적 락 비교표의 한 열이 이 숫자에서 나온다.
 *
 * ── Spring 컨텍스트가 뜨는 것에 대해 ────────────────────────────────────────────
 *
 * 하네스 검증에 DB 는 필요 없지만 AbstractConcurrencyTest 를 그대로 상속한다.
 * 실제로 쓰일 환경에서 검증하는 편이 낫고, 컨텍스트는 다른 테스트와 캐시를 공유하므로
 * 여기서 새로 뜨지 않는다. 이 클래스는 행을 만들지 않으므로 정리할 것도 없다.
 */
public class ConcurrencyHarnessTest extends AbstractConcurrencyTest {

    /** 재고 동시성 테스트가 쓸 스레드 수와 같게 맞춘다. 하네스를 그 규모에서 검증한다. */
    private static final int 스레드수 = 10;

    /** 배리어에 거는 한계. 베이스의 대기 한계보다 짧아야 배리어가 먼저 터진다. */
    private static final int 배리어_한계_초 = 5;

    @Test
    @DisplayName("열 스레드가 모두 실행되고 번호가 0 부터 겹치지 않게 주어진다")
    void 모두_실행되고_번호가_겹치지_않는다() {
        Set<Integer> 받은번호 = ConcurrentHashMap.newKeySet();

        ConcurrencyResult 결과 = 동시에_실행한다(스레드수, 번호 -> {
            받은번호.add(번호);
            return 0;
        });

        assertThat(결과.성공()).isEqualTo(스레드수);
        assertThat(결과.실패()).isZero();

        // 번호가 겹치면 다음 단계에서 스레드마다 다른 이메일을 만들 수 없다.
        // 이메일이 겹치면 재고가 아니라 Customer 유니크 제약에서 먼저 죽는데,
        // 그건 별도 단계가 다루는 주제라 여기 섞이면 무엇을 재는지 알 수 없게 된다.
        assertThat(받은번호)
                .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 스레드수).boxed().toList());
    }

    @Test
    @DisplayName("태스크가 던진 예외는 감싸이지 않고 타입 그대로 담긴다")
    void 예외가_감싸이지_않고_담긴다() {
        // 짝수 번호만 던진다. 절반은 성공하고 절반은 실패하는 상황을 만들어
        // 성공과 실패가 각자 제 몫만 세는지 함께 본다.
        ConcurrencyResult 결과 = 동시에_실행한다(스레드수, 번호 -> {
            if (번호 % 2 == 0) {
                throw new 하네스검증용예외();
            }
            return 0;
        });

        assertThat(결과.성공()).isEqualTo(스레드수 / 2);
        assertThat(결과.실패()).isEqualTo(스레드수 / 2);

        // 이 줄이 이 테스트의 존재 이유다. 하네스가 Future.get() 을 거치면 예외가
        // ExecutionException 으로 감싸이고, 그러면 이 값이 언제나 0 이 된다.
        // 총 실패 건수는 그대로 맞으므로 다음 단계에서 락이 동작한다고 착각하기 딱 좋다.
        assertThat(결과.실패수(하네스검증용예외.class)).isEqualTo(스레드수 / 2);
    }

    @Test
    @DisplayName("열 스레드가 실제로 같은 순간에 겹쳐 돈다")
    void 실제로_겹쳐_돈다() {
        // 열 스레드가 전부 이 지점에 닿아야 배리어가 열린다. 하나라도 아직 출발하지
        // 않았으면 먼저 온 스레드가 한계 시간을 넘겨 죽는다.
        //
        // Thread.sleep 을 넣고 동시 실행 최댓값이 1 보다 크다고 단언하는 방식은 쓰지 않는다.
        // 타이밍에 기대는 단언은 CI 부하에 따라 흔들리고, 흔들리는 테스트는 Phase 2 내내
        // 진짜 실패를 가린다. 배리어는 순차 실행이면 반드시 실패하므로 결정적이다.
        CyclicBarrier 모두도착 = new CyclicBarrier(스레드수);

        // await 의 반환값을 그대로 흘려보내지 않는다. CyclicBarrier.await 는 도착 순번을
        // int 로 돌려주므로 그냥 두면 컴파일은 되면서 그 순번이 재시도 횟수로 집계된다.
        // 재시도가 없었는데 45 라고 말하는 결과가 나오고, 원인을 락에서 찾게 된다.
        ConcurrencyResult 결과 = 동시에_실행한다(스레드수, 번호 -> {
            모두도착.await(배리어_한계_초, TimeUnit.SECONDS);
            return 0;
        });

        // 순차로 돌면 첫 스레드가 TimeoutException 으로 죽고 배리어가 깨져
        // 나머지가 BrokenBarrierException 을 받는다. 성공은 0 이 된다.
        assertThat(결과.성공()).isEqualTo(스레드수);
        assertThat(결과.실패()).isZero();
    }

    @Test
    @DisplayName("성공한 스레드가 돌려준 재시도만 합산되고 실패한 스레드 것은 빠진다")
    void 재시도는_성공한_스레드_것만_합산된다() {
        // 번호를 그대로 재시도 횟수로 돌려준다. 짝수는 돌려주기 전에 죽는다.
        // 홀수 번호 1, 3, 5, 7, 9 의 합인 25 만 남아야 한다.
        // 전부 더하면 45 이므로 두 숫자가 확실히 갈린다.
        ConcurrencyResult 결과 = 동시에_실행한다(스레드수, 번호 -> {
            if (번호 % 2 == 0) {
                throw new 하네스검증용예외();
            }
            return 번호;
        });

        assertThat(결과.재시도())
                .as("실패한 스레드의 시도가 섞이면 45 가 된다. %s", 결과.요약())
                .isEqualTo(25);

        // 요약에 재시도가 실려야 빨간불 출력 한 줄을 PR 비교표로 옮길 수 있다.
        assertThat(결과.요약()).contains("재시도 25");
    }

    /** 이 테스트 밖에서는 쓰지 않는다. 다른 예외와 섞이지 않는 타입이어야 실패수 단언이 성립한다. */
    private static class 하네스검증용예외 extends RuntimeException {
        하네스검증용예외() {
            super("하네스가 예외를 타입 그대로 담는지 보려고 던진다");
        }
    }
}
