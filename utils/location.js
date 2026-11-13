/**
 * 定位统一入口（全站唯一的 wx 定位调用点）。
 *
 * 重要约束：app.json 的 requiredPrivateInfos 里 getFuzzyLocation 与 getLocation
 * 是互斥的（同时声明编译直接报错），只能二选一。
 *
 * 现状：后台 wx.getLocation（精确）与 wx.chooseLocation（地图选点）均已审核通过，
 * 走精确方案：requiredPrivateInfos = ["getLocation", "chooseLocation"]，
 * USE_FUZZY = false，返回的 fuzzy 恒为 false，各页面基于 fuzzy 的模糊提示自动关闭。
 *
 * 若将来要退回模糊定位：
 *   1. app.json 的 requiredPrivateInfos 换成 ["getFuzzyLocation"]（chooseLocation 可保留）；
 *   2. 把下面 USE_FUZZY 改回 true。
 * 调用方代码不用动。
 */

const USE_FUZZY = false;

/**
 * 取当前坐标。成功返回 { latitude, longitude, fuzzy }，
 * 失败 reject { errMsg }，可用 describeError 翻译成用户能执行的下一步。
 *
 * 体验版/正式版对隐私接口强校验（开发者工具与真机调试默认不校验）：
 * 用户尚未同意《用户隐私保护指引》时，wx.getLocation 可能静默挂起不回调。
 * 所以先走 getPrivacySetting / requirePrivacyAuthorize，把官方隐私弹窗显式弹出来。
 */
function getCurrentLocation(opts) {
  // skipPrivacyCheck：调用方刚在官方弹窗里点完「同意」。此时授权已落库，直接调
  // 定位接口即可；如果仍走 getPrivacySetting，平台状态同步有延迟会误报 needAuth=true，
  // 代码又会去调被抑制的弹窗白等 5 秒——这正是「点了同意却不定位」的原因
  const skipPrivacyCheck = !!(opts && opts.skipPrivacyCheck);
  // autoPrompt：是否允许在这里自动调 requirePrivacyAuthorize 去弹官方隐私窗。
  // 微信只在「用户点击手势的上下文」里显示隐私弹窗，页面 onLoad 冷启动、地图
  // cover-view 的点击都不算，自动调用 100% 被抑制并干等 5 秒、只刷出一条
  // privacy popup wait timeout 误导排查。所以首页这类场景显式传 false：
  // 检测到 needAuth 立刻抛 needConsent，交给页面内同意卡片（button open-type）
  const autoPrompt = !(opts && opts.autoPrompt === false);
  return new Promise((resolve, reject) => {
    // 链路诊断：把隐私检查每一步的结果拼起来，失败时随 errMsg 透出到页面横幅，
    // 发布环境没有调试面板，靠这个定位卡点
    const diag = [];
    let timer = null;
    const cleanup = () => {
      if (timer) {
        clearTimeout(timer);
        timer = null;
      }
    };
    const done = (fuzzy) => (res) => {
      cleanup();
      resolve({ latitude: res.latitude, longitude: res.longitude, fuzzy });
    };
    const fail = (err) => {
      cleanup();
      const errMsg = ((err && err.errMsg) || '') + (diag.length ? ' [' + diag.join('; ') + ']' : '');
      console.warn('[location] 定位失败:', errMsg);
      const out = { errMsg, fuzzy: USE_FUZZY };
      if (err && err.needConsent) {
        out.needConsent = true;
      }
      reject(out);
    };
    // 定时器只在真正调用接口时才启动——隐私弹窗需要用户阅读和点击，
    // 不能把等待用户操作的时间也算进接口超时里（曾导致用户点慢一点整个流程已作废）
    const requestNow = () => {
      diag.push('calling getLocation');
      timer = setTimeout(() => fail({ errMsg: 'getLocation:fail timeout' }), 10000);
      if (USE_FUZZY) {
        wx.getFuzzyLocation({ type: 'gcj02', success: done(true), fail });
      } else {
        wx.getLocation({ type: 'gcj02', success: done(false), fail });
      }
    };

    // 刚同意过隐私指引：跳过检查直接定位（见函数头注释）
    if (skipPrivacyCheck) {
      requestNow();
      return;
    }

    // 老基础库没有隐私接口，直接定位
    if (!wx.getPrivacySetting) {
      diag.push('no privacy api (old lib)');
      requestNow();
      return;
    }
    wx.getPrivacySetting({
      success(res) {
        diag.push('needAuth=' + (res && res.needAuthorization));
        if (res && res.needAuthorization) {
          // 调用方明确表示「弹窗一定弹不出来」：直接抛 needConsent，不做无谓等待
          if (!autoPrompt) {
            diag.push('needAuth=true; auto prompt disabled');
            fail({ errMsg: 'getLocation:fail privacy consent required', needConsent: true });
            return;
          }
          // 用户还没同意隐私指引，先弹官方授权弹窗。
          // 关键约束：隐私弹窗只在用户点击手势上下文里显示——页面 onLoad 冷启动
          // 自动调用时微信客户端会抑制弹窗（requirePrivacyAuthorize 长期无回调），
          // 所以只等 5 秒；弹不出来就立刻把「需同意」抛给调用方展示页面内同意卡片，
          // 用户点击后再走 requestPrivacyConsent，不让用户干等半分钟
          timer = setTimeout(() => {
            diag.push('popup not shown (auto-call likely suppressed)');
            fail({ errMsg: 'getLocation:fail privacy popup wait timeout', needConsent: true });
          }, 5000);
          wx.requirePrivacyAuthorize({
            success: () => {
              diag.push('privacy popup agreed');
              cleanup();
              // iOS 上授权刚落库时，同一调用链内立刻调隐私接口可能仍被拦截，
              // 错开一拍再定位
              setTimeout(requestNow, 400);
            },
            fail: (e) => fail({ errMsg: 'getLocation:fail privacy auth denied, ' + ((e && e.errMsg) || '') })
          });
        } else {
          requestNow();
        }
      },
      fail(e) {
        diag.push('getPrivacySetting fail: ' + ((e && e.errMsg) || ''));
        requestNow();
      }
    });
  });
}

