package custom.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static custom.BaseTest.cloudServiceUserInfo;
import static custom.BaseTest.envConfig;

@Slf4j
public class CloudOpsServiceUtils {

    public static String listDBVersionByKeywords(String keywords) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/release_version";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", cloudServiceUserInfo.getToken());
        Map<String, String> paramsDB = new HashMap<>();
        paramsDB.put("currentPage", "1");
        paramsDB.put("pageSize", "100");
        paramsDB.put("dbVersion", keywords);
        paramsDB.put("regionId", envConfig.getRegionId());
        String s = HttpClientUtils.doGet(url, header, paramsDB);
        log.info("listDBVersionByKeywords:" + s);
        return s;

    }

    public static String listTagByKeywords(String keywords) {
        String url = envConfig.getCloudOpsServiceHost() + "/api/v1/release_version";
        Map<String, String> header = new HashMap<>();
        header.put("sa_token", cloudServiceUserInfo.getToken());
        Map<String, String> paramsTag = new HashMap<>();
        paramsTag.put("currentPage", "1");
        paramsTag.put("pageSize", "100");
        paramsTag.put("tag", keywords);
        paramsTag.put("regionId", envConfig.getRegionId());
        String s = HttpClientUtils.doGet(url, header, paramsTag);
        log.info("listTagByKeywords:" + s);
        return s;
    }


    public static String getLatestImageByKeywords(String keywords) {
        List<String> collect;
        JSONObject jsonResponse = JSON.parseObject(listDBVersionByKeywords(keywords));
        JSONObject jsonResponse2 = JSON.parseObject(listTagByKeywords(keywords));
        List<String> lists = new ArrayList<>();
        // 获取data-list
        JSONArray jsonArray = jsonResponse.getJSONObject("data").getJSONArray("list");
        for (int i = 0; i < jsonArray.size(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);
            String dbVersion = jsonObject.getString("dbVersion");
            String tag = jsonObject.getString("tag");
            lists.add(dbVersion + "(" + tag + ")");
        }
        // 获取按照tag筛选的
        JSONArray jsonArray2 = jsonResponse2.getJSONObject("data").getJSONArray("list");
        for (int i = 0; i < jsonArray2.size(); i++) {
            JSONObject jsonObject = jsonArray2.getJSONObject(i);
            String dbVersion = jsonObject.getString("dbVersion");
            String tag = jsonObject.getString("tag");
            lists.add(dbVersion + "(" + tag + ")");
        }

        // 剔除重复
        collect = lists.stream().distinct().collect(Collectors.toList());
        return collect.stream().findFirst().orElse("");
    }



}
