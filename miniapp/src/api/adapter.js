import { BASE_URL } from '../config'
import { getToken, clearSession } from '../utils/storage'

/**
 * =====================================================================================
 * 请求适配层 —— 小程序端实现
 *
 * 【为什么要有这一层】
 * 主项目（frontend）用的是 axios 单例，小程序里 axios 不能直接用（没有 XMLHttpRequest），
 * 换成 uni.request。但如果每个页面都直接写 uni.request，接口层就和平台绑死了。
 * 这里把「发请求」抽象成与 axios 版本**同签名**的实现，
 * 于是 api/index.js 里的函数在两个端可以长得一模一样。
 *
 * 【与 axios 版最重要的行为差异 —— 一定要看】
 * 响应拦截器对 HTTP 200 直接返回 res.data，也就是拿到的是
 *   { code, msg, data }
 * 这种业务信封。**业务失败（code !== 200）不会 reject**，
 * 所以 `await login(...)` 永远会正常返回，必须自己判断 res.code。
 *
 * 主项目踩过的坑：某处忘记判断 code，无感记录未开启（返回 403）
 * 被当成了"接口没数据"，弹出了完全误导用户的提示。
 * 因此这里额外提供 unwrap()：业务失败时直接抛错，调用方可以选一种风格统一使用。
 * =================================================================================== */

/** 业务信封 */
// { code: 200, msg: 'ok', data: {...} }

function request({ url, method = 'GET', data = {}, header = {}, timeout = 15000 }) {
  const token = getToken()
  const fullUrl = url.startsWith('http') ? url : BASE_URL + url

  return new Promise((resolve) => {
    uni.request({
      url: fullUrl,
      method,
      data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...header,
      },
      success: (res) => {
        const status = res.statusCode
        if (status === 401) {
          // 与前端一致：401 统一清会话，由页面自己决定跳不跳登录
          clearSession()
          resolve({ code: 401, msg: '登录已过期，请重新登录', data: null })
          return
        }
        if (status === 403) {
          resolve({ code: 403, msg: res.data?.msg || '没有权限执行此操作', data: null })
          return
        }
        if (status >= 500) {
          resolve({ code: status, msg: '服务器开小差了，请稍后再试', data: null })
          return
        }
        // 后端统一返回 { code, msg, data }
        const body = res.data
        if (body && typeof body === 'object' && 'code' in body) {
          resolve(body)
        } else {
          resolve({ code: status, msg: '返回格式异常', data: body })
        }
      },
      fail: (err) => {
        resolve({
          code: -1,
          msg: err?.errMsg?.includes('timeout')
            ? '请求超时，请检查网络或后端地址'
            : '网络连接失败，请检查后端是否启动、地址是否正确',
          data: null,
        })
      },
    })
  })
}

/**
 * 拆信封：业务成功返回 data，失败直接抛错（错误信息就是后端 msg）。
 * 推荐新代码统一用这个，避免漏判 code。
 */
export async function unwrap(promise, fallbackMsg = '操作失败') {
  const res = await promise
  if (!res || res.code !== 200) {
    throw new Error(res?.msg || fallbackMsg)
  }
  return res.data
}

export default {
  get: (url, params) => request({ url, method: 'GET', data: params }),
  post: (url, data) => request({ url, method: 'POST', data }),
  put: (url, data) => request({ url, method: 'PUT', data }),
  delete: (url, data) => request({ url, method: 'DELETE', data }),
  request,
}
