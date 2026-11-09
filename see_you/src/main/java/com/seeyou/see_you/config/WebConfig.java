package com.seeyou.see_you.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${upload.path:upload}")
    private String uploadPath;

    /** 上传文件的真实落盘位置（生产为持久化卷 /app/frontend-public）。 */
    @Value("${upload.front-path:}")
    private String frontPublicPath;

    /**
     * 前端构建产物 dist 目录。
     * 云端由 Dockerfile 的 Node 阶段构建后拷入 /app/web/dist；
     * 本地开发默认不存在，对应资源处理器不注册即可，不影响本地后端启动。
     */
    @Value("${web.dist-path:/app/web/dist}")
    private String distPath;

    /**
     * 只读媒体前缀：与 UploadController.ALLOWED_DIRS 对齐。
     * 访问顺序：先查持久化卷（用户新上传），再查 dist 内置素材（默认头像/背景/内置音视频封面）。
     */
    private static final String[] MEDIA_DIRS = {
            "banner", "cover", "avatar", "bg", "img", "diary", "user", "video", "audio"
    };

    private String fileLocation(File dir) {
        return "file:" + dir.getAbsolutePath().replace('\\', '/') + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ===== 1) 兼容旧链路：/upload/**（小程序端等历史入口）=====
        File legacyRoot = new File(System.getProperty("user.dir"), uploadPath);
        String legacyLoc = "file:" + legacyRoot.getAbsolutePath().replace('\\', '/') + "/";

        if (frontPublicPath != null && !frontPublicPath.isBlank()) {
            File frontDir = new File(frontPublicPath);
            if (frontDir.isDirectory()) {
                registry.addResourceHandler("/upload/**")
                        .addResourceLocations(legacyLoc, fileLocation(frontDir));
                return;
            }
        }
        registry.addResourceHandler("/upload/**").addResourceLocations(legacyLoc);

        File distDir = new File(distPath);

        // ===== 2) 前端构建产物：index.html、/assets/**、/vite.svg =====
        if (distDir.isDirectory()) {
            File assetsDir = new File(distDir, "assets");
            registry.addResourceHandler("/index.html").addResourceLocations(fileLocation(distDir));
            if (assetsDir.isDirectory()) {
                registry.addResourceHandler("/assets/**").addResourceLocations(fileLocation(assetsDir));
            }
            registry.addResourceHandler("/vite.svg").addResourceLocations(fileLocation(distDir));
        }

        // ===== 3) 只读媒体：先卷目录（用户上传），后 dist 内置素材兜底 =====
        File frontDir = (frontPublicPath != null && !frontPublicPath.isBlank()) ? new File(frontPublicPath) : null;
        for (String dirName : MEDIA_DIRS) {
            List<String> locations = new ArrayList<>();
            if (frontDir != null) {
                File uploaded = new File(frontDir, dirName);
                if (uploaded.isDirectory()) {
                    locations.add(fileLocation(uploaded));
                }
            }
            File builtIn = new File(distDir, dirName);
            if (builtIn.isDirectory()) {
                locations.add(fileLocation(builtIn));
            }
            if (!locations.isEmpty()) {
                registry.addResourceHandler("/" + dirName + "/**")
                        .addResourceLocations(locations.toArray(new String[0]));
            }
        }
    }
}
