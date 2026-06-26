import { describe, expect, it } from 'vitest'
import { boxBreakdown, groupLinesBySupplier, isCompleteBatchInboundLine } from './batchInbound'

describe('batch inbound helpers', () => {
  it('computes full boxes and remainder from total quantity', () => {
    expect(boxBreakdown(230, 100)).toEqual({ boxCount: 2, remainder: 30 })
  })

  it('keeps duplicate supplier and material lines as separate records', () => {
    const lines = [
      { tempId: 'a', supplierId: 1, materialId: 1, plannedQty: 230 },
      { tempId: 'b', supplierId: 1, materialId: 1, plannedQty: 230 }
    ]

    expect(groupLinesBySupplier(lines).get(1)).toHaveLength(2)
  })

  it('requires warehouse and location before final submit', () => {
    expect(isCompleteBatchInboundLine({
      supplierId: 1,
      materialId: 1,
      containerTypeId: 1,
      plannedQty: 230,
      targetWarehouseId: 1,
      targetLocationId: 1
    })).toBe(true)

    expect(isCompleteBatchInboundLine({
      supplierId: 1,
      materialId: 1,
      containerTypeId: 1,
      plannedQty: 230,
      targetWarehouseId: 1
    })).toBe(false)
  })
})
