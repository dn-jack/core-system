package com.dongnao.core.eleme.intf;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.util.ElemeHelper;
import com.dongnao.core.util.HttpRequest;

/** 
 * @Description 批量查询订单详情order-mget
根据饿了么订单ID 批量查询订单详情 
 * @ClassName   OrderMget 
 * @Date        2017年2月8日 下午4:06:58 
 * @Author      luoyang 
 */
public class OrderMget extends ElemeBaseAbstractService implements ElemeService {
    
    private static String url = "orders/";
    
    public String execute(String param) throws Exception {
        
        JSONObject paramJo = JSON.parseObject(param);
        
        Map<String, String> params = new HashMap<String, String>();
        params.put("consumer_key", paramJo.getString("consumer_key"));
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        params.put("eleme_order_ids", paramJo.getString("eleme_order_ids"));
        
        String sig = ElemeHelper.genSig(ElemeHelper.COMMON_URL + url,
                params,
                paramJo.getString("consumerSecret"));
        
        String requestUrl = fixUrl(ElemeHelper.COMMON_URL + url, sig, params);
        
        String retStr = HttpRequest.sendGet(requestUrl);
        return retStr;
    }
}
