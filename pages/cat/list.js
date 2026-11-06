const { catApi, posterApi, resolveImageUrl } = require('../../utils/api');
const { LIFE_STATUS_MAP, GENDER_MAP } = require('../../utils/constants');

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
    // 搜索
    searchKeyword: '',
    searchDisplayList: [],
    // 海报轮播
    posters: [],
    currentSwiperIndex: 0
  },

  onLoad() {
    this.loadPosters();
    this.loadCatList();
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

  // ---- 海报 ----
  async loadPosters() {
    try {
      const list = await posterApi.getList();
      const posters = (list || []).map(p => ({ ...p, imageUrl: resolveImageUrl(p.imageUrl) }));
      this.setData({ posters });
    } catch (err) {
      console.error('加载海报失败:', err);
    }
  },

  onSwiperChange(e) {
    this.setData({ currentSwiperIndex: e.detail.current });
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

  goToDetail(e) {
    const id = e.currentTarget.dataset.id;
    console.log('[list] goToDetail tap, id=', id, 'type=', typeof id);
    wx.navigateTo({ url: `/pages/cat/detail?id=${id}` });
  }
});
