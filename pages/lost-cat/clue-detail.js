const { lostCatApi, resolveImageUrl } = require('../../utils/api.js');

const AUDIT_MAP = {
  0: { text: '待审核', cls: 'warning' },
  1: { text: '已通过', cls: 'success' },
  2: { text: '已驳回', cls: 'danger' }
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
    clue: null,
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
      const d = await lostCatApi.clueDetail(this._id);
      if (!d) {
        this.setData({ loading: false, notFound: true, clue: null });
        return;
      }
      const audit = AUDIT_MAP[d.auditStatus] || AUDIT_MAP[0];
      // 只渲染线索本身，绝不展示上报人的联系方式或身份明细
      this.setData({
        loading: false,
        notFound: false,
        clue: {
          ...d,
          imagesList: normImages(d.images),
          foundAtText: formatTime(d.foundAt),
          auditText: audit.text,
          auditClass: audit.cls
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
      urls: this.data.clue.imagesList
    });
  }
});
