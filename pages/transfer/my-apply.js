const { transferApi } = require('../../utils/api.js');
const { TRANSFER_APPLY_STATUS_MAP } = require('../../utils/constants.js');

function decorate(item) {
  const st = TRANSFER_APPLY_STATUS_MAP[item.status] || TRANSFER_APPLY_STATUS_MAP[0];
  return {
    ...item,
    statusText: st.text,
    statusClass: st.cls,
    createdText: String(item.createdAt || '').replace('T', ' ').slice(0, 16)
  };
}

Page({
  data: {
    list: [],
    loading: true,
    error: false
  },

  onShow() {
    const app = getApp();
    if (!app.globalData.token || !app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600);
      return;
    }
    this.loadList();
  },

  onPullDownRefresh() {
    this.loadList().then(() => wx.stopPullDownRefresh());
  },

  async loadList() {
    this.setData({ loading: true, error: false });
    try {
      const res = await transferApi.myApplies();
      const rows = Array.isArray(res) ? res : [];
      this.setData({ list: rows.map(decorate), loading: false, error: false });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadList();
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.post;
    if (id) wx.navigateTo({ url: `/pages/transfer/detail?id=${id}` });
  },

  goList() {
    wx.navigateTo({ url: '/pages/transfer/list' });
  }
});
