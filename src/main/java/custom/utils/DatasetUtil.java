package custom.utils;

import custom.common.DatasetEnum;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class DatasetUtil {

    public static List<String> providerFileNames(String filePrefixName,String path){
        List<String> fileNameList = new ArrayList<>();
        File file = new File(path);
        // 检查目录是否存在且是一个目录
        if (file.exists() && file.isDirectory()) {
            // 获取目录下的所有文件和子目录
            File[] files = file.listFiles();

            // 遍历并打印包含指定关键字的文件名
            if (files != null) {
                for (File fileItem : files) {
                    if (fileItem.isFile() && fileItem.getName().contains(filePrefixName)) {
                        fileNameList.add(fileItem.getName());
                    }
                }
            }
        } else {
            log.error("指定的路径不是一个有效的目录.");
        }
        // 排序
        fileNameList.sort(Comparator.comparingInt(x->
                        Integer.parseInt(x.substring(x.lastIndexOf("_")+1,x.lastIndexOf("."))))
                );
        return fileNameList;
    }

    public static List<String> providerFileNamesByDataset(DatasetEnum datasetEnum){

        return providerFileNames(datasetEnum.prefixName, datasetEnum.path);
    }


    public static List<Float> providerFloatVectorByDataset(long index,String filePrefixName,String path) {

        return null;
    }

    public static void main(String[] args) {

        List<String> strings = providerFileNamesByDataset(DatasetEnum.GIST);
        System.out.println(strings);

    }
}
