import { http } from './http'

export async function fetchInboundOrders(params) {
  const response = await http.get('/inbound-orders', { params })
  return response.data
}

export async function fetchInboundOrder(id) {
  const response = await http.get(`/inbound-orders/${id}`)
  return response.data
}

export async function createInboundOrder(payload) {
  const response = await http.post('/inbound-orders', payload)
  return response.data
}

export async function updateInboundOrder(id, payload) {
  const response = await http.put(`/inbound-orders/${id}`, payload)
  return response.data
}

export async function releaseInboundOrder(id) {
  const response = await http.post(`/inbound-orders/${id}/release`)
  return response.data
}

export async function cancelInboundOrder(id) {
  const response = await http.post(`/inbound-orders/${id}/cancel`)
  return response.data
}

export async function printInboundOrder(id) {
  const response = await http.get(`/inbound-orders/${id}/print`)
  return response.data
}

export async function printKanbans(id) {
  const response = await http.get(`/inbound-orders/${id}/kanbans/print`)
  return response.data
}
