import { http } from './http'

export async function fetchMasterDataOptions() {
  const response = await http.get('/master-data/options')
  return response.data
}

export async function fetchSuppliers(params) {
  const response = await http.get('/suppliers', { params })
  return response.data
}

export async function createSupplier(payload) {
  const response = await http.post('/suppliers', payload)
  return response.data
}

export async function updateSupplier(id, payload) {
  const response = await http.put(`/suppliers/${id}`, payload)
  return response.data
}

export async function updateSupplierStatus(id, status) {
  const response = await http.put(`/suppliers/${id}/status`, { status })
  return response.data
}

export async function fetchMaterials(params) {
  const response = await http.get('/materials', { params })
  return response.data
}

export async function createMaterial(payload) {
  const response = await http.post('/materials', payload)
  return response.data
}

export async function updateMaterial(id, payload) {
  const response = await http.put(`/materials/${id}`, payload)
  return response.data
}

export async function updateMaterialStatus(id, status) {
  const response = await http.put(`/materials/${id}/status`, { status })
  return response.data
}

export async function fetchMaterialContainerTypes(materialId) {
  const response = await http.get(`/materials/${materialId}/container-types`)
  return response.data
}

export async function updateMaterialContainerTypes(materialId, containerTypeIds) {
  const response = await http.put(`/materials/${materialId}/container-types`, { containerTypeIds })
  return response.data
}

export async function fetchContainerTypes(params) {
  const response = await http.get('/container-types', { params })
  return response.data
}

export async function createContainerType(payload) {
  const response = await http.post('/container-types', payload)
  return response.data
}

export async function updateContainerType(id, payload) {
  const response = await http.put(`/container-types/${id}`, payload)
  return response.data
}

export async function updateContainerTypeStatus(id, status) {
  const response = await http.put(`/container-types/${id}/status`, { status })
  return response.data
}

export async function fetchWarehouses(params) {
  const response = await http.get('/warehouses', { params })
  return response.data
}

export async function createWarehouse(payload) {
  const response = await http.post('/warehouses', payload)
  return response.data
}

export async function updateWarehouse(id, payload) {
  const response = await http.put(`/warehouses/${id}`, payload)
  return response.data
}

export async function fetchStorageLocations(params) {
  const response = await http.get('/storage-locations', { params })
  return response.data
}

export async function createStorageLocation(payload) {
  const response = await http.post('/storage-locations', payload)
  return response.data
}

export async function updateStorageLocation(id, payload) {
  const response = await http.put(`/storage-locations/${id}`, payload)
  return response.data
}
