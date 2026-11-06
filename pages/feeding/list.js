const { feedingApi } = require('../../utils/api');
const { TASK_TYPE_MAP, TASK_STATUS_MAP } = require('../../utils/constants');
const { volunteerLine, prettyTime } = require('../../utils/format');

Page({
  data: {
    taskList: [],
    currentStatus: '',
    onlyMine: false,
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false
  },

  onLoad() {
    this.loadTasks();
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, taskList: [] });
    this.loadTasks().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadTasks();
    }
  },

  async loadTasks() {
    this.setData({ loading: true });
    try {
      const params = { pageNum: this.data.pageNum, pageSize: this.data.pageSize };
      if (this.data.currentStatus !== '') {
        params.status = this.data.currentStatus;
      }
      if (this.data.onlyMine) {
        params.mine = true;
      }
      const result = await feedingApi.getTaskList(params);
      const tasks = (result.records || []).map(task => ({
        ...task,
        taskTypeText: TASK_TYPE_MAP[task.taskType] || '投喂',
        statusText: TASK_STATUS_MAP[task.status]?.text || '未知',
        statusClass: TASK_STATUS_MAP[task.status]?.class || 'info',
        createdText: prettyTime(task.createdAt),
        volunteerText: volunteerLine(task)
      }));
      this.setData({
        taskList: this.data.pageNum === 1 ? tasks : [...this.data.taskList, ...tasks],
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
    } catch (err) {
      console.error('加载任务失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  onFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({ currentStatus: status, pageNum: 1, hasMore: true, taskList: [] });
    this.loadTasks();
  },

  onToggleMine() {
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    this.setData({ onlyMine: !this.data.onlyMine, pageNum: 1, hasMore: true, taskList: [] });
    this.loadTasks();
  },

  async onAccept(e) {
    const taskId = e.currentTarget.dataset.id;
    const app = getApp();
    if (!app.globalData.userInfo) {
      wx.showToast({ title: '请先登录', icon: 'none' });
      return;
    }
    try {
      await feedingApi.acceptTask(taskId);
      wx.showToast({ title: '接单成功', icon: 'success' });
      this.setData({ pageNum: 1, hasMore: true, taskList: [] });
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
