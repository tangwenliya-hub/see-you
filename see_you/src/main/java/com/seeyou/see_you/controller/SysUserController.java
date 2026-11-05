package com.seeyou.see_you.controller;

import com.alibaba.excel.EasyExcel;
import lombok.extern.slf4j.Slf4j;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.seeyou.see_you.dto.UserImportDTO;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.service.AccountSecurityService;
import com.seeyou.see_you.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.util.JwtUtil;
import com.seeyou.see_you.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import com.seeyou.see_you.util.PageLimit;

@Slf4j
@RestController
@RequestMapping("/user")
public class SysUserController {
    @Resource
    private SysUserService sysUserService;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private AccountSecurityService securityService;
    private final BCryptPasswordEncoder bCrypt = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                             HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");
        String captchaKey = body.get("captchaKey");
        String captchaCode = body.get("captchaCode");
        if (username == null || password == null) {
            return Result.error(400, "账号或密码不能为空");
        }

        // 1. 锁定检查
        String lockMsg = securityService.checkLock(username);
        if (lockMsg != null) return Result.error(401, lockMsg);

        // 2. 服务端验证码校验
        if (!securityService.verifyCaptcha(captchaKey, captchaCode)) {
            return Result.error(400, "验证码不正确或已过期");
        }

        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .or().eq(SysUser::getEmail, username)
                .one();
        if (user == null) {
            String failMsg = securityService.recordFail(username);
            return Result.error(401, failMsg != null ? failMsg : "账号或密码错误");
        }

