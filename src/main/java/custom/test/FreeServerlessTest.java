package custom.test;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.response.ListCollectionsResp;

public class FreeServerlessTest {
    public static void main(String[] args) {
        String uri = "https://in05-7695bce85f169d6.serverless.gcp-us-west1.cloud-uat3.zilliz.com";
        String token = "8da3c7e60b46933b19ab9282b64096ff693f5756c64998b6c60967aa6b2e8c68ac0769216f1657859b3a0247d7b2069dda923908";
        MilvusClientV2 milvusClientV2 = new MilvusClientV2(ConnectConfig.builder().uri(uri).token(token).build());
        ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
        System.out.println(listCollectionsResp);
    }
}
