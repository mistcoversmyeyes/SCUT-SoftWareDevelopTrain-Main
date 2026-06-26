export function boxBreakdown(totalQty, capacityQty) {
  const total = Number(totalQty) || 0
  const capacity = Number(capacityQty) || 0
  if (total <= 0 || capacity <= 0) {
    return { boxCount: 0, remainder: 0 }
  }

  return {
    boxCount: Math.floor(total / capacity),
    remainder: total % capacity
  }
}

export function isCompleteBatchInboundLine(line) {
  return Boolean(
    line?.supplierId &&
    line?.materialId &&
    line?.containerTypeId &&
    Number(line?.plannedQty) > 0 &&
    line?.targetWarehouseId &&
    line?.targetLocationId
  )
}

export function groupLinesBySupplier(lines) {
  return lines.reduce((groups, line) => {
    const supplierId = line.supplierId
    if (!groups.has(supplierId)) {
      groups.set(supplierId, [])
    }
    groups.get(supplierId).push(line)
    return groups
  }, new Map())
}

export function filterMaterialsBySupplier(materials, supplierId) {
  if (!supplierId) {
    return []
  }

  return materials.filter(material => material.supplierId === supplierId)
}
