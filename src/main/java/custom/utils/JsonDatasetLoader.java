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

    // 8MB buffer 加速大文件读取
    private static final int BUFFER_SIZE = 8 * 1024 * 1024;

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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)), BUFFER_SIZE)) {
            // 快速跳过 startRow 行（不做 trim，不做 JSON 解析）
            for (long skip = 0; skip < startRow; skip++) {
                if (reader.readLine() == null) {
                    log.warn("JSON Lines 文件 {} 行数不足: startRow={}", file.getName(), startRow);
                    return result;
                }
            }

            // 读取目标行并解析
            String line;
            long lineNum = startRow;
            while ((line = reader.readLine()) != null && result.size() < rowCount) {
                lineNum++;
                if (!line.isEmpty()) {
                    try {
                        result.add(GSON.fromJson(line, JsonObject.class));
                    } catch (com.google.gson.JsonSyntaxException e) {
                        log.warn("JSON Lines 文件 {} 第 {} 行解析失败，跳过: {}", file.getName(), lineNum, e.getMessage());
                    }
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
     * 快速统计 JSON Lines 文件中的行数。
     * 使用字节流计数换行符，比逐行读取+trim快很多。
     *
     * @param file JSON Lines 文件
     * @return 行数
     */
    public static long readRowCount(File file) throws IOException {
        long count = 0;
        byte[] buf = new byte[8192];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = fis.read(buf)) != -1) {
                for (int i = 0; i < bytesRead; i++) {
                    if (buf[i] == '\n') {
                        count++;
                    }
                }
            }
        }
        // 如果文件非空且最后一行没有换行符，也算一行
        if (file.length() > 0) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(file.length() - 1);
                if (raf.readByte() != '\n') {
                    count++;
                }
            }
        }
        return count;
    }
}
