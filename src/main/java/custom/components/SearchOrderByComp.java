package custom.components;

import custom.entity.SearchOrderByParams;
import custom.entity.result.CommonResult;
import custom.entity.result.ResultEnum;
import custom.entity.result.SearchOrderByResult;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.aggregation.AggDirection;
import io.milvus.v2.service.vector.request.aggregation.OrderByField;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static custom.BaseTest.getMilvusClient;

/** Executes vector search with the SDK 3.0.4 orderByFields option. */
@Slf4j
public class SearchOrderByComp {
    public static SearchOrderByResult search(SearchOrderByParams params) {
        List<String> assertMessages = new ArrayList<>();
        try {
            if (params == null || params.getOrderByFields() == null || params.getOrderByFields().isEmpty()) {
                throw new IllegalArgumentException("orderByFields must contain at least one field");
            }
            MilvusClientV2 client = getMilvusClient(params.getTargetEndpoint());
            AdvancedSearchSupport.PreparedSearch prepared = AdvancedSearchSupport.prepare(
                    client, params.getCollectionName(), params.getAnnsField(), params.getNq());
            List<OrderByField> orderByFields = params.getOrderByFields().stream()
                    .map(SearchOrderByComp::toOrderByField)
                    .collect(Collectors.toList());
            SearchReq request = AdvancedSearchSupport.baseRequest(prepared.collection, params.getAnnsField(),
                            params.getTopK(), params.getOutputFields(), params.getFilter(),
                            params.getPartitionNames(), prepared.vectors)
                    .orderByFields(orderByFields)
                    .build();
            long timeout = params.getTimeout() > 0 ? params.getTimeout() : 800;
            SearchResp response = client.withTimeout(timeout, TimeUnit.MILLISECONDS).search(request);
            if (response.getSearchResults() == null || response.getSearchResults().isEmpty()) {
                assertMessages.add("[ASSERT WARN] ordered search returned no vector-search results");
            }
            return SearchOrderByResult.builder()
                    .searchResults(response.getSearchResults())
                    .commonResult(CommonResult.builder().result(ResultEnum.SUCCESS.result).build())
                    .assertMessages(assertMessages)
                    .build();
        } catch (Exception e) {
            log.error("Ordered vector search failed", e);
            assertMessages.add("[ASSERT FAIL] ordered search exception: " + e.getMessage());
            return SearchOrderByResult.builder()
                    .commonResult(CommonResult.builder().result(ResultEnum.EXCEPTION.result).message(e.getMessage()).build())
                    .assertMessages(assertMessages)
                    .build();
        }
    }

    private static OrderByField toOrderByField(SearchOrderByParams.OrderByFieldParams field) {
        if (field == null || field.getFieldName() == null || field.getFieldName().trim().isEmpty()) {
            throw new IllegalArgumentException("orderByFields.fieldName must not be empty");
        }
        return OrderByField.builder()
                .fieldName(field.getFieldName())
                .direction(direction(field.getDirection(), "orderByFields.direction"))
                .build();
    }

    static AggDirection direction(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be ASC or DESC");
        }
        try {
            return AggDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " must be ASC or DESC", e);
        }
    }
}
