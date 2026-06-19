import { defineStore } from 'pinia'
import { fetchMe, login as loginApi } from '../api/auth'

function readStoredUser() {
  const raw = localStorage.getItem('wms-user')
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw)
  } catch {
    localStorage.removeItem('wms-user')
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('wms-token') || '',
    verifiedToken: '',
    user: readStoredUser()
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isSessionVerified: (state) => Boolean(state.token && state.verifiedToken === state.token)
  },
  actions: {
    async login(username, password) {
      const data = await loginApi(username, password)
      this.token = data.token
      this.verifiedToken = data.token
      this.user = {
        username: data.username,
        displayName: data.displayName
      }
      localStorage.setItem('wms-token', data.token)
      localStorage.setItem('wms-user', JSON.stringify(this.user))
    },
    async loadCurrentUser() {
      const user = await fetchMe()
      this.user = user
      this.verifiedToken = this.token
      localStorage.setItem('wms-user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.verifiedToken = ''
      this.user = null
      localStorage.removeItem('wms-token')
      localStorage.removeItem('wms-user')
    }
  }
})
