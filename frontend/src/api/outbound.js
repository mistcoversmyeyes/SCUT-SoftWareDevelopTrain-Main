import { http } from './http'

export async function fetchOutboundOrders(params) {
  const response = await http.get('/outbound-orders', { params })
  return response.data
}

export async function createOutboundOrder(payload) {
  const response = await http.post('/outbound-orders', payload)
  return response.data
}

export async function updateOutboundOrder(id, payload) {
  const response = await http.put(`/outbound-orders/${id}`, payload)
  return response.data
}

export async function releaseOutboundOrder(id) {
  const response = await http.post(`/outbound-orders/${id}/release`)
  return response.data
}

export async function cancelOutboundOrder(id) {
  const response = await http.post(`/outbound-orders/${id}/cancel`)
  return response.data
}

export async function fetchOutboundOrderById(id) {
  const response = await http.get(`/outbound-orders/${id}`)
  return response.data
}

export async function scanOutbound(kanbanCode) {
  const response = await http.post('/outbound/scan', { kanbanCode })
  return response.data
}
