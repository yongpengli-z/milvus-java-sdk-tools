package custom.components;

import custom.entity.HasPartitionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.HasPartitionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.partition.request.HasPartitionReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class HasPartitionComp {
    public static HasPartitionResult hasPartition(HasPartitionParams hasPartitionParams) {
        String collectionName = (hasPartitionParams.getCollectionName() == null ||
                hasPartitionParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : hasPartitionParams.getCollectionName();
        try {
            log.info("Check partition [{}] in collection [{}]", hasPartitionParams.getPartitionName(), collectionName);
            Boolean has = milvusClientV2.hasPartition(HasPartitionReq.builder()
                    .collectionName(collectionName)
                    .partitionName(hasPartitionParams.getPartitionName())
                    .build());
            log.info("Has partition [{}]: {}", hasPartitionParams.getPartitionName(), has);
            return HasPartitionResult.builder()
                    .hasPartition(has)
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Has partition [{}] failed: {}", hasPartitionParams.getPartitionName(), e.getMessage());
            return HasPartitionResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
