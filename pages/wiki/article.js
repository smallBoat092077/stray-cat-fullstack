const { wikiApi, resolveImageUrl } = require('../../utils/api.js');

const CATEGORY_NAME = {
  feeding: '喂养',
  health: '健康观察',
  behavior: '行为沟通',
  hygiene: '清洁护理',
  firstaid: '应急处理'
};

function normTags(tags) {
  if (Array.isArray(tags)) return tags.filter(Boolean);
  return String(tags || '').split(/[,，]/).map((s) => s.trim()).filter(Boolean);
}

function decorateArticle(a) {
  return {
    ...a,
    categoryName: a.categoryName || CATEGORY_NAME[a.category] || '百科',
    cover: resolveImageUrl(a.coverUrl || a.cover || ''),
    tags: normTags(a.tags)
  };
}

Page({
  data: {
    id: null,
    article: null,
    related: [],
    loading: true,
    error: false,
    favorited: false
  },

  onLoad(options) {
    this.setData({ id: options.id });
    this.loadArticle();
  },

  async loadArticle() {
    if (!this.data.id) {
      this.setData({ loading: false, error: true });
      return;
    }
    this.setData({ loading: true, error: false });
    try {
      const res = await wikiApi.article(this.data.id);
      const article = decorateArticle(res || {});
      this.setData({ article, loading: false, error: false });
      this.loadRelated(article.category);
    } catch (err) {
      this.setData({ loading: false, error: true });
    }
  },

  async loadRelated(category) {
    try {
      const params = { pageNum: 1, pageSize: 8 };
      if (category) params.category = category;
      const res = await wikiApi.articles(params);
      const rows = Array.isArray(res) ? res : ((res && res.records) || []);
      const related = rows
        .filter((a) => String(a.id) !== String(this.data.id))
        .slice(0, 3)
        .map(decorateArticle);
      this.setData({ related });
    } catch (err) {
      // 相关阅读独立降级，不阻塞正文
    }
  },

  onRetry() {
    this.loadArticle();
  },

  onFavorite() {
    this.setData({ favorited: !this.data.favorited });
    wx.showToast({ title: this.data.favorited ? '已收藏' : '已取消收藏', icon: 'none' });
  },

  goArticle(e) {
    wx.navigateTo({ url: `/pages/wiki/article?id=${e.currentTarget.dataset.id}` });
  },

  onShareAppMessage() {
    const a = this.data.article || {};
    return {
      title: a.title || '猫咪百科',
      path: `/pages/wiki/article?id=${this.data.id}`,
      imageUrl: '/images/share-cover.png'
    };
  }
});
