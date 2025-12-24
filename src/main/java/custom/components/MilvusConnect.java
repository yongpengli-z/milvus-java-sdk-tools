package custom.components;

import com.alibaba.fastjson.JSON;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import lombok.extern.slf4j.Slf4j;
import custom.utils.HttpClientUtils;

import static custom.BaseTest.isCloud;

/**
 * @Author yongpeng.li @Date 2024/6/4 15:17
 */
@Slf4j
public class MilvusConnect {
    public static String provideToken(String uri) {
        // 获取root密码
        String token = "";
        String urlPWD = null;
        if (uri.contains("ali") || uri.contains("tc") || uri.contains("hwc")) {
            String substring = uri.substring(uri.indexOf("https://") + 8, 28);
            urlPWD =
                    "https://cloud-test.cloud-uat.zilliz.cn/cloud/v1/test/getRootPwd?instanceId="
                            + substring
                            + "";
            String pwdString = HttpClientUtils.doGet(urlPWD);
            token = "root:" + JSON.parseObject(pwdString).getString("Data");
        } else if (uri.contains("aws") || uri.contains("gcp") || uri.contains("az")) {
            String substring = uri.substring(uri.indexOf("https://") + 8, 28);
            urlPWD =
                    "https://cloud-test.cloud-uat3.zilliz.com/cloud/v1/test/getRootPwd?instanceId="
                            + substring
                            + "";
            String pwdString = HttpClientUtils.doGet(urlPWD);
            log.info("pwdString:" + pwdString);
            token = "root:" + JSON.parseObject(pwdString).getString("Data");
        } else {
            token = "";
            isCloud = false;
        }
        return token;
    }

    public static MilvusClientV2 createMilvusClientV2(String uri, String token) {
        ConnectConfig build = ConnectConfig.builder().uri(uri).build();
        if (!token.equalsIgnoreCase("123456") && !token.equalsIgnoreCase("")) {
            build.setToken(token);
        }
        MilvusClientV2 milvusClientV2 = new MilvusClientV2(build);
        log.info("Connecting to DB: " + uri);
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        log.info("List collection: " + listCollectionsResp.getCollectionNames());
        return milvusClientV2;
    }

    public static MilvusServiceClient createMilvusClientV1(String uri, String token) {
        MilvusServiceClient milvusServiceClient = null;
        if (!token.equalsIgnoreCase("123456") && !token.equalsIgnoreCase("")) {
            milvusServiceClient = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withUri(uri).withToken(token).build());
        }
        if (token.equalsIgnoreCase("123456") || token.equalsIgnoreCase("")) {
            milvusServiceClient = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withUri(uri).build());
        }
        log.info("Use clientV1 connecting to DB: " + uri);
        return milvusServiceClient;
    }

}
