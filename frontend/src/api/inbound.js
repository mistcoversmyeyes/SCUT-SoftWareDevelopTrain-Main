import { http } from './http'

export async function fetchInboundOrders(params) {
  const response = await http.get('/inbound-orders', { params })
  return response.data
}

export async function fetchInboundOrderById(id) {
  const response = await http.get(`/inbound-orders/${id}`)
  return response.data
}

export async function fetchInventoryTagsByOrderId(id) {
  const response = await http.get(`/inbound-orders/${id}/inventory-tags`)
  return response.data
}

export async function createInboundOrder(payload) {
  const response = await http.post('/inbound-orders', payload)
  return response.data
}

export async function batchCreateInboundOrders(payload) {
  const response = await http.post('/inbound-orders/batch', payload)
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

export async function printInventoryTags(id) {
  const response = await http.get(`/inbound-orders/${id}/inventory-tags/print`)
  return response.data
}
