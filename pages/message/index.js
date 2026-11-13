const { messageApi } = require('../../utils/api');
const { prettyTime } = require('../../utils/format');
const { prefetchTemplates, setTemplateMap, requestNextGroup, resetGroupCursor } = require('../../utils/subscribe');

// tabBar 里的页面只能用 switchTab 打开，用 navigateTo 会静默失败
const TAB_PAGES = ['pages/index/index', 'pages/cat/list', 'pages/feeding/list', 'pages/profile/index'];

// pushStatus 与后端口径一致：0 仅站内 / 1 已推送 / 2 无订阅额度 / 3 推送失败 / 4 未绑定微信
function pushHintOf(item) {
  if (item.pushStatus === 2) {
    return '未推送微信 · 授权后可收到';
  }
  if (item.pushStatus === 3) {
    return '微信推送失败 · 站内消息仍有效';
  }
  if (item.pushStatus === 4) {
    return '未推送微信 · 账号未绑定微信';
  }
  return '';
}

Page({
  data: {
    messages: [],
    loading: false,
    // 服务端已经配好模板时才给「开启微信通知」入口，没配点了也没用
    canNotify: false,
    notifyTip: false
  },

  onLoad() {
    prefetchTemplates();
  },

  onShow() {
    this.loadMessages();
  },

  onPullDownRefresh() {
    this.loadMessages().then(() => wx.stopPullDownRefresh());
  },

  async loadMessages() {
    if (this.data.loading) {
      return;
    }
    this.setData({ loading: true });
    try {
      const [page, templates] = await Promise.all([
        messageApi.list(1, 50),
        messageApi.templates().catch(() => ({}))
      ]);
      const rows = (page && page.records) || [];
      const hasUnread = rows.some((item) => !item.readFlag);
      const messages = rows.map((item) => ({
        ...item,
        unread: !item.readFlag,
        timeText: prettyTime(item.createdAt),
        pushHint: pushHintOf(item)
      }));
      const canNotify = !!templates && Object.keys(templates).length > 0;
      // 模板表喂给 subscribe 模块：prefetch 可能还没回来，别让点按钮时空转
      setTemplateMap(templates);
      this.setData({ messages, loading: false, canNotify });
      // 看过就算读，未读数在「我的」页会随之清零
      if (hasUnread) {
        messageApi.readAll().catch(() => {});
      }
    } catch (err) {
      this.setData({ loading: false });
      wx.showToast({ title: '网络异常', icon: 'none' });
    }
  },

  /**
   * 一次性订阅：用户点一次同意 = 一条推送额度。被邀请共同领养、被审核这类
   * 由别人触发的事没法当场补授权，所以这里给一个提前攒额度的入口。
   * 微信限制一次手势只能弹一个授权窗（共 9 个模板、一批 3 个），
   * 所以每次点击弹一批，如实提示进度，绝不默认报成功。
   */
  async onEnableNotify() {
    const r = await requestNextGroup();
    let title;
    if (r.done && r.accept > 0) {
      title = `三批全部完成，本批订阅 ${r.accept} 项`;
      resetGroupCursor();
    } else if (r.done) {
      title = '三批已走完，如需重试再点一次';
      resetGroupCursor();
    } else if (r.accept > 0) {
      title = `第 ${r.batch}/${r.total} 批订阅 ${r.accept} 项，请再点一次`;
    } else if (r.ban > 0) {
      title = '有通知此前被选了「总是拒绝」，微信不再询问';
    } else if (r.reject > 0) {
      title = `第 ${r.batch}/${r.total} 批被拒绝，可再点重试`;
    } else {
      title = '授权窗口未能弹出，请稍后再试';
    }
    wx.showToast({ title, icon: 'none', duration: 3000 });
    this.setData({ notifyTip: false });
  },

  formatTime(value) {
    if (!value) {
      return '';
    }
    return String(value).replace('T', ' ').slice(0, 16);
  },

  tapMessage(e) {
    // 跳转路径带 ? 和 &，放 data-* 里容易被属性解析干扰，改从数组里取
    const item = this.data.messages[e.currentTarget.dataset.index];
    const page = item && item.page;
    if (!page) {
      return;
    }
    const path = page.replace(/^\//, '');
    if (TAB_PAGES.indexOf(path.split('?')[0]) >= 0) {
      wx.switchTab({ url: `/${path}` });
      return;
    }
    wx.navigateTo({ url: `/${path}` });
  }
});
