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

    // 조회 경로는 판매중 메뉴만 본다. 판매 중단된 메뉴는 손님 화면 목록에서도 빠지고
    // 점주의 수정 대상에서도 빠진다. 행이 남아 있다는 사실은 과거 주문만 알면 된다.
    public List<Menu> findAll(){
        return menuRepository.findAllByDeletedAtIsNull();
    }

    public Optional<Menu> findById(Long id){
        return menuRepository.findByIdAndDeletedAtIsNull(id);
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
    // readOnly 트랜잭션에서는 Hibernate 가 flush 를 하지 않아 INSERT 도, 변경 감지가
    // 만들어 내는 UPDATE 도 예외 없이 조용히 사라진다.
    // 200 을 응답하고 행은 그대로인 형태로 깨진다.
    //
    // 삭제가 하드 삭제에서 소프트 삭제로 바뀌면서 사라지는 SQL 이 DELETE 에서
    // UPDATE 로 바뀌었을 뿐 함정의 모양은 같다. deleteMenu 의 @Transactional 을 떼면
    // deleted_at 이 채워지지 않은 채 200 이 나간다.
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

    /**
     * 메뉴 삭제. 성공 시 true, 아니면 false 를 돌려준다.
     *
     * 행을 지우지 않고 판매를 중단한다. 이미 중단된 메뉴는 조회에서 걸러지므로
     * 없는 메뉴와 같은 false 가 되고, 컨트롤러가 401 로 바꾼다.
     * 중단 판단 자체는 Menu.discontinue 가 소유하고 여기서는 부르기만 한다.
     */
    @Transactional
    public boolean deleteMenu(DeleteMenuRequestDto req) {
        if (req.getMenuId() == null || req.getEmail() == null) return false;
        return menuRepository.findByIdAndDeletedAtIsNull(req.getMenuId())
                .filter(menu -> menu.getEmail().equals(req.getEmail()))
                .map(menu -> {
                    menu.discontinue();
                    return true;
                })
                .orElse(false);
    }
}
