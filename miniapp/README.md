# 绪见 · 微信小程序端（uni-app + Vue3）

后端与 H5 端**零改动**，本目录是纯增量：复用现有 REST 接口，只做核心记录链路。

页面范围：**6 个页面 + 4 项 tabBar**（主页 / 日记 / AI / 我的）

| 页面 | 路径 | 说明 |
|---|---|---|
| 登录 | `pages/login/login` | 账号密码 + 图形验证码 |
| 主页 | `pages/main/main` | 九宫格心情快选、记录概览、关怀提醒卡片 |
| 日记列表 | `pages/diary/diary` | 分页 + 下拉刷新 + 触底加载 |
| 写日记 | `pages/diary/write` | 情绪 + 强度 + 正文，支持主页快选带入情绪 |
| AI 陪伴 | `pages/assistant/assistant` | 多会话、上下文由后端按 sessionId 重建 |
| 我的 | `pages/profile/profile` | 情绪分布、连续天数、免责声明、退出登录 |

---

## 一、运行（答辩演示）

**最简单的方式：HBuilderX**

1. 用 HBuilderX 新建「uni-app 项目」（Vue3 版本），把生成目录里的文件删掉
2. 把本目录（`miniapp/`）的内容复制进去
3. 运行 → 运行到小程序模拟器 → 微信开发者工具

**命令行方式**

```bash
cd miniapp
npm install
npm run dev:mp-weixin      # 编译到 dist/dev/mp-weixin
```

然后用微信开发者工具打开 `dist/dev/mp-weixin` 目录。

> 若 `npm install` 报版本解析问题（package.json 里依赖写的是 `latest`），
> 请改用 HBuilderX 导入，或对照官方 uni-app 模板覆盖 `package.json` 里的版本号。

## 二、演示前必做两件事

1. **改后端地址**：`src/config.js` 里的 `BASE_URL`
   - 微信开发者工具 → 详情 → 本地设置 → 勾选 **「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」**
   - 模拟器可用 `http://localhost:8081`
   - **真机预览必须改成电脑的局域网 IP**（如 `http://192.168.1.23:8081`），且手机与电脑同一 WiFi
2. **填 AppID**：`src/manifest.json` 的 `mp-weixin.appid`。演示可用开发者工具的「测试号」，不填也能跑

## 三、设计说明

**请求适配层** `src/api/adapter.js`
用 `uni.request` 实现了与主项目 axios 版**同签名**的请求方法，接口函数两个端长得一样。
关键点：响应拦截器返回的是业务信封 `{ code, msg, data }`，**业务失败不会 reject**——
这是主项目踩过的坑（某处漏判 code 导致 403 被误报成"没数据"），
所以额外提供了 `unwrap()`，失败时直接抛错。

**情绪字典** `src/utils/emotions.js`
是「唯一真源」的第三份拷贝（后端 `EmotionConst`、前端 `constants.js`、这里）。
后端已有 `EmotionDictConsistencyTest` 自动读前端文件比对，**改这里之前必须先改前端 constants.js**。

**样式约束**
小程序 WXSS 不支持 `*` 通配符和标签选择器，主项目的 `mobile.css` 那套兜底写法在这里不适用，
所有样式都写在类上，尺寸统一用 rpx。

**未包含的模块（有意裁剪）**
FM（需换 InnerAudioContext）、心理测评、数据看板（ECharts 需换 ec-canvas）、后台管理、
无感记录埋点（依赖 DOM 事件监听，小程序无 DOM）。
答辩话术：小程序端定位是「微信里的轻量记录入口」，重分析与重图表在 Web/H5 端。
