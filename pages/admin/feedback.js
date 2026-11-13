const { userApi, feedbackAdminApi, resolveImageUrl } = require('../../utils/api');

const TYPE_MAP = { 0: '功能建议', 1: '问题反馈', 2: '其他' };
const STATUS_MAP = {
  0: { text: '待处理', class: 'warning' },
  1: { text: '已回复', class: 'success' },
  2: { text: '已忽略', class: 'info' }
};
/** 默认拉这么多条，反馈量级小，一页足够；下拉刷新重新拉 */
const PAGE_SIZE = 50;

function prettyTime(iso) {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '';
}

Page({
  data: {
    ready: false,
    status: 0,
    list: [],
    loading: false,
    // 正在输入回复的反馈 id（null 表示没有展开中的回复框）
    replyFor: null,
    replyText: ''
  },

  async onLoad(options) {
    try {
      const user = await userApi.getCurrent();
      if (!user || user.role === null || user.role === undefined || user.role < 3) {
        wx.showToast({ title: '仅管理员可处理反馈', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 800);
        return;
      }
      this.setData({ ready: true });
      await this.loadList();
      // 消息中心带 id 进来时，展开并定位到那条反馈
      if (options && options.id) {
        this.focusFeedback(Number(options.id));
      }
    } catch (err) {
      // 提示由 request 统一处理
    }
  },

  onPullDownRefresh() {
    this.loadList().then(() => wx.stopPullDownRefresh());
  },

  focusFeedback(id) {
    const list = this.data.list.map((item) => ({ ...item, expanded: item.id === id, highlight: item.id === id }));
    this.setData({ list });
    if (list.some((item) => item.highlight)) {
      wx.pageScrollTo({ selector: '.highlight', duration: 300, offsetTop: -120 });
    }
  },

  onStatus(e) {
    const status = e.currentTarget.dataset.status;
    const target = status === 'all' ? null : Number(status);
    if (target === this.data.status) {
      return;
    }
    this.setData({ status: target });
    this.loadList();
  },

  async loadList() {
    this.setData({ loading: true });
    try {
      const params = { pageNum: 1, pageSize: PAGE_SIZE };
      if (this.data.status !== null) {
        params.status = this.data.status;
      }
      const result = await feedbackAdminApi.page(params);
      const records = (result && result.records) || [];
      this.setData({
        list: records.map((item) => ({
          ...item,
          typeText: TYPE_MAP[item.type] || '其他',
          statusText: STATUS_MAP[item.status] ? STATUS_MAP[item.status].text : '未知',
          statusClass: STATUS_MAP[item.status] ? STATUS_MAP[item.status].class : 'info',
          timeText: prettyTime(item.createdAt),
          repliedText: prettyTime(item.repliedAt),
          // images 落库是逗号分隔的相对路径
          images: (item.images || '').split(',').filter(Boolean).map(resolveImageUrl),
          expanded: false,
          highlight: false
        })),
        loading: false
      });
    } catch (err) {
      this.setData({ loading: false });
    }
  },

  onToggle(e) {
    const id = Number(e.currentTarget.dataset.id);
    this.setData({
      list: this.data.list.map((item) => (item.id === id ? { ...item, expanded: !item.expanded } : item))
    });
  },

  previewImage(e) {
    const { img, index } = e.currentTarget.dataset;
    const item = this.data.list[index];
    if (!item || !item.images.length) {
      return;
    }
    wx.previewImage({ current: img, urls: item.images });
  },

  onReplyInput(e) {
    this.setData({ replyText: e.detail.value });
  },

  openReply(e) {
    const id = Number(e.currentTarget.dataset.id);
    this.setData({ replyFor: this.data.replyFor === id ? null : id, replyText: '' });
  },

  cancelReply() {
    this.setData({ replyFor: null, replyText: '' });
  },

  submitReply() {
    const reply = (this.data.replyText || '').trim();
    if (!reply) {
      wx.showToast({ title: '先写一句回复', icon: 'none' });
      return;
    }
    this.submitHandle(this.data.replyFor, 1, reply, '已回复');
  },

  onIgnore(e) {
    const id = Number(e.currentTarget.dataset.id);
    wx.showModal({
      title: '忽略这条反馈',
      content: '忽略后用户不会再收到回复通知，确定吗？',
      success: (res) => {
        if (res.confirm) {
          this.submitHandle(id, 2, null, '已忽略');
        }
      }
    });
  },

  async submitHandle(id, status, reply, tip) {
    wx.showLoading({ title: '提交中...' });
    try {
      await feedbackAdminApi.handle(id, status, reply);
      wx.hideLoading();
      this.setData({ replyFor: null, replyText: '' });
      wx.showToast({ title: tip, icon: 'success' });
      this.loadList();
    } catch (err) {
      wx.hideLoading();
    }
  }
});
