package custom.entity;

import lombok.Data;

@Data
public class DropCollectionParams {
    private boolean dropAll;
    private String collectionName;
}
