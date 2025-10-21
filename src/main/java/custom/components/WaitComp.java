package custom.components;

import custom.common.DatasetEnum;
import custom.entity.WaitParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.WaitResult;
import custom.utils.DatasetUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;

@Slf4j
public class WaitComp {
    public static WaitResult wait(WaitParams waitParams) {
        CommonResult commonResult;
        try {
            log.info("Waiting " + waitParams.getWaitMinutes() + " minutes...");
            Thread.sleep(waitParams.getWaitMinutes() * 60 * 1000);
            commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
        } catch (InterruptedException e) {
            log.warn("Wait exception:" + e.getMessage());
            commonResult = CommonResult.builder().result(ResultEnum.EXCEPTION.result).build();
        }
        return WaitResult.builder()
                .waitMinutes(waitParams.getWaitMinutes())
                .commonResult(commonResult).build();
    }
}
