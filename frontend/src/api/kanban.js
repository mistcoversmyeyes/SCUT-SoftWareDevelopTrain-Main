import { http } from './http'

export async function fetchKanbanTrace(kanbanCode) {
  const response = await http.get(`/kanbans/${encodeURIComponent(kanbanCode)}/trace`)
  return response.data
}

export async function fetchKanbanList(params) {
  const response = await http.get('/inventory/kanbans', { params })
  return response.data
}

export async function fetchInventoryHolds(params) {
  const response = await http.get('/holds', { params })
  return response.data
}

async function postHoldAction(kanbanId, action, payload) {
  const response = await http.post(`/kanbans/${kanbanId}/${action}`, payload)
  return response.data
}

export async function sealKanban(kanbanId, payload) {
  return postHoldAction(kanbanId, 'seal', payload)
}

export async function unsealKanban(kanbanId, payload) {
  return postHoldAction(kanbanId, 'unseal', payload)
}

export async function manualLockKanban(kanbanId, payload) {
  return postHoldAction(kanbanId, 'manual-lock', payload)
}

export async function manualUnlockKanban(kanbanId, payload) {
  return postHoldAction(kanbanId, 'manual-unlock', payload)
}
