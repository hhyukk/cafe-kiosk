package com.cafekiosk.menu.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "menu")
@Getter
@NoArgsConstructor
public class Menu extends BaseEntity {
    // 메뉴 가격의 범위다. FR-MNU-03. 판정은 아래 requireValidPrice 하나가 하고, 등록 컨트롤러처럼
    // 범위를 앞단에서 다시 보는 자리는 숫자를 직접 적지 않고 이 상수를 가져다 쓴다
    public static final int MIN_PRICE = 0;
    public static final int MAX_PRICE = 10_000_000;

    @NotNull
    String menuName;
    String imgUrl;
    @NotNull
    int menuPrice;
    @NotNull
    String category;
    // 등록자 기록이다. 값은 컨트롤러가 토큰의 sub 에서 꺼내 넘기고 요청 본문에서 받지 않는다.
    // 권한 판단에 쓰지 않으므로 owner 를 외래키로 가리키지도 않는다. docs/ERD.md 3-3 참고.
    @NotNull
    String email;

    /**
     * 판매 중단 시각. NULL 이면 판매중이다.
     *
     * 메뉴를 하드 삭제하지 않는 이유는 order_item.menu_id 와 stock.menu_id 가
     * 이 행을 참조하기 때문이다. 참조가 있는 메뉴는 외래키 위반으로 지워지지 않고,
     * 참조를 끊고 지우면 과거 주문이 무엇을 팔았는지 알 수 없게 된다.
     * 그래서 삭제를 행을 없애는 일이 아니라 판매를 중단하는 일로 정의한다.
     *
     * boolean 이 아니라 nullable timestamp 인 이유는 둘이다.
     * 언제 내렸는지가 함께 남고, 판매중 판정이 deleted_at is null 한 줄로 끝난다.
     */
    LocalDateTime deletedAt;

    // Lombok 의 @AllArgsConstructor 를 쓰지 않는다. deletedAt 은 생성 시점에
    // 언제나 NULL 이어야 하는데, 전체 필드 생성자를 두면 호출자가 처음부터
    // 판매 중단된 메뉴를 만들 수 있는 경로가 생긴다.
    public Menu(
            String menuName,
            String imgUrl,
            int menuPrice,
            String category,
            String email
    ) {
        requireValidPrice(menuPrice);
        this.menuName = menuName;
        this.imgUrl = imgUrl;
        this.menuPrice = menuPrice;
        this.category = category;
        this.email = email;
    }

    /**
     * 판매를 중단한다. 이미 중단된 메뉴라면 시각을 덮어쓰지 않는다.
     * 처음 내린 시각이 진짜 정보이고, 두 번째 요청이 그것을 지울 이유가 없다.
     */
    public void discontinue() {
        if (deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }

    public boolean isDiscontinued() {
        return deletedAt != null;
    }

    public void modify(
            String menuName,
            int menuPrice,
            String imageUrl,
            String category
    ) {
        // 판정이 대입보다 앞서야 한다. 가격에서 터졌는데 이름은 이미 바뀐 반쪽짜리 메뉴를 남기지 않는다
        requireValidPrice(menuPrice);
        this.menuName = menuName;
        this.menuPrice = menuPrice;
        this.imgUrl = imageUrl;
        this.category = category;
    }

    /**
     * 가격 규칙의 유일한 판정 자리다. 0원 이상 10,000,000원 이하. FR-MNU-03.
     *
     * 한때 이 규칙이 등록 컨트롤러의 손 검사와 수정 DTO 의 @Positive 로 흩어져 있었고, 두 경로가
     * 서로 다른 규칙을 따랐다. 등록은 0원을 받고 수정은 거절했으며, 상한은 등록만 지켰다.
     * 메뉴가 만들어지고 바뀌는 모든 경로가 이 엔티티를 지나므로 판정을 여기 하나로 모은다.
     *
     * IllegalArgumentException 을 던지는 것은 Stock 의 수량 규칙과 같고, GlobalExceptionHandler 가
     * 그것을 400-1 로 옮긴다. 필드에 @Min 과 @Max 를 다는 방식은 쓰지 않는다. 그 검사는 부르는 순간이
     * 아니라 Hibernate 가 엔티티를 쓸 때 돌고, 거기서 나는 ConstraintViolationException 은
     * GlobalExceptionHandler 에 매핑이 없다.
     */
    private static void requireValidPrice(int menuPrice) {
        if (menuPrice < MIN_PRICE || menuPrice > MAX_PRICE) {
            throw new IllegalArgumentException(
                    "메뉴 가격은 0원 이상 10,000,000원 이하여야 합니다: " + menuPrice);
        }
    }
}
