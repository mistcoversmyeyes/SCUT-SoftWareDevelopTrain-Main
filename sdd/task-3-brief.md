# Task 3: Layer 2 — API & Service Layer

## Global Constraints
- D02: Material:Container = 1:N via material_container_type middle table
- D14: Kanban cancel — PRINTED kanbans only, refresh line plannedQty + order status
- D20: Material-container association via PUT (full replace) / GET endpoints
- D23: Material with no container types → block on inbound order creation
- D24: No NULL fallbacks in service code.

## Prerequisites
- Layer 0 + 1 DONE. All entities, DDL, and core logic in place.
- `material_container_type` table exists.
- `MaterialContainerType` entity exists at `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerType.java`.
- `container_type` table has seed data: id=1 (KLT-4320), id=2 (EP-1200).
- Test data in tests creates InboundOrderRequest.Line with `containerTypeId`.

## Subtask 1: MaterialContainerTypeMapper

New file: `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerTypeMapper.java`

```java
package com.scut.wms.masterdata;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaterialContainerTypeMapper extends BaseMapper<MaterialContainerType> {
}
```

## Subtask 2: Material-Container Association — GET endpoint

Add to MasterDataController:
```java
@GetMapping("/materials/{id}/container-types")
public List<MaterialContainerTypeResponse> getMaterialContainerTypes(@PathVariable Long id) {
    return service.getMaterialContainerTypes(id);
}
```

Add to MasterDataService:
```java
public List<MaterialContainerTypeResponse> getMaterialContainerTypes(Long materialId) {
    requireMaterial(materialId); // verify material exists
    return materialContainerTypeMapper.selectList(
            Wrappers.<MaterialContainerType>lambdaQuery()
                .eq(MaterialContainerType::getMaterialId, materialId))
        .stream()
        .map(mct -> {
            ContainerType ct = containerTypeMapper.selectById(mct.getContainerTypeId());
            return new MaterialContainerTypeResponse(
                mct.getContainerTypeId(),
                ct != null ? ct.getContainerCode() : "",
                ct != null ? ct.getContainerName() : "",
                ct != null ? ct.getCapacityQty() : BigDecimal.ZERO,
                mct.getIsDefault() != null && mct.getIsDefault() == 1
            );
        })
        .toList();
}
```

Inject `MaterialContainerTypeMapper` into MasterDataService constructor.

New record: `MaterialContainerTypeResponse` (same package):
```java
public record MaterialContainerTypeResponse(
    Long id, String containerCode, String containerName, BigDecimal capacityQty, boolean isDefault
) {}
```

## Subtask 3: Material-Container Association — PUT endpoint

Add to MasterDataController:
```java
@PutMapping("/materials/{id}/container-types")
public void updateMaterialContainerTypes(@PathVariable Long id, @RequestBody MaterialContainerTypeUpdateRequest request) {
    service.updateMaterialContainerTypes(id, request);
}
```

New record: `MaterialContainerTypeUpdateRequest`:
```java
package com.scut.wms.masterdata;

import java.util.List;

public record MaterialContainerTypeUpdateRequest(List<Long> containerTypeIds) {}
```

Add to MasterDataService:
```java
@Transactional
public void updateMaterialContainerTypes(Long materialId, MaterialContainerTypeUpdateRequest request) {
    requireMaterial(materialId);
    // Validate all IDs exist
    for (Long ctId : request.containerTypeIds()) {
        if (containerTypeMapper.selectById(ctId) == null) {
            throw new BusinessException("容器类型不存在: " + ctId);
        }
    }
    // Full replace: delete old, insert new
    materialContainerTypeMapper.delete(Wrappers.<MaterialContainerType>lambdaQuery()
            .eq(MaterialContainerType::getMaterialId, materialId));
    for (Long ctId : request.containerTypeIds()) {
        MaterialContainerType mct = new MaterialContainerType();
        mct.setMaterialId(materialId);
        mct.setContainerTypeId(ctId);
        mct.setIsDefault(0);
        materialContainerTypeMapper.insert(mct);
    }
}
```

