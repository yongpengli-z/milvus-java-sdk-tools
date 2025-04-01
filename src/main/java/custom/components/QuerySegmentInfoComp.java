package custom.components;

import custom.entity.QuerySegmentInfoParams;
import custom.entity.result.CommonResult;
import custom.entity.result.QuerySegmentInfoResult;
import custom.entity.result.ResultEnum;
import io.milvus.exception.ParamException;
import io.milvus.grpc.GetQuerySegmentInfoResponse;
import io.milvus.grpc.QuerySegmentInfo;
import io.milvus.param.R;
import io.milvus.param.control.GetQuerySegmentInfoParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV1;

@Slf4j
public class QuerySegmentInfoComp {
    public static QuerySegmentInfoResult querySegmentInfo(QuerySegmentInfoParams querySegmentInfoParams) {
        // 先search collection
        String collection = (querySegmentInfoParams.getCollectionName() == null ||
                querySegmentInfoParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : querySegmentInfoParams.getCollectionName();
        CommonResult commonResult = CommonResult.builder().build();
        try {
            // get query segment info
            log.info("Get query segment info of collection: " + collection);
            R<GetQuerySegmentInfoResponse> querySegmentInfo = milvusClientV1.getQuerySegmentInfo(GetQuerySegmentInfoParam.newBuilder().withCollectionName(collection).build());
            List<QuerySegmentInfo> querySegmentInfoList = querySegmentInfo.getData().getInfosList();
            List<QuerySegmentInfoResult.segmentInfo> segmentInfoList=new ArrayList<>();
            for (QuerySegmentInfo item : querySegmentInfoList) {
                segmentInfoList.add(QuerySegmentInfoResult.segmentInfo.builder()
                                .segmentID(item.getSegmentID())
                                .collectionID(item.getCollectionID())
                                .partitionID(item.getPartitionID())
                                .numRows(item.getNumRows())
                                .state(item.getState().toString())
                                .level(item.getLevel().toString())
                                .isSorted(item.getIsSorted())
                                .indexName(item.getIndexName())
                                .nodeIds(item.getNodeIds(0))
                        .build());
            }


            commonResult.setResult(ResultEnum.SUCCESS.result);
            return QuerySegmentInfoResult.builder()
                    .commonResult(commonResult)
                    .segmentInfoList(segmentInfoList).build();
        } catch (ParamException e) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
            commonResult.setMessage(e.getMessage());
            return QuerySegmentInfoResult.builder().commonResult(commonResult).build();
        }
    }
}
