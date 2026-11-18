package com.cafekiosk.global.initData;

import com.cafekiosk.auth.entity.Owner;
import com.cafekiosk.auth.repository.OwnerRepository;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final OwnerRepository ownerRepository;
    private final PasswordEncoder passwordEncoder;

    // 값은 .env 에서 온다. 코드에 박지 않는 이유는 application-dev.yml 주석에 있다
    @Value("${cafekiosk.auth.seed-owner.email}")
    private String seedOwnerEmail;

    @Value("${cafekiosk.auth.seed-owner.password}")
    private String seedOwnerPassword;

    @Bean
    ApplicationRunner baseInitDataApplicationRunner() {
        return args -> {
            self.work1();
            self.work2();
        };
    }

    @Transactional
    public void work1() {
        // 이 메서드가 심는 것은 메뉴/재고다. 따라서 가드도 메뉴를 세야 한다.
        // 예전엔 customerRepository.count() 를 봤는데, 정작 Customer 를 만들지 않으니
        // 가드가 항상 0이라 매 기동 재실행됐다. ddl-auto 를 update 로 바꾸는 순간 메뉴가 무한 증식한다.
        if (menuRepository.count() > 0) return;

        // imgUrl 은 호스트를 붙이지 않은 상대경로다 (FR-FILE-07).
        // 여기에 http://localhost:8080 을 박으면 그 문자열이 그대로 DB 에 남아,
        // 배포 환경에서 이미지가 전부 깨진다. 서빙은 WebConfig 의 /uploads/** 핸들러가 한다.
        Menu menu1 = new Menu("에티오피아 예가체프", "/uploads/Ethiopia-Yirgacheffe.jpg", 15000, "커피원두", "example@example.com");
        menuRepository.save(menu1);
        stockRepository.save(new Stock(menu1, 100));

        Menu menu2 = new Menu("콜롬비아 수프리모", "/uploads/Colombia Supremo.jpg", 18000, "커피원두", "example@example.com");
        menuRepository.save(menu2);
        stockRepository.save(new Stock(menu2, 50));

        Menu menu3 = new Menu("브라질 산토스", "/uploads/Brazil Santos.jpg", 12000, "커피원두", "example@example.com");
        menuRepository.save(menu3);
        stockRepository.save(new Stock(menu3, 3));
    }

    @Transactional
    public void work2() {
        // 이 메서드가 심는 것은 점주 계정이다. 가드도 점주를 센다. work1 이 메뉴를 세는 것과 같은 규칙이다.
        if (ownerRepository.count() > 0) return;

        // 값이 비어 있으면 아무도 로그인할 수 없는 계정이 생긴다. 조용히 넘어가면 로그인이 왜 안 되는지
        // 한참 뒤에 찾게 되므로 기동을 멈춘다. 키 자체가 없으면 그보다 앞서 placeholder 해석에서 멈춘다.
        if (seedOwnerEmail.isBlank() || seedOwnerPassword.isBlank()) {
            throw new IllegalStateException(".env 의 OWNER_EMAIL 과 OWNER_PASSWORD 를 채워야 dev 점주 계정을 심을 수 있습니다.");
        }

        // 평문을 넘기는 것이 맞다. 해시는 Owner 생성자가 만든다
        ownerRepository.save(new Owner(seedOwnerEmail, seedOwnerPassword, passwordEncoder));
    }

}
