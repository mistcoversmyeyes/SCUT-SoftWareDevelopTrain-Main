import { http } from './http'

export async function fetchDashboardStats() {
  const response = await http.get('/dashboard/stats')
  return response.data
}
