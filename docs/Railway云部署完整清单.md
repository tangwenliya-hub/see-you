# 绪见 · Railway 云部署完整清单（傻瓜式照抄版）

> 平台：Railway（构建器为 Railpack，报错“Railpack 未能为建造做好准备”即出现在此平台）
> 适用仓库结构（monorepo，根目录不放任何构建文件，后端在子目录）：
>
> ```
> 仓库根/
> ├─ see_you/        ← Spring Boot 3.3.5 + Java 17 后端（pom.xml 在这一层）
> ├─ frontend/       ← Vue3 + Vite 前端（package.json 在这一层）
> ├─ miniapp/       ← UniApp 小程序，不部署到 Railway
> └─ sql 脚本实际位于 see_you/sql/
> ```
>
> 核心原则：**一个 Railway Web Service 只跑后端 `see_you`；前端单独建 Static Site，不要混进同一个服务。**

---

## 〇、一次性总览（要建几个服务、各自配什么）

| Railway 服务 | 类型 | Root Directory | 作用 |
|---|---|---|---|
| MySQL（你已建好，在线） | MySQL Database | — | 存业务数据；先初始化库表 |
| 再见（后端） | **Web Service**（从 GitHub 仓库连） | `see_you` | Spring Boot 接口 + WebSocket + 上传文件 |
| frontend（前端，后置可选） | Static Site | `frontend` | `npm run build` 后托管 `dist/` |

部署顺序：**① 先把数据库表结构导进去 → ② 配并部署后端 → ③ 确认后端公网地址通了 → ④ 再上前端静态站。**
顺序反了后端会在启动时报“连不上库 / 表不存在”。

---

## 一、先初始化 Railway MySQL（必须先做）

Railway 的 MySQL 不会自动执行你仓库里的 SQL，也没有内置建表页面，需要用外部客户端（Navicat / DBeaver / DataGrip）连上后执行。

### 1.1 拿连接信息

进入 Railway 的 **MySQL 服务 → Variables（变量）**，记下：
`MYSQLHOST`、`MYSQLPORT`、`MYSQLUSER`、`MYSQLPASSWORD`；
再到 MySQL 服务的 **Connect / Settings** 里拿 **Public Proxy Domain**（公网代理地址，供本地 Navicat 连接；Web 服务内部互联走 `MYSQLHOST`，不要用公网地址）。

### 1.2 用 Navicat 导入（库名固定 `see_you`）

1. 新建 MySQL 连接：主机填 Public Proxy Domain，端口 `MYSQLPORT`，用户名 `MYSQLUSER`，密码 `MYSQLPASSWORD`。
2. 连接后依次执行本地 `see_you/sql/` 下脚本，**顺序不能乱**：
   1. `base_schema.sql`（自带 `CREATE DATABASE IF NOT EXISTS see_you; USE see_you;`，先跑它会自动建库 + 15 张基础表）
   2. `migration/` 目录按**文件名日期从旧到新**逐个执行：
      ```
      20260901_create_like_table.sql
      20260902_update_database.sql
      20260903_add_banner.sql
      20261008_test_management_update.sql
      20261009_add_user_settings.sql
      20261009_update_scores.sql
      20261010_add_account_security.sql
      20261010_sys_log_table.sql
      20261015_add_sys_config.sql
      20261019_add_sense_and_alert.sql
      20261028_performance_indexes.sql
      20261028_seed_sense_demo.sql
      20261029_diary_day_rule.sql
      20261102_risk_alert_disposition.sql
      ```
   3. 三个初始化数据脚本：`add_60_scales_full.sql`、`add_ai_keywords.sql`、`add_test_papers.sql`
3. 导完在 `see_you` 库里 `show tables;` 应看到全部业务表；初始数据脚本不含管理员，注册后自行 `UPDATE sys_user SET role=1 WHERE username='你的账号'`，并设 ≥12 位强口令（仓库不记录任何账号密码）。

> 若你的 SQL 没有提交到 GitHub（`git ls-files see_you/sql` 为空也没关系）：SQL 只需要在本地用客户端导进远程库一次，部署时不依赖仓库里有没有它。

---

## 二、后端服务（“再见”）Settings 设置页 —— 三个字段

> 注意：Root Directory / Build Command / Start Command 是 **Settings 设置页的独立字段**，
> **不是**环境变量；不要写进 Variables 原始编辑器（`RAILWAY_WORKDIR`、`BUILD_COMMAND`、`START_COMMAND`
> 这类变量名 Railpack 不读取，属于无效配置）。

进入“再见”服务 → **Settings（设置）**：

