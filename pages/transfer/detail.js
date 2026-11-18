const { transferApi, resolveImageUrl } = require('../../utils/api.js');

function formatAge(months) {
  if (months == null || months === '') return '年龄未知';
  const m = Number(months);
  if (isNaN(m)) return '年龄未知';
  if (m < 12) return `${m} 个月`;
  const y = Math.floor(m / 12);
  const rest = m % 12;
  return rest ? `${y} 岁 ${rest} 个月` : `${y} 岁`;
}

function yesNo(v) {
  return v === 1 || v === true ? '是' : '否';
}

function decorate(post) {
  const photos = Array.isArray(post.photos)
    ? post.photos
    : String(post.photos || '').split(',').filter(Boolean);
  const images = photos.map((p) => resolveImageUrl(p)).filter(Boolean);
  return {
    ...post,
    images,
    ageText: formatAge(post.ageMonths),
    genderText: post.gender === 0 ? '母' : (post.gender === 1 ? '公' : '未知'),
    neuteredText: yesNo(post.neutered),
    vaccinatedText: yesNo(post.vaccinated),
    regionText: [post.provinceName, post.cityName, post.districtName].filter(Boolean).join(' ')
      || post.addressHint
      || '地点待补充',
    current: 1
  };
}

Page({
  data: {
    id: null,
    post: null,
    loading: true,
    error: false,
    closed: false
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.loadDetail();
  },

  onPullDownRefresh() {
    this.loadDetail().then(() => wx.stopPullDownRefresh());
  },

  async loadDetail() {
    if (!this.data.id) {
      this.setData({ loading: false, error: true });
      return;
    }
    this.setData({ loading: true, error: false });
    try {
      const res = await transferApi.detail(this.data.id);
      const post = decorate(res || {});
      this.setData({
        post,
        closed: post.status === 2 || post.status === 3,
        loading: false,
        error: false
      });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadDetail();
  },

  onSwiperChange(e) {
    this.setData({ 'post.current': e.detail.current + 1 });
  },

  previewImage(e) {
    wx.previewImage({ current: e.currentTarget.dataset.src, urls: this.data.post.images });
  },

  onApply() {
    const app = getApp();
    if (!app.globalData.token || !app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600);
      return;
    }
    wx.navigateTo({ url: `/pages/transfer/apply?id=${this.data.id}` });
  },

  onReport() {
    const reasons = ['疑似买卖交易', '虚假信息', '其他'];
    wx.showActionSheet({
      itemList: reasons,
      success: async (res) => {
        const reason = reasons[res.tapIndex];
        try {
          await transferApi.report(this.data.id, reason);
          wx.showToast({ title: '已收到举报，感谢反馈', icon: 'none' });
        } catch (e) {
          wx.showToast({ title: '举报失败，请重试', icon: 'none' });
        }
      },
      fail: () => {}
    });
  }
});
