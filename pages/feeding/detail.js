const { feedingApi } = require('../../utils/api');

const TASK_TYPE_MAP = { 0: '日常投喂', 1: '紧急投喂', 2: '医疗救助' };
const TASK_STATUS_MAP = {
  0: { text: '待接单', class: 'warning' },
  1: { text: '已接单', class: 'info' },
  3: { text: '已完成', class: 'success' }
};

Page({
  data: { task: null },

  onLoad(options) {
    if (options.id) {
      this.loadTask(options.id);
    }
  },

  async loadTask(id) {
    try {
      const task = await feedingApi.getTaskDetail(id);
      this.setData({
        task: {
          ...task,
          taskTypeText: TASK_TYPE_MAP[task.taskType] || '投喂',
          statusText: TASK_STATUS_MAP[task.status]?.text || '未知',
          statusClass: TASK_STATUS_MAP[task.status]?.class || 'info'
        }
      });
    } catch (err) {
      wx.showToast({ title: '加载失败', icon: 'none' });
    }
  },

  onCheckIn() {
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
  },

  async doCheckIn(latitude, longitude) {
    wx.showLoading({ title: '打卡中...' });
    try {
      await feedingApi.checkIn(this.data.task.id, latitude, longitude, '');
      wx.hideLoading();
      wx.showToast({ title: '打卡成功', icon: 'success' });
      this.loadTask(this.data.task.id);
    } catch (err) {
      wx.hideLoading();
      wx.showToast({ title: err.message || '打卡失败', icon: 'none' });
    }
  }
});
