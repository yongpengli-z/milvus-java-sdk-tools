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
            List<PersistentSegmentInfoResult.segmentInfo> segmentInfoList = new ArrayList<>();
            for (PersistentSegmentInfo persistentSegmentItem : persistentSegmentInfoList) {
                segmentInfoList.add(
                        PersistentSegmentInfoResult.segmentInfo.builder()
                                .segmentID(persistentSegmentItem.getSegmentID())
                                .collectionID(persistentSegmentItem.getCollectionID())
                                .partitionID(persistentSegmentItem.getPartitionID())
                                .numRows(persistentSegmentItem.getNumRows())
                                .state(persistentSegmentItem.getState().toString())
                                .level(persistentSegmentItem.getLevel().toString())
                                .isSorted(persistentSegmentItem.getIsSorted())
                                .build()
                );
            }
            commonResult.setResult(ResultEnum.SUCCESS.result);
            return PersistentSegmentInfoResult.builder().segmentInfoList(segmentInfoList)
                    .commonResult(commonResult).build();
        } catch (Exception e) {
            commonResult.setResult(ResultEnum.EXCEPTION.result);
            commonResult.setMessage(e.getMessage());
            return PersistentSegmentInfoResult.builder().commonResult(commonResult).build();
        }
    }
}
