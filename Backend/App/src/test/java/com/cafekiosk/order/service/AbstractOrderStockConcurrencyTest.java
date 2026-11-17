package com.cafekiosk.order.service;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.facade.OrderFacade;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.order.repository.OrderItemRepository;
import com.cafekiosk.order.repository.OrderRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.exception.OutOfStockException;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.support.AbstractConcurrencyTest;
import com.cafekiosk.support.ConcurrencyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마지막 한 잔을 여러 손님이 같은 순간에 눌렀을 때 정확히 남은 수만큼만 성공하는지를 본다. AC-09.
 * 이 프로젝트가 증명하겠다고 적은 것이 바로 이 한 문장이다.
 *
 * ── 이 클래스가 추상인 이유 ──────────────────────────────────────────────────────
 *
 * 같은 시나리오를 비관적 락, 낙관적 락, 분산 락 셋에 차례로 걸고 결과를 비교하는 것이
 * NFR-CON-03 이다. 세 번 재려면 시나리오는 한 벌이어야 한다. 세 벌로 복사하면
 * 어느 날 한 벌에만 손이 가고, 그 뒤로 비교표의 세 행이 서로 다른 실험을 재게 된다.
 *
 * 여기 남는 것은 무대와 단언이고, 서브클래스가 갖는 것은 전략을 고르는 프로퍼티와
 * 재시도에 대한 기대뿐이다. 재시도만 서브클래스로 내린 이유는 그 값이 전략마다 다른
 * 유일한 숫자이기 때문이다. 성공 3, 실패 7, 최종 재고 0 은 세 전략이 전부 같아야 한다.
 * 그게 같지 않으면 그 전략은 요구사항을 만족하지 못한 것이다.
 *
 * ── 락이 없을 때 깨지던 방식 ──────────────────────────────────────────────────
 *
 * CHECK 제약 위반이 아니라 lost update 로 깨졌다. PostgreSQL 기본 격리 수준이
 * READ COMMITTED 라 열 스레드가 전부 커밋된 값 3 을 읽는다. 각자 2 를 계산하고,
 * 커밋 시점의 UPDATE 가 그 2 를 통째로 덮어쓴다. 앞사람이 깎은 사실이 사라지는 것이다.
 * 그래서 성공이 열 건이 되고 최종 재고가 0 이 아니라 2 로 남았다.
 * quantity 가 음수로 내려가지 않으므로 CHECK 는 걸리지 않았다.
 *
 * 그 빨간불을 커밋 히스토리에 남기는 것이 NFR-CON-02 였고, 이미 남았다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * 붙이는 순간 모든 스레드가 테스트가 열어 둔 트랜잭션에 참여해 동시성이 사라진다.
 * 그러면 락이 없는데도 초록이 나온다. AbstractConcurrencyTest 주석에 자세히 있다.
 * 롤백이 없으므로 만든 행은 아래 cleanup 이 직접 지운다.
 *
 * ── OrderService 가 아니라 OrderFacade 를 부르는 이유 ─────────────────────────
 *
 * 낙관적 락은 파사드 없이 성립하지 않는다. 잠그지 않고 읽으므로 충돌한 아홉 명이
 * 그대로 실패하면 성공은 한 건이고 재고는 2 가 남는다. 모자란 두 건을 채우는 것이
 * 롤백된 트랜잭션 밖에서 도는 재시도이고, 그 자리가 파사드다. NFR-CON-05.
 *
 * 비관적 락에서도 파사드를 거친다. 거기서는 충돌이 나지 않아 루프가 첫 바퀴에 끝나므로
 * 재는 것이 달라지지 않는다. 전략마다 다른 진입점을 쓰면 비교표의 세 행이 서로 다른
 * 코드 경로를 잰 것이 되는데, 그러면 수치 차이를 락 때문이라고 말할 수 없다.
 *
 * ── 위치를 order/service 로 잡은 이유 ─────────────────────────────────────────
 *
 * MockMvc 없이 스프링 빈을 직접 부르기 때문이다. 같은 재고 규칙을 지키는
 * order/controller/OrderStockTest 는 MockMvc 를 쓰므로 controller 아래에 서 있다.
 * 동시성 테스트가 HTTP 를 거치면 Tomcat 스레드 풀이 경쟁 구간에 끼어들어
 * 무엇을 재는지가 흐려진다.
 */