If request.containerTypeIds() is null or empty, delete all associations (leave material with none → D23 will block it from use in inbound orders).

## Subtask 4: Container type validation on inbound order create/update

In InboundOrderService.validateRequest(), add after the existing warehouse/location check:
```java
// D23: ensure material has the selected container type configured
var mctCount = materialContainerTypeMapper.selectCount(Wrappers.<MaterialContainerType>lambdaQuery()
        .eq(MaterialContainerType::getMaterialId, line.materialId())
        .eq(MaterialContainerType::getContainerTypeId, line.containerTypeId()));
if (mctCount == 0) {
    throw new BusinessException("所选容器类型不适用于该物料");
}
```

Add `MaterialContainerTypeMapper` to InboundOrderService constructor injection.

## Subtask 5: Kanban Cancel — single endpoint

Add to InventoryController:
```java
@PostMapping("/kanbans/{kanbanId}/cancel")
public Map<String, Object> cancelKanban(@PathVariable Long kanbanId) {
    return inventoryService.cancelKanban(kanbanId);
}
```

Add to InventoryService:
```java
@Transactional
public Map<String, Object> cancelKanban(Long kanbanId) {
    KanbanBoard kanban = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
    if (kanban == null) throw new BusinessException(HttpStatus.NOT_FOUND, "看板不存在");
    if (!PRINTED.equals(kanban.getStatus())) throw new BusinessException("看板状态不允许取消");

    InboundOrder order = inboundOrderMapper.selectById(kanban.getInboundOrderId());
    if (order == null) throw new BusinessException("关联入库单不存在");
    if (!RELEASED.equals(order.getStatus()) && !PARTIAL_RECEIVED.equals(order.getStatus())) {
        throw new BusinessException("入库单状态不允许取消看板");
    }

    kanban.setStatus(CANCELLED);
    kanbanBoardMapper.updateById(kanban);

    recalcPlannedQtyAndRefreshStatus(kanban.getInboundOrderLineId(), kanban.getInboundOrderId());

    return Map.of("cancelled", true, "kanbanCode", kanban.getKanbanCode());
}
```

## Subtask 6: Kanban Cancel — batch endpoint

Add to InventoryController:
```java
@PostMapping("/kanbans/cancel")
public Map<String, Object> cancelKanbansBatch(@RequestBody Map<String, List<Long>> body) {
    return inventoryService.cancelKanbansBatch(body.get("ids"));
}
```

Add to InventoryService:
```java
@Transactional
public Map<String, Object> cancelKanbansBatch(List<Long> ids) {
    if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要取消的看板");

    List<KanbanBoard> cancelled = new java.util.ArrayList<>();
    Set<Long> affectedLineIds = new java.util.HashSet<>();
    Set<Long> affectedOrderIds = new java.util.HashSet<>();

    for (Long kanbanId : ids) {
        KanbanBoard kanban = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
        if (kanban == null) throw new BusinessException("看板不存在: " + kanbanId);
        if (!PRINTED.equals(kanban.getStatus())) {
            throw new BusinessException("看板 %s 状态不允许取消".formatted(kanban.getKanbanCode()));
        }
        InboundOrder order = inboundOrderMapper.selectById(kanban.getInboundOrderId());
        if (!RELEASED.equals(order.getStatus()) && !PARTIAL_RECEIVED.equals(order.getStatus())) {
            throw new BusinessException("入库单 %s 状态不允许取消看板".formatted(order.getInboundNo()));
        }
        kanban.setStatus(CANCELLED);
        kanbanBoardMapper.updateById(kanban);
        cancelled.add(kanban);
        affectedLineIds.add(kanban.getInboundOrderLineId());
        affectedOrderIds.add(kanban.getInboundOrderId());
    }

    for (Long lineId : affectedLineIds) {
        Long orderId = cancelled.stream()
                .filter(k -> k.getInboundOrderLineId().equals(lineId))
                .findFirst().get().getInboundOrderId();
        recalcPlannedQtyAndRefreshStatus(lineId, orderId);
    }

    return Map.of("cancelledCount", cancelled.size());
}
```

