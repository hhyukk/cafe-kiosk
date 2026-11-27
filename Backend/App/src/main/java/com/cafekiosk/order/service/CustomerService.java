package com.cafekiosk.order.service;

import com.cafekiosk.order.entity.Customer;
import com.cafekiosk.order.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 손님 행을 주문과 다른 트랜잭션에서 만든다.
 *
 * 같은 이메일로 첫 주문이 동시에 들어오면 email 유니크 제약이 그중 하나의 INSERT 만 받아들인다.
 * 이 클래스가 생기기 전에는 그 INSERT 가 주문 트랜잭션 안에 있었고 이렇게 깨졌다.
 * 열 스레드 중 성공 1, 실패 9. 패자 아홉은 승자의 주문 트랜잭션이 재고까지 깎고 커밋할 때까지
 * 유니크 인덱스에서 기다렸다가, 커밋되는 순간 2 ms 안에 한꺼번에 customer_email_key 위반을 받았다.
 * 그 예외가 주문 트랜잭션째 롤백시켜 재고에 닿기도 전에 끝났다. HTTP 였다면 매핑이 없어 500 이다.
 *
 * ── REQUIRES_NEW 인 이유 ───────────────────────────────────────────────────────
 *
 * 손님 행이 주문과 떨어져 곧바로 커밋된다. 경쟁자는 승자의 주문 전체가 아니라 이 짧은 트랜잭션만
 * 기다렸다가 충돌을 받는다. 충돌로 죽는 것도 이 안쪽 트랜잭션뿐이라 바깥 주문 트랜잭션은 멀쩡하다.
 *
 * 같은 트랜잭션에서 예외를 잡는 방법은 성립하지 않는다. PostgreSQL 은 제약 위반이 난 트랜잭션을
 * abort 상태로 만들어 그 안에서는 다시 읽을 수도 커밋할 수도 없다.
 *
 * OrderService 의 메서드가 아니라 별도 빈인 것도 조건이다. 같은 클래스 안에서 부르면
 * self-invocation 이라 프록시를 지나지 않고 REQUIRES_NEW 가 조용히 무시된다.
 * OrderFacade 가 OrderService 를 다른 빈으로 두는 이유와 같다.
 *
 * ── 이 클래스가 하지 않는 것 ────────────────────────────────────────────────────
 *
 * 조회하지 않는다. 바깥이 먼저 찾아보고 없을 때만 부른다. 이유는 OrderService.findOrCreateCustomer 에 있다.
 *
 * 예외를 잡지 않는다. 실패한 INSERT 가 이 트랜잭션에 rollback-only 를 찍으므로 여기서 삼켜도
 * 커밋되지 않는다. 잡는 자리는 이 빈의 프록시 바깥인 호출자다.
 *
 * 만든 Customer 를 돌려주지 않는다. 이 트랜잭션의 EntityManager 는 커밋과 함께 닫혀 그 인스턴스가
 * 준영속이 된다. 경계를 넘는 것은 email 하나뿐이다.
 *
 * ── 감수하는 것 ──────────────────────────────────────────────────────────────
 *
 * 주문이 실패해도 손님 행은 남는다. 재고 부족 409 든 없는 메뉴 400 이든 손님 확보가 먼저다.
 * 주문을 묶는 열쇠가 먼저 생기고 묶을 주문이 나중에 붙는 순서라서, 주문 없는 손님 행은 어긋난
 * 데이터가 아니라 아직 주문이 없는 손님일 뿐이다. 손님 행을 이메일로 찾아 보여 주는 조회가 없으므로
 * 그 행이 밖으로 드러나는 경로도 없다.
 *
 * docs/ADR/ADR-0004 는 메뉴 등록에 REQUIRES_NEW 가 붙으면 메뉴는 롤백되고 재고 행만 남는 경로가
 * 열린다며 그 경로를 막았다. 여기서는 손님 행에 대해 비슷한 경로를 일부러 연다. 사정이 다르다.
 * 메뉴 없는 재고 행은 팔 수 없는 재고라 장부가 어긋나지만, 주문 없는 손님 행은 어긋날 것이 없다.
 *
 * 주문을 만드는 @Transactional 테스트의 롤백에도 실리지 않는다. 그런 테스트가 정리하는 방법은
 * OrderControllerTest 에 있다.
 *
 * 첫 주문 한 건이 커넥션을 잠깐 둘 쥔다. 바깥 주문 트랜잭션의 커넥션은 정지된 동안에도 반납되지 않는다.
 * 동시 첫 주문이 풀 크기만큼 몰리면 전부 하나씩 쥔 채 두 번째를 기다리며 멈춘다. 키오스크 한 대에서는
 * 닿지 않는 규모이고, 테스트 풀의 계산은 application-test.yml 주석에 있다.
 *
 * 흡수된 충돌마다 Hibernate 가 duplicate key 를 WARN 으로 찍는다. 실패가 아니다.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * 손님 행을 만들고 곧바로 커밋한다. 같은 이메일 행이 이미 있으면 DataIntegrityViolationException 이다.
     *
     * saveAndFlush 로 INSERT 를 이 메서드 안에서 확정한다. 지금은 id 가 IDENTITY 라 save 만으로도
     * 즉시 나가지만, 그 사실에 기대지 않고 충돌이 이 경계 안에서 드러난다는 것을 코드에 적어 둔다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(String email) {
        customerRepository.saveAndFlush(new Customer(email));
    }
}