public abstract class AbstractOrderStockConcurrencyTest extends AbstractConcurrencyTest {

    /** 동시에 들어오는 손님 수. 하네스를 검증한 규모와 같게 맞춘다. */
    private static final int 스레드수 = 10;

    /** dev 시드의 브라질 산토스와 같은 값이다. C-05 가 이 숫자를 AC-09 재현용으로 못 박았다. */
    private static final int 시작재고 = 3;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * 지금 무대에 서 있는 메뉴들이다. 저장하는 즉시 넣어 두므로 무대를 세우다 도중에 실패해도
     * 여기 든 것은 치워진다.
     */
    private final List<Menu> 메뉴들 = new ArrayList<>();

    @AfterEach
    void cleanup() {
        무대를_치운다();
    }

    @Test
    @DisplayName("재고 3 에 열 스레드가 한 잔씩 주문하면 세 건만 성공하고 최종 재고가 0 이다")
    void 마지막_한_잔은_남은_수만큼만_팔린다() {
        Menu 산토스 = 무대를_세운다(1, 시작재고).getFirst();

        ConcurrencyResult 결과 = 동시에_실행한다(스레드수, 번호 ->
                orderFacade.createOrder(주문요청(번호)).retries());

        // 단언마다 요약을 붙인다. 빨간불 출력에 성공, 실패, 재시도, 소요시간이 그대로 찍혀
        // 그 줄을 PR 본문에 옮기면 세 전략 비교표의 한 행이 된다
        assertThat(결과.성공())
                .as("재고 %d 에 %d 스레드가 한 잔씩 주문했다. %s", 시작재고, 스레드수, 결과.요약())
                .isEqualTo(시작재고);

        // 총 실패가 아니라 타입별 실패를 센다. 커넥션 풀 고갈로 죽은 스레드가 섞여도
        // 총합은 7 이 될 수 있고, 그 7 로는 락이 동작한다는 말을 할 수 없다.
        // 낙관적 락에서는 재시도 상한을 다 쓴 스레드가 여기 안 잡히고 총 실패에만 잡힌다
        assertThat(결과.실패수(OutOfStockException.class))
                .as("재고가 부족해 거절된 건수여야 한다. %s", 결과.요약())
                .isEqualTo(스레드수 - 시작재고);

        assertThat(재고(산토스))
                .as("최종 재고. 락이 없으면 앞사람이 깎은 만큼이 덮어써져 0 보다 크게 남는다. %s", 결과.요약())
                .isZero();

        // 성공 카운트는 예외가 안 났다는 뜻일 뿐이다. 실제로 커밋된 행과 묶어 두지 않으면
        // 트랜잭션이 조용히 롤백되고도 성공으로 세어지는 경우를 구분하지 못한다.
        // 재시도가 도는 낙관적 락에서는 이 단언이 하나를 더 잡는다. 앞선 시도가 만든
        // 주문 행이 롤백되지 않고 남으면 손님 한 명이 두 잔을 받은 것이 된다
        assertThat(커밋된주문수())
                .as("커밋된 주문 행 수는 성공 건수와 같아야 한다. %s", 결과.요약())
                .isEqualTo(시작재고);

        재시도를_검사한다(결과);
    }

    /**
     * 전략마다 다른 유일한 기대다. 서브클래스가 자기 락의 성질을 여기에 적는다.
     *
     * 이 훅이 없으면 재시도 열이 아무에게도 검사받지 않는다. 그러면 파사드 배선이 끊겨
     * 재시도가 영영 0 인 채로도 나머지 단언이 전부 초록일 수 있다. 비관적 락에서는
     * 실제로 0 이 정답이라 그 사고가 드러나지 않는다.
     */
    protected abstract void 재시도를_검사한다(ConcurrencyResult 결과);

