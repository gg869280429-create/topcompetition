import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：附加 JWT Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一处理 {code, message, data}
request.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      if (!response.data.type.includes('json')) return response.data
      return response.data.text().then(text => {
        const error = JSON.parse(text)
        if (error.code === 401) handleUnauthorized()
        ElMessage.error(error.message || '下载失败')
        return Promise.reject(new Error(error.message || '下载失败'))
      })
    }
    const res = response.data
    if (res.code === 200) {
      return res
    }
    if (res.code === 401) {
      handleUnauthorized()
      return Promise.reject(new Error(res.message || '登录已过期'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  const userStore = useUserStore()
  userStore.clearAuth()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

export default request
