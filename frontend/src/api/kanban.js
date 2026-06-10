import { http } from './http'

export async function fetchKanbanTrace(kanbanCode) {
  const response = await http.get(`/kanbans/${encodeURIComponent(kanbanCode)}/trace`)
  return response.data
}
