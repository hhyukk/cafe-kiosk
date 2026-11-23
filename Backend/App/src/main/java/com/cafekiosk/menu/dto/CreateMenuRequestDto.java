package com.cafekiosk.menu.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

// 등록자 이메일 필드가 없는 것이 결정이다. 등록자는 보안 필터가 검증한 토큰의 sub 에서 오고
// MenuController 가 그 값을 서비스에 넘긴다. 본문으로 받으면 토큰을 가진 누구든 아무 이메일이나
// 등록자로 적을 수 있다. FR-MNU-06.
@Getter
public class CreateMenuRequestDto {
    @NotBlank(message = "카테고리를 입력하세요.")
    String category;

    @NotBlank(message = "메뉴 이름을 입력하세요.")
    String menuName;

    int price;

    String imageURL;
}
