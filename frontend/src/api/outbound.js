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

export async function scanOutbound(payload) {
  const response = await http.post('/outbound/scan', payload)
  return response.data
}

export async function startPicking(id) {
  const response = await http.post(`/outbound-orders/${id}/start-picking`)
  return response.data
}

export async function suspendPicking(id) {
  const response = await http.post(`/outbound-orders/${id}/suspend`)
  return response.data
}

export async function recommendPick(payload) {
  const response = await http.post('/outbound/recommend', payload)
  return response.data
}

export async function lookupKanban(kanbanCode) {
  const response = await http.get('/outbound/kanban-lookup', { params: { kanbanCode } })
  return response.data
}
