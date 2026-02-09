package custom.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON Lines 格式数据集文件加载器。
 * <p>
 * 文件格式：每行一个独立的 JSON 对象（JSON Lines / NDJSON）。
 * 每行数据作为 collection 中 JSON 类型列的值。
 */
@Slf4j
public class JsonDatasetLoader {

    private static final Gson GSON = new Gson();

    /**
     * 从 JSON Lines 文件中读取指定范围的数据。
     *
     * @param file     JSON Lines 文件
     * @param startRow 起始行号（0-based）
     * @param rowCount 读取行数
     * @return JsonObject 列表
     */
    public static List<JsonObject> readJsonLines(File file, long startRow, int rowCount) throws IOException {
        if (rowCount <= 0) {
            return Collections.emptyList();
        }
        if (startRow < 0) {
            throw new IllegalArgumentException("startRow must be >= 0");
        }

        List<JsonObject> result = new ArrayList<>(rowCount);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            long currentRow = 0;
            String line;

            // 跳过 startRow 之前的行
            while ((line = reader.readLine()) != null && currentRow < startRow) {
                currentRow++;
            }

            // 如果跳过后已经到了文件末尾
            if (line == null) {
                log.warn("JSON Lines 文件 {} 行数不足: startRow={}", file.getName(), startRow);
                return result;
            }

            // 读取第一行（跳过循环结束时已读到的行）
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.add(GSON.fromJson(trimmed, JsonObject.class));
            }

            // 继续读取剩余行
            while ((line = reader.readLine()) != null && result.size() < rowCount) {
                trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    result.add(GSON.fromJson(trimmed, JsonObject.class));
                }
            }
        }

        if (result.size() < rowCount) {
            log.warn("JSON Lines 文件 {} 中可读取的数据不足: 请求 {} 条(startRow={})，实际读取 {} 条",
                    file.getName(), rowCount, startRow, result.size());
        }

        return result;
    }

    /**
     * 快速统计 JSON Lines 文件中的行数（非空行）。
     *
     * @param file JSON Lines 文件
     * @return 行数
     */
    public static long readRowCount(File file) throws IOException {
        long count = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    count++;
                }
            }
        }

        return count;
    }
}
