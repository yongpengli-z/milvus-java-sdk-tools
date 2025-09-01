package custom.pojo;

import lombok.Data;

import java.util.List;

@Data
public class GeneralDataRole {
    String fieldName;
    String prefix;
    String sequenceOrRandom;
    List<RandomRangeParams> randomRangeParamsList;
}
