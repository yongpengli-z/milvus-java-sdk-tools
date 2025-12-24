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
//        log.info("***********customizeParams*********"+customizeParams);
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
//        log.info("EnvEnum:"+envByName);
        if (!env.equalsIgnoreCase("devops") && !env.equalsIgnoreCase("fouram")) {
            envConfig = ConfigUtils.providerEnvConfig(envEnum);
            log.info("当前环境信息:" + envConfig);
        }
        log.info("newInstanceInfo:" + newInstanceInfo.toString());
        if (newInstanceInfo.getUri() != null) {
            log.info("创建milvusClientV2，uri:" + newInstanceInfo.getUri());
            milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), newInstanceInfo.getToken());
            milvusClientV1 = MilvusConnect.createMilvusClientV1(newInstanceInfo.getUri(), newInstanceInfo.getToken());
            importUrl = uri;
            // 初始化环境
            InitialParams initialParamsObj = JSONObject.parseObject(initialParams, InitialParams.class);
            InitialComp.initialRunning(initialParamsObj);
        }
//    // 自动调度
        ComponentSchedule.runningSchedule(customizeParams);
        if (milvusClientV2 != null) {
            milvusClientV2.close();
            milvusClientV1.close();
        }
        ComponentSchedule.updateCaseStatus(10);
        System.exit(0);
    }
}
