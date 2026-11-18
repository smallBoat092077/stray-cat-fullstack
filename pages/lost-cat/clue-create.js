const { lostCatApi, uploadFile } = require('../../utils/api.js');

function pad(n) {
  return n < 10 ? '0' + n : '' + n;
}

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function nowTime() {
  const d = new Date();
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

Page({
  data: {
    images: [], // 本地临时路径，提交前逐张上传
    foundDate: today(),
    foundTime: nowTime(),
    latitude: null,
    longitude: null,
    addressDesc: '',
    description: '',
    uploading: false
  },

  onLoad(options) {
    this._postId = (options && options.postId) || '';
  },

  onDescInput(e) { this.setData({ description: e.detail.value }); },
  onDateChange(e) { this.setData({ foundDate: e.detail.value }); },
  onTimeChange(e) { this.setData({ foundTime: e.detail.value }); },

  chooseImage() {
    const rest = 6 - this.data.images.length;
    if (rest <= 0) {
      wx.showToast({ title: '最多上传 6 张', icon: 'none' });
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const picked = res.tempFiles.map((f) => f.tempFilePath);
        this.setData({ images: [...this.data.images, ...picked] });
      }
    });
  },

  removeImage(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ images: this.data.images.filter((_, i) => i !== index) });
  },

  previewImage(e) {
    wx.previewImage({ current: e.currentTarget.dataset.src, urls: this.data.images });
  },

  chooseLocation() {
    wx.chooseLocation({
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          addressDesc: res.name || res.address || '已选择位置'
        });
      },
      fail: (err) => {
        const msg = (err && err.errMsg) || '';
        if (/cancel/i.test(msg)) {
          return;
        }
        wx.showToast({ title: '选择地点失败，请重试', icon: 'none' });
      }
    });
  },

  async uploadAll(paths) {
    const urls = [];
    for (const p of paths) {
      wx.showLoading({ title: `上传中 ${urls.length + 1}/${paths.length}`, mask: true });
      urls.push(await uploadFile(p));
    }
    return urls;
  },

  async onSubmit() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    const {
      images, foundDate, foundTime, latitude, longitude, addressDesc, description
    } = this.data;

    if (!description.trim()) {
      wx.showToast({ title: '请填写线索描述', icon: 'none' });
      return;
    }
    if (images.length === 0) {
      wx.showToast({ title: '请至少上传一张照片', icon: 'none' });
      return;
    }
    if (!latitude || !longitude) {
      wx.showToast({ title: '请选择发现地点', icon: 'none' });
      return;
    }
    if (this.data.uploading) {
      return;
    }

    this.setData({ uploading: true });
    wx.showLoading({ title: '上传中...', mask: true });
    try {
      const urls = await this.uploadAll(images);
      // foundAt 对齐后端 yyyy-MM-dd HH:mm:ss（无 T、带秒）；images 是逗号分隔字符串
      await lostCatApi.createClue({
        postId: this._postId,
        foundAt: `${foundDate} ${foundTime}:00`,
        latitude,
        longitude,
        addressDesc,
        images: urls.join(','),
        description: description.trim()
      });
      wx.hideLoading();
      wx.showToast({ title: '已提交，待审核', icon: 'success' });
      // 去「我的线索」看待审状态，redirectTo 避免回退到已提交的表单
      setTimeout(() => wx.redirectTo({ url: '/pages/lost-cat/mine?tab=clue' }), 1500);
    } catch (err) {
      wx.hideLoading();
      const msg = (err && (err.message || err.errMsg)) || '';
      wx.showToast({ title: msg || '提交失败', icon: 'none', duration: 3000 });
    } finally {
      this.setData({ uploading: false });
    }
  }
});
