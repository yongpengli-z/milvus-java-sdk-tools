package custom.entity;

import lombok.Data;

import java.util.List;

@Data
public class LoadParams {
    private boolean loadAll;
    private String collectionName;
    private List<String> loadFields;
    private boolean skipLoadDynamicField;

}
