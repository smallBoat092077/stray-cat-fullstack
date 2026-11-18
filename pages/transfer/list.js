const { transferApi, resolveImageUrl } = require('../../utils/api.js');

const BREEDS = ['', '橘猫', '英短', '布偶', '狸花', '中华田园', '渐层', '暹罗'];

function formatAge(months) {
  if (months == null || months === '') return '年龄未知';
  const m = Number(months);
  if (isNaN(m)) return '年龄未知';
  if (m < 12) return `${m}个月`;
  const y = Math.floor(m / 12);
  const rest = m % 12;
  return rest ? `${y}岁${rest}个月` : `${y}岁`;
}

function genderText(g) {
  if (g === 0 || g === '0') return '母';
  if (g === 1 || g === '1') return '公';
  return '未知';
}

function normPhotos(photos) {
  const arr = Array.isArray(photos)
    ? photos
    : String(photos || '').split(',').filter(Boolean);
  return arr.map((p) => resolveImageUrl(p)).filter(Boolean);
}

function decorate(post) {
  const photos = normPhotos(post.photos);
  const traits = [];
  if (post.neutered === 1 || post.neutered === true) traits.push('已绝育');
  if (post.vaccinated === 1 || post.vaccinated === true) traits.push('疫苗齐全');
  if (post.personality) traits.push(post.personality);
  return {
    ...post,
    photos,
    cover: photos[0] || '',
    ageText: formatAge(post.ageMonths),
    genderText: genderText(post.gender),
    traitText: traits.join(' · '),
    regionText: [post.provinceName, post.cityName, post.districtName].filter(Boolean).join(' ')
      || post.addressHint
      || '地点待补充'
  };
}

Page({
  data: {
    breeds: BREEDS,
    breedIndex: 0,
    breed: '',
    keyword: '',
    regionFilter: {},
    list: [],
    loading: false,
    error: false,
    pageNum: 1,
    pageSize: 10,
    hasMore: true
  },

  onLoad() {
    this.loadList(true);
  },

  onPullDownRefresh() {
    this.loadList(true).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading && !this.data.error) {
      this.loadList(false);
    }
  },

  async loadList(reset) {
    if (this.data.loading) return;
    const pageNum = reset ? 1 : this.data.pageNum;
    this.setData({ loading: true, error: false });
    try {
      const params = { pageNum, pageSize: this.data.pageSize };
      if (this.data.breed) params.breed = this.data.breed;
      if (this.data.keyword) params.keyword = this.data.keyword;
      const rf = this.data.regionFilter || {};
      if (rf.districtCode) params.districtCode = rf.districtCode;
      else if (rf.cityCode) params.cityCode = rf.cityCode;
      else if (rf.provinceCode) params.provinceCode = rf.provinceCode;

      const res = await transferApi.list(params);
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      const items = rows.map(decorate);
      const hasMore = res && typeof res.current === 'number' && typeof res.pages === 'number'
        ? res.current < res.pages
        : rows.length >= this.data.pageSize;
      this.setData({
        list: reset ? items : this.data.list.concat(items),
        pageNum: pageNum + 1,
        hasMore,
        loading: false,
        error: false
      });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadList(true);
  },

  onRegionChange(e) {
    this.setData({
      regionFilter: e.detail || {},
      pageNum: 1,
      hasMore: true,
      list: []
    });
    this.loadList(true);
  },

  onBreedTap(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.breedIndex) return;
    this.setData({
      breedIndex: index,
      breed: this.data.breeds[index],
      pageNum: 1,
      hasMore: true,
      list: []
    });
    this.loadList(true);
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value });
  },

  onSearch() {
    this.setData({ pageNum: 1, hasMore: true, list: [] });
    this.loadList(true);
  },

  goDetail(e) {
    wx.navigateTo({ url: `/pages/transfer/detail?id=${e.currentTarget.dataset.id}` });
  },

  goPublish() {
    wx.navigateTo({ url: '/pages/transfer/publish' });
  }
});
