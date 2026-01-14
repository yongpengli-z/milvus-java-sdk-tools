package custom.common;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import custom.entity.*;
import custom.pojo.GeneralDataRole;
import custom.pojo.RandomRangeParams;
import custom.utils.DatasetUtil;
import custom.utils.GenerateUtil;
import custom.utils.JsonObjectUtil;
import custom.utils.MathUtil;
import io.milvus.common.utils.Float16Utils;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexBuildState;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.data.*;
import io.milvus.v2.service.vector.response.QueryResp;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static custom.BaseTest.*;

@Slf4j
public class CommonFunction {

    /**
     * 创建通用的collection方法，支持多个filed，多个向量
     *
     * @param collectionName  collection 可不传
     * @param enableDynamic   是否开启动态列
     * @param fieldParamsList 其他字段
     * @return collection name
     */
    public static String genCommonCollection(@Nullable String collectionName, boolean enableDynamic, int shardNum, int numPartitions, List<FieldParams> fieldParamsList, FunctionParams functionParams, List<CreateCollectionParams.PropertyM> properties, String databaseName) {
        if (collectionName == null || collectionName.equals("")) {
            collectionName = "Collection_" + GenerateUtil.getRandomString(10);
        }
        // 使用 parseDataType 直接生成 CollectionSchema（支持 Struct 类型）
        CreateCollectionReq.CollectionSchema collectionSchema = parseDataType(fieldParamsList, enableDynamic);
        // schema add function
        if (functionParams != null
                && functionParams.getFunctionType() != null
                && functionParams.getInputFieldNames().size() > 0
                && functionParams.getOutputFieldNames().size() > 0) {
            collectionSchema.addFunction(CreateCollectionReq.Function.builder()
                    .functionType(functionParams.getFunctionType())
                    .name(functionParams.getName())
                    .inputFieldNames(functionParams.getInputFieldNames())
                    .outputFieldNames(functionParams.getOutputFieldNames())
                    .build()
            );
        }
        Map<String, String> propertyList = new HashMap<>();
        if (properties != null && properties.size() > 0) {
            for (CreateCollectionParams.PropertyM property : properties) {
                if (!property.getPropertyKey().equalsIgnoreCase("")) {
                    propertyList.put(property.getPropertyKey(), property.getPropertyValue());
                }
            }
        }
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .collectionSchema(collectionSchema)
                .collectionName(collectionName)
                .enableDynamicField(enableDynamic)
                .description("created by qtp!")
                .numShards(shardNum)
                .numPartitions(numPartitions)
                .properties(propertyList)
                .build();
        if (databaseName != null && !databaseName.equalsIgnoreCase("")) {
            createCollectionReq.setDatabaseName(databaseName);
        }
        milvusClientV2.createCollection(createCollectionReq);
        return collectionName;
    }

    /**
     * 遍历fieldParamList生成对应的CollectionSchema
     *
     * @param fieldParamsList field字段集合
     * @param enableDynamic   是否启用动态字段
     * @return CreateCollectionReq.CollectionSchema 给创建collection提供
     */
    public static CreateCollectionReq.CollectionSchema parseDataType(List<FieldParams> fieldParamsList, boolean enableDynamic) {
        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(enableDynamic)
                .build();

        for (int i = 0; i < fieldParamsList.size(); i++) {
            FieldParams fieldParams = fieldParamsList.get(i);
            DataType dataType = fieldParams.getDataType();
            String fieldName = fieldParams.getFieldName() == null ? dataType + "_" + i : fieldParams.getFieldName();

            // 处理 Array of Struct：使用 AddFieldReq 和 addStructField 方法
            if (dataType == DataType.Array && fieldParams.getElementType() != null &&
                    fieldParams.getElementType().name().equals("Struct") &&
                    fieldParams.getStructSchema() != null && !fieldParams.getStructSchema().isEmpty()) {
                // 使用 AddFieldReq 创建 Array of Struct 字段
                AddFieldReq.AddFieldReqBuilder<?> addFieldBuilder = AddFieldReq.builder()
                        .fieldName(fieldName)
                        .dataType(DataType.Array)
                        .elementType(DataType.Struct)
                        .maxCapacity(fieldParams.getMaxCapacity())
                        .isNullable(fieldParams.isNullable())
                        .isPrimaryKey(fieldParams.isPrimaryKey())
                        .isPartitionKey(fieldParams.isPartitionKey())
                        .enableMatch(fieldParams.isEnableMatch())
                        .enableAnalyzer(fieldParams.isEnableAnalyzer());

                // 添加 Struct 子字段
                for (StructFieldParams structFieldParam : fieldParams.getStructSchema()) {
                    AddFieldReq.AddFieldReqBuilder<?> structFieldBuilder = AddFieldReq.builder()
                            .fieldName(structFieldParam.getFieldName())
                            .dataType(structFieldParam.getDataType())
                            .isNullable(structFieldParam.isNullable());

                    // 设置向量维度
                    if (structFieldParam.getDataType() == DataType.FloatVector ||
                            structFieldParam.getDataType() == DataType.BFloat16Vector ||
                            structFieldParam.getDataType() == DataType.Float16Vector ||
                            structFieldParam.getDataType() == DataType.BinaryVector ||
                            structFieldParam.getDataType() == DataType.Int8Vector) {
                        structFieldBuilder.dimension(structFieldParam.getDim());
                    }
                    // 设置 VarChar/String 最大长度
                    if (structFieldParam.getDataType() == DataType.String || structFieldParam.getDataType() == DataType.VarChar) {
                        structFieldBuilder.maxLength(structFieldParam.getMaxLength());
                    }

                    addFieldBuilder.addStructField(structFieldBuilder.build());
                }

                // 设置主键 AutoID
                if (fieldParams.isPrimaryKey()) {
                    addFieldBuilder.autoID(fieldParams.isAutoId());
                }

                // 设置 Analyzer 参数
                if (fieldParams.isEnableAnalyzer() && fieldParams.getAnalyzerParamsList() != null) {
                    Map<String, Object> analyzerParams = new HashMap<>();
                    for (FieldParams.AnalyzerParams analyzerParam : fieldParams.getAnalyzerParamsList()) {
                        analyzerParams.put(analyzerParam.getParamsKey(), analyzerParam.getParamsValue());
                    }
                    addFieldBuilder.analyzerParams(analyzerParams);
                }

                // 添加到 collection schema
                collectionSchema.addField(addFieldBuilder.build());
            } else {
                // 普通字段：使用 AddFieldReq（与 Struct 字段保持一致）
                AddFieldReq.AddFieldReqBuilder<?> addFieldBuilder = AddFieldReq.builder()
                        .fieldName(fieldName)
                        .dataType(dataType)
                        .enableMatch(fieldParams.isEnableMatch())
                        .enableAnalyzer(fieldParams.isEnableAnalyzer())
                        .isPrimaryKey(fieldParams.isPrimaryKey())
                        .isPartitionKey(fieldParams.isPartitionKey())
                        .isNullable(fieldParams.isNullable());

                // 判断主键是否autoid
                if (fieldParams.isPrimaryKey()) {
                    addFieldBuilder.autoID(fieldParams.isAutoId());
                }
                if (dataType == DataType.FloatVector || dataType == DataType.BFloat16Vector || dataType == DataType.Float16Vector || dataType == DataType.BinaryVector || dataType == DataType.Int8Vector) {
                    addFieldBuilder.dimension(fieldParams.getDim());
                }
                if (dataType == DataType.String || dataType == DataType.VarChar) {
                    addFieldBuilder.maxLength(fieldParams.getMaxLength());
                }
                if (dataType == DataType.Array) {
                    addFieldBuilder.maxCapacity(fieldParams.getMaxCapacity());
                    addFieldBuilder.elementType(fieldParams.getElementType());
                    if (fieldParams.getElementType() == DataType.VarChar) {
                        addFieldBuilder.maxLength(fieldParams.getMaxLength());
                    }
                }
                // isEnableAnalyzer 参数解析
                if (fieldParams.isEnableAnalyzer() && fieldParams.getAnalyzerParamsList() != null) {
                    Map<String, Object> analyzerParams = new HashMap<>();
                    for (FieldParams.AnalyzerParams analyzerParam : fieldParams.getAnalyzerParamsList()) {
                        analyzerParams.put(analyzerParam.getParamsKey(), analyzerParam.getParamsValue());
                    }
                    addFieldBuilder.analyzerParams(analyzerParams);
                }

                // 添加到 collection schema
                collectionSchema.addField(addFieldBuilder.build());
            }
        }

        return collectionSchema;
    }


