const { feedbackApi, uploadFile, resolveImageUrl } = require('../../utils/api');
const { prettyTime } = require('../../utils/format');
const app = getApp();

const MAX_IMAGES = 3;
const CONTENT_MAX = 500;
const TYPE_OPTIONS = [
  { value: 0, label: '功能建议' },
  { value: 1, label: '问题反馈' },
  { value: 2, label: '其他' }
];
const STATUS_TEXT = ['待处理', '已回复', '已忽略'];
const PAGE_SIZE = 20;

Page({
  data: {
    typeOptions: TYPE_OPTIONS,
    typeIndex: 0,
    content: '',
    images: [],
    submitting: false,
    // 我的反馈
    records: [],
    statusText: STATUS_TEXT,
    pageNum: 1,
    total: 0,
    loadingMore: false,
    hasMore: true
  },

  onLoad() {
    this.loadMine(true);
  },

  onTypeChange(e) {
    // bindtap 的值在 currentTarget.dataset 里，e.detail.value 是 picker 事件才有的字段
    const index = Number(e.currentTarget.dataset.index);
    if (!Number.isInteger(index)) {
      return;
    }
    this.setData({ typeIndex: index });
  },

  onContentInput(e) {
    this.setData({ content: e.detail.value });
  },

  chooseImage() {
    const rest = MAX_IMAGES - this.data.images.length;
    if (rest <= 0) {
      wx.showToast({ title: `最多上传 ${MAX_IMAGES} 张截图`, icon: 'none' });
      return;
    }
    wx.chooseMedia({
      count: rest,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const picked = res.tempFiles.map((f) => f.tempFilePath);
        this.setData({ images: [...this.data.images, ...picked] });
      }
    });
  },

  removeImage(e) {
    const index = e.currentTarget.dataset.index;
    this.setData({ images: this.data.images.filter((_, i) => i !== index) });
  },

  previewImage(e) {
    const src = e.currentTarget.dataset.src;
    wx.previewImage({ current: src, urls: this.data.images });
  },

  async submit() {
    if (this.data.submitting) return;
    if (!app.ensureLogin()) return;

    const content = this.data.content.trim();
    if (!content) {
      wx.showToast({ title: '请填写反馈内容', icon: 'none' });
      return;
    }
    if (content.length > CONTENT_MAX) {
      wx.showToast({ title: `反馈内容最多 ${CONTENT_MAX} 字`, icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      // 先传截图拿到相对路径，再提交反馈
      let imagePaths = '';
      if (this.data.images.length) {
        wx.showLoading({ title: '上传截图中...', mask: true });
        const urls = [];
        for (const path of this.data.images) {
          urls.push(await uploadFile(path));
        }
        wx.hideLoading();
        imagePaths = urls.join(',');
      }

      // 兜底：typeIndex 意外变成 NaN 时回落到「功能建议」，不让提交静默失败
      const typeIndex = Number.isInteger(this.data.typeIndex) ? this.data.typeIndex : 0;
      await feedbackApi.submit({
        type: TYPE_OPTIONS[typeIndex].value,
        content,
        images: imagePaths
      });

      this.setData({ content: '', images: [] });
      wx.showToast({ title: '已提交，感谢反馈', icon: 'success' });
      // 提交完成即回到「我的」页，不必停留在表单
      setTimeout(() => wx.navigateBack(), 1200);
    } catch (err) {
      wx.hideLoading();
      // 网络层错误 toast 由 app.request 统一弹，业务报错（限频等）也已在 request 里提示
    } finally {
      this.setData({ submitting: false });
    }
  },

  async loadMine(reset) {
    if (!app.globalData.token) return;
    if (this.data.loadingMore) return;
    const pageNum = reset ? 1 : this.data.pageNum + 1;
    this.setData({ loadingMore: true });
    try {
      const page = await feedbackApi.mine(pageNum, PAGE_SIZE);
      // images 是逗号分隔的 /uploads 相对路径，转成完整地址供 <image> 用
      const records = (page.records || []).map((item) => ({
        ...item,
        imageList: item.images
          ? item.images.split(',').filter(Boolean).map((p) => resolveImageUrl(p))
          : [],
        createdAtText: prettyTime(item.createdAt)
      }));
      const merged = reset ? records : [...this.data.records, ...records];
      this.setData({
        records: merged,
        pageNum,
        total: page.total || 0,
        hasMore: merged.length < (page.total || 0)
      });
    } catch (err) {
      // toast 由 request 层统一处理
    } finally {
      this.setData({ loadingMore: false });
    }
  },

  previewRecordImage(e) {
    const { src, urls } = e.currentTarget.dataset;
    wx.previewImage({ current: src, urls });
  },

  onReachBottom() {
    if (this.data.hasMore) {
      this.loadMine(false);
    }
  },

  goLogin() {
    app.goLogin();
  }
});
