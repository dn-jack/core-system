package com.dongnao.core.checkdata;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.util.HttpRequest;
import com.dongnao.core.util.HttpUtil;
import com.dongnao.core.util.JsonUtil;
import com.dongnao.core.util.SpringContextHolder;
import com.dongnao.core.util.UrlUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.taobao.pamirs.schedule.IScheduleTaskDealMulti;
import com.taobao.pamirs.schedule.TaskItemDefine;

public class BdwmCheckData implements IScheduleTaskDealMulti<JSONObject> {
    
    private static transient Logger log = LoggerFactory.getLogger(BdwmCheckData.class);
    
    public Comparator<JSONObject> getComparator() {
        return null;
    }
    
    public List<JSONObject> selectTasks(String arg0, String arg1, int arg2,
            List<TaskItemDefine> arg3, int arg4) throws Exception {
        log.info("--------------------------【百度外卖数据比对开始BdwmCheckData】---------------------------"
                + arg0);
        
        // TODO Auto-generated method stub
        //获取所有百度外卖店铺的账号和密码和shopid
        String retStr = HttpRequest.sendGet(UrlUtil.queryShop
                + "?platformType=bdwm");
        log.info("--------------------------所有百度外卖店铺信息-----" + retStr);
        JSONObject bdwmShops = JSON.parseObject(retStr);
        if (!"0000".equals(bdwmShops.getString("respCode"))) {
            return null;
        }
        
        //从餐予者数据中获取所有百度外卖店铺订单数据
        JSONObject queryJo = new JSONObject();
        queryJo.put("orderTime", JsonUtil.isNotBlank(arg0) ? arg0 : getPreDay());
        queryJo.put("platformType", "bdwm");
        String queryStr = HttpRequest.sendPostJson(UrlUtil.queryOrderUrl,
                queryJo.toString());
        log.info("当天【百度外卖】店铺的所有在餐予者平台入库的订单数据-----------" + queryStr);
        JSONObject queryRetJo = JSON.parseObject(queryStr);
        if (!"0000".equals(queryRetJo.getString("respCode"))) {
            log.info(queryRetJo.getString("respDesc"));
            return null;
        }
        //所有百度外卖店铺数据
        Object orderJa = queryRetJo.get("result");
        
        JSONArray resultJa = bdwmShops.getJSONArray("result");
        //所有店铺的丢失的数据
        JSONArray loseDatas = new JSONArray();
        
        for (Object o : resultJa) {
            JSONObject shop = (JSONObject)o;
            //从百度外卖平台查询出该店铺今天的所有订单
            JSONArray queryJa = queryOrderFromBdwm(shop);
            //从db中获取该店铺今天的所有订单
            JSONArray dbJa = queryOrderFromdb(shop.getString("username"),
                    orderJa);
            //百度外卖平台数据和餐予者库中数据做比对，如果餐予者库中不存在的数据则把订单数据补入餐予者库中
            JSONArray loseData = checkData(queryJa, dbJa);
            addToTotal(loseDatas, loseData);
        }
        if (loseDatas.size() > 0) {
            insertToDb(loseDatas);
        }
        log.info("--------------------------【百度外卖数据比对结束BdwmCheckData】---------------------------");
        return null;
    }
    
    private void test(String str) {
        JSONObject jo = JSON.parseObject(str);
        
        if (0 != jo.getInteger("errno")) {
            return;
        }
        
        JSONObject dataJo = jo.getJSONObject("data");
        JSONArray order_list = dataJo.getJSONArray("order_list");
        
        if (JsonUtil.isBlank(order_list)) {
            return;
        }
        JSONArray retJa = new JSONArray();
        
        for (Object o : order_list) {
            JSONObject order = (JSONObject)o;
            retJa.add(fixData(order, "cs15274926695"));
        }
        insertToDb(retJa);
    }
    
    private void insertToDb(JSONArray loseDatas) {
        String queryStr = HttpRequest.sendPostJson(UrlUtil.orderInsertBatchDb,
                loseDatas.toString());
        log.info("数据插入后结果-------------------->" + queryStr);
    }
    
    private void addToTotal(JSONArray loseDatas, JSONArray loseData) {
        if (loseData.size() <= 0) {
            return;
        }
        
        for (Object o : loseData) {
            loseDatas.add(o);
        }
    }
    
    /** 
     * @Description 数据比对 
     * @param @param queryJa
     * @param @param mongodbJa
     * @param @return 参数 
     * @return JSONArray 返回类型  
     * @throws 
     */
    private JSONArray checkData(JSONArray queryJa, JSONArray dbJa) {
        JSONArray retJa = new JSONArray();
        
        if (queryJa.size() <= 0) {
            return retJa;
        }
        
        for (Object o : queryJa) {
            JSONObject jo = (JSONObject)o;
            String orderNo = jo.getString("orderNo");
            if (!isExist(orderNo, dbJa)) {
                retJa.add(jo);
            }
        }
        
        return retJa;
    }
    
    private boolean isExist(String orderNo, JSONArray dbJa) {
        
        if (dbJa.size() <= 0) {
            return false;
        }
        
        for (Object o : dbJa) {
            JSONObject jo = (JSONObject)o;
            if (jo.containsValue(orderNo)) {
                return true;
            }
        }
        
        return false;
    }
    
    private JSONArray queryOrderFromdb(String shopId, Object orderJa) {
        JSONArray retJa = new JSONArray();
        if (JsonUtil.isBlank(orderJa)) {
            return retJa;
        }
        List<Map> orders = (List<Map>)orderJa;
        
        for (Map map : orders) {
            if (shopId.equals(map.get("STORE_ID".toLowerCase()).toString())) {
                retJa.add(JSONObject.parseObject(JSON.toJSONString(map)));
            }
        }
        
        return retJa;
    }
    
