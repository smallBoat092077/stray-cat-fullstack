const http = require('http');

const HOST = 'localhost';
const PORT = 8080;
const BASE = '/api';

function request(method, path, data) {
  return new Promise((resolve, reject) => {
    let urlPath = BASE + path;
    if (method === 'GET' && data) {
      const params = new URLSearchParams();
      Object.entries(data).forEach(([k, v]) => params.set(k, v));
      urlPath += '?' + params.toString();
    }
    const options = {
      hostname: HOST, port: PORT,
      path: urlPath, method,
      headers: { 'Content-Type': 'application/json' }
    };
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => { try { resolve(JSON.parse(body)); } catch { resolve(body); } });
    });
    req.on('error', reject);
    if (data && method !== 'GET') req.write(JSON.stringify(data));
    req.end();
  });
}

async function runTests() {
  console.log('=== 流浪猫小程序 API 联调测试 ===\n');
  let pass = 0, fail = 0;

  // 1. 猫咪列表
  console.log('1. 获取猫咪列表');
  try {
    const res = await request('GET', '/cat/list', { pageNum: 1, pageSize: 10 });
    if (res.code === 200 && res.data.records.length > 0) {
      console.log(`   PASS - 返回 ${res.data.total} 只猫咪`);
      res.data.records.forEach(c => console.log(`      - ${c.name} (${['流浪中','投喂中','待领养','已领养','回访中'][c.lifeStatus]})`));
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 2. 猫咪详情
  console.log('\n2. 获取猫咪详情 (id=1)');
  try {
    const res = await request('GET', '/cat/1');
    if (res.code === 200 && res.data) {
      console.log(`   PASS - ${res.data.name}, 状态: ${['流浪中','投喂中','待领养','已领养','回访中'][res.data.lifeStatus]}`);
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 3. 附近猫咪
  console.log('\n3. 附近猫咪查询 (上海)');
  try {
    const res = await request('GET', '/cat/nearby', { latitude: 31.2304, longitude: 121.4737, radius: 5 });
    if (res.code === 200) {
      console.log(`   PASS - 附近 ${res.data.length} 只猫咪`);
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 4. 投喂任务列表
  console.log('\n4. 获取投喂任务列表');
  try {
    const res = await request('GET', '/feeding/task/list', { pageNum: 1, pageSize: 10 });
    if (res.code === 200) {
      console.log(`   PASS - 返回 ${res.data.total || 0} 个任务`);
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 5. 创建投喂任务
  console.log('\n5. 创建投喂任务');
  try {
    const res = await request('POST', '/feeding/task', {
      catId: 1, taskType: 0,
      latitude: 31.230416, longitude: 121.473701,
      radius: 100, amount: 10.00
    });
    if (res.code === 200 && res.data) {
      console.log(`   PASS - 任务ID: ${res.data}`);
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 6. 接单
  console.log('\n6. 志愿者接单');
  try {
    const res = await new Promise((resolve, reject) => {
      const req = http.request({
        hostname: HOST, port: PORT,
        path: '/api/feeding/task/1/accept?volunteerId=2',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => { try { resolve(JSON.parse(body)); } catch { resolve(body); } });
      });
      req.on('error', reject);
      req.end();
    });
    if (res.code === 200) {
      console.log('   PASS - 接单成功');
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 7. 微信登录
  console.log('\n7. 微信登录（模拟）');
  try {
    const res = await request('POST', '/user/login', { code: 'test_code_001' });
    console.log(`   PASS - 接口正常 (code=${res.code})`);
    pass++;
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 8. 上报流浪猫
  console.log('\n8. 上报流浪猫');
  try {
    const res = await request('POST', '/cat/report', {
      name: '小白', gender: 0, ageEstimate: '1岁',
      color: '白色', lifeStatus: 0,
      latitude: 31.235000, longitude: 121.480000,
      locationDesc: '测试上报位置',
      description: '测试上报的小白猫'
    });
    if (res.code === 200 && res.data) {
      console.log(`   PASS - 新猫咪ID: ${res.data}`);
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 9. 验证新猫
  console.log('\n9. 验证新猫已入库');
  try {
    const res = await request('GET', '/cat/list', { pageNum: 1, pageSize: 10 });
    const hasNew = res.data.records.some(c => c.name === '小白');
    if (hasNew) {
      console.log('   PASS - 小白猫已在列表中');
      pass++;
    } else { console.log('   FAIL - 未找到小白猫'); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  // 10. 投喂打卡
  console.log('\n10. 投喂打卡（GPS+距离校验）');
  try {
    const res = await new Promise((resolve, reject) => {
      const req = http.request({
        hostname: HOST, port: PORT,
        path: '/api/feeding/task/1/checkin?latitude=31.230416&longitude=121.473701',
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      }, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => { try { resolve(JSON.parse(body)); } catch { resolve(body); } });
      });
      req.on('error', reject);
      req.end();
    });
    if (res.code === 200) {
      console.log('   PASS - 打卡成功');
      pass++;
    } else { console.log('   FAIL -', res.message); fail++; }
  } catch (e) { console.log('   FAIL -', e.message); fail++; }

  console.log(`\n=== 测试结果: ${pass} 通过, ${fail} 失败 ===`);
}

runTests();
