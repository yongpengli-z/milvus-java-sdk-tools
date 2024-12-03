package custom.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import custom.components.*;
import custom.entity.*;
import custom.entity.result.*;
import custom.utils.HttpClientUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
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
        }
        if (object instanceof CreateIndexParams) {
            log.info("*********** < create index > ***********");
            CreateIndexResult createIndexResult = CreateIndexComp.CreateIndex((CreateIndexParams) object);
            jsonObject.put("CreateIndex_" + index, createIndexResult);
        }
        if (object instanceof LoadParams) {
            log.info("*********** < load collection > ***********");
            LoadResult loadResult = LoadCollectionComp.loadCollection((LoadParams) object);
            jsonObject.put("LoadCollection_" + index, loadResult);
        }
        if (object instanceof InsertParams) {
            log.info("*********** < insert data > ***********");
            InsertResult insertResult = InsertComp.insertCollection((InsertParams) object);
            jsonObject.put("Insert_" + index, insertResult);
        }
        if (object instanceof SearchParams) {
            log.info("*********** < search collection > ***********");
            SearchResultA searchResultA = SearchComp.searchCollection((SearchParams) object);
            jsonObject.put("Search_" + index, searchResultA);
        }
        if (object instanceof ReleaseParams) {
            log.info("*********** < release collection > ***********");
            ReleaseResult releaseResult = ReleaseCollectionComp.releaseCollection((ReleaseParams) object);
            jsonObject.put("ReleaseCollection_" + index, releaseResult);
        }
        if (object instanceof DropCollectionParams) {
            log.info("*********** < drop collection > ***********");
            DropCollectionResult dropCollectionResult = DropCollectionComp.dropCollection((DropCollectionParams) object);
            jsonObject.put("DropCollection_" + index, dropCollectionResult);
        }
        if (object instanceof RecallParams) {
            log.info("*********** < recall > ***********");
            RecallComp.calcRecall((RecallParams) object);
        }
        if (object instanceof WaitParams) {
            log.info("*********** < Wait > ***********");
            WaitResult waitResult = WaitComp.wait((WaitParams) object);
            jsonObject.put("Wait" + index, waitResult);
        }
        if (object instanceof UpsertParams) {
            log.info("*********** < upsert data > ***********");
            UpsertResult upsertResult = UpsertComp.upsertCollection((UpsertParams) object);
            jsonObject.put("Upsert_" + index, upsertResult);
        }
        if (object instanceof ConcurrentParams) {
            log.info("*********** < Concurrent Operator > ***********");
            parentNodeName.add("ConcurrentParams_" + index);
            List<JSONObject> jsonObjects = ConcurrentComp.concurrentComp((ConcurrentParams) object);
            parentNodeName.remove(parentNodeName.size() - 1);
            jsonObject.put("Concurrent_" + index, jsonObjects);
        }
        if (object instanceof QueryParams) {
            log.info("*********** < query collection > ***********");
            QueryResult queryResult = QueryComp.queryCollection((QueryParams) object);
            jsonObject.put("Query_" + index, queryResult);
        }
        if (object instanceof DropIndexParams) {
            log.info("*********** < drop index > ***********");
            DropIndexResult dropIndexResult = DropIndexComp.dropIndex((DropIndexParams) object);
            jsonObject.put("DropIndex_" + index, dropIndexResult);
        }
        if (object instanceof LoopParams) {
            log.info("*********** < Loop Operator> ***********");
            parentNodeName.add("LoopParams_" + index);
            JSONObject loopJO = LoopComp.loopComp((LoopParams) object);
            parentNodeName.remove(parentNodeName.size() - 1);
            jsonObject.put("Loop_" + index, loopJO);
        }
        if (object instanceof CreateInstanceParams) {
            log.info("*********** < create instance> ***********");
            CreateInstanceResult createInstanceResult = CreateInstanceComp.createInstance((CreateInstanceParams) object);
            jsonObject.put("CreateInstance_" + index, createInstanceResult);
        }
        if (object instanceof RollingUpgradeParams) {
            log.info("*********** < rolling upgrade > ***********");
            RollingUpgradeResult rollingUpgradeResult = RollingUpgradeComp.rollingUpgradeInstance((RollingUpgradeParams) object);
            jsonObject.put("RollingUpgrade_" + index, rollingUpgradeResult);
        }
        if (object instanceof DeleteInstanceParams) {
            log.info("*********** < delete instance > ***********");
            DeleteInstanceResult deleteInstanceResult = DeleteInstanceComp.deleteInstance((DeleteInstanceParams) object);
            jsonObject.put("DeleteInstance_" + index, deleteInstanceResult);
        }
        if (object instanceof StopInstanceParams) {
            log.info("*********** < stop instance > ***********");
            StopInstanceResult stopInstanceResult = StopInstanceComp.stopInstance((StopInstanceParams) object);
            jsonObject.put("StopInstance_" + index, stopInstanceResult);
        }
        if (object instanceof ResumeInstanceParams) {
            log.info("*********** < resume instance > ***********");
            ResumeInstanceResult resumeInstanceResult = ResumeInstanceComp.resumeInstance((ResumeInstanceParams) object);
            jsonObject.put("ResumeInstance_" + index, resumeInstanceResult);
        }
        if (object instanceof ModifyParams) {
            log.info("*********** < modify params > ***********");
            ModifyParamsResult modifyParamsResult = ModifyParamsComp.modifyParams((ModifyParams) object);
            jsonObject.put("ModifyParams_" + index, modifyParamsResult);
        }
        if (object instanceof RestartInstanceParams) {
            log.info("*********** < restart instance > ***********");
            RestartInstanceResult restartInstanceResult = RestartInstanceComp.restartInstance((RestartInstanceParams) object);
            jsonObject.put("RestartInstance_" + index, restartInstanceResult);
        }
        return jsonObject;
    }

    public static int queryTaskRedisValue() {
        String uri = "http://qtp-server.zilliz.cc/customize-task/query/status?redisKey=" + redisKey;
        String s = HttpClientUtils.doGet(uri);
        log.info("request qtp:" + s);
        JSONObject jsonObject = JSON.parseObject(s);
        return jsonObject.getInteger("data");
    }

    public static void updateArgoStatus(int status) {
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/argo/status";
        HashMap<String, Object> params = new HashMap<>();
        params.put("id", taskId);
        params.put("argoStatus", status);
        String s = HttpClientUtils.doPost(uri, null, params);
        log.info("Update argo status:" + s);
    }

    public static void updateCaseStatus(int status) {
        String uri = "http://qtp-server.zilliz.cc/customize-task/task/case/status";
        HashMap<String, Object> params = new HashMap<>();
        params.put("id", taskId);
        params.put("caseStatus", status);
        log.info("params:" + params);
        String s = HttpClientUtils.doPost(uri, null, params);
        log.info("Update case status:" + s);
    }
}
