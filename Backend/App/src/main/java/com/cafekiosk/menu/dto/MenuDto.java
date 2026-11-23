package com.cafekiosk.menu.dto;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.stock.entity.Stock;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


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
            String imgUrl,
            Integer stock,
            @JsonProperty("sold_out")
            boolean soldOut
    ) {
        /**
         * 메뉴와 그 재고로 목록 한 줄을 만든다. 재고가 없으면 stock 인자가 null 이다.
         *
         * 재고 행이 없는 메뉴의 stock 을 0 으로 적지 않는 이유는 응답이 남은 수량 0 이라고
         * 말하는데 실제로는 행이 없는 상태가 되기 때문이다. ADR-0003 이 버린 대안 그대로다.
         * 없다는 사실을 null 로 그대로 내보내고, 팔 수 없다는 것만 품절로 말한다.
         *
         * 수량이 있을 때의 품절 판정은 Stock 이 한다. 여기서 quantity 를 꺼내 비교하지 않는다.
         *
         * 메뉴만 받는 생성자를 남기지 않는 이유는 재고를 모르는 호출자가 조용히
         * sold_out false 를 만들 수 있기 때문이다. 이 DTO 를 만드는 자리는 재고를 답해야 한다.
         */
        public MenuListResponse(Menu menu, Stock stock) {
            this(
                    menu.getId(),
                    menu.getCategory(),
                    menu.getMenuName(),
                    menu.getMenuPrice(),
                    menu.getImgUrl(),
                    stock == null ? null : stock.getQuantity(),
                    stock == null || stock.isSoldOut()
            );
        }
    }

    // 이메일 컴포넌트가 없는 것이 결정이다. 누가 고칠 수 있는지는 토큰으로만 판단하고
    // 요청 본문의 이메일을 등록자와 비교하지 않는다. FR-AUTH-05.
    public record MenuModifyRequest(
            @JsonProperty("menu_id")
            Long menuId,
            @NotBlank(message = "메뉴 이름을 입력하세요.")
            @JsonProperty("menu_name")
            String menuName,
            // 범위는 Menu 가 판정하고 여기서는 값이 왔는지만 본다. int 로 받으면 빠진 가격이 0 이 되어
            // 정상 가격처럼 통과하므로 Integer 로 받아 빠진 것을 null 로 가른다
            @NotNull(message = "가격을 입력하세요.")
            @JsonProperty("price")
            Integer menuPrice,
            @JsonProperty("img_url")
            String imgUrl,
            @NotBlank(message = "카테고리를 입력하세요.")
            String category

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
