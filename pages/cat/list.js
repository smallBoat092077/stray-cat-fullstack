const { catApi, resolveImageUrl } = require('../../utils/api');
const { LIFE_STATUS_MAP, GENDER_MAP } = require('../../utils/constants.js');

const SEARCH_DEBOUNCE_MS = 350;

Page({
  data: {
    catList: [],
    displayList: [],
    focusedCat: null,
    currentStatus: '',
    pageNum: 1,
    pageSize: 10,
    hasMore: true,
    loading: false,
    // 区域过滤（区域划分需求 R4）：组件 change 事件下发，空对象 = 全部口径
    regionFilter: {},
    regionHasFilter: false,
    regionLabel: '',
    // 搜索
    searchKeyword: '',
    searchDisplayList: [],
    // 猫咪统计（去轮播改造）：接口失败时 catStatsReady 保持 false，信息条隐藏
    catTotal: 0,
    catAdoptable: 0,
    catStatsReady: false
  },

  onLoad() {
    this.loadCatList();
    this.loadCatStats();
  },

  onShow() {
    const app = getApp();
    const focusId = app.globalData.focusCatId;
    if (focusId) {
      app.globalData.focusCatId = null;
      this.focusCat(focusId);
    }
  },

  onPullDownRefresh() {
    this.setData({ pageNum: 1, hasMore: true, catList: [], focusedCat: null, searchKeyword: '' });
    this.loadCatList().then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (!this.data.searchKeyword && this.data.hasMore && !this.data.loading) {
      this.loadCatList();
    }
  },

  // ---- 猫咪统计（去轮播改造） ----
  async loadCatStats() {
    try {
      const stats = await catApi.getStats();
      if (stats && typeof stats.total === 'number') {
        this.setData({
          catTotal: stats.total,
          catAdoptable: typeof stats.adoptable === 'number' ? stats.adoptable : 0,
          catStatsReady: true
        });
      }
    } catch (err) {
      // 静默：统计接口失败时信息条隐藏，不影响列表
      console.warn('加载猫咪统计失败:', err);
    }
  },

  // 「去领养」：等价于点状态筛选栏的「待领养」，并回到顶部
  onGoAdopt() {
    this.setData({
      currentStatus: 2,
      pageNum: 1,
      hasMore: true,
      catList: [],
      focusedCat: null
    });
    this.loadCatList(this.data.searchKeyword);
    wx.pageScrollTo({ scrollTop: 0, duration: 200 });
  },

  // ---- 搜索 ----
  onSearchInput(e) {
    const keyword = e.detail.value.trim();
    clearTimeout(this._searchTimer);
    this._searchTimer = setTimeout(() => {
      this.performSearch(keyword);
    }, SEARCH_DEBOUNCE_MS);
  },

  onSearchClear() {
    this.setData({ searchKeyword: '' });
    this.performSearch('');
  },

  performSearch(keyword) {
    this.setData({
      searchKeyword: keyword,
      pageNum: 1,
      hasMore: true,
      catList: [],
      focusedCat: null
    });
    this.loadCatList(keyword);
  },

  // ---- 猫咪列表 ----
  decorate(cat) {
    return {
      ...cat,
      coverImage: resolveImageUrl(cat.coverImage),
      lifeStatusText: LIFE_STATUS_MAP[cat.lifeStatus] ? LIFE_STATUS_MAP[cat.lifeStatus].text : '未知',
      lifeStatusClass: LIFE_STATUS_MAP[cat.lifeStatus] ? LIFE_STATUS_MAP[cat.lifeStatus].class : 'info',
      genderText: GENDER_MAP[cat.gender] || '未知'
    };
  },

  refreshDisplay() {
    const focus = this.data.focusedCat;
    const source = this.data.searchKeyword ? this.data.catList : this.data.catList;
    const rest = focus
      ? source.filter(cat => cat.id !== focus.id)
      : source;
    this.setData({ displayList: focus ? [focus, ...rest] : rest });
  },

  async focusCat(id) {
    try {
      const cat = await catApi.getDetail(id);
      this.setData({ focusedCat: this.decorate(cat) });
      this.refreshDisplay();
    } catch (err) {
      console.error('加载选中的猫咪失败:', err);
    }
  },

  async loadCatList(keyword) {
    this.setData({ loading: true });
    try {
      const params = {
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize
      };
      if (this.data.currentStatus !== '') {
        params.lifeStatus = this.data.currentStatus;
      }
      if (keyword || this.data.searchKeyword) {
        params.keyword = keyword || this.data.searchKeyword;
      }
      // 区域过滤（区域划分需求 R4）：后端口径为区 > 市 > 省，不带 = 全部
      const rf = this.data.regionFilter || {};
      if (rf.districtCode) params.districtCode = rf.districtCode;
      else if (rf.cityCode) params.cityCode = rf.cityCode;
      else if (rf.provinceCode) params.provinceCode = rf.provinceCode;
      const result = await catApi.getList(params);
      const cats = (result.records || []).map(cat => this.decorate(cat));
      this.setData({
        catList: this.data.pageNum === 1 ? cats : [...this.data.catList, ...cats],
        hasMore: result.current < result.pages,
        pageNum: this.data.pageNum + 1
      });
      this.refreshDisplay();
    } catch (err) {
      console.error('加载猫咪列表失败:', err);
    } finally {
      this.setData({ loading: false });
    }
  },

  onStatusFilter(e) {
    const status = e.currentTarget.dataset.status;
    this.setData({
      currentStatus: status,
      pageNum: 1,
      hasMore: true,
      catList: [],
      focusedCat: null
    });
    this.loadCatList(this.data.searchKeyword);
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
      catList: [],
      focusedCat: null
    });
    this.loadCatList(this.data.searchKeyword);
  },

  // 空态「看看全市的猫」：调用组件提升到市级重查（不覆盖手选记忆）
  onViewCity() {
    const picker = this.selectComponent('#regionPicker');
    if (picker) {
      picker.switchToCity();
    }
  },

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    console.log('[list] goToDetail tap, id=', id, 'type=', typeof id);
    wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
  }
});
