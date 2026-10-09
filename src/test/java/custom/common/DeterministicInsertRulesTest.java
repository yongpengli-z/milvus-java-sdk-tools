package custom.common;

import com.google.gson.JsonObject;
import custom.pojo.GeneralDataRole;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicInsertRulesTest {
    @Test
    void scalarAndJsonRulesStayStableAcrossBatches() {
        DescribeCollectionResp collection = collection();
        List<GeneralDataRole> rules = rules();
        List<JsonObject> all = generate(collection, rules, 98, 5);
        List<JsonObject> split = new ArrayList<>(generate(collection, rules, 98, 2));
        split.addAll(generate(collection, rules, 100, 3));

        for (int offset = 0; offset < all.size(); offset++) {
            long id = 98L + offset;
            JsonObject row = all.get(offset);
            JsonObject fromSplit = split.get(offset);
            assertEquals(id, row.get("id").getAsLong());
            assertEquals(id % 100, row.get("g100").getAsLong());
            assertEquals("g_" + id % 100, row.get("g_str100").getAsString());
            assertEquals(id / 100 % 10_000, row.get("g10k").getAsLong());
            assertEquals(id % 100, row.getAsJsonObject("meta").get("g100").getAsLong());
            assertTrue(row.getAsJsonObject("meta").has(CommonData.fieldInt64));
            assertEquals(row.get("filter_bucket"), fromSplit.get("filter_bucket"));
            assertEquals(row.get("g100"), fromSplit.get("g100"));
            assertEquals(row.get("g10k"), fromSplit.get("g10k"));
            assertEquals(row.getAsJsonObject("meta").get("g100"),
                    fromSplit.getAsJsonObject("meta").get("g100"));
        }
    }

    @Test
    void conflictingSourcesAndJsonPathsFailBeforeInsert() {
        DescribeCollectionResp collection = collection();
        List<GeneralDataRole> rules = rules();
        assertThrows(IllegalArgumentException.class, () -> CommonFunction.validateDeterministicInsertRules(
                rules, collection, Collections.singleton("g100")));

        GeneralDataRole nested = rule("meta", "cyclic", 100);
        nested.setJsonKeys(Arrays.asList("g100", "nested"));
        nested.setValueType("INT64");
        rules.add(nested);
        assertThrows(IllegalArgumentException.class, () -> CommonFunction.validateDeterministicInsertRules(
                rules, collection, Collections.emptySet()));
    }

    @Test
    void hashModeWithWhitespaceUsesHashDistribution() {
        DescribeCollectionResp collection = collection();
        GeneralDataRole hash = rule("filter_bucket", "hash", 100);
        hash.setSeed(42L);
        GeneralDataRole paddedHash = rule("filter_bucket", " hash ", 100);
        paddedHash.setSeed(42L);

        List<JsonObject> expected = generate(collection, Collections.singletonList(hash), 0, 20);
        List<JsonObject> actual = generate(collection, Collections.singletonList(paddedHash), 0, 20);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).get("filter_bucket"), actual.get(i).get("filter_bucket"));
        }
    }

    private static List<JsonObject> generate(DescribeCollectionResp collection, List<GeneralDataRole> rules,
                                             long startId, long count) {
        return CommonFunction.genCommonData(count, startId, rules, 10_000_000, 0, collection,
                Collections.emptyMap(), 0.5, 0.0);
    }

    private static List<GeneralDataRole> rules() {
        GeneralDataRole g100 = rule("g100", "cyclic", 100);
        GeneralDataRole g10k = rule("g10k", "cyclic", 10_000);
        g10k.setDivisor(100L);
        GeneralDataRole gStr100 = rule("g_str100", "cyclic", 100);
        gStr100.setPrefix("g_");
        GeneralDataRole jsonG100 = rule("meta", "cyclic", 100);
        jsonG100.setJsonKeys(Collections.singletonList("g100"));
        jsonG100.setValueType("INT64");
        GeneralDataRole filterBucket = rule("filter_bucket", "hash", 100);
        filterBucket.setSeed(42L);
        return new ArrayList<>(Arrays.asList(g100, g10k, gStr100, jsonG100, filterBucket));
    }

    private static GeneralDataRole rule(String field, String mode, long cardinality) {
        GeneralDataRole rule = new GeneralDataRole();
        rule.setFieldName(field);
        rule.setGenerationMode(mode);
        rule.setCardinality(cardinality);
        return rule;
    }

    private static DescribeCollectionResp collection() {
        List<CreateCollectionReq.FieldSchema> fields = Arrays.asList(
                field("id", DataType.Int64, true),
                field("g100", DataType.Int64, false),
                field("g10k", DataType.Int64, false),
                CreateCollectionReq.FieldSchema.builder().name("g_str100").dataType(DataType.VarChar)
                        .maxLength(32).isPrimaryKey(false).autoID(false).isNullable(false).build(),
                field("meta", DataType.JSON, false),
                field("filter_bucket", DataType.Int8, false));
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fields).functionList(Collections.emptyList())
                .structFields(Collections.emptyList()).build();
        return DescribeCollectionResp.builder().collectionSchema(schema).build();
    }

    private static CreateCollectionReq.FieldSchema field(String name, DataType type, boolean primaryKey) {
        return CreateCollectionReq.FieldSchema.builder().name(name).dataType(type)
                .isPrimaryKey(primaryKey).autoID(false).isNullable(false).build();
    }
}
