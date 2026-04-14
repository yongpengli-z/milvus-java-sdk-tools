package custom.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.MessageType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parquet 格式数据集文件加载器。
 * <p>
 * 读取 Parquet 文件中第一列的文本数据，每行作为一条字符串记录。
 */
@Slf4j
public class ParquetDatasetLoader {

    private static final Configuration HADOOP_CONF = new Configuration();

    /**
     * 从 Parquet 文件中读取指定范围的第一列文本数据。
     *
     * @param file     Parquet 文件
     * @param startRow 起始行号（0-based）
     * @param rowCount 读取行数
     * @return 字符串列表
     */
    public static List<String> readTextColumn(File file, long startRow, int rowCount) throws IOException {
        if (rowCount <= 0) {
            return Collections.emptyList();
        }
        if (startRow < 0) {
            throw new IllegalArgumentException("startRow must be >= 0");
        }

        List<String> result = new ArrayList<>(rowCount);
        Path parquetPath = new Path(file.getAbsolutePath());

        try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(parquetPath, HADOOP_CONF))) {
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            String firstColumnName = schema.getFieldName(0);

            long currentRow = 0;
            PageReadStore rowGroup;

            while ((rowGroup = reader.readNextRowGroup()) != null && result.size() < rowCount) {
                long rowGroupSize = rowGroup.getRowCount();

                // 如果整个 row group 都在 startRow 之前，跳过
                if (currentRow + rowGroupSize <= startRow) {
                    currentRow += rowGroupSize;
                    continue;
                }

                MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                RecordReader<Group> recordReader = columnIO.getRecordReader(rowGroup, new GroupRecordConverter(schema));

                for (long i = 0; i < rowGroupSize && result.size() < rowCount; i++) {
                    Group group = recordReader.read();
                    long globalRow = currentRow + i;

                    if (globalRow < startRow) {
                        continue;
                    }

                    try {
                        String value = group.getString(firstColumnName, 0);
                        result.add(value);
                    } catch (Exception e) {
                        log.warn("Parquet 文件 {} 第 {} 行读取失败，跳过: {}", file.getName(), globalRow, e.getMessage());
                    }
                }

                currentRow += rowGroupSize;
            }
        }

        if (result.size() < rowCount) {
            log.warn("Parquet 文件 {} 中可读取的数据不足: 请求 {} 条(startRow={})，实际读取 {} 条",
                    file.getName(), rowCount, startRow, result.size());
        }

        return result;
    }

    /**
     * 快速获取 Parquet 文件的总行数（从 footer metadata 读取，不扫描数据）。
     *
     * @param file Parquet 文件
     * @return 总行数
     */
    public static long readRowCount(File file) throws IOException {
        Path parquetPath = new Path(file.getAbsolutePath());
        try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(parquetPath, HADOOP_CONF))) {
            return reader.getRecordCount();
        }
    }
}
