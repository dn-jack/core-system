package com.dongnao.core.bdwm.intf;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.intf.OrderHandlerIntf;
import com.dongnao.core.util.HttpUtil;
import com.dongnao.core.util.JsonUtil;
import com.dongnao.core.util.SpringContextHolder;
import com.dongnao.core.util.UrlUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

public class BaiduOrderHandler implements OrderHandlerIntf {
    
    private static transient Logger log = LoggerFactory.getLogger(BaiduOrderHandler.class);
    
    public String execute(String param) throws Exception {
        
        JSONObject tbParamJo = JSON.parseObject(param);
        String username = tbParamJo.getString("userName");
        String cookies = queryCookieFromMongo(username);
        
        if (JsonUtil.isBlank(cookies)) {
            return "";
        }
        
        Map<String, String> params = new HashMap<String, String>();
        Map<String, String> headers = new HashMap<String, String>();
        params.put("qt", "neworderlist");
        params.put("display", "json");
        headers.put("Cookie", cookies);
        log.info("-----------------baidu-cookies----------------->" + cookies);
        String orderInfo = HttpUtil.doGet(UrlUtil.BD_neworderlist, cookies, "");
        log.info("------------------orderinfo---------------------->"
                + orderInfo);
        JSONObject orderJo = JSON.parseObject(orderInfo);
        if (0 != orderJo.getInteger("errno")) {
            return "";
        }
        JSONObject dataJo = orderJo.getJSONObject("data");
        JSONArray order_list = dataJo.getJSONArray("order_list");
        if (JsonUtil.isBlank(order_list)) {
            return "";
        }
        for (Object o : order_list) {
            JSONObject order = (JSONObject)o;
            insertOrderToMongodb(order, username);
        }
        return null;
    }
    