    /**
     * 创建通用索引
     *
     * @param collectionName collection name
     * @param indexParams    index field集合
     */
    public static void createCommonIndex(String collectionName, List<IndexParams> indexParams, String databaseName) {
        log.info("indexParams.size():" + indexParams.size());
        List<IndexParam> indexParamList = new ArrayList<>();
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName((collectionName == null || collectionName.equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : collectionName).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        if (indexParams.size() == 0) {
            for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
                String name = fieldSchema.getName();
                DataType dataType = fieldSchema.getDataType();
                // 给向量自动建索引
                if (dataType == DataType.FloatVector || dataType == DataType.BFloat16Vector || dataType == DataType.Float16Vector || dataType == DataType.BinaryVector || dataType == DataType.Int8Vector || dataType == DataType.SparseFloatVector) {
                    IndexParam indexParam = IndexParam.builder()
                            .fieldName(name)
                            .indexName("idx_" + name)
                            .indexType(IndexParam.IndexType.AUTOINDEX)
                            .extraParams(CommonFunction.provideExtraParam(providerIndexType(dataType)))
                            .metricType(provideMetricTypeByVectorType(dataType))
                            .build();
                    indexParamList.add(indexParam);
                }
            }
        } else {
            for (int i = 0; i < indexParams.size(); i++) {
                IndexParams indexParamItem = indexParams.get(i);
                Map<String, Object> params = new HashMap<>();
                // 判断是否是json index
                if (indexParamItem.getJsonPath() != null && !indexParamItem.getJsonPath().equals("")) {
                    params.put("json_cast_type", indexParamItem.getJsonCastType());
                    params.put("json_path", indexParamItem.getJsonPath());
                } else {
                    params = CommonFunction.provideExtraParam(indexParamItem.getIndextype());
                }
                // 增加build——level
                CreateCollectionReq.FieldSchema fieldSchema = fieldSchemaList.stream().filter(x -> x.getName().equalsIgnoreCase(indexParamItem.getFieldName())).findFirst().orElse(null);
                if (fieldSchema != null && (fieldSchema.getDataType() == DataType.BFloat16Vector || fieldSchema.getDataType() == DataType.Float16Vector || fieldSchema.getDataType() == DataType.FloatVector)) {
                    if (indexParamItem.getBuildLevel() != null && !indexParamItem.getBuildLevel().equalsIgnoreCase("")) {
                        params.put("build_level", indexParamItem.getBuildLevel());
                    }
                }
                IndexParam indexParam = IndexParam.builder()
                        .fieldName(indexParamItem.getFieldName())
                        .indexName("idx_" + indexParamItem.getFieldName() + "_" + i)
                        .indexType(indexParamItem.getIndextype())
                        .extraParams(params)
                        .build();
                if (indexParamItem.getMetricType() != null) {
                    indexParam.setMetricType(indexParamItem.getMetricType());
                }
                log.info("indexParam (i={}): {}", i, indexParam.toString());
                indexParamList.add(indexParam);
            }
        }
        log.info("create index :" + indexParamList);
        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName((collectionName == null || collectionName.equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : collectionName)
                .indexParams(indexParamList)
                .timeout(1000 * 60 * 100L)
                .build();
        if (!databaseName.equalsIgnoreCase("")) {
            createIndexReq.setDatabaseName(databaseName);
        }
        milvusClientV2.createIndex(createIndexReq);
        // 查询索引是否建完
        List<Boolean> indexStateList = new ArrayList<>(Collections.nCopies(indexParamList.size(), false));
        long startTimeTotal = System.currentTimeMillis();
        while (!(indexStateList.size() == indexStateList.stream().filter(x -> x).count())) {
            for (int i = 0; i < indexParamList.size(); i++) {
                DescribeIndexResp describeIndexResp = milvusClientV2.describeIndex(DescribeIndexReq.builder()
                        .fieldName(indexParamList.get(i).getFieldName())
                        .collectionName((collectionName == null || collectionName.equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : collectionName)
                        .build());
                log.info(indexParamList.get(i).getFieldName() + "--" + describeIndexResp.getIndexDescriptions());
                if (describeIndexResp.getIndexDescByFieldName(indexParamList.get(i).getFieldName()).getIndexState() == IndexBuildState.Finished) {
                    indexStateList.set(i, true);
                }
            }
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                log.error("get index state:" + e.getMessage());
            }
        }
        long endTimeTotal = System.currentTimeMillis();
        float indexCost = (float) ((endTimeTotal - startTimeTotal) / 1000.00);
        log.info("create index " + indexParamList + " ,total cost: " + indexCost + " seconds");
    }

    /**
     * 创建索引时，提供额外的参数
     *
     * @param indexType 索引类型
     * @return Map类型参数
     */
    public static Map<String, Object> provideExtraParam(IndexParam.IndexType indexType) {
        Map<String, Object> map = new HashMap<>();
        switch (indexType) {
            case FLAT:
            case AUTOINDEX:
                break;
            case HNSW:
                map.put("M", 16);
                map.put("efConstruction", 64);
                break;
            default:
//                map.put("nlist", 128);
                break;
        }
        return map;
    }

    /**
     * 更具向量类型提供MetricType
     *
     * @param vectorType 向量类型
     * @return MetricType
     */
    public static IndexParam.MetricType provideMetricTypeByVectorType(DataType vectorType) {
        switch (vectorType.getCode()) {
            case 101:
            case 102:
            case 103:
            case 105:
                return IndexParam.MetricType.L2;
            case 100:
                return IndexParam.MetricType.HAMMING;
            case 104:
                return IndexParam.MetricType.IP;
            default:
                return IndexParam.MetricType.INVALID;
        }
    }

    /**
     * 根据向量类型决定IndexType
     *
     * @param vectorType DataType
     * @return IndexParam.IndexType
     */
    public static IndexParam.IndexType providerIndexType(DataType vectorType) {
        switch (vectorType.getCode()) {
            case 101:
                return IndexParam.IndexType.HNSW;
            case 102:
                return IndexParam.IndexType.HNSW;
            case 103:
                return IndexParam.IndexType.HNSW;
            case 100:
                return IndexParam.IndexType.BIN_IVF_FLAT;
            case 104:
                return IndexParam.IndexType.SPARSE_WAND;
            case 105:
                return IndexParam.IndexType.HNSW;
            default:
                return IndexParam.IndexType.TRIE;
        }
    }

    /**
     * 生成通用的数据
     *
     * @param count 生成的数量
     * @return List<JsonObject>
     */
    public static List<JsonObject> genCommonData(long count, long startId, String dataset, List<String> fileNames, List<Long> fileSizeList, List<GeneralDataRole> generalDataRoleList, long totalNum, long realStartId, DescribeCollectionResp describeCollectionResp) {
        Random random = new Random();
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        // 获取function列表，查找不需要构建数据的 outputFieldNames
        List<CreateCollectionReq.Function> functionList = collectionSchema.getFunctionList();
        List<String> tempOutputFieldNames = new ArrayList<>();
        for (CreateCollectionReq.Function function : functionList) {
            List<String> outputFieldNames1 = function.getOutputFieldNames();
            tempOutputFieldNames.addAll(outputFieldNames1);
        }
        // 分别获取普通字段和 Struct 字段
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        List<CreateCollectionReq.StructFieldSchema> structFieldSchemaList = collectionSchema.getStructFields();

        List<JsonObject> jsonList = new ArrayList<>();
        //提供给floatVector用
        List<List<Float>> floatVectorList = new ArrayList<>();
        // 先获取Dataset数据集
        if (dataset.equalsIgnoreCase("gist")) {
            floatVectorList = DatasetUtil.providerFloatVectorByDataset(startId, count, fileNames, DatasetEnum.GIST, fileSizeList);
        }
        if (dataset.equalsIgnoreCase("deep")) {
            floatVectorList = DatasetUtil.providerFloatVectorByDataset(startId, count, fileNames, DatasetEnum.DEEP, fileSizeList);
        }
        if (dataset.equalsIgnoreCase("laion")) {
            floatVectorList = DatasetUtil.providerFloatVectorByDataset(startId, count, fileNames, DatasetEnum.LAION, fileSizeList);
        }
        if (dataset.equalsIgnoreCase("sift")) {
            floatVectorList = DatasetUtil.providerFloatVectorByDataset(startId, count, fileNames, DatasetEnum.SIFT, fileSizeList);
        }
        for (long i = startId; i < (startId + count); i++) {
            JsonObject row = new JsonObject();
            // 处理普通字段（Array of Struct 不会出现在这里）
            for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
                String name = fieldSchema.getName();
                DataType dataType = fieldSchema.getDataType();
                DataType elementType = fieldSchema.getElementType();
                Integer dimension = fieldSchema.getDimension();
                Integer maxCapacity = fieldSchema.getMaxCapacity();
                Integer maxLength = fieldSchema.getMaxLength();
                boolean isNullable = fieldSchema.getIsNullable();
                boolean isEnableMatch = fieldSchema.getEnableMatch() != null && fieldSchema.getEnableMatch();
                // primary key auto id
                if (fieldSchema.getIsPrimaryKey() && fieldSchema.getAutoID()) {
                    continue;
                }
                // 如果使用function自动生成数据，则继续
                if (tempOutputFieldNames.contains(name)) {
                    continue;
                }
                JsonObject jsonObject = new JsonObject();
                Gson gson = new Gson();
                if (dataType == DataType.FloatVector || dataType == DataType.BFloat16Vector || dataType == DataType.Float16Vector || dataType == DataType.BinaryVector || dataType == DataType.Int8Vector) {
                    if (dataset.equalsIgnoreCase("random")) {
                        jsonObject = generalJsonObjectByDataType(name, dataType, dimension, i, null, 0, generalDataRoleList, totalNum, realStartId, isEnableMatch);
                    }
                    if (dataset.equalsIgnoreCase("gist")) {
                        jsonObject.add(name, gson.toJsonTree(floatVectorList.get((int) (i - startId))));
                    }
                    if (dataset.equalsIgnoreCase("deep")) {
                        jsonObject.add(name, gson.toJsonTree(floatVectorList.get((int) (i - startId))));
                    }
                    if (dataset.equalsIgnoreCase("sift")) {
                        jsonObject.add(name, gson.toJsonTree(floatVectorList.get((int) (i - startId))));
                    }
                    if (dataset.equalsIgnoreCase("laion")) {
                        jsonObject.add(name, gson.toJsonTree(floatVectorList.get((int) (i - startId))));
                    }
                } else if (dataType == DataType.SparseFloatVector) {
                    jsonObject = generalJsonObjectByDataType(name, dataType, random.nextInt(768) + 1, i, null, 0, generalDataRoleList, totalNum, realStartId, isEnableMatch);
                } else if (dataType == DataType.VarChar || dataType == DataType.String) {
                    JsonObject jsonObjectItem = new JsonObject();
                    jsonObjectItem.add(name, null);
                    jsonObject = (isNullable && i % 2 == 0) ? jsonObjectItem : generalJsonObjectByDataType(name, dataType, random.nextInt(maxLength - 1) + 1, i, null, 0, generalDataRoleList, totalNum, realStartId, isEnableMatch);
                } else if (dataType == DataType.Array) {
                    // 普通 Array 类型（不包括 Array of Struct）
                    JsonObject jsonObjectItem = new JsonObject();
                    jsonObjectItem.add(name, null);
                    jsonObject = (isNullable && i % 2 == 0) ? jsonObjectItem : generalJsonObjectByDataType(name, dataType, random.nextInt(maxCapacity - 1) + 1, i, elementType, random.nextInt(maxLength - 1) + 1, generalDataRoleList, totalNum, realStartId, isEnableMatch);
                } else {
                    JsonObject jsonObjectItem = new JsonObject();
                    jsonObjectItem.add(name, null);
                    jsonObject = (isNullable && i % 2 == 0) ? jsonObjectItem : generalJsonObjectByDataType(name, dataType, 0, i, null, 0, generalDataRoleList, totalNum, realStartId, isEnableMatch);
                }
                row = JsonObjectUtil.jsonMerge(row, jsonObject);
            }

            // 单独处理 Array of Struct 字段（从 getStructFields() 获取）
            for (CreateCollectionReq.StructFieldSchema structFieldSchema : structFieldSchemaList) {
                String structFieldName = structFieldSchema.getName();
                Integer structFieldMaxCapacity = structFieldSchema.getMaxCapacity();
                // StructFieldSchema 可能没有 getIsNullable() 方法，使用默认值 false
                boolean structFieldIsNullable = false;

                // 跳过 function 自动生成的字段
                if (tempOutputFieldNames.contains(structFieldName)) {
                    continue;
                }

                // 获取 Struct 子字段列表
                List<CreateCollectionReq.FieldSchema> structSubFields = structFieldSchema.getFields();
                if (structSubFields == null || structSubFields.isEmpty()) {
                    log.warn("字段 {} 的 Struct 子字段为空，跳过数据生成", structFieldName);
                    continue;
                }

                // 生成 Array of Struct 数据
                JsonObject jsonObject = new JsonObject();
                Gson gson = new Gson();
                JsonObject jsonObjectItem = new JsonObject();
                jsonObjectItem.add(structFieldName, null);

                if (structFieldIsNullable && i % 2 == 0) {
                    jsonObject = jsonObjectItem;
                } else {
                    int arrayLength = random.nextInt(Math.max(1, structFieldMaxCapacity - 1)) + 1;
                    List<com.google.gson.JsonObject> structArray = new ArrayList<>();
                    for (int structIdx = 0; structIdx < arrayLength; structIdx++) {
                        com.google.gson.JsonObject structObj = new com.google.gson.JsonObject();
                        for (CreateCollectionReq.FieldSchema subFieldSchema : structSubFields) {
                            String subFieldName = subFieldSchema.getName();
                            DataType subFieldType = subFieldSchema.getDataType();
                            Integer subFieldDim = subFieldSchema.getDimension();
                            Integer subFieldMaxLength = subFieldSchema.getMaxLength();
                            boolean subFieldIsEnableMatch = subFieldSchema.getEnableMatch() != null && subFieldSchema.getEnableMatch();

                            // 生成 struct 子字段的数据
                            if (subFieldType == DataType.FloatVector) {
                                List<Float> vector = generateFloatVector(subFieldDim);
                                structObj.add(subFieldName, gson.toJsonTree(vector));
                            } else if (subFieldType == DataType.BFloat16Vector) {
                                ByteBuffer bf16Buffer = generateBF16Vector(subFieldDim);
                                structObj.add(subFieldName, gson.toJsonTree(bf16Buffer.array()));
                            } else if (subFieldType == DataType.Float16Vector) {
                                ByteBuffer f16Buffer = generateFloat16Vector(subFieldDim);
                                structObj.add(subFieldName, gson.toJsonTree(f16Buffer.array()));
                            } else if (subFieldType == DataType.BinaryVector) {
                                ByteBuffer binaryBuffer = generateBinaryVector(subFieldDim);
                                structObj.add(subFieldName, gson.toJsonTree(binaryBuffer.array()));
                            } else if (subFieldType == DataType.Int8Vector) {
                                ByteBuffer int8Buffer = generateInt8Vector(subFieldDim);
                                structObj.add(subFieldName, gson.toJsonTree(int8Buffer.array()));
                            } else if (subFieldType == DataType.VarChar || subFieldType == DataType.String) {
                                String strValue = subFieldIsEnableMatch ?
                                        GenerateUtil.generateRandomLengthSentence(subFieldMaxLength) :
                                        MathUtil.genRandomString(subFieldMaxLength);
                                structObj.add(subFieldName, gson.toJsonTree(strValue));
                            } else if (subFieldType == DataType.Int64) {
                                structObj.add(subFieldName, gson.toJsonTree(i * 1000L + structIdx));
                            } else if (subFieldType == DataType.Int32) {
                                structObj.add(subFieldName, gson.toJsonTree((int) (i * 1000 + structIdx) % 32767));
                            } else if (subFieldType == DataType.Float) {
                                structObj.add(subFieldName, gson.toJsonTree((float) (i * 0.1 + structIdx * 0.01)));
                            } else if (subFieldType == DataType.Double) {
                                structObj.add(subFieldName, gson.toJsonTree((double) (i * 0.1 + structIdx * 0.01)));
                            } else if (subFieldType == DataType.Bool) {
                                structObj.add(subFieldName, gson.toJsonTree(structIdx % 2 == 0));
                            } else {
                                structObj.add(subFieldName, gson.toJsonTree(structIdx));
                            }
                        }
                        structArray.add(structObj);
                    }
                    jsonObject.add(structFieldName, gson.toJsonTree(structArray));
                }
                row = JsonObjectUtil.jsonMerge(row, jsonObject);
            }

            // 判断是否有动态列
            if (describeCollectionResp.getCollectionSchema().isEnableDynamicField()) {
                JsonObject jsonObject = generalJsonObjectByDataType(CommonData.dynamicField, DataType.JSON, 0, i, null, 0, generalDataRoleList, totalNum, realStartId, false);
                row = JsonObjectUtil.jsonMerge(row, jsonObject);
            }
            jsonList.add(row);
        }
        return jsonList;
    }

