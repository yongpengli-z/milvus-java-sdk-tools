package custom.entity;

import lombok.Data;

import java.util.List;

/**
 * @Author yongpeng.li @Date 2024/6/4 16:57
 */
@Data
public class CreateCollectionParams {
    private String collectionName;
    private int shardNum;
    private int  numPartitions;
    private String enableMmap;
    private boolean enableDynamic;
    private List<FieldParams> fieldParamsList;

}
