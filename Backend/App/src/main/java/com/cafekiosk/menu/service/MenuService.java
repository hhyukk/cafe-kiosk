package com.cafekiosk.menu.service;


import com.cafekiosk.menu.dto.CreateMenuRequestDto;
import com.cafekiosk.menu.dto.DeleteMenuRequestDto;
import com.cafekiosk.menu.dto.MenuDto;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {
    private final MenuRepository menuRepository;

    public List<Menu> findAll(){
        return menuRepository.findAll();
    }

    public Optional<Menu> findById(Long id){
        return menuRepository.findById(id);
    }

    //이메일 유효성 검사 성공시 true 아니면 false
    @Transactional
    public boolean modify(
            Menu menu,
            String menuName,
            int menuPrice,
            String imageUrl,
            String category,
            String email
            ) {
        if (menu.getEmail().equals(email)) {
            menu.modify(menuName, menuPrice, imageUrl, category);
            return true;
        }
        else return false;
    }
    public void createMenu(CreateMenuRequestDto req) {
        Menu menu = new Menu(req.getMenuName(),req.getImageURL(),req.getPrice(),req.getCategory(),req.getEmail());
        menuRepository.save(menu);
    }

    // 삭제 성공 시 True, 아니면 False return
    public boolean deleteMenu(DeleteMenuRequestDto req) {
        if (req.getMenuId() == null || req.getEmail() == null) return false;
        return menuRepository.deleteByIdAndEmail(req.getMenuId(), req.getEmail()) == 1;
    }
}
