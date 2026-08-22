package com.cafekiosk.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 테스트 베이스 클래스.
 * 스레드 여러 개를 같은 순간에 출발시키고 성공과 실패를 집계해 돌려준다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * 붙이는 순간 모든 스레드가 테스트가 열어 둔 트랜잭션에 참여하게 되어 동시성 자체가
 * 사라진다. 재려던 경쟁이 없어지므로 락이 없어도 통과하고, 락을 붙여도 아무것도 달라지지
 * 않는다. MenuWriteTransactionTest 와 OrderStockTest 와 StockControllerTest 가
 * 트랜잭션 밖에 서 있는 것과 같은 계열의 이유이며, 그중에서도 가장 직접적인 경우다.
 *
 * 롤백이 없으므로 서브클래스가 만든 행은 서브클래스가 @AfterEach 에서 직접 지운다.
 * 이 베이스는 정리를 대신해 주지 않는다. 무엇을 만드는지가 서브클래스마다 다르기 때문이다.
 * 정리를 빠뜨리면 OrderControllerTest 가 전체 주문 목록 길이를 세는 자리에서 깨진다.
 *
 * ── 어노테이션을 새로 붙이지 않는 이유 ───────────────────────────────────────────
 *
 * AbstractIntegrationTest 를 그대로 상속하고 아무것도 얹지 않는다. 어노테이션을 하나라도
 * 추가하면 Spring 컨텍스트 캐시 키가 달라져 컨텍스트가 새로 뜬다.
 * 동시성 테스트는 서비스를 직접 부르므로 MockMvc 가 필요 없고, 베이스가
 * @AutoConfigureMockMvc 를 갖지 않은 덕분에 여기서 추가 비용이 들지 않는다.
 *
 * ── 래치 세 개 ────────────────────────────────────────────────────────────────
 *
 * 준비완료  스레드 수만큼. 각 스레드가 대기에 들어가기 직전에 내린다
 * 출발선    1. 테스트 스레드가 한 번에 연다
 * 결승선    스레드 수만큼. 각 스레드가 끝나며 내린다
 *
 * 출발선만 두면 첫 스레드가 이미 일을 시작한 뒤에 마지막 스레드가 생성될 수 있다.
 * 스레드를 만드는 시간이 경쟁 구간을 갉아먹어 실제보다 약한 경쟁을 재게 되고,
 * 그렇게 되면 락이 없는데도 통과하는 동시성 테스트가 나온다. 준비완료가 그 창을 닫는다.
 */
public abstract class AbstractConcurrencyTest extends AbstractIntegrationTest {

    /**
     * 결승선을 기다리는 한계. 넘으면 데드락으로 보고 실패시킨다.
     *
     * 무한 대기로 두면 데드락이 났을 때 테스트가 끝나지 않고 CI 가 그냥 멈춘다.
     * 여러 메뉴를 반대 순서로 주문해도 데드락이 없어야 한다는 것이 이 프로젝트의
     * 요구사항인데, 그 위반이 무응답이 아니라 실패로 드러나야 손으로 재현할 수 있다.
     */
    private static final long 대기_한계_초 = 30;

    /**
     * 동시에 실행할 작업. 스레드마다 자기 번호를 받는다.
     *
     * IntConsumer 가 아니라 자체 인터페이스인 이유가 둘이다.
     * 번호를 받아야 스레드마다 다른 이메일 같은 것을 만들 수 있고, throws Exception 이
     * 있어야 checked 예외를 던지는 코드를 호출자가 try 로 감싸지 않고 그대로 부를 수 있다.
     * 어차피 하네스가 Throwable 을 통째로 잡으므로 여기서 좁힐 이유가 없다.
     */
    @FunctionalInterface
    protected interface ConcurrentTask {
        void run(int 번호) throws Exception;
    }

