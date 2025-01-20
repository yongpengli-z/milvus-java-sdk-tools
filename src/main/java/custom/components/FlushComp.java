package custom.components;

import custom.entity.FlushParams;
import custom.entity.result.CommonResult;
import custom.entity.result.FlushResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.collection.response.ListCollectionsResp;
import io.milvus.v2.service.utility.request.FlushReq;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class FlushComp {
    public static FlushResult flush(FlushParams flushParams) {
        List<String> collectionList = new ArrayList<>();
        if (flushParams.isFlushAll()) {
            log.info("flush all collection !");
            ListCollectionsResp listCollectionsResp = milvusClientV2.listCollections();
            collectionList = listCollectionsResp.getCollectionNames();
        } else {
            String collectionName = (flushParams.getCollectionName() == null || flushParams.getCollectionName().equalsIgnoreCase("")) ?
                    globalCollectionNames.get(globalCollectionNames.size() - 1) : flushParams.getCollectionName();
            log.info("flush collection [" + collectionName + "]");
            collectionList.add(collectionName);
        }
        CommonResult commonResult;
        try {
            milvusClientV2.flush(FlushReq.builder()
                    .collectionNames(collectionList)
                    .build());
            commonResult = CommonResult.builder()
                    .result(ResultEnum.SUCCESS.result)
                    .build();
        } catch (Exception e) {
            commonResult = CommonResult.builder()
                    .result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage())
                    .build();
        }
        return FlushResult.builder().commonResult(commonResult).build();
    }
}
