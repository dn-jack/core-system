package com.dongnao.core.checkdata;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.util.ElemeUtil;
import com.dongnao.core.util.HttpRequest;
import com.dongnao.core.util.HttpsRequestUtil;
import com.dongnao.core.util.JsonUtil;
import com.dongnao.core.util.SpringContextHolder;
import com.dongnao.core.util.UrlUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.taobao.pamirs.schedule.IScheduleTaskDealMulti;
import com.taobao.pamirs.schedule.TaskItemDefine;

public class ElemeCheckData implements IScheduleTaskDealMulti<JSONObject> {
    
    private static transient Logger log = LoggerFactory.getLogger(ElemeCheckData.class);
    
    private Map<String, String> cache = new HashMap<String, String>();
    
    @Override
    public Comparator<JSONObject> getComparator() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public List<JSONObject> selectTasks(String arg0, String arg1, int arg2,
            List<TaskItemDefine> arg3, int arg4) throws Exception {
        log.info("--------------------------【饿了么数据比对开始ElemeCheckData】---------------------------"
                + arg0);
        //获取所有饿了么店铺的账号和密码和shopid
        String retStr = HttpRequest.sendGet(UrlUtil.queryShop
                + "?platformType=elm");
        log.info("-----------------------饿了么所有店铺信息---------------------"
                + retStr);
        JSONObject elemShops = JSON.parseObject(retStr);
        if (!"0000".equals(elemShops.getString("respCode"))) {
            return null;
        }
        
        //从餐予者数据中获取所有饿了么店铺订单数据
        JSONObject queryJo = new JSONObject();
        queryJo.put("orderTime", JsonUtil.isNotBlank(arg0) ? arg0 : getPreDay());
        queryJo.put("platformType", "elm");
        String queryStr = HttpRequest.sendPostJson(UrlUtil.queryOrderUrl,
                queryJo.toString());
        log.info("当天【饿了么】店铺的所有在餐予者平台入库的订单数据-----------" + queryStr);
        JSONObject queryRetJo = JSON.parseObject(queryStr);
        if (!"0000".equals(queryRetJo.getString("respCode"))) {
            log.info(queryRetJo.getString("respDesc"));
            return null;
        }
        //所有饿了么店铺数据
        Object orderJa = queryRetJo.get("result");
        
        JSONArray resultJa = elemShops.getJSONArray("result");
        //所有店铺的丢失的数据
        JSONArray loseDatas = new JSONArray();
        
        for (Object o : resultJa) {
            JSONObject shop = (JSONObject)o;
            String shopId = shop.getString("shopId");
            String username = shop.getString("username");
            String password = shop.getString("password");
            JSONArray queryJa = null;
            try {
                //从饿了么平台查询出该店铺今天的所有订单
                queryJa = queryOrderFromElem(shop, arg0);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.info(e.getMessage());
                dn_errorOrder(username,
                        password,
                        shopId,
                        "elm",
                        null,
                        e.getMessage());
                continue;
            }
            //从db中获取该店铺今天的所有订单
            JSONArray dbJa = queryOrderFromdb(shop.getString("shopId"), orderJa);
            //饿了么平台数据和餐予者库中数据做比对，如果餐予者库中不存在的数据则把订单数据补入餐予者库中
            JSONArray loseData = checkData(queryJa, dbJa);
            addToTotal(loseDatas, loseData);
        }
        
        if (loseDatas.size() > 0) {
            insertToDb(loseDatas);
        }
        log.info("--------------------------【饿了么数据比对结束ElemeCheckData】---------------------------");
        return null;
    }
    
    private void insertToDb(JSONArray loseDatas) {
        String queryStr = HttpRequest.sendPostJson(UrlUtil.orderInsertBatchDb,
                loseDatas.toString());
        log.info("数据插入后结果-------------------->" + queryStr);
    }
    
    private void insertToMongodb(JSONArray loseDatas) {
        if (loseDatas.size() <= 0) {
            return;
        }
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        for (Object o : loseDatas) {
            mt.insert(o.toString(), "dn_order");
        }
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
            if (shopId.equals(map.get("store_id").toString())) {
                retJa.add(JSONObject.parseObject(JSON.toJSONString(map)));
            }
        }
        
