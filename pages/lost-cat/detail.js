const { lostCatApi, resolveImageUrl } = require('../../utils/api.js');

const STATUS_MAP = {
  0: { text: '寻找中', cls: 'warning' },
  1: { text: '已找到', cls: 'success' },
  2: { text: '已关闭', cls: 'info' }
};

function formatTime(v) {
  if (!v) return '';
  return String(v).replace('T', ' ').slice(0, 16);
}

function normImages(images) {
  const arr = Array.isArray(images)
    ? images
    : String(images || '').split(',').filter(Boolean);
  return arr.map((p) => resolveImageUrl(p)).filter(Boolean);
}

Page({
  data: {
    post: null,
    loading: true,
    notFound: false
  },

  onLoad(options) {
    this._id = (options && options.id) || '';
    if (!this._id) {
      this.setData({ loading: false, notFound: true });
      return;
    }
    this.loadDetail();
  },

  onPullDownRefresh() {
    this.loadDetail().then(() => wx.stopPullDownRefresh());
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const d = await lostCatApi.detail(this._id);
      if (!d) {
        this.setData({ loading: false, notFound: true, post: null });
        return;
      }
      const st = STATUS_MAP[d.status] || STATUS_MAP[0];
      this.setData({
        loading: false,
        notFound: false,
        post: {
          ...d,
          imagesList: normImages(d.images),
          statusText: st.text,
          statusClass: st.cls,
          lostAtText: formatTime(d.lostAt)
        }
      });
    } catch (err) {
      this.setData({ loading: false, notFound: true });
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  previewImage(e) {
    wx.previewImage({
      current: e.currentTarget.dataset.src,
      urls: this.data.post.imagesList
    });
  },

  onProvideClue() {
    wx.navigateTo({ url: '/pages/lost-cat/clue-create?postId=' + this._id });
  },

  goPlaza() {
    wx.navigateTo({ url: '/pages/lost-cat/list' });
  }
});
