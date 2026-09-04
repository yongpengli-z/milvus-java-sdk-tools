package custom.components;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import custom.entity.AddCollectionFieldParams;
import custom.entity.StructFieldParams;
import custom.entity.result.AddCollectionFieldResult;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddCollectionFieldReq;
import io.milvus.v2.service.collection.request.AddCollectionStructFieldReq;
import io.milvus.v2.service.collection.request.AddFieldReq;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Slf4j
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
            // Array of Struct：走 addCollectionStructField 专用接口（Milvus 3.0.0+ 支持）
            if (isStructArrayField(addCollectionFieldParams)) {
                addStructArrayField(addCollectionFieldParams, collectionName);
                commonResult.setResult(ResultEnum.SUCCESS.result);
                addCollectionFieldResult.setCommonResult(commonResult);
                return addCollectionFieldResult;
            }

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
                defaultValue = JsonParser.parseString(addCollectionFieldParams.getDefaultValue()).getAsJsonObject();
                log.info("defaultValue:" + defaultValue);
                log.info("defaultValue type:" + defaultValue.getClass().getName());
            } else if (addCollectionFieldParams.getDataType() == DataType.Array) {
                if (addCollectionFieldParams.getElementType() == DataType.JSON) {
                    defaultValue = JsonParser.parseString(addCollectionFieldParams.getDefaultValue()).getAsJsonArray();
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

    /**
     * 判断是否为 Array of Struct 字段（dataType=Array 且 elementType=Struct 且 structSchema 非空）。
     */
    private static boolean isStructArrayField(AddCollectionFieldParams params) {
        return params.getDataType() == DataType.Array
                && params.getElementType() == DataType.Struct
                && params.getStructSchema() != null
                && !params.getStructSchema().isEmpty();
    }

    /**
     * 动态添加 Array of Struct 字段。
     * <p>
     * 服务端限制（Milvus 3.0.0+）：字段必须 nullable=true、maxCapacity 必填；
     * 子字段不能是 Struct/Array/JSON，不能设置主键/默认值/nullable。
     */
    private static void addStructArrayField(AddCollectionFieldParams params, String collectionName) {
        if (params.getMaxCapacity() == null || params.getMaxCapacity() <= 0) {
            throw new IllegalArgumentException("maxCapacity is required for struct array field: " + params.getFieldName());
        }
        AddCollectionStructFieldReq.AddCollectionStructFieldReqBuilder builder = AddCollectionStructFieldReq.builder()
                .collectionName(collectionName)
                .fieldName(params.getFieldName())
                .maxCapacity(params.getMaxCapacity())
                // 动态添加的 StructArray 字段必须 nullable
                .nullable(params.getIsNullable() == null || params.getIsNullable());

        for (StructFieldParams structFieldParam : params.getStructSchema()) {
            AddFieldReq.AddFieldReqBuilder<?> structFieldBuilder = AddFieldReq.builder()
                    .fieldName(structFieldParam.getFieldName())
                    .dataType(structFieldParam.getDataType())
                    .isNullable(structFieldParam.isNullable());
            // 向量维度
            if (structFieldParam.getDataType() == DataType.FloatVector ||
                    structFieldParam.getDataType() == DataType.BFloat16Vector ||
                    structFieldParam.getDataType() == DataType.Float16Vector ||
                    structFieldParam.getDataType() == DataType.BinaryVector ||
                    structFieldParam.getDataType() == DataType.Int8Vector) {
                structFieldBuilder.dimension(structFieldParam.getDim());
            }
            // VarChar/String/Text 最大长度
            if ((structFieldParam.getDataType() == DataType.VarChar ||
                    structFieldParam.getDataType() == DataType.String ||
                    structFieldParam.getDataType() == DataType.Text) && structFieldParam.getMaxLength() > 0) {
                structFieldBuilder.maxLength(structFieldParam.getMaxLength());
            }
            builder.addStructField(structFieldBuilder.build());
        }

        AddCollectionStructFieldReq req = builder.build();
        if (params.getDatabaseName() != null && !params.getDatabaseName().equalsIgnoreCase("")) {
            req.setDatabaseName(params.getDatabaseName());
        }
        milvusClientV2.addCollectionStructField(req);
    }
}
