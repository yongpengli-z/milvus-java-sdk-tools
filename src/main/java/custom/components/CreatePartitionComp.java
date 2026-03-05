package custom.components;

import custom.entity.CreatePartitionParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreatePartitionResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.partition.request.CreatePartitionReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
public class CreatePartitionComp {
    public static CreatePartitionResult createPartition(CreatePartitionParams createPartitionParams) {
        String collectionName = (createPartitionParams.getCollectionName() == null ||
                createPartitionParams.getCollectionName().equalsIgnoreCase(""))
                ? globalCollectionNames.get(globalCollectionNames.size() - 1)
                : createPartitionParams.getCollectionName();
        try {
            log.info("Create partition [{}] in collection [{}]", createPartitionParams.getPartitionName(), collectionName);
            milvusClientV2.createPartition(CreatePartitionReq.builder()
                    .collectionName(collectionName)
                    .partitionName(createPartitionParams.getPartitionName())
                    .build());
            log.info("Create partition [{}] success", createPartitionParams.getPartitionName());
            return CreatePartitionResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Create partition [{}] failed: {}", createPartitionParams.getPartitionName(), e.getMessage());
            return CreatePartitionResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.EXCEPTION.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
