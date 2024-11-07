package custom.entity;

import lombok.Data;

@Data
public class DropIndexParams {
    String collectionName;
    String fieldName;
}
