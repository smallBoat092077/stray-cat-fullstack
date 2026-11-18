/**
 * 渠道归因。
 *
 * 三类入口都只带一个 sid：
 *   ① 分享卡片 / 公众号图文卡片  -> ?sid=xxx
 *   ② 小程序码                    -> scene=s=xxx（page 不能带 query，业务 id 靠 resolve 补）
 *   ③ 冷启动兜底                  -> wx.getLaunchOptionsSync()
 *
 * app.js 不自动 wx.login，首访是匿名的，所以这里先把上下文暂存 7 天，
 * 等用户真正登录 / 注册时再随请求上报。任何解析或网络失败都静默降级，
 * 绝不弹窗、绝不阻断页面。
 */
const ATTR_KEY = 'attribution';
const TTL_MS = 7 * 24 * 60 * 60 * 1000;
const SID_PATTERN = /^[A-Za-z0-9]{6,12}$/;

function baseUrl() {
  try {
    const app = getApp();
    return app && app.globalData ? app.globalData.baseUrl : '';
  } catch (e) {
    return '';
  }
}

function tokenHeader() {
  try {
    const app = getApp();
    return app && app.globalData && app.globalData.token
      ? { Authorization: `Bearer ${app.globalData.token}` }
      : {};
  } catch (e) {
    return {};
  }
}

function read() {
  try {
    const ctx = wx.getStorageSync(ATTR_KEY);
    if (!ctx || !ctx.sid || !ctx.ts || Date.now() - ctx.ts > TTL_MS) {
      wx.removeStorageSync(ATTR_KEY);
      return null;
    }
    return ctx;
  } catch (e) {
    return null;
  }
}

function write(ctx) {
  try {
    wx.setStorageSync(ATTR_KEY, ctx);
  } catch (e) {
    // storage 写失败不影响页面
  }
}

function extract(options) {
  if (!options) {
    return null;
  }
  if (options.scene) {
    let scene = String(options.scene);
    try {
      scene = decodeURIComponent(scene);
    } catch (e) {
      // 原样用
    }
    const matched = /(?:^|&)s=([A-Za-z0-9]{6,12})(?:&|$)/.exec(scene);
    const sid = matched ? matched[1] : (SID_PATTERN.test(scene) ? scene : null);
    if (sid) {
      return { sid, entryType: 'wxacode', sceneValue: scene.slice(0, 64) };
    }
  }
  if (options.sid && SID_PATTERN.test(String(options.sid).trim())) {
    return {
      sid: String(options.sid).trim(),
      entryType: options.channel || 'share_card',
      sceneValue: null
    };
  }
  return null;
}

function resolveOnServer(sid) {
  return new Promise((done) => {
    const url = baseUrl();
    if (!url || !sid) {
      done(null);
      return;
    }
    wx.request({
      url: `${url}/share/resolve?sid=${encodeURIComponent(sid)}`,
      method: 'GET',
      timeout: 6000,
      // 带上登录态：老用户点新链接时服务端据此补一条幂等访问记录
      header: Object.assign({ 'ngrok-skip-browser-warning': 'true' }, tokenHeader()),
      success: (res) => {
        const body = res && res.data;
        done(body && body.code === 200 && body.data ? body.data : null);
      },
      fail: () => done(null)
    });
  });
}

/**
 * 归一捕获入口参数。返回 Promise<ctx|null>，ctx 含 sid / bizId / campaign / channel。
 *
 * 两条线分开处理：
 *   - 落地页（返回值）：永远解析「本次点击」的链接，bizId 必须来自这一次，否则页面会拿旧猫 id 卡在加载中；
 *   - 归因上报（storage）：首触优先，已有未上报的旧触点且 sid 不同时不覆盖，等登录时上报最早的。
 * 旧触点在登录成功后由 clear() 清掉，7 天 TTL 兜底。
 */
async function capture(options) {
  try {
    const found = extract(options);
    const prior = read();
    if (!found && !prior) {
      return null;
    }

    if (found) {
      const info = await resolveOnServer(found.sid);
      const landingCtx = Object.assign(
        {
          sid: found.sid,
          entryType: found.entryType,
          sceneValue: found.sceneValue,
          ts: Date.now(),
          resolved: true
        },
        info || {}
      );
      // 没有旧触点、或就是同一个链接、或旧触点已经上报完毕（resolved 且即将被 clear）时才覆盖
      if (!prior || prior.sid === found.sid) {
        write(landingCtx);
      }
      return landingCtx;
    }

    // 只有旧触点（暖启动切回且无新参数）：未解析的补一次解析
    if (prior && !prior.resolved) {
      const info = await resolveOnServer(prior.sid);
      const merged = Object.assign({}, prior, info || {}, { resolved: true });
      write(merged);
      return merged;
    }
    return prior;
  } catch (e) {
    return read();
  }
}

/** 供登录/注册请求拼接归因参数；没有上下文时返回空串 */
function queryString() {
  const ctx = read();
  if (!ctx || !ctx.sid) {
    return '';
  }
  const params = {
    sid: ctx.sid,
    channel: ctx.channel || '',
    campaign: ctx.campaign || '',
    entryType: ctx.entryType || '',
    sceneValue: ctx.sceneValue || ''
  };
  return Object.keys(params)
    .filter((key) => params[key])
    .map((key) => `${key}=${encodeURIComponent(params[key])}`)
    .join('&');
}

function clear() {
  try {
    wx.removeStorageSync(ATTR_KEY);
  } catch (e) {
    // ignore
  }
}

/**
 * 预生成分享短码。onShareAppMessage 必须同步返回，不能等网络请求，
 * 所以由页面在 onLoad 里先取好放进 data。失败时返回 null，分享退回不带 sid 的旧 path。
 */
function createLink(params) {
  return new Promise((done) => {
    const url = baseUrl();
    if (!url) {
      done(null);
      return;
    }
    wx.request({
      url: `${url}/share/link`,
      method: 'POST',
      timeout: 6000,
      header: Object.assign(
        { 'Content-Type': 'application/json', 'ngrok-skip-browser-warning': 'true' },
        tokenHeader()
      ),
      data: params || {},
      success: (res) => {
        const body = res && res.data;
        done(body && body.code === 200 && body.data ? body.data : null);
      },
      fail: () => done(null)
    });
  });
}

module.exports = {
  capture,
  queryString,
  clear,
  createLink,
  read
};