    /**
     * 更具数据类型，创建JsonObject
     *
     * @param fieldName   字段名称
     * @param dataType    类型
     * @param dimOrLength 向量维度或者array容量或者varchar长度
     * @param countIndex  索引i，避免多次创建时数据内容一样
     * @return JsonObject
     */
    public static JsonObject generalJsonObjectByDataType(String fieldName, DataType dataType, int dimOrLength, long countIndex, DataType elementType, int lengthForCapacity, List<GeneralDataRole> generalDataRoleList, long totalNum, long realStartId, boolean isEnableMatch) {
        JsonObject row = new JsonObject();
        Gson gson = new Gson();
        Random random = new Random();
        // 判断是否有设定生成数据的规则
        GeneralDataRole generalDataRole = null;
        if (generalDataRoleList != null) {
            generalDataRole = generalDataRoleList.stream().filter(x -> x.getFieldName().equalsIgnoreCase(fieldName)).findFirst().orElse(null);
        }
        if (dataType == DataType.Int64) {
            if (generalDataRole != null) {
                if (generalDataRole.getSequenceOrRandom().equalsIgnoreCase("sequence")) {
                    row.add(fieldName, gson.toJsonTree(advanceSequence(generalDataRole.getRandomRangeParamsList(), totalNum, countIndex, realStartId)));
                } else {
                    row.add(fieldName, gson.toJsonTree(advanceRandom(generalDataRole.getRandomRangeParamsList())));
                }
            } else {
                row.add(fieldName, gson.toJsonTree(countIndex));
            }
        }
        if (dataType == DataType.Int32) {
            row.add(fieldName, gson.toJsonTree((int) countIndex % 32767));
        }
        if (dataType == DataType.Int16) {
            row.add(fieldName, gson.toJsonTree((int) countIndex % 32767));
        }
        if (dataType == DataType.Int8) {
            row.add(fieldName, gson.toJsonTree((short) countIndex % 127));
        }
        if (dataType == DataType.Double) {
            row.add(fieldName, gson.toJsonTree((double) countIndex * 0.1f));
        }
        if (dataType == DataType.Array) {
            List<Object> list = MathUtil.providerArrayData(elementType, dimOrLength, lengthForCapacity);
            row.add(fieldName, gson.toJsonTree(list));
        }
        if (dataType == DataType.Bool) {
            row.add(fieldName, gson.toJsonTree(true));
        }
        if (dataType == DataType.VarChar) {
            if (generalDataRole != null) {
                if (generalDataRole.getSequenceOrRandom().equalsIgnoreCase("sequence")) {
                    row.add(fieldName, gson.toJsonTree(generalDataRole.getPrefix() + advanceSequence(generalDataRole.getRandomRangeParamsList(), totalNum, countIndex, realStartId)));
                } else {
                    row.add(fieldName, gson.toJsonTree(generalDataRole.getPrefix() + advanceRandom(generalDataRole.getRandomRangeParamsList())));
                }
            } else {
                row.add(fieldName, gson.toJsonTree(isEnableMatch ? GenerateUtil.generateRandomLengthSentence(dimOrLength) : MathUtil.genRandomString(dimOrLength)));
            }
        }
        if (dataType == DataType.String) {
            if (generalDataRole != null) {
                if (generalDataRole.getSequenceOrRandom().equalsIgnoreCase("sequence")) {
                    row.add(fieldName, gson.toJsonTree(generalDataRole.getPrefix() + advanceSequence(generalDataRole.getRandomRangeParamsList(), totalNum, countIndex, realStartId)));
                } else {
                    row.add(fieldName, gson.toJsonTree(generalDataRole.getPrefix() + advanceRandom(generalDataRole.getRandomRangeParamsList())));
                }
            } else {
                row.add(fieldName, gson.toJsonTree(isEnableMatch ? GenerateUtil.generateRandomLengthSentence(dimOrLength) : MathUtil.genRandomString(dimOrLength)));

            }
        }
        if (dataType == DataType.Float) {
            row.add(fieldName, gson.toJsonTree((float) countIndex * 0.1f));
        }
        if (dataType == DataType.FloatVector) {
            List<Float> vector = new ArrayList<>();
            for (int k = 0; k < dimOrLength; ++k) {
                vector.add(random.nextFloat());
            }
            row.add(fieldName, gson.toJsonTree(vector));
        }
        if (dataType == DataType.BinaryVector) {
            row.add(fieldName, gson.toJsonTree(generateBinaryVector(dimOrLength).array()));
        }
        if (dataType == DataType.Int8Vector) {
            row.add(fieldName, gson.toJsonTree(generateInt8Vector(dimOrLength).array()));
        }
        if (dataType == DataType.Float16Vector) {
            row.add(fieldName, gson.toJsonTree(generateFloat16Vector(dimOrLength).array()));
        }
        if (dataType == DataType.BFloat16Vector) {
            row.add(fieldName, gson.toJsonTree(generateBF16Vector(dimOrLength).array()));
        }
        if (dataType == DataType.SparseFloatVector) {
            row.add(fieldName, gson.toJsonTree(generateSparseVector(dimOrLength)));
        }
        if (dataType == DataType.JSON) {
            JsonObject json = new JsonObject();
            json.add(CommonData.fieldInt64, gson.toJsonTree((int) countIndex % 32767));
            json.add(CommonData.fieldInt32, gson.toJsonTree((int) countIndex % 32767));
            json.add(CommonData.fieldDouble, gson.toJsonTree((double) countIndex));
            json.add(CommonData.fieldArray, gson.toJsonTree(Arrays.asList(countIndex, countIndex + 1, countIndex + 2)));
            json.add(CommonData.fieldBool, gson.toJsonTree(countIndex % 2 == 0));
            json.add(CommonData.fieldVarchar, gson.toJsonTree("Str" + countIndex));
            json.add(CommonData.fieldFloat, gson.toJsonTree((float) countIndex));

            JsonObject json2 = new JsonObject();
            json2.add(CommonData.fieldInt64, gson.toJsonTree((int) countIndex % 32767));
            json2.add(CommonData.fieldInt32, gson.toJsonTree((int) countIndex % 32767));
            json2.add(CommonData.fieldDouble, gson.toJsonTree((double) countIndex));
            json2.add(CommonData.fieldArray, gson.toJsonTree(Arrays.asList(countIndex, countIndex + 1, countIndex + 2)));
            json2.add(CommonData.fieldBool, gson.toJsonTree(countIndex % 2 == 0));
            json2.add(CommonData.fieldVarchar, gson.toJsonTree("Str" + countIndex));
            json2.add(CommonData.fieldFloat, gson.toJsonTree((float) countIndex));

            json.add(CommonData.fieldJson, json2);
            row.add(fieldName, json);
        }
        if (dataType == DataType.Geometry) {
            String geoData = "";
            if (countIndex % 3 == 0) {
                geoData = generateRandomGeoPoint();
            }
            if (countIndex % 3 == 1) {
                geoData = generateRandomGeoLineString(random.nextInt(5) + 2);
            }
            if (countIndex % 3 == 2) {
                geoData = generateRandomGeoPolygon(random.nextInt(5) + 3);
            }
            row.add(fieldName, gson.toJsonTree(geoData));
        }
        if (dataType == DataType.Timestamptz) {
            row.add(fieldName, gson.toJsonTree(generateTimeStamp()));
        }
        return row;
    }

