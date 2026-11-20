// 微信官方贴图话题（wx.openPhotoTopicView，基础库 3.17.0+）
// 使用前提：在小程序后台创建同名话题（后台入口见《贴图话题-后台创建清单.md》），
// 话题的推荐链接建议指向猫咪详情页（/pages/cat/detail），用户从贴图点回
// 小程序的官方场景值为 1426，可做归因统计。
// TOPIC_NAME 留空则所有话题入口自动隐藏（与 utils/ads.js 同款降级策略）。
const TOPIC_NAME = '晒晒流浪猫';

// 入口是否展示：话题名已配置 且 当前基础库支持该 API
function topicAvailable() {
  return !!TOPIC_NAME && !!wx.canIUse && wx.canIUse('openPhotoTopicView');
}

// 拉起官方贴图话题发布页。
// resolve(true)=已成功拉起；resolve(false)=拉起失败（话题未创建/版本过低等），
// 由调用方决定是否提示，任何情况都不抛错、不影响主流程。
function openPhotoTopic() {
  if (!topicAvailable()) return Promise.resolve(false);
  return new Promise((resolve) => {
    wx.openPhotoTopicView({
      queryString: `topic=${TOPIC_NAME}`,
      extraData: { halfscreen: true },
      success: () => resolve(true),
      fail: () => resolve(false)
    });
  });
}

// 通用提示：拉起失败时给用户一个可理解的解释
function showTopicUnavailableToast() {
  wx.showToast({ title: '话题暂未开放，敬请期待', icon: 'none' });
}

module.exports = { TOPIC_NAME, topicAvailable, openPhotoTopic, showTopicUnavailableToast };
