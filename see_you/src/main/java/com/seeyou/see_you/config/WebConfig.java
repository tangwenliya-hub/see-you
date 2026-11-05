package com.seeyou.see_you.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${upload.path:upload}")
    private String uploadPath;

    /**
     * 上传文件的真实落盘位置（frontend/public）。H5 端由 Vite dev server 同源直接访问，
     * 小程序端没有 Vite，统一走后端 /upload/** 静态映射（该前缀已在 JWT 放行列表）。
     */
    @Value("${upload.front-path:}")
    private String frontPublicPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 和 UploadController 用同一个根目录，确保一致
        File rootDir = new File(System.getProperty("user.dir"), uploadPath);
        String absPath = rootDir.getAbsolutePath();

        // /upload/** 挂两个查找目录：先查后端 upload/（banner 等历史文件），再查前端 public/
        // （头像 /user/xxx.jpg、背景 /bg/xxx.jpg 实际落在这里）。
        // Controller（/upload/file、/upload/list）优先级高于 resource handler，互不影响。
        if (frontPublicPath != null && !frontPublicPath.isBlank()) {
            File frontDir = new File(frontPublicPath);
            if (frontDir.exists() && frontDir.isDirectory()) {
                registry.addResourceHandler("/upload/**")
                        .addResourceLocations(
                                "file:" + absPath + "/",
                                "file:" + frontDir.getAbsolutePath().replace('\\', '/') + "/");
                return;
            }
        }
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + absPath + "/");
    }
}