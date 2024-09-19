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

  public static int logInterval=500;
  public static boolean isCloud=true;
  public static List<Object> recallBaseIdList=new ArrayList<>();

  public static void main(String[] args) {
    String uri = args[0];
    String token = args[1];
    String initialParams = args[2];
    String customizeParams= args[3];
    System.out.println(uri);
    System.out.println(token);
    System.out.println(initialParams);
    System.out.println(customizeParams);

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
