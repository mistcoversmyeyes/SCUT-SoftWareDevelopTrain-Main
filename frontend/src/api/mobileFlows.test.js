import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { scanInbound } from './inventory'
import { fetchKanbanTrace } from './kanban'
import { fetchQrInfo, lookupKanban, pickNoOrder, pickWithOrder } from './outbound'

vi.mock('./http', () => ({
  http: {
    get: vi.fn(),
    post: vi.fn()
  }
}))

describe('mobile-related api wrappers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submits inbound scan with optional location', async () => {
    http.post.mockResolvedValue({ data: { ok: true } })

    await scanInbound('KB:1', 8)

    expect(http.post).toHaveBeenCalledWith('/inventory/scan-inbound', {
      kanbanCode: 'KB:1',
      locationId: 8
    })
  })

  it('loads outbound order info by outbound number', async () => {
    http.get.mockResolvedValue({ data: { order: { id: 1 } } })

    await fetchQrInfo('OUT-1')

    expect(http.get).toHaveBeenCalledWith('/outbound-orders/no/OUT-1/qr-info')
  })

  it('submits with-order and no-order outbound picks', async () => {
    http.post.mockResolvedValue({ data: { ok: true } })

    await pickWithOrder({ kanbanCode: 'KB:2', qty: 5, outboundOrderId: 3 })
    await pickNoOrder({ kanbanCode: 'KB:3', qty: 2 })

    expect(http.post).toHaveBeenNthCalledWith(1, '/outbound/pick-with-order', {
      kanbanCode: 'KB:2',
      qty: 5,
      outboundOrderId: 3
    })
    expect(http.post).toHaveBeenNthCalledWith(2, '/outbound/pick-no-order', {
      kanbanCode: 'KB:3',
      qty: 2
    })
  })

  it('loads mobile kanban preview and trace', async () => {
    http.get.mockResolvedValue({ data: { kanbanCode: 'KB:4' } })

    await lookupKanban('KB:4')
    await fetchKanbanTrace('KB:4')

    expect(http.get).toHaveBeenNthCalledWith(1, '/outbound/kanban-lookup', {
      params: { kanbanCode: 'KB:4' }
    })
    expect(http.get).toHaveBeenNthCalledWith(2, '/kanbans/KB%3A4/trace')
  })
})
