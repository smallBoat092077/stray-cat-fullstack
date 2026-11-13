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
      this._calcSwiperHeight(posters);
    } catch (err) {
      console.error('加载海报失败:', err);
    }
  },

  async _calcSwiperHeight(posters) {
    if (!posters.length) return;
    // 等 swiper 渲染出来后量它的实际宽度：页面容器有左右 padding，
    // 不能直接拿全屏 windowWidth 算，否则高度和图片真实高度对不齐
    wx.nextTick(() => {
      wx.createSelectorQuery().in(this)
        .select('.poster-swiper').boundingClientRect()
        .exec(async (rects) => {
          const rect = rects && rects[0];
          const sw = rect && rect.width
            ? rect.width
            : wx.getSystemInfoSync().windowWidth - 24; // 兜底：量不到就按容器宽估算
          // 轮播里每张海报宽高比可能不同，容器高度只能统一取最大那张，
          // 否则更高的海报会被裁掉（2026-11-10 用户反馈「轮播图没有显示完整」）
          const heights = await Promise.all(posters.map((p) => new Promise((resolve) => {
            wx.getImageInfo({
              src: p.imageUrl,
              success: (r) => resolve(Math.round(sw * r.height / r.width)),
              fail: () => resolve(0)
            });
          })));
          const valid = heights.filter((h) => h > 0);
          this.setData({ swiperHeight: valid.length ? Math.max(...valid) : 340 });
        });
    });
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
