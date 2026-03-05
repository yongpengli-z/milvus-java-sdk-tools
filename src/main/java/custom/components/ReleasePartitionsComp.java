package custom.components;

import custom.entity.ReleasePartitionsParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ReleasePartitionsResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.partition.request.ReleasePartitionsReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class ReleasePartitionsComp {
    public static ReleasePartitionsResult releasePartitions(ReleasePartitionsParams releasePartitionsParams) {
        String collectionName = (releasePartitionsParams.getCollectionName() == null ||
                releasePartitionsParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : releasePartitionsParams.getCollectionName();
        try {
            log.info("Release partitions {} in collection [{}]", releasePartitionsParams.getPartitionNames(), collectionName);
            milvusClientV2.releasePartitions(ReleasePartitionsReq.builder()
                    .collectionName(collectionName)
                    .partitionNames(releasePartitionsParams.getPartitionNames())
                    .build());
            log.info("Release partitions {} success", releasePartitionsParams.getPartitionNames());
            return ReleasePartitionsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Release partitions {} failed: {}", releasePartitionsParams.getPartitionNames(), e.getMessage());
            return ReleasePartitionsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
