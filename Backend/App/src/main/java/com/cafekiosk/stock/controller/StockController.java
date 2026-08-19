package com.cafekiosk.stock.controller;

import com.cafekiosk.global.rsData.RsData;
import com.cafekiosk.stock.dto.StockDto;
import com.cafekiosk.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stock API", description = "메뉴별 재고를 조정하는 API 그룹입니다.")
@RequestMapping("/api/stock")
@RestController
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @Operation(
            summary = "재고 조정 (점주)",
            description = "특정 메뉴의 재고를 요청한 수량으로 맞춥니다. 증감이 아니라 절대값입니다. "
                    + "0 을 보내면 품절 처리가 되고 음수는 400 으로 거부됩니다. "
                    + "인증은 아직 붙지 않아 지금은 누구나 부를 수 있습니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조정 완료"),
            @ApiResponse(responseCode = "400", description = "수량 누락이나 음수"),
            @ApiResponse(responseCode = "404", description = "없는 메뉴이거나 판매가 중단된 메뉴")
    })
    // 컨트롤러에 @Transactional 을 붙이지 않는다. NFR-CON-05.
    // 200 한 갈래만 여기서 만들고 나머지 상태 코드는 전부 GlobalExceptionHandler 가 만든다.
    @PatchMapping("/{menuId}")
    public RsData<StockDto.AdjustResponse> adjustStock(
            @Parameter(description = "재고를 조정할 메뉴의 고유 ID")
            @PathVariable long menuId,
            @Valid @RequestBody StockDto.AdjustRequest request
    ) {
        return new RsData<>(
                "200-1",
                "재고를 조정하였습니다.",
                stockService.adjust(menuId, request.quantity())
        );
    }
}
