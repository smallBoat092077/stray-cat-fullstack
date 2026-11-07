const { catApi, coinApi, tipApi, resolveImageUrl } = require('../../utils/api');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe');

const PRESETS = [10, 30, 50, 100];

Page({
  data: {
    cat: null,
    presets: PRESETS,
    selected: 30,
    customAmount: '',
    isCustom: false,
    message: '',
    balance: 0,
    tipped: 0,
    signedToday: false,
    submitting: false
  },

  onLoad(options) {
    prefetchTemplates();
    this.catId = Number(options.catId);
    this.loadCat();
  },

  onShow() {
    this.loadSummary();
  },

  async loadCat() {
    try {
      const cat = await catApi.getDetail(this.catId);
      this.setData({
        cat: {
          id: cat.id,
          name: cat.name,
          description: cat.description,
          coverImage: resolveImageUrl(cat.coverImage),
          coinTotal: cat.coinTotal || 0
        }
      });
    } catch (err) {
      wx.showToast({ title: '猫咪信息加载失败', icon: 'none' });
    }
  },

  async loadSummary() {
    try {
      const summary = await coinApi.getSummary();
      this.setData({
        balance: summary.balance,
        tipped: summary.tipped,
        signedToday: summary.signedToday
      });
    } catch (err) {
      // 未登录时 request 已引导去登录页，这里静默即可
    }
  },

  onPickAmount(e) {
    const amount = Number(e.currentTarget.dataset.amount);
    this.setData({ selected: amount, isCustom: false, customAmount: '' });
  },

  onPickCustom() {
    this.setData({ isCustom: true });
  },

  onCustomInput(e) {
    this.setData({ customAmount: e.detail.value, isCustom: true });
  },

  onMessageInput(e) {
    this.setData({ message: e.detail.value });
  },

  currentAmount() {
    if (this.data.isCustom) {
      return parseInt(this.data.customAmount, 10) || 0;
    }
    return this.data.selected;
  },

  async onSignIn() {
    if (this.data.signedToday) {
      return;
    }
    if (!getApp().ensureLogin()) {
      return;
    }
    try {
      const res = await coinApi.signIn();
      this.setData({ balance: res.balance, signedToday: true });
      wx.showToast({ title: `签到成功 +${res.reward} 猫粮`, icon: 'none' });
    } catch (err) {
      // 错误提示由 request 统一处理
    }
  },

  async onSubmit() {
    const app = getApp();
    if (!app.ensureLogin()) {
      return;
    }
    const amount = this.currentAmount();
    if (amount < 1 || amount > 200) {
      wx.showToast({ title: '单次猫粮需在 1~200 之间', icon: 'none' });
      return;
    }
    if (amount > this.data.balance) {
      this.promptInsufficient();
      return;
    }
    if (this.data.submitting) {
      return;
    }

    // 打赏即委托，发起人要等「被接单」和「已打卡」两条，所以在这里一次取两份授权
    await requestSubscribe(SUBSCRIBE_KEYS.tip);
    this.setData({ submitting: true });
    try {
      const tip = await tipApi.create(this.catId, amount, this.data.message);
      const balance = this.data.balance - amount;
      this.setData({ balance, tipped: this.data.tipped + amount, submitting: false });
      if (tip && tip.taskId) {
        this.promptTaskProgress(tip.taskId);
        return;
      }
      wx.showToast({ title: '已送达', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 900);
    } catch (err) {
      this.setData({ submitting: false });
    }
  },

  // 打赏会生成一条待接单投喂任务，先让人去投喂栏目看到它，否则这条链路像断了
  promptTaskProgress(taskId) {
    wx.showModal({
      title: '已送达',
      content: '这份猫粮会委托志愿者实地投喂，可在「投喂」栏目查看接单进度。现在就去看了吗？',
      confirmText: '去看进度',
      cancelText: '先不用',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: `/pages/feeding/detail?id=${taskId}` });
          return;
        }
        wx.navigateBack();
      }
    });
  },

  promptInsufficient() {
    wx.showModal({
      title: '猫粮不足',
      content: '余额不够送出这些猫粮。可以每日签到累积，也可以直接购买猫粮礼包。',
      confirmText: '去购买',
      cancelText: '知道了',
      success: (res) => {
        if (res.confirm) {
          wx.navigateTo({ url: '/pages/recharge/index' });
        }
      }
    });
  },

  onGoMine() {
    wx.navigateTo({ url: '/pages/tip/list' });
  }
});