/**
 * 弹出官方《用户隐私保护指引》弹窗。必须在用户点击手势（bindtap 回调）里调用，
 * 否则微信客户端不显示弹窗。用户同意后 resolve，拒绝或失败 reject。
 * 老基础库没有该接口时直接 resolve（视为无需同意）。
 */
function requestPrivacyConsent() {
  return new Promise((resolve, reject) => {
    if (!wx.requirePrivacyAuthorize) {
      resolve();
      return;
    }
    // 兜底：即便在点击手势里调用，个别机型/客户端版本仍可能不弹窗且不回调，
    // 15 秒没有结果就明确返回失败，让页面给出可执行提示，而不是静默卡住
    const timer = setTimeout(() => {
      reject({ errMsg: 'requirePrivacyAuthorize:fail popup not shown timeout' });
    }, 15000);
    wx.requirePrivacyAuthorize({
      success: () => {
        clearTimeout(timer);
        resolve();
      },
      fail: (e) => {
        clearTimeout(timer);
        reject({ errMsg: (e && e.errMsg) || 'requirePrivacyAuthorize:fail' });
      }
    });
  });
}

/**
 * 把定位失败翻译成人话 + 可执行的下一步。
 */
function describeError(err) {
  const raw = (err && err.errMsg) || '';

  if (/system permission denied|SYSTEM_PERMISSION/i.test(raw)) {
    return {
      title: '手机定位权限被关闭',
      content: '请到「手机设置 → 微信 → 位置信息」允许访问位置，或在本小程序的系统权限里打开定位，然后回到这里',
      modal: true,
      confirmText: '去设置',
      openAppSetting: true
    };
  }
  if (/auth deny|auth denied|authorize no response|USER_DENIED/i.test(raw)) {
    return {
      title: '未获得位置授权',
      content: '允许获取位置后，才能看到你附近的流浪猫和投喂点',
      modal: true,
      openSetting: true
    };
  }
  if (/privacy consent required|privacy popup wait timeout/i.test(raw)) {
    return {
      title: '需同意《用户隐私保护指引》后才能定位',
      content: '点击「同意并开启定位」，同意后即可自动定位',
      modal: false
    };
  }
  if (/privacy|declared|not authorized|unauthorized|api is not|access denied|scope/i.test(raw)) {
    return {
      title: '位置功能暂不可用',
      content: '小程序后台还没开通「地理位置」接口，或未在隐私指引里声明位置信息，请稍后再试',
      modal: false
    };
  }
  if (/LOCATIONSWITCHOFF|location service|location switch/i.test(raw)) {
    return {
      title: '手机定位服务已关闭',
      content: '请打开手机下拉快捷面板里的「位置信息」，再回来重试',
      modal: true,
      confirmText: '知道了'
    };
  }
  if (/time ?out|timeout/i.test(raw)) {
    return { title: '定位超时，可重试或点「手动选点」', modal: false };
  }
  return { title: '定位失败，请检查定位权限后重试', modal: false };
}

/** 失败时统一弹提示；返回 true 表示已经给过用户反馈 */
function notifyError(err) {
  const tip = describeError(err);
  if (tip.modal) {
    wx.showModal({
      title: tip.title,
      content: tip.content,
      confirmText: tip.confirmText || '去设置',
      cancelText: '暂不',
      success: (r) => {
        if (!r.confirm) {
          return;
        }
        // openSetting 管小程序授权；openAppAuthorizeSetting 管手机系统层定位开关
        if (tip.openSetting) {
          wx.openSetting();
        } else if (tip.openAppSetting && wx.openAppAuthorizeSetting) {
          wx.openAppAuthorizeSetting();
        }
      }
    });
  } else {
    wx.showToast({ title: tip.title, icon: 'none', duration: 3000 });
  }
  return true;
}

module.exports = {
  getCurrentLocation,
  requestPrivacyConsent,
  describeError,
  notifyError
};