    /**
     * 生成一条binary向量
     *
     * @param dim 维度
     * @return ByteBuffer
     */
    public static ByteBuffer generateBinaryVector(int dim) {
        Random ran = new Random();
        int byteCount = dim / 8;
        ByteBuffer vector = ByteBuffer.allocate(byteCount);
        for (int i = 0; i < byteCount; ++i) {
            vector.put((byte) ran.nextInt(Byte.MAX_VALUE));
        }
        return vector;
    }

    /**
     * 创建指定数量的float16的向量
     *
     * @param dim   维度
     * @param count 指定条数
     * @return List<ByteBuffer>
     */
    public static List<ByteBuffer> generateFloat16Vectors(int dim, long count) {
        List<ByteBuffer> vectors = new ArrayList<>();
        for (int n = 0; n < count; ++n) {
            vectors.add(generateFloat16Vector(dim));
        }
        return vectors;
    }

    /**
     * 创建一条float16的向量
     *
     * @param dim 维度
     * @return ByteBuffer
     */
    public static ByteBuffer generateFloat16Vector(int dim) {
        List<Float> originalVector = generateFloatVector(dim);
        return Float16Utils.f32VectorToFp16Buffer(originalVector);
    }

    /**
     * 创建一条float32的向量
     *
     * @param dimension 维度
     * @return List<Float>
     */
    public static List<Float> generateFloatVector(int dimension) {
        Random ran = new Random();
        List<Float> vector = new ArrayList<>();
        for (int i = 0; i < dimension; ++i) {
            vector.add(ran.nextFloat());
        }
        return vector;
    }

