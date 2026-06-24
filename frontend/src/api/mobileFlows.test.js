import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { scanInbound } from './inventory'
import { fetchInventoryTagTrace } from './inventoryTag'
import { fetchQrInfo, lookupInventoryTag, pickNoOrder, pickWithOrder } from './outbound'

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

    await scanInbound('IT:v1:IN-1:1:1', 8)

    expect(http.post).toHaveBeenCalledWith('/inventory/scan-inbound', {
      inventoryTagCode: 'IT:v1:IN-1:1:1',
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

    await pickWithOrder({ inventoryTagCode: 'IT:v1:IN-2:1:1', qty: 5, outboundOrderId: 3 })
    await pickNoOrder({ inventoryTagCode: 'IT:v1:IN-3:1:1', qty: 2 })

    expect(http.post).toHaveBeenNthCalledWith(1, '/outbound/pick-with-order', {
      inventoryTagCode: 'IT:v1:IN-2:1:1',
      qty: 5,
      outboundOrderId: 3
    })
    expect(http.post).toHaveBeenNthCalledWith(2, '/outbound/pick-no-order', {
      inventoryTagCode: 'IT:v1:IN-3:1:1',
      qty: 2
    })
  })

  it('loads mobile inventory tag preview and trace', async () => {
    http.get.mockResolvedValue({ data: { inventoryTagCode: 'IT:v1:IN-4:1:1' } })

    await lookupInventoryTag('IT:v1:IN-4:1:1')
    await fetchInventoryTagTrace('IT:v1:IN-4:1:1')

    expect(http.get).toHaveBeenNthCalledWith(1, '/inventory-tags/lookup', {
      params: { inventoryTagCode: 'IT:v1:IN-4:1:1' }
    })
    expect(http.get).toHaveBeenNthCalledWith(2, '/inventory-tags/IT%3Av1%3AIN-4%3A1%3A1/trace')
  })
})
