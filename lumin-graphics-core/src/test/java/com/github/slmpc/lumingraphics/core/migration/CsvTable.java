package com.github.slmpc.lumingraphics.core.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CsvTable {
    private CsvTable() {
    }

    static List<Map<String, String>> read(Path path) throws IOException {
        List<List<String>> records = parse(Files.readString(path));
        if (records.isEmpty()) {
            throw new IllegalArgumentException("CSV is empty: " + path);
        }

        List<String> header = records.get(0);
        Set<String> uniqueHeader = new LinkedHashSet<>(header);
        if (header.stream().anyMatch(String::isBlank) || uniqueHeader.size() != header.size()) {
            throw new IllegalArgumentException("CSV header contains blank or duplicate fields: " + path);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        for (int recordIndex = 1; recordIndex < records.size(); recordIndex++) {
            List<String> record = records.get(recordIndex);
            if (record.size() != header.size()) {
                throw new IllegalArgumentException(
                        "CSV row " + (recordIndex + 1) + " has " + record.size()
                                + " fields; expected " + header.size());
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int fieldIndex = 0; fieldIndex < header.size(); fieldIndex++) {
                row.put(header.get(fieldIndex), record.get(fieldIndex));
            }
            rows.add(Collections.unmodifiableMap(row));
        }
        return Collections.unmodifiableList(rows);
    }

    private static List<List<String>> parse(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < csv.length(); index++) {
            char character = csv.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"' && field.isEmpty()) {
                quoted = true;
            } else if (character == ',') {
                record.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                record.add(stripCarriageReturn(field));
                field.setLength(0);
                if (!record.stream().allMatch(String::isEmpty)) {
                    records.add(List.copyOf(record));
                }
                record.clear();
            } else {
                field.append(character);
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("CSV ends inside a quoted field");
        }
        if (!field.isEmpty() || !record.isEmpty()) {
            record.add(stripCarriageReturn(field));
            records.add(List.copyOf(record));
        }
        return records;
    }

    private static String stripCarriageReturn(StringBuilder field) {
        int length = field.length();
        if (length > 0 && field.charAt(length - 1) == '\r') {
            return field.substring(0, length - 1);
        }
        return field.toString();
    }
}
