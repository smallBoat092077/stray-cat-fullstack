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
      { path: 'tips', name: 'tips', component: () => import('../views/TipList.vue'), meta: { title: '打赏记录' } }
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
