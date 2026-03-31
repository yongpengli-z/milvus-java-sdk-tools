package custom;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.components.InitialComp;
import custom.components.MilvusConnect;
import custom.config.CloudServiceUserInfo;
import custom.config.EnvConfig;
import custom.config.EnvEnum;
import custom.entity.InitialParams;
import custom.pojo.InstanceInfo;
import custom.utils.ConfigUtils;
import io.milvus.client.MilvusServiceClient;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author yongpeng.li @Date 2024/6/3 17:40
 */
@Slf4j
public class BaseTest {
    public static MilvusClientV2 milvusClientV2 = null;
    public static MilvusServiceClient milvusClientV1 = null;
    public static List<String> globalCollectionNames = new ArrayList<>();
    public static String importUrl = null;
    public static int logInterval = 500;
    public static boolean isCloud = true;
    public static List<Object> recallBaseIdList = new ArrayList<>();

    public static EnvConfig envConfig = new EnvConfig();

    public static CloudServiceUserInfo cloudServiceUserInfo = new CloudServiceUserInfo();

    public static InstanceInfo newInstanceInfo = new InstanceInfo();

    public static String redisKey;
    public static int taskId;
    // 定义根节点
    public static List<String> parentNodeName = new ArrayList<>();

    public static EnvEnum envEnum;

    public static int insertCollectionIndex = 0;
    public static int searchCollectionIndex = 0;
    public static int queryCollectionIndex = 0;
    public static int upsertCollectionIndex = 0;

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            taskId = Integer.parseInt(System.getProperty("taskId") == null
                    ? ""
                    : System.getProperty("taskId"));
            String env = System.getProperty("env") == null
                    ? ""
                    : System.getProperty("env");
            String uri =
                    System.getProperty("uri") == null || System.getProperty("uri").trim().equals("")
                            ? ""
                            : System.getProperty("uri");
            String token =
                    System.getProperty("token") == null || System.getProperty("token").trim().equals("")
                            ? ""
                            : System.getProperty("token");
            String initialParams =
                    System.getProperty("initial_params") == null
                            || System.getProperty("initial_params").equals("")
                            ? ""
                            : System.getProperty("initial_params");

            String customizeParams =
                    System.getProperty("customize_params") == null
                            || System.getProperty("customize_params").equals("")
                            ? ""
                            : System.getProperty("customize_params");
//            log.info("***********customizeParams*********"+customizeParams);
            log.info("========== [阶段1] 参数解析完成, taskId={}, env={}, uri={} ==========", taskId, env, uri.isEmpty() ? "(空)" : uri);
            redisKey = "customize_task_" + taskId;
            if (!uri.equalsIgnoreCase("")) {
                newInstanceInfo.setUri(uri);
                if (uri.contains("ali") || uri.contains("tc") || uri.contains("aws") || uri.contains("gcp") || uri.contains("az") || uri.contains("hwc")) {
                    String substring = uri.substring(uri.indexOf("https://") + 8, 28);
                    log.info("instance-id:" + substring);
                    newInstanceInfo.setInstanceId(substring);
                }
            }
            if (!uri.equalsIgnoreCase("") && token.equals("")) {
                token = MilvusConnect.provideToken(uri);
                log.info("查询到token:" + token);
            }
            newInstanceInfo.setToken(token);
            envEnum = EnvEnum.getEnvByName(env);
            //先更新argo任务状态
            ComponentSchedule.updateArgoStatus(1);
//            log.info("EnvEnum:"+envByName);
            if (!env.equalsIgnoreCase("devops") && !env.equalsIgnoreCase("fouram")) {
                envConfig = ConfigUtils.providerEnvConfig(envEnum);
                log.info("当前环境信息:" + envConfig);
            }
            log.info("newInstanceInfo:" + newInstanceInfo.toString());
            log.info("========== [阶段2] 环境配置完成，准备连接Milvus ==========");
            if (newInstanceInfo.getUri() != null) {
                log.info("创建milvusClientV2，uri:" + newInstanceInfo.getUri());
                milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), newInstanceInfo.getToken());
                milvusClientV1 = MilvusConnect.createMilvusClientV1(newInstanceInfo.getUri(), newInstanceInfo.getToken());
                importUrl = uri;
                // 初始化环境
                InitialParams initialParamsObj = JSONObject.parseObject(initialParams, InitialParams.class);
                InitialComp.initialRunning(initialParamsObj);
            }
            log.info("========== [阶段3] Milvus连接完成，开始执行调度 ==========");
//        // 自动调度
            List<JSONObject> results = ComponentSchedule.runningSchedule(customizeParams);
            // 检查是否有步骤失败（result为exception）
            boolean hasFailed = false;
            for (JSONObject result : results) {
                String resultStr = result.toJSONString();
                if (resultStr.contains("\"result\":\"exception\"")) {
                    hasFailed = true;
                    break;
                }
            }
            ComponentSchedule.updateCaseStatus(hasFailed ? -1 : 10);
            if (hasFailed) {
                log.error("任务存在失败步骤，退出码设为1");
                exitCode = 1;
            }
        } catch (Exception e) {
            log.error("Task execution failed: {} - {}", e.getClass().getName(), e.getMessage());
            log.error("异常堆栈:", e);
            // 打印完整异常链
            Throwable cause = e.getCause();
            int depth = 1;
            while (cause != null) {
                log.error("  Caused by (depth={}): {} - {}", depth, cause.getClass().getName(), cause.getMessage());
                cause = cause.getCause();
                depth++;
            }
            exitCode = 1;
            try {
                ComponentSchedule.updateCaseStatus(-1);
            } catch (Exception updateEx) {
                log.error("更新任务状态也失败了: {}", updateEx.getMessage());
            }
        } finally {
            if (milvusClientV2 != null) {
                try { milvusClientV2.close(); } catch (Exception ignored) {}
            }
            if (milvusClientV1 != null) {
                try { milvusClientV1.close(); } catch (Exception ignored) {}
            }
            System.exit(exitCode);
        }
    }
}
