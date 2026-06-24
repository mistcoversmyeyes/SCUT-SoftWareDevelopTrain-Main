import { http } from './http'

export async function fetchInventoryTagTrace(inventoryTagCode) {
  const response = await http.get(`/inventory-tags/${encodeURIComponent(inventoryTagCode)}/trace`)
  return response.data
}

export async function fetchInventoryTags(params) {
  const response = await http.get('/inventory/inventory-tags', { params })
  return response.data
}

export async function fetchInventoryHolds(params) {
  const response = await http.get('/holds', { params })
  return response.data
}

async function postHoldAction(inventoryTagId, action, payload) {
  const response = await http.post(`/inventory-tags/${inventoryTagId}/${action}`, payload)
  return response.data
}

export async function sealInventoryTag(inventoryTagId, payload) {
  return postHoldAction(inventoryTagId, 'seal', payload)
}

export async function unsealInventoryTag(inventoryTagId, payload) {
  return postHoldAction(inventoryTagId, 'unseal', payload)
}

export async function manualLockInventoryTag(inventoryTagId, payload) {
  return postHoldAction(inventoryTagId, 'manual-lock', payload)
}

export async function manualUnlockInventoryTag(inventoryTagId, payload) {
  return postHoldAction(inventoryTagId, 'manual-unlock', payload)
}
