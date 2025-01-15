package custom.components;

import com.google.common.collect.Lists;
import custom.common.CommonFunction;
import custom.entity.RecallParams;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static custom.BaseTest.*;
import static custom.BaseTest.recallBaseIdList;

@Slf4j
public class RecallComp {

    public static void calcRecall(RecallParams recallParams) {
        // 先search collection
        String collection = (recallParams.getCollectionName() == null ||
                recallParams.getCollectionName().equalsIgnoreCase("")) ? globalCollectionNames.get(globalCollectionNames.size()-1) : recallParams.getCollectionName();

        // 随机向量，从数据库里筛选
        log.info("从collection里捞取向量: " + 10000);
        List<BaseVector> searchBaseVectors = CommonFunction.providerSearchVectorDataset(collection, 10000, recallParams.getAnnsField());
        log.info("提供给search使用的随机向量数: " + searchBaseVectors.size());
        List<Object> searchResult=new ArrayList<>();
        Map<String, Object> searchLevel = new HashMap<>();
        searchLevel.put("level", recallParams.getSearchLevel());
        for (int i = 0; i < recallBaseIdList.size(); i++) {
            SearchResp search = milvusClientV2.search(SearchReq.builder()
                    .collectionName(collection)
                    .topK(1)
                    .consistencyLevel(ConsistencyLevel.BOUNDED)
                    .collectionName(collection)
                    .data(Lists.newArrayList(searchBaseVectors.get(i)))
                    .searchParams(searchLevel)
                    .build());
            searchResult.add(search.getSearchResults().get(0).get(0).getId());
        }
        int matchResult=0;
        for (int i = 0; i < searchResult.size(); i++) {
               if(recallBaseIdList.get(i).toString().equals(searchResult.get(i).toString())){
                   matchResult++;
               }
        }
        log.info("BasePKId:"+recallBaseIdList);
        log.info("SearchResult:"+searchResult);
        double result = (double) matchResult / searchResult.size();
        String formattedResult = String.format("%.4f", result);
        log.info("search level ["+recallParams.getSearchLevel()+"], recall:"+formattedResult);
    }
}
