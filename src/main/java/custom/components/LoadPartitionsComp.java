package custom.components;

import custom.entity.LoadPartitionsParams;
import custom.entity.result.CommonResult;
import custom.entity.result.LoadPartitionsResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.partition.request.LoadPartitionsReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class LoadPartitionsComp {
    public static LoadPartitionsResult loadPartitions(LoadPartitionsParams loadPartitionsParams) {
        String collectionName = (loadPartitionsParams.getCollectionName() == null ||
                loadPartitionsParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : loadPartitionsParams.getCollectionName();
        try {
            log.info("Load partitions {} in collection [{}]", loadPartitionsParams.getPartitionNames(), collectionName);
            milvusClientV2.loadPartitions(LoadPartitionsReq.builder()
                    .collectionName(collectionName)
                    .partitionNames(loadPartitionsParams.getPartitionNames())
                    .build());
            log.info("Load partitions {} success", loadPartitionsParams.getPartitionNames());
            return LoadPartitionsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Load partitions {} failed: {}", loadPartitionsParams.getPartitionNames(), e.getMessage());
            return LoadPartitionsResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
