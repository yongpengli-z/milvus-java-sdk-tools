package custom.components;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.common.InstanceStatusEnum;
import custom.entity.CreateInstanceParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateInstanceResult;
import custom.entity.result.ResultEnum;
import custom.pojo.InstanceInfo;
import custom.utils.CloudOpsServiceUtils;
import custom.utils.CloudServiceUtils;
import custom.utils.InfraServiceUtils;
import custom.utils.ResourceManagerServiceUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static custom.BaseTest.*;

@Slf4j
public class CreateInstanceComp {
    public static CreateInstanceResult createInstance(CreateInstanceParams createInstanceParams) {
        // 登录cloudService获取账户信息 // 检查账号
        if (cloudServiceUserInfo.getUserId() == null || cloudServiceUserInfo.getUserId().equalsIgnoreCase("")) {
            if (createInstanceParams.getAccountEmail() == null || createInstanceParams.getAccountEmail().equalsIgnoreCase("")) {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(null, null);
            } else {
                cloudServiceUserInfo = CloudServiceUtils.queryUserIdOfCloudService(createInstanceParams.getAccountEmail(), createInstanceParams.getAccountPassword());
            }
        }        // check是否存在同名的实例
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
        // image重新获取
        String latestImageByKeywords;
        if (createInstanceParams.getDbVersion().equalsIgnoreCase("latest-release")) {
            List<String> strings = ComponentSchedule.queryReleaseImage();
            latestImageByKeywords = strings.get(0);
            createInstanceParams.setDbVersion(latestImageByKeywords.substring(0, latestImageByKeywords.indexOf("(")));
        } else {
            latestImageByKeywords = CloudOpsServiceUtils.getLatestImageByKeywords(createInstanceParams.getDbVersion());
            createInstanceParams.setDbVersion(latestImageByKeywords.substring(0, latestImageByKeywords.indexOf("(")));
        }
        // 调用rm-service
        String resp = ResourceManagerServiceUtils.createInstance(createInstanceParams);
        log.info("create instance:" + resp);
        JSONObject jsonObject = JSONObject.parseObject(resp);
        Integer code = jsonObject.getInteger("Code");
        if (code != 0) {
            log.info("create instance failed: " + jsonObject.getString("Message"));
            ComponentSchedule.initInstanceStatus("--", "--", latestImageByKeywords, InstanceStatusEnum.CREATE_FAILED.code);
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message(jsonObject.getString("Message"))
                    .result(ResultEnum.EXCEPTION.result).build()).build();
        }
        String instanceId = jsonObject.getJSONObject("Data").getString("InstanceId");
        log.info("Submit create instance success!");
        ComponentSchedule.initInstanceStatus(instanceId, "", latestImageByKeywords, InstanceStatusEnum.CREATING.code);
        // 判断是否需要独占
        if (createInstanceParams.isBizCritical()) {
            HashMap<String, String> labels = new HashMap<>();
            labels.put("biz-critical", "true");
            String s = ResourceManagerServiceUtils.updateLabel(instanceId, labels);
            log.info("update labels: " + s);
        }
        // 轮询是否建成功
        int waitingTime = 30;
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(waitingTime);
        boolean createSuccess = false;
        while (LocalDateTime.now().isBefore(endTime)) {
            List<InstanceInfo> instanceInfos = CloudServiceUtils.listInstance();
            if (instanceInfos.size() > 0) {
                for (InstanceInfo instanceInfo : instanceInfos) {
                    if (instanceInfo.getInstanceName().equalsIgnoreCase(createInstanceParams.getInstanceName())) {
                        createSuccess = true;
                        newInstanceInfo.setInstanceId(instanceInfo.getInstanceId());
                        newInstanceInfo.setUri(instanceInfo.getUri());
                        newInstanceInfo.setInstanceName(instanceInfo.getInstanceName());
                        break;
                    }
                }
            }
            if (createSuccess) {
                break;
            }
            log.info("waiting for create instance...");
            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException e) {
                log.info(e.getMessage());
            }
        }
        if (!createSuccess) {
            log.info("轮询超时！未监测到实例创建成功！");
            // 上报结果
            ComponentSchedule.updateInstanceStatus(instanceId, "--", latestImageByKeywords, InstanceStatusEnum.CREATE_FAILED.code);
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message("轮询超时！未监测到实例创建成功！")
                    .result(ResultEnum.EXCEPTION.result).build()).build();
        }

        CreateInstanceResult createInstanceResult = CreateInstanceResult.builder().build();
        // 创建成功，检查是否是独占
        if (createInstanceParams.isBizCritical()) {
            String milvusPodLabels = InfraServiceUtils.getMilvusPodLabels(envEnum.cluster, instanceId);
            log.info("InfraServiceUtils.getMilvusPodLabels:" + milvusPodLabels);
            JSONObject jsonObject1 = JSONObject.parseObject(milvusPodLabels);
            JSONObject data = jsonObject1.getJSONObject("data");
            if (data == null) {
                ComponentSchedule.updateInstanceStatus(newInstanceInfo.getInstanceId(), newInstanceInfo.getUri(), latestImageByKeywords, InstanceStatusEnum.RUNNING.code);
                return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                                .message("实例创建成功！但未独占！！！")
                                .result(ResultEnum.EXCEPTION.result).build())
                        .uri(newInstanceInfo.getUri())
                        .instanceId(newInstanceInfo.getInstanceId()).build();
            } else {
                if (data.containsKey("biz-critical")) {
                    log.info("监测到实例已经独占！");
                    createInstanceResult.setAlone(true);
                } else {
                    createInstanceResult.setAlone(false);
                }
            }
        } else {
            createInstanceResult.setAlone(false);
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
        milvusClientV1 = MilvusConnect.createMilvusClientV1(newInstanceInfo.getUri(), newInstanceInfo.getToken());

        log.info("创建实例成功：" + newInstanceInfo);
        ComponentSchedule.updateInstanceStatus(newInstanceInfo.getInstanceId(), newInstanceInfo.getUri(), latestImageByKeywords, InstanceStatusEnum.RUNNING.code);
        createInstanceResult.setCommonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build());
        createInstanceResult.setInstanceId(newInstanceInfo.getInstanceId());
        createInstanceResult.setUri(newInstanceInfo.getUri());
        return createInstanceResult;

    }
}
