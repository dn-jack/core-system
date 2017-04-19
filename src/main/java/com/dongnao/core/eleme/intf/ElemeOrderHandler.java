package com.dongnao.core.eleme.intf;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.intf.OrderHandlerIntf;
import com.dongnao.core.util.ElemeUtil;
import com.dongnao.core.util.HttpsRequestUtil;
import com.dongnao.core.util.JsonUtil;
import com.dongnao.core.util.SpringContextHolder;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;

@Service
public class ElemeOrderHandler implements OrderHandlerIntf {
    
    private static transient Logger log = LoggerFactory.getLogger(ElemeOrderHandler.class);
    
    private Map<String, String> cache = new HashMap<String, String>();
    
    public String execute(String param) throws Exception {
        JSONObject tbParamJo = JSON.parseObject(param);
        JSONObject loginJo = ElemeUtil.loginJo();
        JSONObject paramsJo = loginJo.getJSONObject("params");
        paramsJo.put("username", tbParamJo.getString("userName"));
        paramsJo.put("password", tbParamJo.getString("password"));
        
        String ksid = null;
        
        if (!cache.containsKey(tbParamJo.getString("shopId"))) {
            String loginRe = HttpsRequestUtil.doPost(ElemeUtil.loginurl,
                    loginJo.toString(),
                    "UTF-8",
                    300000,
                    300000);
            
            log.info("---------------loginurl----------------" + loginRe);
            
            JSONObject loginRejo = JSON.parseObject(loginRe);
            if (JsonUtil.isNotBlank(loginRejo.get("result"))
                    && loginRejo.getJSONObject("result").getBoolean("succeed")) {
                ksid = loginRejo.getJSONObject("result")
                        .getJSONObject("successData")
                        .getString("ksid");
                cache.put(tbParamJo.getString("shopId"), ksid);
                
                MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                        .getBean("mongoTemplate");
                
                JSONObject relJo = new JSONObject();
                relJo.put("shopId", tbParamJo.getString("shopId"));
                relJo.put("ksid", ksid);
                
                BasicDBObject shopId = new BasicDBObject();
                shopId.put("shopId", tbParamJo.getString("shopId"));
                DBCollection dbc = mt.getCollection("dn_ksid");
                dbc.remove(shopId);
                mt.remove(shopId, "dn_ksid");
                mt.insert(relJo.toString(), "dn_ksid");
                
            }
        }
        
        JSONObject paramJo = ElemeUtil.pollForHighJo();
        paramJo.put("shopId", tbParamJo.getString("shopId"));
        paramJo.getJSONObject("params").put("shopId",
                tbParamJo.getString("shopId"));
        JSONObject metas = paramJo.getJSONObject("metas");
        metas.put("ksid", cache.get(tbParamJo.getString("shopId")));
        
        String result = HttpsRequestUtil.doPost(ElemeUtil.pollForHighUrl,
                paramJo.toString(),
                "UTF-8",
                300000,
                300000);
        
        log.info("---------------pollForHighUrl----------------" + result);
        
        if (result != null && !"".equals(result)) {
            JSONObject resultJo = JSON.parseObject(result);
            JSONObject reJo = resultJo.getJSONObject("result");
            
            JSONArray newOrderIdsJa = reJo.getJSONArray("newOrderIds");
            
            if (JsonUtil.isNotBlank(newOrderIdsJa)) {
                JSONObject queryJo = ElemeUtil.queryOrderJo();
                queryJo.getJSONObject("params").put("shopId",
                        tbParamJo.getString("shopId"));
                queryJo.getJSONObject("metas").put("ksid",
                        cache.get(tbParamJo.getString("shopId")));
                String queryRe = HttpsRequestUtil.doPost(ElemeUtil.queryOrderurl,
                        queryJo.toString(),
                        "UTF-8",
                        300000,
                        300000);
                //            String queryRe = HttpRequest.sendPost(ElemeUtil.queryOrderurl, "");
                
                log.info("---------------queryOrderurl----------------"
                        + queryRe);
                
                JSONObject queryReJo = JSON.parseObject(queryRe);
                JSONArray qureJa = queryReJo.getJSONArray("result");
                
                if (JsonUtil.isNotBlank(qureJa)) {
                    //把订单数据插入到mongodb
                    insertToMongo(queryReJo);
                }
            }
        }
        
        return null;
    }
    
    private void insertToMongo(JSONObject queryReJo) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        JSONArray reJa = queryReJo.getJSONArray("result");
        
        //        JSONObject reJo = reJa.getJSONObject(0);
        
