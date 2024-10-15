package custom.components;

import custom.entity.WaitParams;
import lombok.extern.slf4j.Slf4j;

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
