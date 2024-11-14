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
    public static List<String> globalCollectionNames = new ArrayList<>();
    public static String importUrl = null;
    public static int logInterval = 500;
    public static boolean isCloud = true;
    public static List<Object> recallBaseIdList = new ArrayList<>();

    public static EnvConfig envConfig;

    public static CloudServiceUserInfo cloudServiceUserInfo;

    public static InstanceInfo newInstanceInfo;

    public static void main(String[] args) {
        String env = System.getProperty("env") == null
                ? "awswest"
                : System.getProperty("uri");
        String uri =
                System.getProperty("uri") == null
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

        if (!uri.equalsIgnoreCase("")) {
            newInstanceInfo.setUri(uri);
        }
        if (!uri.equalsIgnoreCase("") & token.equals("")) {
            token = MilvusConnect.provideToken(uri);
            log.info("查询到token:" + token);
        }
        if (!token.equalsIgnoreCase("")) {
            newInstanceInfo.setToken(token);
        }

        EnvEnum envByName = EnvEnum.getEnvByName(env);
        envConfig = ConfigUtils.providerEnvConfig(envByName);
        log.info("当前环境信息:" + envConfig);

        if (!newInstanceInfo.getUri().equalsIgnoreCase("")) {
            milvusClientV2 = MilvusConnect.createMilvusClientV2(newInstanceInfo.getUri(), token);
            importUrl = uri;
            // 初始化环境
            InitialParams initialParamsObj = JSONObject.parseObject(initialParams, InitialParams.class);
            InitialComp.initialRunning(initialParamsObj);
        }
//    // 自动调度
        ComponentSchedule.runningSchedule(customizeParams);
        milvusClientV2.close();
        System.exit(0);
    }
}
