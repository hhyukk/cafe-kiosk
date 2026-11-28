package com.cafekiosk.menu.controller;

import com.cafekiosk.menu.dto.CreateMenuRequestDto;
import com.cafekiosk.menu.dto.MenuDto;
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
import org.springframework.security.core.Authentication;
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
    public RsData<List<MenuListResponse>> getMenus() {
        // 재고를 맞추는 일은 서비스 트랜잭션 안에서 끝난다. 여기서 조립하면
        // 영속성 컨텍스트 밖이라 재고를 붙일 때마다 메뉴를 다시 읽을 여지가 생긴다.
        return new RsData<>(
                "200-1",
                "메뉴 목록 조회 성공",
                menuService.findAllWithStock()
        );
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
        // 조회와 수정과 응답 조립이 모두 서비스 트랜잭션 안에서 끝난다. 엔티티는 컨트롤러로 나오지 않는다.
        // 없거나 판매 중단된 메뉴면 서비스가 던진 예외를 GlobalExceptionHandler 가 404 로 옮긴다
        MenuModifyResponse modified = menuService.modify(
                id,
                req.menuName(),
                req.menuPrice(),
                req.imgUrl(),
                req.category()
                );

        RsData<MenuModifyResponse> rs = new RsData<>(
                "200-1",
                "메뉴를 수정하였습니다.",
                modified
        );

        return ResponseEntity.ok(rs);
    }


    @PostMapping //추가기능
    public ResponseEntity<RsData<MenuCreateResponse>> createMenu(
            @Valid @RequestBody CreateMenuRequestDto req,
            Authentication authentication
    ) {
        // 등록자는 보안 필터가 검증한 토큰에서 꺼낸다. 토큰의 sub 가 이메일이라 getName() 이
        // 곧 등록자 이메일이다. 그렇게 정한 이유는 AccessTokenIssuer 주석에 있다.
        // 여기서 신원을 읽는 것은 기록하려는 것이지 권한을 판단하려는 것이 아니다. 누가 이 API 를
        // 부를 수 있는지는 SecurityConfig 의 경로 표가 이미 정했다.
        //
        // 서비스에는 문자열로 넘긴다. 서비스가 보안 컨텍스트를 직접 읽으면 그 메서드가 기대는
        // 입력 하나가 시그니처에서 사라진다.
        //
        // 가격 범위는 여기서 다시 보지 않는다. 판정은 Menu 하나가 하고, 벗어나면 엔티티가 던진 예외를
        // GlobalExceptionHandler 가 400-1 로 옮긴다. 수정과 같은 판정을 지나므로 두 경로가 갈리지 않는다.
        MenuCreateResponse created = menuService.createMenu(req, authentication.getName());

        RsData<MenuCreateResponse> rs = new RsData<>(
                "201-1",
                "생성 완료되었습니다.",
                created
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(rs);
    }

    @DeleteMapping("/delete/{menu_id}")
    public RsData<Void> deleteMenu(
            @PathVariable Long menu_id
    ) {
        // 실패 갈래가 여기 없다. 없거나 이미 판매 중단된 메뉴면 서비스가 예외를 던지고
        // GlobalExceptionHandler 가 404 로 옮긴다. 이유는 MenuService.deleteMenu 주석에 있다.
        menuService.deleteMenu(menu_id);
        return new RsData<>(
                "200-1",
                "삭제되었습니다."
        );
    }
}
