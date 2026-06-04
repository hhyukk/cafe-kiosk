package com.cafekiosk.menu.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class CreateMenuRequestDto {
    @NotBlank(message = "이메일을 입력하세요.")
    @Email(message = "유효하지 않은 이메일 형식입니다.")
    String email;

    @NotBlank(message = "카테고리를 입력하세요.")
    String category;

    @NotBlank(message = "메뉴 이름을 입력하세요.")
    String menuName;

    int price;

    String imageURL;
}
