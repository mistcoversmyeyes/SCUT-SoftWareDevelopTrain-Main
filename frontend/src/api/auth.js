import { http } from './http'

export async function login(username, password) {
  const response = await http.post('/auth/login', { username, password })
  return response.data
}

export async function fetchMe() {
  const response = await http.get('/auth/me')
  return response.data
}
