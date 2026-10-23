const { catApi } = require('../../utils/api');

Page({
  data: {
    genderIndex: 2,
    genderOptions: ['母', '公', '未知'],
    name: '', ageEstimate: '', color: '',
    locationDesc: '', personality: '', description: ''
  },

  onNameInput(e) { this.setData({ name: e.detail.value }); },
  onAgeInput(e) { this.setData({ ageEstimate: e.detail.value }); },
  onColorInput(e) { this.setData({ color: e.detail.value }); },
  onLocationInput(e) { this.setData({ locationDesc: e.detail.value }); },
  onPersonalityInput(e) { this.setData({ personality: e.detail.value }); },
  onDescInput(e) { this.setData({ description: e.detail.value }); },
  onGenderChange(e) { this.setData({ genderIndex: e.detail.value }); },

  async onSubmit() {
    const { name, genderIndex, ageEstimate, color, locationDesc, personality, description } = this.data;
    if (!name || !locationDesc) {
      wx.showToast({ title: '请填写名称和位置', icon: 'none' });
      return;
    }
    wx.showLoading({ title: '提交中...' });
    try {
      await catApi.report({
        name, gender: genderIndex, ageEstimate, color,
        lifeStatus: 0, locationDesc, personality, description
      });
      wx.hideLoading();
      wx.showToast({ title: '上报成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1500);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: err.message || '提交失败', icon: 'none' });
    }
  }
});
