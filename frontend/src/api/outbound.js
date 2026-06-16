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

export async function releaseAndLockOrder(id, warehouseIds) {
  const response = await http.post(`/outbound-orders/${id}/release-and-lock`, { warehouseIds })
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

export async function fetchQrInfo(outboundNo) {
  const response = await http.get(`/outbound-orders/no/${outboundNo}/qr-info`)
  return response.data
}

export async function lookupKanban(kanbanCode) {
  const response = await http.get('/outbound/kanban-lookup', { params: { kanbanCode } })
  return response.data
}

export async function pickWithOrder(payload) {
  const response = await http.post('/outbound/pick-with-order', payload)
  return response.data
}

export async function pickWithOrderForce(payload) {
  const response = await http.post('/outbound/pick-with-order/force', payload)
  return response.data
}

export async function pickNoOrder(payload) {
  const response = await http.post('/outbound/pick-no-order', payload)
  return response.data
}

// Lock management
export async function fetchLockOrders(params) {
  const response = await http.get('/locks', { params })
  return response.data
}

export async function fetchLockDetails(outboundOrderId) {
  const response = await http.get(`/locks/${outboundOrderId}/details`)
  return response.data
}

export async function unlockRecord(id, operator = 'web') {
  const response = await http.post(`/locks/${id}/unlock`, { operator })
  return response.data
}

export async function reassignOrder(id) {
  const response = await http.post(`/outbound-orders/${id}/reassign`)
  return response.data
}

export async function fetchForceLogs(params) {
  const response = await http.get('/locks/force-logs', { params })
  return response.data
}

export async function fetchForceCandidates(id) {
  const response = await http.get(`/outbound-orders/${id}/force-candidates`)
  return response.data
}
