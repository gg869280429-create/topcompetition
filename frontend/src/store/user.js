import { defineStore } from 'pinia'
import { login as loginApi, getUserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    role: (state) => state.userInfo?.role || ''
  },
  actions: {
    async login(loginForm) {
      const res = await loginApi(loginForm)
      this.token = res.data.token
      this.userInfo = res.data.user
      localStorage.setItem('token', this.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    async fetchUserInfo() {
      const res = await getUserInfo()
      this.userInfo = res.data
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },
    clearAuth() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
