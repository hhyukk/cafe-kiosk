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
    // 쓰기 메서드는 클래스 레벨의 readOnly = true 를 반드시 덮어써야 한다.
    // readOnly 트랜잭션에서는 Hibernate 가 flush 를 하지 않아 INSERT 와 DELETE 가
    // 예외 없이 조용히 사라진다. 200 을 응답하고 행은 그대로 남는 형태로 깨진다.
    //
    // 지금까지 이 두 메서드가 동작한 유일한 이유는 MenuController 의 @Transactional 이
    // read-write 트랜잭션을 먼저 열어 안쪽 readOnly 가 무시됐기 때문이다.
    // 낙관적 락 재시도는 롤백된 트랜잭션 밖 새 트랜잭션에서 일어나야 하므로(NFR-CON-05)
    // 그 컨트롤러 트랜잭션을 걷어냈고, 서비스가 자기 경계를 직접 갖는다.
    @Transactional
    public void createMenu(CreateMenuRequestDto req) {
        Menu menu = new Menu(req.getMenuName(),req.getImageURL(),req.getPrice(),req.getCategory(),req.getEmail());
        menuRepository.save(menu);
    }

    // 삭제 성공 시 True, 아니면 False return
    @Transactional
    public boolean deleteMenu(DeleteMenuRequestDto req) {
        if (req.getMenuId() == null || req.getEmail() == null) return false;
        return menuRepository.deleteByIdAndEmail(req.getMenuId(), req.getEmail()) == 1;
    }
}
