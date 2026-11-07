const { messageApi } = require('./api');

// 模板 ID 由服务端 /message/templates 下发，后台没配就返回空对象，
// 这时 requestSubscribe 直接跳过，业务照常提交，只是收不到微信推送。
let templateMap = null;
let fetching = false;

// 一次 wx.requestSubscribeMessage 最多 3 个模板，超出的先忽略
const MAX_TEMPLATES = 3;

function prefetchTemplates() {
  if (templateMap || fetching) {
    return;
  }
  fetching = true;
  messageApi.templates()
    .then((data) => {
      templateMap = data || {};
    })
    .catch(() => {
      // 拉不到就这次不弹授权，不影响页面
    })
    .finally(() => {
      fetching = false;
    });
}

/**
 * 必须在点击事件里同步调用：微信要求订阅弹窗由用户手势触发，
 * 所以模板 ID 提前 prefetch 到内存，这里不再 await 网络。
 * 一次授权 = 一条推送额度，返回的 promise 只表示弹窗已关闭，不区分用户同意与否。
 */
function requestSubscribe(templateKeys) {
  const ids = [];
  (templateKeys || []).forEach((key) => {
    const id = templateMap && templateMap[key];
    if (id && ids.indexOf(id) < 0) {
      ids.push(id);
    }
  });
  if (!ids.length || !wx.requestSubscribeMessage) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    wx.requestSubscribeMessage({
      templateIds: ids.slice(0, MAX_TEMPLATES),
      complete: resolve
    });
  });
}

// 各动作对应的收件人：谁点按钮，就替他订阅他将收到的那几条
const SUBSCRIBE_KEYS = {
  // 打赏即委托，发起人等接单和打卡两条
  tip: ['taskAccepted', 'recordSubmitted'],
  // 打卡人等的是审核结果
  checkIn: ['recordApproved', 'recordRejected'],
  // 领养申请人等审核结论，以及之后的回访提醒
  adoption: ['adoptionApproved', 'adoptionRejected', 'followUpDue']
};

module.exports = {
  prefetchTemplates,
  requestSubscribe,
  SUBSCRIBE_KEYS
};
