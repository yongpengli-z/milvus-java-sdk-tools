package custom.components;

import custom.entity.PersistentSegmentInfoParams;
import custom.entity.result.CommonResult;
import custom.entity.result.PersistentSegmentInfoResult;
import custom.entity.result.ResultEnum;
import io.milvus.grpc.GetPersistentSegmentInfoResponse;
import io.milvus.grpc.PersistentSegmentInfo;
import io.milvus.param.R;
import io.milvus.param.control.GetPersistentSegmentInfoParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static custom.BaseTest.globalCollectionNames;
import static custom.BaseTest.milvusClientV1;

@Slf4j
public class PersistentSegmentInfoComp {
    public static PersistentSegmentInfoResult persistentSegmentInfo(PersistentSegmentInfoParams persistentSegmentInfoParams) {
        // 先search collection
        String collection = (persistentSegmentInfoParams.getCollectionName() == null ||
                persistentSegmentInfoParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size() - 1) : persistentSegmentInfoParams.getCollectionName();
        CommonResult commonResult = CommonResult.builder().build();
        try {
            log.info("Start to get persistent segment info of collection: " + collection);
            R<GetPersistentSegmentInfoResponse> persistentSegmentInfo = milvusClientV1.getPersistentSegmentInfo(GetPersistentSegmentInfoParam.newBuilder()
                    .withCollectionName(collection).build());
            List<PersistentSegmentInfo> persistentSegmentInfoList = persistentSegmentInfo.getData().getInfosList();
            long collectionId = 0;
            List<Long> segmentIDList = new ArrayList<>();
            List<Long> partitionIDList = new ArrayList<>();
            List<Long> numRowsList = new ArrayList<>();
            List<String> stateList = new ArrayList<>();
            List<String> levelList = new ArrayList<>();
            List<Boolean> isSortedList = new ArrayList<>();
            for (PersistentSegmentInfo persistentSegmentItem : persistentSegmentInfoList) {
                collectionId = persistentSegmentItem.getCollectionID();
                segmentIDList.add(persistentSegmentItem.getSegmentID());
                partitionIDList.add(persistentSegmentItem.getPartitionID());
                numRowsList.add(persistentSegmentItem.getNumRows());
                stateList.add(persistentSegmentItem.getState().toString());
                levelList.add(persistentSegmentItem.getLevel().toString());
                isSortedList.add(persistentSegmentItem.getIsSorted());
            }
            PersistentSegmentInfoResult.SegmentInfoList segmentInfoList = PersistentSegmentInfoResult.SegmentInfoList.builder()
                    .collectionId(collectionId)
                    .segmentIDList(segmentIDList)
                    .partitionIDList(partitionIDList)
                    .numRowsList(numRowsList)
                    .stateList(stateList)
                    .levelList(levelList)
                    .isSortedList(isSortedList)
                    .build();
            commonResult.setResult(ResultEnum.SUCCESS.result);
            return PersistentSegmentInfoResult.builder().segmentInfoList(segmentInfoList)
                    .commonResult(commonResult)
                    .segmentCount(segmentInfoList.getSegmentIDList().size()).build();
        } catch (Exception e) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
            commonResult.setMessage(e.getMessage());
            return PersistentSegmentInfoResult.builder().commonResult(commonResult).build();
        }
    }
}
