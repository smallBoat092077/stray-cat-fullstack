const { feedingApi } = require('../../utils/api');
const { TASK_TYPE_MAP, TASK_STATUS_MAP } = require('../../utils/constants');
const { volunteerLine, prettyTime } = require('../../utils/format');

Page({
  data: {
    taskList: [],
    currentStatus: '',
    onlyMine: false,
    searchKeyword: '',
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false,
    // 区域过滤（区域划分需求 R4/D7：按猫的归属区域），空对象 = 全部口径
    regionFilter: {},
    regionHasFilter: false,
    regionLabel: ''
  },

  onLoad() {
    this.loadTasks();
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, taskList: [], searchKeyword: '' });
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
      if (this.data.searchKeyword) {
        params.keyword = this.data.searchKeyword;
      }
      // 区域过滤（区域划分需求 R4/D7）：后端口径为区 > 市 > 省，不带 = 全部
      const rf = this.data.regionFilter || {};
      if (rf.districtCode) params.districtCode = rf.districtCode;
      else if (rf.cityCode) params.cityCode = rf.cityCode;
      else if (rf.provinceCode) params.provinceCode = rf.provinceCode;
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

  // 区域筛选变化（区域划分需求 R4）：重置分页重查
  onRegionChange(e) {
    const d = e.detail || {};
    const hasFilter = !!(d.provinceCode || d.cityCode || d.districtCode);
    this.setData({
      regionFilter: d,
      regionHasFilter: hasFilter,
      regionLabel: hasFilter ? (d.label || '') : '',
      pageNum: 1,
      hasMore: true,
      taskList: []
    });
    this.loadTasks();
  },

  // 空态「看看全市的猫」：调用组件提升到市级重查（不覆盖手选记忆）
  onViewCity() {
    const picker = this.selectComponent('#regionPicker');
    if (picker) {
      picker.switchToCity();
    }
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

  onSearchInput(e) {
    const keyword = e.detail.value.trim();
    clearTimeout(this._searchTimer);
    this._searchTimer = setTimeout(() => this.performSearch(keyword), 350);
  },

  onSearchConfirm(e) {
    this.performSearch(e.detail.value.trim());
  },

  onSearchClear() {
    this.setData({ searchKeyword: '' });
    this.performSearch('');
  },

  performSearch(keyword) {
    this.setData({ searchKeyword: keyword, pageNum: 1, hasMore: true, taskList: [] });
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
