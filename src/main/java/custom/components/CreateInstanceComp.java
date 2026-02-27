package custom.components;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static custom.BaseTest.*;

@Slf4j
public class CreateInstanceComp {
    public static CreateInstanceResult createInstance(CreateInstanceParams createInstanceParams) {
        LocalDateTime startTime = LocalDateTime.now();
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
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message("The specified instance name already exists.")
                    .result(ResultEnum.EXCEPTION.result).build())
                    .createCostSeconds(costSeconds).build();
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
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message(jsonObject.getString("Message"))
                    .result(ResultEnum.EXCEPTION.result).build())
                    .createCostSeconds(costSeconds).build();
        }
        String instanceId = jsonObject.getJSONObject("Data").getString("InstanceId");
        log.info("Submit create instance success!");
        ComponentSchedule.initInstanceStatus(instanceId, "", latestImageByKeywords, InstanceStatusEnum.CREATING.code);
        // 判断是否需要重保
        if (createInstanceParams.isBizCritical()) {
            HashMap<String, String> labels = new HashMap<>();
            labels.put("biz-critical", "true");
            String s = ResourceManagerServiceUtils.updateLabel(instanceId);
            log.info("update biz-critical: " + s);
        }
        // 判断是否需要独占
        if (createInstanceParams.isMonopolized()) {
            String s = ResourceManagerServiceUtils.updateQNMonopoly(instanceId);
            log.info("update monopolized: " + s);
        }
        // 判断是否需要修改replica（创建接口的replica字段可能不生效，需要通过update_replicas接口设置）
        if (createInstanceParams.getReplica() > 1) {
            String s = ResourceManagerServiceUtils.updateReplica(instanceId, createInstanceParams.getReplica(),
                    Lists.newArrayList("queryNode"));
            log.info("update queryNode replica: " + s);
        }
        //判断是否需要修改streamingNode配置--2.6 image 才行
        if (latestImageByKeywords.contains("2.6") && createInstanceParams.getStreamingNodeParams() != null) {
            // 判断是否需要修改replica
            if (createInstanceParams.getStreamingNodeParams().getReplicaNum() > 0) {
                String s = ResourceManagerServiceUtils.updateReplica(instanceId, createInstanceParams.getStreamingNodeParams().getReplicaNum(),
                        Lists.newArrayList("streamingNode"));
                log.info("update replica: " + s);
            }
            // 判断是否需要修改limits
            if (createInstanceParams.getStreamingNodeParams().getCpu() != null && !createInstanceParams.getStreamingNodeParams().getCpu().equalsIgnoreCase("")) {
                String streamingNode = ResourceManagerServiceUtils.updateLimits(instanceId,
                        createInstanceParams.getStreamingNodeParams().getCpu(),
                        createInstanceParams.getStreamingNodeParams().getMemory(),
                        createInstanceParams.getStreamingNodeParams().getDisk(),
                        Lists.newArrayList("streamingNode"));
                log.info("update limit: " + streamingNode);
            }
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
            int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            return CreateInstanceResult.builder().commonResult(CommonResult.builder()
                    .message("轮询超时！未监测到实例创建成功！")
                    .result(ResultEnum.EXCEPTION.result).build())
                    .createCostSeconds(costSeconds).build();
        }

        int costSeconds = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
        CreateInstanceResult createInstanceResult = CreateInstanceResult.builder()
                .createCostSeconds(costSeconds).build();
        // 创建成功，检查是否是重保
        createInstanceResult.setBizCritical(createInstanceParams.isBizCritical());
        // 创建成功，检查是否是独占
        createInstanceResult.setMonopolized(createInstanceParams.isMonopolized());
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
