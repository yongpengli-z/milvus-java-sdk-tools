package custom.components;

import custom.common.CommonFunction;
import custom.entity.CreateIndexParams;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateIndexComp {
    public static void CreateIndex(CreateIndexParams createIndexParams){
        CommonFunction.createCommonIndex(createIndexParams.getCollectionName(),createIndexParams.getIndexParams());
    }
}
