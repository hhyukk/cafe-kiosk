package com.cafekiosk.menu.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Table(name = "menu")
@Getter
@NoArgsConstructor
@AllArgsConstructor
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
