package custom.components;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import custom.entity.AddCollectionFieldParams;
import custom.entity.result.AddCollectionFieldResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddCollectionFieldReq;

import java.lang.annotation.ElementType;
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
            Object defaultValue = null;
            if (addCollectionFieldParams.getDataType() == DataType.Float) {
                defaultValue = Float.parseFloat(addCollectionFieldParams.getDefaultValue());
            } else if (addCollectionFieldParams.getDataType() == DataType.Int8) {
                defaultValue = Short.parseShort(addCollectionFieldParams.getDefaultValue());
            } else if (addCollectionFieldParams.getDataType() == DataType.Int16) {
                defaultValue = Short.parseShort(addCollectionFieldParams.getDefaultValue());
            } else if (addCollectionFieldParams.getDataType() == DataType.Int32) {
                defaultValue = Integer.parseInt(addCollectionFieldParams.getDefaultValue());
            } else if (addCollectionFieldParams.getDataType() == DataType.Int64) {
                defaultValue = Long.parseLong(addCollectionFieldParams.getDefaultValue());
            } else if (addCollectionFieldParams.getDataType() == DataType.JSON) {
                defaultValue = JSONObject.parseObject(addCollectionFieldParams.getDefaultValue());
            } else if (addCollectionFieldParams.getDataType() == DataType.Array) {
                if (addCollectionFieldParams.getElementType() == DataType.JSON) {
                    defaultValue = JSONObject.parseArray(addCollectionFieldParams.getDefaultValue());
                }
                if (addCollectionFieldParams.getElementType() == DataType.VarChar ||
                        addCollectionFieldParams.getElementType() == DataType.Int8 ||
                        addCollectionFieldParams.getElementType() == DataType.Int16 ||
                        addCollectionFieldParams.getElementType() == DataType.Int32 ||
                        addCollectionFieldParams.getElementType() == DataType.Int64 ||
                        addCollectionFieldParams.getElementType() == DataType.Float
                ) {
                    defaultValue = Lists.newArrayList(addCollectionFieldParams.getDefaultValue());
                }
            } else if (addCollectionFieldParams.getDataType() == DataType.Bool) {
                defaultValue = Boolean.parseBoolean(addCollectionFieldParams.getDefaultValue());
            } else {
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
