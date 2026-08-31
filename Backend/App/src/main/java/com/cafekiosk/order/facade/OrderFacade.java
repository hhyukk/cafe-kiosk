package com.cafekiosk.order.facade;

import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.entity.OrderStatus;
import com.cafekiosk.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 재고에 닿는 주문 경로를 감싸고, 낙관적 락이 충돌을 알리면 처음부터 다시 시도한다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 존재 이유다 ──────────────────────────
 *
 * 붙이는 순간 이 파일은 아무 쓸모가 없어진다. 낙관적 락 충돌은 트랜잭션이 롤백돼야
 * 비로소 드러나는 사건이고, 롤백 표시가 붙은 트랜잭션 안에서 다시 시도해 봐야 그 시도도
 * 커밋될 수 없다. 재시도는 죽은 트랜잭션 밖 새 트랜잭션에서 일어나야 한다. NFR-CON-05.
 *
 * OrderController 에 @Transactional 이 없는 것도 같은 이유였다. 이 파사드가 그 자리에
 * 들어와 비어 있던 바깥 seam 을 실제로 채운다.
 *
 * OrderService 가 다른 빈이라는 사실도 조건이다. 재시도할 메서드를 같은 클래스 안에 두고
 * 부르면 self-invocation 이라 프록시를 지나지 않고, 트랜잭션이 새로 열리지도 롤백되지도
 * 않는다. 재시도 루프만 도는 채로 같은 실패가 반복된다.
 *
 * ── StockLockStrategy 와 이 클래스의 관계 ──────────────────────────────────────
 *
 * 그쪽은 트랜잭션 안쪽 seam 이고 이쪽은 바깥 seam 이다. 세 전략이 서로 다른 층에서 노는
 * 탓에 인터페이스 하나로 덮을 수 없다는 판단이 StockLockStrategy 주석에 적혀 있고,
 * 그 주석이 예고한 얇은 파사드가 이것이다.
 *
 * 여기에는 전략별 분기가 없다. 비관적 락과 분산 락에서는 아래 예외가 나지 않으므로
 * 루프가 첫 바퀴에 끝나고 재시도는 0 이다. if 로 전략을 가르면 세 전략을 비교할 때
 * 파사드가 상수가 아니게 되어, 수치 차이가 락 때문인지 파사드 때문인지 흐려진다.
 *
 * ── 기성 재시도 메커니즘을 쓰지 않은 이유 ─────────────────────────────────────
 *
 * 셋을 저울에 올렸고 셋 다 같은 곳에서 걸렸다. 재시도 횟수가 호출자에게 오지 않는다.
 * 이 프로젝트에서 그 숫자는 로그가 아니라 세 전략 비교표의 한 열이다. NFR-CON-07.
 * 관측이 부수적인 편의가 아니라 기능 요구라서, 그것을 내주는 대안은 전부 탈락한다.
 *
 * Spring Retry 는 @Retryable(retryFor = OptimisticLockingFailureException.class)
 * 한 줄로 이 파일을 통째로 대신한다. 그런데 재시도 횟수가 프레임워크 안에 갇힌다.
 * RetryContext.getRetryCount 를 읽는 자리가 RetryListener 나 @Recover 인데, 거기서
 * 꺼낸 값을 반환값에 실으려면 결국 ThreadLocal 이나 카운터 빈이 필요하고 그것은
 * RetryResult 주석이 이미 버린 방법이다. spring-retry 와 aspectjweaver 가 새 의존성으로
 * 들어오는 대가로 얻는 것이 스무 줄짜리 루프 하나를 없애는 것뿐이다.
 *
 * 더 걸리는 것은 @Retryable 이 @Transactional 보다 바깥에 놓이는지가 어드바이스 순서에
 * 달려 있다는 점이다. 안쪽에 놓이면 재시도가 롤백된 트랜잭션 안에서 돈다. 그 위반은
 * 예외로 드러나지 않고 재시도가 전부 실패하는 모양으로만 보인다.
 *
 * 커스텀 어노테이션과 AOP 어드바이스도 같은 관측 문제를 갖고 같은 의존성을 요구한다.
 * 여기에 하나가 더 붙는다. 재시도가 트랜잭션 바깥에서 돈다는 사실이 어노테이션 하나로
 * 줄어들어 코드에서 사라진다. 파사드로 두면 그 순서가 호출 스택에 그대로 보인다.
 * 이 레포에서 가장 지키기 어려운 조건이 그 순서이므로 눈에 보이는 편이 낫다.
 *
 * OrderService 안에서 TransactionTemplate 을 루프에 넣는 방법은 새 빈도 새 의존성도
 * 필요 없고 self-invocation 문제도 없다. 대신 트랜잭션 경계를 읽는 방법이 어노테이션과
 * 코드 둘이 된다. 그리고 createOrder 가 주문을 만드는 일과 실패하면 다시 만드는 일을
 * 함께 하게 된다. 락을 갈아 끼우는 동안 Stock.decrease 가 바뀌지 않아야 하는 것과 같은
 * 이유로, 주문을 조립하는 코드도 락 때문에 바뀌지 않아야 한다.
 */
