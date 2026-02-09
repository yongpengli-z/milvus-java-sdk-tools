package custom.utils;

import com.google.gson.JsonObject;
import custom.common.DatasetEnum;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.*;

@Slf4j
public class DatasetUtil {

    public static List<String> providerFileNames(DatasetEnum datasetEnum) {
        log.info("正在遍历检查数据集...");
        List<String> fileNameList = new ArrayList<>();
        File file = new File(datasetEnum.path);
        // 检查目录是否存在且是一个目录
        if (file.exists() && file.isDirectory()) {
            // 获取目录下的所有文件和子目录
            File[] files = file.listFiles();
            // 遍历并打印包含指定关键字的文件名
            if (files != null) {
                String expectedExt = "." + datasetEnum.fileFormat;
                for (File fileItem : files) {
                    if (fileItem.isFile()
                            && fileItem.getName().contains(datasetEnum.prefixName)
                            && fileItem.getName().endsWith(expectedExt)) {
                        fileNameList.add(fileItem.getName());
                    }
                }
            }
        } else {
            log.error("指定的路径不是一个有效的目录.");
        }
        // 排序
        fileNameList.sort(Comparator.comparingInt(x ->
                Integer.parseInt(x.substring(x.lastIndexOf("_") + 1, x.lastIndexOf("."))))
        );
        return fileNameList;
    }

//    public static List<Long> providerFileSize(List<String> filesNames, DatasetEnum datasetEnum) {
//        log.info("正在统计数据集各个文件大小...");
//        List<Long> fileSizeList = new ArrayList<>();
//        int i = 0;
//        while (i < filesNames.size()) {
//            String npyDataPath = datasetEnum.path + filesNames.get(i);
//            File file = new File(npyDataPath);
//            NpyArray<?> npyArray = Npy.read(file);
//            fileSizeList.add((long) npyArray.shape()[0]);
//            i++;
//        }
//        return fileSizeList;
//    }
public static List<Long> providerFileSize(List<String> fileNames, DatasetEnum datasetEnum) {
    log.info("正在统计数据集各个文件大小...");
    List<Long> fileSizeList = new ArrayList<>(fileNames.size());
    for (String fileName : fileNames) {
        String path = datasetEnum.path + fileName;
        try {
            long rows;
            if ("json".equalsIgnoreCase(datasetEnum.fileFormat)) {
                rows = JsonDatasetLoader.readRowCount(new File(path));
            } else {
                try (FileInputStream fis = new FileInputStream(path)) {
                    rows = NpyLoader.readFirstDimensionSize(fis);
                }
            }
            fileSizeList.add(rows);
        } catch (IOException e) {
            log.error("读取文件行数失败: {}", path, e);
            fileSizeList.add(0L);
        }
    }
    return fileSizeList;
}

    public static List<List<Float>> providerFloatVectorByDataset(long index, long count, List<String> fileNames, DatasetEnum datasetEnum, List<Long> fileSizeList) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("count is too large for List: " + count);
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (fileNames == null || fileNames.isEmpty()) {
            log.warn("Dataset fileNames is empty");
            return Collections.emptyList();
        }
        if (fileSizeList == null || fileSizeList.isEmpty()) {
            log.warn("Dataset fileSizeList is empty");
            return Collections.emptyList();
        }

        List<List<Float>> vectors = new ArrayList<>((int) count);

        long remaining = count;
        long globalPos = index;

        int fileIndex = findIndex(globalPos, fileSizeList);
        if (fileIndex < 0 || fileIndex >= fileNames.size() || fileIndex >= fileSizeList.size()) {
            log.warn("index {} out of dataset range (files={}, sizes={})", index, fileNames.size(), fileSizeList.size());
            return Collections.emptyList();
        }

        // global start row of current file
        long fileStartGlobal = 0;
        for (int i = 0; i < fileIndex; i++) {
            fileStartGlobal += fileSizeList.get(i);
        }

        while (remaining > 0 && fileIndex < fileNames.size() && fileIndex < fileSizeList.size()) {
            long rowsInFile = fileSizeList.get(fileIndex);
            long localStart = globalPos - fileStartGlobal;
            if (localStart < 0) {
                localStart = 0;
            }
            if (localStart >= rowsInFile) {
                fileStartGlobal += rowsInFile;
                fileIndex++;
                continue;
            }

            long available = rowsInFile - localStart;
            int rowsToRead = (int) Math.min(remaining, available);

            String npyDataPath = datasetEnum.path + fileNames.get(fileIndex);
            log.info("使用文件：{} (localStart={}, rowsToRead={})", fileNames.get(fileIndex), localStart, rowsToRead);

            try {
                NpyLoader.FloatMatrixSlice slice = NpyLoader.readFloatMatrixSlice(new File(npyDataPath), localStart, rowsToRead);
                if (slice.rows != rowsToRead) {
                    log.warn("Unexpected slice rows. expected={}, actual={}", rowsToRead, slice.rows);
                }
                vectors.addAll(splitArray(slice.data, slice.cols));
            } catch (IOException e) {
                log.error("读取npy文件失败: {} (localStart={}, rowsToRead={})", npyDataPath, localStart, rowsToRead, e);
                throw new RuntimeException("读取npy文件失败: " + npyDataPath + ", cause: " + e.getMessage(), e);
            }

            remaining -= rowsToRead;
            globalPos += rowsToRead;
            fileStartGlobal += rowsInFile;
            fileIndex++;
        }

