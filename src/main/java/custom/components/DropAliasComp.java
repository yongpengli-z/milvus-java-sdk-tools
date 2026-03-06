package custom.components;

import custom.entity.DropAliasParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DropAliasResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.utility.request.DropAliasReq;
import lombok.extern.slf4j.Slf4j;

import static custom.BaseTest.milvusClientV2;

@Slf4j
public class DropAliasComp {
    public static DropAliasResult dropAlias(DropAliasParams dropAliasParams) {
        try {
            log.info("Drop alias [{}]", dropAliasParams.getAlias());
            DropAliasReq dropAliasReq = DropAliasReq.builder()
                    .alias(dropAliasParams.getAlias())
                    .build();
            if (dropAliasParams.getDatabaseName() != null && !dropAliasParams.getDatabaseName().equalsIgnoreCase("")) {
                dropAliasReq.setDatabaseName(dropAliasParams.getDatabaseName());
            }
            milvusClientV2.dropAlias(dropAliasReq);
            log.info("Drop alias [{}] success", dropAliasParams.getAlias());
            return DropAliasResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.SUCCESS.result)
                            .message(String.format("Drop alias [%s] success!", dropAliasParams.getAlias()))
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Drop alias [{}] failed: {}", dropAliasParams.getAlias(), e.getMessage());
            return DropAliasResult.builder()
                    .commonResult(CommonResult.builder()
                            .result(ResultEnum.WARNING.result)
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
