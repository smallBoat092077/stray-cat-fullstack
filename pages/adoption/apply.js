const { adoptionApi } = require('../../utils/api');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe');

Page({
  data: {
    catId: null,
    housingIndex: 0,
    housingTypes: ['整租', '合租', '自有', '宿舍'],
    petIndex: 0,
    petOptions: ['无', '有'],
    familyIndex: 0,
    familyOptions: ['同意', '不同意'],
    housingArea: '',
    catExperience: '',
    adoptionReason: '',
    workSchedule: '',
    contactPhone: '',
    contactWechat: ''
  },

  onLoad(options) {
    prefetchTemplates();
    if (options.catId) {
      this.setData({ catId: options.catId });
    }
  },

  onHousingChange(e) { this.setData({ housingIndex: e.detail.value }); },
  onPetChange(e) { this.setData({ petIndex: e.detail.value }); },
  onFamilyChange(e) { this.setData({ familyIndex: e.detail.value }); },
  onAreaInput(e) { this.setData({ housingArea: e.detail.value }); },
  onExperienceInput(e) { this.setData({ catExperience: e.detail.value }); },
  onReasonInput(e) { this.setData({ adoptionReason: e.detail.value }); },
  onWorkInput(e) { this.setData({ workSchedule: e.detail.value }); },
  onPhoneInput(e) { this.setData({ contactPhone: e.detail.value }); },
  onWechatInput(e) { this.setData({ contactWechat: e.detail.value }); },

  async onSubmit() {
    const {
      catId, housingIndex, housingArea, petIndex, catExperience, adoptionReason,
      familyIndex, workSchedule, contactPhone, contactWechat
    } = this.data;

    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }

    if (!catId) {
      wx.showToast({ title: '猫咪信息缺失', icon: 'none' });
      return;
    }
    if (!housingArea || !catExperience || !adoptionReason) {
      wx.showToast({ title: '请填写完整信息', icon: 'none' });
      return;
    }
    if (!/^1[3-9]\d{9}$/.test(contactPhone.trim())) {
      wx.showToast({ title: '请填写正确的 11 位手机号', icon: 'none' });
      return;
    }

    // 申请人在等审核结论，通过之后还要按节点交回访，三条一起授权
    await requestSubscribe(SUBSCRIBE_KEYS.adoption);

    wx.showLoading({ title: '提交中...' });
    try {
      await adoptionApi.apply({
        catId,
        housingType: housingIndex,
        housingArea: parseFloat(housingArea),
        hasOtherPets: petIndex,
        catExperience,
        adoptionReason,
        familyAgree: familyIndex,
        workSchedule,
        contactPhone: contactPhone.trim(),
        contactWechat: contactWechat.trim()
      });
      wx.hideLoading();
      wx.showToast({ title: '申请已提交', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1500);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: err.message || '提交失败', icon: 'none' });
    }
  }
});
