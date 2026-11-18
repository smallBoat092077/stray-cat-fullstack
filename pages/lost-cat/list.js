const { lostCatApi, resolveImageUrl } = require('../../utils/api.js');

const STATUS_MAP = {
  0: { text: '寻找中', cls: 'warning' },
  1: { text: '已找到', cls: 'success' },
  2: { text: '已关闭', cls: 'info' }
};

function formatTime(v) {
  if (!v) return '';
  return String(v).replace('T', ' ').slice(0, 16);
}

// images 后端是逗号分隔字符串，VO 也可能直接给数组，两种都兼容
function normImages(images) {
  const arr = Array.isArray(images)
    ? images
    : String(images || '').split(',').filter(Boolean);
  return arr.map((p) => resolveImageUrl(p)).filter(Boolean);
}

function decorate(post) {
  const imagesList = normImages(post.images);
  const st = STATUS_MAP[post.status] || STATUS_MAP[0];
  return {
    ...post,
    imagesList,
    cover: imagesList[0] || '',
    statusText: st.text,
    statusClass: st.cls,
    lostAtText: formatTime(post.lostAt)
  };
}

Page({
  data: {
    filters: [
      { key: '', label: '全部' },
      { key: '0', label: '寻找中' },
      { key: '1', label: '已找到' }
    ],
    status: '',
    list: [],
    loading: false,
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    // 区域过滤（区域划分需求 R4），空对象 = 全部口径
    regionFilter: {},
    regionHasFilter: false,
    regionLabel: ''
  },

  onLoad() {
    this.loadList(true);
  },

  onPullDownRefresh() {
    this.loadList(true).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadList(false);
    }
  },

  async loadList(reset) {
    if (this.data.loading) {
      return;
    }
    const pageNum = reset ? 1 : this.data.pageNum;
    this.setData({ loading: true });
    try {
      const params = { pageNum, pageSize: this.data.pageSize };
      if (this.data.status !== '') {
        params.status = this.data.status;
      }
      // 区域过滤（区域划分需求 R4）：后端口径为区 > 市 > 省，不带 = 全部
      const rf = this.data.regionFilter || {};
      if (rf.districtCode) params.districtCode = rf.districtCode;
      else if (rf.cityCode) params.cityCode = rf.cityCode;
      else if (rf.provinceCode) params.provinceCode = rf.provinceCode;
      const res = await lostCatApi.list(params);
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      const items = rows.map(decorate);
      let hasMore;
      if (res && typeof res.current === 'number' && typeof res.pages === 'number') {
        hasMore = res.current < res.pages;
      } else {
        hasMore = rows.length >= this.data.pageSize;
      }
      this.setData({
        list: reset ? items : [...this.data.list, ...items],
        pageNum: pageNum + 1,
        hasMore,
        loading: false
      });
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: '网络异常，请重试', icon: 'none' });
    }
  },

  onStatusFilter(e) {
    const index = e.currentTarget.dataset.index;
    const status = this.data.filters[index].key;
    if (status === this.data.status) {
      return;
    }
    this.setData({ status, pageNum: 1, hasMore: true, list: [] });
    this.loadList(true);
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
      list: []
    });
    this.loadList(true);
  },

  // 空态「看看全市的猫」：调用组件提升到市级重查（不覆盖手选记忆）
  onViewCity() {
    const picker = this.selectComponent('#regionPicker');
    if (picker) {
      picker.switchToCity();
    }
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/lost-cat/detail?id=${id}` });
  },

  goCreate() {
    wx.navigateTo({ url: '/pages/lost-cat/create' });
  },

  goMine() {
    wx.navigateTo({ url: '/pages/lost-cat/mine' });
  }
});
