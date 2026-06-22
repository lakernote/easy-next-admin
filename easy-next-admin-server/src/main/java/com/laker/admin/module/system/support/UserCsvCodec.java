package com.laker.admin.module.system.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UserCsvCodec {
    public static final String IMPORT_TEMPLATE_FILE_NAME = "用户导入模板.csv";
    public static final String EXPORT_FILE_NAME = "用户导出.csv";

    private static final List<String> IMPORT_HEADERS = List.of(
            "用户名", "姓名", "员工编号", "岗位", "手机号", "邮箱", "部门名称", "角色编码", "启用"
    );
    private static final List<String> EXPORT_HEADERS = List.of(
            "用户名", "姓名", "员工编号", "岗位", "手机号", "邮箱", "部门名称", "角色编码", "角色名称", "启用"
    );

    private UserCsvCodec() {
    }

    public static byte[] importTemplateBytes() {
        List<List<String>> rows = List.of(
                IMPORT_HEADERS,
                List.of("new_staff", "新员工", "EA009001", "客户运营专员", "13800009001", "new.staff@example.com", "客户成功中心", "staff", "1")
        );
        return toCsvBytes(rows);
    }

    public static List<ImportRow> parseImportRows(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        List<List<String>> records = parse(content);
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "导入文件为空");
        }
        Map<String, Integer> headerIndex = indexHeaders(records.get(0));
        requireHeader(headerIndex, "用户名");
        requireHeader(headerIndex, "姓名");
        requireHeader(headerIndex, "部门名称");
        requireHeader(headerIndex, "角色编码");

        List<ImportRow> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            if (isBlankRecord(record)) {
                continue;
            }
            rows.add(new ImportRow(
                    index + 1,
                    read(record, headerIndex, "用户名"),
                    read(record, headerIndex, "姓名"),
                    read(record, headerIndex, "员工编号"),
                    read(record, headerIndex, "岗位"),
                    read(record, headerIndex, "手机号"),
                    read(record, headerIndex, "邮箱"),
                    read(record, headerIndex, "部门名称"),
                    read(record, headerIndex, "角色编码"),
                    read(record, headerIndex, "启用")
            ));
        }
        return rows;
    }

    public static byte[] exportRows(List<ExportRow> rows) {
        List<List<String>> csvRows = new ArrayList<>();
        csvRows.add(EXPORT_HEADERS);
        rows.forEach(row -> csvRows.add(List.of(
                value(row.userName()),
                value(row.nickName()),
                value(row.employeeNo()),
                value(row.positionName()),
                value(row.phone()),
                value(row.email()),
                value(row.deptName()),
                value(row.roleCodes()),
                value(row.roleNames()),
                row.enabled() == null || row.enabled() == 1 ? "1" : "0"
        )));
        return toCsvBytes(csvRows);
    }

    private static byte[] toCsvBytes(List<List<String>> rows) {
        return ("\uFEFF" + format(rows)).getBytes(StandardCharsets.UTF_8);
    }

    private static String format(List<List<String>> rows) {
        StringBuilder builder = new StringBuilder();
        for (List<String> row : rows) {
            for (int index = 0; index < row.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(escape(row.get(index)));
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String escape(String value) {
        String text = protectFormula(value(value));
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String protectFormula(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private static List<List<String>> parse(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char ch = content.charAt(index);
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            if (ch == '"' && cell.length() == 0) {
                quoted = true;
            } else if (ch == ',') {
                record.add(cell.toString().trim());
                cell.setLength(0);
            } else if (ch == '\n') {
                record.add(cell.toString().trim());
                records.add(record);
                record = new ArrayList<>();
                cell.setLength(0);
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !CollectionUtils.isEmpty(record)) {
            record.add(cell.toString().trim());
            records.add(record);
        }
        return records;
    }

    private static Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int position = 0; position < headers.size(); position++) {
            index.put(normalizeHeader(headers.get(position)), position);
        }
        return index;
    }

    private static void requireHeader(Map<String, Integer> headerIndex, String... candidates) {
        for (String candidate : candidates) {
            if (headerIndex.containsKey(normalizeHeader(candidate))) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "导入文件缺少必要列：" + candidates[0]);
    }

    private static String read(List<String> record, Map<String, Integer> headerIndex, String... candidates) {
        for (String candidate : candidates) {
            Integer index = headerIndex.get(normalizeHeader(candidate));
            if (index != null && index < record.size()) {
                return record.get(index);
            }
        }
        return "";
    }

    private static boolean isBlankRecord(List<String> record) {
        return record.stream().noneMatch(StringUtils::hasText);
    }

    private static String normalizeHeader(String header) {
        return value(header).replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record ImportRow(
            int rowNumber,
            String userName,
            String nickName,
            String employeeNo,
            String positionName,
            String phone,
            String email,
            String deptName,
            String roleCodes,
            String enable
    ) {
    }

    public record ExportRow(
            String userName,
            String nickName,
            String employeeNo,
            String positionName,
            String phone,
            String email,
            String deptName,
            String roleCodes,
            String roleNames,
            Integer enabled
    ) {
    }
}
