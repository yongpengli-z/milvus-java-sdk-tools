package custom.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import custom.components.*;
import custom.config.EnvEnum;
import custom.entity.*;
import custom.exception.CustomException;
import custom.exception.CustomExceptionCode;
import custom.entity.result.*;
import custom.entity.result.HelmCreateInstanceResult;
import custom.entity.result.HelmDeleteInstanceResult;
import custom.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static custom.BaseTest.*;

/**
 * @Author yongpeng.li @Date 2024/6/5 17:26
 */
@Slf4j
public class ComponentSchedule {
    private static final ThreadLocal<List<String>> PARENT_NODE_NAMES = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<LoopIterationContext>> LOOP_CONTEXTS = ThreadLocal.withInitial(ArrayList::new);
    private static final Map<String, LoopAggregateState> LOOP_AGGREGATE_STATES = new ConcurrentHashMap<>();

    private static boolean skipQtpServer() {
        if (Boolean.getBoolean("qtp.report.disabled")) {
            log.info("qtp.report.disabled=true, skip qtp-server request");
            return true;
        }
        if (envEnum == EnvEnum.ALI_HZ || envEnum == EnvEnum.HWC) {
            log.info("current env:" + envEnum);
            return true;
        }
        return false;
    }

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
                log.info("反序列化步骤: key={}, className=custom.entity.{}", keyString, paramName);
                Object o = JSONObject.parseObject(item, Class.forName("custom.entity." + paramName));
                log.info("反序列化成功: {} -> {}", keyString, o.getClass().getSimpleName());
                operators.add(o);
            } catch (ClassNotFoundException e) {
                log.error("找不到实体类 custom.entity.{}, key={}", paramName, keyString, e);
                throw new CustomException(CustomExceptionCode.CLASS_NOT_FOUND,
                        "找不到实体类 custom.entity." + paramName + ", 请检查步骤key: " + keyString, e);
            } catch (Exception e) {
                log.error("JSON反序列化失败, key={}, className=custom.entity.{}, json={}", keyString, paramName, item, e);
                throw new CustomException(CustomExceptionCode.JSON_PARSE_ERROR,
                        "JSON反序列化失败, key=" + keyString + ", class=" + paramName + ": " + e.getMessage(), e);
            }
        }
        // 收集结果
        List<JSONObject> results = new ArrayList<>();
        for (int i = 0; i < operators.size(); i++) {
            log.warn("Step--[ " + operators.size() + " , " + (i + 1) + " ]:");
            int taskStatus = waitIfTaskPaused();

            if (taskStatus == TaskStatusEnum.TERMINATE.status) {
                log.info("监测到任务终止...");
                break;
            }
            JSONObject jsonObject = callComponentSchedule(operators.get(i), i);
            results.add(jsonObject);

            if (containsFailResult(jsonObject.toJSONString())) {
                log.error("步骤返回 fail 状态，终止后续步骤执行！");
                break;
            }
        }
        log.info("[结果汇总]： " +
                "\n" + results);
        return results;
    }

    public static JSONObject callComponentSchedule(Object object, int index) {
        JSONObject jsonObject = new JSONObject();
        String componentName = object.getClass().getSimpleName();
        log.info("当前父节点：" + snapshotParentNodeName());
        log.info("执行组件: {} , 参数: {}", componentName, JSON.toJSONString(object));
        try {
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
            log.info("*********** < recall test > ***********");
            RecallResult recallResult = RecallComp.recallTest((RecallParams) object);
            jsonObject.put("Recall_" + index, recallResult);
            reportStepResult(RecallParams.class.getSimpleName() + "_" + index, JSON.toJSONString(recallResult));
        }
        if (object instanceof WaitParams) {
            log.info("*********** < Wait > ***********");
            WaitResult waitResult = WaitComp.wait((WaitParams) object);
            jsonObject.put("Wait" + index, waitResult);
            reportStepResult(WaitParams.class.getSimpleName() + "_" + index, JSON.toJSONString(waitResult));
        }
        if (object instanceof PauseParams) {
            log.info("*********** < Pause > ***********");
            PauseResult pauseResult = PauseComp.pause((PauseParams) object);
            jsonObject.put("Pause_" + index, pauseResult);
            reportStepResult(PauseParams.class.getSimpleName() + "_" + index, JSON.toJSONString(pauseResult));
        }
        if (object instanceof UpsertParams) {
            log.info("*********** < upsert data > ***********");
            UpsertResult upsertResult = UpsertComp.upsertCollection((UpsertParams) object);
            jsonObject.put("Upsert_" + index, upsertResult);
            reportStepResult(UpsertParams.class.getSimpleName() + "_" + index, JSON.toJSONString(upsertResult));
        }
        if (object instanceof ConcurrentParams) {
            log.info("*********** < Concurrent Operator > ***********");
            pushParentNodeName("ConcurrentParams_" + index);
            List<JSONObject> jsonObjects = ConcurrentComp.concurrentComp((ConcurrentParams) object);
            popParentNodeName();
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
            String loopNodeName = "LoopParams_" + index;
            List<String> loopParentNodeName = snapshotParentNodeName();
            pushParentNodeName(loopNodeName);
            LoopResult loopResult = LoopComp.loopComp((LoopParams) object, loopNodeName, loopParentNodeName);
            popParentNodeName();
            jsonObject.put("Loop_" + index, loopResult);
            reportStepResult(loopNodeName, JSON.toJSONString(loopResult), loopParentNodeName);
        }
        if (object instanceof CreateInstanceParams) {
            log.info("*********** < create instance> ***********");
            CreateInstanceResult createInstanceResult = CreateInstanceComp.createInstance((CreateInstanceParams) object);
            jsonObject.put("CreateInstance_" + index, createInstanceResult);
            reportStepResult(CreateInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createInstanceResult));
        }
        if (object instanceof CreateQueryClusterParams) {
            log.info("*********** < create query cluster> ***********");
            CreateInstanceResult createInstanceResult = CreateQueryClusterComp.createQueryCluster((CreateQueryClusterParams) object);
            jsonObject.put("CreateQueryCluster_" + index, createInstanceResult);
            reportStepResult(CreateQueryClusterParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createInstanceResult));
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
        if (object instanceof RestfulSearchParams) {
            log.info("*********** < restful search collection > ***********");
            SearchResultA restfulSearchResultA = RestfulSearchComp.restfulSearchCollection((RestfulSearchParams) object);
            jsonObject.put("RestfulSearch_" + index, restfulSearchResultA);
            reportStepResult(RestfulSearchParams.class.getSimpleName() + "_" + index, JSON.toJSONString(restfulSearchResultA));
        }
        if (object instanceof HybridSearchParams) {
            log.info("*********** < HybridSearch > ***********");
            HybridSearchResult hybridSearchResult = HybridSearchComp.hybridSearchCollection((HybridSearchParams) object);
            jsonObject.put("HybridSearch_" + index, hybridSearchResult);
            reportStepResult(HybridSearchParams.class.getSimpleName() + "_" + index, JSON.toJSONString(hybridSearchResult));
        }
        if (object instanceof RestfulHybridSearchParams) {
            log.info("*********** < restful hybrid search collection > ***********");
            RestfulHybridSearchResult restfulHybridSearchResult = RestfulHybridSearchComp.restfulHybridSearchCollection((RestfulHybridSearchParams) object);
            jsonObject.put("RestfulHybridSearch_" + index, restfulHybridSearchResult);
            reportStepResult(RestfulHybridSearchParams.class.getSimpleName() + "_" + index, JSON.toJSONString(restfulHybridSearchResult));
        }
        if (object instanceof ScaleInstanceParams) {
            log.info("*********** < scale instance > ***********");
            ScaleInstanceResult scaleInstanceResult = ScaleInstanceComp.scaleInstance((ScaleInstanceParams) object);
            jsonObject.put("ScaleInstance_" + index, scaleInstanceResult);
            reportStepResult(ScaleInstanceParams.class.getSimpleName() + "_" + index, JSON.toJSONString(scaleInstanceResult));
        }
        if (object instanceof UpdateInstanceComponentParams) {
            log.info("*********** < update instance component > ***********");
            UpdateInstanceComponentResult updateInstanceComponentResult = UpdateInstanceComponentComp.updateInstanceComponent((UpdateInstanceComponentParams) object);
            jsonObject.put("UpdateInstanceComponent_" + index, updateInstanceComponentResult);
            reportStepResult(UpdateInstanceComponentParams.class.getSimpleName() + "_" + index, JSON.toJSONString(updateInstanceComponentResult));
        }
        if (object instanceof CreateGlobalClusterParams) {
            log.info("*********** < create global cluster > ***********");
            CreateGlobalClusterResult createGlobalClusterResult = CreateGlobalClusterComp.createGlobalCluster((CreateGlobalClusterParams) object);
            jsonObject.put("CreateGlobalCluster_" + index, createGlobalClusterResult);
            reportStepResult(CreateGlobalClusterParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createGlobalClusterResult));
        }
        if (object instanceof CreateSecondaryParams) {
            log.info("*********** < create secondary > ***********");
            CreateSecondaryResult createSecondaryResult = CreateSecondaryComp.createSecondary((CreateSecondaryParams) object);
            jsonObject.put("CreateSecondary_" + index, createSecondaryResult);
            reportStepResult(CreateSecondaryParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createSecondaryResult));
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
        if (object instanceof CreatePartitionParams) {
            log.info("*********** < create partition > ***********");
            CreatePartitionResult createPartitionResult = CreatePartitionComp.createPartition((CreatePartitionParams) object);
            jsonObject.put("CreatePartition_" + index, createPartitionResult);
            reportStepResult(CreatePartitionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(createPartitionResult));
        }
        if (object instanceof DropPartitionParams) {
            log.info("*********** < drop partition > ***********");
            DropPartitionResult dropPartitionResult = DropPartitionComp.dropPartition((DropPartitionParams) object);
            jsonObject.put("DropPartition_" + index, dropPartitionResult);
            reportStepResult(DropPartitionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(dropPartitionResult));
        }
        if (object instanceof ListPartitionsParams) {
            log.info("*********** < list partitions > ***********");
            ListPartitionsResult listPartitionsResult = ListPartitionsComp.listPartitions((ListPartitionsParams) object);
            jsonObject.put("ListPartitions_" + index, listPartitionsResult);
            reportStepResult(ListPartitionsParams.class.getSimpleName() + "_" + index, JSON.toJSONString(listPartitionsResult));
        }
        if (object instanceof HasPartitionParams) {
            log.info("*********** < has partition > ***********");
            HasPartitionResult hasPartitionResult = HasPartitionComp.hasPartition((HasPartitionParams) object);
            jsonObject.put("HasPartition_" + index, hasPartitionResult);
            reportStepResult(HasPartitionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(hasPartitionResult));
        }
        if (object instanceof LoadPartitionsParams) {
            log.info("*********** < load partitions > ***********");
            LoadPartitionsResult loadPartitionsResult = LoadPartitionsComp.loadPartitions((LoadPartitionsParams) object);
            jsonObject.put("LoadPartitions_" + index, loadPartitionsResult);
            reportStepResult(LoadPartitionsParams.class.getSimpleName() + "_" + index, JSON.toJSONString(loadPartitionsResult));
        }
        if (object instanceof ReleasePartitionsParams) {
            log.info("*********** < release partitions > ***********");
            ReleasePartitionsResult releasePartitionsResult = ReleasePartitionsComp.releasePartitions((ReleasePartitionsParams) object);
            jsonObject.put("ReleasePartitions_" + index, releasePartitionsResult);
            reportStepResult(ReleasePartitionsParams.class.getSimpleName() + "_" + index, JSON.toJSONString(releasePartitionsResult));
        }
        if (object instanceof ListCollectionsParams) {
            log.info("*********** < list collections > ***********");
            ListCollectionsResult listCollectionsResult = ListCollectionsComp.listCollections((ListCollectionsParams) object);
            jsonObject.put("ListCollections_" + index, listCollectionsResult);
            reportStepResult(ListCollectionsParams.class.getSimpleName() + "_" + index, JSON.toJSONString(listCollectionsResult));
        }
        if (object instanceof HasCollectionParams) {
            log.info("*********** < has collection > ***********");
            HasCollectionResult hasCollectionResult = HasCollectionComp.hasCollection((HasCollectionParams) object);
            jsonObject.put("HasCollection_" + index, hasCollectionResult);
            reportStepResult(HasCollectionParams.class.getSimpleName() + "_" + index, JSON.toJSONString(hasCollectionResult));
        }
        if (object instanceof GetLoadStateParams) {
            log.info("*********** < get load state > ***********");
            GetLoadStateResult getLoadStateResult = GetLoadStateComp.getLoadState((GetLoadStateParams) object);
            jsonObject.put("GetLoadState_" + index, getLoadStateResult);
            reportStepResult(GetLoadStateParams.class.getSimpleName() + "_" + index, JSON.toJSONString(getLoadStateResult));
        }
        if (object instanceof DescribeIndexParams) {
            log.info("*********** < describe index > ***********");
            DescribeIndexResult describeIndexResult = DescribeIndexComp.describeIndex((DescribeIndexParams) object);
            jsonObject.put("DescribeIndex_" + index, describeIndexResult);
            reportStepResult(DescribeIndexParams.class.getSimpleName() + "_" + index, JSON.toJSONString(describeIndexResult));
        }
        if (object instanceof ListIndexesParams) {
            log.info("*********** < list indexes > ***********");
            ListIndexesResult listIndexesResult = ListIndexesComp.listIndexes((ListIndexesParams) object);
            jsonObject.put("ListIndexes_" + index, listIndexesResult);
            reportStepResult(ListIndexesParams.class.getSimpleName() + "_" + index, JSON.toJSONString(listIndexesResult));
        }
        if (object instanceof DropAliasParams) {
            log.info("*********** < drop alias > ***********");
            DropAliasResult dropAliasResult = DropAliasComp.dropAlias((DropAliasParams) object);
            jsonObject.put("DropAlias_" + index, dropAliasResult);
            reportStepResult(DropAliasParams.class.getSimpleName() + "_" + index, JSON.toJSONString(dropAliasResult));
        }
        if (object instanceof ListAliasesParams) {
            log.info("*********** < list aliases > ***********");
            ListAliasesResult listAliasesResult = ListAliasesComp.listAliases((ListAliasesParams) object);
            jsonObject.put("ListAliases_" + index, listAliasesResult);
            reportStepResult(ListAliasesParams.class.getSimpleName() + "_" + index, JSON.toJSONString(listAliasesResult));
        }
        if (object instanceof DescribeAliasParams) {
            log.info("*********** < describe alias > ***********");
            DescribeAliasResult describeAliasResult = DescribeAliasComp.describeAlias((DescribeAliasParams) object);
            jsonObject.put("DescribeAlias_" + index, describeAliasResult);
            reportStepResult(DescribeAliasParams.class.getSimpleName() + "_" + index, JSON.toJSONString(describeAliasResult));
        }
        if (object instanceof GetParams) {
            log.info("*********** < get entities > ***********");
            GetResult getResult = GetComp.get((GetParams) object);
            jsonObject.put("Get_" + index, getResult);
            reportStepResult(GetParams.class.getSimpleName() + "_" + index, JSON.toJSONString(getResult));
        }
        if (object instanceof QueryIteratorParams) {
            log.info("*********** < query iterator > ***********");
            QueryIteratorResult queryIteratorResult = QueryIteratorComp.queryIterator((QueryIteratorParams) object);
            jsonObject.put("QueryIterator_" + index, queryIteratorResult);
            reportStepResult(QueryIteratorParams.class.getSimpleName() + "_" + index, JSON.toJSONString(queryIteratorResult));
        }
        } catch (Exception e) {
            String errorKey = componentName + "_" + index;
            log.error("组件 [{}] 执行异常! Step index: {}", componentName, index, e);
            // 打印完整异常链
            Throwable cause = e.getCause();
            int depth = 1;
            while (cause != null) {
                log.error("  Caused by (depth={}): {} - {}", depth, cause.getClass().getName(), cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            // 构造异常结果，让上层能感知到失败
            JSONObject errorResult = new JSONObject();
            errorResult.put("result", "exception");
            errorResult.put("errorMsg", buildReadableErrorMessage(componentName, e));
            jsonObject.put(errorKey, errorResult);
            reportStepResult(errorKey, JSON.toJSONString(errorResult));
        }
        return jsonObject;
    }

    private static String buildReadableErrorMessage(String componentName, Exception e) {
        String rawMessage = e.getMessage();
        if (e instanceof IndexOutOfBoundsException
                && rawMessage != null
                && rawMessage.contains("Index -1 out of bounds for length 0")
                && isDefaultCollectionLookupError(e)) {
            return e.getClass().getName() + ": No default collection is available for " + componentName
                    + ". collectionName is empty, but no collection has been created successfully. "
                    + "Check previous CreateCollectionParams result before running this component. "
                    + "Original error: " + rawMessage;
        }
        return e.getClass().getName() + ": " + rawMessage;
    }

    private static boolean isDefaultCollectionLookupError(Exception e) {
        for (StackTraceElement element : e.getStackTrace()) {
            String className = element.getClassName();
            int line = element.getLineNumber();
            if ("custom.components.InsertComp".equals(className) && line >= 40 && line <= 55) {
                return true;
            }
            if ("custom.components.CreateIndexComp".equals(className) && line >= 15 && line <= 22) {
                return true;
            }
            if ("custom.components.LoadCollectionComp".equals(className) && line >= 68 && line <= 72) {
                return true;
            }
            if ("custom.common.CommonFunction".equals(className) && line >= 220 && line <= 305) {
                return true;
            }
        }
        return false;
    }

    public static JSONObject callComponentSchedule(Object object, int index, List<String> parentNodeNames) {
        return callComponentSchedule(object, index, parentNodeNames, snapshotLoopContexts());
    }

    public static JSONObject callComponentSchedule(Object object, int index, List<String> parentNodeNames,
                                                   List<LoopIterationContext> loopContexts) {
        List<String> previousParentNodeNames = snapshotParentNodeName();
        List<LoopIterationContext> previousLoopContexts = snapshotLoopContexts();
        setParentNodeNames(parentNodeNames);
        setLoopContexts(loopContexts);
        try {
            return callComponentSchedule(object, index);
        } finally {
            setParentNodeNames(previousParentNodeNames);
            setLoopContexts(previousLoopContexts);
        }
    }

    public static boolean containsFailureResult(String result) {
        String resultText = Objects.toString(result, "");
        return containsResult(resultText, ResultEnum.FAIL.result)
                || containsResult(resultText, ResultEnum.EXCEPTION.result);
    }

    public static boolean containsFailResult(String result) {
        return containsResult(Objects.toString(result, ""), ResultEnum.FAIL.result);
    }

    private static boolean containsResult(String resultText, String result) {
        return resultText.contains("\"result\":\"" + result + "\"")
                || resultText.contains("result=" + result);
    }

    public static int queryTaskRedisValue() {
        if (skipQtpServer()) {
            return 1;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/query/status?redisKey=" + redisKey;
        String s = HttpClientUtils.doGet(uri);
        log.debug("request qtp:" + s);
        JSONObject jsonObject = JSON.parseObject(s);
        return jsonObject.getInteger("data");
    }

    public static int waitIfTaskPaused() {
        int taskStatus = queryTaskRedisValue();
        if (taskStatus == TaskStatusEnum.STOPPING.status) {
            do {
                log.info("监测到暂停...");
                try {
                    Thread.sleep(1000 * 5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error(e.getMessage());
                    break;
                }
                taskStatus = queryTaskRedisValue();
            } while (taskStatus == TaskStatusEnum.STOPPING.status);
        }
        return taskStatus;
    }

    public static String pauseTaskByQtpServer() {
        if (skipQtpServer()) {
            return "";
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/stop?id=" + taskId;
        String s = HttpClientUtils.doPost(uri);
        log.info("Pause task by qtp-server: {}", s);
        return s;
    }

    public static void updateArgoStatus(int status) {
        if (skipQtpServer()) {
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/argo/status?id=" + taskId + "&argoStatus=" + status;
        String s = HttpClientUtils.doPost(uri);
        log.debug("Update argo status:" + s);
    }

    public static void updateCaseStatus(int status) {
        if (skipQtpServer()) {
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/case/status?id=" + taskId + "&caseStatus=" + status;
        String s = HttpClientUtils.doPost(uri);
        log.debug("Update case status:" + s);
    }

    public static void reportStepResult(String nodeName, String result) {
        reportStepResult(nodeName, result, snapshotParentNodeName());
    }

    public static void reportStepResult(String nodeName, String result, List<String> parentNodeNames) {
        if (skipQtpServer()) {
            return;
        }
        String uri = "http://qtp-server.zilliz.cc/customize-task-details/result/insert";
        JSONObject params = new JSONObject();
        params.put("taskId", taskId);
        params.put("nodeName", nodeName);
        params.put("parentNodeName", parentNodeNames);
        params.put("result", buildReportResult(nodeName, result, parentNodeNames));
        String s = HttpClientUtils.doPostJson(uri, params.toJSONString());
        log.info(parentNodeNames + "[" + nodeName + "]Insert result:" + s);
        log.info("params " + "[" + params.toJSONString() + "]Insert result:" + s);

    }

    public static List<String> snapshotParentNodeName() {
        return new ArrayList<>(PARENT_NODE_NAMES.get());
    }

    private static void setParentNodeNames(List<String> parentNodeNames) {
        PARENT_NODE_NAMES.set(new ArrayList<>(parentNodeNames));
    }

    public static void pushLoopContext(String loopNodeName, List<String> loopParentNodeName) {
        List<LoopIterationContext> loopContexts = snapshotLoopContexts();
        loopContexts.add(new LoopIterationContext(loopNodeName, buildLoopPath(loopParentNodeName, loopNodeName)));
        LOOP_CONTEXTS.set(loopContexts);
    }

    public static void popLoopContext() {
        List<LoopIterationContext> loopContexts = snapshotLoopContexts();
        if (!loopContexts.isEmpty()) {
            loopContexts.remove(loopContexts.size() - 1);
        }
        LOOP_CONTEXTS.set(loopContexts);
    }

    public static List<LoopIterationContext> snapshotLoopContexts() {
        return new ArrayList<>(LOOP_CONTEXTS.get());
    }

    private static void setLoopContexts(List<LoopIterationContext> loopContexts) {
        LOOP_CONTEXTS.set(new ArrayList<>(loopContexts));
    }

    private static void pushParentNodeName(String nodeName) {
        List<String> parentNodeNames = snapshotParentNodeName();
        parentNodeNames.add(nodeName);
        PARENT_NODE_NAMES.set(parentNodeNames);
    }

    private static void popParentNodeName() {
        List<String> parentNodeNames = snapshotParentNodeName();
        if (!parentNodeNames.isEmpty()) {
            parentNodeNames.remove(parentNodeNames.size() - 1);
        }
        PARENT_NODE_NAMES.set(parentNodeNames);
    }

    private static String buildReportResult(String nodeName, String result, List<String> parentNodeNames) {
        LoopIterationContext loopContext = findCurrentLoopContext(nodeName, parentNodeNames);
        if (loopContext == null) {
            return result;
        }

        String aggregateKey = taskId + "|" + loopContext.getLoopPath() + "|" + JSON.toJSONString(parentNodeNames) + "|" + nodeName;
        LoopAggregateState state = LOOP_AGGREGATE_STATES.computeIfAbsent(aggregateKey, key -> new LoopAggregateState());
        return state.update(loopContext, result).toJSONString();
    }

    private static LoopIterationContext findCurrentLoopContext(String nodeName, List<String> parentNodeNames) {
        if (nodeName.startsWith("LoopParams_")) {
            return null;
        }
        List<LoopIterationContext> loopContexts = snapshotLoopContexts();
        for (int i = loopContexts.size() - 1; i >= 0; i--) {
            LoopIterationContext loopContext = loopContexts.get(i);
            if (parentNodeNames.contains(loopContext.getLoopNodeName())) {
                return loopContext;
            }
        }
        return null;
    }

    private static String buildLoopPath(List<String> loopParentNodeName, String loopNodeName) {
        List<String> path = new ArrayList<>(loopParentNodeName);
        path.add(loopNodeName);
        return String.join("/", path);
    }

    private static Object parseResultPayload(String result) {
        try {
            return JSON.parse(result);
        } catch (Exception e) {
            return result;
        }
    }

    public static class LoopIterationContext {
        private final String loopNodeName;
        private final String loopPath;

        public LoopIterationContext(String loopNodeName, String loopPath) {
            this.loopNodeName = loopNodeName;
            this.loopPath = loopPath;
        }

        public String getLoopNodeName() {
            return loopNodeName;
        }

        public String getLoopPath() {
            return loopPath;
        }
    }

    private static class LoopAggregateState {
        private int completedCycles;
        private int abnormalNum;

        synchronized JSONObject update(LoopIterationContext loopContext, String result) {
            completedCycles++;
            if (containsFailureResult(result)) {
                abnormalNum++;
            }

            JSONObject loopAggregate = new JSONObject(true);
            loopAggregate.put("loopAggregate", true);
            loopAggregate.put("loopPath", loopContext.getLoopPath());
            loopAggregate.put("completedCycles", completedCycles);
            loopAggregate.put("abnormalNum", abnormalNum);
            loopAggregate.put("lastResult", parseResultPayload(result));
            return loopAggregate;
        }
    }

    public static void initInstanceStatus(String instanceId, String instanceUri, String image, int status) {
        if (skipQtpServer()) {
            return;
        }
        try {
            String encodedInstanceUri = URLEncoder.encode(instanceUri, StandardCharsets.UTF_8.toString());
            String encodedImage = URLEncoder.encode(image, StandardCharsets.UTF_8.toString());
            String uri = "http://qtp-server.zilliz.cc/customize-task/task/instance/add?id=" + taskId + "&instanceId=" + instanceId + "&instanceUri=" + encodedInstanceUri + "&image=" + encodedImage + "&status=" + status;
            String s = HttpClientUtils.doPost(uri);
            log.info("add instanceId:" + s);
        } catch (Exception e) {
            log.error("Failed to init instance status: " + e.getMessage(), e);
        }
    }

    public static void updateInstanceStatus(String instanceId, String instanceUri, String image, int status) {
        if (skipQtpServer()) {
            return;
        }
        try {
            String encodedInstanceUri = URLEncoder.encode(instanceUri, StandardCharsets.UTF_8.toString());
            String encodedImage = URLEncoder.encode(image, StandardCharsets.UTF_8.toString());
            String uri = "http://qtp-server.zilliz.cc/customize-task/task/instance/update?id=" + taskId + "&instanceId=" + instanceId + "&instanceUri=" + encodedInstanceUri + "&image=" + encodedImage + "&status=" + status;
            String s = HttpClientUtils.doPost(uri);
            log.info("update instanceId:" + s);
        } catch (Exception e) {
            log.error("Failed to update instance status: " + e.getMessage(), e);
        }
    }

    public static List<String> queryReleaseImage() {
        if (skipQtpServer()) {
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
