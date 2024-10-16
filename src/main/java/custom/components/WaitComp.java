package custom.components;

import custom.common.DatasetEnum;
import custom.entity.WaitParams;
import custom.utils.DatasetUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

@Slf4j
public class WaitComp {
    public static void wait(WaitParams waitParams){
        try {
            List<String> strings = DatasetUtil.providerFileNames(DatasetEnum.GIST);
            List<Float> floats = DatasetUtil.providerFloatVectorByDataset(199999, strings, DatasetEnum.GIST);
            System.out.println(floats);
            System.out.println(floats.size());

            log.info("Waiting "+ waitParams.getWaitMinutes()+" minutes...");
            Thread.sleep(waitParams.getWaitMinutes()*60*1000);
        } catch (InterruptedException e) {
            log.warn("Wait exception:" + e.getMessage());
        }
    }
}