| Settings 字段 | 填写值 | 说明 |
|---|---|---|
| Service Type | `Web Service` | 不能选 Static Site / Background Worker |
| Repository | `tangwenliya-hub/see-you`（你的 GitHub 仓库） | 确认已授权 |
| Branch | 你实际推送的分支（本地为 `master`，与 GitHub 默认分支保持一致） | 选错会拿不到最新代码 |
| **Root Directory** | `see_you` | **本次修复“Railpack 未能为建造做好准备”的关键**：让 Railpack 进入有 `pom.xml` 的目录识别 Java |
| **Build Command** | `mvn clean package -DskipTests` | 在 `see_you` 目录执行，产出 fat jar |
| **Start Command** | `java -jar target/see_you-0.0.1-SNAPSHOT.jar` | 工作目录随 Root Directory 定位到 `see_you`，相对路径有效；jar 名来自 pom：`see_you-0.0.1-SNAPSHOT.jar` |

保存 Settings。

### 2.1 Java 版本兜底（一般不用配）

`pom.xml` 已声明 `<java.version>17</java.version>`，Railpack 通常据此自动选 JDK 17。
万一构建日志报 `UnsupportedClassVersionError` 或 “requires Java 17”：
在 `see_you/` 目录新增文件 `.java-version`，内容只写一行 `17`，提交推送即可。

---

## 三、后端服务 Variables（变量）—— 可整段照抄后替换 3 处

进入“再见”服务 → **Variables（变量）→ 原始编辑器（环境环境 tab）**。

### 3.1 先做“引用”（否则 `${{MYSQLHOST}}` 展开为空）

Railway 不会让手写的 `${MYSQLHOST}` 生效，必须：
1. 在本服务 Variables 页点 **Add Reference / 添加引用**；
2. 选择已建好的 **MySQL 服务**；
3. 勾选 `MYSQLHOST`、`MYSQLPORT`、`MYSQLUSER`、`MYSQLPASSWORD` 引入本服务；
4. 变量值中再用双花括号 `${{MYSQLHOST}}` 这类语法交叉引用。

> 偷懒备选：直接把上面 4 个 MySQL 变量的**真实值**复制过来填死（毕设演示够用；缺点是改库密码要同步改这里）。

### 3.2 变量清单（KEY="VALUE" 逐行粘贴；替换 3 处占位）

```bash
SPRING_PROFILES_ACTIVE="prod"
SERVER_PORT="${{PORT}}"
DB_URL="jdbc:mysql://${{MYSQLHOST}}:${{MYSQLPORT}}/see_you?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
DB_USERNAME="${{MYSQLUSER}}"
DB_PASSWORD="${{MYSQLPASSWORD}}"
JWT_SECRET="【替换为32位以上随机十六进制字符串，例如随机64个十六进制字符】"
AI_API_KEY="【替换为你的DeepSeek API Key】"
AI_API_URL="https://api.deepseek.com/v1/chat/completions"
AI_MODEL="deepseek-chat"
UPLOAD_PATH="/app/upload"
FRONT_PATH="/app/frontend-public"
CORS_ORIGINS="*"
NO_CACHE="1"
```

### 3.3 每项作用与必填性

| 变量 | 必填 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | ✅ | 切生产配置：关 SQL 日志、上传走 `/app/upload`；`prod.yml` 数据源再由下面 `DB_URL` 覆盖 |
| `SERVER_PORT=${{PORT}}` | ✅ | **最容易漏、漏了会“构建成功但服务不健康”**：Railway 注入 `PORT`，必须让 Spring 绑定它（代码 `server.port=${SERVER_PORT:8081}` 只读 `SERVER_PORT`，不读 `PORT`，所以必须显式设） |
| `DB_URL` | ✅ | 库名固定 `see_you`；内部连接 `useSSL=false&allowPublicKeyRetrieval=true`，避免 MySQL8 caching_sha2 握手失败 |
| `DB_USERNAME` / `DB_PASSWORD` | ✅ | 引用 MySQL 变量，禁止用占位符 `__SET_DB_PASSWORD__` |
| `JWT_SECRET` | ✅ | 不给默认密钥；不配启动会直接失败；长度 ≥32，且不要复用本地弱密钥 |
| `AI_API_KEY` | 可选 | 不配只是 AI 聊天不可用，其余功能正常 |
| `AI_API_URL` / `AI_MODEL` | 可选 | 默认值已合理，保留即可 |
| `UPLOAD_PATH` / `FRONT_PATH` | 建议 | 与 `prod.yml` 对齐；注意 Railway 临时文件系统在重启后非挂载卷内容会清空，用户头像/封面建议挂载 Volume 持久化（见第六节） |
| `CORS_ORIGINS` | 视前端 | `prod.yml` 默认只允许 `https://localhost`；联调期先填 `*`，正式上线收窄为前端实际公网域名（逗号分隔多个） |
| `NO_CACHE=1` | 首次排错用 | 强制清旧构建缓存；确认能正常构建后可删除以加快后续部署 |

可选邮件找回密码（不配则验证码降级打进后端日志）：
```bash
MAIL_HOST="smtp.qq.com"
MAIL_PORT="587"
MAIL_USERNAME="【邮箱】"
MAIL_PASSWORD="【邮箱授权码，不是登录密码】"
```

---

## 四、首次部署与日志判读

