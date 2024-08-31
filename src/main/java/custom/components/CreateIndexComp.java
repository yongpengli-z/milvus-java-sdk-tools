package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateIndexParams;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreateIndexComp {
    public static void CreateIndex(CreateIndexParams createIndexParams){
        String collectionName=(createIndexParams.getCollectionName() == null || createIndexParams.getCollectionName().equals("")) ? globalCollectionNames.get(0) : createIndexParams.getCollectionName();
        CommonFunction.createCommonIndex(collectionName,createIndexParams.getIndexParams());
    }
}
