import { http } from './http'

export async function scanInbound(kanbanCode, locationId) {
  const payload = { kanbanCode }
  if (locationId) {
    payload.locationId = locationId
  }
  const response = await http.post('/inventory/scan-inbound', payload)
  return response.data
}

export async function cancelKanban(kanbanId) {
  const response = await http.post(`/inventory/kanbans/${kanbanId}/cancel`)
  return response.data
}

export async function cancelKanbansBatch(ids) {
  const response = await http.post('/inventory/kanbans/cancel', { ids })
  return response.data
}

export async function lookupKanbanInbound(kanbanCode) {
  const response = await http.get('/outbound/kanban-lookup', { params: { kanbanCode } })
  return response.data
}

export async function fetchInventoryBalances(params) {
  const response = await http.get('/inventory/balances', { params })
  return response.data
}

export async function fetchInventoryMovements(params) {
  const response = await http.get('/inventory/movements', { params })
  return response.data
}
