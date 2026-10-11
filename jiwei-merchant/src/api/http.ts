import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.token = token
  return config
}, (e) => Promise.reject(e))

http.interceptors.response.use(
  (res) => { if (res.data.code === 0) { ElMessage.error(res.data.msg); return Promise.reject(new Error(res.data.msg)) } return res.data },
  (e) => { if (e.response?.status === 401) { ElMessage.error('登录过期'); window.location.href = '/login' } return Promise.reject(e) }
)

export default http
