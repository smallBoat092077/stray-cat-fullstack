const LIFE_STATUS_MAP = {
  0: { text: '流浪中', class: 'warning' },
  1: { text: '投喂中', class: 'info' },
  2: { text: '待领养', class: 'success' },
  3: { text: '已领养', class: 'success' },
  4: { text: '回访中', class: 'info' }
};

const GENDER_MAP = { 0: '母', 1: '公', 2: '未知' };

const ROLE_TEXT_MAP = { 0: '爱心用户', 1: '志愿者', 3: '管理员' };

const HEALTH_STATUS_MAP = {
  0: { text: '未检查', class: 'warning' },
  1: { text: '健康', class: 'success' },
  2: { text: '生病', class: 'danger' },
  3: { text: '已绝育', class: 'info' }
};

const TASK_TYPE_MAP = { 0: '日常投喂', 1: '紧急投喂', 2: '医疗救助' };

const TASK_STATUS_MAP = {
  0: { text: '待接单', class: 'warning' },
  1: { text: '已接单', class: 'info' },
  2: { text: '已投喂待审核', class: 'info' },
  3: { text: '已完成', class: 'success' },
  4: { text: '已取消', class: 'danger' }
};

const ADOPTION_STATUS_MAP = {
  0: { text: '待审核', class: 'warning' },
  1: { text: '审核中', class: 'info' },
  2: { text: '已通过', class: 'success' },
  3: { text: '已拒绝', class: 'danger' },
  4: { text: '已签约', class: 'success' },
  5: { text: '已完成', class: 'success' }
};

const RECORD_STATUS_MAP = {
  0: { text: '待审核', class: 'warning' },
  1: { text: '已通过', class: 'success' },
  2: { text: '已拒绝', class: 'danger' }
};

const COIN_BIZ_MAP = {
  REGISTER: '注册赠送',
  SIGNIN: '每日签到',
  REPORT: '上报猫咪',
  CHECKIN: '投喂打卡',
  FOLLOWUP: '领养回访',
  VOLUNTEER_LEVEL: '志愿者升级',
  TIP: '送猫粮',
  RECHARGE: '购买猫粮',
  RECHARGE_REFUND: '退款回收'
};

const CO_ADOPTION_STATUS_MAP = {
  0: { text: '待确认', class: 'warning' },
  1: { text: '已同意', class: 'success' },
  2: { text: '已拒绝', class: 'danger' }
};

// 爱猫搬家：转让信息状态 0待审 1在架 2已转让 3下架 4驳回
const TRANSFER_STATUS_MAP = {
  0: { text: '待审', class: 'warning' },
  1: { text: '在架', class: 'success' },
  2: { text: '已转让', class: 'info' },
  3: { text: '已下架', class: 'info' },
  4: { text: '已驳回', class: 'danger' }
};

// 爱猫搬家：接猫申请状态 0待处理 1已联系 2不合适
const TRANSFER_APPLY_STATUS_MAP = {
  0: { text: '待处理', class: 'warning' },
  1: { text: '已联系', class: 'success' },
  2: { text: '不合适', class: 'info' }
};

// tabBar 页白名单：唯一口径，统一带前导斜杠。
// tab 页只能用 switchTab 打开，且不接受 query；改动 tabBar 时只需改这里。
const TAB_PAGES = [
  '/pages/home/index',
  '/pages/index/index',
  '/pages/cat/list',
  '/pages/feeding/list',
  '/pages/profile/index'
];

module.exports = {
  LIFE_STATUS_MAP,
  GENDER_MAP,
  ROLE_TEXT_MAP,
  HEALTH_STATUS_MAP,
  TASK_TYPE_MAP,
  TASK_STATUS_MAP,
  ADOPTION_STATUS_MAP,
  RECORD_STATUS_MAP,
  COIN_BIZ_MAP,
  CO_ADOPTION_STATUS_MAP,
  TRANSFER_STATUS_MAP,
  TRANSFER_APPLY_STATUS_MAP,
  TAB_PAGES
};
