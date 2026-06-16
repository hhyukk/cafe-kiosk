package com.cafekiosk.stock.repository;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class StockRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private EntityManager em;

    private Menu menu;

    @BeforeEach
    void setUp() {
        menu = menuRepository.save(new Menu("브라질 산토스", "tmpImgUrl", 12000, "커피원두", "example@example.com"));
        stockRepository.save(new Stock(menu, 3));

        // 영속성 컨텍스트를 비워, 이어지는 조회가 실제 DB에서 다시 읽도록 한다.
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("menuId로 재고를 조회하면 수량과 메뉴 연관이 일치한다")
    void t00() {
        Optional<Stock> found = stockRepository.findByMenuId(menu.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(3);
        assertThat(found.get().getMenu().getId()).isEqualTo(menu.getId());
        assertThat(found.get().getMenu().getMenuName()).isEqualTo("브라질 산토스");
    }

    @Test
    @DisplayName("존재하지 않는 menuId면 빈 Optional을 반환한다")
    void t01() {
        Optional<Stock> found = stockRepository.findByMenuId(Long.MAX_VALUE);

        assertThat(found).isEmpty();
    }
}
