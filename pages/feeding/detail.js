const { feedingApi, uploadFile } = require('../../utils/api');
const { TASK_TYPE_MAP, TASK_STATUS_MAP } = require('../../utils/constants');
const { prettyTime, volunteerLine } = require('../../utils/format');
const { prefetchTemplates, requestSubscribe, SUBSCRIBE_KEYS } = require('../../utils/subscribe');

Page({
  data: { task: null, images: [], canCheckIn: false },

  onLoad(options) {
    prefetchTemplates();
    if (options.id) {
      this.loadTask(options.id);
    }
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

  onCheckIn() {
    // 订阅弹窗必须由点击直接触发，模板 ID 已在 onLoad 预取，这里不再等网络
    requestSubscribe(SUBSCRIBE_KEYS.checkIn).then(() => {
      wx.getLocation({
        type: 'gcj02',
        success: (res) => {
          wx.showModal({
            title: '确认打卡',
            content: '请确认你已到达投喂点并完成投喂',
            success: (modalRes) => {
              if (modalRes.confirm) {
                this.doCheckIn(res.latitude, res.longitude);
              }
            }
          });
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
