<template>
  <el-container class="layout">
    <el-aside :width="sidebarWidth" class="layout-aside">
      <div class="logo">
        <el-icon :size="24" color="#fff"><Trophy /></el-icon>
        <span v-if="!isCollapse" class="logo-text">竞赛管理平台</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        background-color="#001529"
        text-color="rgba(255,255,255,0.68)"
        active-text-color="#fff"
        router
      >
        <el-menu-item v-for="m in visibleMenus" :key="m.path" :index="m.path" :class="{ 'studio-menu-item': m.path === '/studio' }">
          <el-icon><component :is="m.icon" /></el-icon>
          <template #title>{{ m.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="layout-body">
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <el-badge :value="unread" :hidden="unread === 0" :max="99" class="msg-badge">
            <el-icon class="msg-btn" @click="messageVisible = true"><Message /></el-icon>
          </el-badge>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info" role="button" tabindex="0" aria-label="打开个人菜单">
              <el-avatar :size="30">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="['STUDENT','TEACHER'].includes(userStore.role)" command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <MessageDrawer v-model="messageVisible" :unread="unread" @refresh="fetchUnread" />
    <AwardCelebration v-if="userStore.role === 'TEACHER'" />
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import MessageDrawer from '@/components/MessageDrawer.vue'
import AwardCelebration from '@/components/AwardCelebration.vue'
import { unreadCount } from '@/api/message'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isCollapse = ref(false)
const sidebarWidth = computed(() => (isCollapse.value ? '64px' : '220px'))
const activeMenu = computed(() => route.path)
const avatarText = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const menuConfig = [
  { path: '/team', title: '我的组队', icon: 'UserFilled', roles: ['STUDENT'] },
  { path: '/dashboard', title: '首页', icon: 'HomeFilled', roles: ['ADMIN', 'MAJOR_ADMIN', 'TEACHER', 'STUDENT'] },
  { path: '/competition', title: '竞赛管理', icon: 'Trophy', roles: ['ADMIN', 'MAJOR_ADMIN'] },
  { path: '/registration', title: '报名管理', icon: 'Tickets', roles: ['ADMIN', 'MAJOR_ADMIN', 'STUDENT'] },
  { path: '/guidance', title: '指导管理', icon: 'UserFilled', roles: ['ADMIN', 'MAJOR_ADMIN', 'TEACHER'] },
  { path: '/award', title: '获奖记录', icon: 'Medal', roles: ['ADMIN', 'MAJOR_ADMIN', 'TEACHER', 'STUDENT'] },
  { path: '/profile', title: '个人中心', icon: 'User', roles: ['STUDENT', 'TEACHER'] },
  { path: '/content', title: '新闻资讯', icon: 'Document', roles: ['ADMIN', 'MAJOR_ADMIN', 'TEACHER', 'STUDENT'] },
  { path: '/major', title: '专业管理', icon: 'School', roles: ['ADMIN'] },
  { path: '/student', title: '学生管理', icon: 'User', roles: ['ADMIN', 'MAJOR_ADMIN'] },
  { path: '/teacher', title: '教师库', icon: 'UserFilled', roles: ['ADMIN', 'MAJOR_ADMIN'] },
  { path: '/statistics', title: '统计中心', icon: 'Histogram', roles: ['ADMIN', 'MAJOR_ADMIN'] },
  { path: '/studio', title: '大师工作室', icon: 'OfficeBuilding', roles: ['ADMIN', 'MAJOR_ADMIN'] }
]
const visibleMenus = computed(() => {
  const menus = menuConfig.filter(m => m.roles.includes(userStore.role)).map(m =>
    m.path === '/content' ? { ...m, title: ['ADMIN', 'MAJOR_ADMIN'].includes(userStore.role) ? '新闻管理' : '竞赛预览' } : m)
  if (userStore.role === 'TEACHER') return ['/dashboard', '/guidance', '/award', '/content', '/profile'].map(path => menus.find(m => m.path === path))
  if (userStore.role !== 'STUDENT') return menus
  return ['/dashboard', '/content', '/team', '/registration', '/award', '/profile'].map(path => {
    const menu = menus.find(m => m.path === path)
    return { ...menu, title: path === '/award' ? '获奖管理' : path === '/profile' ? '个人信息' : menu.title }
  })
})

const unread = ref(0)
const messageVisible = ref(false)

async function fetchUnread() {
  const res = await unreadCount()
  unread.value = res.data
}

async function handleCommand(command) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    userStore.clearAuth()
    router.push('/login')
  }
}

onMounted(fetchUnread)
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  display: flex;
  flex-direction: column;
  background-color: #001529;
  overflow: hidden;
  transition: width 0.2s;
}

.logo {
  height: var(--header-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  overflow: hidden;
  white-space: nowrap;
}

.logo-text {
  font-size: var(--font-size-large);
  font-weight: 600;
}

.layout-aside :deep(.el-menu) {
  border-right: none;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.layout-aside :deep(.el-menu-item) {
  flex-shrink: 0;
}

.layout-aside :deep(.studio-menu-item) {
  margin-top: auto;
  position: sticky;
  bottom: 0;
  z-index: 1;
  background: #001529;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.layout-body {
  min-width: 0;
}

.layout-header {
  height: var(--header-height);
  background-color: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.msg-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--color-text);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--color-text);
}

.user-name {
  font-size: var(--font-size-base);
}

.layout-main {
  background-color: var(--color-bg);
  padding: var(--spacing-md);
}
</style>
