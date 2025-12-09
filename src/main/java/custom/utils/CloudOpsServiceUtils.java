package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import custom.entity.AlterInstanceIndexClusterParams;
import custom.entity.RestoreBackupParams;
import custom.pojo.IndexPoolInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static custom.BaseTest.envConfig;
import static custom.BaseTest.newInstanceInfo;

@Slf4j
public class CloudOpsServiceUtils {

    public static String listDBVersionByKeywords(String keywords) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/release_version";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> paramsDB = new HashMap<>();
        paramsDB.put("currentPage", "1");
        paramsDB.put("pageSize", "100");
        paramsDB.put("dbVersion", keywords);
        paramsDB.put("regionId", envConfig.getRegionId());
        String s = HttpClientUtils.doGet(url, header, paramsDB);
        log.info("listDBVersionByKeywords:" + s);
        return s;
    }

    public static String listTagByKeywords(String keywords) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/release_version";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", envConfig.getCloudOpsServiceToken());
        Map<String, String> paramsTag = new HashMap<>();
        paramsTag.put("currentPage", "1");
        paramsTag.put("pageSize", "100");
        paramsTag.put("tag", keywords);
        paramsTag.put("regionId", envConfig.getRegionId());
        String s = HttpClientUtils.doGet(url, header, paramsTag);
        log.info("listTagByKeywords:" + s);
        return s;
    }

    public static String getLatestImageByKeywords(String keywords) {
        List<String> collect;
        JSONObject jsonResponse = JSON.parseObject(listDBVersionByKeywords(keywords));
        JSONObject jsonResponse2 = JSON.parseObject(listTagByKeywords(keywords));
        List<String> lists = new ArrayList<>();
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

        // 剔除重复
        collect = lists.stream().distinct().collect(Collectors.toList());
        return collect.stream().findFirst().orElse("");
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

    public static String restoreBackup(RestoreBackupParams restoreBackupParams) {
        String instanceId = restoreBackupParams.getToInstanceId().equalsIgnoreCase("") ? newInstanceInfo.getInstanceId() : restoreBackupParams.getToInstanceId();
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
