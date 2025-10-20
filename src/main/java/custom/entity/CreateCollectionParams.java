package custom.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author yongpeng.li @Date 2024/6/4 16:57
 */
@Data
public class CreateCollectionParams {
    private String collectionName;
    private int shardNum;
    private int  numPartitions;
    private boolean enableDynamic;
    private List<FieldParams> fieldParamsList;
    private FunctionParams functionParams;
    private List<PropertyM> properties;

    @Data
    public static class PropertyM {
        String propertyKey;
        String propertyValue;
    }

}