        log.info("读取文件(可能跨多个文件)，可以使用的数据长度：{}", vectors.size());
        return vectors;
    }

    public static List<JsonObject> providerJsonLinesByDataset(long index, long count, List<String> fileNames, DatasetEnum datasetEnum, List<Long> fileSizeList) {
        if (count <= 0) {
            return Collections.emptyList();
        }
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("count is too large for List: " + count);
        }
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (fileNames == null || fileNames.isEmpty()) {
            log.warn("Dataset fileNames is empty");
            return Collections.emptyList();
        }
        if (fileSizeList == null || fileSizeList.isEmpty()) {
            log.warn("Dataset fileSizeList is empty");
            return Collections.emptyList();
        }

        List<JsonObject> result = new ArrayList<>((int) count);

        long remaining = count;
        long globalPos = index;

        int fileIndex = findIndex(globalPos, fileSizeList);
        if (fileIndex < 0 || fileIndex >= fileNames.size() || fileIndex >= fileSizeList.size()) {
            log.warn("index {} out of dataset range (files={}, sizes={})", index, fileNames.size(), fileSizeList.size());
            return Collections.emptyList();
        }

        long fileStartGlobal = 0;
        for (int i = 0; i < fileIndex; i++) {
            fileStartGlobal += fileSizeList.get(i);
        }

        while (remaining > 0 && fileIndex < fileNames.size() && fileIndex < fileSizeList.size()) {
            long rowsInFile = fileSizeList.get(fileIndex);
            long localStart = globalPos - fileStartGlobal;
            if (localStart < 0) {
                localStart = 0;
            }
            if (localStart >= rowsInFile) {
                fileStartGlobal += rowsInFile;
                fileIndex++;
                continue;
            }

            long available = rowsInFile - localStart;
            int rowsToRead = (int) Math.min(remaining, available);

            String jsonDataPath = datasetEnum.path + fileNames.get(fileIndex);
            log.info("使用JSON文件：{} (localStart={}, rowsToRead={})", fileNames.get(fileIndex), localStart, rowsToRead);

            try {
                List<JsonObject> slice = JsonDatasetLoader.readJsonLines(new File(jsonDataPath), localStart, rowsToRead);
                result.addAll(slice);
            } catch (IOException e) {
                log.error("读取JSON文件失败: {} (localStart={}, rowsToRead={})", jsonDataPath, localStart, rowsToRead, e);
                throw new RuntimeException("读取JSON文件失败: " + jsonDataPath + ", cause: " + e.getMessage(), e);
            }

            remaining -= rowsToRead;
            globalPos += rowsToRead;
            fileStartGlobal += rowsInFile;
            fileIndex++;
        }

        log.info("读取JSON文件(可能跨多个文件)，可以使用的数据长度：{}", result.size());
        return result;
    }

    public static int findIndex(long startId, List<Long> fileSizeList) {
        int fileIndex = 0;
        long countTemp = 0;
        while (fileIndex < fileSizeList.size()) {
            countTemp = countTemp + fileSizeList.get(fileIndex);
            if (countTemp > startId) {
                return fileIndex;
            }
            fileIndex++;
        }
        return fileIndex;
    }

    public static List<List<Float>> splitArray(float[] array, int chunkSize) {
        if (array == null || array.length == 0) {
            return Collections.emptyList();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }

        int chunkCount = (array.length + chunkSize - 1) / chunkSize;
        List<List<Float>> chunks = new ArrayList<>(chunkCount);
        for (int offset = 0; offset < array.length; offset += chunkSize) {
            int size = Math.min(chunkSize, array.length - offset);
            chunks.add(new FloatArrayVectorView(array, offset, size));
        }
        return chunks;
    }

    /**
     * A lightweight {@code List<Float>} view backed by a {@code float[]} slice.
     * It avoids allocating millions of boxed {@link Float} objects.
     */
    private static final class FloatArrayVectorView extends AbstractList<Float> implements RandomAccess {
        private final float[] data;
        private final int offset;
        private final int size;

        private FloatArrayVectorView(float[] data, int offset, int size) {
            this.data = Objects.requireNonNull(data, "data");
            this.offset = offset;
            this.size = size;
        }

        @Override
        public Float get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
            return data[offset + index];
        }

        @Override
        public int size() {
            return size;
        }
    }

    public static String providerConfigFile(String vdcConfigPath){
        StringBuilder contentBuilder = new StringBuilder();
        File file = new File(vdcConfigPath);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append(System.lineSeparator()); // 添加每一行
            }
        } catch (IOException e) {
            e.printStackTrace(); // 处理异常
        }
        return contentBuilder.toString();
    }
}
