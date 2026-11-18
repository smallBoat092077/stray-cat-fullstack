const { wikiApi } = require('../../utils/api.js');

function decorate(item) {
  return {
    ...item,
    createdText: String(item.createdAt || '').replace('T', ' ').slice(0, 16),
    score: item.score != null ? item.score : 0
  };
}

Page({
  data: {
    list: [],
    loading: true,
    error: false,
    needLogin: false
  },

  onShow() {
    const app = getApp();
    if (!app.globalData.token || !app.globalData.userInfo) {
      this.setData({ needLogin: true, loading: false });
      return;
    }
    this.setData({ needLogin: false });
    this.loadList();
  },

  onPullDownRefresh() {
    this.loadList().then(() => wx.stopPullDownRefresh());
  },

  async loadList() {
    this.setData({ loading: true, error: false });
    try {
      const res = await wikiApi.myQuiz();
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      this.setData({ list: rows.map(decorate), loading: false, error: false });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadList();
  },

  onItemTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    const item = this.data.list[index];
    if (!item) return;
    // 结果页从本地缓存读取，点历史记录时先写入缓存再跳转
    try {
      wx.setStorageSync('wikiQuizResult', item);
    } catch (e) {}
    wx.navigateTo({ url: '/pages/wiki/result' });
  },

  goLogin() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  goQuiz() {
    wx.navigateTo({ url: '/pages/wiki/quiz' });
  }
});
