package custom.common;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import custom.components.MilvusConnect;
import custom.entity.*;
import custom.utils.DatasetUtil;
import custom.utils.GenerateUtil;
import custom.utils.JsonObjectUtil;
import custom.utils.MathUtil;
import io.milvus.common.utils.Float16Utils;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexBuildState;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.index.request.DescribeIndexReq;
import io.milvus.v2.service.index.response.DescribeIndexResp;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.*;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.*;

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
    public static String genCommonCollection(@Nullable String collectionName, boolean enableDynamic, int shardNum, int numPartitions, List<FieldParams> fieldParamsList, FunctionParams functionParams) {
        if (collectionName == null || collectionName.equals("")) {
            collectionName = "Collection_" + GenerateUtil.getRandomString(10);
        }
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = parseDataType(fieldParamsList);
        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fieldSchemaList)
                .enableDynamicField(enableDynamic)
                .build();
        // schema add function
        if (functionParams != null
                && functionParams.getFunctionType() != null
                && functionParams.getInputFieldNames().size() > 0
                && functionParams.getOutputFieldNames().size() > 0) {
            // enableAnalyzer input fields
            for (String inputFieldName : functionParams.getInputFieldNames()) {
                fieldSchemaList.stream().filter(x -> x.getName().equals(inputFieldName)).findFirst().ifPresent(y -> {
                    y.setEnableAnalyzer(true);
                });
            }
            collectionSchema.addFunction(CreateCollectionReq.Function.builder()
                    .functionType(functionParams.getFunctionType())
                    .name(functionParams.getName())
                    .inputFieldNames(functionParams.getInputFieldNames())
                    .outputFieldNames(functionParams.getOutputFieldNames())
                    .build()
            );
        }
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .collectionSchema(collectionSchema)
                .collectionName(collectionName)
                .enableDynamicField(enableDynamic)
                .description("collection desc")
                .numShards(shardNum)
                .numPartitions(numPartitions)
                .build();
        milvusClientV2.createCollection(createCollectionReq);
        return collectionName;
    }

    /**
     * 遍历fieldParamList生成对应的schema
     *
     * @param fieldParamsList field字段集合
     * @return List<CreateCollectionReq.FieldSchema> 给创建collection提供
     */
    public static List<CreateCollectionReq.FieldSchema> parseDataType(List<FieldParams> fieldParamsList) {
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = new ArrayList<>();
        for (int i = 0; i < fieldParamsList.size(); i++) {
            FieldParams fieldParams = fieldParamsList.get(i);
            //按照_分组
            DataType dataType = fieldParams.getDataType();
            CreateCollectionReq.FieldSchema fieldSchema = CreateCollectionReq.FieldSchema.builder()
                    .dataType(dataType)
                    .name(fieldParams.getFieldName() == null ? dataType + "_" + i : fieldParams.getFieldName())
                    .enableMatch(dataType.equals(DataType.VarChar))
                    .isPrimaryKey(fieldParams.isPrimaryKey())
                    .isNullable(fieldParams.isNullable())
                    .build();
            // 判断主键是否autoid
            if (fieldParams.isPrimaryKey()) {
                fieldSchema.setAutoID(fieldParams.isAutoId());
            }
            if (dataType == DataType.FloatVector || dataType == DataType.BFloat16Vector || dataType == DataType.Float16Vector || dataType == DataType.BinaryVector) {
                fieldSchema.setDimension(fieldParams.getDim());
            }
            if (dataType == DataType.String || dataType == DataType.VarChar) {
                fieldSchema.setMaxLength(fieldParams.getMaxLength());
            }
            if (dataType == DataType.Array) {
                fieldSchema.setMaxCapacity(fieldParams.getMaxCapacity());
                fieldSchema.setElementType(fieldParams.getElementType());
                if (fieldParams.getElementType() == DataType.VarChar) {
                    fieldSchema.setMaxLength(fieldParams.getMaxLength());
                }
            }
            fieldSchemaList.add(fieldSchema);
        }

        return fieldSchemaList;
    }


    /**
     * 创建通用索引
     *
     * @param collectionName collection name
     * @param indexParams    index field集合
     */
    public static void createCommonIndex(String collectionName, List<IndexParams> indexParams) {
        log.info("indexParams.size():" + indexParams.size());
        List<IndexParam> indexParamList = new ArrayList<>();
        if (indexParams.size() == 0) {
            DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName((collectionName == null || collectionName.equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : collectionName).build());
            CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
            List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
            for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
                String name = fieldSchema.getName();
                DataType dataType = fieldSchema.getDataType();
                // 给向量自动建索引
                if (dataType == DataType.FloatVector || dataType == DataType.BFloat16Vector || dataType == DataType.Float16Vector || dataType == DataType.BinaryVector) {
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
        milvusClientV2.createIndex(CreateIndexReq.builder()
                .collectionName((collectionName == null || collectionName.equals("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : collectionName)
                .indexParams(indexParamList)
                .build());
        // 查询索引是否建完
        List<Boolean> indexStateList = new ArrayList<>();
        for (IndexParam indexParam : indexParamList) {
            indexStateList.add(false);
        }
        long startTimeTotal = System.currentTimeMillis();
        while (!(indexStateList.size() == indexStateList.stream().filter(x -> x).count())) {
            for (int i = 0; i < indexParamList.size(); i++) {
                DescribeIndexResp describeIndexResp = milvusClientV2.describeIndex(DescribeIndexReq.builder()
                        .fieldName(indexParamList.get(i).getFieldName())
                        .collectionName(collectionName)
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
            default:
                return IndexParam.IndexType.TRIE;
        }
    }

    /**
     * 生成通用的数据
     *
     * @param collectionName 向量名称
     * @param count          生成的数量
     * @return List<JsonObject>
     */
    public static List<JsonObject> genCommonData(String collectionName, long count, long startId, String dataset, List<String> fileNames, List<Long> fileSizeList) {
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        // 获取function列表，查找不需要构建数据的 outputFieldNames
        List<CreateCollectionReq.Function> functionList = collectionSchema.getFunctionList();
        List<String> tempOutputFieldNames=new ArrayList<>();
        for (CreateCollectionReq.Function function : functionList) {
            List<String> outputFieldNames1 = function.getOutputFieldNames();
            tempOutputFieldNames.addAll(outputFieldNames1);
        }
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
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
            for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
                String name = fieldSchema.getName();
                DataType dataType = fieldSchema.getDataType();
                Integer dimension = fieldSchema.getDimension();
                Integer maxCapacity = fieldSchema.getMaxCapacity();
                Integer maxLength = fieldSchema.getMaxLength();
                DataType elementType = fieldSchema.getElementType();
                boolean isNullable = fieldSchema.getIsNullable();
                // primary key auto id
                if (fieldSchema.getIsPrimaryKey() && fieldSchema.getAutoID()) {
                    continue;
                }
                // 如果使用function自动生成数据，则继续
                if (tempOutputFieldNames.contains(name)){
                    continue;
                }
                JsonObject jsonObject = new JsonObject();
                Gson gson = new Gson();
                if (dataType == DataType.FloatVector || dataType == DataType.BFloat16Vector || dataType == DataType.Float16Vector || dataType == DataType.BinaryVector) {
                    if (dataset.equalsIgnoreCase("random")) {
                        jsonObject = generalJsonObjectByDataType(name, dataType, dimension, i, null, 0);
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
                    jsonObject = generalJsonObjectByDataType(name, dataType, 100, i, null, 0);
                } else if (dataType == DataType.VarChar || dataType == DataType.String) {
                    JsonObject jsonObjectItem = new JsonObject();
                    jsonObjectItem.add(name, null);
                    jsonObject = (isNullable && i % 2 == 0) ? jsonObjectItem : generalJsonObjectByDataType(name, dataType, maxLength, i, null, 0);
                } else if (dataType == DataType.Array) {
                    JsonObject jsonObjectItem = new JsonObject();
                    jsonObjectItem.add(name, null);
                    jsonObject = (isNullable && i % 2 == 0) ? jsonObjectItem : generalJsonObjectByDataType(name, dataType, maxCapacity, i, elementType, maxLength);
                } else {
                    JsonObject jsonObjectItem = new JsonObject();
                    jsonObjectItem.add(name, null);
                    jsonObject = (isNullable && i % 2 == 0) ? jsonObjectItem : generalJsonObjectByDataType(name, dataType, 0, i, null, 0);
                }
                row = JsonObjectUtil.jsonMerge(row, jsonObject);
            }
            // 判断是否有动态列
            if (describeCollectionResp.getCollectionSchema().isEnableDynamicField()) {
                JsonObject jsonObject = generalJsonObjectByDataType(CommonData.dynamicField, DataType.JSON, 0, i, null, 0);
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
    public static JsonObject generalJsonObjectByDataType(String fieldName, DataType dataType, int dimOrLength, long countIndex, DataType elementType, int lengthForCapacity) {
        JsonObject row = new JsonObject();
        Gson gson = new Gson();
        Random random = new Random();
        if (dataType == DataType.Int64) {
            row.add(fieldName, gson.toJsonTree(countIndex));
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
//            int i = random.nextInt(dimOrLength /2);
            String s;
            if (countIndex % 9 == 0) {
                s = "abcdefg";
            } else if (countIndex % 8 == 0) {
                s = "samevalue";
            } else {
                s = MathUtil.genRandomString(dimOrLength);
            }
            row.add(fieldName, gson.toJsonTree(s));
        }
        if (dataType == DataType.String) {
//            int i = random.nextInt(dimOrLength );
            String s = MathUtil.genRandomString(dimOrLength);
            row.add(fieldName, gson.toJsonTree(s));
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
     * 获取collection的向量信息
     *
     * @param collectionName collection
     * @return VectorInfo
     */
    public static VectorInfo getCollectionVectorInfo(String collectionName, String annsField) {
        VectorInfo vectorInfo = new VectorInfo();
        DescribeCollectionResp describeCollectionResp = milvusClientV2.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        for (CreateCollectionReq.FieldSchema fieldSchema : fieldSchemaList) {
            String name = fieldSchema.getName();
            DataType dataType = fieldSchema.getDataType();
            int dimension = fieldSchema.getDimension() == null ? 0 : fieldSchema.getDimension();
            if (name.equalsIgnoreCase(annsField)) {
                vectorInfo.setDim(dimension);
                vectorInfo.setFieldName(name);
                vectorInfo.setDataType(dataType);
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
        for (QueryResp.QueryResult queryResult : query.getQueryResults()) {
            Object o = queryResult.getEntity().get(collectionVectorInfo.getFieldName());
            if (vectorDataType == DataType.FloatVector) {
                List<Float> floatList = (List<Float>) o;
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
                SortedMap<Long, Float> sortedMap = (SortedMap<Long, Float>) o;
                baseVectorDataset.add(new SparseFloatVec(sortedMap));
            }
            // 收集recall base id
            Object pkObj = queryResult.getEntity().get(pkFieldInfo.getFieldName());
            recallBaseIdList.add(pkObj);
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
     *
     * @param collection  collection
     * @param randomNum 从collection捞取的向量数
     * @param inputFieldName function BM25的输入文本
     * @return List<BaseVector>
     */
    public static List<BaseVector> providerSearchFunctionData(String collection,int randomNum,String inputFieldName) {
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
            log.info("query result:"+query.getQueryResults().size());
        } catch (Exception e){
            log.error("query 异常: " + e.getMessage());
        }
        for (QueryResp.QueryResult queryResult : query.getQueryResults()) {
            Object o = queryResult.getEntity().get(inputFieldName);
            baseVectorDataset.add(new EmbeddedText(o.toString()));
        }
        return baseVectorDataset;
    }

    public static void main(String[] args) {

        String uri1="https://in01-b1dc77ab75a8fdd.aws-us-west-2.vectordb-uat3.zillizcloud.com:19530";
        String uri2="https://in01-c560fd4b0c11191.aws-us-west-2.vectordb-uat3.zillizcloud.com:19532";
        String uri3="https://in01-8b4bf16d21680d1.aws-us-west-2.vectordb-uat3.zillizcloud.com:19534";
        String collection1="Collection_TAbaiqfFxG";
        String collection2="Collection_zabLqnEHjG";
        String collection3="Collection_BOnJHTNzYP";
        MilvusClientV2 milvusClientV22222 = new MilvusClientV2(
                ConnectConfig.builder().uri(uri2).secure(true)
                        .token("29e5f94f1244eae2bd1cb6fd627d299d2f7e17bf4390d53a904eb30d954ab9ce03dfd18ad4ff9d66cea05714bb6b142a7722e434").build());
        String collectionName=collection2;
        DescribeCollectionResp describeCollectionResp = milvusClientV22222.describeCollection(DescribeCollectionReq.builder().collectionName(collectionName).build());
        CreateCollectionReq.CollectionSchema collectionSchema = describeCollectionResp.getCollectionSchema();
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = collectionSchema.getFieldSchemaList();
        // 获取function列表，查找不需要构建数据的 outputFieldNames
        List<CreateCollectionReq.Function> functionList = collectionSchema.getFunctionList();
        List<String> tempOutputFieldNames=new ArrayList<>();
        for (CreateCollectionReq.Function function : functionList) {
            List<String> outputFieldNames1 = function.getOutputFieldNames();
            tempOutputFieldNames.addAll(outputFieldNames1);
        }
        System.out.println(tempOutputFieldNames);

        // query
        QueryResp queryResp = milvusClientV22222.query(QueryReq.builder().collectionName(collectionName)
                .filter("Int64_0 > 160000 ")
                .outputFields(Lists.newArrayList("VarChar_14"))
                .limit(10)
                .build());
        List<QueryResp.QueryResult> queryResults = queryResp.getQueryResults();
        System.out.println(queryResults.get(0).getEntity().get("VarChar_14"));
        // search
        SearchResp suijishu = milvusClientV22222.search(SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new EmbeddedText(queryResults.get(0).getEntity().get("VarChar_14").toString())))
                        .topK(1)
                .annsField("SparseFloatVector_22").build());
        System.out.println(suijishu.getSearchResults());


    }

}