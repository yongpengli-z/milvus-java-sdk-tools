package custom.components;

import custom.entity.ListIndexesParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ListIndexesResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.index.request.ListIndexesReq;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ListIndexesComp {
    public static ListIndexesResult listIndexes(ListIndexesParams listIndexesParams) {
        String collectionName = (listIndexesParams.getCollectionName() == null ||
                listIndexesParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : listIndexesParams.getCollectionName();
        try {
            log.info("List indexes for collection [{}]", collectionName);
            List<String> indexNames = milvusClientV2.listIndexes(ListIndexesReq.builder()
                    .collectionName(collectionName)
                    .build());
            log.info("Collection [{}] indexes: {}", collectionName, indexNames);
            return ListIndexesResult.builder()
                    .indexNames(indexNames)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("List indexes for collection [{}] failed: {}", collectionName, e.getMessage());
            return ListIndexesResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
