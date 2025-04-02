package custom.components;

import custom.entity.DeleteParams;
import custom.entity.result.CommonResult;
import custom.entity.result.DeleteResult;
import custom.entity.result.ResultEnum;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.response.DeleteResp;
import lombok.Data;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV2;

@Data
public class DeleteComp {
    public static DeleteResult delete(DeleteParams deleteParams) {
        // 先search collection
        String collection = (deleteParams.getCollectionName() == null ||
                deleteParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : deleteParams.getCollectionName();
        CommonResult commonResult = CommonResult.builder().build();

        // 然后delete
        // TODO: delete logic here
        try {
            DeleteReq deleteReq = DeleteReq.builder()
                    .collectionName(collection)
                    .partitionName(deleteParams.getPartitionName())
                    .build();
            if (deleteParams.getIds().size() > 0) {
                deleteReq.setIds(deleteParams.getIds());
            }
            if (deleteParams.getFilter() != null && !deleteParams.getFilter().equalsIgnoreCase("")) {
                deleteReq.setFilter(deleteParams.getFilter());
            }
            DeleteResp deleteResp = milvusClientV2.delete(deleteReq);
            long deleteCnt = deleteResp.getDeleteCnt();
            commonResult.setResult(ResultEnum.SUCCESS.result);
            return DeleteResult.builder().deletedCount(deleteCnt).commonResult(commonResult).build();
        } catch (Exception e) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
            commonResult.setMessage(e.getMessage());
            return DeleteResult.builder().commonResult(commonResult).build();
        }
    }
}
