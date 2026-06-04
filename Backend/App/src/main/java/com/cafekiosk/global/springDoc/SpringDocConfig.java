package com.cafekiosk.global.springDoc;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "카페 메뉴 관리 서비스",
        version = "v1.0.0",
        description = "주문, 결제, 메뉴 관리를 위한 API 서버 문서"
))
public class SpringDocConfig {
}
