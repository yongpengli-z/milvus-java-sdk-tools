package custom.entity;


import lombok.Data;

import java.util.List;

@Data
public class CreateIndexParams {
    private String collectionName;
    private List<IndexParams> indexParams;

}
