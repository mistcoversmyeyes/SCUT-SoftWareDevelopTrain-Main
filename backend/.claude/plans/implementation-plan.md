# Implementation Plan: SCUT WMS Backend Extensions

## Overview
Implement container type CRUD, extend master data CRUD, outbound order CRUD, FIFO outbound picking, and dashboard/inventory extensions.

## Files to Create/Modify

### Step 1: Container Type (5 NEW files)
- `backend/src/main/java/com/scut/wms/container/ContainerType.java`
- `backend/src/main/java/com/scut/wms/container/ContainerTypeMapper.java`
- `backend/src/main/java/com/scut/wms/container/ContainerTypeRequest.java`
- `backend/src/main/java/com/scut/wms/container/ContainerTypeService.java`
- `backend/src/main/java/com/scut/wms/container/ContainerTypeController.java`

### Step 2: Extend MasterData (CREATE 4 + MODIFY 4)
Create:
- `backend/src/main/java/com/scut/wms/masterdata/SupplierRequest.java`
- `backend/src/main/java/com/scut/wms/masterdata/MaterialRequest.java`
- `backend/src/main/java/com/scut/wms/masterdata/WarehouseRequest.java`
- `backend/src/main/java/com/scut/wms/masterdata/StorageLocationRequest.java`

Modify:
- `MasterDataService.java` - add CRUD methods
- `MasterDataController.java` - add POST/PUT endpoints
- `MasterDataOptionsResponse.java` - add containerTypes field
- `Material.java` - add containerTypeId, lowStockQty, highStockQty fields

### Step 3: Outbound Order CRUD (9 NEW files)
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrder.java`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderLine.java`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderRequest.java`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderResponse.java`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderMapper.java`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderLineMapper.java`
- `backend/src/main/resources/mapper/OutboundMapper.xml`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderService.java`
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderController.java`

### Step 4: FIFO Picking (5 NEW + MODIFY 2)
Create:
- `backend/src/main/java/com/scut/wms/outbound/picking/ScanOutboundRequest.java`
- `backend/src/main/java/com/scut/wms/outbound/picking/ScanOutboundResponse.java`
- `backend/src/main/java/com/scut/wms/outbound/picking/FifoPickCandidate.java`
- `backend/src/main/java/com/scut/wms/outbound/picking/OutboundPickingService.java`
- `backend/src/main/java/com/scut/wms/outbound/picking/PickingController.java`

Modify:
- `InventoryTransactionMapper.java` - add selectFifoCandidateForUpdate
- `InventoryMapper.xml` - add FIFO select
- `KanbanBoard.java` - add pickedQty field

### Step 5: Extend Inventory + Dashboard (MODIFY 1 + CREATE 3)
Modify:
- `InventoryQueryController.java` - add endpoints

Create:
- `backend/src/main/java/com/scut/wms/dashboard/DashboardStatsResponse.java`
- `backend/src/main/java/com/scut/wms/dashboard/DashboardService.java`
- `backend/src/main/java/com/scut/wms/dashboard/DashboardController.java`

## Execution
Execute steps sequentially 1-5, with `mvn compile -q` after each step.
Run `mvn test` after all steps complete.
