import { createRouter, createWebHashHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '概览' } },
      { path: 'adoption', name: 'adoption', component: () => import('../views/AdoptionList.vue'), meta: { title: '领养申请审核' } },
      { path: 'records', name: 'records', component: () => import('../views/RecordList.vue'), meta: { title: '投喂打卡审核' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/TaskList.vue'), meta: { title: '投喂任务' } },
      { path: 'cats', name: 'cats', component: () => import('../views/CatList.vue'), meta: { title: '猫咪管理' } },
      { path: 'users', name: 'users', component: () => import('../views/UserList.vue'), meta: { title: '用户与角色' } },
      { path: 'ledger', name: 'ledger', component: () => import('../views/LedgerList.vue'), meta: { title: '猫粮流水' } },
      { path: 'tips', name: 'tips', component: () => import('../views/TipList.vue'), meta: { title: '打赏记录' } },
      { path: 'posters', name: 'posters', component: () => import('../views/PosterList.vue'), meta: { title: '海报管理' } },
      { path: 'campaigns', name: 'campaigns', component: () => import('../views/CampaignList.vue'), meta: { title: '活动管理' } },
      { path: 'feedback', name: 'feedback', component: () => import('../views/FeedbackList.vue'), meta: { title: '用户反馈' } },
      { path: 'home-content', name: 'home-content', component: () => import('../views/HomeContentList.vue'), meta: { title: '首页内容' } },
      { path: 'wiki-manage', name: 'wiki-manage', component: () => import('../views/WikiManage.vue'), meta: { title: '猫咪百科' } },
      { path: 'lost-cat-review', name: 'lost-cat-review', component: () => import('../views/LostCatReview.vue'), meta: { title: '寻猫审核' } },
      { path: 'transfer-review', name: 'transfer-review', component: () => import('../views/TransferReview.vue'), meta: { title: '爱猫搬家审核' } },
      { path: 'lost-cat-clue-review', name: 'lost-cat-clue-review', component: () => import('../views/LostCatClueReview.vue'), meta: { title: '线索审核' } },
      { path: 'recognizer-sample', name: 'recognizer-sample', component: () => import('../views/RecognizerSample.vue'), meta: { title: '花纹样本' } },
      { path: 'region-data', name: 'region-data', component: () => import('../views/RegionData.vue'), meta: { title: '区域数据' } },
      { path: 'about-config', name: 'about-config', component: () => import('../views/AboutConfig.vue'), meta: { title: '关于我们' } },
      { path: 'operation-log', name: 'OperationLog', component: () => import('../views/OperationLog.vue'), meta: { title: '操作日志' } },
      { path: 'reset-requests', name: 'ResetRequests', component: () => import('../views/PasswordResetRequest.vue'), meta: { title: '密码找回申请' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    return auth.logged && auth.isAdmin ? '/dashboard' : true;
  }
  if (!auth.logged) {
    return '/login';
  }
  // 只有管理员能进后台，志愿者/普通用户直接退回登录页，免得逐页 1007
  if (!auth.isAdmin) {
    auth.logout();
    return '/login';
  }
  return true;
});

export default router;