    /**
     * 创建一条BF16的向量
     *
     * @param dim
     * @return ByteBuffer
     */
    public static ByteBuffer generateBF16Vector(int dim) {
        List<Float> originalVector = generateFloatVector(dim);
        return Float16Utils.f32VectorToBf16Buffer(originalVector);
    }

    /**
     * 创建指定数量的BF16的向量
     *
     * @param dim
     * @param count
     * @return List<ByteBuffer>
     */
    public static List<ByteBuffer> generateBF16Vectors(int dim, long count) {
        List<ByteBuffer> vectors = new ArrayList<>();
        for (int n = 0; n < count; ++n) {
            vectors.add(generateBF16Vector(dim));
        }
        return vectors;
    }

    /**
     * 创建一条Sparse向量数据
     *
     * @param dim 维度，sparse不需要指定维度，所以方法里随机
     * @return SortedMap<Long, Float>
     */
    public static SortedMap<Long, Float> generateSparseVector(int dim) {
        Random ran = new Random();
        SortedMap<Long, Float> sparse = new TreeMap<>();
        int dimNum = ran.nextInt(dim) + 1;
        for (int i = 0; i < dimNum; ++i) {
            sparse.put((long) ran.nextInt(1000000), ran.nextFloat());
        }
        return sparse;
    }

