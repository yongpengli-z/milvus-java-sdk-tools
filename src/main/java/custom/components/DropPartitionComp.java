package custom.components;

import custom.entity.DropPartitionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropPartitionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.partition.request.DropPartitionReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DropPartitionComp {
    public static DropPartitionResult dropPartition(DropPartitionParams dropPartitionParams) {
        String collectionName = (dropPartitionParams.getCollectionName() == null ||
                dropPartitionParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : dropPartitionParams.getCollectionName();
        try {
            log.info("Drop partition [{}] in collection [{}]", dropPartitionParams.getPartitionName(), collectionName);
            milvusClientV2.dropPartition(DropPartitionReq.builder()
                    .collectionName(collectionName)
                    .partitionName(dropPartitionParams.getPartitionName())
                    .build());
            log.info("Drop partition [{}] success", dropPartitionParams.getPartitionName());
            return DropPartitionResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Drop partition [{}] failed: {}", dropPartitionParams.getPartitionName(), e.getMessage());
            return DropPartitionResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
