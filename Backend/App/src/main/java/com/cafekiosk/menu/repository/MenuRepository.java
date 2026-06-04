package com.cafekiosk.menu.repository;


import com.cafekiosk.menu.entity.Menu;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    int deleteByIdAndEmail(Long menuId, @NotBlank @Email String email);
}
