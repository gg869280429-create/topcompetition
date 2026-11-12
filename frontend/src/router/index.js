import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'profile', name: 'StudentProfile', component: () => import('@/views/profile/index.vue'), meta: { title: '个人信息管理', requiresAuth: true, roles: ['STUDENT'] } },
      { path: 'major', name: 'Major', component: () => import('@/views/major/index.vue'), meta: { title: '专业管理', requiresAuth: true, roles: ['ADMIN'] } },
      { path: 'student', name: 'Student', component: () => import('@/views/student/index.vue'), meta: { title: '学生管理', requiresAuth: true, roles: ['ADMIN', 'MAJOR_ADMIN'] } },
      { path: 'teacher', name: 'Teacher', component: () => import('@/views/teacher/index.vue'), meta: { title: '教师库', requiresAuth: true, roles: ['ADMIN', 'MAJOR_ADMIN'] } },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', requiresAuth: true }
      },
      {
        path: 'competition',
        name: 'Competition',
        component: () => import('@/views/competition/index.vue'),
        meta: { title: '竞赛管理', requiresAuth: true }
      },
      {
        path: 'registration',
        name: 'Registration',
        component: () => import('@/views/registration/index.vue'),
        meta: { title: '报名管理', requiresAuth: true }
      },
      {
        path: 'guidance',
        name: 'Guidance',
        component: () => import('@/views/guidance/index.vue'),
        meta: { title: '指导管理', requiresAuth: true }
      },
      {
        path: 'award',
        name: 'Award',
        component: () => import('@/views/award/index.vue'),
        meta: { title: '获奖管理', requiresAuth: true }
      },
      {
        path: 'content',
        name: 'Content',
        component: () => import('@/views/content/index.vue'),
        meta: { title: '新闻资讯', requiresAuth: true }
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/statistics/index.vue'),
        meta: { title: '统计中心', requiresAuth: true }
      }
    ]
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const role = JSON.parse(localStorage.getItem('userInfo') || 'null')?.role
  const title = to.name === 'Content' ? (['STUDENT', 'TEACHER'].includes(role) ? '竞赛预览' : '新闻管理') : to.meta.title
  document.title = title ? `${title} - 竞赛管理平台` : '竞赛管理平台'
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/dashboard')
  } else if (to.meta.roles && !to.meta.roles.includes(JSON.parse(localStorage.getItem('userInfo') || 'null')?.role)) {
    next('/403')
  } else {
    next()
  }
})

export default router
