package custom.components;

import custom.common.DatasetEnum;
import custom.entity.WaitParams;
import custom.utils.DatasetUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
public class WaitComp {
    public static void wait(WaitParams waitParams){
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
