package custom.components;

import custom.entity.AddCollectionFieldParams;
import custom.entity.result.AddCollectionFieldResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddCollectionFieldReq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

public class AddCollectionFieldComp {
    public static AddCollectionFieldResult addCollectionField(AddCollectionFieldParams addCollectionFieldParams) {
        String collectionName = (addCollectionFieldParams.getCollectionName() == null || addCollectionFieldParams.getCollectionName().equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : addCollectionFieldParams.getCollectionName();
        CommonResult commonResult = CommonResult.builder().build();
        AddCollectionFieldResult addCollectionFieldResult = AddCollectionFieldResult.builder().collectionName(collectionName).build();
        // 处理params
        Map<String, Object> analyzerParams = new HashMap<>();
        List<AddCollectionFieldParams.AnalyzerParams> analyzerParamsList =
                addCollectionFieldParams.getAnalyzerParamsList();
        if (analyzerParamsList != null && analyzerParamsList.size() > 0) {
            for (AddCollectionFieldParams.AnalyzerParams params : analyzerParamsList) {
                if (!params.getParamsKey().equalsIgnoreCase("")) {
                    analyzerParams.put(params.getParamsKey(), params.getParamsValue());
                }
            }
        }

        try {
            Object defaultValue = new Object();
            if (addCollectionFieldParams.getDataType() == DataType.Float){
                defaultValue = Float.parseFloat((String) addCollectionFieldParams.getDefaultValue());
            }else if (addCollectionFieldParams.getDataType() == DataType.Int8){
                defaultValue = Short.parseShort((String) addCollectionFieldParams.getDefaultValue());
            }
            else if (addCollectionFieldParams.getDataType() == DataType.Bool){
                defaultValue =  Boolean.parseBoolean((String) addCollectionFieldParams.getDefaultValue());
            }else {
                defaultValue = addCollectionFieldParams.getDefaultValue();
            }

            AddCollectionFieldReq addCollectionFieldReq = AddCollectionFieldReq.builder()
                    .collectionName(collectionName)
                    .fieldName(addCollectionFieldParams.getFieldName())
                    .defaultValue(defaultValue)
                    .dataType(addCollectionFieldParams.getDataType())
                    .isNullable(addCollectionFieldParams.getIsNullable())
                    .maxLength(addCollectionFieldParams.getMaxLength())
                    .autoID(addCollectionFieldParams.getAutoID())
                    .dimension(addCollectionFieldParams.getDimension())
                    .elementType(addCollectionFieldParams.getElementType())
                    .enableAnalyzer(addCollectionFieldParams.getEnableAnalyzer())
                    .enableMatch(addCollectionFieldParams.getEnableMatch())
                    .enableDefaultValue(addCollectionFieldParams.isEnableDefaultValue())
                    .isClusteringKey(addCollectionFieldParams.getIsClusteringKey())
                    .isPartitionKey(addCollectionFieldParams.getIsPartitionKey())
                    .isPrimaryKey(addCollectionFieldParams.getIsPrimaryKey())
                    .maxCapacity(addCollectionFieldParams.getMaxCapacity())
                    .analyzerParams(analyzerParams)
                    .build();
            if (addCollectionFieldParams.getDatabaseName() != null && !addCollectionFieldParams.getDatabaseName().equalsIgnoreCase("")) {
                addCollectionFieldReq.setDatabaseName(addCollectionFieldParams.getDatabaseName());
            }
            milvusClientV2.addCollectionField(addCollectionFieldReq);
            commonResult.setResult(ResultEnum.SUCCESS.result);
        } catch (Exception e) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
            commonResult.setMessage(e.getMessage());
        }
        addCollectionFieldResult.setCommonResult(commonResult);
        return addCollectionFieldResult;

    }
}
