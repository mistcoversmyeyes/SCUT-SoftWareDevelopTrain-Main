export const pendingOutboundStatuses = ['DRAFT', 'RELEASED', 'LOCKED', 'PICKING', 'PARTIAL_SHIPPED']

export function isRecommendedInventoryTag(recommendation, outboundOrderLineId, inventoryTagCode) {
  const line = recommendation?.lines?.find((item) => item.outboundOrderLineId === outboundOrderLineId)
  return Boolean(line?.recommendations?.some((item) => item.inventoryTagCode === inventoryTagCode))
}

export function findRecommendedLineId(recommendation, inventoryTagCode) {
  const line = recommendation?.lines?.find((item) =>
    item.recommendations?.some((recommend) => recommend.inventoryTagCode === inventoryTagCode)
  )
  return line?.outboundOrderLineId
}

export function pendingRecommendationLines(recommendation) {
  return (recommendation?.lines || []).filter((line) => Number(line.neededQty || 0) > 0)
}
