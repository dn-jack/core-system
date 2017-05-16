package com.dongnao.core.checkdata;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
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
    
    private static Map<String, String> userToCookie = new HashMap<String, String>();
    
    static {
        userToCookie.put("CS18900750234",
                "WMPTOKEN=cAAAA4CUIxaCQuNGYsFmdpIj11OhxFV3aXxGKbwAARxxTAsHChUG6LgMBl_AoAAG; expires=Sat, 10-Jun-2017 09:05:54 GMT; Max-Age=2592000; path=/; domain=wmpass.baidu.com; HttpOnly;WMUSS=QAAL0AAABrOFFTIXIRFy0tXANJTU9UVFhoWU5-eHoeWlklYGVgTB0xID4jMFVQXTFSBFadwAAJ30helF5YipHYSNCfn0bJF5%7EBCocNElaV3kvJ1dfeH1dGncuE1pYWjt7HDp5Nl1XNWECURVLK2tEaANqCy54kwyFyWcN8BAhDoJVJQ0NMWEQzCIEAN1w-EB; expires=Sat, 10-Jun-2017 09:05:54 GMT; Max-Age=2592000; path=/; domain=.baidu.com; HttpOnly;newyear=open;new_remind_time=1491707183;new_order_time=1491707474;WMSTOKEN=sgAAASJRktXxNiPWkXHmtnMzYTH3ltMRYoPC4_YD4FGCQAAgiKU07VSxNOGk3TggiNmjWOApdJpsMGlgAAGQ5ThQFDAAA66d4GNoucBjwiwkZWqAAAH0t3yAa15AsAAN");
        userToCookie.put("15201590151",
                "WMPTOKEN=AAGkDAAB9eBZwcjI0KQFJahp9NUk2OE8xHQKtLhDnZQAARdQFlQaKTV1DYEMACAk; expires=Fri, 09-Jun-2017 07:41:27 GMT; Max-Age=2592000; path=/; domain=wmpass.baidu.com; HttpOnly;WMUSS=JICAAA3T2khaGdWMR4Pf08rFx9BazlqIC5pQzZkXlNRbCJxI3BbRFlDGwthKQowgAAIIkvRZveQAAVCFPFQwIfyw2aG5rTU0-HzpfEFxQayEqLjs0D2M-PzheDk1qFUwWSzQFNgJRcyALUzBnJBsJTUlDAlgnP40FKwFbkS8NOM8CDAOrShF%7EHy0NmAoAwAA; expires=Fri, 09-Jun-2017 07:41:27 GMT; Max-Age=2592000; path=/; domain=.baidu.com; HttpOnly;newyear=open;new_remind_time=1491707183;new_order_time=1491707474;WMSTOKEN=MAAAtci5NfQJ8ZSUYbXJhMXM5VzNQNlB0YFcuV3QzAxwAAZsMAkafAcceWtAEVkzcY3zsAACrS4QR1YAAGQMHRMxDQAA_tjeF9I8hhpy1EQddKQAAKI85B1fztBMAAO8");
    }
    
    private static String cookies = "WMPTOKEN=oAAH8BAABGSiohcxwLcUZpD3EoNiIbHA50CAA2NzMRhLDGFRaKkHCdWPAgz5VQnB; expires=Sat, 10-Jun-2017 06:43:29 GMT; Max-Age=2592000; path=/; domain=wmpass.baidu.com; HttpOnly;WMUSS=MAAFMBAAAyC0gjM31ETRlRQSM0NFRLf18sUBA2dQYLRBxSLz0MYEhTBHpRR0hpGQoNFVh8xR5lAAATD5gGD1ARA5PegNUBUdwV3YuYld1NlpwByI5cDpCfAd%7EXR49HmJKbFxNQWEVMTgLXxtiVFA8RVdeaHzWB67h-guFyWcN8Je0EYKvhw-NmsgPbDgDArB; expires=Sat, 10-Jun-2017 06:43:29 GMT; Max-Age=2592000; path=/; domain=.baidu.com; HttpOnly;newyear=open;new_remind_time=1491707183;new_order_time=1491707474;WMSTOKEN=BcAACcMAAAsehRtWygfEzN9LRVRfVEWVC8UHEdTX2QSAiENQDQ6wAAHWHQSP50gAA5TbDhmWRQPdydBdFNWRmgDagtVgP0OEVIAAGQ5ThT5DQAAH6cXGTH_PBp4C3UZO";
    
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
            String username = shop.getString("username");
            String password = shop.getString("password");
            JSONArray queryJa = null;
            try {
                //从百度外卖平台查询出该店铺今天的所有订单
                queryJa = queryOrderFromBdwm(shop, arg0);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.info(e.getMessage());
                dn_errorOrder(username, password, "bdwm", null, e.getMessage());
                continue;
            }
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
    
    //    private void test(String str) {
    //        JSONObject jo = JSON.parseObject(str);
    //        
    //        if (0 != jo.getInteger("errno")) {
    //            return;
    //        }
    //        
    //        JSONObject dataJo = jo.getJSONObject("data");
    //        JSONArray order_list = dataJo.getJSONArray("order_list");
    //        
    //        if (JsonUtil.isBlank(order_list)) {
    //            return;
    //        }
    //        JSONArray retJa = new JSONArray();
    //        
    //        for (Object o : order_list) {
    //            JSONObject order = (JSONObject)o;
    //            retJa.add(fixData(order, "cs15274926695"));
    //        }
    //        insertToDb(retJa);
    //    }
    
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
    
    private String queryCookieFromMongo(String username) {
        String cookies = null;
        try {
            MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                    .getBean("mongoTemplate");
            
            DBCollection dbc = mt.getCollection("dn_bdloginInfo");
            BasicDBObject cond1 = new BasicDBObject();
            cond1.put("username", username);
            DBCursor cursor = dbc.find(cond1);
            
            while (cursor.hasNext()) {
                DBObject dbo = cursor.next();
                cookies = dbo.get("loginInfo").toString();
            }
        }
        catch (Exception e) {
            return null;
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
    
    private JSONArray queryOrderFromBdwm(JSONObject shop, String time)
            throws Exception {
        String username = shop.getString("username");
        String password = shop.getString("password");
        
        JSONArray retJa = new JSONArray();
        
        if (JsonUtil.isBlank(username) || JsonUtil.isBlank(password)) {
            return retJa;
        }
        
        String cookies = queryCookieFromMongo(username) != null ? queryCookieFromMongo(username)
                : BdwmCheckData.userToCookie.get(username);
        log.info("-----------------baidu-cookies----------------->" + cookies);
        //如果cookies为null需要重新登录
        if (cookies == null) {
            dn_errorOrder(username, password, "bdwm", null, "该账号没有登录！");
            return retJa;
        }
        
        String day = JsonUtil.isNotBlank(time) ? time : getPreDay();
        
        //https://wmcrm.baidu.com/crm?qt=orderlist&order_status=0&start_timestamp=1493568000&end_timestamp=1494399600&pay_type=2&is_asap=0
        String queryParamStr = "&order_status=0&start_timestamp="
                + getLongTypeTime(day, " 00:00:00") + "&end_timestamp="
                + getLongTypeTime(day, " 23:59:59") + "&pay_type=2&is_asap=0";
        String orderInfo = HttpUtil.doGet("https://wmcrm.baidu.com/crm?qt=orderlist&display=json"
                + queryParamStr,
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
            retJa.add(fixData(order, username, password));
        }
        
        return retJa;
    }
    
    private JSONObject fixData(JSONObject jo, String userName, String password)
            throws Exception {
        JSONObject fixJo = new JSONObject();
        try {
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
            //        fixJo.put("merchantActivityPart", getmerchantPart(jo));
            fixJo.put("serviceFee", getserviceFee(jo));
            merchant_activities_subsidies(fixJo, jo);
            merchant_dist_charge(fixJo, jo);
            //        platform_dist_charge(fixJo, jo);
            settlement_amount(fixJo, jo);
            distribution_mode(fixJo, jo);
        }
        catch (Exception e) {
            e.printStackTrace();
            log.info(e.getMessage());
            dn_errorOrder(userName, password, "bdwm", jo, e.getMessage());
            throw e;
        }
        return fixJo;
    }
    
    private void dn_errorOrder(String username, String password,
            String platform, JSONObject jo, String msg) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        BasicDBObject relJo = new BasicDBObject();
        relJo.put("username", username);
        relJo.put("password", password);
        relJo.put("platformType", platform);
        relJo.put("json", jo);
        relJo.put("msg", msg);
        
        BasicDBObject shopIds = new BasicDBObject();
        shopIds.put("username", username);
        shopIds.put("password", password);
        DBCollection dbc = mt.getCollection("dn_errorOrder");
        dbc.remove(shopIds);
        mt.remove(shopIds, "dn_errorOrder");
        mt.insert(relJo, "dn_errorOrder");
    }
    
    private void settlement_amount(JSONObject fixJo, JSONObject jo)
            throws Exception {
        if (JsonUtil.isBlank(jo.get("order_total"))) {
            return;
        }
        
        JSONObject order_total = jo.getJSONObject("order_total");
        fixJo.put("settlement_amount",
                JsonUtil.getString(order_total, "shop_price"));
    }
    
    private void distribution_mode(JSONObject fixJo, JSONObject jo)
            throws Exception {
        String is_baidu_logistics = JsonUtil.getString(jo.getJSONObject("order_basic"),
                "is_baidu_logistics");
        fixJo.put("distribution_mode",
                "1".equals(is_baidu_logistics) ? "CONTROLLED" : "CROWD");
    }
    
    private void platform_dist_charge(JSONObject fixJo, JSONObject jo)
            throws Exception {
        if (JsonUtil.isBlank(jo.get("takeout_cost"))) {
            return;
        }
        JSONObject takeout_cost = jo.getJSONObject("takeout_cost");
        if (JsonUtil.isBlank(takeout_cost.get("price"))) {
            fixJo.put("platform_dist_charge", "0");
        }
        String is_baidu_logistics = JsonUtil.getString(jo.getJSONObject("order_basic"),
                "is_baidu_logistics");
        if ("1".equals(is_baidu_logistics)) {
            fixJo.put("platform_dist_charge",
                    JsonUtil.getBigDecimal(takeout_cost, "price"));
        }
        else {
            fixJo.put("platform_dist_charge", "0");
        }
    }
    
    private void merchant_dist_charge(JSONObject fixJo, JSONObject jo)
            throws Exception {
        if (JsonUtil.isBlank(jo.get("takeout_cost"))) {
            return;
        }
        JSONObject takeout_cost = jo.getJSONObject("takeout_cost");
        if (JsonUtil.isBlank(takeout_cost.get("price"))) {
            fixJo.put("merchant_dist_charge", "0");
        }
        String is_baidu_logistics = JsonUtil.getString(jo.getJSONObject("order_basic"),
                "is_baidu_logistics");
        if ("0".equals(is_baidu_logistics)) {
            fixJo.put("merchant_dist_charge",
                    JsonUtil.getBigDecimal(takeout_cost, "price"));
        }
        else {
            fixJo.put("merchant_dist_charge", "0");
        }
    }
    
    private void merchant_activities_subsidies(JSONObject fixJo, JSONObject jo)
            throws Exception {
        if (JsonUtil.isBlank(jo.get("order_goods"))) {
            return;
        }
        JSONObject order_goods = jo.getJSONObject("order_goods");
        if (JsonUtil.isBlank(order_goods.get("goods_list"))) {
            return;
        }
        JSONArray goods_list = order_goods.getJSONArray("goods_list");
        double shop_total_discount = 0.0;
        for (Object o : goods_list) {
            JSONObject goodJo = (JSONObject)o;
            if (JsonUtil.isBlank(goodJo.get("ext"))) {
                return;
            }
            JSONObject extJo = goodJo.getJSONObject("ext");
            if (JsonUtil.isBlank(extJo.get("shop_total_discount"))) {
                continue;
            }
            String shop_total_discountStr = extJo.getString("shop_total_discount");
            shop_total_discount += Double.parseDouble(shop_total_discountStr.replace("已优惠",
                    ""));
        }
        
        if (JsonUtil.isNotBlank(jo.get("shop_other_discount"))) {
            JSONObject shop_other_discount = jo.getJSONObject("shop_other_discount");
            if (JsonUtil.isNotBlank(shop_other_discount.get("price"))) {
                shop_total_discount += Math.abs(shop_other_discount.getDouble("price"));
            }
        }
        fixJo.put("merchant_activities_subsidies", shop_total_discount);
    }
    
    private Double getserviceFee(JSONObject jo) throws Exception {
        JSONObject extract_commission = jo.getJSONObject("extract_commission");
        if (JsonUtil.isBlank(extract_commission)) {
            return 0.00;
        }
        if (JsonUtil.isBlank(extract_commission.get("commission_total"))) {
            return 0.00;
        }
        return extract_commission.getDouble("commission_total");
    }
    
    private Double getmerchantPart(JSONObject jo) throws Exception {
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
    
    private void fixorder_total(JSONObject fixJo, JSONObject order)
            throws Exception {
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
    
    private Long getLongTypeTime(String day, String time) {
        SimpleDateFormat dateformat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");
        try {
            Date date = dateformat.parse(day + time);
            return date.getTime() / 1000;
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private void fixorder_meal_fee(JSONObject fixJo, JSONObject order)
            throws Exception {
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
    
    private void fixOrder_goods(JSONObject fixJo, JSONObject order)
            throws Exception {
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
    
    private void fixOrder_basic(JSONObject fixJo, JSONObject order)
            throws Exception {
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
        fixJo.put("platformCount",
                JsonUtil.getString(order_basic, "order_index"));
        fixJo.put("remark", JsonUtil.getString(order_basic, "user_note"));
    }
    
    public boolean execute(JSONObject[] arg0, String arg1) throws Exception {
        return false;
    }
    
    public static void main(String[] args) {
        BdwmCheckData dddd = new BdwmCheckData();
        //        String day1 = "2017-05-08";
        //        String day2 = "2017-05-08";
        //        String queryParamStr = "&order_status=0&start_timestamp="
        //                + dddd.getLongTypeTime(day1, " 00:00:00") + "&end_timestamp="
        //                + dddd.getLongTypeTime(day2, " 23:59:59")
        //                + "&pay_type=2&is_asap=0";
        //        String orderInfo = HttpUtil.doGet("https://wmcrm.baidu.com/crm?qt=orderlist&display=json"
        //                + queryParamStr,
        //                "WMPTOKEN=oAAH8BAABGSiohcxwLcUZpD3EoNiIbHA50CAA2NzMRhLDGFRaKkHCdWPAgz5VQnB; expires=Sat, 10-Jun-2017 06:43:29 GMT; Max-Age=2592000; path=/; domain=wmpass.baidu.com; HttpOnly;WMUSS=MAAFMBAAAyC0gjM31ETRlRQSM0NFRLf18sUBA2dQYLRBxSLz0MYEhTBHpRR0hpGQoNFVh8xR5lAAATD5gGD1ARA5PegNUBUdwV3YuYld1NlpwByI5cDpCfAd%7EXR49HmJKbFxNQWEVMTgLXxtiVFA8RVdeaHzWB67h-guFyWcN8Je0EYKvhw-NmsgPbDgDArB; expires=Sat, 10-Jun-2017 06:43:29 GMT; Max-Age=2592000; path=/; domain=.baidu.com; HttpOnly;newyear=open;new_remind_time=1491707183;new_order_time=1491707474;WMSTOKEN=BcAACcMAAAsehRtWygfEzN9LRVRfVEWVC8UHEdTX2QSAiENQDQ6wAAHWHQSP50gAA5TbDhmWRQPdydBdFNWRmgDagtVgP0OEVIAAGQ5ThT5DQAAH6cXGTH_PBp4C3UZO",
        //                "");
        //        log.info(orderInfo);
        
        try {
            for (int i = 1; i <= 30; i++) {
                if (i < 10) {
                    dddd.selectTasks("2017-04-0" + i, null, 0, null, 0);
                }
                else {
                    dddd.selectTasks("2017-04-" + i, null, 0, null, 0);
                }
                Thread.sleep(500);
            }
            
            for (int i = 1; i <= 11; i++) {
                if (i < 10) {
                    dddd.selectTasks("2017-05-0" + i, null, 0, null, 0);
                }
                else {
                    dddd.selectTasks("2017-05-" + i, null, 0, null, 0);
                }
                Thread.sleep(500);
            }
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        //        new BdwmCheckData().test(orderInfo);
        
        //        SimpleDateFormat dateformat = new SimpleDateFormat(
        //                "yyyy-MM-dd HH:mm:ss");
        //        GregorianCalendar gc = new GregorianCalendar();
        //        gc.setTimeInMillis(1494342000000L);
        //        log.info(dateformat.format(gc.getTime()));
        //        
        //        try {
        //            Date date1 = dateformat.parse(dddd.getPreDay() + " 00:00:00");
        //            log.info(date1.getTime() / 1000 + "");
        //            gc.setTimeInMillis(date1.getTime());
        //            log.info(dateformat.format(gc.getTime()));
        //            Date date2 = dateformat.parse(dddd.getPreDay() + " 23:59:59");
        //            log.info(date2.getTime() + "");
        //            gc.setTimeInMillis(date2.getTime());
        //            log.info(dateformat.format(gc.getTime()));
        //        }
        //        catch (ParseException e) {
        //            // TODO Auto-generated catch block
        //            e.printStackTrace();
        //        }
        
    }
}
