const { TAB_PAGES } = require('./constants.js');

// 把后台下发或页面传入的路径统一成「恰好一个前导斜杠 + 保留 query」，
// 这样 TAB_PAGES（带斜杠）与不带斜杠的旧口径都能命中同一份白名单。
function normalize(url) {
  return '/' + String(url || '').trim().replace(/^\/+/, '');
}

function isTabPage(url) {
  const path = normalize(url).split('?')[0];
  return TAB_PAGES.indexOf(path) >= 0;
}

/**
 * 通用页面跳转：命中 tabBar 白名单用 switchTab 并剥离 query，
 * 否则 navigateTo；目标不存在时统一 toast「页面不存在」。
 */
function navigateToPage(url) {
  if (!url) {
    return;
  }
  const full = normalize(url);
  const path = full.split('?')[0];
  if (isTabPage(path)) {
    wx.switchTab({ url: path });
    return;
  }
  wx.navigateTo({
    url: full,
    fail: () => wx.showToast({ title: '页面不存在', icon: 'none' })
  });
}

// 内容位（热点 / 海报 / 消息 / 公告）下发的 link 统一走这里，避免各处再分叉。
function navigateByLink(link) {
  navigateToPage(link);
}

module.exports = { navigateByLink, navigateToPage };
