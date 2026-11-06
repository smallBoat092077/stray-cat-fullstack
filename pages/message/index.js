const { messageApi } = require('../../utils/api');
const { prettyTime } = require('../../utils/format');

// tabBar 里的页面只能用 switchTab 打开，用 navigateTo 会静默失败
const TAB_PAGES = ['pages/index/index', 'pages/cat/list', 'pages/feeding/list', 'pages/profile/index'];

Page({
  data: {
    messages: [],
    loading: false
  },

  onShow() {
    this.loadMessages();
  },

  onPullDownRefresh() {
    this.loadMessages().then(() => wx.stopPullDownRefresh());
  },

  async loadMessages() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const page = await messageApi.list(1, 50);
      const rows = (page && page.records) || [];
      const hasUnread = rows.some((item) => !item.readFlag);
      const messages = rows.map((item) => ({
        ...item,
        unread: !item.readFlag,
        timeText: prettyTime(item.createdAt)
      }));
      this.setData({ messages, loading: false });
      // 看过就算读，未读数在「我的」页会随之清零
      if (hasUnread) {
        messageApi.readAll().catch(() => {});
      }
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: '网络异常', icon: 'none' });
    }
  },

  formatTime(value) {
    if (!value) {
      return '';
    }
    return String(value).replace('T', ' ').slice(0, 16);
  },

  tapMessage(e) {
    // 跳转路径带 ? 和 &，放 data-* 里容易被属性解析干扰，改从数组里取
    const item = this.data.messages[e.currentTarget.dataset.index];
    const page = item && item.page;
    if (!page) {
      return;
    }
    const path = page.replace(/^\//, '');
    if (TAB_PAGES.indexOf(path.split('?')[0]) >= 0) {
      wx.switchTab({ url: `/${path}` });
      return;
    }
    wx.navigateTo({ url: `/${path}` });
  }
});
