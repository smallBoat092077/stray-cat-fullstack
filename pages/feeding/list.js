const { feedingApi } = require('../../utils/api');

const TASK_TYPE_MAP = { 0: '日常投喂', 1: '紧急投喂', 2: '医疗救助' };
const TASK_STATUS_MAP = {
  0: { text: '待接单', class: 'warning' },
  1: { text: '已接单', class: 'info' },
  2: { text: '进行中', class: 'info' },
  3: { text: '已完成', class: 'success' },
  4: { text: '已取消', class: 'danger' }
};

Page({
  data: {
    taskList: [],
    currentStatus: '',
    pageNum: 1,
    loading: false
  },

  onLoad() {
    this.loadTasks();
  },

  async loadTasks() {
    this.setData({ loading: true });
    try {
      const params = { pageNum: this.data.pageNum, pageSize: 10 };
      if (this.data.currentStatus !== '') {
        params.status = this.data.currentStatus;
      }
      const result = await feedingApi.getTaskList(params);
      const tasks = (result.records || []).map(task => ({
        ...task,
        taskTypeText: TASK_TYPE_MAP[task.taskType] || '投喂',
        statusText: TASK_STATUS_MAP[task.status]?.text || '未知',
        statusClass: TASK_STATUS_MAP[task.status]?.class || 'info'
      }));
      this.setData({ taskList: tasks });
    } catch (err) {
      console.error('加载任务失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  onFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ currentStatus: status, pageNum: 1 });
    this.loadTasks();
  },

  async onAccept(e) {
    const taskId = e.currentTarget.dataset.id;
    const app = getApp();
    const userId = app.globalData.userInfo?.id;
    if (!userId) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    try {
      await feedingApi.acceptTask(taskId, userId);
      wx.showToast({ title: '接单成功', icon: 'success' });
      this.loadTasks();
    } catch (err) {
      wx.showToast({ title: err.message || '接单失败', icon: 'none' });
    }
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/feeding/detail?id=${id}` });
  }
});
