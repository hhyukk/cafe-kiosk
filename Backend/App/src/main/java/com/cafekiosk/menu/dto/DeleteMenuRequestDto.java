package com.cafekiosk.menu.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DeleteMenuRequestDto {
    @NotBlank
    @Email
    String email;

    @JsonIgnore
    private Long menuId;
}
