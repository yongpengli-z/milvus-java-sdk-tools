package custom;

import com.alibaba.fastjson.JSONObject;
import custom.common.ComponentSchedule;
import custom.components.InitialComp;
import custom.components.MilvusConnect;
import custom.entity.InitialParams;
import io.milvus.v2.client.MilvusClientV2;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author yongpeng.li @Date 2024/6/3 17:40
 */
@Slf4j
public class BaseTest {
  public static MilvusClientV2 milvusClientV2=null;
  public static List<String> globalCollectionNames=new ArrayList<>();

  public static int logInterval=1000;
  public static boolean isCloud=true;

  public static void main(String[] args) {
    String uri =
        System.getProperty("uri") == null
            ? ""
            : System.getProperty("uri");
    String token =
        System.getProperty("token") == null || System.getProperty("token").equals("")
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


    if (token.equals("")) {
      token = MilvusConnect.provideToken(uri);
    }

    milvusClientV2 = MilvusConnect.createMilvusClientV2(uri, token);

    // 初始化环境
    InitialParams initialParamsObj = JSONObject.parseObject(initialParams, InitialParams.class);
    InitialComp.initialRunning(initialParamsObj);
//    // 自动调度
    ComponentSchedule.runningSchedule(customizeParams);
    milvusClientV2.close();
    System.exit(0);
  }
}