        for (Object o : reJa) {
            
            JSONObject eachJo = (JSONObject)o;
            
            DBCollection dbc = mt.getCollection("dn_order");
            BasicDBObject cond1 = new BasicDBObject();
            cond1.put("orderNo", JsonUtil.getString(eachJo, "id"));
            DBCursor cursor = dbc.find(cond1);
            if (!cursor.hasNext()) {
                mt.insert(fixData(eachJo).toString(), "dn_order");
            }
        }
    }
    
    private JSONObject fixData(JSONObject jo) {
        JSONObject fixJo = new JSONObject();
        
        fixJo.put("orderTime", JsonUtil.getString(jo, "activeTime"));
        fixJo.put("orderNo", JsonUtil.getString(jo, "id"));
        fixJo.put("userName", JsonUtil.getString(jo, "consigneeName"));
        //        fixJo.put("sex", JsonUtil.getString(fixJo, "consigneeName"));
        fixJo.put("phone", jo.getJSONArray("consigneePhones").get(0));
        fixJo.put("merchantId", JsonUtil.getString(jo, "shopId"));
        
        JSONArray groupsJa = jo.getJSONArray("groups");
        JSONArray dishesJa = new JSONArray();
        for (Object o : groupsJa) {
            JSONObject groupsJo = (JSONObject)o;
            if ("NORMAL".equals(groupsJo.getString("type"))) {
                JSONArray itemsJa = groupsJo.getJSONArray("items");
                
                for (Object itemO : itemsJa) {
                    JSONObject itemJo = (JSONObject)itemO;
                    JSONObject dishesJo = new JSONObject();
                    dishesJo.put("dishName", JsonUtil.getString(itemJo, "name"));
                    dishesJo.put("activityName", "特价");
                    dishesJo.put("count",
                            JsonUtil.getString(itemJo, "quantity"));
                    dishesJo.put("price1", JsonUtil.getString(itemJo, "price"));
                    dishesJo.put("price2", JsonUtil.getString(itemJo, "total"));
                    dishesJo.put("goods_id", JsonUtil.getString(itemJo, "id"));
                    dishesJo.put("goods_price",
                            JsonUtil.getString(itemJo, "price"));
                    dishesJa.add(dishesJo);
                }
            }
            else if ("EXTRA".equals(groupsJo.getString("type"))) {
                JSONArray itemsJa = groupsJo.getJSONArray("items");
                
                for (Object itemO : itemsJa) {
                    JSONObject itemJo = (JSONObject)itemO;
                    if ("102".equals(itemJo.getString("categoryId"))
                            || "餐盒".equals(itemJo.getString("name"))) {
                        fixJo.put("boxPrice",
                                JsonUtil.getString(itemJo, "price"));
                    }
                }
            }
        }
        
        fixJo.put("dishes", dishesJa);
        fixJo.put("distributionPrice", "");
        fixJo.put("discount", "");
        fixJo.put("hongbao", JsonUtil.getString(jo, "hongbao"));
        fixJo.put("orderPrice",
                JsonUtil.getString(jo, "goodsTotalWithoutPackage"));
        fixJo.put("state", "0");
        fixJo.put("merchantName", JsonUtil.getString(jo, "shopName"));
        fixJo.put("orderType", JsonUtil.getString(jo, "orderType"));
        fixJo.put("merchantActivityPart",
                JsonUtil.getString(jo, "merchantActivityPart"));
        fixJo.put("elemeActivityPart",
                JsonUtil.getString(jo, "elemeActivityPart"));
        fixJo.put("serviceFee", JsonUtil.getString(jo, "serviceFee"));
        fixJo.put("serviceRate", JsonUtil.getString(jo, "serviceRate"));
        
        fixJo.put("platform_dist_charge",
                JsonUtil.getString(jo, "deliveryFeeTotal"));
        fixJo.put("settlement_amount", JsonUtil.getString(jo, "income"));
        fixJo.put("distribution_mode",
                JsonUtil.getString(jo, "deliveryServiceType"));
        fixJo.put("remark", JsonUtil.getString(jo, "remark"));
        fixJo.put("platform_type", "elm");
        fixJo.put("booked_time", JsonUtil.getString(jo, "bookedTime"));
        fixJo.put("consignee_name", JsonUtil.getString(jo, "consigneeName"));
        fixJo.put("active_time", JsonUtil.getString(jo, "activeTime"));
        fixJo.put("active_total", JsonUtil.getString(jo, "activityTotal"));
        fixJo.put("orderLatestStatus",
                JsonUtil.getString(jo, "orderLatestStatus"));
        fixJo.put("consigneeAddress",
                JsonUtil.getString(jo, "consigneeAddress"));
        fixJo.put("distance", JsonUtil.getString(jo, "distance"));
        return fixJo;
    }
}
