package com.cafekiosk.menu.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 메뉴 가격 규칙 단위 테스트.
 * Spring 컨텍스트 없이 순수 POJO 로 경계값만 검증한다.
 *
 * 가격은 0원 이상 10,000,000원 이하다. FR-MNU-03.
 * 이 규칙이 등록 컨트롤러의 손 검사와 수정 DTO 의 어노테이션으로 흩어져 있던 때, 두 경로가
 * 서로 다른 규칙을 따랐다. 등록은 0원을 받고 수정은 거절했으며, 상한은 등록만 지켰다.
 * 그래서 생성자와 modify 를 같은 경계값으로 나란히 본다.
 */
class MenuTest {

    private Menu newMenu(int price) {
        return new Menu("브라질 산토스", "/uploads/Brazil Santos.jpg", price, "커피원두", "test@example.com");
    }

    @Test
    @DisplayName("가격 0원과 10,000,000원으로 메뉴를 만들 수 있다")
    void should_경계값_가격으로_만들수있다() {
        assertThat(newMenu(0).getMenuPrice()).isZero();
        assertThat(newMenu(10_000_000).getMenuPrice()).isEqualTo(10_000_000);
    }

    @Test
    @DisplayName("음수 가격으로는 메뉴를 만들 수 없다")
    void should_음수_가격으로_만들면_예외다() {
        assertThatThrownBy(() -> newMenu(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("10,000,000원을 넘는 가격으로는 메뉴를 만들 수 없다")
    void should_상한을_넘는_가격으로_만들면_예외다() {
        assertThatThrownBy(() -> newMenu(10_000_001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("가격을 0원과 10,000,000원으로 고칠 수 있다")
    void should_경계값_가격으로_고칠수있다() {
        Menu menu = newMenu(12000);

        menu.modify("브라질 산토스", 0, "/uploads/Brazil Santos.jpg", "커피원두");
        assertThat(menu.getMenuPrice()).isZero();

        menu.modify("브라질 산토스", 10_000_000, "/uploads/Brazil Santos.jpg", "커피원두");
        assertThat(menu.getMenuPrice()).isEqualTo(10_000_000);
    }

    @Test
    @DisplayName("범위를 벗어난 가격으로 고치려 하면 예외가 나고 메뉴는 그대로다")
    void should_범위를_벗어난_가격으로_고치면_예외이고_메뉴는_그대로다() {
        Menu menu = newMenu(12000);

        assertThatThrownBy(() -> menu.modify("바뀐 이름", 10_000_001, "바뀐 이미지", "바뀐 분류"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> menu.modify("바뀐 이름", -1, "바뀐 이미지", "바뀐 분류"))
                .isInstanceOf(IllegalArgumentException.class);

        // 가격만 거절하고 이름은 바꿔 두면 반쯤 고쳐진 메뉴가 남는다. 판정이 대입보다 앞서야 한다.
        // Stock 이 부족할 때 예외를 던지고 수량을 건드리지 않는 것과 같은 규칙이다
        assertThat(menu.getMenuName()).isEqualTo("브라질 산토스");
        assertThat(menu.getMenuPrice()).isEqualTo(12000);
        assertThat(menu.getImgUrl()).isEqualTo("/uploads/Brazil Santos.jpg");
        assertThat(menu.getCategory()).isEqualTo("커피원두");
    }
}
