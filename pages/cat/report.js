const { catApi, geoApi, uploadFile } = require('../../utils/api');
const { getCurrentLocation, notifyError } = require('../../utils/location');

Page({
  data: {
    genderIndex: 2,
    genderOptions: ['母', '公', '未知'],
    breedIndex: 0,
    breedOptions: ['未知', '短毛猫（中华田园猫）', '橘猫', '狸花猫', '黑白猫', '三花猫', '英短', '美短', '布偶', '暹罗', '波斯猫', '缅因猫', '异国短毛猫', '斯芬克斯（无毛猫）', '其他'],
    name: '', ageEstimate: '', color: '',
    locationDesc: '', personality: '', description: '',
    latitude: null, longitude: null, locationName: '',
    // 区域归属（区域划分需求 R2）：选点/定位成功后解析，随表单提交；解析失败留空由后端兜底
    provinceCode: '', cityCode: '', districtCode: '',
    images: [],
    envImages: [], envDesc: '',
    uploading: false
  },

  onNameInput(e) { this.setData({ name: e.detail.value }); },
  onAgeInput(e) { this.setData({ ageEstimate: e.detail.value }); },
  onColorInput(e) { this.setData({ color: e.detail.value }); },
  onLocationInput(e) { this.setData({ locationDesc: e.detail.value }); },
  onPersonalityInput(e) { this.setData({ personality: e.detail.value }); },
  onDescInput(e) { this.setData({ description: e.detail.value }); },
  onEnvDescInput(e) { this.setData({ envDesc: e.detail.value }); },
  onGenderChange(e) { this.setData({ genderIndex: e.detail.value }); },
  onBreedChange(e) { this.setData({ breedIndex: e.detail.value }); },

  /**
   * 地图选点：后台 chooseLocation 已审核通过，app.json 已声明。
   * 用户取消不打扰；授权被拒按原因引导去设置；接口异常时退回「用当前位置标记」，不卡上报。
   */
  chooseLocation() {
    wx.chooseLocation({
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          locationName: res.name || res.address || '已选择位置',
          provinceCode: '', cityCode: '', districtCode: ''
        });
        this.resolveRegion(res.latitude, res.longitude);
      },
      fail: (err) => {
        const errMsg = (err && err.errMsg) || '';
        if (/cancel/i.test(errMsg)) {
          return;
        }
        if (/auth deny|auth denied|USER_DENIED/i.test(errMsg)) {
          notifyError(err);
          return;
        }
        // 接口异常时不让用户卡在表单里：直接用当前位置兜底
        this.useCurrentLocationAsSpot();
      }
    });
  },

  /**
   * 用当前位置作为猫咪出没点，作为地图选点之外的快捷方式。
   */
  useCurrentLocationAsSpot() {
    wx.showLoading({ title: '定位中...' });
    getCurrentLocation()
      .then((loc) => {
        wx.hideLoading();
        this.setData({
          latitude: loc.latitude,
          longitude: loc.longitude,
          locationName: '已标记当前位置',
          provinceCode: '', cityCode: '', districtCode: ''
        });
        this.resolveRegion(loc.latitude, loc.longitude);
        wx.showToast({
          title: '已用当前位置标记，请补全地点描述',
          icon: 'none',
          duration: 3000
        });
      })
      .catch((err) => {
        wx.hideLoading();
        notifyError(err);
      });
  },

  /**
   * 解析坐标归属省市区（区域划分需求 R2）：静默进行，失败不提示、不阻断上报，
   * 区域码留空时后端会用坐标兜底补码（CatController.report → fillRegionIfMissing）。
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
      // 静默：未登录/网络异常都不影响上报
    }
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

  chooseEnvImage() {
    const rest = 6 - this.data.envImages.length;
    if (rest <= 0) {
      wx.showToast({ title: '环境照片最多6张', icon: 'none' });
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const picked = res.tempFiles.map(f => f.tempFilePath);
        this.setData({ envImages: [...this.data.envImages, ...picked] });
      }
    });
  },

  removeEnvImage(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ envImages: this.data.envImages.filter((_, i) => i !== index) });
  },

  previewLocalImage(e) {
    const { src, group } = e.currentTarget.dataset;
    const urls = group === 'env' ? this.data.envImages : this.data.images;
    wx.previewImage({ current: src, urls });
  },

  /**
   * 猫咪照 + 环境照一起传，loading 里带进度，用户能看出卡在哪一张
   */
  async uploadAll(catPaths, envPaths) {
    const total = catPaths.length + envPaths.length;
    const catUrls = [];
    const envUrls = [];
    for (const path of catPaths) {
      wx.showLoading({ title: `上传中 ${catUrls.length + envUrls.length + 1}/${total}`, mask: true });
      catUrls.push(await uploadFile(path));
    }
    for (const path of envPaths) {
      wx.showLoading({ title: `上传中 ${catUrls.length + envUrls.length + 1}/${total}`, mask: true });
      envUrls.push(await uploadFile(path));
    }
    return { catUrls, envUrls };
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

    const envDesc = (this.data.envDesc || '').trim();
    if (this.data.envImages.length === 0) {
      wx.showToast({ title: '请至少上传一张周边环境照片', icon: 'none' });
      return;
    }
    if (envDesc.length < 10) {
      wx.showToast({ title: '周边环境描述至少10个字', icon: 'none' });
      return;
    }
    if (envDesc.length > 200) {
      wx.showToast({ title: '周边环境描述最多200字', icon: 'none' });
      return;
    }

    if (this.data.uploading) {
      return;
    }
    this.setData({ uploading: true });
    wx.showLoading({ title: '上传中...', mask: true });

    try {
      const { catUrls, envUrls } = await this.uploadAll(this.data.images, this.data.envImages);

      // 真实地点名：只有地图选点拿到的 POI 名称/地址才入库；占位文案不当真实地址
      const rawName = (this.data.locationName || '').trim();
      const placeholder = ['已标记当前位置', '已选择位置'];
      const locationName = rawName && placeholder.indexOf(rawName) < 0 ? rawName : null;

      await catApi.report({
        name, gender: genderIndex, ageEstimate, breed: this.data.breedOptions[this.data.breedIndex], color,
        lifeStatus: 0, locationDesc, locationName, personality, description,
        latitude, longitude,
        provinceCode: this.data.provinceCode || null,
        cityCode: this.data.cityCode || null,
        districtCode: this.data.districtCode || null,
        coverImage: catUrls.length > 0 ? catUrls[0] : null,
        images: catUrls.join(','),
        environmentImages: envUrls.join(','),
        environmentDesc: envDesc
      });

      wx.hideLoading();
      wx.showToast({ title: '上报成功', icon: 'success' });
      // 上报页已降级为普通页面：从首页或「我的」navigateTo 进来，成功后自然回来源页
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
