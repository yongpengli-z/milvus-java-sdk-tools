package custom.components;

import custom.common.CommonFunction;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;

/** Shared request preparation for SDK 3.0.4 advanced search components. */
final class AdvancedSearchSupport {
    private AdvancedSearchSupport() {
    }

    static PreparedSearch prepare(MilvusClientV2 client, String collectionName, String annsField, int nq) {
        if (annsField == null || annsField.trim().isEmpty()) {
            throw new IllegalArgumentException("annsField must not be empty");
        }
        if (nq <= 0) {
            throw new IllegalArgumentException("nq must be greater than zero");
        }

        String collection = collectionName;
        if (collection == null || collection.trim().isEmpty()) {
            if (globalCollectionNames.isEmpty()) {
                throw new IllegalStateException("collectionName is required when no collection has been created in this task");
            }
            collection = globalCollectionNames.get(globalCollectionNames.size() - 1);
        }

        DescribeCollectionResp describeResponse = client.describeCollection(
                DescribeCollectionReq.builder().collectionName(collection).build());
        List<CreateCollectionReq.Function> functions = describeResponse.getCollectionSchema().getFunctionList();
        if (functions == null) {
            functions = Collections.emptyList();
        }

        String inputField = null;
        for (CreateCollectionReq.Function function : functions) {
            if (function.getOutputFieldNames().contains(annsField)) {
                int index = function.getOutputFieldNames().indexOf(annsField);
                inputField = function.getInputFieldNames().get(index);
                break;
            }
        }

        List<BaseVector> candidates = inputField == null
                ? CommonFunction.providerSearchVectorDataset(client, collection, 1000, annsField)
                : CommonFunction.providerSearchFunctionData(client, collection, 1000, inputField);
        List<BaseVector> vectors = CommonFunction.providerSearchVectorByNq(candidates, nq);
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalStateException("no query vectors are available for collection " + collection);
        }
        return new PreparedSearch(collection, vectors);
    }

    static SearchReq.SearchReqBuilder baseRequest(String collection, String annsField, int topK,
                                                   List<String> outputFields, String filter,
                                                   List<String> partitionNames, List<BaseVector> vectors) {
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than zero");
        }
        return SearchReq.builder()
                .collectionName(collection)
                .annsField(annsField)
                .topK(topK)
                .outputFields(outputFields == null ? new ArrayList<>() : outputFields)
                .filter(filter == null ? "" : filter)
                .partitionNames(partitionNames == null ? new ArrayList<>() : partitionNames)
                .data(vectors);
    }

    static final class PreparedSearch {
        final String collection;
        final List<BaseVector> vectors;

        private PreparedSearch(String collection, List<BaseVector> vectors) {
            this.collection = collection;
            this.vectors = vectors;
        }
    }
}
