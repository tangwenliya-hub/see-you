package com.seeyou.see_you.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeyou.see_you.entity.SysLog;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.mapper.SysLogMapper;
import com.seeyou.see_you.mapper.SysUserMapper;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 后台操作日志切面。
 *
 * <p>记录管理员在 /admin/ 下所有写操作（POST / PUT / DELETE）、
 * 被 {@link AdminWrite} 标注的 GET 操作，以及登录行为。
 * 日志写入失败绝不影响业务主流程，全部异常都会被吞掉。</p>
 *
 * <p>隐私红线：请求参数中的密码、验证码、token 一律打码后再落库。</p>
 */
@Aspect
@Component
public class AdminLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AdminLogAspect.class);

    /** 需要打码的字段名（不分大小写） */
    private static final Set<String> MASK_KEYS = new HashSet<>(Arrays.asList(
            "password", "newPassword", "oldPassword", "confirmPassword",
            "captchaCode", "code", "token", "authorization", "secret"
    ));

    /** URI 前缀 -> 模块名 */
    private static final Map<String, String> MODULE_MAP = new LinkedHashMap<>();
    static {
        MODULE_MAP.put("/admin/alert", "情绪预警");
        MODULE_MAP.put("/admin/sense", "无感记录");
        MODULE_MAP.put("/admin/lexicon", "情绪词典");
        MODULE_MAP.put("/admin/settings", "系统参数");
        MODULE_MAP.put("/admin/aichat", "AI 对话");
        MODULE_MAP.put("/admin/record", "测评记录");
        MODULE_MAP.put("/admin/security", "账号安全");
        MODULE_MAP.put("/admin/privacy", "隐私审计");
        MODULE_MAP.put("/admin/interaction", "互动统计");
        MODULE_MAP.put("/admin/banner", "轮播公告");
        MODULE_MAP.put("/admin/log", "操作日志");
        MODULE_MAP.put("/admin/user", "用户管理");
        MODULE_MAP.put("/admin/avatar", "头像审核");
        MODULE_MAP.put("/admin/dashboard", "数据大盘");
        MODULE_MAP.put("/diary/admin", "日记管理");
        MODULE_MAP.put("/comment/", "评论管理");
        MODULE_MAP.put("/feedback/", "用户反馈");
        MODULE_MAP.put("/video/", "心理视频");
        MODULE_MAP.put("/audio/", "治愈音频");
        MODULE_MAP.put("/paper/", "测评量表");
        MODULE_MAP.put("/question/", "测评题目");
        MODULE_MAP.put("/option/", "题目选项");
        MODULE_MAP.put("/user/import", "用户导入");
        MODULE_MAP.put("/user/login", "登录");
        MODULE_MAP.put("/auth/login", "登录");
    }

    @Resource private SysLogMapper sysLogMapper;
    @Resource private SysUserMapper sysUserMapper;
    private final ObjectMapper om = new ObjectMapper();

    /** 后台所有接口 + 登录 + 资源写操作 */
    @Pointcut("execution(* com.seeyou.see_you.controller..*(..))")
    public void controllerMethods() { }

    @Around("controllerMethods()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            return pjp.proceed();
        }
        String uri = req.getRequestURI();
        String method = req.getMethod();
        if (!shouldLog(uri, method, pjp)) {
            return pjp.proceed();
        }

        long begin = System.currentTimeMillis();
        Integer status = 1;
        String errorMsg = null;
        Object result = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable e) {
            status = 0;
            errorMsg = String.valueOf(e.getMessage());
            if (errorMsg != null && errorMsg.length() > 400) {
                errorMsg = errorMsg.substring(0, 400);
            }
            throw e;
        } finally {
            // 业务失败也要记失败：控制器没抛异常、但返回 Result.error(...)（code != 200）
            // 例如「用户不存在」「无需重置」「只读管理员无权限」——旧实现全部记成成功
            if (status == 1 && result instanceof Result<?> r
                    && r.getCode() != null && r.getCode() != 200) {
                status = 0;
                errorMsg = r.getMsg();
            }
            try {
                writeLog(req, uri, method, pjp, status, errorMsg, System.currentTimeMillis() - begin);
            } catch (Exception ignore) {
                // 日志写失败不能影响业务
            }
        }
    }

    /** 只记录写操作与登录，读操作不记（否则日志表会被列表查询刷爆） */
    private boolean shouldLog(String uri, String method, ProceedingJoinPoint pjp) {
        if (uri == null) return false;
        boolean login = uri.startsWith("/user/login") || uri.startsWith("/auth/login");
        if (login) return true;
        if (!uri.contains("/admin/")) return false;
        if (!"GET".equalsIgnoreCase(method)) return true;
        // GET 但标注了 @AdminWrite（强制下线、恢复默认等）
        try {
            MethodSignature ms = (MethodSignature) pjp.getSignature();
            Method m = ms.getMethod();
            return m.isAnnotationPresent(AdminWrite.class);
        } catch (Exception e) {
            return false;
        }
    }

    private void writeLog(HttpServletRequest req, String uri, String method,
                          ProceedingJoinPoint pjp, Integer status, String errorMsg, long cost) {
        SysLog l = new SysLog();
        String params = maskParams(pjp.getArgs());
        Object uid = req.getAttribute("userId");
        Long userId = uid == null ? null : Long.valueOf(String.valueOf(uid));
        l.setUserId(userId);
        if (userId != null) {
            try {
                SysUser u = sysUserMapper.selectById(userId);
                if (u != null) {
                    l.setUsername(u.getUsername());
                    l.setNickname(u.getNickname());
                }
            } catch (Exception ignore) { }
        } else {
            // 登录请求此时还没有 token，userId 为空；从参数里补上账号，
            // 否则登录日志的「操作人」列永远是「-」，无法追溯谁在尝试登录。
            String uname = extractField(params, "username");
            if (uname != null) l.setUsername(uname);
        }
        l.setModule(resolveModule(uri));
        l.setOperation(resolveOperation(uri, method));
        l.setDescription(resolveDescription(uri, method));
        l.setIp(clientIp(req));
        l.setMethod(method);
        l.setParams(params);
        l.setStatus(status);
        l.setErrorMsg(errorMsg);
        l.setCostTime(cost);
        l.setCreateTime(LocalDateTime.now());
        sysLogMapper.insert(l);
    }

    /** 从已序列化的参数 JSON 中取一个字符串字段（用于登录日志补操作人账号） */
    private String extractField(String json, String key) {
        if (json == null || json.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*\"([^\"]{1,64})\"")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String resolveModule(String uri) {
        for (Map.Entry<String, String> e : MODULE_MAP.entrySet()) {
            if (uri.startsWith(e.getKey())) return e.getValue();
        }
        return "其他";
    }

    private String resolveOperation(String uri, String method) {
        if (uri.startsWith("/user/login") || uri.startsWith("/auth/login")) return "登录";
        // 语义后缀必须优先于按 HTTP 方法兜底，否则「重置头像」「清理日志」这类 POST
        // 请求会被统一记成「新增」，操作类型列失去意义。
        if (uri.endsWith("/kick")) return "强制下线";
        if (uri.endsWith("/unlock")) return "解锁账号";
        if (uri.endsWith("/reset")) return "重置";
        if (uri.endsWith("/clear")) return "清理日志";
        if (uri.endsWith("/export")) return "导出";
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        return switch (m) {
            case "POST" -> "新增";
            case "PUT", "PATCH" -> "修改";
            case "DELETE" -> "删除";
            default -> "操作";
        };
    }

    private String resolveDescription(String uri, String method) {
        if (uri.startsWith("/user/login") || uri.startsWith("/auth/login")) return "账号登录";
        return resolveModule(uri) + " - " + resolveOperation(uri, method) + "：" + uri;
    }

    /** 参数序列化后打码敏感字段 */
    private String maskParams(Object[] args) {
        if (args == null || args.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            if (a == null) continue;
            // 跳过框架注入的对象
            if (a instanceof HttpServletRequest || a instanceof jakarta.servlet.http.HttpServletResponse) continue;
            try {
                String json = om.writeValueAsString(a);
                json = maskJson(json);
                if (json.length() > 900) json = json.substring(0, 900) + "…（已截断）";
                if (sb.length() > 0) sb.append(" | ");
                sb.append(json);
            } catch (Exception ignore) { }
        }
        return sb.length() > 1000 ? sb.substring(0, 1000) : sb.toString();
    }

    /** 简单可靠的打码：对已知敏感 key 的值替换为 *** */
    private String maskJson(String json) {
        String out = json;
        for (String k : MASK_KEYS) {
            // 匹配 "key":"value" 与 "key":value（数字/布尔）
            out = out.replaceAll("(?i)\"" + k + "\"\\s*:\\s*\"[^\"]*\"", "\"" + k + "\":\"***\"");
            out = out.replaceAll("(?i)\"" + k + "\"\\s*:\\s*[^,}\\]]+", "\"" + k + "\":\"***\"");
        }
        return out;
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            return null;
        }
    }
}
