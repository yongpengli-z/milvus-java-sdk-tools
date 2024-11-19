package custom.components;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import custom.entity.DeleteInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DeleteInstanceResult;
import custom.entity.result.ResultEnum;
import custom.pojo.InstanceInfo;
import custom.utils.CloudServiceTestUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class DeleteInstanceComp {
    public static DeleteInstanceResult deleteInstance(DeleteInstanceParams deleteInstanceParams) {
        // 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            if(deleteInstanceParams.getAccountEmail()==null||deleteInstanceParams.getAccountEmail().equalsIgnoreCase("")){
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            }else{
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(deleteInstanceParams.getAccountEmail(),deleteInstanceParams.getAccountPassword());
            }
        }

        if (deleteInstanceParams.isUseOPSTestApi()) {
            String s = CloudServiceTestUtils.deleteInstance(deleteInstanceParams);
            JSONObject jsonObject = JSON.parseObject(s);
            if (jsonObject.getInteger("Code") == 0) {
                boolean isExist = true;
                long startLoadTime = System.currentTimeMillis();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
                while (isExist && LocalDateTime.now().isBefore(endTime)) {
                    List<InstanceInfo> instanceInfoList =
                            CloudServiceUtils.listInstance();
                    for (InstanceInfo instanceInfo : instanceInfoList) {
                        if (!instanceInfo.getInstanceId().equalsIgnoreCase(newInstanceInfo.getInstanceId())) {
                            isExist = false;
                            break;
                        }
                    }
                    try {
                        Thread.sleep(1000 * 10);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }
                long endLoadTime = System.currentTimeMillis();
                log.info("Delete instance cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
                if (!isExist) {
                    return DeleteInstanceResult.builder()
                            .commonResult(CommonResult.builder()
                                    .result(ResultEnum.SUCCESS.result).build())
                            .costSeconds((int) ((endLoadTime - startLoadTime) / 1000.00)).build();
                }
                return DeleteInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.WARNING.result)
                                .message("Delete instance [" + newInstanceInfo.getInstanceId() + "] time out!").build())
                        .build();

            }
            return DeleteInstanceResult.builder()
                    .commonResult(CommonResult.builder().message(jsonObject.getString("Message"))
                            .result(ResultEnum.WARNING.result).build()).build();
        } else {
            String s = ResourceManagerServiceUtils.deleteInstance(deleteInstanceParams);
            JSONObject jsonObject = JSON.parseObject(s);
            if (jsonObject.getInteger("Code") == 0) {
                boolean isExist = true;
                long startLoadTime = System.currentTimeMillis();
                LocalDateTime endTime = LocalDateTime.now().plusMinutes(30);
                while (isExist && LocalDateTime.now().isBefore(endTime)) {
                    List<InstanceInfo> instanceInfoList =
                            CloudServiceUtils.listInstance();
                    for (InstanceInfo instanceInfo : instanceInfoList) {
                        if (!instanceInfo.getInstanceId().equalsIgnoreCase(newInstanceInfo.getInstanceId())) {
                            isExist = false;
                            break;
                        }
                    }
                    try {
                        Thread.sleep(1000 * 10);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }
                long endLoadTime = System.currentTimeMillis();
                log.info("Delete instance cost " + (endLoadTime - startLoadTime) / 1000.00 + " seconds");
                if (!isExist) {
                    return DeleteInstanceResult.builder()
                            .commonResult(CommonResult.builder()
                                    .result(ResultEnum.SUCCESS.result).build())
                            .costSeconds((int) ((endLoadTime - startLoadTime) / 1000.00)).build();
                }
                return DeleteInstanceResult.builder()
                        .commonResult(CommonResult.builder()
                                .result(ResultEnum.WARNING.result)
                                .message("Delete instance [" + newInstanceInfo.getInstanceId() + "] time out!").build())
                        .build();

            }
            return DeleteInstanceResult.builder()
                    .commonResult(CommonResult.builder().message(jsonObject.getString("Message"))
                            .result(ResultEnum.WARNING.result).build()).build();
        }
    }
}
