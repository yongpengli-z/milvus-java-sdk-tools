package custom.utils;

import custom.common.QueryDatasetEnum;
import custom.exception.CustomException;
import custom.exception.CustomExceptionCode;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Query（搜索）数据集加载工具。
 * <p>
 * 与 DatasetUtil（插入数据集）区分：
 * <ul>
 *   <li>文件名不要求数字后缀，按文件名自然排序</li>
 *   <li>产物是 search 查询输入：vector 类型 → FloatVec，text 类型 → EmbeddedText</li>
 * </ul>
 */
@Slf4j
public class QueryDatasetUtil {

    /**
     * 遍历 query 数据集目录，返回匹配 prefixName + fileFormat 后缀的文件名列表。
     * 按文件名自然排序（query 数据集文件名不要求数字后缀，如 bm25_title_short.txt）。
     */
    public static List<String> providerFileNames(QueryDatasetEnum queryDatasetEnum) {
        log.info("正在遍历检查query数据集: {}", queryDatasetEnum.path);
        List<String> fileNameList = new ArrayList<>();
        File file = new File(queryDatasetEnum.path);
        if (file.exists() && file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                String expectedExt = "." + queryDatasetEnum.fileFormat;
                for (File fileItem : files) {
                    if (fileItem.isFile()
                            && fileItem.getName().contains(queryDatasetEnum.prefixName)
                            && fileItem.getName().endsWith(expectedExt)) {
                        fileNameList.add(fileItem.getName());
                    }
                }
            }
        } else {
            log.error("指定的query数据集路径不是一个有效的目录: {}", queryDatasetEnum.path);
        }
        // query 数据集按文件名自然排序，不强制数字后缀
        fileNameList.sort(Comparator.naturalOrder());
        return fileNameList;
    }

    /**
     * 从 query 数据集加载查询输入（最多 count 条）。
     *
     * @param queryDatasetEnum query 数据集枚举
     * @param count            期望加载的条数
     * @return vector 类型返回 FloatVec 列表，text 类型返回 EmbeddedText 列表
     */
    public static List<BaseVector> providerQueryVectors(QueryDatasetEnum queryDatasetEnum, int count) {
        if (count <= 0) {
            return new ArrayList<>();
        }
        List<String> fileNames = providerFileNames(queryDatasetEnum);
        if (fileNames.isEmpty()) {
            throw new CustomException(CustomExceptionCode.DATASET_LOAD_FAILED,
                    "query数据集未匹配到文件: path=" + queryDatasetEnum.path
                            + ", prefix=" + queryDatasetEnum.prefixName
                            + ", format=" + queryDatasetEnum.fileFormat);
        }

        if ("vector".equalsIgnoreCase(queryDatasetEnum.dataType)) {
            return loadFloatVectors(queryDatasetEnum, fileNames, count);
        } else if ("text".equalsIgnoreCase(queryDatasetEnum.dataType)) {
            return loadEmbeddedTexts(queryDatasetEnum, fileNames, count);
        } else {
            throw new CustomException(CustomExceptionCode.INVALID_PARAMS,
                    "不支持的query数据集类型: " + queryDatasetEnum.dataType
                            + "（仅支持 vector / text）");
        }
    }

    /**
     * 全量加载 query 数据集：先统计所有匹配文件的总条数，再全部读入。
     *
     * @param queryDatasetEnum query 数据集枚举
     * @return vector 类型返回 FloatVec 列表，text 类型返回 EmbeddedText 列表
     */
    public static List<BaseVector> providerAllQueryVectors(QueryDatasetEnum queryDatasetEnum) {
        long total = countTotalRows(queryDatasetEnum);
        if (total > Integer.MAX_VALUE) {
            throw new CustomException(CustomExceptionCode.INVALID_PARAMS,
                    "query数据集总条数超过 Integer.MAX_VALUE: " + total + "，请拆分文件");
        }
        log.info("query数据集[{}]全量加载，共 {} 条", queryDatasetEnum.datasetName, total);
        return providerQueryVectors(queryDatasetEnum, (int) total);
    }

    /**
     * 统计 query 数据集所有匹配文件的总条数（npy 读 header 第一维，txt 数行）。
     */
    private static long countTotalRows(QueryDatasetEnum queryDatasetEnum) {
        List<String> fileNames = providerFileNames(queryDatasetEnum);
        if (fileNames.isEmpty()) {
            throw new CustomException(CustomExceptionCode.DATASET_LOAD_FAILED,
                    "query数据集未匹配到文件: path=" + queryDatasetEnum.path
                            + ", prefix=" + queryDatasetEnum.prefixName
                            + ", format=" + queryDatasetEnum.fileFormat);
        }
        long total = 0;
        if ("vector".equalsIgnoreCase(queryDatasetEnum.dataType)) {
            for (String fileName : fileNames) {
                String npyDataPath = queryDatasetEnum.path + fileName;
                try (FileInputStream fis = new FileInputStream(npyDataPath)) {
                    total += NpyLoader.readFirstDimensionSize(fis);
                } catch (IOException e) {
                    throw new CustomException(CustomExceptionCode.DATASET_LOAD_FAILED,
                            "读取query数据集npy文件header失败: " + npyDataPath + ", cause: " + e.getMessage(), e);
                }
            }
        } else if ("text".equalsIgnoreCase(queryDatasetEnum.dataType)) {
            for (String fileName : fileNames) {
                String txtDataPath = queryDatasetEnum.path + fileName;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(txtDataPath)), 8 * 1024 * 1024)) {
                    while (reader.readLine() != null) {
                        total++;
                    }
                } catch (IOException e) {
                    throw new CustomException(CustomExceptionCode.DATASET_LOAD_FAILED,
                            "统计query数据集txt文件行数失败: " + txtDataPath + ", cause: " + e.getMessage(), e);
                }
            }
        } else {
            throw new CustomException(CustomExceptionCode.INVALID_PARAMS,
                    "不支持的query数据集类型: " + queryDatasetEnum.dataType
                            + "（仅支持 vector / text）");
        }
        return total;
    }

    /**
     * 从 npy 文件加载稠密向量，包装为 FloatVec。
     */
    private static List<BaseVector> loadFloatVectors(QueryDatasetEnum queryDatasetEnum, List<String> fileNames, int count) {
        List<BaseVector> vectors = new ArrayList<>(count);
        int remaining = count;
        for (String fileName : fileNames) {
            if (remaining <= 0) {
                break;
            }
            String npyDataPath = queryDatasetEnum.path + fileName;
            File npyFile = new File(npyDataPath);
            try (FileInputStream fis = new FileInputStream(npyFile)) {
                long totalRows = NpyLoader.readFirstDimensionSize(fis);
                int rowsToRead = (int) Math.min(remaining, totalRows);
                NpyLoader.FloatMatrixSlice slice = NpyLoader.readFloatMatrixSlice(npyFile, 0, rowsToRead);
                // 校验维度与枚举定义一致
                if (queryDatasetEnum.dim > 0 && slice.cols != queryDatasetEnum.dim) {
                    throw new CustomException(CustomExceptionCode.INVALID_PARAMS,
                            "query数据集维度不匹配: 文件 " + fileName + " 实际维度=" + slice.cols
                                    + "，枚举定义 dim=" + queryDatasetEnum.dim);
                }
                for (int r = 0; r < slice.rows; r++) {
                    List<Float> row = new ArrayList<>(slice.cols);
                    for (int c = 0; c < slice.cols; c++) {
                        row.add(slice.data[r * slice.cols + c]);
                    }
                    vectors.add(new FloatVec(row));
                }
                remaining -= slice.rows;
                log.info("query数据集加载向量: {} (rows={}, cols={}), 累计={}", fileName, slice.rows, slice.cols, vectors.size());
            } catch (IOException e) {
                log.error("读取query数据集npy文件失败: {}", npyDataPath, e);
                throw new CustomException(CustomExceptionCode.DATASET_LOAD_FAILED,
                        "读取query数据集npy文件失败: " + npyDataPath + ", cause: " + e.getMessage(), e);
            }
        }
        log.info("query数据集[{}]加载完成，可用向量数: {}", queryDatasetEnum.datasetName, vectors.size());
        return vectors;
    }

    /**
     * 从 txt 文件加载文本行，包装为 EmbeddedText（与 BM25 Function 输入一致）。
     */
    private static List<BaseVector> loadEmbeddedTexts(QueryDatasetEnum queryDatasetEnum, List<String> fileNames, int count) {
        List<BaseVector> texts = new ArrayList<>(count);
        int remaining = count;
        for (String fileName : fileNames) {
            if (remaining <= 0) {
                break;
            }
            String txtDataPath = queryDatasetEnum.path + fileName;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(txtDataPath)), 8 * 1024 * 1024)) {
                String line;
                while (remaining > 0 && (line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    texts.add(new EmbeddedText(line));
                    remaining--;
                }
                log.info("query数据集加载文本: {}, 累计={}", fileName, texts.size());
            } catch (IOException e) {
                log.error("读取query数据集txt文件失败: {}", txtDataPath, e);
                throw new CustomException(CustomExceptionCode.DATASET_LOAD_FAILED,
                        "读取query数据集txt文件失败: " + txtDataPath + ", cause: " + e.getMessage(), e);
            }
        }
        log.info("query数据集[{}]加载完成，可用文本数: {}", queryDatasetEnum.datasetName, texts.size());
        return texts;
    }
}
