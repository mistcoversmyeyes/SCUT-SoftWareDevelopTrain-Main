import { describe, expect, it } from 'vitest'
import { isRecommendedInventoryTag, pendingOutboundStatuses } from './outboundRecommendation'

describe('outbound recommendation helpers', () => {
  const recommendation = {
    lines: [
      {
        outboundOrderLineId: 10,
        recommendations: [
          { inventoryTagCode: 'IT:v1:IN-1:1:1' },
          { inventoryTagCode: 'IT:v1:IN-1:1:2' }
        ]
      }
    ]
  }

  it('detects recommended tags per outbound line', () => {
    expect(isRecommendedInventoryTag(recommendation, 10, 'IT:v1:IN-1:1:1')).toBe(true)
    expect(isRecommendedInventoryTag(recommendation, 10, 'IT:v1:IN-2:1:1')).toBe(false)
  })

  it('keeps only pending mobile outbound statuses', () => {
    expect(pendingOutboundStatuses).toEqual(['DRAFT', 'RELEASED', 'LOCKED', 'PICKING', 'PARTIAL_SHIPPED'])
  })
})
