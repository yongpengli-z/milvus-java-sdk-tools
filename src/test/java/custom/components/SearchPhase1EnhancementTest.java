package custom.components;

import com.alibaba.fastjson.JSON;
import custom.entity.SearchParams;
import custom.entity.result.SearchResultA;
import io.milvus.grpc.KeyValuePair;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.utils.VectorUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPhase1EnhancementTest {
    @Test
    void explicitCorpusCyclesAcrossRequests() {
        List<BaseVector> vectors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            vectors.add(new FloatVec(Collections.singletonList((float) i)));
        }

        List<BaseVector> first = AdvancedSearchSupport.queryBatch(vectors, 2, 0);
        List<BaseVector> second = AdvancedSearchSupport.queryBatch(vectors, 2, 2);
        List<BaseVector> wrapped = AdvancedSearchSupport.queryBatch(vectors, 2, 4);

        assertSame(vectors.get(0), first.get(0));
        assertSame(vectors.get(1), first.get(1));
        assertSame(vectors.get(2), second.get(0));
        assertSame(vectors.get(3), second.get(1));
        assertSame(vectors.get(4), wrapped.get(0));
        assertSame(vectors.get(0), wrapped.get(1));
    }

    @Test
    void pluralGroupByUsesRpcSuccessInsteadOfHitCount() {
        List<Integer> hitCounts = Arrays.asList(40, 10, 0, -1);
        assertEquals(3, SearchComp.countPassingRequests(hitCounts, true, 10));
        assertEquals(1, SearchComp.countPassingRequests(hitCounts, false, 10));
    }

    @Test
    void resultCaptureIsRestrictedToOneRequest() {
        SearchParams params = new SearchParams();
        params.setCaptureSearchResults(true);
        params.setNumConcurrency(1);
        params.setRunningCount(1);
        SearchComp.validateCaptureConfig(params);

        params.setRunningCount(2);
        assertThrows(IllegalArgumentException.class, () -> SearchComp.validateCaptureConfig(params));
        params.setRunningCount(1);
        params.setNumConcurrency(2);
        assertThrows(IllegalArgumentException.class, () -> SearchComp.validateCaptureConfig(params));
    }

    @Test
    void capturedHitsRemainAvailableInReportedResult() {
        Map<String, Object> entity = Collections.singletonMap("g100", 7L);
        SearchResp.SearchResult hit = SearchResp.SearchResult.builder()
                .id(107L).score(0.8f).entity(entity).build();
        SearchResultA result = SearchResultA.builder()
                .searchResults(Collections.singletonList(Collections.singletonList(hit))).build();

        assertEquals(7L, result.getSearchResults().get(0).get(0).getEntity().get("g100"));
        assertTrue(JSON.toJSONString(result).contains("\"g100\":7"));
    }

    @Test
    void sdkSerializesPluralAndJsonGroupByParameters() {
        SearchParams params = new SearchParams();
        params.setGroupByFields(Arrays.asList("g100", "g10k"));
        params.setGroupSize(4);
        params.setStrictGroupSize(true);
        Map<String, Object> searchLevel = new HashMap<>();
        SearchComp.addGroupBySearchParams(searchLevel, params);

        Map<String, String> wire = wireSearchParams(searchLevel);
        assertEquals("g100,g10k", wire.get("group_by_fields"));
        assertEquals("4", wire.get("group_size"));
        assertEquals("true", wire.get("strict_group_size"));

        params.setGroupByFields(Collections.singletonList("meta[\"g100\"]"));
        params.setGroupByJsonType("Int64");
        params.setGroupByStrictCast(true);
        searchLevel.clear();
        SearchComp.addGroupBySearchParams(searchLevel, params);
        wire = wireSearchParams(searchLevel);
        assertEquals("meta[\"g100\"]", wire.get("group_by_fields"));
        assertEquals("Int64", wire.get("json_type"));
        assertEquals("true", wire.get("strict_cast"));
    }

    private static Map<String, String> wireSearchParams(Map<String, Object> searchParams) {
        SearchReq request = SearchReq.builder()
                .collectionName("test_collection")
                .annsField("vector")
                .topK(10)
                .data(Collections.singletonList(new FloatVec(Arrays.asList(0.1f, 0.2f))))
                .outputFields(Collections.singletonList("g100"))
                .partitionNames(Collections.emptyList())
                .searchParams(searchParams)
                .build();
        return new VectorUtils().ConvertToGrpcSearchRequest(request).getSearchParamsList().stream()
                .collect(Collectors.toMap(KeyValuePair::getKey, KeyValuePair::getValue, (first, second) -> first));
    }
}
