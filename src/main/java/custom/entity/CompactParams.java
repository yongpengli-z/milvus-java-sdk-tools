package custom.entity;

import lombok.Data;

@Data
public class CompactParams {
    boolean compactAll;
    String collectionName;
    boolean isClustering;
}
