export const ROLE_MAP = {
  0: { text: '普通用户', type: 'info' },
  1: { text: '志愿者', type: 'success' },
  2: { text: '领养人', type: 'warning' },
  3: { text: '管理员', type: 'danger' }
};

export const ADOPTION_STATUS_MAP = {
  0: { text: '待审核', type: 'info' },
  1: { text: '审核中', type: 'warning' },
  2: { text: '已通过', type: 'success' },
  3: { text: '已拒绝', type: 'danger' },
  4: { text: '已签约', type: 'success' },
  5: { text: '已完成', type: 'success' }
};

export const TASK_STATUS_MAP = {
  0: { text: '待接单', type: 'info' },
  1: { text: '已接单', type: 'warning' },
  2: { text: '已投喂待审核', type: 'primary' },
  3: { text: '已完成', type: 'success' },
  4: { text: '已取消', type: 'danger' }
};

export const RECORD_STATUS_MAP = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'danger' }
};

export const LIFE_STATUS_MAP = {
  0: { text: '流浪中', type: 'info' },
  1: { text: '投喂中', type: 'primary' },
  2: { text: '待领养', type: 'warning' },
  3: { text: '已领养', type: 'success' },
  4: { text: '领养回访中', type: 'success' }
};

export const HEALTH_STATUS_MAP = {
  0: '未检查',
  1: '已检查',
  2: '已绝育',
  3: '已疫苗',
  4: '已驱虫'
};

export const GENDER_MAP = { 0: '母', 1: '公', 2: '未知' };

export const HOUSING_MAP = { 0: '整租', 1: '合租', 2: '自有住房', 3: '宿舍' };

export const BIZ_TYPE_MAP = {
  REGISTER: '注册赠送',
  SIGNIN: '每日签到',
  REPORT: '上报猫咪',
  CHECKIN: '投喂打卡',
  FOLLOWUP: '领养回访',
  TIP: '打赏猫粮',
  RECHARGE: '充值到账',
  RECHARGE_REFUND: '充值退款'
};

export function dictText(map, key, fallback = '未知') {
  const item = map[key];
  if (!item) {
    return typeof item === 'string' ? item : fallback;
  }
  return typeof item === 'object' ? item.text : item;
}

export function dictType(map, key) {
  const item = map[key];
  return item && typeof item === 'object' ? item.type : 'info';
}

export function formatTime(value) {
  if (!value) {
    return '';
  }
  return String(value).slice(0, 19).replace('T', ' ');
}

/**
 * 后端回的是 /uploads/xxx 相对路径，同源 /api 前缀才是静态资源真实地址
 */
export function imageUrl(path) {
  if (!path) {
    return '';
  }
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  return path.startsWith('/api') ? path : `/api${path}`;
}