        return retJa;
    }
    
    private JSONArray queryOrderFromMongodb(String shopId) {
        BasicDBObject cond3 = new BasicDBObject();
        BasicDBObject cond1 = new BasicDBObject();
        cond1.put("$gt", getPreDay());
        cond3.put("orderTime", cond1);
        cond3.put("merchantId", shopId);
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        DBCollection dbc = mt.getCollection("dn_order");
        DBCursor cursor = dbc.find(cond3);
        JSONArray resultJa = new JSONArray();
        while (cursor.hasNext()) {
            DBObject each = cursor.next();
            each.put("shopName", each.get("merchantName"));
            each.put("platformType", each.get("platform_type"));
            resultJa.add(each);
        }
        return resultJa;
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
    
    private String login(String shopId, String username, String password) {
        String ksid = null;
        JSONObject loginJo = ElemeUtil.loginJo();
        JSONObject paramsJo = loginJo.getJSONObject("params");
        paramsJo.put("username", username);
        paramsJo.put("password", password);
        if (!cache.containsKey(username)) {
            String loginRe = null;
            try {
                loginRe = HttpsRequestUtil.doPost(ElemeUtil.loginurl,
                        loginJo.toString(),
                        "UTF-8",
                        300000,
                        300000);
            }
            catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            log.info("---------------loginurl----------------" + loginRe);
            
            JSONObject loginRejo = JSON.parseObject(loginRe);
            if (JsonUtil.isNotBlank(loginRejo.get("result"))
                    && loginRejo.getJSONObject("result").getBoolean("succeed")) {
                ksid = loginRejo.getJSONObject("result")
                        .getJSONObject("successData")
                        .getString("ksid");
                cache.put(username, ksid);
                
                MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                        .getBean("mongoTemplate");
                
                JSONObject relJo = new JSONObject();
                relJo.put("username", username);
                relJo.put("ksid", ksid);
                
                BasicDBObject shopIds = new BasicDBObject();
                shopIds.put("username", username);
                DBCollection dbc = mt.getCollection("dn_ksid");
                dbc.remove(shopIds);
                mt.remove(shopIds, "dn_ksid");
                mt.insert(relJo.toString(), "dn_ksid");
                
            }
        }
        return ksid;
    }
    
    private String diguiInvoke(String url, JSONObject countJo) throws Exception {
        log.info("-------------------------countJo--------------------"
                + countJo);
        String countRe = HttpsRequestUtil.doPost(url,
                countJo.toString(),
                "UTF-8",
                300000,
                300000);
        log.info("-----------------------countRe------------------" + countRe);
        JSONObject count = JSON.parseObject(countRe);
        if (count.get("result") != null) {
            return countRe;
        }
        else {
            Thread.sleep(1000);
            
            return diguiInvoke(url, countJo);
        }
        
    }
    
    private void errorId(String shopId, String username, String password) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        JSONObject relJo = new JSONObject();
        relJo.put("username", username);
        relJo.put("password", password);
        relJo.put("shopId", shopId);
        relJo.put("platformType", "elm");
        
        BasicDBObject shopIds = new BasicDBObject();
        shopIds.put("username", username);
        shopIds.put("password", password);
        DBCollection dbc = mt.getCollection("dn_errorId");
        dbc.remove(shopIds);
        mt.remove(shopIds, "dn_errorId");
        mt.insert(relJo.toString(), "dn_errorId");
    }
    
    private JSONArray queryOrderFromElem(JSONObject shop, String time)
            throws Exception {
        String shopId = shop.getString("shopId");
        String username = shop.getString("username");
        String password = shop.getString("password");
        JSONArray retJa = new JSONArray();
        if (JsonUtil.isBlank(username) || JsonUtil.isBlank(password)) {
            return retJa;
        }
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        DBCollection dbc = mt.getCollection("dn_ksid");
        BasicDBObject cond1 = new BasicDBObject();
        cond1.put("username", username);
        DBCursor cursor = dbc.find(cond1);
        
        String ksid = null;
        
        while (cursor.hasNext()) {
            DBObject dbo = cursor.next();
            ksid = dbo.get("ksid").toString();
        }
        
        //如果ksid为null重新模拟登陆
        if (ksid == null) {
            ksid = login(shopId, username, password);
        }
        
        if (ksid == null) {
            dn_errorOrder(username, password, shopId, "elm", null, "账号密码错误！");
            return retJa;
        }
        
        JSONObject countJo = ElemeUtil.countOrderJo();
        JSONObject queryOrderJo = ElemeUtil.queryOrderJo();
        countJo.getJSONObject("metas").put("ksid", ksid);
        countJo.getJSONObject("params").put("shopId", shopId);
        countJo.getJSONObject("params")
                .getJSONObject("condition")
                .put("beginTime",
                        (JsonUtil.isNotBlank(time) ? time : getPreDay())
                                + "T00:00:00");
        countJo.getJSONObject("params")
                .getJSONObject("condition")
                .put("endTime",
                        (JsonUtil.isNotBlank(time) ? time : getPreDay())
                                + "T23:59:59");
        log.info("--------------------------countJo----------------------"
                + countJo.toJSONString());
        //            String countRe = HttpsRequestUtil.doPost(ElemeUtil.countOrderurl,
        //                    countJo.toString(),
        //                    "UTF-8",
        //                    300000,
        //                    300000);
        String countRe = diguiInvoke(ElemeUtil.countOrderurl, countJo);
        log.info(countRe);
        JSONObject count = JSON.parseObject(countRe);
        
        if (count.get("result") != null) {
            queryOrderJo.getJSONObject("params")
                    .getJSONObject("condition")
                    .put("limit", count.getInteger("result"));
        }
        
        queryOrderJo.getJSONObject("metas").put("ksid", ksid);
        
        queryOrderJo.getJSONObject("params").put("shopId", shopId);
        queryOrderJo.getJSONObject("params").put("orderFilter",
                "ORDER_QUERY_ALL");
        
        log.info("-----------------------------查询时间："
                + (JsonUtil.isNotBlank(time) ? time : getPreDay()));
        
        queryOrderJo.getJSONObject("params")
                .getJSONObject("condition")
                .put("beginTime",
                        (JsonUtil.isNotBlank(time) ? time : getPreDay())
                                + "T00:00:00");
        queryOrderJo.getJSONObject("params")
                .getJSONObject("condition")
                .put("endTime",
                        (JsonUtil.isNotBlank(time) ? time : getPreDay())
                                + "T23:59:59");
        
        log.info("--------------queryOrderJo-------------"
                + queryOrderJo.toString());
        
        //            String queryOrderRe = HttpsRequestUtil.doPost(ElemeUtil.queryOrderurl,
        //                    queryOrderJo.toString(),
        //                    "UTF-8",
        //                    300000,
        //                    300000);
        String queryOrderRe = diguiInvoke(ElemeUtil.queryOrderurl, queryOrderJo);
        
        log.info("--------------------queryOrderurl-----------------"
                + queryOrderRe);
        JSONObject queryReJo = JSON.parseObject(queryOrderRe);
        JSONArray reJa = queryReJo.getJSONArray("result");
        
        if (JsonUtil.isNotBlank(reJa)) {
            for (Object o : reJa) {
                JSONObject reJo = (JSONObject)o;
                String status = JsonUtil.getString(reJo, "status");
                
                /**
                 * 饿了么订单
                "status": "INVALID" -- 订单取消
                "status": "VALID" -- 已接单
                "status": "UNPROCESSED" -- 未接单
                 */
                //                    if ("INVALID".equals(status)) {
                //                        continue;
                //                    }
                
                retJa.add(fixData(reJo, username, password, shopId));
            }
        }
        return retJa;
    }
    
    private JSONObject fixData(JSONObject jo, String username, String password,
            String shopId) throws Exception {
        JSONObject fixJo = new JSONObject();
        try {
            fixJo.put("orderTime", JsonUtil.getString(jo, "activeTime")
                    .replace("T", " "));
            fixJo.put("orderNo", JsonUtil.getString(jo, "id"));
            fixJo.put("userName", JsonUtil.getString(jo, "consigneeName"));
            //        fixJo.put("sex", JsonUtil.getString(fixJo, "consigneeName"));
            fixJo.put("phone", jo.getJSONArray("consigneePhones").get(0));
            fixJo.put("merchantId", JsonUtil.getString(jo, "shopId"));
            fixJo.put("platformCount", JsonUtil.getString(jo, "daySn"));
            String status = JsonUtil.getString(jo, "status");
            
            if ("INVALID".equals(status)) {
                fixJo.put("is_invalid", "1");
            }
            else {
                fixJo.put("is_invalid", "0");
            }
            
            JSONArray groupsJa = jo.getJSONArray("groups");
            JSONArray dishesJa = new JSONArray();
            for (Object o : groupsJa) {
                JSONObject groupsJo = (JSONObject)o;
                if ("NORMAL".equals(groupsJo.getString("type"))) {
                    JSONArray itemsJa = groupsJo.getJSONArray("items");
                    
                    for (Object itemO : itemsJa) {
                        JSONObject itemJo = (JSONObject)itemO;
                        JSONObject dishesJo = new JSONObject();
                        dishesJo.put("dishName",
                                JsonUtil.getString(itemJo, "name"));
                        dishesJo.put("activityName", "特价");
                        dishesJo.put("count",
                                JsonUtil.getString(itemJo, "quantity"));
                        dishesJo.put("price1",
                                JsonUtil.getString(itemJo, "price"));
                        dishesJo.put("price2",
                                JsonUtil.getString(itemJo, "total"));
                        dishesJo.put("goods_id",
                                JsonUtil.getString(itemJo, "id"));
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
            String orderType = JsonUtil.getString(jo, "orderType");
            if ("BOOKING_UNPROCESSED".equals(orderType)) {
                orderType = "BOOKING";
            }
            fixJo.put("orderType", orderType);
            fixJo.put("merchantActivityPart",
                    JsonUtil.getString(jo, "merchantActivityPart"));
            fixJo.put("elemeActivityPart",
                    JsonUtil.getString(jo, "elemeActivityPart"));
            fixJo.put("serviceFee", JsonUtil.getString(jo, "serviceFee"));
            fixJo.put("serviceRate", JsonUtil.getString(jo, "serviceRate"));
            
            //        fixJo.put("platform_dist_charge",
            //                JsonUtil.getString(jo, "deliveryFeeTotal"));
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
            
            //20170508新增
            double amount = 0.0;
            if (JsonUtil.isNotBlank(jo.get("merchantActivities"))) {
                JSONArray merchantActivities = jo.getJSONArray("merchantActivities");
                for (Object o : merchantActivities) {
                    JSONObject merchantActivitieJo = (JSONObject)o;
                    if ("商家代金券抵扣".equals(merchantActivitieJo.getString("name")
                            .trim())) {
                        amount += merchantActivitieJo.getDouble("amount");
                    }
                    //                else if ("红包抵扣".equals(merchantActivitieJo.getString("name")
                    //                        .trim())) {
                    //                    amount += merchantActivitieJo.getDouble("amount");
                    //                }
                }
                fixJo.put("merchant_subsidy_vouchers", amount);
            }
            fixJo.put("merchant_activities_subsidies",
                    jo.getDouble("restaurantPart") - amount);
            String deliveryServiceType = JsonUtil.getString(jo,
                    "deliveryServiceType");
            if ("CROWD".equals(deliveryServiceType)) {
                fixJo.put("merchant_dist_charge",
                        JsonUtil.getString(jo, "deliveryFeeTotal"));
            }
            else {
                fixJo.put("merchant_dist_charge", "0");
            }
            
            if ("CONTROLLED".equals(deliveryServiceType)) {
                fixJo.put("platform_dist_charge",
                        JsonUtil.getString(jo, "deliveryFeeTotal"));
                fixJo.put("order_dist_charge ",
                        JsonUtil.getString(jo, "deliveryFeeTotal"));
            }
            else {
                fixJo.put("platform_dist_charge", "0");
                fixJo.put("order_dist_charge ", "0");
            }
            fixJo.put("distribution_mode",
                    JsonUtil.getString(jo, "deliveryServiceType"));
            fixJo.put("platform_activities_subsidies",
                    JsonUtil.getString(jo, "elemeActivityPart"));
            //20170508新增
            
        }
        catch (Exception e) {
            e.printStackTrace();
            log.info(e.getMessage());
            dn_errorOrder(username, password, shopId, "elm", jo, e.getMessage());
            throw e;
        }
        return fixJo;
    }
    
    private void dn_errorOrder(String username, String password, String shopId,
            String platform, JSONObject jo, String msg) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        BasicDBObject relJo = new BasicDBObject();
        relJo.put("username", username);
        relJo.put("password", password);
        relJo.put("shopId", shopId);
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
    
    @Override
    public boolean execute(JSONObject[] arg0, String arg1) throws Exception {
        // TODO Auto-generated method stub
        return false;
    }
    
    public static void main(String[] args) {
        //        try {
        //            String aaa = "2017-05-01";
        //            ElemeCheckData ee = new ElemeCheckData();
        //            //            new ElemeCheckData().selectTasks(null, null, 1, null, 1);
        //            
        //            JSONObject countJo = ElemeUtil.countOrderJo();
        //            JSONObject queryOrderJo = ElemeUtil.queryOrderJo();
        //            countJo.getJSONObject("metas").put("ksid",
        //                    "MzU3ZTA3NTctMTgxMS00OWNiLTk3YWYWM1ZG");
        //            countJo.getJSONObject("params").put("shopId", 2263877);
        //            countJo.getJSONObject("params")
        //                    .getJSONObject("condition")
        //                    .put("beginTime", aaa + "T00:00:00");
        //            countJo.getJSONObject("params")
        //                    .getJSONObject("condition")
        //                    .put("endTime", aaa + "T23:59:59");
        //            log.info(countJo.toJSONString());
        //            String countRe = HttpsRequestUtil.doPost(ElemeUtil.countOrderurl,
        //                    countJo.toString(),
        //                    "UTF-8",
        //                    300000,
        //                    300000);
        //            log.info(countRe);
        //            JSONObject count = JSON.parseObject(countRe);
        //            if (count.get("result") != null) {
        //                queryOrderJo.getJSONObject("params")
        //                        .getJSONObject("condition")
        //                        .put("limit", count.getInteger("result"));
        //            }
        //            
        //            queryOrderJo.getJSONObject("metas").put("ksid",
        //                    "MzU3ZTA3NTctMTgxMS00OWNiLTk3YWYWM1ZG");
        //            
        //            queryOrderJo.getJSONObject("params").put("shopId", 2263877);
        //            queryOrderJo.getJSONObject("params").put("orderFilter",
        //                    "ORDER_QUERY_ALL");
        //            
        //            log.info("-----------------------------查询时间：" + ee.getPreDay());
        //            
        //            queryOrderJo.getJSONObject("params")
        //                    .getJSONObject("condition")
        //                    .put("beginTime", aaa + "T00:00:00");
        //            queryOrderJo.getJSONObject("params")
        //                    .getJSONObject("condition")
        //                    .put("endTime", aaa + "T23:59:59");
        //            log.info(queryOrderJo.toJSONString());
        //            String queryOrderRe = HttpsRequestUtil.doPost(ElemeUtil.queryOrderurl,
        //                    queryOrderJo.toString(),
        //                    "UTF-8",
        //                    300000,
        //                    300000);
        //            log.info(queryOrderRe);
        //            JSONObject aaaaa = JSON.parseObject(queryOrderRe);
        //            if (aaaaa.get("result") != null) {
        //                log.info((JsonUtil.isNotBlank(aaa) ? aaa : "") + "T00:00:00");
        //            }
        //            
        //        }
        //        catch (Exception e) {
        //            // TODO Auto-generated catch block
        //            e.printStackTrace();
        //        }
        ElemeCheckData ee = new ElemeCheckData();
        //        ee.login("152165927", "wmhfhs29652", "PNHYJ54041");
        String ksid = null;
        JSONObject loginJo = ElemeUtil.loginJo();
        JSONObject paramsJo = loginJo.getJSONObject("params");
        paramsJo.put("username", "wmhfhs29652");
        paramsJo.put("password", "PNHYJ54041");
        String loginRe = null;
        try {
            loginRe = HttpsRequestUtil.doPost(ElemeUtil.loginurl,
                    loginJo.toString(),
                    "UTF-8",
                    300000,
                    300000);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        log.info("---------------loginurl----------------" + loginRe);
    }
}
