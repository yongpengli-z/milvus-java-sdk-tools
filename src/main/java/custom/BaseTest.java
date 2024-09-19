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
    String uri = "https://in01-2fe732db8ac0299.aws-us-west-2.vectordb-uat3.zillizcloud.com:19531";
    String token = "6aff239bed5702130e09ad03a3379a71034a3e7b4160de384dce58c501e5bf98e49816c670b4b6335f51276c294976ce6ba25fa4";
    String initialParams = "{\"cleanCollection\":true}";
    String customizeParams =  "{\"CreateCollectionParams_0\":{\"shardNum\":1,\"enableDynamic\":false,\"enableMmap\":false,\"fieldParamsList\":[{\"dataType\":\"Int64\",\"primaryKey\":true},{\"dataType\":\"FloatVector\",\"dim\":768,\"primaryKey\":false}],\"numPartitions\":0,\"collectionName\":\"\"},\"LoadParams_3\":{\"loadAll\":true},\"CreateIndexParams_2\":{\"collectionName\":\"\",\"indexParams\":[]},\"InsertParams_4\":{\"batchSize\":1000,\"collectionName\":\"\",\"numConcurrency\":1,\"numEntries\":100000},\"SearchParams_5\":{\"collectionName\":\"\",\"filter\":\"\",\"nq\":5,\"numConcurrency\":50,\"outputs\":[],\"randomVector\":true,\"runningMinutes\":10,\"topK\":10,\"searchLevel\":1}}";
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