    /**
     * 创建多条Sparse向量数据
     *
     * @param dim 维度，sparse不需要指定维度，所以方法里随机
     * @return List<SortedMap < Long, Float>>
     */
    public static List<SortedMap<Long, Float>> generateSparseVectors(int dim, long count) {
        List<SortedMap<Long, Float>> list = new ArrayList<>();
        for (int n = 0; n < count; ++n) {
            list.add(generateSparseVector(dim));
        }
        return list;
    }

    /**
     * 提供search时候的向量参数
     *
     * @param nq         向量个数
     * @param dim        维度
     * @param vectorType 向量类型
     * @return List<BaseVector>
     */
    public static List<BaseVector> providerSearchVector(int nq, int dim, DataType vectorType) {
        List<BaseVector> data = new ArrayList<>();

        if (vectorType.equals(DataType.FloatVector)) {
            List<List<Float>> lists = GenerateUtil.generateFloatVector(nq, 3, dim);
            lists.forEach((v) -> data.add(new FloatVec(v)));
        }
        if (vectorType.equals(DataType.BinaryVector)) {
            List<ByteBuffer> byteBuffers = generateBinaryVectors(dim, nq);
            byteBuffers.forEach(x -> data.add(new BinaryVec(x)));
        }
        if (vectorType.equals(DataType.Int8Vector)) {
            List<ByteBuffer> byteBuffers = generateInt8Vectors(dim, nq);
            byteBuffers.forEach(x -> data.add(new Int8Vec(x)));
        }
        if (vectorType.equals(DataType.Float16Vector)) {
            List<ByteBuffer> byteBuffers = generateFloat16Vectors(dim, nq);
            byteBuffers.forEach(x -> data.add(new Float16Vec(x)));
        }
        if (vectorType.equals(DataType.BFloat16Vector)) {
            List<ByteBuffer> byteBuffers = generateBF16Vectors(dim, nq);
            byteBuffers.forEach(x -> data.add(new BFloat16Vec(x)));
        }
        if (vectorType.equals(DataType.SparseFloatVector)) {
            List<SortedMap<Long, Float>> list = generateSparseVectors(dim, nq);
            list.forEach(x -> data.add(new SparseFloatVec(x)));
        }
        return data;

    }

    /**
     * 生成指定数量的binary向量数据
     *
     * @param count binary向量的数据条数
     * @param dim   维度
     * @return List<ByteBuffer>
     */
    public static List<ByteBuffer> generateBinaryVectors(int dim, long count) {
        List<ByteBuffer> vectors = new ArrayList<>();
        for (int n = 0; n < count; ++n) {
            vectors.add(generateBinaryVector(dim));
        }
        return vectors;
    }

