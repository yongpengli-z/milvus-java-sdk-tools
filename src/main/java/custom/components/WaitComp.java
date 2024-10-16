package custom.components;

import custom.common.DatasetEnum;
import custom.entity.WaitParams;
import custom.utils.DatasetUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class WaitComp {
    public static void wait(WaitParams waitParams){
        String currentDir = System.getProperty("user.dir");
        // 输出当前目录
        System.out.println("当前工作目录: " + currentDir);
        File rootDirectory = new File("/");

        // 检查目录是否存在且是一个目录
        if (rootDirectory.exists() && rootDirectory.isDirectory()) {
            // 获取根目录下的所有文件和子目录
            File[] files = rootDirectory.listFiles();

            // 遍历并打印子目录名
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        System.out.println(file.getName());
                    }
                }
            }
        } else {
            System.out.println("指定的路径不是一个有效的目录.");
        }
        try {
            log.info("Gist数据集"+DatasetUtil.providerFileNamesByDataset(DatasetEnum.GIST));
            log.info("Sift数据集"+DatasetUtil.providerFileNamesByDataset(DatasetEnum.SIFT));
            log.info("Laion数据集"+DatasetUtil.providerFileNamesByDataset(DatasetEnum.LAION));
            log.info("Deep数据集"+DatasetUtil.providerFileNamesByDataset(DatasetEnum.DEEP));

            log.info("Waiting "+ waitParams.getWaitMinutes()+" minutes...");
            Thread.sleep(waitParams.getWaitMinutes()*60*1000);
        } catch (InterruptedException e) {
            log.warn("Wait exception:" + e.getMessage());
        }
    }
}