1. 保存 Settings 与 Variables → 右下角 **Deploy / Redeploy**（首次保留 `NO_CACHE=1` 清缓存）。
2. **构建阶段日志**正确顺序：
   - `Railpack detected: Java / Maven`（看到这句 = Root Directory 修复成功）
   - Maven 下载依赖、`Building jar`、`BUILD SUCCESS`
3. **启动阶段日志**成功标志：
   - `Tomcat started on port(s): ...`（端口应为 Railway 注入端口，不是 8081）
   - `HikariPool-1 - Start completed`（数据库连上了）
   - 没有 `__SET_DB_PASSWORD__` / `Communications link failure` / 端口绑定错误
4. 在后端 Settings → **Networking / Generate Domain** 生成公网域名，访问 `https<域名>/swagger-ui/index.html`（springdoc 接口页）验证 200。

---

## 五、前端 Static Site（后端跑通后再做）

1. 新建服务 → **Static Site**，仓库同上，Branch 同后端。
2. Settings：
   - Root Directory：`frontend`
   - Build Command：`npm run build`
   - Publish Directory：`dist`
3. **接口地址改公网**：Vite 的 `/api`、`/ws`、`/upload` 代理只在 `npm run dev` 生效；生产静态站必须把前端请求基址改为后端公网域名（`https://<后端域名>/api`），WebSocket 改为 `wss://<后端域名>/ws`。
4. 后端 `CORS_ORIGINS` 从 `*` 改为该前端静态站域名；若前端与后端同域反代则无需 CORS。
5. 小程序（UniApp）不部署在 Railway，其接口基址同样改为后端公网 HTTPS 地址。

---

## 六、持久化与免费额度注意事项

- **文件上传持久化**：`UPLOAD_PATH=/app/upload` 在 Railway 无挂载卷时重启即丢；对“再见”服务挂一个 Volume 挂载到 `/app/upload`（Settings → Volumes），否则用户头像/封面会在重新部署后消失。
- **数据库备份**：Railway 试用/低配额度有限（你截图显示“30天或剩5美元”），MySQL 数据务必定期用 Navicat `mysqldump` 导出到本地，额度用尽或实例回收后未备份数据不可恢复。
- **服务休眠/冷启动**：免费或低配实例空闲会降配/休眠，首次请求变慢属正常；答辩前手动点开预热。

---

## 七、报错对照表（按现象定位）

| 现象（日志/面板） | 根因 | 处理 |
|---|---|---|
| `Railpack 未能为建造做好准备` / `no provider detected`，20 秒内失败 | Root Directory 仍是 `/`，根目录无 `pom.xml`/`package.json`/`Dockerfile` | Settings → Root Directory 填 `see_you`；不要用 `RAILWAY_WORKDIR` 变量代替 |
| 构建成功但服务反复 Restarting / 端口探测失败 | 没设 `SERVER_PORT`，Spring 还在监听 8081 | 加 `SERVER_PORT=${{PORT}}` |
| `Communications link failure` / `Access denied for user` | MySQL 变量未 Add Reference，或值为空/错误 | 引用 `MYSQLHOST/MYSQLPORT/MYSQLUSER/MYSQLPASSWORD`；核对 DB_URL 库名 `see_you` |
| `Unknown column` / `Table 'sys_user' doesn't exist` | 数据库没初始化或脚本顺序错 | 回到第一节按顺序导 SQL |
| `UnsupportedClassVersionError`（class 版本 61/17） | Railpack 用了低版本 JDK | `see_you/.java-version` 写 `17` 并推送 |
| 启动报 `没有主清单属性` / no main manifest | fat jar 没 repackage（几百 KB 的普通 jar） | 确认 `spring-boot-maven-plugin` repackage 生效；Build Command 用 `mvn clean package -DskipTests` 重打；本项目 pom 已配 repackage |
| 接口跨域被拦（浏览器 CORS 报错） | prod 只放行 `https://localhost` | 配置 `CORS_ORIGINS` 为前端域名或临时 `*` |
| 登录后操作偶发 429 | 生产限流开启 | 演示期可设 `app.rate-limit.enabled=false`（环境变量 `APP_RATE_LIMIT_ENABLED=false`，以代码绑定为准） |
| 部署后图片/头像丢失 | `/app/upload` 未挂 Volume | 给服务挂持久卷到 `/app/upload` |

---

## 八、上线安全清单（与 docs/部署说明.md 对齐，逐项打勾）

- [ ] `JWT_SECRET` 为新的 ≥32 位随机串，不复用本地弱密钥
- [ ] 数据库使用 Railway 强密码，不出现 `__SET_DB_PASSWORD__`
- [ ] `SPRING_PROFILES_ACTIVE=prod`，不开启 `app.dev-captcha-log`
- [ ] `application-local.yml` 未提交入库（已在 `.gitignore`）
- [ ] 曾在截图/仓库暴露过的 `AI_API_KEY` 已在 DeepSeek 后台轮换
- [ ] `CORS_ORIGINS` 上线前从 `*` 收窄到真实前端域名
- [ ] MySQL 已做本地备份
