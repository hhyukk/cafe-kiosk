package com.cafekiosk.global.initData;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Profile("dev") // dev 프로필에서만 실행
public class BaseInitData {
    @Autowired
    @Lazy
    private BaseInitData self;

    private final MenuRepository menuRepository;
    private final StockRepository stockRepository;

    @Bean
    ApplicationRunner baseInitDataApplicationRunner() {
        return args -> {
            self.work1();
        };
    }

    @Transactional
    public void work1() {
        // 이 메서드가 심는 것은 메뉴/재고다. 따라서 가드도 메뉴를 세야 한다.
        // 예전엔 customerRepository.count() 를 봤는데, 정작 Customer 를 만들지 않으니
        // 가드가 항상 0이라 매 기동 재실행됐다 — ddl-auto 를 update 로 바꾸는 순간 메뉴가 무한 증식한다.
        if (menuRepository.count() > 0) return;

        Menu menu1 = new Menu("에티오피아 예가체프", "http://localhost:8080/uploads/Ethiopia-Yirgacheffe.jpg", 15000, "커피원두", "example@example.com");
        menuRepository.save(menu1);
        stockRepository.save(new Stock(menu1, 100));

        Menu menu2 = new Menu("콜롬비아 수프리모", "http://localhost:8080/uploads/Colombia Supremo.jpg", 18000, "커피원두", "example@example.com");
        menuRepository.save(menu2);
        stockRepository.save(new Stock(menu2, 50));

        Menu menu3 = new Menu("브라질 산토스", "http://localhost:8080/uploads/Brazil Santos.jpg", 12000, "커피원두", "example@example.com");
        menuRepository.save(menu3);
        stockRepository.save(new Stock(menu3, 3));
    }

}
