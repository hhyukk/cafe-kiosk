package com.cafekiosk.order.service;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.facade.OrderFacade;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.order.repository.OrderItemRepository;
import com.cafekiosk.order.repository.OrderRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.support.AbstractConcurrencyTest;
import com.cafekiosk.support.ConcurrencyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 이메일로 첫 주문이 같은 순간에 여러 건 들어와도 손님 행에서 주문을 잃지 않는지를 본다. NFR-CON-06.
 *
 * ── 재고 동시성 테스트와 무엇이 다른가 ──────────────────────────────────────────
 *
 * AbstractOrderStockConcurrencyTest 는 스레드마다 다른 이메일을 써서 손님 행 경쟁을 일부러 비켜 갔다.
 * 이 클래스는 반대로 이메일을 하나로 묶고 재고를 넉넉히 둬서 재고 경쟁을 비켜 간다.
 * 둘을 한 시나리오에 섞으면 실패가 손님 행 때문인지 재고 때문인지 가를 수 없다.
 *
 * 경쟁은 주문의 첫 줄에서 벌어진다. 열 스레드가 같은 순간에 이메일로 손님을 찾으면 전부 빈 결과를
 * 받고 전부 INSERT 한다. email 유니크 제약은 그중 하나만 받아들인다. 나머지가 그 예외로 주문째
 * 죽으면 재고에 닿기도 전에 끝나고, 그러면 재고 동시성 테스트가 무엇을 증명했는지도 흐려진다.
 *
 * ── 어노테이션을 하나도 붙이지 않는 이유 ─────────────────────────────────────────
 *
 * 기본 컨텍스트를 공유하려는 것이다. 붙이는 순간 컨텍스트가 하나 더 뜨고 커넥션 풀도 하나 더 열린다.
 * 기본 전략인 비관적 락으로 도는데, 손님 행 경쟁은 재고를 어떤 락으로 잡든 그보다 앞에서 벌어지므로
 * 전략을 바꿔 가며 잴 이유가 없다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * AbstractConcurrencyTest 주석과 같은 이유다. 붙이면 열 스레드가 테스트가 열어 둔 트랜잭션에
 * 참여해 경쟁 자체가 사라진다. 롤백이 없으므로 만든 행은 아래 cleanup 이 직접 지운다.
 *
 * 부르는 대상이 OrderService 가 아니라 OrderFacade 인 것은 재고 동시성 테스트와 같은 이유다.
 * 손님이 주문하는 실제 경로를 그대로 탄다.
 */
public class OrderCustomerConcurrencyTest extends AbstractConcurrencyTest {

    /** 동시에 들어오는 첫 주문 수. 하네스를 검증한 규모와 같게 맞춘다. */
    private static final int 스레드수 = 10;

    /** 열 스레드가 함께 쓰는 이메일. 테스트가 시작될 때 이 손님 행은 없어야 한다. */
    private static final String 이메일 = "same-first-order@example.com";

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

    private Menu 메뉴;

    @BeforeEach
    void setup() {
        메뉴 = menuRepository.save(
                new Menu("손님 경쟁 확인용 라떼", "tmpImgUrl", 5000, "커피", "example@example.com"));

        // 재고를 스레드 수만큼 둔다. 재고가 모자라 거절되는 스레드가 섞이면
        // 실패가 손님 행 때문인지 재고 때문인지 가를 수 없다
        stockRepository.save(new Stock(메뉴, 스레드수));
    }

    @AfterEach
    void cleanup() {
        // 캐스케이드에 기대지 않고 외래키 안쪽부터 지운다. 성공했든 롤백됐든 전부 대상이다
        orderItemRepository.deleteAllInBatch(orderItemRepository.findByOrderCustomerEmail(이메일));
        orderRepository.deleteAllInBatch(orderRepository.findByCustomerEmail(이메일));
        customerRepository.findByEmail(이메일).ifPresent(customerRepository::delete);

        // setup 이 도중에 실패하면 메뉴가 비어 있을 수 있다
        if (메뉴 != null) {
            stockRepository.findByMenuId(메뉴.getId()).ifPresent(stockRepository::delete);
            menuRepository.findById(메뉴.getId()).ifPresent(menuRepository::delete);
            메뉴 = null;
        }
    }

    @Test
    @DisplayName("같은 이메일로 첫 주문 열 건이 동시에 들어와도 전부 성공하고 손님 행은 하나다")
    void 같은_이메일_첫_주문은_동시에_와도_전부_성립한다() {
        // 출발 전에 이 이메일의 손님 행이 없어야 한다. 이미 있으면 열 스레드가 전부
        // 찾기만 하고 아무도 INSERT 하지 않아, 경쟁 없이 초록이 나온다
        assertThat(customerRepository.findByEmail(이메일))
                .as("전제가 깨졌다. 앞선 실행이 남긴 손님 행을 의심한다")
                .isEmpty();

        ConcurrencyResult 결과 = 동시에_실행한다(스레드수, 번호 ->
                orderFacade.createOrder(주문요청()).retries());

        // 결과 중에서는 예외 목록을 가장 먼저 본다. 실패하면 어떤 예외로 죽었는지가
        // 목록째 찍혀 빨간불 출력만으로 원인이 드러난다. 성공 건수부터 보면 숫자 하나만 남는다
        assertThat(결과.예외들())
                .as("같은 이메일 첫 주문은 한 건도 실패하면 안 된다. %s", 결과.요약())
                .isEmpty();

        assertThat(결과.성공())
                .as("%s", 결과.요약())
                .isEqualTo(스레드수);

        // 성공 카운트는 예외가 안 났다는 뜻일 뿐이다. 커밋된 주문 행과 묶어 둬야
        // 조용히 롤백되고도 성공으로 세어지는 경우를 가른다. 이메일로 찾으므로
        // 열 건이 전부 한 손님 아래에 붙어 있다는 뜻도 된다
        assertThat(orderRepository.findByCustomerEmail(이메일))
                .as("커밋된 주문은 전부 한 손님 아래에 있어야 한다. %s", 결과.요약())
                .hasSize(스레드수);

        // 손님 행이 둘일 수 없는 것은 유니크 제약이 보장한다. 이 테스트가 지키는 것은
        // 그 제약에 부딪힌 손님이 주문을 잃지 않는다는 쪽이고, 여기서는 그 한 행이
        // 실제로 커밋돼 남아 있는지만 본다
        assertThat(customerRepository.findByEmail(이메일))
                .as("손님 행이 커밋돼 있어야 한다. %s", 결과.요약())
                .isPresent();

        // 기본 전략인 비관적 락에서는 재시도가 0 이다. 손님 충돌을 파사드 재시도로 흡수하게
        // 고치면 위 단언은 전부 초록인 채로 이 값만 0 보다 커진다. 재시도 횟수는 세 전략
        // 비교표의 한 열이라 손님 충돌이 섞이면 그 열이 무엇을 재는지 흐려진다.
        // 파사드가 낙관적 락 충돌만 다시 시도하는 이유는 OrderFacade 주석에 있다
        assertThat(결과.재시도())
                .as("손님 충돌이 재시도 열에 섞이면 안 된다. %s", 결과.요약())
                .isZero();
    }

    private OrderDto.CreateRequest 주문요청() {
        return new OrderDto.CreateRequest(
                이메일,
                List.of(new OrderDto.OrderItemRequest(메뉴.getId(), 1))
        );
    }
}
