package com.cafekiosk.menu.dto;

import com.cafekiosk.menu.entity.Menu;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.aspectj.weaver.ast.Not;


public class MenuDto {

    public record MenuListResponse(
            @JsonProperty("menu_id")
            long menuId,
            String category,
            @JsonProperty("menu_name")
            String menuName,
            @JsonProperty("price")
            int menuPrice,
            @JsonProperty("img_url")
            String imgUrl
    ) {
        public MenuListResponse(Menu menu) {
            this(
                    menu.getId(),
                    menu.getCategory(),
                    menu.getMenuName(),
                    menu.getMenuPrice(),
                    menu.getImgUrl()
            );
        }
    }

    public record MenuModifyRequest(
            @JsonProperty("menu_id")
            Long menuId,
            @NotBlank(message = "메뉴 이름을 입력하세요.")
            @JsonProperty("menu_name")
            String menuName,
            @Positive(message = "가격은 0보다 커야 합니다.")
            @JsonProperty("price")
            int menuPrice,
            @JsonProperty("img_url")
            String imgUrl,
            @NotBlank(message = "카테고리를 입력하세요.")
            String category,
            @NotBlank(message = "이메일을 입력하세요.")
            String email

    ) {}

    public record MenuModifyResponse(
            @JsonProperty("menu_id")
            long menuId,
            @JsonProperty("menu_name")
            String menuName,
            @JsonProperty("price")
            int menuPrice,
            String category
    ) {
        public MenuModifyResponse(Menu menu) {
            this(
                    menu.getId(),
                    menu.getMenuName(),
                    menu.getMenuPrice(),
                    menu.getCategory()
            );
        }
    }
}