        // 0. 禁用/注销账号不允许登录（status=2 为用户自主注销，0 为管理员禁用）
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            return Result.error(403, Integer.valueOf(2).equals(user.getStatus())
                ? "该账号已注销" : "该账号已被禁用，请联系管理员");
        }

        // 1. 先试 BCrypt（新注册的账号）
        boolean loginOk = bCrypt.matches(password, user.getPassword());

        // 2. 如果 BCrypt 失败，再试明文（数据库里的老数据）
        if (!loginOk && password.equals(user.getPassword())) {
            loginOk = true;
            // 🔥 自动升级：把明文密码改成 BCrypt，以后就一直用加密了
            user.setPassword(bCrypt.encode(password));
            sysUserService.updateById(user);
        }

        if (!loginOk) {
            return Result.error(401, securityService.recordFail(username));
        }
        // 登录成功：清空失败计数
        securityService.clearFail(username);

        String token = jwtUtil.generateToken(user.getUserId(), user.getRole());
        // 记录本次登录会话（多设备管理）
        securityService.createSession(user.getUserId(), token, request);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        data.put("theme", user.getTheme());
        data.put("darkMode", user.getDarkMode());
        data.put("avatarFrame", user.getAvatarFrame());
        data.put("profileBg", user.getProfileBg());
        return Result.success(data);
    }

    /**
     * 当前登录用户信息（服务端权威数据）
     *
     * <p>存在意义：前端路由守卫判断「是不是管理员」时不能只信 localStorage —— 那个值
     * 用户按 F12 改一下就能让后台菜单/页面渲染出来（虽然接口会被 JWT 拦住拿不到数据，
     * 但页面结构与接口地址等于全部暴露）。所以进 /admin 前统一以本接口返回的 role 为准，
     * 顺手把本地 userInfo 里的 role 纠正回来。
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info(HttpServletRequest request) {
        Object uidObj = request.getAttribute("userId");
        if (uidObj == null) return Result.error(401, "未登录");
        Long uid = ((Number) uidObj).longValue();

        SysUser u = sysUserService.getById(uid);
        if (u == null) return Result.error(404, "用户不存在");

        Map<String, Object> data = new HashMap<>();
        data.put("userId", u.getUserId());
        data.put("username", u.getUsername());
        data.put("nickname", u.getNickname());
        data.put("avatar", u.getAvatar());
        data.put("email", u.getEmail());
        data.put("role", u.getRole());
        data.put("status", u.getStatus());
        data.put("theme", u.getTheme());
        data.put("darkMode", u.getDarkMode());
        data.put("avatarFrame", u.getAvatarFrame());
        data.put("profileBg", u.getProfileBg());
        return Result.success(data);
    }

    @GetMapping("/{id:\\d+}")
    public Result<SysUser> getById(@PathVariable Long id){
        SysUser u = sysUserService.getById(id);
        if (u == null) return Result.error(404, "用户不存在");
        // 敏感字段不下发：密码哈希/密保等一律抹掉，避免任意登录用户枚举他人账号信息
        u.setPassword(null);
        return Result.success(u);
    }
    @GetMapping("/list")
    public Result<Page<SysUser>> list(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size,
                                       @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getNickname, keyword)
                    .or().like(SysUser::getEmail, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> p = sysUserService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper);
        // 列表同样不出密码哈希
        if (p.getRecords() != null) p.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(p);
    }

    @PostMapping("/register")
    public Result<Boolean> register(@RequestBody SysUser sysUser){
        // 用户名预检查：库里 username 有唯一索引，不拦的话会触发 DuplicateKeyException，
        // 被全局处理器笼统翻成"数据操作失败"，用户完全不知道是重名
        if (sysUser.getUsername() == null || sysUser.getUsername().isBlank()) {
            return Result.error(400, "用户名不能为空");
        }
        boolean exists = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, sysUser.getUsername())) > 0;
        if (exists) return Result.error(400, "用户名已被注册，请换一个或直接登录");
        // 邮箱必填：忘记密码的验证码发到这里，没邮箱的账号无法自助找回，只能管理员重置
        String email = sysUser.getEmail() == null ? "" : sysUser.getEmail().trim();
        if (email.isBlank()) return Result.error(400, "邮箱不能为空（用于找回密码）");
        if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            return Result.error(400, "邮箱格式不正确");
        }
        // 邮箱唯一：一个邮箱找回时只能对应一个账号，否则验证码可能发错人
        boolean emailTaken = sysUserService.count(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, email)) > 0;
        if (emailTaken) return Result.error(400, "该邮箱已被其他账号绑定");
        sysUser.setEmail(email);
        sysUser.setRole(0);
        // BCrypt 加密密码
        sysUser.setPassword(bCrypt.encode(sysUser.getPassword()));
        // 默认头像
        if (sysUser.getAvatar() == null || sysUser.getAvatar().isBlank()) {
            sysUser.setAvatar("/img/1 (1).png");
        }
        boolean b = sysUserService.save(sysUser);
        return b ? Result.success() : Result.error(500, "注册失败");
    }

    /** 更新用户（编辑资料）
     *  越权防护：普通用户只能改自己的资料，且不能改 role / status / username；
     *           管理员可以改任何人（含改角色）。
     */
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody SysUser sysUser, HttpServletRequest request){
        if (sysUser.getUserId() == null) return Result.error(400, "userId不能为空");

        Long myId = (Long) request.getAttribute("userId");
        Integer myRole = (Integer) request.getAttribute("role");
        boolean isAdmin = Integer.valueOf(1).equals(myRole);

        if (!isAdmin) {
            // 只能改自己
            if (myId == null || !myId.equals(sysUser.getUserId())) {
                return Result.error(403, "只能修改自己的资料");
            }
            // 敏感字段一律不生效（MyBatis-Plus 只更新非空字段，置 null 即等于忽略）
            sysUser.setRole(null);
            sysUser.setStatus(null);
            sysUser.setUsername(null);
            // 没有任何可更新字段时 updateById 会拼出非法 SQL，这里提前返回友好提示
            if (sysUser.getNickname() == null && sysUser.getEmail() == null
                    && sysUser.getPhone() == null && sysUser.getAvatar() == null
                    && sysUser.getBio() == null && sysUser.getTheme() == null
                    && sysUser.getDarkMode() == null && sysUser.getAvatarFrame() == null
                    && sysUser.getProfileBg() == null) {
                return Result.error(400, "没有需要更新的内容");
            }
        }
        // 密码：普通用户不允许通过此接口改密码（须走 /user/changePassword 验证旧密码）；
        // 管理员编辑用户时可重置密码（BCrypt 加密落库），留空则不修改
        if (isAdmin && sysUser.getPassword() != null && !sysUser.getPassword().isBlank()) {
            if (sysUser.getPassword().length() < 6) {
                return Result.error(400, "新密码至少 6 位");
            }
            sysUser.setPassword(bCrypt.encode(sysUser.getPassword()));
        } else {
            sysUser.setPassword(null);
        }
        // 邮箱唯一性校验：资料页改邮箱也不许撞别人，否则找回密码按邮箱查询会命中多个账号
        if (sysUser.getEmail() != null && !sysUser.getEmail().isBlank()) {
            String email = sysUser.getEmail().trim();
            if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                return Result.error(400, "邮箱格式不正确");
            }
            Long taken = sysUserService.lambdaQuery()
                    .eq(SysUser::getEmail, email)
                    .ne(sysUser.getUserId() != null, SysUser::getUserId, sysUser.getUserId())
                    .count();
            if (taken > 0) return Result.error(400, "该邮箱已被其他账号绑定");
            sysUser.setEmail(email);
        }
        boolean b = sysUserService.updateById(sysUser);
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    /** 修改密码 */
    /** 注销账号（需密码二次确认）：账号匿名化并停用，所有设备立即下线 */
    @PutMapping("/deactivate")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deactivate(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String pwd = body.get("password");
        if (userId == null) return Result.error(401, "未登录");
        if (pwd == null || pwd.isBlank()) return Result.error(400, "请输入密码确认注销");
        SysUser user = sysUserService.getById(userId);
        if (user == null) return Result.error(404, "用户不存在");
        boolean ok = pwd.equals(user.getPassword()) || bCrypt.matches(pwd, user.getPassword());
        if (!ok) return Result.error(400, "密码错误");
        if (!Integer.valueOf(1).equals(user.getStatus())) return Result.error(400, "该账号已是注销/禁用状态");
        // 匿名化：身份字段清空（日记等历史数据保留，但不再关联到真实身份，满足隐私权要求）
        user.setStatus(2);
        user.setUsername("已注销用户_" + userId);
        user.setNickname("已注销用户");
        user.setEmail(null);
        user.setPhone(null);
        user.setAvatar(null);
        user.setBio(null);
        sysUserService.updateById(user);
        securityService.deleteAllSessions(userId);
        log.info("[账号注销] userId={} 已注销并匿名化", userId);
        return Result.success();
    }

    @PutMapping("/changePassword")    public Result<Boolean> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        // 安全：userId 一律以登录态为准，防止越权修改他人密码
        Long userId = (Long) request.getAttribute("userId");
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (userId == null) return Result.error(401, "未登录");
        if (oldPwd == null || newPwd == null || newPwd.length() < 6) {
            return Result.error(400, "新密码至少 6 位");
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) return Result.error(404, "用户不存在");
        // 验证旧密码（兼容 BCrypt 和明文）
        boolean ok = oldPwd.equals(user.getPassword()) || bCrypt.matches(oldPwd, user.getPassword());
        if (!ok) return Result.error(400, "旧密码错误");
        user.setPassword(bCrypt.encode(newPwd));
        boolean b = sysUserService.updateById(user);
        return b ? Result.success() : Result.error(500, "修改失败");
    }

    /** 单独改状态（启用/禁用） */
    @PutMapping("/status")
    public Result<Boolean> updateStatus(@RequestBody Map<String, Object> body){
        Long userId = Long.valueOf(body.get("userId").toString());
        Integer status = Integer.valueOf(body.get("status").toString());
        SysUser u = new SysUser();
        u.setUserId(userId);
        u.setStatus(status);
        boolean b = sysUserService.updateById(u);
        return b ? Result.success() : Result.error(500, "操作失败");
    }

    /** 删除用户 */
    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> delete(@PathVariable Long id) {
        boolean b = sysUserService.removeById(id);
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    /**
     * 下载导入模板
     */
    @GetMapping("/import/template")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("用户导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        // 模板表头 + 示例数据
        List<UserImportDTO> list = new ArrayList<>();
        UserImportDTO demo = new UserImportDTO();
        demo.setUsername("zhangsan");
        demo.setNickname("张三");
        demo.setPassword("123456");
        demo.setEmail("zhangsan@example.com");
        demo.setPhone("13800138000");
        demo.setRole("普通用户");
        list.add(demo);

        EasyExcel.write(response.getOutputStream(), UserImportDTO.class)
                .sheet("用户列表")
                .doWrite(list);
    }

    /**
     * Excel 批量导入用户
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importUsers(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error(400, "请上传文件");
        }

        final List<UserImportDTO> importList = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        final int[] successCount = {0};
        final int[] failCount = {0};

        try {
            EasyExcel.read(file.getInputStream(), UserImportDTO.class, new ReadListener<UserImportDTO>() {
                @Override
                public void invoke(UserImportDTO data, AnalysisContext context) {
                    int rowNum = context.readRowHolder().getRowIndex() + 1;
                    // 跳过第一行表头
                    if (rowNum == 1) return;

                    // 数据校验
                    if (data.getUsername() == null || data.getUsername().isBlank()) {
                        errors.add("第" + rowNum + "行：用户名不能为空");
                        failCount[0]++;
                        return;
                    }
                    if (data.getPassword() == null || data.getPassword().isBlank()) {
                        errors.add("第" + rowNum + "行：密码不能为空");
                        failCount[0]++;
                        return;
                    }
                    // 检查用户名是否已存在
                    SysUser exist = sysUserService.lambdaQuery()
                            .eq(SysUser::getUsername, data.getUsername())
                            .one();
                    if (exist != null) {
                        errors.add("第" + rowNum + "行：用户名 " + data.getUsername() + " 已存在");
                        failCount[0]++;
                        return;
                    }

                    importList.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 批量保存
                    for (UserImportDTO dto : importList) {
                        try {
                            SysUser user = new SysUser();
                            user.setUsername(dto.getUsername());
                            user.setNickname(dto.getNickname());
                            user.setPassword(bCrypt.encode(dto.getPassword()));
                            user.setEmail(dto.getEmail());
                            user.setPhone(dto.getPhone());
                            user.setRole("管理员".equals(dto.getRole()) ? 1 : 0);
                            user.setStatus(1);
                            user.setAvatar("/img/1 (1).png");
                            user.setTheme("green");
                            user.setDarkMode(0);
                            user.setAvatarFrame("none");
                            sysUserService.save(user);
                            successCount[0]++;
                        } catch (Exception e) {
                            // 只记录序号与用户名，不回传异常原文（可能含 SQL/表结构信息）
                            log.warn("导入用户失败，username={}", dto.getUsername(), e);
                            failCount[0]++;
                            errors.add("用户名 " + dto.getUsername() + "：导入失败，请检查数据格式是否合法");
                        }
                    }
                }
            }).sheet().doRead();
        } catch (Exception e) {
            log.error("导入用户整体失败", e);
            return Result.error(500, "导入失败，请检查文件格式是否为标准模板");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", successCount[0]);
        result.put("fail", failCount[0]);
        result.put("total", successCount[0] + failCount[0]);
        result.put("errors", errors);
        return Result.success(result);
    }
}