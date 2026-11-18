const { wikiApi, resolveImageUrl } = require('../../utils/api.js');

const CATEGORY_META = {
  feeding: { name: '喂养', initial: '喂' },
  health: { name: '健康观察', initial: '健' },
  behavior: { name: '行为沟通', initial: '行' },
  hygiene: { name: '清洁护理', initial: '洁' },
  firstaid: { name: '应急处理', initial: '急' }
};

function formatViews(n) {
  if (n == null) return '0';
  if (n >= 10000) return `${(n / 10000).toFixed(1)}w`;
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`;
  return String(n);
}

function decorateArticle(a) {
  const meta = CATEGORY_META[a.category] || { name: a.category || '百科', initial: '卷' };
  return {
    ...a,
    categoryName: a.categoryName || meta.name,
    initial: meta.initial,
    cover: resolveImageUrl(a.coverUrl || a.cover || ''),
    viewsText: formatViews(a.viewCount),
    readText: a.readMinutes ? `约 ${a.readMinutes} 分钟` : ''
  };
}

Page({
  data: {
    categories: [],
    activeCategory: '',
    articles: [],
    loading: true,
    error: false,
    totalArticles: 0
  },

  onLoad() {
    this.loadAll();
  },

  onPullDownRefresh() {
    this.loadAll().then(() => wx.stopPullDownRefresh());
  },

  async loadAll() {
    this.setData({ loading: true, error: false });
    try {
      const [cats, arts] = await Promise.all([
        wikiApi.categories().catch(() => []),
        wikiApi.articles({ pageNum: 1, pageSize: 20 })
      ]);
      const catList = (Array.isArray(cats) ? cats : []).map((c) => {
        const meta = CATEGORY_META[c.category] || { initial: '卷' };
        return {
          ...c,
          name: c.name || meta.name,
          initial: meta.initial,
          count: c.count || 0
        };
      });
      const rows = Array.isArray(arts) ? arts : ((arts && arts.records) || []);
      const total = (arts && arts.total) != null ? arts.total : rows.length;
      this.setData({
        categories: catList,
        totalArticles: total,
        articles: rows.map(decorateArticle),
        loading: false,
        error: false
      });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  onRetry() {
    this.loadAll();
  },

  async onCategoryTap(e) {
    const category = e.currentTarget.dataset.category || '';
    if (category === this.data.activeCategory) return;
    this.setData({ activeCategory: category, loading: true });
    try {
      const params = { pageNum: 1, pageSize: 20 };
      if (category) params.category = category;
      const res = await wikiApi.articles(params);
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      this.setData({ articles: rows.map(decorateArticle), loading: false, error: false });
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  goQuiz() {
    wx.navigateTo({ url: '/pages/wiki/quiz' });
  },

  goMyQuiz() {
    wx.navigateTo({ url: '/pages/wiki/my-quiz' });
  },

  goArticle(e) {
    wx.navigateTo({ url: `/pages/wiki/article?id=${e.currentTarget.dataset.id}` });
  }
});