    /**
     * 메뉴를 메뉴수만큼 만들고 저마다 같은 재고를 심는다. 만든 메뉴를 만든 순서대로 돌려준다.
     *
     * 스레드가 주문할 메뉴는 스레드 번호를 메뉴 수로 나눈 나머지로 정해진다. 메뉴가 하나면 열 스레드가
     * 한 재고 행에 몰리고, 열 개면 스레드마다 자기 행을 갖는다. 이 무대에서 경쟁의 세기를 바꾸는
     * 손잡이는 행 하나에 몰리는 스레드 수뿐이다. 출발선이 모든 스레드를 같은 순간에 내보내므로
     * 재고를 늘려도 한 행을 함께 두드리는 스레드 수는 줄지 않는다.
     */
    private List<Menu> 무대를_세운다(int 메뉴수, int 메뉴당재고) {
        // 앞 무대가 남아 있으면 나머지 연산이 남은 메뉴까지 세어 스레드와 메뉴의 짝이 조용히 어긋난다
        assertThat(메뉴들).as("앞 무대를 치우지 않고 새로 세우려 한다").isEmpty();

        for (int i = 0; i < 메뉴수; i++) {
            Menu 메뉴 = menuRepository.save(new Menu(
                    "동시성 확인용 산토스 " + i, "tmpImgUrl", 12000, "커피원두", "example@example.com"));
            메뉴들.add(메뉴);
            stockRepository.save(new Stock(메뉴, 메뉴당재고));
        }
        return List.copyOf(메뉴들);
    }

    /**
     * 무대에 남은 것을 전부 지운다. 손님 쪽을 메뉴 쪽보다 먼저 지운다.
     * 주문 아이템이 메뉴를 참조하므로 순서가 바뀌면 외래키에 걸린다.
     */
    private void 무대를_치운다() {
        for (int 번호 = 0; 번호 < 스레드수; 번호++) {
            String email = 이메일(번호);
            // 캐스케이드에 기대지 않고 외래키 안쪽부터 지운다. 성공했든 롤백됐든 전부 대상이다
            orderItemRepository.deleteAllInBatch(orderItemRepository.findByOrderCustomerEmail(email));
            orderRepository.deleteAllInBatch(orderRepository.findByCustomerEmail(email));
            customerRepository.findByEmail(email).ifPresent(customerRepository::delete);
        }

        for (Menu 메뉴 : 메뉴들) {
            stockRepository.findByMenuId(메뉴.getId()).ifPresent(stockRepository::delete);
            menuRepository.findById(메뉴.getId()).ifPresent(menuRepository::delete);
        }
        메뉴들.clear();
    }

    private OrderDto.CreateRequest 주문요청(int 번호) {
        Menu 메뉴 = 메뉴들.get(번호 % 메뉴들.size());
        return new OrderDto.CreateRequest(
                이메일(번호),
                List.of(new OrderDto.OrderItemRequest(메뉴.getId(), 1))
        );
    }

    /**
     * 스레드마다 다른 이메일을 쓴다.
     * 같은 이메일이면 손님 행 확보에서 먼저 서로 부딪히고, 그 충돌을 흡수하는 시간이 소요시간에 섞인다.
     * 그 경쟁은 OrderCustomerConcurrencyTest 가 따로 재므로 여기 섞이면 무엇을 재는지 알 수 없게 된다.
     *
     * 다른 이메일이어도 스레드마다 첫 주문이라 손님 행을 만드는 트랜잭션이 하나씩 붙는다.
     * 세 전략에 똑같이 붙으므로 전략 비교에서는 상수다.
     */
    private static String 이메일(int 번호) {
        return "concurrency-%d@example.com".formatted(번호);
    }

    // 테스트를 감싸는 트랜잭션이 없으므로 이 조회는 자기 트랜잭션에서 돌고 커밋된 값을 본다
    private int 재고(Menu 메뉴) {
        return stockRepository.findByMenuId(메뉴.getId()).orElseThrow().getQuantity();
    }

    private long 커밋된주문수() {
        return IntStream.range(0, 스레드수)
                .mapToLong(번호 -> orderRepository.findByCustomerEmail(이메일(번호)).size())
                .sum();
    }
}
