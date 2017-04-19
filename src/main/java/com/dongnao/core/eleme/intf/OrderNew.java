package com.dongnao.core.eleme.intf;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.util.ElemeHelper;
import com.dongnao.core.util.HttpRequest;

public class OrderNew extends ElemeBaseAbstractService implements ElemeService {
    
    private static String url = "order/new/";
    
    public String execute(String param) throws Exception {
        JSONObject paramJo = JSON.parseObject(param);
        
        Map<String, String> params = new HashMap<String, String>();
        params.put("consumer_key", paramJo.getString("consumer_key"));
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("restaurant_id", paramJo.getString("restaurant_id"));
        
        String sig = ElemeHelper.genSig(ElemeHelper.COMMON_URL + url,
                params,
                paramJo.getString("consumerSecret"));
        
        String requestUrl = fixUrl(ElemeHelper.COMMON_URL + url, sig, params);
        
        String retStr = HttpRequest.sendGet(requestUrl);
        return retStr;
    }
}
