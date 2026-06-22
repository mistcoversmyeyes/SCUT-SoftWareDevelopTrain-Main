import { http } from './http'

export async function importInventoryFlowHistory(file) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post('/ai-warning/imports/inventory-flow-history', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
  return response.data
}

export async function fetchInventoryFlowImportBatches() {
  const response = await http.get('/ai-warning/imports/inventory-flow-history/batches')
  return response.data
}

export async function fetchInventoryFlowImportRecords(params = {}) {
  const response = await http.get('/ai-warning/imports/inventory-flow-history/records', { params })
  return response.data
}
