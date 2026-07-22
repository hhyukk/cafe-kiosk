package com.cafekiosk.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /**
     * /uploads/** 를 두 곳에서 찾는다. 앞에 있는 위치가 우선한다.
     *
     * 1) file:{upload-dir}/ 는 FileUploadController 가 런타임에 저장하는 폴더.
     *    상대경로라 프로세스 작업 디렉토리 기준으로 풀리고, gitignore 대상이라 커밋되지 않는다.
     * 2) classpath:/static/uploads/ 는 리포지토리에 커밋된 시드 이미지(BaseInitData 가 참조).
     *    클래스패스라서 작업 디렉토리가 무엇이든, jar 로 패키징되든 항상 찾을 수 있다.
     *
     * 시드 이미지를 1) 에 두면 안 되는 이유:
     *   1) 은 작업 디렉토리에 따라 위치가 바뀌고(gradle bootRun 은 Backend/App/),
     *   gitignore 되어 있어 clone 한 팀원에게 파일이 전달되지 않는다.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(
                        "file:" + uploadDir + "/",
                        "classpath:/static/uploads/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}