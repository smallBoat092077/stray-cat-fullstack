const { feedingApi, uploadFile } = require('../../utils/api');
const { TASK_TYPE_MAP, TASK_STATUS_MAP } = require('../../utils/constants');
const { prettyTime, volunteerLine } = require('../../utils/format');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe');

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
    showCatSelector: false // 显示猫咪选择器
  },

  onLoad(options) {
    prefetchTemplates();
    if (options.id) {
      this.loadTask(options.id);
    }
    // 检查是否启用识别模式（可从后端配置获取）
    this.setData({ recognizeMode: true });
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
          volunteerText: volunteerLine(task)
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
      address: task.locationDesc || '',
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
        this.doRecognize(photoPath);
      }
    });
  },

  /**
   * 执行识别
   */
  async doRecognize(photoPath) {
    this.setData({ recognizing: true, recognizeResult: null });
    wx.showLoading({ title: '识别中...' });

    try {
      const result = await feedingApi.recognizeCat(photoPath);
      wx.hideLoading();
      this.setData({
        recognizing: false,
        recognizeResult: result,
        showCatSelector: !result.success // 未识别成功时显示选择器
      });
    } catch (err) {
      wx.hideLoading();
      this.setData({ recognizing: false });
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
      wx.getLocation({
        type: 'gcj02',
        success: (res) => {
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
          } else {
            // 直接打卡或带识别打卡
            wx.showModal({
              title: '确认打卡',
              content: '请确认你已到达投喂点并完成投喂',
              success: (modalRes) => {
                if (modalRes.confirm) {
                  if (this.data.recognizeMode && this.data.selectedCatId && this.data.images.length > 0) {
                    // 带识别的打卡
                    this.doCheckInWithRecognize(res.latitude, res.longitude);
                  } else {
                    // 普通打卡
                    this.doCheckIn(res.latitude, res.longitude);
                  }
                }
              }
            });
          }
        },
        fail: () => {
          wx.showToast({ title: '获取位置失败', icon: 'none' });
        }
      });
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
      const photoPath = this.data.images[0]; // 第一张作为识别照片
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
      this.setData({ images: [], recognizeResult: null, selectedCatId: null });
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
