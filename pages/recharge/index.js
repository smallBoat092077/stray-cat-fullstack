const app = getApp();
const { rechargeApi, coinApi } = require('../../utils/api');

const IOS_MIN_VERSION = [8, 0, 68];
const POLL_INTERVAL = 1500;
const POLL_TIMES = 20;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** iOS 走苹果内购，低版本微信调不起来 */
function iosVersionOk() {
  const sys = wx.getSystemInfoSync() || {};
  if (sys.platform !== 'ios') {
    return true;
  }
  const current = String(sys.version || '').split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < IOS_MIN_VERSION.length; i++) {
    const value = current[i] || 0;
    if (value > IOS_MIN_VERSION[i]) {
      return true;
    }
    if (value < IOS_MIN_VERSION[i]) {
      return false;
    }
  }
  return true;
}

Page({
  data: {
    packs: [],
    enabled: false,
    balance: 0,
    buying: false
  },

  onLoad() {
    this.loadPacks();
    this.loadBalance();
  },

  onShow() {
    this.loadBalance();
  },

  async loadPacks() {
    try {
      const res = await rechargeApi.getPacks();
      let packs = (res.packs || []).map((item) => ({
        code: item.code,
        title: item.title,
        coinAmount: item.coinAmount,
        priceFen: item.priceFen,
        priceYuan: ((item.priceFen || 0) / 100).toFixed(2)
      }));

      // iOS 过滤掉低于 ¥1 的道具（Apple 最低价格限制）
      const sys = wx.getSystemInfoSync() || {};
      if (sys.platform === 'ios') {
        packs = packs.filter((item) => item.priceFen >= 100);
      }

      this.setData({ packs, enabled: !!res.enabled });
    } catch (err) {
      // 列表拿不到时按未开通渲染，避免给出点了付不了的按钮
    }
  },

  async loadBalance() {
    try {
      const summary = await coinApi.getSummary();
      this.setData({ balance: summary.balance });
    } catch (err) {
      // 未登录时 request 已引导去登录页
    }
  },

  onBuy(e) {
    if (!app.ensureLogin()) {
      return;
    }
    if (this.data.buying) {
      return;
    }
    if (typeof wx.requestVirtualPayment !== 'function') {
      wx.showToast({ title: '请把微信和小程序更新到最新版', icon: 'none' });
      return;
    }
    if (!iosVersionOk()) {
      wx.showToast({ title: '请将微信更新至最新版后再支付', icon: 'none' });
      return;
    }

    const code = e.currentTarget.dataset.code;
    const pack = this.data.packs.filter((item) => item.code === code)[0];
    if (!pack) {
      return;
    }

    this.setData({ buying: true });
    rechargeApi
      .createOrder(code)
      .then((payData) => {
        // signData 必须原样透传：服务端签的就是这个字符串，前端重新序列化就会签名不匹配
        wx.requestVirtualPayment({
          signData: payData.signData,
          mode: payData.mode,
          paySig: payData.paySig,
          signature: payData.signature,
          success: () => this.waitDelivered(payData.outTradeNo),
          fail: (err) => {
            this.setData({ buying: false });
            console.warn('虚拟支付失败', err);
            wx.showToast({ title: '支付未完成', icon: 'none' });
          }
        });
      })
      .catch(() => {
        this.setData({ buying: false });
      });
  },

  /**
   * 到账以服务端为准：这里轮询的是服务端查单/回调的结果，而不是前端的支付成功回调。
   * 先主动查单一次补上可能丢失的消息推送，之后只读本地订单状态，避免反复打微信接口。
   */
  async waitDelivered(outTradeNo) {
    wx.showLoading({ title: '确认到账中', mask: true });

    try {
      const res = await rechargeApi.confirm(outTradeNo);
      if (res && res.delivered) {
        this.finishDeliver(res);
        return;
      }
    } catch (err) {
      // 查单接口不通时继续等微信的发货通知
    }

    for (let i = 0; i < POLL_TIMES; i++) {
      await sleep(POLL_INTERVAL);
      try {
        const status = await rechargeApi.getStatus(outTradeNo);
        if (status && status.delivered) {
          this.finishDeliver(status);
          return;
        }
      } catch (err) {
        break;
      }
    }

    wx.hideLoading();
    this.setData({ buying: false });
    wx.showModal({
      title: '支付已完成',
      content: '猫粮到账可能有几分钟延迟，稍后可在「我的 - 猫粮明细」查看。仍未到账请通过客服反馈。',
      showCancel: false
    });
  },

  finishDeliver(res) {
    wx.hideLoading();
    this.setData({ buying: false, balance: res.balance });
    wx.showToast({ title: '猫粮已到账', icon: 'success' });
  },

  onViewPrivacy() {
    wx.navigateTo({ url: '/pages/privacy/index' });
  }
});
