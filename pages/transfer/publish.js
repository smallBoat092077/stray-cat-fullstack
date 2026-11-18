const { transferApi, uploadFile } = require('../../utils/api.js');

const GENDERS = [
  { value: 0, label: '母' },
  { value: 1, label: '公' },
  { value: 2, label: '未知' }
];
const YES_NO = [
  { value: 1, label: '是' },
  { value: 0, label: '否' }
];

Page({
  data: {
    photos: [], // 本地临时路径，第一张为封面
    catName: '',
    breed: '',
    ageMonths: '',
    genders: GENDERS,
    gender: 1,
    yesNo: YES_NO,
    neutered: 1,
    vaccinated: 1,
    healthNote: '',
    personality: '',
    regionFilter: {},
    regionLabel: '',
    addressHint: '',
    reason: '',
    requirement: '',
    contactName: '',
    contactPhone: '',
    promised: false,
    submitting: false
  },

  onLoad() {},

  onRegionChange(e) {
    const d = e.detail || {};
    this.setData({ regionFilter: d, regionLabel: d.label || '' });
  },

  chooseImage() {
    const rest = 6 - this.data.photos.length;
    if (rest <= 0) {
      wx.showToast({ title: '最多上传 6 张照片', icon: 'none' });
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const picked = (res.tempFiles || []).map((f) => f.tempFilePath);
        this.setData({ photos: this.data.photos.concat(picked) });
      }
    });
  },

  removeImage(e) {
    const index = Number(e.currentTarget.dataset.index);
    this.setData({ photos: this.data.photos.filter((_, i) => i !== index) });
  },

  previewImage(e) {
    wx.previewImage({ current: e.currentTarget.dataset.src, urls: this.data.photos });
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onGender(e) {
    this.setData({ gender: Number(e.currentTarget.dataset.value) });
  },

  onNeutered(e) {
    this.setData({ neutered: Number(e.currentTarget.dataset.value) });
  },

  onVaccinated(e) {
    this.setData({ vaccinated: Number(e.currentTarget.dataset.value) });
  },

  onPromise() {
    this.setData({ promised: !this.data.promised });
  },

  validate() {
    const d = this.data;
    if (!d.catName.trim()) return '请填写猫咪名字';
    if (!d.breed.trim()) return '请填写品种';
    if (d.ageMonths === '' || isNaN(Number(d.ageMonths)) || Number(d.ageMonths) < 0) return '请填写正确的年龄（月龄）';
    const rf = d.regionFilter || {};
    if (!rf.provinceCode || !rf.cityCode) return '请选择所在区域';
    if (!d.reason.trim()) return '请填写转让原因';
    if (!d.contactName.trim()) return '请填写联系人称呼';
    if (!/^1\d{10}$/.test(d.contactPhone)) return '请填写正确的 11 位手机号';
    if (!d.promised) return '请先勾选承诺';
    if (d.photos.length === 0) return '请至少上传 1 张照片';
    return '';
  },

  async uploadAll(paths) {
    const urls = [];
    for (let i = 0; i < paths.length; i++) {
      wx.showLoading({ title: `上传中 ${i + 1}/${paths.length}`, mask: true });
      urls.push(await uploadFile(paths[i]));
    }
    wx.hideLoading();
    return urls;
  },

  async onSubmit() {
    if (this.data.submitting) return;
    const err = this.validate();
    if (err) {
      wx.showToast({ title: err, icon: 'none' });
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
      const d = this.data;
      const photos = await this.uploadAll(d.photos);
      const rf = d.regionFilter;
      await transferApi.create({
        catName: d.catName.trim(),
        breed: d.breed.trim(),
        ageMonths: Number(d.ageMonths),
        gender: d.gender,
        neutered: d.neutered,
        vaccinated: d.vaccinated,
        healthNote: d.healthNote.trim(),
        personality: d.personality.trim(),
        provinceCode: rf.provinceCode || '',
        cityCode: rf.cityCode || '',
        districtCode: rf.districtCode || '',
        addressHint: d.addressHint.trim(),
        reason: d.reason.trim(),
        requirement: d.requirement.trim(),
        contactName: d.contactName.trim(),
        contactPhone: d.contactPhone.trim(),
        photos: photos.join(',')
      });
      wx.showToast({ title: '已提交审核', icon: 'success' });
      setTimeout(() => {
        wx.redirectTo({ url: '/pages/transfer/mine' });
      }, 800);
    } catch (e) {
      wx.hideLoading();
      wx.showToast({ title: (e && e.message) || '提交失败，请重试', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  }
});
