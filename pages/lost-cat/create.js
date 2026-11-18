const { lostCatApi, geoApi, uploadFile } = require('../../utils/api.js');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe.js');

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
    catName: '',
    coatDesc: '',
    images: [], // 本地临时路径，提交前统一上传
    lostDate: today(),
    lostTime: nowTime(),
    latitude: null,
    longitude: null,
    addressDesc: '',
    // 区域归属（区域划分需求 R2）：选点后静默解析，随表单提交；失败留空由后端兜底
    provinceCode: '',
    cityCode: '',
    districtCode: '',
    contactWay: 'inapp', // 默认站内消息，不公开展示未经审核的手机号
    contactValue: '',
    uploading: false
  },

  onLoad() {
    // 模板 ID 由服务端 /message/templates 下发，提交前先预热，手势内才能弹出订阅窗
    prefetchTemplates();
  },

  onNameInput(e) { this.setData({ catName: e.detail.value }); },
  onCoatInput(e) { this.setData({ coatDesc: e.detail.value }); },
  onContactInput(e) { this.setData({ contactValue: e.detail.value }); },
  onDateChange(e) { this.setData({ lostDate: e.detail.value }); },
  onTimeChange(e) { this.setData({ lostTime: e.detail.value }); },
  onContactWay(e) { this.setData({ contactWay: e.currentTarget.dataset.way }); },

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
          addressDesc: res.name || res.address || '已选择位置',
          provinceCode: '', cityCode: '', districtCode: ''
        });
        this.resolveRegion(res.latitude, res.longitude);
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

  /**
   * 解析坐标归属省市区（区域划分需求 R2）：静默失败不阻断发布，
   * 区域码留空时后端会用坐标兜底补码（LostCatController.create）。
   */
  async resolveRegion(latitude, longitude) {
    try {
      const geo = await geoApi.reverse(latitude, longitude);
      if (geo && geo.success) {
        this.setData({
          provinceCode: geo.provinceCode || '',
          cityCode: geo.cityCode || '',
          districtCode: geo.districtCode || ''
        });
      }
    } catch (err) {
      // 静默
    }
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
      catName, coatDesc, images, lostDate, lostTime,
      latitude, longitude, addressDesc, contactWay, contactValue
    } = this.data;

    if (!catName.trim() || !coatDesc.trim()) {
      wx.showToast({ title: '请填写猫咪名字和花纹描述', icon: 'none' });
      return;
    }
    if (images.length === 0) {
      wx.showToast({ title: '请至少上传一张照片', icon: 'none' });
      return;
    }
    if (!latitude || !longitude) {
      wx.showToast({ title: '请选择丢失地点', icon: 'none' });
      return;
    }
    if (contactWay === 'phone' && !contactValue.trim()) {
      wx.showToast({ title: '请填写联系电话', icon: 'none' });
      return;
    }
    if (this.data.uploading) {
      return;
    }

    // 发布者是「有人见过你的猫」线索通知的收件人，提交前攒一次订阅额度。
    // 模板 ID 未配置时 requestSubscribe 直接 skipped 返回，不阻断发布流程。
    await requestSubscribe(SUBSCRIBE_KEYS.lostCatClue);

    this.setData({ uploading: true });
    wx.showLoading({ title: '上传中...', mask: true });
    try {
      const urls = await this.uploadAll(images);
      await lostCatApi.create({
        catName: catName.trim(),
        coatDesc: coatDesc.trim(),
        images: urls.join(','),
        lostAt: `${lostDate} ${lostTime}:00`,
        latitude,
        longitude,
        addressDesc,
        provinceCode: this.data.provinceCode || null,
        cityCode: this.data.cityCode || null,
        districtCode: this.data.districtCode || null,
        contactWay,
        contactValue: contactWay === 'phone' ? contactValue.trim() : ''
      });
      wx.hideLoading();
      wx.showToast({ title: '提交成功，待审核', icon: 'success' });
      // 发布后去「我的发布」看待审状态，redirectTo 避免回退到已提交的表单
      setTimeout(() => wx.redirectTo({ url: '/pages/lost-cat/mine' }), 1500);
    } catch (err) {
      wx.hideLoading();
      const msg = (err && (err.message || err.errMsg)) || '';
      wx.showToast({ title: msg || '提交失败', icon: 'none', duration: 3000 });
    } finally {
      this.setData({ uploading: false });
    }
  }
});
