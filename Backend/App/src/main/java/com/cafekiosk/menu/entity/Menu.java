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
    @NotNull
    String menuName;
    String imgUrl;
    @NotNull
    int menuPrice;
    @NotNull
    String category;
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
        this.menuName = menuName;
        this.menuPrice = menuPrice;
        this.imgUrl = imageUrl;
        this.category = category;
    }
}
