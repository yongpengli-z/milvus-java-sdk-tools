package custom.entity;

import lombok.Data;

@Data
public class CreateAliasParams {
    String databaseName;
    String collectionName;
    String alias;
}
