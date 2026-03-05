package custom.components;

import custom.entity.ListPartitionsParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ListPartitionsResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.partition.request.ListPartitionsReq;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ListPartitionsComp {
    public static ListPartitionsResult listPartitions(ListPartitionsParams listPartitionsParams) {
        String collectionName = (listPartitionsParams.getCollectionName() == null ||
                listPartitionsParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : listPartitionsParams.getCollectionName();
        try {
            log.info("List partitions in collection [{}]", collectionName);
            List<String> partitionNames = milvusClientV2.listPartitions(ListPartitionsReq.builder()
                    .collectionName(collectionName)
                    .build());
            log.info("List partitions success, partitions: {}", partitionNames);
            return ListPartitionsResult.builder()
                    .partitionNames(partitionNames)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("List partitions in collection [{}] failed: {}", collectionName, e.getMessage());
            return ListPartitionsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
