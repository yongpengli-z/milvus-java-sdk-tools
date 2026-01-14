package custom.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import custom.components.*;
import custom.config.EnvEnum;
import custom.entity.*;
import custom.entity.result.*;
import custom.entity.result.HelmCreateInstanceResult;
import custom.entity.result.HelmDeleteInstanceResult;
import custom.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

/**
 * @Author yongpeng.li @Date 2024/6/5 17:26
 */
@Slf4j
public class ComponentSchedule {
    public static List<JSONObject> runningSchedule(String customizeParams) {
        log.info("--customizeParams--:" + customizeParams);
        // 获取params的所有根节点
        JSONObject parseJO = JSONObject.parseObject(customizeParams);
        List<String> keyList = new ArrayList<>(parseJO.keySet());
        // 按照编号对key进行排序
        keyList = keyList.stream().sorted((s1, s2) -> {
            int num1 = Integer.parseInt(s1.split("_")[1]);
            int num2 = Integer.parseInt(s2.split("_")[1]);
            return Integer.compare(num1, num2);
        }).collect(Collectors.toList());

        log.info(keyList.toString());

        List<Object> operators = new ArrayList<>();
        for (String keyString : keyList) {
            String item = parseJO.getString(keyString);
            String paramName = keyString.substring(0, keyString.indexOf("_"));
            try {
                Object o = JSONObject.parseObject(item, Class.forName("custom.entity." + paramName));
                operators.add(o);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        // 收集结果
        List<JSONObject> results = new ArrayList<>();
        for (int i = 0; i < operators.size(); i++) {
            log.warn("Step--[ " + operators.size() + " , " + (i + 1) + " ]:");
            int taskStatus = queryTaskRedisValue();

            if (taskStatus == TaskStatusEnum.STOPPING.status) {
                do {
                    log.info("监测到暂停...");
                    try {
                        Thread.sleep(1000 * 5);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                    taskStatus = queryTaskRedisValue();
                } while (taskStatus == TaskStatusEnum.STOPPING.status);
            }

            if (taskStatus == TaskStatusEnum.TERMINATE.status) {
                log.info("监测到任务终止...");
                break;
            }
            JSONObject jsonObject = callComponentSchedule(operators.get(i), i);
            results.add(jsonObject);
        }
        log.info("[结果汇总]： " +
                "\n" + results);
        return results;
    }

    public static JSONObject callComponentSchedule(Object object, int index) {
        JSONObject jsonObject = new JSONObject();
        log.info("当前父节点：" + parentNodeName.toString());
        if (object instanceof CreateCollectionParams) {
            log.info("*********** < create collection > ***********");
            CreateCollectionResult createCollectionResult = CreateCollectionComp.createCollection((CreateCollectionParams) object);
            jsonObject.put("CreateCollection_" + index, createCollectionResult);
            reportStepResult(CreateCollectionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createCollectionResult));
        }
        if (object instanceof CreateIndexParams) {
            log.info("*********** < create index > ***********");
            CreateIndexResult createIndexResult = CreateIndexComp.CreateIndex((CreateIndexParams) object);
            jsonObject.put("CreateIndex_" + index, createIndexResult);
            reportStepResult(CreateIndexParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createIndexResult));
        }
        if (object instanceof LoadParams) {
            log.info("*********** < load collection > ***********");
            LoadResult loadResult = LoadCollectionComp.loadCollection((LoadParams) object);
            jsonObject.put("LoadCollection_" + index, loadResult);
            reportStepResult(LoadParams.class.getSimpleName() + "_" + index, JSON.toJSONString(loadResult));
        }
        if (object instanceof InsertParams) {
            log.info("*********** < insert data > ***********");
            InsertResult insertResult = InsertComp.insertCollection((InsertParams) object);
            jsonObject.put("Insert_" + index, insertResult);
            reportStepResult(InsertParams.class.getSimpleName() + "_" + index, JSON.toJSONString(insertResult));
        }
        if (object instanceof SearchParams) {
            log.info("*********** < search collection > ***********");
            SearchResultA searchResultA = SearchComp.searchCollection((SearchParams) object);
            jsonObject.put("Search_" + index, searchResultA);
            reportStepResult(SearchParams.class.getSimpleName() + "_" + index, JSON.toJSONString(searchResultA));
        }
        if (object instanceof SearchIteratorParams) {
            log.info("*********** < search collection > ***********");
            SearchIteratorResultA searchIteratorResultA = SearchIteratorComp.searchIteratorCollection((SearchIteratorParams) object);
            jsonObject.put("Search_" + index, searchIteratorResultA);
        }
        if (object instanceof ReleaseParams) {
            log.info("*********** < release collection > ***********");
            ReleaseResult releaseResult = ReleaseCollectionComp.releaseCollection((ReleaseParams) object);
            jsonObject.put("ReleaseCollection_" + index, releaseResult);
            reportStepResult(ReleaseParams.class.getSimpleName() + "_" + index, JSON.toJSONString(releaseResult));
        }
        if (object instanceof DropCollectionParams) {
            log.info("*********** < drop collection > ***********");
            DropCollectionResult dropCollectionResult = DropCollectionComp.dropCollection((DropCollectionParams) object);
            jsonObject.put("DropCollection_" + index, dropCollectionResult);
            reportStepResult(DropCollectionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(dropCollectionResult));
        }
        if (object instanceof RecallParams) {
            log.info("*********** < recall > ***********");
            RecallComp.calcRecall((RecallParams) object);

        }
        if (object instanceof WaitParams) {
            log.info("*********** < Wait > ***********");
            WaitResult waitResult = WaitComp.wait((WaitParams) object);
            jsonObject.put("Wait" + index, waitResult);
            reportStepResult(WaitParams.class.getSimpleName() + "_" + index, JSON.toJSONString(waitResult));
        }
        if (object instanceof UpsertParams) {
            log.info("*********** < upsert data > ***********");
            UpsertResult upsertResult = UpsertComp.upsertCollection((UpsertParams) object);
            jsonObject.put("Upsert_" + index, upsertResult);
            reportStepResult(UpsertParams.class.getSimpleName() + "_" + index, JSON.toJSONString(upsertResult));
        }
        if (object instanceof ConcurrentParams) {
            log.info("*********** < Concurrent Operator > ***********");
            parentNodeName.add("ConcurrentParams_" + index);
            List<JSONObject> jsonObjects = ConcurrentComp.concurrentComp((ConcurrentParams) object);
            parentNodeName.remove(parentNodeName.size() - 1);
            jsonObject.put("Concurrent_" + index, jsonObjects);
            reportStepResult(ConcurrentParams.class.getSimpleName() + "_" + index, JSON.toJSONString(jsonObjects));
        }

        if (object instanceof QueryParams) {
            log.info("*********** < query collection > ***********");
            QueryResult queryResult = QueryComp.queryCollection((QueryParams) object);
            jsonObject.put("Query_" + index, queryResult);
            reportStepResult(QueryParams.class.getSimpleName() + "_" + index, JSON.toJSONString(queryResult));
        }
        if (object instanceof DropIndexParams) {
            log.info("*********** < drop index > ***********");
            DropIndexResult dropIndexResult = DropIndexComp.dropIndex((DropIndexParams) object);
            jsonObject.put("DropIndex_" + index, dropIndexResult);
            reportStepResult(DropIndexParams.class.getSimpleName() + "_" + index, JSON.toJSONString(dropIndexResult));
        }
        if (object instanceof LoopParams) {
            log.info("*********** < Loop Operator> ***********");
            parentNodeName.add("LoopParams_" + index);
            LoopResult loopResult = LoopComp.loopComp((LoopParams) object);
            parentNodeName.remove(parentNodeName.size() - 1);
            jsonObject.put("Loop_" + index, loopResult);
            reportStepResult(LoopParams.class.getSimpleName() + "_" + index, JSON.toJSONString(loopResult));
        }
        if (object instanceof CreateInstanceParams) {
            log.info("*********** < create instance> ***********");
            CreateInstanceResult createInstanceResult = CreateInstanceComp.createInstance((CreateInstanceParams) object);
            jsonObject.put("CreateInstance_" + index, createInstanceResult);
            reportStepResult(CreateInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createInstanceResult));
        }
        if (object instanceof RollingUpgradeParams) {
            log.info("*********** < rolling upgrade > ***********");
            RollingUpgradeResult rollingUpgradeResult = RollingUpgradeComp.rollingUpgradeInstance((RollingUpgradeParams) object);
            jsonObject.put("RollingUpgrade_" + index, rollingUpgradeResult);
            reportStepResult(RollingUpgradeParams.class.getSimpleName() + "_" + index, JSON.toJSONString(rollingUpgradeResult));
        }
        if (object instanceof DeleteInstanceParams) {
            log.info("*********** < delete instance > ***********");
            DeleteInstanceResult deleteInstanceResult = DeleteInstanceComp.deleteInstance((DeleteInstanceParams) object);
            jsonObject.put("DeleteInstance_" + index, deleteInstanceResult);
            reportStepResult(DeleteInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(deleteInstanceResult));
        }
        if (object instanceof StopInstanceParams) {
            log.info("*********** < stop instance > ***********");
            StopInstanceResult stopInstanceResult = StopInstanceComp.stopInstance((StopInstanceParams) object);
            jsonObject.put("StopInstance_" + index, stopInstanceResult);
            reportStepResult(StopInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(stopInstanceResult));
        }
        if (object instanceof ResumeInstanceParams) {
            log.info("*********** < resume instance > ***********");
            ResumeInstanceResult resumeInstanceResult = ResumeInstanceComp.resumeInstance((ResumeInstanceParams) object);
            jsonObject.put("ResumeInstance_" + index, resumeInstanceResult);
            reportStepResult(ResumeInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(resumeInstanceResult));
        }
        if (object instanceof ModifyParams) {
            log.info("*********** < modify params > ***********");
            ModifyParamsResult modifyParamsResult = ModifyParamsComp.modifyParams((ModifyParams) object);
            jsonObject.put("ModifyParams_" + index, modifyParamsResult);
            reportStepResult(ModifyParams.class.getSimpleName() + "_" + index, JSON.toJSONString(modifyParamsResult));
        }
        if (object instanceof RestartInstanceParams) {
            log.info("*********** < restart instance > ***********");
            RestartInstanceResult restartInstanceResult = RestartInstanceComp.restartInstance((RestartInstanceParams) object);
            jsonObject.put("RestartInstance_" + index, restartInstanceResult);
            reportStepResult(RestartInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(restartInstanceResult));
        }
        if (object instanceof FlushParams) {
            log.info("*********** < Flush > ***********");
            FlushResult flushResult = FlushComp.flush((FlushParams) object);
            jsonObject.put("Flush_" + index, flushResult);
            reportStepResult(FlushParams.class.getSimpleName() + "_" + index, JSON.toJSONString(flushResult));
        }
        if (object instanceof CompactParams) {
            log.info("*********** < Compact > ***********");
            CompactResult compactResult = CompactComp.compact((CompactParams) object);
            jsonObject.put("Compact_" + index, compactResult);
            reportStepResult(CompactParams.class.getSimpleName() + "_" + index, JSON.toJSONString(compactResult));
        }
        if (object instanceof UpdateIndexPoolParams) {
            log.info("*********** < Update Index Pool > ***********");
            UpdateIndexPoolResult updateIndexPoolResult = UpdateIndexPoolComp.updateIndexPool((UpdateIndexPoolParams) object);
            jsonObject.put("UpdateIndexPool_" + index, updateIndexPoolResult);
            reportStepResult(UpdateIndexPoolParams.class.getSimpleName() + "_" + index, JSON.toJSONString(updateIndexPoolResult));
        }
        if (object instanceof QuerySegmentInfoParams) {
            log.info("*********** < Query Segment Info > ***********");
            QuerySegmentInfoResult querySegmentInfoResult = QuerySegmentInfoComp.querySegmentInfo((QuerySegmentInfoParams) object);
            jsonObject.put("QuerySegmentInfo_" + index, querySegmentInfoResult);
            reportStepResult(QuerySegmentInfoParams.class.getSimpleName() + "_" + index, JSON.toJSONString(querySegmentInfoResult));
        }
        if (object instanceof PersistentSegmentInfoParams) {
            log.info("*********** < Persistent Segment Info > ***********");
            PersistentSegmentInfoResult persistentSegmentInfoResult = PersistentSegmentInfoComp.persistentSegmentInfo((PersistentSegmentInfoParams) object);
            jsonObject.put("PersistentSegmentInfo_" + index, persistentSegmentInfoResult);
            reportStepResult(PersistentSegmentInfoParams.class.getSimpleName() + "_" + index, JSON.toJSONString(persistentSegmentInfoResult));
        }
        if (object instanceof DeleteParams) {
            log.info("*********** < Delete > ***********");
            DeleteResult deleteResult = DeleteComp.delete((DeleteParams) object);
            jsonObject.put("Delete_" + index, deleteResult);
            reportStepResult(DeleteParams.class.getSimpleName() + "_" + index, JSON.toJSONString(deleteResult));
        }
        if (object instanceof UseDatabaseParams) {
            log.info("*********** < UseDatabase > ***********");
            UseDatabaseResult useDatabaseResult = UseDatabaseComp.useDatabase((UseDatabaseParams) object);
            jsonObject.put("UseDatabase_" + index, useDatabaseResult);
            reportStepResult(UseDatabaseParams.class.getSimpleName() + "_" + index, JSON.toJSONString(useDatabaseResult));
        }
        if (object instanceof AlterInstanceIndexClusterParams) {
            log.info("*********** < Alter Instance Index Cluster > ***********");
            AlterInstanceIndexClusterResult alterInstanceIndexClusterResult = AlterInstanceIndexClusterComp.alterIndexCluster((AlterInstanceIndexClusterParams) object);
            jsonObject.put("AlterInstanceIndexCluster_" + index, alterInstanceIndexClusterResult);
            reportStepResult(AlterInstanceIndexClusterParams.class.getSimpleName() + "_" + index, JSON.toJSONString(alterInstanceIndexClusterResult));
        }
        if (object instanceof AddCollectionFieldParams) {
            log.info("*********** < Add Collection Field > ***********");
            AddCollectionFieldResult addCollectionFieldResult = AddCollectionFieldComp.addCollectionField((AddCollectionFieldParams) object);
            jsonObject.put("AddCollectionField_" + index, addCollectionFieldResult);
            reportStepResult(AddCollectionFieldParams.class.getSimpleName() + "_" + index, JSON.toJSONString(addCollectionFieldResult));
        }
        if (object instanceof RestoreBackupParams) {
            log.info("*********** < Restore Backup > ***********");
            RestoreBackupResult restoreBackupResult = RestoreBackupComp.restoreBackup((RestoreBackupParams) object);
            jsonObject.put("RestoreBackup_" + index, restoreBackupResult);
            reportStepResult(RestoreBackupParams.class.getSimpleName() + "_" + index, JSON.toJSONString(restoreBackupResult));
        }
        if (object instanceof RenameCollectionParams) {
            log.info("*********** < Rename collection > ***********");
            RenameCollectionResult renameCollectionResult = RenameCollectionComp.renameCollection((RenameCollectionParams) object);
            jsonObject.put("RenameCollection_" + index, renameCollectionResult);
            reportStepResult(RenameCollectionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(renameCollectionResult));
        }
        if (object instanceof DescribeCollectionParams) {
            log.info("*********** < Describe collection > ***********");
            DescribeCollectionResult describeCollectionResult = DescribeCollectionComp.describeCollection((DescribeCollectionParams) object);
            jsonObject.put("DescribeCollection_" + index, describeCollectionResult);
            reportStepResult(DescribeCollectionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(describeCollectionResult));
        }
        if (object instanceof CreateDatabaseParams) {
            log.info("*********** < Create database > ***********");
            CreateDatabaseResult createDatabaseResult = CreateDatabaseComp.createDatabase((CreateDatabaseParams) object);
            jsonObject.put("CreateDatabase_" + index, createDatabaseResult);
            reportStepResult(CreateDatabaseParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createDatabaseResult));
        }
        if (object instanceof CreateAliasParams) {
            log.info("*********** < Create alias > ***********");
            CreateAliasResult createAliasResult = CreateAliasComp.createAlias((CreateAliasParams) object);
            jsonObject.put("CreateAlias_" + index, createAliasResult);
            reportStepResult(CreateAliasParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createAliasResult));
        }
        if (object instanceof AlterAliasParams) {
            log.info("*********** < Alter alias > ***********");
            AlterAliasResult alterAliasResult = AlterAliasComp.alterAlias((AlterAliasParams) object);
            jsonObject.put("AlterAlias_" + index, alterAliasResult);
            reportStepResult(AlterAliasParams.class.getSimpleName() + "_" + index, JSON.toJSONString(alterAliasResult));
        }
        if (object instanceof DebugTestParams) {
            log.info("*********** < DebugTest > ***********");
            String s = DebugTestComp.debugTest((DebugTestParams) object);
            jsonObject.put("DebugTest_" + index, s);
            reportStepResult(DebugTestParams.class.getSimpleName() + "_" + index, s);
        }
        if (object instanceof HybridSearchParams) {
            log.info("*********** < HybridSearch > ***********");
            HybridSearchResult hybridSearchResult = HybridSearchComp.hybridSearchCollection((HybridSearchParams) object);
            jsonObject.put("HybridSearch_" + index, hybridSearchResult);
            reportStepResult(HybridSearchParams.class.getSimpleName() + "_" + index, JSON.toJSONString(hybridSearchResult));
        }
        if (object instanceof HelmCreateInstanceParams) {
            log.info("*********** < Helm create instance > ***********");
            HelmCreateInstanceResult helmCreateInstanceResult = HelmCreateInstanceComp.createInstance((HelmCreateInstanceParams) object);
            jsonObject.put("HelmCreateInstance_" + index, helmCreateInstanceResult);
            reportStepResult(HelmCreateInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(helmCreateInstanceResult));
        }
        if (object instanceof HelmDeleteInstanceParams) {
            log.info("*********** < Helm delete instance > ***********");
            HelmDeleteInstanceResult helmDeleteInstanceResult = HelmDeleteInstanceComp.deleteInstance((HelmDeleteInstanceParams) object);
            jsonObject.put("HelmDeleteInstance_" + index, helmDeleteInstanceResult);
            reportStepResult(HelmDeleteInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(helmDeleteInstanceResult));
        }
        return jsonObject;
    }

    public static int queryTaskRedisValue() {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            log.info("current env:" + envEnum);
            return 1;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/query/status?redisKey=" + redisKey;
        String s = HttpClientUtils.doGet(uri);
//        log.info("request qtp:" + s);
        JSONObject jsonObject = JSON.parseObject(s);
        return jsonObject.getInteger("data");
    }

    public static void updateArgoStatus(int status) {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            log.info("current env:" + envEnum);
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/argo/status?id=" + taskId + "&argoStatus=" + status;
        String s = HttpClientUtils.doPost(uri);
//        log.info("Update case status:" + s);
    }

    public static void updateCaseStatus(int status) {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            log.info("current env:" + envEnum);

            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/case/status?id=" + taskId + "&caseStatus=" + status;
        String s = HttpClientUtils.doPost(uri);
//        log.info("Update case status:" + s);
    }

    public static void reportStepResult(String nodeName, String result) {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            log.info("current env:" + envEnum);
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task-details/result/insert";
        JSONObject params = new JSONObject();
        params.put("taskId", taskId);
        params.put("nodeName", nodeName);
        params.put("parentNodeName", parentNodeName);
        params.put("result", result);
        String s = HttpClientUtils.doPostJson(uri, params.toJSONString());
        log.info(parentNodeName + "[" + nodeName + "]Insert result:" + s);
        log.info("params " + "[" + params.toJSONString() + "]Insert result:" + s);

    }

    public static void initInstanceStatus(String instanceId, String instanceUri, String image, int status) {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/instance/add?id=" + taskId + "&instanceId=" + instanceId + "&instanceUri=" + instanceUri + "&image=" + image + "&status=" + status;
        String s = HttpClientUtils.doPost(uri);
        log.info("add instanceId:" + s);
    }

    public static void updateInstanceStatus(String instanceId, String instanceUri, String image, int status) {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/instance/update?id=" + taskId + "&instanceId=" + instanceId + "&instanceUri=" + instanceUri + "&image=" + image + "&status=" + status;
        String s = HttpClientUtils.doPost(uri);
        log.info("add instanceId:" + s);
    }

    public static List<String> queryReleaseImage() {
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.TC_NJ || envEnum == EnvEnum.HWC) {
            return Lists.newArrayList("v2.6.7-hotfix3-8d95e4417-2971(2.6.7-hotfix3-20251211-8d95e4417-78bee5c)"); //适配访问不通qtp环境
        }
        String uri = "http://qtp-server.zilliz.cc/jenkins-info/vdc/milvus/build/release";
        String s = HttpClientUtils.doGet(uri);
        // 所得结果为倒序
        JSONArray respJO = JSON.parseObject(s).getJSONArray("data");
        List<String> dbVersion = new ArrayList<>();
        for (int i = 0; i < respJO.size(); i++) {
            JSONObject jsonObject = respJO.getJSONObject(i);
            dbVersion.add(jsonObject.getString("dbVersion") + "(" + jsonObject.getString("imageInfo") + ")");
        }
        return dbVersion;
    }
}
