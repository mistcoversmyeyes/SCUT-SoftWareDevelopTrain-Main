import { http } from './http'

export async function scanInbound(inventoryTagCode, locationId) {
  const payload = { inventoryTagCode }
  if (locationId) {
    payload.locationId = locationId
  }
  const response = await http.post('/inventory/scan-inbound', payload)
  return response.data
}

export async function cancelInventoryTag(inventoryTagId) {
  const response = await http.post(`/inventory/inventory-tags/${inventoryTagId}/cancel`)
  return response.data
}

export async function cancelInventoryTagsBatch(ids) {
  const response = await http.post('/inventory/inventory-tags/cancel', { ids })
  return response.data
}

export async function lookupInventoryTagInbound(inventoryTagCode) {
  const response = await http.get('/inventory/inventory-tag-lookup', { params: { inventoryTagCode } })
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
