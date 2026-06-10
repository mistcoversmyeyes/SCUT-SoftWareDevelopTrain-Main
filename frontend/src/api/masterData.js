import { http } from './http'

export async function fetchMasterDataOptions() {
  const response = await http.get('/master-data/options')
  return response.data
}
