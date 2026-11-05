package com.seeyou.see_you.controller;

import com.seeyou.see_you.util.BizException;
import com.seeyou.see_you.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 文件上传
 *
 * <p>安全约定（防止任意文件写入 / 路径穿越）：
 * <ol>
 *   <li>dir 只能取白名单内的值，杜绝 {@code dir=../../} 写到任意目录；</li>
 *   <li>扩展名按目录类型白名单校验，禁止上传 .jsp/.exe/.html 等可执行内容；</li>
 *   <li>单文件大小按类型限制，超出直接拒绝；</li>
 *   <li>落盘前用 getCanonicalPath 复核最终路径仍位于根目录之内。</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    /** 文件保存到前端 public/ 目录下的对应子文件夹，Vite dev server 可直接通过 /banner/xxx.jpg 访问 */
    @Value("${upload.front-path:./upload}")
    private String frontPublicPath;

    /** 允许上传的目录 -> 允许的扩展名 */
    private static final Map<String, Set<String>> ALLOWED_DIRS = new HashMap<>();

    /** 各目录单文件大小上限（字节） */
    private static final Map<String, Long> MAX_SIZE = new HashMap<>();

    static {
        Set<String> img = new HashSet<>(Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"));
        Set<String> video = new HashSet<>(Arrays.asList(".mp4", ".webm", ".mov", ".avi", ".mkv", ".flv", ".wmv", ".m4v"));
        Set<String> audio = new HashSet<>(Arrays.asList(".mp3", ".wav", ".ogg", ".m4a", ".flac", ".aac", ".wma"));

        ALLOWED_DIRS.put("banner", img);
        ALLOWED_DIRS.put("cover", img);
        ALLOWED_DIRS.put("avatar", img);
        ALLOWED_DIRS.put("bg", img);
        ALLOWED_DIRS.put("img", img);
        ALLOWED_DIRS.put("diary", img);
        ALLOWED_DIRS.put("user", img);
        ALLOWED_DIRS.put("video", video);
        ALLOWED_DIRS.put("audio", audio);

        MAX_SIZE.put("video", 200L * 1024 * 1024);   // 视频 200MB
        MAX_SIZE.put("audio", 50L * 1024 * 1024);    // 音频 50MB
        // 其余（图片类）默认 5MB
    }

    private static final long DEFAULT_MAX_SIZE = 5L * 1024 * 1024;

    /** 列出目录时的扩展名过滤（与上传白名单保持一致） */
    private static Set<String> extOf(String dir) {
        return ALLOWED_DIRS.getOrDefault(dir, ALLOWED_DIRS.get("banner"));
    }

    @PostMapping("/file")
    public Result<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dir", defaultValue = "banner") String dir) {

        if (file.isEmpty()) throw new BizException("文件不能为空");

        // 1) 目录白名单（同时挡掉 ../ 这类路径穿越写法）
        String safeDir = dir == null ? "banner" : dir.toLowerCase(Locale.ROOT).trim();
        Set<String> allowExt = ALLOWED_DIRS.get(safeDir);
        if (allowExt == null) {
            throw new BizException("不支持的上传目录：" + dir);
        }

        // 2) 扩展名白名单
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }
        if (ext.isEmpty() || !allowExt.contains(ext)) {
            throw new BizException("不支持的文件类型" + ext + "，仅允许：" + String.join("/", allowExt));
        }

        // 3) 大小限制
        long max = MAX_SIZE.getOrDefault(safeDir, DEFAULT_MAX_SIZE);
        if (file.getSize() > max) {
            throw new BizException("文件超过大小限制（最大 " + (max / 1024 / 1024) + "MB）");
        }

        try {
            File root = new File(frontPublicPath).getCanonicalFile();
            File dirFile = new File(root, safeDir).getCanonicalFile();

            // 4) 落盘前复核：最终目录必须仍在根目录之内（纵深防御）
            if (!dirFile.getPath().startsWith(root.getPath() + File.separator)) {
                throw new BizException("非法的上传路径");
            }
            if (!dirFile.exists() && !dirFile.mkdirs()) {
                throw new BizException("上传目录创建失败，请检查 upload.front-path 配置");
            }

            // 5) 随机文件名，避免覆盖与猜测
            String newName = UUID.randomUUID().toString().replace("-", "") + ext;
            File target = new File(dirFile, newName);
            file.transferTo(target);

            Map<String, String> data = new HashMap<>();
            data.put("url", "/" + safeDir + "/" + newName);
            data.put("name", newName);
            return Result.success(data);
        } catch (IOException e) {
            // 不回传 e.getMessage()：可能包含服务器绝对路径
            log.error("文件上传失败，dir={}", safeDir, e);
            throw new BizException("上传失败，请稍后重试");
        }
    }

    /**
     * 扫描目录返回已有文件列表（让前端弹窗显示缩略图网格供点选）
     * GET /upload/list?dir=banner
     */
    @GetMapping("/list")
    public Result<List<Map<String, String>>> listFiles(
            @RequestParam(value = "dir", defaultValue = "banner") String dir) {

        String safeDir = dir == null ? "banner" : dir.toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_DIRS.containsKey(safeDir)) {
            throw new BizException("不支持的目录：" + dir);
        }

        List<Map<String, String>> result = new ArrayList<>();
        File dirFile = new File(frontPublicPath, safeDir);
        if (dirFile.exists() && dirFile.isDirectory()) {
            final Set<String> finalAllow = extOf(safeDir);

            // 收集单个目录下的文件（sub 为空表示根目录）
            java.util.function.BiConsumer<File, String> collect = (File scanDir, String sub) -> {
                File[] files = scanDir.listFiles((fileDir, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return finalAllow.stream().anyMatch(lower::endsWith);
                });
                if (files == null) return;
                for (File f : files) {
                    Map<String, String> item = new HashMap<>();
                    item.put("name", (sub.isEmpty() ? "" : sub + "/") + f.getName());
                    item.put("url", "/" + safeDir + (sub.isEmpty() ? "" : "/" + sub) + "/" + f.getName());
                    item.put("size", f.length() + "");
                    item.put("time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(f.lastModified())));
                    result.add(item);
                }
            };

            // 根目录 + 一级子文件夹（如 cover/视频、cover/音频）都扫进来
            collect.accept(dirFile, "");
            File[] subs = dirFile.listFiles(File::isDirectory);
            if (subs != null) {
                Arrays.sort(subs, java.util.Comparator.comparing(File::getName));
                for (File subDir : subs) {
                    collect.accept(subDir, subDir.getName());
                }
            }

            // 按修改时间倒序，最新的排前面
            result.sort((a, b) -> b.get("time").compareTo(a.get("time")));
        }
        return Result.success(result);
    }
}
