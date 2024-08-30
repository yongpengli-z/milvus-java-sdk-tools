package custom.common;

import com.alibaba.fastjson.JSONObject;
import custom.components.*;
import custom.entity.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author yongpeng.li @Date 2024/6/5 17:26
 */
@Slf4j
public class ComponentSchedule {
    public static void runningSchedule(String customizeParams) {

        // 获取params的所有根节点
        JSONObject parseJO = JSONObject.parseObject(customizeParams);
        Set<String> keyStrings = parseJO.keySet();
        List<String> keyList = new ArrayList();
        for (String keyString : keyStrings) {
            keyList.add(keyString);
        }
        // 按照编号对key进行排序
        keyList = keyList.stream().sorted((s1, s2) -> {
            int num1 = Integer.parseInt(s1.split("_")[1]);
            int num2 = Integer.parseInt(s2.split("_")[1]);
            return Integer.compare(num1, num2);
        }).collect(Collectors.toList());

        System.out.println(keyList);

        List<Object> operators = new ArrayList();
        for (String keyString : keyList) {
            String item = parseJO.getString(keyString);
            String paramName = keyString.substring(0, keyString.indexOf("_"));
            try {
                Object o = JSONObject.parseObject(item, Class.forName("custom.entity." + paramName));
                operators.add(o);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < operators.size(); i++) {
            log.warn("Step--[ " + operators.size() + " , " + (i + 1) + " ]:");

            if (operators.get(i) instanceof CreateCollectionParams) {
                log.info("*********** < create collection > ***********");
                CreateCollectionComp.createCollection((CreateCollectionParams) operators.get(i));
            }
            if (operators.get(i) instanceof CreateIndexParams) {
                log.info("*********** < create index > ***********");
                CreateIndexComp.CreateIndex((CreateIndexParams) operators.get(i));
            }
            if (operators.get(i) instanceof LoadParams) {
                log.info("*********** < load collection > ***********");
                LoadCollectionComp.loadCollection((LoadParams) operators.get(i));
            }
            if (operators.get(i) instanceof InsertParams) {
                log.info("*********** < insert data > ***********");
                InsertComp.insertCollection((InsertParams) operators.get(i));
            }
            if (operators.get(i) instanceof SearchParams) {
                log.info("*********** < search collection > ***********");
                SearchComp.searchCollection((SearchParams) operators.get(i));
            }
        }


    }
}
