package custom.entity;

import lombok.Data;

@Data
public class FlushParams {
    private boolean flushAll;
    private String collectionName;
}