    /** 
     * @Description 定时器凌晨3点跑数据，需要查询店铺前一天的所有数据 
     * @param @return 参数 
     * @return String 返回类型  
     * @throws 
     */
    private String getPreDay() {
        SimpleDateFormat dft = new SimpleDateFormat("yyyy-MM-dd");
        Date beginDate = new Date();
        Calendar date = Calendar.getInstance();
        date.setTime(beginDate);
        date.set(Calendar.DATE, date.get(Calendar.DATE) - 1);
        return dft.format(date.getTime());
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
    
    private boolean isJson(String param) {
        try {
            JSON.parseObject(param);
        }
        catch (Exception e) {
            return false;
        }
        return true;
    }
    
    private JSONArray queryOrderFromBdwm(JSONObject shop) {
        String username = shop.getString("username");
        String password = shop.getString("password");
        
        JSONArray retJa = new JSONArray();
        
        if (JsonUtil.isBlank(username) || JsonUtil.isBlank(password)) {
            return retJa;
        }
        
        String cookies = queryCookieFromMongo(username);
        log.info("-----------------baidu-cookies----------------->" + cookies);
        //如果cookies为null需要重新登录
        if (cookies == null) {
            return retJa;
        }
        
        String orderInfo = HttpUtil.doGet("https://wmcrm.baidu.com/crm?qt=orderlist&qt=orderlist&display=json",
                cookies,
                "");
        
        log.info("------------------orderinfo---------------------->"
                + orderInfo);
        
        if (!isJson(orderInfo)) {
            log.info("------------------orderinfo---------------------->接口返回的订单数据不是JSON格式！");
            return retJa;
        }
        
        JSONObject orderJo = JSON.parseObject(orderInfo);
        if (0 != orderJo.getInteger("errno")) {
            return retJa;
        }
        
        JSONObject dataJo = orderJo.getJSONObject("data");
        JSONArray order_list = dataJo.getJSONArray("order_list");
        
        if (JsonUtil.isBlank(order_list)) {
            return retJa;
        }
        
        for (Object o : order_list) {
            JSONObject order = (JSONObject)o;
            
            /**
             * "status : 1" -- 未接单
                "status : 10"-- 无效订单
                "status : 5"-- 已确认
                47-57 大概10分钟订单失效
             */
            //            String status = JsonUtil.getString(order.getJSONObject("order_basic"),
            //                    "status");
            //            if ("10".equals(status)) {
            //                continue;
            //            }
            retJa.add(fixData(order, username));
        }
        
        return retJa;
    }
    
    private JSONObject fixData(JSONObject jo, String userName) {
        JSONObject fixJo = new JSONObject();
        fixJo.put("merchantId", userName);
        fixOrder_basic(fixJo, jo);
        fixOrder_goods(fixJo, jo);
        fixorder_meal_fee(fixJo, jo);
        //        fixorder_total(fixJo, jo);
        fixJo.put("state", "0");
        
        String status = JsonUtil.getString(jo.getJSONObject("order_basic"),
                "status");
        if ("10".equals(status)) {
            fixJo.put("is_invalid", "1");
        }
        else {
            fixJo.put("is_invalid", "0");
        }
        
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
        double origCount = 0.0;
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
            origCount += good.getDouble("orig_price");
            dishesJa.add(dishJo);
        }
        fixJo.put("orderPrice", origCount);
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
        fixJo.put("orderType", JsonUtil.getString(order_basic, "send_time")
                .indexOf("立即送餐") > -1 ? "NORMAL" : "BOOKING");
    }
    
    public boolean execute(JSONObject[] arg0, String arg1) throws Exception {
        return false;
    }
    
    public static void main(String[] args) {
        String orderInfo = HttpUtil.doGet("https://wmcrm.baidu.com/crm?qt=orderlist&qt=orderlist&display=json",
                "WMPTOKEN=APcBAABId2llelF8YGxtLEwdchYWPDUXE_bOnBtYVlg8cXQAAQlCeA8kbiR0vB0A; expires=Wed, 07-Jun-2017 08:07:16 GMT; Max-Age=2592000; path=/; domain=wmpass.baidu.com; HttpOnly;WMUSS=AADkCAAB6L1xHTE5cYQoRdxsZOhMoXQJLUhkpJXh-YT1Ob35-aS5aMHkoDVoyHVJMcfAAANgxfXWlgKTx5UWByT0IwHjEuHBpcdUpVHlYwUHMCHCoNewh2XDAyVgISSFFUd24FJgofAn9pVh5A%7EbOnBi7%7EJhBZIsoP06oYEJCIhw%7EFoaMOq9QDANb2ixVVBE; expires=Wed, 07-Jun-2017 08:07:16 GMT; Max-Age=2592000; path=/; domain=.baidu.com; HttpOnly;newyear=open;new_remind_time=1491707183;new_order_time=1491707474;WMSTOKEN=sJAAA3Fk5-ZyJ2ViYcYEUdXy5zfG15aiEBc34VeGIfWXAAPY75B3s4QAAdaEA4pD3RKBkpwISs1Pn0dDwbWQscQBGEAAGTf6xEFDAAA3OUSHIUPwRiJSiUYzrcGBQAAG",
                "");
        log.info(orderInfo);
        //        new BdwmCheckData().test(orderInfo);
    }
    
}
