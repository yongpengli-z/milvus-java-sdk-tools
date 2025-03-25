package custom.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

public class JsonObjectUtil {

    public static JsonObject jsonMerge(JsonObject jsonObject1,JsonObject jsonObject2){
        JsonObject merged = new JsonObject();

        // 将第一个 JsonObject 的所有属性添加到合并对象中
        jsonObject1.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));

        // 将第二个 JsonObject 的所有属性添加到合并对象中
        jsonObject2.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));

        return merged;
    }
}
