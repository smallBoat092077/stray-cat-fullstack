const { catApi, uploadFile } = require('../../utils/api');

Page({
  data: {
    genderIndex: 2,
    genderOptions: ['母', '公', '未知'],
    name: '', ageEstimate: '', color: '',
    locationDesc: '', personality: '', description: '',
    latitude: null, longitude: null, locationName: '',
    images: [],
    uploading: false
  },

  onNameInput(e) { this.setData({ name: e.detail.value }); },
  onAgeInput(e) { this.setData({ ageEstimate: e.detail.value }); },
  onColorInput(e) { this.setData({ color: e.detail.value }); },
  onLocationInput(e) { this.setData({ locationDesc: e.detail.value }); },
  onPersonalityInput(e) { this.setData({ personality: e.detail.value }); },
  onDescInput(e) { this.setData({ description: e.detail.value }); },
  onGenderChange(e) { this.setData({ genderIndex: e.detail.value }); },

  chooseLocation() {
    wx.chooseLocation({
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          locationName: res.name || res.address,
          locationDesc: this.data.locationDesc || res.name || res.address
        });
      },
      fail: (err) => {
        console.error('chooseLocation失败:', err);
        const msg = err.errMsg || '';
        if (msg.includes('auth deny') || msg.includes('authorize')) {
          wx.showModal({
            title: '需要位置权限',
            content: '请在设置中允许使用位置信息',
            confirmText: '去设置',
            success: (r) => { if (r.confirm) wx.openSetting(); }
          });
        } else if (msg.includes('not declared')) {
          wx.showToast({ title: '需在app.json声明chooseLocation', icon: 'none' });
        } else {
          wx.showToast({ title: '选择位置失败', icon: 'none' });
        }
      }
    });
  },

  chooseImage() {
    wx.chooseMedia({
      count: 3,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const newImages = res.tempFiles.map(f => f.tempFilePath);
        this.setData({ images: [...this.data.images, ...newImages] });
      }
    });
  },

  removeImage(e) {
    const index = e.currentTarget.dataset.index;
    const images = this.data.images.filter((_, i) => i !== index);
    this.setData({ images });
  },

  async uploadImages() {
    const urls = [];
    for (const path of this.data.images) {
      const url = await uploadFile(path);
      urls.push(url);
    }
    return urls;
  },

  async onSubmit() {
    const { name, genderIndex, ageEstimate, color, locationDesc, personality, description, latitude, longitude } = this.data;

    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }

    if (!name || !locationDesc) {
      wx.showToast({ title: '请填写名称和位置', icon: 'none' });
      return;
    }
    if (!latitude || !longitude) {
      wx.showToast({ title: '请选择地图定位', icon: 'none' });
      return;
    }

    this.setData({ uploading: true });
    wx.showLoading({ title: '提交中...' });

    try {
      const imageUrls = await this.uploadImages();

      await catApi.report({
        name, gender: genderIndex, ageEstimate, color,
        lifeStatus: 0, locationDesc, personality, description,
        latitude, longitude,
        coverImage: imageUrls.length > 0 ? imageUrls[0] : null
      });

      wx.hideLoading();
      wx.showToast({ title: '上报成功', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 1500);
    } catch (err) {
      wx.hideLoading();
      const msg = err.message || err.errMsg || '';
      wx.showToast({ title: msg || '提交失败', icon: 'none', duration: 3000 });
    } finally {
      this.setData({ uploading: false });
    }
  }
});
