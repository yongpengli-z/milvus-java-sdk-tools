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
            R<GetQuerySegmentInfoResponse> querySegmentInfo
                    = milvusClientV1.getQuerySegmentInfo(GetQuerySegmentInfoParam.newBuilder().withCollectionName(collection).build());
            List<QuerySegmentInfo> querySegmentInfoList = querySegmentInfo.getData().getInfosList();
            long collectionId = 0;
            List<Long> segmentIDList = new ArrayList<>();
            List<Long> partitionIDList = new ArrayList<>();
            List<Long> numRowsList = new ArrayList<>();
            List<String> stateList = new ArrayList<>();
            List<String> levelList = new ArrayList<>();
            List<Boolean> isSortedList = new ArrayList<>();
            List<String> indexNameList = new ArrayList<>();
            List<Long> nodeIdList = new ArrayList<>();
            for (QuerySegmentInfo item : querySegmentInfoList) {
                collectionId = item.getCollectionID();
                segmentIDList.add(item.getSegmentID());
                partitionIDList.add(item.getPartitionID());
                numRowsList.add(item.getNumRows());
                stateList.add(item.getState().toString());
                levelList.add(item.getLevel().toString());
                isSortedList.add(item.getIsSorted());
                indexNameList.add(item.getIndexName());
                nodeIdList.add(item.getNodeIds(0));
            }
            QuerySegmentInfoResult.SegmentInfoList segmentInfoList = QuerySegmentInfoResult.SegmentInfoList.builder()
                    .collectionId(collectionId)
                    .segmentIDList(segmentIDList)
                    .partitionIDList(partitionIDList)
                    .numRowsList(numRowsList)
                    .stateList(stateList)
                    .levelList(levelList)
                    .isSortedList(isSortedList)
                    .indexNameList(indexNameList)
                    .nodeIdList(nodeIdList)
                    .build();

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
