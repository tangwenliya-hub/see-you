/**
 * 小程序端运行配置
 *
 * 【三种运行场景，改一处即可】
 *  1. 开发者工具模拟器：不用配任何东西，默认就是 http://localhost:8081
 *  2. 真机预览（同一 WiFi）：把 LOCAL_BASE 换成电脑局域网 IP，如 http://192.168.1.5:8081
 *  3. 体验版 / 上线（微信里真用）：在 miniapp/.env.production 里写
 *        VITE_API_BASE=https://你的域名/api
 *     构建时自动生效，无需改本文件，本地演示也不会被影响。
 *
 * 【为什么线上地址要带 /api】
 *  服务器上的 nginx 把 /api/ 反代到后端并剥掉前缀，同时 /api/upload/xxx.jpg
 *  会被转成后端的 /upload/xxx.jpg（WebConfig 的静态映射），图片和接口同域，
 *  小程序只需要配置一个 request 合法域名即可。
 *
 * 【微信的硬性要求】
 *  BASE_URL 必须是 https + 已 ICP 备案的域名，不能用 IP、不能用 localhost、不能带端口。
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