    /**
     * 스레드 수만큼을 같은 순간에 출발시키고 전부 끝날 때까지 기다린다.
     *
     * 소요시간은 출발선을 여는 순간부터 잰다. 스레드 풀을 만들고 태스크를 등록하는 시간이
     * 섞이면 세 전략을 비교할 때 락이 아니라 스케줄러를 재게 된다.
     */
    protected ConcurrencyResult 동시에_실행한다(int 스레드수, ConcurrentTask 작업) {
        CountDownLatch 준비완료 = new CountDownLatch(스레드수);
        CountDownLatch 출발선 = new CountDownLatch(1);
        CountDownLatch 결승선 = new CountDownLatch(스레드수);

        AtomicInteger 성공 = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> 예외들 = new ConcurrentLinkedQueue<>();

        // 가상 스레드를 쓰지 않는다. Java 21 에서는 JDBC 드라이버가 synchronized 블록
        // 안에서 블로킹할 때 가상 스레드가 캐리어 스레드에 고정된다. 고정되면 실제 병렬도가
        // 캐리어 수까지 줄어들어, 스레드 10 개를 띄웠는데 재고를 동시에 건드리는 것은
        // 그보다 적은 수가 된다. 재려던 경쟁이 조용히 약해지는 것이 가장 나쁘다.
        ExecutorService 스레드풀 = Executors.newFixedThreadPool(스레드수);

        try {
            for (int i = 0; i < 스레드수; i++) {
                final int 번호 = i;
                스레드풀.execute(() -> {
                    준비완료.countDown();
                    try {
                        // 출발선 대기와 작업 실행을 나눠 잡는다. 한 try 로 묶고
                        // InterruptedException 을 먼저 잡으면, 작업이 스스로 던진
                        // InterruptedException 까지 하네스 중단으로 오해해 삼킨다.
                        try {
                            출발선.await();
                        } catch (InterruptedException e) {
                            // 작업이 실패한 것이 아니라 하네스가 중단된 것이다.
                            // 예외 목록에 넣지 않는다. 대신 아래 집계 단언이 이 스레드의
                            // 부재를 잡아낸다. 성공에도 실패에도 세지지 않기 때문이다.
                            Thread.currentThread().interrupt();
                            return;
                        }

                        // Future 를 거치지 않고 여기서 직접 잡는다.
                        // Future.get() 은 예외를 ExecutionException 으로 감싸므로
                        // 감싸인 채 담으면 실패수(OutOfStockException.class) 가 언제나 0 이 된다.
                        // 총 실패 건수만 맞고 이유는 알 수 없는 결과가 나오는데,
                        // 그 숫자로는 락이 동작한다는 말을 할 수 없다.
                        try {
                            작업.run(번호);
                            성공.incrementAndGet();
                        } catch (Throwable t) {
                            예외들.add(t);
                        }
                    } finally {
                        결승선.countDown();
                    }
                });
            }

            대기한다(준비완료, "모든 스레드가 출발선에 서기를");

            long 시작 = System.nanoTime();
            출발선.countDown();
            대기한다(결승선, "모든 스레드가 끝나기를");
            Duration 소요시간 = Duration.ofNanos(System.nanoTime() - 시작);

            ConcurrencyResult 결과 = new ConcurrencyResult(성공.get(), List.copyOf(예외들), 소요시간);

            // 하네스가 스스로를 검사한다. 띄운 스레드 수와 집계된 수가 다르면 어떤 스레드가
            // 성공도 실패도 아닌 채로 사라진 것이고, 그 상태에서 성공 3 실패 7 같은 숫자를
            // 그대로 내보내면 락을 의심하게 만든다. 의심할 자리는 하네스다.
            assertThat(결과.성공() + 결과.실패())
                    .as("띄운 스레드 %d 개와 집계된 결과 수가 다르다. 재고가 아니라 하네스를 의심할 자리다", 스레드수)
                    .isEqualTo(스레드수);

            return 결과;
        } finally {
            // close() 를 쓰지 않는다. Java 19 부터 ExecutorService 가 AutoCloseable 이라
            // try-with-resources 가 되지만, close() 는 종료를 무한히 기다린다.
            // 데드락으로 대기_한계_초 를 넘겨 빠져나온 자리에서 다시 무한히 기다리면
            // 타임아웃을 둔 의미가 사라진다.
            스레드풀.shutdownNow();
        }
    }

    private void 대기한다(CountDownLatch 래치, String 무엇을) {
        try {
            if (!래치.await(대기_한계_초, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "%s %d 초 동안 기다렸으나 끝나지 않았다. 남은 스레드 %d 개다. 데드락을 의심한다"
                                .formatted(무엇을, 대기_한계_초, 래치.getCount()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("%s 기다리다 인터럽트됐다".formatted(무엇을), e);
        }
    }
}
