const { feedingApi, uploadFile, resolveImageUrl } = require('../../utils/api');
const { TASK_TYPE_MAP, TASK_STATUS_MAP } = require('../../utils/constants');
const { prettyTime, volunteerLine } = require('../../utils/format');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe');
const { getCurrentLocation, describeError } = require('../../utils/location');

Page({
  data: {
    task: null,
    images: [],
    canCheckIn: false,
    // 识别相关
    recognizeMode: false,  // 是否启用识别模式
    recognizing: false,    // 识别中
    recognizeResult: null, // 识别结果
    selectedCatId: null,   // 用户选择的猫咪 ID
    showCatSelector: false,// 显示猫咪选择器
    recognizePhoto: '',    // 本次用于识别（并作为留证）的照片
    confidence: null,      // 后端置信度分级 high/medium/low
    degraded: false,       // 识别服务是否降级
    rejected: false,       // 二期：拒识闸门命中（照片里没有可识别的猫）
    rejectMessage: '',     // 二期：拒识提示文案
    lowConfidence: false   // 低置信但仍有候选：展示候选供人工核对，绝不自动预选
  },

  onLoad(options) {
    prefetchTemplates();
    if (options.id) {
      this.loadTask(options.id);
    }
    // 灰度开关由独立 config 接口下发；失败或异常一律降级为普通打卡，且不与 loadTask 互相阻塞
    feedingApi.recognizeConfig()
      .then((config) => {
        this.setData({ recognizeMode: !!(config && config.enabled) });
      })
      .catch(() => {
        this.setData({ recognizeMode: false });
      });
  },

  async loadTask(id) {
    try {
      const task = await feedingApi.getTaskDetail(id);
      const userInfo = getApp().globalData.userInfo || {};
      this.setData({
        task: {
          ...task,
          taskTypeText: TASK_TYPE_MAP[task.taskType] || '投喂',
          statusText: TASK_STATUS_MAP[task.status]?.text || '未知',
          statusClass: TASK_STATUS_MAP[task.status]?.class || 'info',
          completedText: prettyTime(task.completedAt),
          volunteerText: volunteerLine(task),
          // 猫咪头像（/uploads 相对路径），无值时 WXML 走 default-cat.png 兜底
          catCoverImage: resolveImageUrl(task.catCoverImage)
        },
        canCheckIn: !!userInfo.id && userInfo.id === task.volunteerId
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onOpenMap() {
    const task = this.data.task;
    if (!task || !task.latitude || !task.longitude) {
      wx.showToast({ title: '该任务没有坐标', icon: 'none' });
      return;
    }
    wx.openLocation({
      latitude: Number(task.latitude),
      longitude: Number(task.longitude),
      name: `${task.catName || '投喂点'}`,
      address: task.locationName || task.locationDesc || '',
      scale: 18,
      fail: () => wx.showToast({ title: '打开地图失败', icon: 'none' })
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

  /**
   * 拍照识别猫咪（打卡前）
   */
  onRecognizeCat() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['camera'],
      success: (res) => {
        const photoPath = res.tempFiles[0].tempFilePath;
        // 把识别用照片一并作为留证图加入 images：去重、最多 3 张
        const images = this.data.images.slice();
        if (images.indexOf(photoPath) === -1 && images.length < 3) {
          images.push(photoPath);
        }
        this.setData({ recognizePhoto: photoPath, images });
        this.doRecognize(photoPath);
      }
    });
  },

  /**
   * 执行识别
   */
  async doRecognize(photoPath) {
    this.setData({
      recognizing: true,
      recognizeResult: null,
      confidence: null,
      degraded: false,
      rejected: false,
      rejectMessage: '',
      lowConfidence: false,
      selectedCatId: null,
      showCatSelector: false
    });
    wx.showLoading({ title: '识别中...' });

    try {
      const result = await feedingApi.recognizeCat(photoPath);
      wx.hideLoading();

      // 二期：拒识闸门命中（服务健康，但照片里没有可识别的猫咪）
      if (result && result.rejected === true) {
        this.setData({
          recognizing: false,
          rejected: true,
          rejectMessage: result.message || '照片中未检测到猫咪，请重新拍摄或手动选择猫咪',
          recognizeResult: { ...result, matches: [] },
          confidence: null,
          degraded: false,
          selectedCatId: null,
          showCatSelector: true
        });
        wx.showToast({ title: '照片中未检测到猫咪', icon: 'none' });
        return;
      }

      const degraded = !!(result && result.degraded === true);
      const confidence = degraded ? null : ((result && result.confidence) || null);
      const rawMatches = result && Array.isArray(result.matches) ? result.matches : [];
      // 二期：不再计算 / 展示相似度百分比，避免「99%」对用户造成误导
      // 头像补全：coverImage 是 /uploads 相对路径，必须 resolve 成完整 URL 才能在 <image> 显示
      const matches = rawMatches.map((m) => ({ ...m, coverImage: resolveImageUrl(m.coverImage) }));

      let visibleMatches = [];
      let selectedCatId = null;
      let showCatSelector = false;
      let lowConfidence = false;
      let tip = '';

      if (degraded) {
        // 服务不可用：不展示候选，走人工兜底，且不阻断打卡
        tip = '识别服务暂不可用，请手动选择猫咪';
        showCatSelector = true;
      } else if (matches.length > 0 && (confidence === 'high' || confidence === 'medium')) {
        // 二期：所有置信度都只展示候选，绝不自动预选，必须用户点选确认
        visibleMatches = matches;
        showCatSelector = true;
        tip = 'AI 识别仅供参考，请核对后点选';
      } else if (matches.length > 0) {
        // 低置信：后端仍返回候选，只是分数低。此前整段丢弃导致「未识别到匹配猫咪」，
        // 用户误以为基准图登记没生效。这里只把候选显示出来供人工核对：
        // 不自动预选、不展示相似度、不改变打卡归属（cat_id 仍由任务决定）。
        visibleMatches = matches;
        lowConfidence = true;
        showCatSelector = true;
        tip = '匹配度较低，仅供参考，请人工核对后点选';
      } else {
        showCatSelector = true;
        tip = '未识别到匹配猫咪，请手动选择';
      }

      this.setData({
        recognizing: false,
        recognizeResult: { ...result, matches: visibleMatches },
        confidence,
        lowConfidence,
        degraded,
        rejected: false,
        rejectMessage: '',
        selectedCatId,
        showCatSelector
      });
      wx.showToast({ title: tip, icon: 'none' });
    } catch (err) {
      wx.hideLoading();
      this.setData({ recognizing: false, lowConfidence: false });
      wx.showToast({ title: err.message || '识别失败', icon: 'none' });
    }
  },

  /**
   * 选择匹配结果中的猫咪
   */
  onSelectMatch(e) {
    const catId = e.currentTarget.dataset.catId;
    this.setData({ selectedCatId: catId, showCatSelector: false });
    wx.showToast({ title: '已选择', icon: 'success' });
  },

  /**
   * 手动选择猫咪（从任务关联的猫咪）
   */
  onSelectTaskCat() {
    const task = this.data.task;
    if (task && task.catId) {
      this.setData({ selectedCatId: task.catId, showCatSelector: false });
      wx.showToast({ title: '已选择任务猫咪', icon: 'success' });
    }
  },

  /**
   * 注册新猫（识别失败时）
   */
  onRegisterNewCat() {
    wx.showModal({
      title: '注册新猫',
      content: '这只猫不在数据库中，是否先上报猫咪信息？',
      success: (res) => {
        if (res.confirm) {
          // 上报页已降级为普通页面，不再是 tabBar 页
          wx.navigateTo({ url: '/pages/cat/report' });
        }
      }
    });
  },

  /**
   * 打卡（带识别）
   */
  onCheckIn() {
    // 订阅弹窗必须由点击直接触发，模板 ID 已在 onLoad 预取，这里不再等网络
    requestSubscribe(SUBSCRIBE_KEYS.checkIn).then(() => {
      getCurrentLocation()
        .then((loc) => this.confirmCheckIn(loc))
        .catch((err) => {
          const tip = describeError(err);
          wx.showToast({ title: tip.title, icon: 'none', duration: 3000 });
        });
    });
  },

  // 打卡前的确认链路：先看要不要识别猫咪，再弹确认框
  confirmCheckIn(loc) {
    if (this.data.recognizeMode && !this.data.selectedCatId) {
      // 需要先识别
      wx.showModal({
        title: '请先识别猫咪',
        content: '拍照识别猫咪身份后再打卡，或选择任务关联的猫咪',
        confirmText: '去拍照',
        cancelText: '选任务猫',
        success: (modalRes) => {
          if (modalRes.confirm) {
            this.onRecognizeCat();
          } else if (modalRes.cancel) {
            this.onSelectTaskCat();
          }
        }
      });
      return;
    }
    // 直接打卡或带识别打卡
    wx.showModal({
      title: '确认打卡',
      content: '请确认你已到达投喂点并完成投喂',
      success: (modalRes) => {
        if (!modalRes.confirm) {
          return;
        }
        if (this.data.recognizeMode && this.data.selectedCatId && this.data.images.length > 0) {
          // 带识别的打卡
          this.doCheckInWithRecognize(loc.latitude, loc.longitude);
        } else {
          // 普通打卡
          this.doCheckIn(loc.latitude, loc.longitude);
        }
      }
    });
  },

  async uploadImages() {
    const urls = [];
    for (const path of this.data.images) {
      const url = await uploadFile(path);
      urls.push(url);
    }
    return urls;
  },

  /**
   * 带识别的打卡
   */
  async doCheckInWithRecognize(latitude, longitude) {
    wx.showLoading({ title: '打卡中...' });
    try {
      const photoPath = this.data.recognizePhoto || this.data.images[0]; // 识别与留证必须是同一张
      await feedingApi.checkinWithRecognize(
        this.data.task.id,
        photoPath,
        this.data.selectedCatId,
        latitude,
        longitude,
        this.data.task.locationDesc,
        1, // 默认 1 份
        ''
      );
      wx.hideLoading();
      wx.showToast({ title: '打卡成功', icon: 'success' });
      this.setData({
        images: [], recognizeResult: null, selectedCatId: null, recognizePhoto: '',
        confidence: null, lowConfidence: false, degraded: false, rejected: false, rejectMessage: ''
      });
      this.loadTask(this.data.task.id);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: err.message || '打卡失败', icon: 'none' });
    }
  },

  /**
   * 普通打卡（原有逻辑）
   */
  async doCheckIn(latitude, longitude) {
    wx.showLoading({ title: '打卡中...' });
    try {
      const imageUrls = await this.uploadImages();
      await feedingApi.checkIn(this.data.task.id, latitude, longitude, imageUrls.join(','));
      wx.hideLoading();
      wx.showToast({ title: '打卡成功', icon: 'success' });
      this.setData({ images: [] });
      this.loadTask(this.data.task.id);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: err.message || '打卡失败', icon: 'none' });
    }
  }
});
