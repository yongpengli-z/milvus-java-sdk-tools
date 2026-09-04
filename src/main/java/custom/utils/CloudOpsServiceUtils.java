package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.entity.AlterInstanceIndexClusterParams;
import custom.entity.RestoreBackupParams;
import custom.entity.RollingUpgradeParams;
import custom.entity.SwitchInstanceMqParams;
import custom.entity.UpdateWoodpeckerImageParams;
import custom.pojo.IndexPoolInfo;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static custom.BaseTest.envConfig;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class CloudOpsServiceUtils {
    private static Map<String, String> buildCloudOpsAuthHeader() {
        Map<String, String> header = new HashMap<>();
        String token = envConfig.getCloudOpsServiceToken();
        header.put("sa_token", token);
        header.put("cookie", "sa_token=" + token);
        return header;
    }

    /**
     * Submit a rolling upgrade through the same Cloud Ops endpoint used by the Ops console.
     */
    public static String rollingUpgrade(String instanceId, RollingUpgradeParams rollingUpgradeParams) {
        String url = envConfig.getCloudOpsServiceHost()
                + "/api/v1/ops/resource/custInstance/rolling_upgrade/"
                + instanceId + "/" + rollingUpgradeParams.getTargetDbVersion()
                + "?needBackup=false"
                + "&force=true"
                + "&forceRestart=" + rollingUpgradeParams.isForceRestart()
                + "&syncMilvusConfig=true"
                + "&syncHookConfig=true"
                + "&syncDeploymentConfig=true";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        String response = HttpClientUtils.doPost(url, header, null);
        log.info("rolling upgrade instance {}: {}", instanceId, response);
        return response;
    }

    public static String listDBVersionByKeywords(String keywords,int insType) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/release_version";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> paramsDB = new HashMap<>();
        paramsDB.put("currentPage", "1");
        paramsDB.put("pageSize", "100");
        paramsDB.put("insType", String.valueOf(insType));
        paramsDB.put("dbVersion", keywords);
        paramsDB.put("regionId", envConfig.getRegionId());
        String s = HttpClientUtils.doGet(url, header, paramsDB);
        log.info("listDBVersionByKeywords:" + s);
        return s;
    }

    public static String listTagByKeywords(String keywords,int insType) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/release_version";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> paramsTag = new HashMap<>();
        paramsTag.put("currentPage", "1");
        paramsTag.put("pageSize", "100");
        paramsTag.put("insType", String.valueOf(insType));
        paramsTag.put("tag", keywords);
        paramsTag.put("regionId", envConfig.getRegionId());
        String s = HttpClientUtils.doGet(url, header, paramsTag);
        log.info("listTagByKeywords:" + s);
        return s;
    }

    /**
     * 按关键字查询最新镜像。
     * <p>
     * 支持逗号分隔的多条件，例如 {@code "v3.0,nightly"}：每个条件分别按 dbVersion 和 tag
     * 走后端模糊查询，合并去重后，本地过滤出同时包含所有条件的镜像，返回第一条。
     */
    public static String getLatestImageByKeywords(String keywords,int insType) {
        // 拆分多条件（逗号分隔），忽略空白
        List<String> keywordList = new ArrayList<>();
        for (String keyword : keywords.split(",")) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                keywordList.add(trimmed);
            }
        }
        List<String> lists = new ArrayList<>();
        for (String keyword : keywordList) {
            JSONObject jsonResponse = JSON.parseObject(listDBVersionByKeywords(keyword,insType));
            JSONObject jsonResponse2 = JSON.parseObject(listTagByKeywords(keyword,insType));
            // 请求失败（非200或异常）时 doGet 返回空串，parseObject 结果为 null，这里直接抛出带上下文的异常
            if (jsonResponse == null || jsonResponse.getJSONObject("data") == null
                    || jsonResponse2 == null || jsonResponse2.getJSONObject("data") == null) {
                throw new RuntimeException("查询镜像版本接口失败(release_version 返回为空或无 data)，关键字: " + keyword
                        + ", insType: " + insType + ", regionId: " + envConfig.getRegionId()
                        + "，请检查 cloud-ops 服务是否可用");
            }
            // 获取data-list
            JSONArray jsonArray = jsonResponse.getJSONObject("data").getJSONArray("list");
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String dbVersion = jsonObject.getString("dbVersion");
                String tag = jsonObject.getString("tag");
                lists.add(dbVersion + "(" + tag + ")");
            }
            // 获取按照tag筛选的
            JSONArray jsonArray2 = jsonResponse2.getJSONObject("data").getJSONArray("list");
            for (int i = 0; i < jsonArray2.size(); i++) {
                JSONObject jsonObject = jsonArray2.getJSONObject(i);
                String dbVersion = jsonObject.getString("dbVersion");
                String tag = jsonObject.getString("tag");
                lists.add(dbVersion + "(" + tag + ")");
            }
        }

        // 剔除重复
        List<String> collect = lists.stream().distinct().collect(Collectors.toList());
        // 多条件时，本地过滤出同时包含所有条件的镜像（对 dbVersion(tag) 整体做忽略大小写的包含匹配）
        if (keywordList.size() > 1) {
            collect = collect.stream()
                    .filter(s -> {
                        String lower = s.toLowerCase();
                        return keywordList.stream().allMatch(k -> lower.contains(k.toLowerCase()));
                    })
                    .collect(Collectors.toList());
        }
        // 无匹配时抛出明确提示，避免调用方对空串做 substring 出现越界异常
        return collect.stream().findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "未找到匹配 [" + keywords + "] 的镜像(image)，请检查输入的版本关键字是否正确"));
    }

    public static String listRunningIndexPool() {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/resource/index/cluster";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> params = new HashMap<>();
        params.put("currentPage", "1");
        params.put("pageSize", "100");
        params.put("regionId", envConfig.getRegionId());
//        params.put("status", "1");
        params.put("enable", "true");
        String s = HttpClientUtils.doGet(url, header, params);
        log.info("list index pool" + s);
        return s;
    }

    public static IndexPoolInfo providerIndexPool(int indexClusterId) {
        String s = listRunningIndexPool();
        JSONObject jsonObject = JSONObject.parseObject(s);
        JSONArray jsonArray = jsonObject.getJSONObject("data").getJSONArray("list");
        IndexPoolInfo indexPoolInfo = new IndexPoolInfo();
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject1 = jsonArray.getJSONObject(i);
            if (jsonObject1.getInteger("id") == indexClusterId) {
                indexPoolInfo.setId(jsonObject1.getInteger("id"));
                indexPoolInfo.setRegionId(jsonObject1.getString("regionId"));
                indexPoolInfo.setK8sCluster(jsonObject1.getString("k8sCluster"));
                indexPoolInfo.setK8sNamespace(jsonObject1.getString("k8sNamespace"));
                indexPoolInfo.setName(jsonObject1.getString("name"));
                indexPoolInfo.setImageTag(jsonObject1.getString("imageTag"));
                indexPoolInfo.setWorkerImageTag(jsonObject1.getString("workerImageTag"));
                indexPoolInfo.setIndexTypes(jsonObject1.getJSONArray("indexTypes").toJavaList(Integer.class));
                indexPoolInfo.setArchitecture(jsonObject1.getInteger("architecture"));
                indexPoolInfo.setDomain(jsonObject1.getString("domain"));
                indexPoolInfo.setPort(jsonObject1.getInteger("port"));
                indexPoolInfo.setStatus(jsonObject1.getInteger("status"));
                indexPoolInfo.setCheckSchedule(jsonObject1.getString("checkSchedule"));
                indexPoolInfo.setFreeNum(jsonObject1.getInteger("freeNum"));
                indexPoolInfo.setMaxIndexNode(jsonObject1.getInteger("maxIndexNode"));
                indexPoolInfo.setMaxWaitingTask(jsonObject1.getInteger("maxWaitingTask"));
                indexPoolInfo.setDescription(jsonObject1.getString("description"));
                indexPoolInfo.setScalingStrategy(jsonObject1.getInteger("scalingStrategy"));
                indexPoolInfo.setMinFreeNum(jsonObject1.getString("minFreeNum"));
                indexPoolInfo.setFreePercent(jsonObject1.getString("freePercent"));
                indexPoolInfo.setMaxWaitingTimeSeconds(jsonObject1.getInteger("maxWaitingTimeSeconds"));
                indexPoolInfo.setArchitecture(jsonObject1.getInteger("architecture"));
                indexPoolInfo.setFreeSlots(jsonObject1.getInteger("freeSlots"));
                indexPoolInfo.setMaxSlots(jsonObject1.getInteger("maxSlots"));
                indexPoolInfo.setStrideSlots(jsonObject1.getInteger("strideSlots"));
                indexPoolInfo.setWorkerRole(jsonObject1.getInteger("workerRole"));
                JSONArray workerSpecs = jsonObject1.getJSONArray("workerSpecs");
                List<IndexPoolInfo.WorkerSpec> workerSpecsList = new ArrayList<>();
                for (int i1 = 0; i1 < workerSpecs.size(); i1++) {
                    IndexPoolInfo.WorkerSpec workerSpec = new IndexPoolInfo.WorkerSpec();
                    JSONObject specsJSONObject = workerSpecs.getJSONObject(i1);
                    workerSpec.setId(specsJSONObject.getInteger("id"));
                    workerSpec.setIndexClusterId(specsJSONObject.getInteger("indexClusterId"));
                    workerSpec.setMaxSlots(specsJSONObject.getInteger("maxSlots"));
                    workerSpec.setRequestsCpu(specsJSONObject.getString("requestsCpu"));
                    workerSpec.setRequestsMemory(specsJSONObject.getString("requestsMemory"));
                    workerSpec.setLimitsCpu(specsJSONObject.getString("limitsCpu"));
                    workerSpec.setLimitsMemory(specsJSONObject.getString("limitsMemory"));
                    workerSpec.setEnable(specsJSONObject.getBoolean("enable"));
                    workerSpecsList.add(workerSpec);
                }
                indexPoolInfo.setWorkerSpecs(workerSpecsList);
                break;
            }
        }
        log.info("current index pool :" + indexPoolInfo);
        return indexPoolInfo;
    }

    public static String updateIndexPool(IndexPoolInfo indexPoolInfo) {
        log.info("update index pool params:" + JSONObject.toJSONString(indexPoolInfo));
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/resource/index/cluster";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        String s = HttpClientUtils.doPut(url, header, JSONObject.toJSONString(indexPoolInfo));
        log.info("updateIndexPool:" + s);
        return s;
    }

    public static String alterIndexCluster(AlterInstanceIndexClusterParams alterInstanceIndexClusterParams) {
        String instanceId = alterInstanceIndexClusterParams.getInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : alterInstanceIndexClusterParams.getInstanceId();
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/resource/index/cluster/instance/alterCluster";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", instanceId);
        body.put("newClusterId", alterInstanceIndexClusterParams.getIndexClusterId());
        body.put("regionId", envConfig.getRegionId());
        log.info("alterIndexCluster req:" + JSON.toJSONString(body));
        String s = HttpClientUtils.doPostJson(url, header, JSON.toJSONString(body));
        log.info("alter instance index cluster:" + s);
        return s;
    }

    public static String restartInstance(String instanceId) {
        String instanceIdTemp = (instanceId == null || instanceId.equalsIgnoreCase("")) ? newInstanceInfo.getInstanceId() : instanceId;
        String url = envConfig.getCloudOpsServiceHost()
                + "/api/v1/ops/resource/custInstance/rolling_restart/"
                + instanceIdTemp
                + "?force=true"
                + "&syncMilvusConfig=true"
                + "&syncHookConfig=true"
                + "&syncDeploymentConfig=true";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        String s = HttpClientUtils.doPost(url, header, null);
        log.info("ops restart instance {}: {}", instanceIdTemp, s);
        return s;
    }

    public static String switchInstanceMq(String instanceId, String regionId, SwitchInstanceMqParams params) {
        String url = envConfig.getCloudOpsServiceHost()
                + "/api/v1/ops/resource/instance/mq-transfer/switch";
        Map<String, String> header = buildCloudOpsAuthHeader();
        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", instanceId);
        body.put("regionId", regionId);
        body.put("targetMqType", params.getTargetMqType());
        if (params.getTargetWoodpeckerId() != null && !params.getTargetWoodpeckerId().isEmpty()) {
            body.put("targetWoodpeckerId", params.getTargetWoodpeckerId());
        }
        log.info("switch instance mq req:" + JSON.toJSONString(body));
        String s = HttpClientUtils.doPostJson(url, header, JSON.toJSONString(body));
        log.info("switch instance mq:" + s);
        return s;
    }

    public static String previewSwitchInstanceMq(String instanceId, String regionId, SwitchInstanceMqParams params) {
        String url = envConfig.getCloudOpsServiceHost()
                + "/api/v1/ops/resource/instance/mq-transfer/preview";
        Map<String, String> header = buildCloudOpsAuthHeader();
        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", instanceId);
        body.put("regionId", regionId);
        body.put("targetMqType", params.getTargetMqType());
        if (params.getTargetWoodpeckerId() != null && !params.getTargetWoodpeckerId().isEmpty()) {
            body.put("targetWoodpeckerId", params.getTargetWoodpeckerId());
        }
        log.info("preview switch instance mq req:" + JSON.toJSONString(body));
        String s = HttpClientUtils.doPostJson(url, header, JSON.toJSONString(body));
        log.info("preview switch instance mq:" + s);
        return s;
    }

    public static String cleanupInstanceMqTopics(String instanceId, String regionId, String transferTaskId) {
        String url = envConfig.getCloudOpsServiceHost()
                + "/api/v1/ops/resource/instance/mq-transfer/cleanup";
        Map<String, String> header = buildCloudOpsAuthHeader();
        Map<String, Object> body = new HashMap<>();
        body.put("instanceId", instanceId);
        body.put("regionId", regionId);
        body.put("transferTaskId", transferTaskId);
        log.info("cleanup instance mq topics req:" + JSON.toJSONString(body));
        String s = HttpClientUtils.doPostJson(url, header, JSON.toJSONString(body));
        log.info("cleanup instance mq topics:" + s);
        return s;
    }

    public static String upgradeWoodpeckerImage(String regionId, UpdateWoodpeckerImageParams params) {
        String url = envConfig.getCloudOpsServiceHost()
                + "/api/v1/ops/resource/woodpecker/cluster/upgrade"
                + "?regionId=" + urlEncode(regionId)
                + "&woodpeckerId=" + urlEncode(params.getWoodpeckerId())
                + "&newImageTag=" + urlEncode(params.getNewImageTag());
        Map<String, String> header = buildCloudOpsAuthHeader();
        log.info("upgrade woodpecker image req: regionId={}, woodpeckerId={}, newImageTag={}",
                regionId, params.getWoodpeckerId(), params.getNewImageTag());
        String s = HttpClientUtils.doPost(url, header, null);
        log.info("upgrade woodpecker image:" + s);
        return s;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public static String restoreBackup(RestoreBackupParams restoreBackupParams) {
        String toInstanceId = restoreBackupParams.getToInstanceId();
        String instanceId = (toInstanceId == null || toInstanceId.isEmpty()) ? newInstanceInfo.getInstanceId() : toInstanceId;
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/restore/restore_backup";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, Object> body = new HashMap<>();
        body.put("backupId", restoreBackupParams.getBackupId());
        body.put("fromInstanceId", restoreBackupParams.getFromInstanceId());
        body.put("notChangeStatus", restoreBackupParams.isNotChangeStatus());
        body.put("restorePolicy", restoreBackupParams.getRestorePolicy());
        body.put("skipCreateCollection", restoreBackupParams.isSkipCreateCollection());
        body.put("toInstanceId", instanceId);
        body.put("truncateBinlogByTs", restoreBackupParams.isTruncateBinlogByTs());
        body.put("withRBAC", restoreBackupParams.isWithRBAC());
        String s = HttpClientUtils.doPostJson(url, header, JSON.toJSONString(body));
        log.info("restore backup:" + s);
        return s;
    }

    public static String queryInstanceIdByBackupId(String backupId) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/backup/total_page";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> body = new HashMap<>();
        body.put("currentPage", "1");
        body.put("pageSize", "20");
        body.put("backupId", backupId);
        String s = HttpClientUtils.doGet(url, header, body);
        log.info("query backup info:" + s);
        return s;
    }

    public static String queryRestoreBackupStatus(String jobId) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/ops/restore/total_page";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> body = new HashMap<>();
        body.put("currentPage", "1");
        body.put("pageSize", "20");
        body.put("jobId", jobId);
        String s = HttpClientUtils.doGet(url, header, body);
        log.info("query restore info:" + s);
        return s;
    }
}
