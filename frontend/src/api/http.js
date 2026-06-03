import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 5000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wms-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
