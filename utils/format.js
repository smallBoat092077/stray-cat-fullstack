const { ROLE_TEXT_MAP, TASK_STATUS_MAP } = require('./constants');

const VOLVERB_MAP = { 1: '已接单', 2: '已投喂待审核', 3: '已完成投喂' };

function prettyTime(iso) {
  return iso ? iso.slice(0, 16).replace('T', ' ') : '';
}

/**
 * 接单人身份行，例如「志愿者-张三-已接单，该志愿者已完成 5 次投喂」
 */
function volunteerLine(task) {
  if (!task || !task.volunteerId || !task.volunteerName) {
    return '';
  }
  const role = ROLE_TEXT_MAP[task.volunteerRole] || '志愿者';
  const verb = VOLVERB_MAP[task.status] || (TASK_STATUS_MAP[task.status] || {}).text || '接单中';
  const done = task.volunteerDoneCount || 0;
  return `${role}-${task.volunteerName}-${verb}，该${role}已完成 ${done} 次投喂`;
}

module.exports = { prettyTime, volunteerLine };
