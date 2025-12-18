package custom.entity;

import lombok.Data;

@Data
public class AlterAliasParams {
    String databaseName;
    String collectionName;
    String alias;
}
