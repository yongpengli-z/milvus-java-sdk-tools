package custom.components;

import custom.common.CommonFunction;
import custom.entity.PKFieldInfo;
import custom.entity.RecallParams;
import custom.utils.MathUtil;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.util.Lists;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static custom.BaseTest.*;
import static custom.BaseTest.recallBaseIdList;

@Slf4j
public class RecallComp {

    public static void calcRecall(RecallParams recallParams) {
        // 先search collection
        String collection = (recallParams.getCollectionName() == null ||
                recallParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(0) : recallParams.getCollectionName();

        // 随机向量，从数据库里筛选
        log.info("从collection里捞取向量: " + 100);
        List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 100);
        log.info("提供给search使用的随机向量数: " + searchBaseVectors.size());
        List<Object> searchResult=new ArrayList<>();
        for (int i = 0; i < recallBaseIdList.size(); i++) {
            SearchResp search = milvusClientV2.search(SearchReq.builder()
                    .collectionName(collection)
                    .topK(1)
                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                    .collectionName(collection)
                    .data(Lists.newArrayList(searchBaseVectors.get(i)))
                    .build());
            searchResult.add(search.getSearchResults().get(0).get(0).getId());
        }
        int matchResult=0;
        PKFieldInfo pkFieldInfo = CommonFunction.getPKFieldInfo(collection);
        for (int i = 0; i < searchResult.size(); i++) {
               if(recallBaseIdList.get(i)==searchResult.get(i)){
                   matchResult++;
               }
        }
        log.info("BasePKId:"+recallBaseIdList);
        log.info("SearchResult:"+searchResult);
        double result = (double) matchResult / searchResult.size();
        String formattedResult = String.format("%.4f", result);
        log.info("recall:"+formattedResult);
    }
}
