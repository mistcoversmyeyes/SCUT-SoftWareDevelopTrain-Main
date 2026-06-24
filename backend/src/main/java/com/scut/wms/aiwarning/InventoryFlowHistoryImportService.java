package com.scut.wms.aiwarning;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.masterdata.StorageLocation;
import com.scut.wms.masterdata.StorageLocationMapper;
import com.scut.wms.masterdata.Warehouse;
import com.scut.wms.masterdata.WarehouseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InventoryFlowHistoryImportService {
    private static final String IMPORT_OBJECT = "inventory_flow_history";
    private static final String TEMPLATE_VERSION = "inventory_flow_history.v1.csv";
    private static final List<String> HEADERS = List.of(
            "business_date",
            "material_code",
            "warehouse_code",
            "location_code",
            "board_code",
            "movement_type",
            "quantity",
            "source_order_no",
            "quality_status"
    );
    private static final Set<String> ALLOWED_MOVEMENT_TYPES = Set.of(
            "INBOUND",
            "OUTBOUND",
            "ADJUST",
            "SEAL",
            "UNSEAL",
            "SCRAP"
    );

    private final AiImportBatchMapper batchMapper;
    private final InventoryFlowHistoryRecordMapper recordMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final InventoryFlowHistoryCsvParser csvParser = new InventoryFlowHistoryCsvParser();

    public InventoryFlowHistoryImportService(
            AiImportBatchMapper batchMapper,
            InventoryFlowHistoryRecordMapper recordMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper
    ) {
        this.batchMapper = batchMapper;
        this.recordMapper = recordMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
    }

    @Transactional
    public InventoryFlowHistoryImportResponse importInventoryFlowHistory(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传非空 CSV 文件");
        }

        InventoryFlowHistoryCsvParser.ParsedFile parsedFile = csvParser.parse(readFile(file));
        validateHeaders(parsedFile.headers());

        Map<String, Long> materialCodeMap = materialMapper.selectList(Wrappers.<Material>lambdaQuery()
                        .select(Material::getId, Material::getMaterialCode))
                .stream()
                .filter(item -> StringUtils.hasText(item.getMaterialCode()))
                .collect(Collectors.toMap(Material::getMaterialCode, Material::getId, (left, right) -> left));

        List<Warehouse> warehouses = warehouseMapper.selectList(null);
        Map<String, Long> warehouseCodeMap = warehouses.stream()
                .filter(item -> StringUtils.hasText(item.getWarehouseCode()))
                .collect(Collectors.toMap(Warehouse::getWarehouseCode, Warehouse::getId, (left, right) -> left));
        Map<Long, String> warehouseIdToCode = warehouses.stream()
                .filter(item -> StringUtils.hasText(item.getWarehouseCode()))
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseCode, (left, right) -> left));

        Map<String, Long> locationKeyMap = new HashMap<>();
        for (StorageLocation location : storageLocationMapper.selectList(null)) {
            String warehouseCode = warehouseIdToCode.get(location.getWarehouseId());
            if (!StringUtils.hasText(warehouseCode) || !StringUtils.hasText(location.getLocationCode())) {
                continue;
            }
            locationKeyMap.put(locationKey(warehouseCode, location.getLocationCode()), location.getId());
        }

        AiImportBatch batch = new AiImportBatch();
        batch.setImportType(IMPORT_OBJECT);
        batch.setFileName(file.getOriginalFilename() == null ? "inventory-flow-history.csv" : file.getOriginalFilename());
        batch.setTemplateVersion(TEMPLATE_VERSION);
        batch.setTotalRows(parsedFile.lines().size());
        batch.setSuccessRows(0);
        batch.setFailedRows(0);
        batchMapper.insert(batch);

        List<InventoryFlowHistoryRecord> validRecords = new ArrayList<>();
        List<InventoryFlowHistoryImportError> errors = new ArrayList<>();
        for (InventoryFlowHistoryCsvParser.ParsedLine line : parsedFile.lines()) {
            validateLine(line, batch.getId(), materialCodeMap, warehouseCodeMap, locationKeyMap, validRecords, errors);
        }

        for (InventoryFlowHistoryRecord record : validRecords) {
            recordMapper.insert(record);
        }

        batch.setSuccessRows(validRecords.size());
        batch.setFailedRows((int) parsedFile.lines().stream()
                .map(InventoryFlowHistoryCsvParser.ParsedLine::rowNumber)
                .filter(rowNumber -> errors.stream().anyMatch(error -> Objects.equals(error.rowNumber(), rowNumber)))
                .distinct()
                .count());
        batchMapper.updateById(batch);

        return new InventoryFlowHistoryImportResponse(
                IMPORT_OBJECT,
                batch.getFileName(),
                batch.getTotalRows(),
                batch.getSuccessRows(),
                batch.getFailedRows(),
                batch.getId(),
                summarize(validRecords),
                errors
        );
    }

    public List<InventoryFlowHistoryBatchView> listBatches() {
        return batchMapper.selectList(Wrappers.<AiImportBatch>lambdaQuery()
                        .eq(AiImportBatch::getImportType, IMPORT_OBJECT)
                        .orderByDesc(AiImportBatch::getImportedAt)
                        .orderByDesc(AiImportBatch::getId))
                .stream()
                .map(batch -> new InventoryFlowHistoryBatchView(
                        batch.getId(),
                        batch.getImportType(),
                        batch.getFileName(),
                        batch.getTotalRows(),
                        batch.getSuccessRows(),
                        batch.getFailedRows(),
                        batch.getImportedAt()
                ))
                .toList();
    }

    public List<InventoryFlowHistoryRecordView> listRecords(Long batchId, String materialCode, String movementType) {
        return recordMapper.selectList(Wrappers.<InventoryFlowHistoryRecord>lambdaQuery()
                        .eq(batchId != null, InventoryFlowHistoryRecord::getBatchId, batchId)
                        .eq(StringUtils.hasText(materialCode), InventoryFlowHistoryRecord::getMaterialCode, materialCode)
                        .eq(StringUtils.hasText(movementType), InventoryFlowHistoryRecord::getMovementType, normalizeMovementType(movementType))
                        .orderByDesc(InventoryFlowHistoryRecord::getBusinessDate)
                        .orderByDesc(InventoryFlowHistoryRecord::getRowNumber))
                .stream()
                .map(record -> new InventoryFlowHistoryRecordView(
                        record.getBatchId(),
                        record.getRowNumber(),
                        record.getBusinessDate(),
                        record.getMaterialCode(),
                        record.getWarehouseCode(),
                        record.getLocationCode(),
                        record.getBoardCode(),
                        record.getMovementType(),
                        record.getQuantity(),
                        record.getSourceOrderNo(),
                        record.getQualityStatus(),
                        record.getImportedAt()
                ))
                .toList();
    }

    private void validateHeaders(List<String> headers) {
        if (!HEADERS.equals(headers)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CSV 表头必须为: " + String.join(",", HEADERS));
        }
    }

    private void validateLine(
            InventoryFlowHistoryCsvParser.ParsedLine line,
            Long batchId,
            Map<String, Long> materialCodeMap,
            Map<String, Long> warehouseCodeMap,
            Map<String, Long> locationKeyMap,
            List<InventoryFlowHistoryRecord> validRecords,
            List<InventoryFlowHistoryImportError> errors
    ) {
        if (line.cells().size() != HEADERS.size()) {
            errors.add(new InventoryFlowHistoryImportError(
                    line.rowNumber(),
                    "row",
                    "列数不正确，应为 " + HEADERS.size() + " 列",
                    String.join(",", line.cells())
            ));
            return;
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < HEADERS.size(); index++) {
            values.put(HEADERS.get(index), line.cells().get(index));
        }

        LocalDate businessDate = parseDate(line.rowNumber(), values.get("business_date"), errors);
        String materialCode = requireText(line.rowNumber(), "material_code", values.get("material_code"), errors);
        String warehouseCode = requireText(line.rowNumber(), "warehouse_code", values.get("warehouse_code"), errors);
        String locationCode = requireText(line.rowNumber(), "location_code", values.get("location_code"), errors);
        String boardCode = requireText(line.rowNumber(), "board_code", values.get("board_code"), errors);
        String movementType = normalizeMovementType(values.get("movement_type"));
        BigDecimal quantity = parseQuantity(line.rowNumber(), values.get("quantity"), errors);
        String sourceOrderNo = requireText(line.rowNumber(), "source_order_no", values.get("source_order_no"), errors);
        String qualityStatus = normalizeOptional(values.get("quality_status"));

        if (!StringUtils.hasText(movementType)) {
            errors.add(new InventoryFlowHistoryImportError(line.rowNumber(), "movement_type", "movement_type 不能为空", values.get("movement_type")));
        } else if (!ALLOWED_MOVEMENT_TYPES.contains(movementType)) {
            errors.add(new InventoryFlowHistoryImportError(line.rowNumber(), "movement_type", "movement_type 不在允许范围内", values.get("movement_type")));
        }
        if (StringUtils.hasText(materialCode) && !materialCodeMap.containsKey(materialCode)) {
            errors.add(new InventoryFlowHistoryImportError(line.rowNumber(), "material_code", "物料编码不存在", materialCode));
        }
        if (StringUtils.hasText(warehouseCode) && !warehouseCodeMap.containsKey(warehouseCode)) {
            errors.add(new InventoryFlowHistoryImportError(line.rowNumber(), "warehouse_code", "仓库编码不存在", warehouseCode));
        }
        if (StringUtils.hasText(warehouseCode) && StringUtils.hasText(locationCode)
                && !locationKeyMap.containsKey(locationKey(warehouseCode, locationCode))) {
            errors.add(new InventoryFlowHistoryImportError(line.rowNumber(), "location_code", "库位编码不存在或不属于该仓库", locationCode));
        }
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new InventoryFlowHistoryImportError(line.rowNumber(), "quantity", "数量必须大于 0", values.get("quantity")));
        }
        if (errors.stream().anyMatch(error -> Objects.equals(error.rowNumber(), line.rowNumber()))) {
            return;
        }

        InventoryFlowHistoryRecord record = new InventoryFlowHistoryRecord();
        record.setBatchId(batchId);
        record.setRowNumber(line.rowNumber());
        record.setBusinessDate(businessDate);
        record.setMaterialCode(materialCode);
        record.setWarehouseCode(warehouseCode);
        record.setLocationCode(locationCode);
        record.setBoardCode(boardCode);
        record.setMovementType(movementType);
        record.setQuantity(quantity);
        record.setSourceOrderNo(sourceOrderNo);
        record.setQualityStatus(qualityStatus);
        validRecords.add(record);
    }

    private InventoryFlowHistoryImportSummary summarize(List<InventoryFlowHistoryRecord> records) {
        if (records.isEmpty()) {
            return new InventoryFlowHistoryImportSummary(0, Map.of(), null, null);
        }

        Map<String, Long> movementTypeCounts = records.stream()
                .collect(Collectors.groupingBy(
                        InventoryFlowHistoryRecord::getMovementType,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        LocalDate businessDateStart = records.stream()
                .map(InventoryFlowHistoryRecord::getBusinessDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate businessDateEnd = records.stream()
                .map(InventoryFlowHistoryRecord::getBusinessDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        int materialCount = (int) records.stream()
                .map(InventoryFlowHistoryRecord::getMaterialCode)
                .distinct()
                .count();

        return new InventoryFlowHistoryImportSummary(materialCount, movementTypeCounts, businessDateStart, businessDateEnd);
    }

    private String readFile(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "读取导入文件失败");
        }
    }

    private LocalDate parseDate(int rowNumber, String value, List<InventoryFlowHistoryImportError> errors) {
        String normalized = requireText(rowNumber, "business_date", value, errors);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException exception) {
            errors.add(new InventoryFlowHistoryImportError(rowNumber, "business_date", "日期格式必须为 yyyy-MM-dd", value));
            return null;
        }
    }

    private BigDecimal parseQuantity(int rowNumber, String value, List<InventoryFlowHistoryImportError> errors) {
        String normalized = requireText(rowNumber, "quantity", value, errors);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException exception) {
            errors.add(new InventoryFlowHistoryImportError(rowNumber, "quantity", "数量必须为数字", value));
            return null;
        }
    }

    private String requireText(int rowNumber, String field, String value, List<InventoryFlowHistoryImportError> errors) {
        if (!StringUtils.hasText(value)) {
            errors.add(new InventoryFlowHistoryImportError(rowNumber, field, field + " 不能为空", value));
            return null;
        }
        return value.trim();
    }

    private String normalizeMovementType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String locationKey(String warehouseCode, String locationCode) {
        return warehouseCode + "::" + locationCode;
    }
}