    /**
     * 创建指定条数Int8Vector
     *
     * @param dim   Int8Vector 维度
     * @param count Int8Vector向量的数据条数
     * @return List<ByteBuffer>
     */
    private static List<ByteBuffer> generateInt8Vectors(int dim, long count) {
        Random RANDOM = new Random();
        List<ByteBuffer> vectors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ByteBuffer vector = ByteBuffer.allocate(dim);
            for (int k = 0; k < dim; ++k) {
                vector.put((byte) (RANDOM.nextInt(256) - 128));
            }
            vectors.add(vector);
        }
        return vectors;
    }

    /**
     * 创建一条Int8Vector
     *
     * @param dim 维度
     * @return ByteBuffer
     */
    private static ByteBuffer generateInt8Vector(int dim) {
        Random RANDOM = new Random();
        ByteBuffer vector = ByteBuffer.allocate(dim);
        for (int k = 0; k < dim; ++k) {
            vector.put((byte) (RANDOM.nextInt(256) - 128));
        }
        return vector;
    }

    /**
     * 获取collection的向量信息
     *
     * @param collectionName collection
     * @param annsField 向量字段名，可以是正常向量字段或 struct 中的向量字段（格式：structFieldName[subFieldName]）
     * @return VectorInfo
     */
    public static VectorInfo getCollectionVectorInfo(String collectionName, String annsField) {
        VectorInfo vectorInfo = new VectorInfo();
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        
        // 检查是否是 struct 中的向量字段（格式：structFieldName[subFieldName]）
        if (annsField != null && annsField.contains("[") && annsField.contains("]")) {
            // 解析 struct 字段名和子字段名
            int bracketStart = annsField.indexOf('[');
            int bracketEnd = annsField.indexOf(']');
            if (bracketStart > 0 && bracketEnd > bracketStart) {
                String structFieldName = annsField.substring(0, bracketStart);
                String structSubFieldName = annsField.substring(bracketStart + 1, bracketEnd);
                
                // 从 structFieldSchemaList 中查找
                List<CreateCollectionReq.StructFieldSchema> structFieldSchemaList = collectionSchema.getStructFields();
                for (CreateCollectionReq.StructFieldSchema structFieldSchema : structFieldSchemaList) {
                    if (structFieldSchema.getName().equalsIgnoreCase(structFieldName)) {
                        // 查找子字段
                        List<CreateCollectionReq.FieldSchema> subFields = structFieldSchema.getFields();
                        for (CreateCollectionReq.FieldSchema subField : subFields) {
                            if (subField.getName().equalsIgnoreCase(structSubFieldName)) {
                                DataType subFieldDataType = subField.getDataType();
                                int dimension = subField.getDimension() == null ? 0 : subField.getDimension();
                                // 检查是否是向量类型
                                if (subFieldDataType == DataType.FloatVector || 
                                    subFieldDataType == DataType.BinaryVector ||
                                    subFieldDataType == DataType.Float16Vector ||
                                    subFieldDataType == DataType.BFloat16Vector ||
                                    subFieldDataType == DataType.SparseFloatVector ||
                                    subFieldDataType == DataType.Int8Vector) {
                                    vectorInfo.setStructVector(true);
                                    vectorInfo.setStructFieldName(structFieldName);
                                    vectorInfo.setStructSubFieldName(structSubFieldName);
                                    vectorInfo.setFieldName(structFieldName); // 查询时使用 struct 字段名
                                    vectorInfo.setDataType(subFieldDataType);
                                    vectorInfo.setDim(dimension);
                                    return vectorInfo;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 处理正常的向量字段
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            String name = fieldSchema.getName();
            DataType dataType = fieldSchema.getDataType();
            int dimension = fieldSchema.getDimension() == null ? 0 : fieldSchema.getDimension();
            if (name.equalsIgnoreCase(annsField)) {
                vectorInfo.setDim(dimension);
                vectorInfo.setFieldName(name);
                vectorInfo.setDataType(dataType);
                vectorInfo.setStructVector(false);
            }
        }
        return vectorInfo;
    }

    /**
     * 获取collection的主键PK信息
     *
     * @param collectionName collectionName
     * @return PKFieldInfo
     */
    public static PKFieldInfo getPKFieldInfo(String collectionName) {
        PKFieldInfo pkFieldInfo = new PKFieldInfo();
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            String name = fieldSchema.getName();
            DataType dataType = fieldSchema.getDataType();
            Boolean isPrimaryKey = fieldSchema.getIsPrimaryKey();
            if (isPrimaryKey) {
                pkFieldInfo.setFieldName(name);
                pkFieldInfo.setDataType(dataType);
            }
        }
        return pkFieldInfo;
    }

    /**
     * 为search提供真实的vector
     *
     * @param collection collection
     * @param randomNum  从collection捞取的向量数
     * @param annsField 向量字段名，可以是正常向量字段或 struct 中的向量字段（格式：structFieldName[subFieldName]）
     * @return List<BaseVector>
     */
    public static List<BaseVector> providerSearchVectorDataset(String collection, int randomNum, String annsField) {
        VectorInfo collectionVectorInfo = getCollectionVectorInfo(collection, annsField);
        DataType vectorDataType = collectionVectorInfo.getDataType();
        // 获取主键信息
        PKFieldInfo pkFieldInfo = getPKFieldInfo(collection);
        List<BaseVector> baseVectorDataset = new ArrayList<>();
        QueryResp query = null;
        try {
            String filterStr;
            if (pkFieldInfo.getDataType() == DataType.VarChar) {
                filterStr = pkFieldInfo.getFieldName() + " > \"0\" ";
            } else {
                filterStr = pkFieldInfo.getFieldName() + " > 0 ";
            }
            query = milvusClientV2.query(QueryReq.builder().collectionName(collection)
                    .filter(filterStr)
                    .outputFields(Lists.newArrayList(collectionVectorInfo.getFieldName()))
                    .limit(randomNum)
                    .build());
            // 清空下 recallBaseIdList
            recallBaseIdList.clear();
        } catch (Exception e) {
            log.error("query 异常: " + e.getMessage());
        }
        
        // 处理 Array of Struct 中的向量字段
        if (collectionVectorInfo.isStructVector()) {
            String structFieldName = collectionVectorInfo.getStructFieldName();
            String structSubFieldName = collectionVectorInfo.getStructSubFieldName();
            
            if (query != null && query.getQueryResults() != null) {
                for (QueryResp.QueryResult queryResult : query.getQueryResults()) {
                    Object structArrayObj = queryResult.getEntity().get(structFieldName);
                    if (structArrayObj == null) {
                        continue;
                    }
                    
                    // structArrayObj 是一个 List<Map<String, Object>>，每个 Map 代表一个 struct
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> structArray = (List<Map<String, Object>>) structArrayObj;
                    
                    // 创建 EmbeddingList，包含该行所有 struct 中的向量
                    EmbeddingList embeddingList = new EmbeddingList();
                    int vectorCount = 0;
                    for (Map<String, Object> struct : structArray) {
                        Object vectorObj = struct.get(structSubFieldName);
                        if (vectorObj == null) {
                            continue;
                        }
                        
                        // 根据向量类型创建对应的 BaseVector
                        if (vectorDataType == DataType.FloatVector) {
                            @SuppressWarnings("unchecked")
                            List<Float> floatList = (List<Float>) vectorObj;
                            embeddingList.add(new FloatVec(floatList));
                            vectorCount++;
                        } else if (vectorDataType == DataType.BinaryVector) {
                            ByteBuffer byteBuffer = (ByteBuffer) vectorObj;
                            embeddingList.add(new BinaryVec(byteBuffer));
                            vectorCount++;
                        } else if (vectorDataType == DataType.Float16Vector) {
                            ByteBuffer byteBuffer = (ByteBuffer) vectorObj;
                            embeddingList.add(new Float16Vec(byteBuffer));
                            vectorCount++;
                        } else if (vectorDataType == DataType.BFloat16Vector) {
                            ByteBuffer byteBuffer = (ByteBuffer) vectorObj;
                            embeddingList.add(new BFloat16Vec(byteBuffer));
                            vectorCount++;
                        } else if (vectorDataType == DataType.SparseFloatVector) {
                            @SuppressWarnings("unchecked")
                            SortedMap<Long, Float> sortedMap = (SortedMap<Long, Float>) vectorObj;
                            embeddingList.add(new SparseFloatVec(sortedMap));
                            vectorCount++;
                        } else if (vectorDataType == DataType.Int8Vector) {
                            ByteBuffer byteBuffer = (ByteBuffer) vectorObj;
                            embeddingList.add(new Int8Vec(byteBuffer));
                            vectorCount++;
                        }
                    }
                    
                    // 只有当 EmbeddingList 不为空时才添加（检查是否有添加向量）
                    if (vectorCount > 0) {
                        baseVectorDataset.add(embeddingList);
                    }
                    
                    // 收集recall base id
                    Object pkObj = queryResult.getEntity().get(pkFieldInfo.getFieldName());
                    recallBaseIdList.add(pkObj);
                }
            }
        } else {
            // 处理正常的向量字段
            if (query != null && query.getQueryResults() != null) {
                for (QueryResp.QueryResult queryResult : query.getQueryResults()) {
                Object o = queryResult.getEntity().get(collectionVectorInfo.getFieldName());
                if (vectorDataType == DataType.FloatVector) {
                    if (!(o instanceof List)) {
                        throw new IllegalArgumentException("Expected List for FloatVector field: " + collectionVectorInfo.getFieldName()
                                + ", but got: " + (o == null ? "null" : o.getClass().getName()));
                    }
                    List<?> rawList = (List<?>) o;
                    List<Float> floatList = new ArrayList<>(rawList.size());
                    for (Object item : rawList) {
                        if (!(item instanceof Number)) {
                            throw new IllegalArgumentException("Expected numeric elements for FloatVector field: " + collectionVectorInfo.getFieldName()
                                    + ", but got element: " + (item == null ? "null" : item.getClass().getName()));
                        }
                        floatList.add(((Number) item).floatValue());
                    }
                    baseVectorDataset.add(new FloatVec(floatList));
                }
                if (vectorDataType == DataType.BinaryVector) {
                    ByteBuffer byteBuffer = (ByteBuffer) o;
                    baseVectorDataset.add(new BinaryVec(byteBuffer));
                }
                if (vectorDataType == DataType.Float16Vector) {
                    ByteBuffer byteBuffer = (ByteBuffer) o;
                    baseVectorDataset.add(new Float16Vec(byteBuffer));
                }
                if (vectorDataType == DataType.BFloat16Vector) {
                    ByteBuffer byteBuffer = (ByteBuffer) o;
                    baseVectorDataset.add(new BFloat16Vec(byteBuffer));
                }
                if (vectorDataType == DataType.SparseFloatVector) {
                    if (!(o instanceof Map)) {
                        throw new IllegalArgumentException("Expected Map for SparseFloatVector field: " + collectionVectorInfo.getFieldName()
                                + ", but got: " + (o == null ? "null" : o.getClass().getName()));
                    }
                    Map<?, ?> rawMap = (Map<?, ?>) o;
                    SortedMap<Long, Float> sortedMap = new TreeMap<>();
                    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                        Object k = entry.getKey();
                        Object v = entry.getValue();
                        if (!(k instanceof Number)) {
                            throw new IllegalArgumentException("Expected numeric key for SparseFloatVector field: " + collectionVectorInfo.getFieldName()
                                    + ", but got key: " + (k == null ? "null" : k.getClass().getName()));
                        }
                        if (!(v instanceof Number)) {
                            throw new IllegalArgumentException("Expected numeric value for SparseFloatVector field: " + collectionVectorInfo.getFieldName()
                                    + ", but got value: " + (v == null ? "null" : v.getClass().getName()));
                        }
                        sortedMap.put(((Number) k).longValue(), ((Number) v).floatValue());
                    }
                    baseVectorDataset.add(new SparseFloatVec(sortedMap));
                }
                if (vectorDataType == DataType.Int8Vector) {
                    ByteBuffer byteBuffer = (ByteBuffer) o;
                    baseVectorDataset.add(new Int8Vec(byteBuffer));
                }
                    // 收集recall base id
                    Object pkObj = queryResult.getEntity().get(pkFieldInfo.getFieldName());
                    recallBaseIdList.add(pkObj);
                }
            }
        }
        return baseVectorDataset;
    }

    /**
     * 跟具nq提供search的BaseVector
     *
     * @param baseVectorDataset baseVector数据集
     * @param nq                nq
     * @return List<BaseVector>
     */
    public static List<BaseVector> providerSearchVectorByNq(List<BaseVector> baseVectorDataset, int nq) {
        Random random = new Random();
        int randomNum = baseVectorDataset.size();
        List<BaseVector> baseVectors = new ArrayList<>();
        for (int i = 0; i < nq; i++) {
            BaseVector baseVector = baseVectorDataset.get(random.nextInt(randomNum - 1));
            baseVectors.add(baseVector);
        }
        return baseVectors;
    }

    /**
     * @param collection     collection
     * @param randomNum      从collection捞取的向量数
     * @param inputFieldName function BM25的输入文本
     * @return List<BaseVector>
     */
    public static List<BaseVector> providerSearchFunctionData(String collection, int randomNum, String inputFieldName) {
        // 获取主键信息
        PKFieldInfo pkFieldInfo = getPKFieldInfo(collection);
        List<BaseVector> baseVectorDataset = new ArrayList<>();
        QueryResp query = null;
        try {
            String filterStr;
            if (pkFieldInfo.getDataType() == DataType.VarChar) {
                filterStr = pkFieldInfo.getFieldName() + " > \"0\" ";
            } else {
                filterStr = pkFieldInfo.getFieldName() + " > 0 ";
            }
            query = milvusClientV2.query(QueryReq.builder().collectionName(collection)
                    .filter(filterStr)
                    .outputFields(Lists.newArrayList(inputFieldName))
                    .limit(randomNum)
                    .build());
            log.info("query result:" + query.getQueryResults().size());
        } catch (Exception e) {
            log.error("query 异常: " + e.getMessage());
        }
        for (QueryResp.QueryResult queryResult : query.getQueryResults()) {
            Object o = queryResult.getEntity().get(inputFieldName);
            baseVectorDataset.add(new EmbeddedText(o.toString()));
        }
        return baseVectorDataset;
    }


    // 按照定义规则生成对应的varchar数据
    public String generalVarcharByRoles(String prefix) {
        return null;
    }

    /**
     * 按照随机的规则随机数
     *
     * @param randomRangeParamsList randomRangeParamsList
     * @return integer
     */
    public static int advanceRandom(List<RandomRangeParams> randomRangeParamsList) {
        int bucket = ThreadLocalRandom.current().nextInt(100); // 0..99
        int i = 0;
        double rate = 0.00;
        while (i < randomRangeParamsList.size() - 1) {
            rate = rate + randomRangeParamsList.get(i).getRate();
            int compareNum = (int) (rate * 100);
            if (compareNum >= bucket) {
                break;
            }
            i++;
        }
        return ThreadLocalRandom.current().nextInt(randomRangeParamsList.get(i).getEnd() + 1 - randomRangeParamsList.get(i).getStart()) + randomRangeParamsList.get(i).getStart();
    }

    /**
     * 按照随机的规则，按顺序生成
     *
     * @param randomRangeParamsList randomRangeParamsList
     * @param totalNum              总数据量
     * @param countIndex            生成的数据的下标
     * @param startId               起始ID
     * @return integer
     */
    public static int advanceSequence(List<RandomRangeParams> randomRangeParamsList, long totalNum, long countIndex, long startId) {
        int i = 0;
        int compareNum = 0;
        while (i < (randomRangeParamsList.size() - 1)) {
            compareNum = compareNum + (int) (randomRangeParamsList.get(i).getRate() * totalNum);
            if (compareNum > (countIndex - startId)) {
                break;
            }
            i++;
        }
        int countNum = 0;
        for (int j = 0; j < i; j++) {
            countNum = countNum + (int) (randomRangeParamsList.get(j).getRate() * totalNum);
        }
        long countNum2 = (countIndex - startId) - countNum;
        double averageCount = (totalNum * randomRangeParamsList.get(i).getRate()) / (randomRangeParamsList.get(i).getEnd() - randomRangeParamsList.get(i).getStart() + 1);
        return (int) ((countNum2 / averageCount) + randomRangeParamsList.get(i).getStart());
    }


    /**
     * @param randomRangeParamsList randomRangeParamsList
     * @param totalConcurrencyNum   总线程数/并发数
     * @param concurrencyIndex      当前线程下标
     * @param searchCount           当前线程已经search的次数
     * @return integer
     */
    public static int advanceSequenceForSearch(List<RandomRangeParams> randomRangeParamsList, int totalConcurrencyNum, int concurrencyIndex, int searchCount) {
        // 目前只支持一条数据规则
        RandomRangeParams randomRangeParams = randomRangeParamsList.get(0);
        int start = randomRangeParams.getStart();
        int end = randomRangeParams.getEnd();
        int totalDataNum = end - start + 1;
        // 按线程index划分生成数据的区域
        int perConcurrencyDataNum = totalDataNum / totalConcurrencyNum;
        return (searchCount % perConcurrencyDataNum) + start + (perConcurrencyDataNum * concurrencyIndex);
    }

    /**
     * 随机生成geo-polygon
     *
     * @param numPoints 点数
     * @return POLYGON ((0 100, 100 100, 100 50, 0 50, 0 100))
     */
    public static String generateRandomGeoPolygon(int numPoints) {
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] coords = new Coordinate[numPoints + 1];

        // 生成随机顶点
        for (int i = 0; i < numPoints; i++) {
            coords[i] = new Coordinate(
                    Math.random() * 100,
                    Math.random() * 100
            );
        }
        coords[numPoints] = coords[0]; // 闭合多边形
        Polygon polygon = gf.createPolygon(coords);
        return polygon.toText();
    }

    /**
     * 随机生成geo-lineString
     *
     * @param numPoints 点数
     * @return LINESTRING (10 10, 10 30, 40 40)
     */
    public static String generateRandomGeoLineString(int numPoints) {
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] coords = new Coordinate[numPoints];
        // 生成随机坐标点
        for (int i = 0; i < numPoints; i++) {
            coords[i] = new Coordinate(
                    Math.random() * 100,  // X坐标 (0-100范围)
                    Math.random() * 100   // Y坐标 (0-100范围)
            );
        }
        LineString lineString = gf.createLineString(coords);
        return lineString.toText();
    }

    /**
     * 地理坐标生成（WGS84）
     *
     * @return 随机生成 POINT (1 1)
     */
    public static String generateRandomGeoPoint() {
        GeometryFactory gf = new GeometryFactory(new PrecisionModel(), 4326); // SRID 4326
        Coordinate coord = new Coordinate(
                -180 + Math.random() * 360,  // 经度 (-180到180)
                -90 + Math.random() * 180    // 纬度 (-90到90)
        );
        Point point = gf.createPoint(coord);
        return point.toText();
    }

    public static String generateTimeStamp() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        return LocalDateTime.now().format(dtf);
    }


}