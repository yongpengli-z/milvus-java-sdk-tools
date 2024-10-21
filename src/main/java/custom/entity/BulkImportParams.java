package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class BulkImportParams {
    List<List<String>> filePaths;
    String collectionName;
    String partitionName;
    String dataset;
}
