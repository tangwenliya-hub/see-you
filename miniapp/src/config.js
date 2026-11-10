/**
 * 小程序端运行配置
 *
 * 【三种运行场景，改一处即可】
 *  1. 开发者工具模拟器（本地后端）：不配 ENV_BASE 即回退 http://localhost:8081
 *  2. 真机预览（同一 WiFi）：把 LOCAL_BASE 换成电脑局域网 IP，如 http://192.168.1.5:8081
 *  3. 对接云端（模拟器演示 / 体验版 / 上线）：在 miniapp/.env.production 里写
 *        VITE_API_BASE=https://你的域名.com
 *     构建时自动生效，无需改本文件；清空该行则回退本地 LOCAL_BASE。
 *
 * 【基址不要带 /api】
 *  当前后端同源直连，控制器路径本身无前缀（/auth/captcha、/user/login ...）。
 *  若基址加 /api，请求会变成 /api/auth/captcha，后端无此路由 → 404。
 *  小程序 request 不走浏览器，不存在 CORS 跨域问题，直连 https 域名即可。
 *
 * 【微信硬性要求（仅真机预览 / 体验版 / 正式版）】
 *  真机与正式版要求 https + 已 ICP 备案域名，并在微信后台配置 request 合法域名，
 *  不能用 IP/localhost、不能带端口；开发者工具「模拟器」勾选“不校验合法域名”即可先演示。
 */

/**
 * 构建期注入的线上地址（来自 .env.production 的 VITE_API_BASE）
 * 由 vite.config.js 的 define 替换成字符串字面量，没配时为空串。
 * 不能写 import.meta.env —— 小程序平台会把它编译成空对象，读不到值。
 */
const ENV_BASE = typeof __API_BASE__ === 'string' ? __API_BASE__ : ''

/** 本地开发用的地址：真机预览时改成电脑局域网 IP */
const LOCAL_BASE = 'http://localhost:8081'

export const BASE_URL = ENV_BASE || LOCAL_BASE

/** 是否为开发态（开发态可在界面上显示接口地址，方便排查） */
export const IS_DEV = !ENV_BASE
