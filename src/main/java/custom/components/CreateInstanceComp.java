package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.entity.CreateInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateInstanceResult;
import custom.entity.result.ResultEnum;
import custom.pojo.InstanceInfo;
import custom.utils.CloudServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;

import static custom.BaseTest.*;

@Slf4j
public class CreateInstanceComp {
    public static CreateInstanceResult createInstance(CreateInstanceParams createInstanceParams) {
        // 登录cloudService获取账户信息
        cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(createInstanceParams.getAccountEmail(), createInstanceParams.getAccountPassword());
        // check是否存在同名的实例
        List<InstanceInfo> instanceInfoList =
                CloudServiceUtils.listInstance();
        boolean isExist = false;
        for (InstanceInfo instanceInfo : instanceInfoList) {
            if (instanceInfo.getInstanceName().equalsIgnoreCase(createInstanceParams.getInstanceName())) {
                isExist = true;
                break;
            }
        }
        if (isExist) {
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message("The specified instance name already exists.")
                    .result(ResultEnum.EXCEPTION.result).build()).build();
        }
        // 调用rm-service
        String resp = ResourceManagerServiceUtils.createInstance(createInstanceParams);
        log.info("create instance:" + resp);
        JSONObject jsonObject = JSONObject.parseObject(resp);
        Integer code = jsonObject.getInteger("Code");
        if (code != 0) {
            log.info("create instance failed: " + jsonObject.getString("Message"));
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message(jsonObject.getString("Message"))
                    .result(ResultEnum.EXCEPTION.result).build()).build();
        }
        log.info("Submit create instance success!");
        // 轮询是否建成功
        int waitingTime = 30;
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(waitingTime);
        boolean createSuccess = false;
        while (LocalDateTime.now().isBefore(endTime)) {
            List<InstanceInfo> instanceInfos = CloudServiceUtils.listInstance();
            for (InstanceInfo instanceInfo : instanceInfos) {
                if (instanceInfo.getInstanceName().equalsIgnoreCase(createInstanceParams.getInstanceName())) {
                    createSuccess = true;
                    newInstanceInfo.setInstanceId(instanceInfo.getInstanceId());
                    newInstanceInfo.setUri(instanceInfo.getUri());
                    newInstanceInfo.setInstanceName(instanceInfo.getInstanceName());
                    break;
                }
                log.info("轮询实例是否创建成功：false");
            }
            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException e) {
                log.info(e.getMessage());
            }
        }
        if (!createSuccess) {
            log.info("轮询超时！未监测到实例创建成功！");
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message("轮询超时！未监测到实例创建成功！")
                    .result(ResultEnum.EXCEPTION.result).build()).build();
        }
        // 初始化实例
        if (createInstanceParams.getRoleUse().equalsIgnoreCase("root")) {
            String token = MilvusConnect.provideToken(newInstanceInfo.getUri());
            newInstanceInfo.setToken(token);
        }
        if (createInstanceParams.getRoleUse().equalsIgnoreCase("db_admin")) {
            String token = "db_admin:" + createInstanceParams.getRootPassword();
            newInstanceInfo.setToken(token);
        }
        milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), newInstanceInfo.getToken());

        log.info("创建实例成功：" + newInstanceInfo);
        return CreateInstanceResult.builder()
                .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                .instanceId(newInstanceInfo.getInstanceId())
                .uri(newInstanceInfo.getUri())
                .build();

    }
}
