package com.cafekiosk.menu.controller;

import com.cafekiosk.menu.dto.CreateMenuRequestDto;
import com.cafekiosk.menu.dto.DeleteMenuRequestDto;
import com.cafekiosk.menu.dto.MenuDto;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.service.MenuService;
import com.cafekiosk.global.rsData.RsData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import static com.cafekiosk.menu.dto.MenuDto.*;

@Tag(name = "Menu API", description = "메뉴 조회, 생성, 수정, 삭제를 담당하는 API 그룹입니다.")
@RequestMapping("/api/menu")
@RestController
@RequiredArgsConstructor
public class MenuController {
    private final MenuService menuService;

    @Operation(
            summary = "메뉴 전체 조회",
            description = "판매중인 메뉴 목록을 남은 재고와 품절 여부까지 함께 반환합니다. "
                    + "재고 행이 없는 메뉴는 stock 이 null 이고 품절로 내려갑니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200-1", description = "조회 성공")
    })
    @GetMapping
    public List<MenuListResponse> getMenus() {
        // 재고를 맞추는 일은 서비스 트랜잭션 안에서 끝난다. 여기서 조립하면
        // 영속성 컨텍스트 밖이라 재고를 붙일 때마다 메뉴를 다시 읽을 여지가 생긴다.
        return menuService.findAllWithStock();
    }

    @Operation(summary = "메뉴 정보 수정", description = "특정 메뉴의 이름, 가격, 이미지 URL, 카테고리 정보를 업데이트합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200-1", description = "수정 완료"),
            @ApiResponse(responseCode = "400-1", description = "잘못된 입력 값 (Validation 실패)"),
            @ApiResponse(responseCode = "404-1", description = "수정할 메뉴를 찾을 수 없음")
    })
    @PutMapping("/modify/{id}")
    public ResponseEntity<RsData<MenuModifyResponse>> modifyMenu(
            @Parameter(description = "수정할 메뉴의 고유 ID")
            @PathVariable
            long id,
            @Valid
            @RequestBody
            MenuModifyRequest req
    ) {
        Menu menu = menuService
                .findById(id).get();
        boolean ok = menuService.modify(
                menu,
                req.menuName(),
                req.menuPrice(),
                req.imgUrl(),
                req.category(),
                req.email()
                );
        if(ok){
            RsData<MenuModifyResponse> rs = new RsData<>(
                    "200-1",
                    "메뉴를 수정하였습니다.",
                    new MenuModifyResponse(menu)
            );

            return ResponseEntity.ok(rs);
        }
        else return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    }


    @PostMapping //추가기능
    public ResponseEntity<String> createMenu(
            @Valid @RequestBody CreateMenuRequestDto req
    ) {
        // 품목 제안 최대 값 10,000,000원 검증
        if (req.getPrice() < 0 || req.getPrice() > 10_000_000) {
            return ResponseEntity
                    .badRequest()
                    .body("메뉴 가격은 0원 이상 10,000,000원 이하만 가능합니다.");
        }

        menuService.createMenu(req);
        return ResponseEntity.status(HttpStatus.CREATED).body("생성 완료되었습니다.");
    }

    @DeleteMapping("/delete/{menu_id}")
    public ResponseEntity<String> deleteMenu(
            @PathVariable Long menu_id,
            @RequestBody @Valid DeleteMenuRequestDto req
    ) {
        req.setMenuId(menu_id);

        boolean ok = menuService.deleteMenu(req);

        if (ok) { //validation 통과
            return ResponseEntity.status(HttpStatus.OK).body("삭제되었습니다.");
        }
        // validation 실패
         return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("이메일이 잘못되었거나 삭제 권한이 없습니다.");
    }
}
