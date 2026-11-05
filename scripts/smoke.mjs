/**
 * 后端冒烟测试：重启后端后跑一次，确认核心链路没坏。
 *
 * 用法：
 *   node scripts/smoke.mjs                      # 默认 8081
 *   node scripts/smoke.mjs 8082                 # 指定端口
 *
 * 覆盖：限流 / 验证码 / 登录 / 鉴权 / 游客放行 / 无感推测 / 风险预警。
 * 依赖：后端以 --app.dev-captcha-log=true 启动（否则拿不到验证码答案）；
 *       数据库里要有 test / ****** 账号。
 */
const BASE = `http://127.0.0.1:${process.argv[2] || 8081}`;

let failed = 0;
const check = (name, ok, detail) => {
  console.log(`${ok ? '  OK  ' : ' FAIL '} ${name}${detail ? ' — ' + detail : ''}`);
  if (!ok) failed++;
};

const get = async (p, token) => {
  const r = await fetch(BASE + p, { headers: token ? { Authorization: 'Bearer ' + token } : {} });
  return { s: r.status, b: await r.json().catch(() => null) };
};
const post = async (p, body, token) => {
  const r = await fetch(BASE + p, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify(body)
  });
  return { s: r.status, b: await r.json().catch(() => null) };
};

(async () => {
  console.log(`冒烟目标：${BASE}\n`);

  // 1. 游客可访问的全站统计
  const pub = await get('/statistics/overview');
  check('游客访问全站统计（应 200）', pub.s === 200, `status=${pub.s}`);

  // 2. 未登录访问个人接口必须 401
  const anon = await get('/statistics/my/overview');
  check('未登录访问个人统计（应 401）', anon.s === 401, `status=${anon.s}`);

  // 3. 验证码 + 开发模式取值 + 登录
  const ck = await get('/auth/captcha');
  const key = ck.b?.data?.captchaKey;
  check('获取图形验证码', !!key, `captchaKey=${key ? '有' : '无'}`);

  // 注意：参数名是 key，不是 captchaKey
  const dc = await get('/auth/captcha/dev-code?key=' + key);
  const code = dc.b?.data?.code;
  check('开发模式取验证码答案', !!code,
    code ? `code=${code}` : `需以 --app.dev-captcha-log=true 启动（当前 msg=${dc.b?.msg}）`);

  let token = null;
  if (code) {
    const lg = await post('/user/login', { username: 'test', password: '123456', captchaKey: key, captchaCode: code });
    token = lg.b?.data?.token;
    check('登录 test/123456', lg.b?.code === 200 && !!token, `bizCode=${lg.b?.code} msg=${lg.b?.msg}`);
  }

  if (token) {
    const ov = await get('/statistics/my/overview', token);
    check('带 token 访问个人统计（应 200）', ov.b?.code === 200, `bizCode=${ov.b?.code}`);

    const st = await get('/sense/predict/today', token);
    check('无感今日推测接口', st.b?.code === 200,
      `hasSuggestion=${st.b?.data?.hasSuggestion}${st.b?.data?.hasSuggestion ? '' : '（未播种行为数据时属正常）'}`);

    const rk = await get('/risk/latest', token);
    check('风险预警接口', rk.b?.code === 200, `bizCode=${rk.b?.code}`);
  }

  // 4. 限流：验证码阈值 20 次/分钟，连打 25 次应出现 429
  let hit429 = 0;
  for (let i = 0; i < 25; i++) {
    const r = await get('/auth/captcha');
    if (r.s === 429) hit429++;
  }
  check('限流生效（验证码 20/min，连打 25 次）', hit429 > 0, `429 次数=${hit429}`);

  console.log(`\n${failed === 0 ? '全部通过' : failed + ' 项失败'}`);
  process.exit(failed === 0 ? 0 : 1);
})();
