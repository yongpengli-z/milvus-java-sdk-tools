package custom.components;

import com.alibaba.fastjson.JSON;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.client.globalcluster.GlobalClusterUtils;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.database.response.ListDatabasesResp;
import lombok.extern.slf4j.Slf4j;
import custom.utils.HttpClientUtils;

import java.util.List;

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
        if (uri.contains("tc")) {
            String substring = uri.substring(uri.indexOf("https://") + 8, 28);
            urlPWD =
                    "https://cloud-test.tc-ap-beijing-6.tencent-stage.zilliz.cn/cloud/v1/test/getRootPwd?instanceId="
                            + substring
                            + "";
            String pwdString = HttpClientUtils.doGet(urlPWD);
            log.info("getRootPwd resp:" + pwdString);
            token = "root:" + JSON.parseObject(pwdString).getString("Data");
        } else if (uri.contains("ali") || uri.contains("hwc")) {
            String substring = uri.substring(uri.indexOf("https://") + 8, 28);
            urlPWD =
                    "https://cloud-test.cloud-uat.zilliz.cn/cloud/v1/test/getRootPwd?instanceId="
                            + substring
                            + "";
            String pwdString = HttpClientUtils.doGet(urlPWD);
            log.info("getRootPwd resp:" + pwdString);
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
        useServerlessDatabaseIfNeeded(uri, milvusClientV2);
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        log.info("List collection: " + listCollectionsResp.getCollectionNames());
        return milvusClientV2;
    }

    private static void useServerlessDatabaseIfNeeded(String uri, MilvusClientV2 milvusClientV2) {
        if (uri == null || !uri.toLowerCase().contains(".serverless.")) {
            return;
        }
        try {
            ListDatabasesResp listDatabasesResp = milvusClientV2.listDatabases();
            List<String> databaseNames = listDatabasesResp.getDatabaseNames();
            log.info("Serverless list databases: {}", databaseNames);
            String databaseName = selectServerlessDatabase(uri, databaseNames);
            if (databaseName == null || databaseName.isEmpty()) {
                log.warn("Serverless instance has no usable database, skip useDatabase");
                return;
            }
            milvusClientV2.useDatabase(databaseName);
            log.info("Serverless use database: {}", databaseName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while using serverless database", e);
        }
    }

    private static String selectServerlessDatabase(String uri, List<String> databaseNames) {
        if (databaseNames == null || databaseNames.isEmpty()) {
            return "";
        }
        String expectedDatabaseName = expectedServerlessDatabaseName(uri);
        if (!expectedDatabaseName.isEmpty()) {
            for (String databaseName : databaseNames) {
                if (expectedDatabaseName.equals(databaseName)) {
                    return databaseName;
                }
            }
        }
        if (databaseNames.size() == 1) {
            return databaseNames.get(0);
        }
        for (String databaseName : databaseNames) {
            if (databaseName != null && databaseName.startsWith("db_")) {
                return databaseName;
            }
        }
        return databaseNames.get(0);
    }

    private static String expectedServerlessDatabaseName(String uri) {
        if (uri == null || uri.trim().isEmpty()) {
            return "";
        }
        String host = uri.trim();
        if (host.startsWith("https://")) {
            host = host.substring("https://".length());
        } else if (host.startsWith("http://")) {
            host = host.substring("http://".length());
        }
        int dotIndex = host.indexOf(".");
        if (dotIndex > 0) {
            host = host.substring(0, dotIndex);
        }
        int dashIndex = host.indexOf("-");
        String databaseId = dashIndex >= 0 && dashIndex + 1 < host.length()
                ? host.substring(dashIndex + 1)
                : host;
        return databaseId.isEmpty() ? "" : "db_" + databaseId;
    }

    public static MilvusServiceClient createMilvusClientV1(String uri, String token) {
        // V1 不支持 global cluster 协议，需要先解析出真实的 primary endpoint
        String actualUri = uri;
        if (GlobalClusterUtils.isGlobalEndpoint(uri)) {
            String authorization = token;
            // token 格式可能是 "root:password"，fetchTopology 需要的是原始 token
            String primaryEndpoint = GlobalClusterUtils.fetchTopology(uri, authorization)
                    .getPrimary().getEndpoint();
            if (!primaryEndpoint.startsWith("http://") && !primaryEndpoint.startsWith("https://")) {
                primaryEndpoint = "https://" + primaryEndpoint;
            }
            log.info("Global cluster: resolved primary endpoint for V1 client: {}", primaryEndpoint);
            actualUri = primaryEndpoint;
        }

        MilvusServiceClient milvusServiceClient = null;
        if (!token.equalsIgnoreCase("123456") && !token.equalsIgnoreCase("")) {
            milvusServiceClient = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withUri(actualUri).withToken(token).build());
        }
        if (token.equalsIgnoreCase("123456") || token.equalsIgnoreCase("")) {
            milvusServiceClient = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withUri(actualUri).build());
        }
        log.info("Use clientV1 connecting to DB: " + actualUri);
        return milvusServiceClient;
    }

}
