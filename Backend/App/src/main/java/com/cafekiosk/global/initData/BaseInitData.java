package com.cafekiosk.global.initData;

import com.cafekiosk.order.entity.Customer;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.repository.OrderRepository;
import com.cafekiosk.order.entity.OrderItem;
import com.cafekiosk.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Component
@RequiredArgsConstructor
@Profile("dev") // dev 프로필에서만 실행
public class BaseInitData {
    @Autowired
    @Lazy
    private BaseInitData self;

    private final CustomerRepository customerRepository;
    private final MenuRepository menuRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Bean
    ApplicationRunner baseInitDataApplicationRunner() {
        return args -> {
            self.work1();
        };
    }

    @Transactional
    public void work1() {
        if (customerRepository.count() > 0) return;

        Menu menu1 = new Menu("에티오피아 예가체프", "http://localhost:8080/uploads/Ethiopia-Yirgacheffe.jpg", 15000, "커피원두", "example@example.com");
        menuRepository.save(menu1);

        Menu menu2 = new Menu("콜롬비아 수프리모", "http://localhost:8080/uploads/Colombia Supremo.jpg", 18000, "커피원두", "example@example.com");
        menuRepository.save(menu2);

        Menu menu3 = new Menu("브라질 산토스", "http://localhost:8080/uploads/Brazil Santos.jpg", 12000, "커피원두", "example@example.com");
        menuRepository.save(menu3);
    }

}
