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
            log.info("Waiting "+ waitParams.getWaitMinutes()+" minutes...");
            Thread.sleep(waitParams.getWaitMinutes()*60*1000);
        } catch (InterruptedException e) {
            log.warn("Wait exception:" + e.getMessage());
        }
    }
}
