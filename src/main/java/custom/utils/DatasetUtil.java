package custom.utils;

import custom.common.DatasetEnum;
import lombok.extern.slf4j.Slf4j;
import org.openlca.npy.Npy;
import org.openlca.npy.arrays.NpyArray;

import java.io.File;
import java.util.*;

@Slf4j
public class DatasetUtil {

    public static List<String> providerFileNames(DatasetEnum datasetEnum) {
        List<String> fileNameList = new ArrayList<>();
        File file = new File(datasetEnum.path);
        // 检查目录是否存在且是一个目录
        if (file.exists() && file.isDirectory()) {
            // 获取目录下的所有文件和子目录
            File[] files = file.listFiles();
            // 遍历并打印包含指定关键字的文件名
            if (files != null) {
                for (File fileItem : files) {
                    if (fileItem.isFile() && fileItem.getName().contains(datasetEnum.prefixName)) {
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

    public static List<Long> providerFileSize(List<String> filesNames, DatasetEnum datasetEnum) {
        List<Long> fileSizeList = new ArrayList<>();
        int i = 0;
        while (i < filesNames.size()) {
            String npyDataPath = datasetEnum.path + filesNames.get(i);
            File file = new File(npyDataPath);
            NpyArray<?> npyArray = Npy.read(file);
            fileSizeList.add((long) npyArray.shape()[0]);
            i++;
        }
        return fileSizeList;
    }

    public static List<List<Float>> providerFloatVectorByDataset(long index, long count, List<String> fileNames, DatasetEnum datasetEnum, List<Long> fileSizeList) {
        List<List<Float>> floatList = new ArrayList<>();
       /* long countIndex = 0;
        long countIndexTemp = countIndex;
        int i = 0;
        while (i < fileNames.size()) {
            String npyDataPath = datasetEnum.path + fileNames.get(i);
            File file = new File(npyDataPath);
            NpyArray<?> npyArray = Npy.read(file);
            countIndex += npyArray.shape()[0];
            if (countIndex > index) {
                System.out.println("查到数据，from " + fileNames.get(i));
                float[] floatData = (float[]) npyArray.data();
                List<List<Float>> floats = splitArray(floatData, npyArray.shape()[1]);
                floatList = floats.subList((int) (index - countIndexTemp), (int) (index - countIndexTemp + count));
                break;
            }
            countIndexTemp = countIndex;
            i++;
        }*/
        int fileIndex = findIndex(index, fileSizeList);
        String npyDataPath = datasetEnum.path + fileNames.get(fileIndex);
        File file = new File(npyDataPath);
        NpyArray<?> npyArray = Npy.read(file);
        float[] floatData = (float[]) npyArray.data();
//        List<List<Float>> floats = splitArray(floatData, npyArray.shape()[1]);
        long tempIndex = 0;
        if (fileIndex > 0) {
            tempIndex = fileSizeList.stream().limit(fileIndex)
                    .mapToLong(Long::longValue)
                    .sum();
        }
        // 截取需要的一段
        float[] subArray= Arrays.copyOfRange(floatData, (int) ((index - tempIndex ) * npyArray.shape()[1]), (int) ((index - tempIndex + count) * npyArray.shape()[1]));
        floatList=splitArray(subArray,npyArray.shape()[1]);
//        floatList = floats.subList((int) (index - tempIndex), (int) (index - tempIndex + count));
        return floatList;
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
        List<List<Float>> chunks = new ArrayList<>();
        List<Float> chunk;

        for (int i = 0; i < array.length; i += chunkSize) {
            chunk = new ArrayList<>();
            for (int j = i; j < i + chunkSize && j < array.length; j++) {
                chunk.add(array[j]);
            }
            chunks.add(chunk);
        }

        return chunks;
    }

    public static void main(String[] args) {
        List<String> strings = providerFileNames(DatasetEnum.GIST);
        List<Long> longs = providerFileSize(strings, DatasetEnum.GIST);
        System.out.println(longs);
        List<List<Float>> lists = providerFloatVectorByDataset(9999, 1, strings, DatasetEnum.GIST, longs);
        System.out.println(lists.size());
        System.out.println(lists.get(0));

    }
}
