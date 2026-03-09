package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateIndexParams;
import custom.entity.result.CommonResult;
import custom.entity.result.CreateIndexResult;
import custom.entity.result.ResultEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;

@Slf4j
public class CreateIndexComp {
    public static CreateIndexResult CreateIndex(CreateIndexParams createIndexParams) {
        String collectionName = (createIndexParams.getCollectionName() == null || createIndexParams.getCollectionName().equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : createIndexParams.getCollectionName();
        CommonResult commonResult;
        CreateIndexResult createIndexResult = CreateIndexResult.builder().build();
        String databaseName = "";
        if (createIndexParams.getDatabaseName() != null && !createIndexParams.getDatabaseName().equalsIgnoreCase("")) {
            databaseName = createIndexParams.getDatabaseName();
        }
        try {
            long startTimeTotal = System.currentTimeMillis();
            CommonFunction.createCommonIndex(collectionName, createIndexParams.getIndexParams(), databaseName);
            long endTimeTotal = System.currentTimeMillis();
            float indexCost = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
            commonResult = CommonResult.builder().result(ResultEnum.SUCCESS.result).build();
            createIndexResult.setCostTimes(indexCost);
        } catch (Exception e) {
            log.error(e.getMessage());
            commonResult = CommonResult.builder().result(ResultEnum.EXCEPTION.result)
                    .message(e.getMessage()).build();
        }
        // assertions
        List<String> assertMessages = new ArrayList<>();
        if (commonResult.getResult().equals(ResultEnum.EXCEPTION.result)) {
            assertMessages.add("[ASSERT FAIL] createIndex exception: " + commonResult.getMessage());
        }
        if (commonResult.getResult().equals(ResultEnum.SUCCESS.result) && createIndexResult.getCostTimes() <= 0) {
            assertMessages.add("[ASSERT WARN] createIndex costTimes <= 0");
        }
        if (!assertMessages.isEmpty()) {
            log.warn("CreateIndex assertions: " + assertMessages);
        }
        createIndexResult.setIndexParams(createIndexParams.getIndexParams());
        createIndexResult.setCommonResult(commonResult);
        createIndexResult.setCollectionName(collectionName);
        createIndexResult.setAssertMessages(assertMessages);
        return createIndexResult;
    }
}
