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
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author yongpeng.li @Date 2024/6/3 17:40
 */
@Slf4j
public class BaseTest {
    public static MilvusClientV2 milvusClientV2 = null;
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
    public static List<String> parentNodeName=new ArrayList<>();

    /*public static void main(String[] args) {
        System.out.println("参数个数："+args.length);
         taskId = Integer.parseInt(args[0]);
        //先更新argo任务状态
        ComponentSchedule.updateArgoStatus(1);
        String env = args[1];
        String uri = args[2];
        String token =args[3];
        String initialParams = args[4];
        String customizeParams =args[5];
        redisKey = "customize_task_" + taskId;
        if (!uri.equalsIgnoreCase("")) {
            newInstanceInfo.setUri(uri);
            if (uri.contains("ali") || uri.contains("tc") || uri.contains("aws") || uri.contains("gcp") || uri.contains("az")) {
                String substring = uri.substring(uri.indexOf("https://") + 8, 28);
                newInstanceInfo.setInstanceId(substring);
            }
        }
        if (!uri.equalsIgnoreCase("") & token.equals("")) {
            token = MilvusConnect.provideToken(uri);
            log.info("查询到token:" + token);
        }
        if (!token.equalsIgnoreCase("")) {
            newInstanceInfo.setToken(token);
        }

        EnvEnum envByName = EnvEnum.getEnvByName(env);
//        log.info("EnvEnum:"+envByName);
        envConfig = ConfigUtils.providerEnvConfig(envByName);
        log.info("当前环境信息:" + envConfig);
        log.info("newInstanceInfo:" + newInstanceInfo.toString());
        if (newInstanceInfo.getUri() != null) {
            log.info("创建milvusClientV2，uri:" + newInstanceInfo.getUri());
            milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), newInstanceInfo.getToken());
            importUrl = uri;
            // 初始化环境
            InitialParams initialParamsObj = JSONObject.parseObject(initialParams, InitialParams.class);
            InitialComp.initialRunning(initialParamsObj);
        }
//    // 自动调度
        ComponentSchedule.runningSchedule(customizeParams);
        milvusClientV2.close();
        ComponentSchedule.updateCaseStatus(10);
        System.exit(0);
    }*/

    public static void main(String[] args) {
        // 第一次直接连接
        String uri="https://in01-95b32b8444ec645.tc-ap-nanjing.cloud-uat.zilliz.cn:443";
        String token="1e2f2b0aec61f5ff356d2f78e71edea61a22fb5c163eebb5f41ed1afdaab6f2c5618f38ee1c908aa38b59b50c107d35a03937c4b";
        MilvusClientV2 milvusClientV2 = new MilvusClientV2(
                ConnectConfig.builder().uri(uri).token(token).connectTimeoutMs(30000).build()
        );
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        log.info("First list collection:"+ listCollectionsResp.getCollectionNames());
         // 第二次
        String env = args[1];
        String uri2 = args[2];
        String token2 =args[3];
        MilvusClientV2 milvusClientV22 = new MilvusClientV2(
                ConnectConfig.builder().uri(uri2).token(token2).connectTimeoutMs(30000).build()
        );
        ListCollectionsResp listCollectionsResp2 = milvusClientV22.listCollections();
        log.info("Second list collection:"+ listCollectionsResp2.getCollectionNames());
    }
}
