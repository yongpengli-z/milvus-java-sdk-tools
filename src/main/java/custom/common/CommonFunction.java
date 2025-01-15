package custom.common;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import custom.entity.FieldParams;
import custom.entity.IndexParams;
import custom.entity.PKFieldInfo;
import custom.entity.VectorInfo;
import custom.utils.DatasetUtil;
import custom.utils.GenerateUtil;
import custom.utils.JsonObjectUtil;
import custom.utils.MathUtil;
import io.milvus.common.utils.Float16Utils;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexBuildState;
import io.milvus.v2.common.IndexParam;
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
    public static String genCommonCollection(@Nullable String collectionName, boolean enableDynamic, int shardNum, int numPartitions, List<FieldParams> fieldParamsList) {
        if (collectionName == null || collectionName.equals("")) {
            collectionName = "Collection_" + GenerateUtil.getRandomString(10);
        }
        List<CreateCollectionReq.FieldSchema> fieldSchemaList = parseDataType(fieldParamsList);
        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fieldSchemaList)
                .enableDynamicField(enableDynamic)
                .build();
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
                    .isPrimaryKey(fieldParams.isPrimaryKey())
                    .build();
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
            for (IndexParams indexParamItem : indexParams) {
                IndexParam indexParam = IndexParam.builder()
                        .fieldName(indexParamItem.getFieldName())
                        .indexName("idx_" + indexParamItem.getFieldName())
                        .indexType(indexParamItem.getIndextype())
                        .extraParams(CommonFunction.provideExtraParam(indexParamItem.getIndextype()))
                        .metricType(indexParamItem.getMetricType())
                        .build();
                log.info("indexParam:"+ indexParam.toString());
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
                    jsonObject = generalJsonObjectByDataType(name, dataType, 1000, i, null, 0);
                } else if (dataType == DataType.VarChar || dataType == DataType.String) {
                    jsonObject = generalJsonObjectByDataType(name, dataType, maxLength, i, null, 0);
                } else if (dataType == DataType.Array) {
                    jsonObject = generalJsonObjectByDataType(name, dataType, maxCapacity, i, elementType, maxLength);
                } else {
                    jsonObject = generalJsonObjectByDataType(name, dataType, 0, i, null, 0);
                }
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
            row.addProperty(fieldName, countIndex);
        }
        if (dataType == DataType.Int32) {
            row.addProperty(fieldName, (int) countIndex % 32767);
        }
        if (dataType == DataType.Int16) {
            row.addProperty(fieldName, (int) countIndex % 32767);
        }
        if (dataType == DataType.Int8) {
            row.addProperty(fieldName, (short) countIndex % 127);
        }
        if (dataType == DataType.Double) {
            row.addProperty(fieldName, (double) countIndex * 0.1f);
        }
        if (dataType == DataType.Array) {
            List<Object> list = MathUtil.providerArrayData(elementType, dimOrLength, lengthForCapacity);
            row.add(fieldName, gson.toJsonTree(list));
        }
        if (dataType == DataType.Bool) {
            row.addProperty(fieldName, countIndex % 2 == 0);
        }
        if (dataType == DataType.VarChar) {
//            int i = random.nextInt(dimOrLength /2);
            String s;
           /* if (countIndex % 9 == 0) {
                s = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            } else if (countIndex % 8 == 0) {
                s = "samevalue";
            } else {*/
            s = MathUtil.genRandomString(dimOrLength);
//            }
            row.addProperty(fieldName, s);
        }
        if (dataType == DataType.String) {
//            int i = random.nextInt(dimOrLength );
            String s = MathUtil.genRandomString(dimOrLength);
            row.addProperty(fieldName, s);
        }
        if (dataType == DataType.Float) {
            row.addProperty(fieldName, (float) countIndex * 0.1f);
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
            json.addProperty(CommonData.fieldInt64, (int) countIndex % 32767);
            json.addProperty(CommonData.fieldInt32, (int) countIndex % 32767);
            json.addProperty(CommonData.fieldDouble, (double) countIndex);
            json.add(CommonData.fieldArray, gson.toJsonTree(Arrays.asList(countIndex, countIndex + 1, countIndex + 2)));
            json.addProperty(CommonData.fieldBool, countIndex % 2 == 0);
            json.addProperty(CommonData.fieldVarchar, "Str" + countIndex);
            json.addProperty(CommonData.fieldFloat, (float) countIndex);
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
}