import { http } from './http'

export async function scanInbound(kanbanCode) {
  const response = await http.post('/inventory/scan-inbound', { kanbanCode })
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
