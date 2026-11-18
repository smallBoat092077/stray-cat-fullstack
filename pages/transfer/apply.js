const { transferApi, resolveImageUrl } = require('../../utils/api.js');

const YES_NO = [
  { value: 1, label: '是' },
  { value: 0, label: '否' }
];

function formatAge(months) {
  if (months == null || months === '') return '年龄未知';
  const m = Number(months);
  if (isNaN(m)) return '年龄未知';
  return m < 12 ? `${m}个月` : `${Math.floor(m / 12)}岁`;
}

Page({
  data: {
    id: null,
    cat: null,
    loading: true,
    error: false,
    yesNo: YES_NO,
    contactName: '',
    contactPhone: '',
    applyNote: '',
    familyAgree: 1,
    hasOtherPets: 0,
    promised: false,
    submitting: false
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.loadCat();
  },

  async loadCat() {
    if (!this.data.id) {
      this.setData({ loading: false, error: true });
      return;
    }
    try {
      const res = await transferApi.detail(this.data.id);
      const photos = Array.isArray(res.photos)
        ? res.photos
        : String((res && res.photos) || '').split(',').filter(Boolean);
      this.setData({
        cat: {
          catName: (res && res.catName) || '这只猫',
          breed: (res && res.breed) || '',
          ageText: formatAge(res && res.ageMonths),
          cover: resolveImageUrl(photos[0] || '')
        },
        loading: false,
        error: false
      });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadCat();
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onFamilyAgree(e) {
    this.setData({ familyAgree: Number(e.currentTarget.dataset.value) });
  },

  onHasOtherPets(e) {
    this.setData({ hasOtherPets: Number(e.currentTarget.dataset.value) });
  },

  onPromise() {
    this.setData({ promised: !this.data.promised });
  },

  async onSubmit() {
    if (this.data.submitting) return;
    const d = this.data;
    if (!d.contactName.trim()) {
      wx.showToast({ title: '请填写你的称呼', icon: 'none' });
      return;
    }
    if (!/^1\d{10}$/.test(d.contactPhone)) {
      wx.showToast({ title: '请填写正确的 11 位手机号', icon: 'none' });
      return;
    }
    if (!d.applyNote.trim()) {
      wx.showToast({ title: '请填写申请说明', icon: 'none' });
      return;
    }
    if (!d.promised) {
      wx.showToast({ title: '请先勾选领养承诺', icon: 'none' });
      return;
    }
    const app = getApp();
    if (!app.globalData.token || !app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      setTimeout(() => wx.navigateTo({ url: '/pages/login/index' }), 600);
      return;
    }
    this.setData({ submitting: true });
    try {
      const applyNote = `称呼：${d.contactName.trim()}\n${d.applyNote.trim()}`;
      await transferApi.apply({
        postId: d.id,
        applyNote,
        contactPhone: d.contactPhone.trim(),
        familyAgree: d.familyAgree,
        hasOtherPets: d.hasOtherPets,
        promiseVisit: d.promised ? 1 : 0
      });
      wx.showToast({ title: '申请已提交', icon: 'success' });
      setTimeout(() => {
        wx.navigateTo({ url: '/pages/transfer/my-apply' });
      }, 800);
    } catch (e) {
      wx.showToast({ title: (e && e.message) || '提交失败，请重试', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
