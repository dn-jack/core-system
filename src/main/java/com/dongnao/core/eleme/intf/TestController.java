package com.dongnao.core.eleme.intf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.util.ElemeUtil;
import com.dongnao.core.util.HttpRequest;
import com.dongnao.core.util.JsonUtil;
import com.dongnao.core.util.SpringContextHolder;

@Controller
@RequestMapping("/test")
public class TestController {
    
    private static transient Logger log = LoggerFactory.getLogger(TestController.class);
    
    @RequestMapping("mytest")
    public void mytest() {
        
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        String queryRe = HttpRequest.sendPost(ElemeUtil.queryOrderurl, "");
        
        log.info(queryRe);
        
        JSONObject queryReJo = JSON.parseObject(queryRe);
        
        JSONArray reJa = queryReJo.getJSONArray("result");
        
        if (JsonUtil.isNotBlank(reJa)) {
            
            JSONObject reJo = reJa.getJSONObject(0);
            
            mt.insert(fixData(reJo).toString(), "dn_order");
        }
    }
    
    private JSONObject fixData(JSONObject jo) {
        JSONObject fixJo = new JSONObject();
        
        fixJo.put("orderTime", JsonUtil.getString(jo, "activeTime"));
        fixJo.put("orderNo", JsonUtil.getString(jo, "id"));
        fixJo.put("userName", JsonUtil.getString(jo, "consigneeName"));
        //        fixJo.put("sex", JsonUtil.getString(fixJo, "consigneeName"));
        fixJo.put("phone", jo.getJSONArray("consigneePhones").get(0));
        
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
                    dishesJo.put("count",
                            JsonUtil.getString(itemJo, "quantity"));
                    dishesJo.put("price1", JsonUtil.getString(itemJo, "price"));
                    dishesJo.put("price2", JsonUtil.getString(itemJo, "total"));
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
        fixJo.put("orderPrice", JsonUtil.getString(jo, "goodsTotal"));
        return fixJo;
    }
}
