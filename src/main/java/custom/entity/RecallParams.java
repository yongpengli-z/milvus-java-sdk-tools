package custom.entity;

import lombok.Data;

@Data
public class RecallParams {
    private String collectionName;
    private int searchLevel;
    String annsField;
}
