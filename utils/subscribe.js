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
      setTemplateMap(data);
    })
    .catch(() => {
      // 拉不到就这次不弹授权，不影响页面
    })
    .finally(() => {
      fetching = false;
    });
}

/**
 * 页面自己拉到的模板表也可以喂进来（消息中心每次刷新都会拉），
 * 避免 prefetch 还没回来时点按钮弹不出窗。
 */
function setTemplateMap(map) {
  if (map && Object.keys(map).length) {
    templateMap = map;
  }
}

/**
 * 必须在点击事件里同步调用：微信要求订阅弹窗由用户手势触发，
 * 所以模板 ID 提前 prefetch 到内存，这里不再 await 网络。
 * 一次授权 = 一条推送额度。返回结果里区分 accept/reject/ban，
 * 调用方据此提示，不能默认用户点了允许。
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
    return Promise.resolve({ skipped: true, accept: 0, reject: 0, ban: 0 });
  }
  return new Promise((resolve) => {
    wx.requestSubscribeMessage({
      // 微信参数名是 tmplIds（不是 templateIds），拼错会 fail parameter error 且不弹窗
      tmplIds: ids.slice(0, MAX_TEMPLATES),
      success: (res) => {
        let accept = 0;
        let reject = 0;
        let ban = 0;
        ids.forEach((id) => {
          if (res[id] === 'accept') {
            accept += 1;
          } else if (res[id] === 'ban') {
            // 用户此前对该模板点过「总是保持以上选择·拒绝」，微信不再弹窗
            ban += 1;
          } else {
            reject += 1;
          }
        });
        resolve({ skipped: false, accept, reject, ban });
      },
      fail: (err) => {
        // 常见 fail：不在手势上下文里调用。绝不能静默吞掉
        console.warn('requestSubscribeMessage fail', err && err.errMsg);
        resolve({ skipped: false, accept: 0, reject: 0, ban: 0, failMsg: err && err.errMsg });
      }
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
  adoption: ['adoptionApproved', 'adoptionRejected', 'followUpDue'],
  // 邀请共同领养的人等回应；被邀请的人等邀请（邀请是别人发起的，
  // 只能在「消息中心 → 微信通知」里提前把额度攒上）
  coAdoptionInvite: ['coAdoptionInvite'],
  coAdoptionResult: ['coAdoptionResult'],
  // 管理员待办：这两个模板后台留空时不生效，退化成站内消息
  admin: ['adoptionNew', 'recordPending']
};

// 「开启微信通知」的授权分组，每组 ≤3 个模板 ID
const ALL_GROUPS = [
  ['taskAccepted', 'recordSubmitted', 'recordApproved'],
  ['adoptionApproved', 'adoptionRejected', 'followUpDue'],
  ['coAdoptionInvite', 'coAdoptionResult', 'recordPending']
];

// 微信限制：一次手势只允许发起一个授权弹窗——同步连发第二组报
// "last call has not ended"，await 上一组关闭后再弹又脱离手势上下文。
// 所以「开启微信通知」改成每次点击弹一批，点完 3 批才算全部授权。
let groupCursor = 0;

/**
 * 每次点击请求下一组。返回 { batch, total, done, accept, reject, ban, skipped, failMsg }。
 */
function requestNextGroup() {
  if (groupCursor >= ALL_GROUPS.length) {
    return Promise.resolve({
      batch: ALL_GROUPS.length, total: ALL_GROUPS.length, done: true,
      accept: 0, reject: 0, ban: 0, skipped: 0
    });
  }
  const batch = groupCursor + 1;
  groupCursor += 1;
  return requestSubscribe(ALL_GROUPS[batch - 1]).then((r) => ({
    ...r,
    batch,
    total: ALL_GROUPS.length,
    done: groupCursor >= ALL_GROUPS.length
  }));
}

/** 三批走完后从头再来（用户想复查时） */
function resetGroupCursor() {
  groupCursor = 0;
}

/**
 * 「开启微信通知」：每次调用弹一批（微信一次手势只允许一个授权窗），
 * 点满 3 批完成全部授权。被邀请共同领养、被审核这些事由别人触发，
 * 用户没法在事发时补授权，只能提前把额度攒好。
 */
module.exports = {
  prefetchTemplates,
  setTemplateMap,
  requestSubscribe,
  requestNextGroup,
  resetGroupCursor,
  SUBSCRIBE_KEYS
};
