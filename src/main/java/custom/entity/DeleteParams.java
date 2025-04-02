package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class DeleteParams {
    String collectionName;
    List<Object> ids;
    String filter;
    String partitionName;
}
