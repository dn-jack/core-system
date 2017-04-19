package com.dongnao.core.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class TestMain {
    
    private String loginurl = "https://app-api.shop.ele.me/arena/invoke/?method=LoginService.loginByUsername";
    
    private String countOrderurl = "https://app-api.shop.ele.me/nevermore/invoke/?method=OrderService.countOrder";
    
    private String queryOrderurl = "https://app-api.shop.ele.me/nevermore/invoke/?method=OrderService.queryOrder";
    
    private String charset = "utf-8";
    
    private HttpClientUtil httpClientUtil = null;
    
    public TestMain() {
        httpClientUtil = new HttpClientUtil();
    }
    
    private JSONObject queryOrderJo() {
        JSONObject paramJo = new JSONObject();
        paramJo.put("id", "6a69984a-e07e-4095-9379-5a7af0905a45");
        paramJo.put("method", "queryOrder");
        paramJo.put("service", "OrderService");
        JSONObject params = new JSONObject();
        params.put("shopId", "848415");
        params.put("orderFilter", "UNPROCESSED_ORDERS");
        JSONObject conditionJo = new JSONObject();
        conditionJo.put("page", 1);
        conditionJo.put("orderIds", new JSONArray());
        params.put("condition", conditionJo);
        paramJo.put("params", params);
        JSONObject metas = new JSONObject();
        metas.put("appName", "melody");
        metas.put("appVersion", "4.4.2");
        metas.put("ksid", "MWE0N2Y5MWYtZjIyZi00ZjcxLThjMTNTQ1M2");
        paramJo.put("metas", metas);
        paramJo.put("ncp", "2.0.0");
        return paramJo;
    }
    
    private JSONObject countOrderJo() {
        JSONObject paramJo = new JSONObject();
        paramJo.put("id", "6a69984a-e07e-4095-9379-5a7af0905a45");
        paramJo.put("method", "countOrder");
        paramJo.put("service", "OrderService");
        JSONObject params = new JSONObject();
        params.put("shopId", "848415");
        params.put("orderFilter", "UNPROCESSED_ORDERS");
        JSONObject conditionJo = new JSONObject();
        conditionJo.put("page", 1);
        conditionJo.put("orderIds", new JSONArray());
        params.put("condition", conditionJo);
        paramJo.put("params", params);
        JSONObject metas = new JSONObject();
        metas.put("appName", "melody");
        metas.put("appVersion", "4.4.2");
        metas.put("ksid", "MWE0N2Y5MWYtZjIyZi00ZjcxLThjMTNTQ1M2");
        paramJo.put("metas", metas);
        paramJo.put("ncp", "2.0.0");
        return paramJo;
    }
    
    private JSONObject loginJo() {
        JSONObject paramJo = new JSONObject();
        paramJo.put("id", "3c9691a3-39cd-4324-843b-7d6d54c270a9");
        paramJo.put("method", "loginByUsername");
        paramJo.put("service", "LoginService");
        JSONObject params = new JSONObject();
        params.put("username", "yuanchuan123");
        params.put("password", "ycjfx0826");
        params.put("captchaCode", "");
        params.put("loginedSessionIds", new JSONArray());
        paramJo.put("params", params);
        JSONObject metas = new JSONObject();
        metas.put("appName", "melody");
        metas.put("appVersion", "4.4.0");
        paramJo.put("metas", metas);
        paramJo.put("ncp", "2.0.0");
        return paramJo;
    }
    
    public void test() {
        //        Map<String, String> createMap = new HashMap<String, String>();
        //        createMap.put("authuser", "*****");
        //        createMap.put("authpass", "*****");
        //        createMap.put("orgkey", "****");
        //        createMap.put("orgname", "****");
        //        String httpOrgCreateTestRtn = httpClientUtil.doPost(url,
        //                createMap,
        //                charset);
        
        /*{"id":"3c9691a3-39cd-4324-843b-7d6d54c270a9",
         * "method":"loginByUsername",
         * "service":"LoginService",
         * "params":{"username":"dkdkkd","password":"dddd","captchaCode":"","loginedSessionIds":[]},
         * "metas":{"appName":"melody","appVersion":"4.4.0"},"ncp":"2.0.0"}*/
        
        String result = null;
        try {
            result = HttpsRequestUtil.doPost(queryOrderurl,
                    queryOrderJo().toString(),
                    "UTF-8",
                    300000,
                    300000);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println("result:" + result);
    }
    
    public static void main(String[] args) {
        TestMain main = new TestMain();
        main.test();
    }
    
}