@Component
@RequiredArgsConstructor
public class OrderFacade {

    /**
     * 재시도 상한. 넘으면 마지막 충돌을 그대로 던진다.
     *
     * 재고 3 에 손님 10 이면 한 손님이 최악의 경우 나머지 아홉에게 차례로 밀린다.
     * 아홉 번이 이론적 최대이고, 20 은 그 위에 여유를 둔 값이다.
     *
     * 무한 재시도로 두지 않는 이유는 버전이 계속 올라가는 다른 원인이 생겼을 때
     * 요청 하나가 스레드를 붙잡고 영영 돌기 때문이다. 상한이 있으면 그때 409 가 나가고,
     * 재시도 횟수가 상한에 붙어 있다는 사실이 원인을 찾는 첫 단서가 된다.
     */
    private static final int MAX_RETRIES = 20;

    private final OrderService orderService;

    /**
     * 주문을 만든다. 재고 차감이 버전 충돌로 롤백되면 새 트랜잭션에서 다시 시도한다.
     *
     * 다시 시도한다는 말은 createOrder 를 처음부터 다시 부른다는 뜻이다. 앞선 시도가
     * 만든 Customer 와 Order 와 OrderItem 은 롤백으로 사라졌으므로 다시 만들어진다.
     * 대기번호도 다시 발급된다. 실패한 시도가 채번한 PK 값은 버려지는데, 시퀀스에 구멍이
     * 생길 뿐 대기번호가 전역 유일하고 단조 증가한다는 성질은 그대로다. FR-ORD-07.
     */
    public RetryResult<OrderDto.CreateResponse> createOrder(OrderDto.CreateRequest request) {
        return withRetry(() -> orderService.createOrder(request));
    }

    /**
     * 주문 상태를 바꾼다. 취소 전이가 재고를 되돌리므로 여기도 충돌할 수 있다.
     *
     * 로드맵이 시킨 것은 주문 생성뿐이었다. 취소까지 감싼 이유는 재고에 닿는 경로가
     * 둘인데 한쪽만 재시도를 가지면 두 경로가 서로 다른 락 성질을 갖게 되기 때문이다.
     * 차감과 복구가 같은 전략으로 행을 확보해야 한다는 OrderService.restoreStock 의
     * 판단과 같은 계열이다. 잠긴 읽기와 안 잠긴 읽기가 한 행에 섞이면 락이 가끔만 듣는
     * 것처럼 보인다는 그 주석이, 재시도가 한쪽에만 있을 때도 똑같이 성립한다.
     *
     * 상태 전이 자체는 재시도해도 안전하다. 앞선 시도가 롤백됐으면 상태가 그대로이므로
     * 다시 부른 전이가 같은 검사를 통과한다. 이미 커밋된 전이를 다시 밟는 일은 없다.
     */
    public RetryResult<Void> changeStatus(Long orderId, OrderStatus next) {
        return withRetry(() -> {
            orderService.changeStatus(orderId, next);
            return null;
        });
    }

    /**
     * 낙관적 락 충돌이면 다시, 아니면 그대로 내보낸다.
     *
     * 잡는 예외가 OptimisticLockingFailureException 하나인 것이 중요하다.
     * Hibernate 의 OptimisticLockException 을 Spring 이 ObjectOptimisticLockingFailureException
     * 으로 변환하는데 그 상위 타입이라 둘 다 걸린다.
     *
     * OutOfStockException 은 잡지 않는다. 재고 부족은 다시 물어도 같은 답이고,
     * 재시도하면 손님이 기다리는 시간만 스무 배가 된 뒤 똑같이 409 를 받는다.
     * 그리고 그 재시도가 재시도 횟수에 섞이면 2-6 비교표의 그 열이 무의미해진다.
     *
     * 충돌은 attempt.get() 안이 아니라 그 호출이 끝나는 자리에서 나온다. 버전 검사는
     * 서비스 메서드 본문이 아니라 트랜잭션 프록시가 커밋할 때 벌어지기 때문이다.
     * OrderService 안에 try/catch 를 둬서는 이 예외를 잡을 수 없다.
     *
     * 백오프와 지터를 넣지 않는다. 세 전략 비교가 읽을 두 숫자가 재시도 횟수와
     * 소요시간인데, 사이에 sleep 을 끼우면 소요시간이 락의 성질이 아니라 sleep 길이가 된다.
     * 경합이 훨씬 심한 환경이라면 백오프가 필요하겠지만 그건 그때 측정하고 넣을 일이다.
     */
    private <T> RetryResult<T> withRetry(Supplier<T> attempt) {
        OptimisticLockingFailureException lastFailure = null;

        for (int retries = 0; retries <= MAX_RETRIES; retries++) {
            try {
                return new RetryResult<>(attempt.get(), retries);
            } catch (OptimisticLockingFailureException e) {
                lastFailure = e;
            }
        }

        throw lastFailure;
    }
}