    private void insertOrderToMongodb(JSONObject jo, String userName) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        DBCollection dbc = mt.getCollection("dn_order");
        BasicDBObject cond1 = new BasicDBObject();
        cond1.put("orderNo",
                JsonUtil.getString(jo.getJSONObject("order_basic"), "order_id"));
        DBCursor cursor = dbc.find(cond1);
        if (!cursor.hasNext()) {
            mt.insert(fixData(jo, userName).toString(), "dn_order");
        }
    }
    
    private JSONObject fixData(JSONObject jo, String userName) {
        JSONObject fixJo = new JSONObject();
        fixJo.put("merchantId", userName);
        fixOrder_basic(fixJo, jo);
        fixOrder_goods(fixJo, jo);
        fixorder_meal_fee(fixJo, jo);
        fixorder_total(fixJo, jo);
        fixJo.put("state", "0");
        
        fixJo.put("platform_type", "bdwm");
        fixJo.put("merchantActivityPart", getmerchantPart(jo));
        fixJo.put("serviceFee", getserviceFee(jo));
        return fixJo;
    }
    
    private Double getserviceFee(JSONObject jo) {
        JSONObject extract_commission = jo.getJSONObject("extract_commission");
        if (JsonUtil.isBlank(extract_commission)) {
            return 0.00;
        }
        if (JsonUtil.isBlank(extract_commission.get("commission_total"))) {
            return 0.00;
        }
        return extract_commission.getDouble("commission_total");
    }
    
    private Double getmerchantPart(JSONObject jo) {
        JSONObject order_goods = jo.getJSONObject("order_goods");
        if (JsonUtil.isBlank(order_goods)) {
            return 0.00;
        }
        JSONArray goods_list = order_goods.getJSONArray("goods_list");
        if (JsonUtil.isBlank(goods_list)) {
            return 0.00;
        }
        Double discount = 0.00;
        for (Object o : goods_list) {
            JSONObject good = (JSONObject)o;
            JSONObject extJo = good.getJSONObject("ext");
            Double eachDiscount = 0.00;
            if (extJo.containsKey("shop_total_discount")) {
                if (JsonUtil.isBlank(extJo.get("shop_total_discount"))) {
                    eachDiscount = 0.00;
                }
                else {
                    eachDiscount = extJo.getDouble("shop_total_discount");
                }
            }
            else {
                eachDiscount = 0.00;
            }
            discount += eachDiscount;
        }
        JSONObject shop_other_discount = jo.getJSONObject("shop_other_discount");
        Double price = 0.00;
        if (JsonUtil.isBlank(shop_other_discount.get("price"))) {
            price = 0.00;
        }
        else {
            price = shop_other_discount.getDouble("price");
        }
        discount += price;
        
        return discount;
    }
    
    private String dateParse(long mesc) {
        SimpleDateFormat dateformat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeInMillis(mesc);
        return dateformat.format(gc.getTime());
    }
    
    private void fixorder_total(JSONObject fixJo, JSONObject order) {
        if (!order.containsKey("order_total")) {
            return;
        }
        if (JsonUtil.isBlank(order.get("order_total"))) {
            return;
        }
        fixJo.put("orderPrice",
                JsonUtil.getString(order.getJSONObject("order_total"),
                        "customer_price"));
    }
    
    private void fixorder_meal_fee(JSONObject fixJo, JSONObject order) {
        if (!order.containsKey("order_meal_fee")) {
            return;
        }
        if (JsonUtil.isBlank(order.get("order_meal_fee"))) {
            return;
        }
        fixJo.put("boxPrice",
                JsonUtil.getString(order.getJSONObject("order_meal_fee"),
                        "price"));
    }
    
    private void fixOrder_goods(JSONObject fixJo, JSONObject order) {
        JSONObject order_goods = order.getJSONObject("order_goods");
        if (JsonUtil.isBlank(order_goods)) {
            return;
        }
        JSONArray goods_list = order_goods.getJSONArray("goods_list");
        if (JsonUtil.isBlank(goods_list)) {
            return;
        }
        JSONArray dishesJa = new JSONArray();
        for (Object o : goods_list) {
            JSONObject good = (JSONObject)o;
            JSONObject dishJo = new JSONObject();
            dishJo.put("dishName", JsonUtil.getString(good, "name"));
            //                        dishJo.put("activityName", "特价");
            dishJo.put("count", JsonUtil.getString(good, "number"));
            dishJo.put("price1", JsonUtil.getString(good, "orig_price"));
            dishJo.put("price2", JsonUtil.getString(good, "orig_unit_price"));
            dishJo.put("goods_id", "");
            dishJo.put("goods_price", JsonUtil.getString(good, "orig_price"));
            dishesJa.add(dishJo);
        }
        fixJo.put("dishes", dishesJa);
    }
    
    private void fixOrder_basic(JSONObject fixJo, JSONObject order) {
        JSONObject order_basic = order.getJSONObject("order_basic");
        if (JsonUtil.isBlank(order_basic)) {
            return;
        }
        
        fixJo.put("orderTime",
                dateParse(Long.parseLong(JsonUtil.getLong(order_basic,
                        "create_time") + "000")));
        fixJo.put("orderNo", JsonUtil.getString(order_basic, "order_id"));
        fixJo.put("userName", JsonUtil.getString(order_basic, "user_real_name"));
        fixJo.put("phone", JsonUtil.getString(order_basic, "user_phone"));
        fixJo.put("merchantName", JsonUtil.getString(order_basic, "shop_name"));
        fixJo.put("consignee_name",
                JsonUtil.getString(order_basic, "user_real_name"));
        fixJo.put("consigneeAddress",
                JsonUtil.getString(order_basic, "user_address"));
        fixJo.put("distance",
                JsonUtil.getString(order_basic, "shop_user_distance"));
    }
    
    private String queryCookieFromMongo(String username) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        DBCollection dbc = mt.getCollection("dn_bdloginInfo");
        BasicDBObject cond1 = new BasicDBObject();
        cond1.put("username", username);
        DBCursor cursor = dbc.find(cond1);
        
        String cookies = null;
        
        while (cursor.hasNext()) {
            DBObject dbo = cursor.next();
            cookies = dbo.get("loginInfo").toString();
        }
        return cookies;
    }
    
    public static void main(String[] args) {
        //        String queryStr = "?token=3sAAAcdN2YfFVxBQUEfFA5lZhg_GzMGLzAlJR9NJ0phVUdwAiAAQBs8AYANBQUFBQXceWV49bjpcAkhBaGB-AxQGMkMqVmF2fSxXUnNIVBZBQUFBQUFBQHJvHjBdWAxeIU1GAU1JFdzT&t=1490854769008&color=3c78d8";
        //        String getStr = "https://wmcrm.baidu.com/crm?qt=neworderlist&display=json";
        //        String getret = HttpRequest.sendGet(getStr);
        
        log.info(new BaiduOrderHandler().dateParse(1491709145000L));
        //        String retStr1 = HttpRequest.sendGet("https://wmpass.baidu.com/wmpass/openservice/captchapair?protocal=https&callback=jQuery1110015827547668209752_1490844419324&_=1490844419343");
        //        String data = retStr1.substring((retStr1.indexOf("(")) + 1)
        //                .replace(");", "");
        //        log.info(data);
        //        JSONObject dataJo = JSON.parseObject(data);
        //        JSONObject resultJo = dataJo.getJSONObject("data");
        //        String token = resultJo.getString("token");
        //        log.info("------------token------------" + token);
        //        
        //        String queryStr = "redirect_url=https%253A%252F%252Fwmcrm.baidu.com%252F&return_url=https%253A%252F%252Fwmcrm.baidu.com%252Fcrm%252Fsetwmstoken&type=1&channel=pc&account=cs13337385217&upass=Sdh888888&captcha=yzmv&token=BxVHU5laFUlaBIZZkFBQUFKX2JcFxMsFjxiXzQNEW1xMVjf931DQAXCQUAAfTCVBQUFBQUFBOxoLI24wDAFJcUsoUyg-ZlNVIzY0VkJEQ0FBQUFBM3UUUUE5DVtDMEpzO0s8LBZ5N4AA";
        //        JSONObject paramJo = new JSONObject();
        //        paramJo.put("redirect_url", "https%3A%2F%2Fwmcrm.baidu.com%2F");
        //        paramJo.put("return_url",
        //                "https%3A%2F%2Fwmcrm.baidu.com%2Fcrm%2Fsetwmstoken");
        //        paramJo.put("type", 1);
        //        paramJo.put("channel", "pc");
        //        paramJo.put("account", 1);
        //        paramJo.put("upass", "gM");
        //        paramJo.put("captcha", "yzmv");
        //        paramJo.put("token",
        //                "BxVHU5laFUlaBIZZkFBQUFKX2JcFxMsFjxiXzQNEW1xMVjf931DQAXCQUAAfTCVBQUFBQUFBOxoLI24wDAFJcUsoUyg-ZlNVIzY0VkJEQ0FBQUFBM3UUUUE5DVtDMEpzO0s8LBZ5N4AA");
        //        String retStr = HttpRequest.sendPost("https://wmpass.baidu.com/api/login",
        //                queryStr);
        //        
        //        log.info(retStr);
        //        int i = 0;
        //        System.out.print(i == 0);
    }
}