## Subtask 7: RecalcPlannedQty helper method

Add to InventoryService:
```java
private void recalcPlannedQtyAndRefreshStatus(Long lineId, Long orderId) {
    List<KanbanBoard> lineKanbans = kanbanBoardMapper.selectList(Wrappers.<KanbanBoard>lambdaQuery()
            .eq(KanbanBoard::getInboundOrderLineId, lineId)
            .ne(KanbanBoard::getStatus, CANCELLED));
    BigDecimal newPlannedQty = lineKanbans.stream()
            .map(KanbanBoard::getBoardQty)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    InboundOrderLine line = inboundOrderLineMapper.selectById(lineId);
    if (line != null) {
        line.setPlannedQty(newPlannedQty);
        inboundOrderLineMapper.updateById(line);
    }

    InboundOrder order = inboundOrderMapper.selectById(orderId);
    if (order != null) refreshOrderStatus(order, LocalDateTime.now());
}
```

NOTE: Since refreshOrderStatus is private in InventoryService, you can reuse it directly. But recalcPlannedQtyAndRefreshStatus changes the line.plannedQty first, then calls refreshOrderStatus.

## Subtask 8: Release response enhancement

Modify InboundOrderController.release() to return kanban metadata. Create a wrapper or simply override:

Option: Create `InboundOrderReleaseResponse` extending InboundOrderResponse with `int kanbanCount` and `List<String> kanbanCodes`.

OR simpler: override the release method to return a Map:

In InboundOrderController:
```java
@PostMapping("/{id}/release")
public Map<String, Object> release(@PathVariable Long id) {
    InboundOrderResponse resp = service.release(id);
    // Count kanbans for this order
    java.util.List<KanbanBoard> kanbans = ...;
    return Map.of("order", resp, "kanbanCount", kanbans.size(), 
        "kanbanCodes", kanbans.stream().map(KanbanBoard::getKanbanCode).toList());
}
```

Actually, simpler: modify InboundOrderController to inject KanbanBoardMapper and query kanbans after release. Return a Map with order + kanban metadata.

## Subtask 9: MasterDataOptions — verify no containerTypeId leak

The MasterDataOptionsResponse.materials list uses OptionItem which has (id, code, name). Since Material.entity no longer has containerTypeId, the materialOptions() method in MasterDataService no longer maps containerTypeId. Verify this is correct and no other code still tries to include containerTypeId in material options.

## Subtask 10: data.sql seed — material_container_type

Add to data.sql:
```sql
INSERT IGNORE INTO material_container_type (material_id, container_type_id, is_default)
VALUES
  (1, 1, 1),
  (2, 1, 1),
  (3, 2, 1);
```

Also update the material INSERT to remove `container_type_id` column reference (it was removed from the table):
```sql
INSERT IGNORE INTO material (id, material_code, material_name, specification, unit, supplier_id, low_stock_qty, high_stock_qty, status)
VALUES
  (1, '5HG 807 109 C', '前保险杠支架', '汽车零件', '件', 1, 10, 500, 'ENABLED'),
  (2, '5WD 723 913 C', '踏板组件', '汽车零件', '件', 1, 20, 400, 'ENABLED'),
  (3, '5Q0 803 219 D', '车身连接件', '汽车零件', '件', 2, 30, 600, 'ENABLED');
```

## Verification

```bash
cd backend && mvn test
```
Target: ALL 35 tests pass, BUILD SUCCESS.

## Commit
```
feat: Layer 2 — material-container association CRUD, kanban cancel API, release response enhancement

- Material-container type association: GET/PUT /api/materials/{id}/container-types
- Kanban cancel: POST /api/inventory/kanbans/{kanbanId}/cancel and batch cancel
- Container type validation on inbound order create/update (material_container_type check)
- Release endpoint returns kanbanCount + kanbanCodes metadata
- Seed data: material_container_type associations + fix material INSERT
```
