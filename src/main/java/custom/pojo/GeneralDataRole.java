package custom.pojo;

import lombok.Data;

import java.util.List;

@Data
public class GeneralDataRole {
    String fieldName;
    String prefix;
    String sequenceOrRandom;
    List<RandomRangeParams> randomRangeParamsList;
    /** New deterministic insert rule; absent means the legacy sequence/random rule. */
    String generationMode;
    /** Object keys below a JSON field; empty means the scalar field itself. */
    List<String> jsonKeys;
    /** Required for JSON leaves: INT64, DOUBLE, or STRING. */
    String valueType;
    Long cardinality;
    Long divisor;
    Long seed;
}
