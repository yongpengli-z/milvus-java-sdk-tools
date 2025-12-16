package custom.entity;

import lombok.Data;

@Data
public class RenameCollectionParams {
    String collectionName;
    String newCollectionName;
    String databaseName;
}
