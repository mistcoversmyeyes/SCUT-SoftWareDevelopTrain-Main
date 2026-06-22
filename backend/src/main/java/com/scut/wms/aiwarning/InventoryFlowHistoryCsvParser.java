package com.scut.wms.aiwarning;

import com.scut.wms.common.BusinessException;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

final class InventoryFlowHistoryCsvParser {
    record ParsedFile(List<String> headers, List<ParsedLine> lines) {
    }

    record ParsedLine(int rowNumber, List<String> cells) {
    }

    ParsedFile parse(String content) {
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "导入文件为空");
            }

            List<String> headers = parseLine(removeBom(headerLine));
            List<ParsedLine> rows = new ArrayList<>();
            String line;
            int rowNumber = 2;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    rowNumber++;
                    continue;
                }
                rows.add(new ParsedLine(rowNumber, parseLine(line)));
                rowNumber++;
            }
            return new ParsedFile(headers, rows);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "读取导入文件失败");
        }
    }

    private String removeBom(String value) {
        if (!value.isEmpty() && value.charAt(0) == '﻿') {
            return value.substring(1);
        }
        return value;
    }

    private List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (currentChar == ',' && !inQuotes) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(currentChar);
        }
        if (inQuotes) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "CSV 存在未闭合的引号");
        }
        cells.add(current.toString().trim());
        return cells;
    }
}
