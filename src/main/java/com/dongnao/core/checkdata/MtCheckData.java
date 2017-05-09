package com.dongnao.core.checkdata;

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

public class MtCheckData implements IScheduleTaskDealMulti<JSONObject> {
    
    private static transient Logger log = LoggerFactory.getLogger(MtCheckData.class);
    
    public Comparator<JSONObject> getComparator() {
        return null;
    }
    
    public List<JSONObject> selectTasks(String arg0, String arg1, int arg2,
            List<TaskItemDefine> arg3, int arg4) throws Exception {
        log.info("--------------------------【美团数据比对开始MtCheckData】---------------------------"
                + arg0);
        
        // TODO Auto-generated method stub
        //获取所有美团店铺的账号和密码和shopid
        String retStr = HttpRequest.sendGet(UrlUtil.queryShop
                + "?platformType=mt");
        log.info("--------------------------所有美团店铺信息-----" + retStr);
        JSONObject mtShops = JSON.parseObject(retStr);
        if (!"0000".equals(mtShops.getString("respCode"))) {
            return null;
        }
        
        //从餐予者数据中获取所有美团店铺订单数据
        JSONObject queryJo = new JSONObject();
        queryJo.put("orderTime", JsonUtil.isNotBlank(arg0) ? arg0 : getPreDay());
        queryJo.put("platformType", "mt");
        String queryStr = HttpRequest.sendPostJson(UrlUtil.queryOrderUrl,
                queryJo.toString());
        log.info("当天【美团】店铺的所有在餐予者平台入库的订单数据-----------" + queryStr);
        JSONObject queryRetJo = JSON.parseObject(queryStr);
        if (!"0000".equals(queryRetJo.getString("respCode"))) {
            log.info(queryRetJo.getString("respDesc"));
            return null;
        }
        //所有美团店铺数据
        Object orderJa = queryRetJo.get("result");
        
        JSONArray resultJa = mtShops.getJSONArray("result");
        //所有店铺的丢失的数据
        JSONArray loseDatas = new JSONArray();
        
        for (Object o : resultJa) {
            JSONObject shop = (JSONObject)o;
            //从美团平台查询出该店铺今天的所有订单
            JSONArray queryJa = queryOrderFromMt(shop, arg0);
            //从db中获取该店铺今天的所有订单
            JSONArray dbJa = queryOrderFromdb(shop.getString("username"),
                    orderJa);
            //美团平台数据和餐予者库中数据做比对，如果餐予者库中不存在的数据则把订单数据补入餐予者库中
            JSONArray loseData = checkData(queryJa, dbJa);
            addToTotal(loseDatas, loseData);
        }
        if (loseDatas.size() > 0) {
            insertToDb(loseDatas);
        }
        log.info("--------------------------【美团数据比对结束MtCheckData】---------------------------");
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
            if (shopId.equals(map.get("STORE_ID".toLowerCase()).toString())) {
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
    
    private String login(String username, String password) {
        log.info("step1--------------------->invoke");
        JSONObject step1Ret = step1(username, password);
        
        if (step1Ret.containsKey("Location")) {
            log.info("step2--------------------->invoke");
            Map<String, String> step2Ret = step2(step1Ret.getString("Location"));
            if (step2Ret.containsKey("BSID")
                    && step2Ret.containsKey("entryList")
                    && step2Ret.containsKey("device_uuid")) {
                log.info("step3--------------------->invoke");
                return step3(step2Ret, username);
            }
        }
        return null;
    }
    
    private JSONObject step1(String userName, String password) {
        StringBuffer sb = new StringBuffer();
        sb.append("login=" + userName)
                .append("&password=" + password)
                .append("&part_key=")
                .append("&captcha_code=")
                .append("&captcha_v_token=")
                .append("&sms_verify=" + 0)
                .append("&sms_code=");
        log.info("step1--------------------->param:" + sb.toString());
        JSONObject ret = null;
        try {
            ret = HttpsRequestUtil.doPost1(UrlUtil.MT_loginurl,
                    sb.toString(),
                    "UTF-8",
                    300000,
                    300000);
            log.info("step1--------------------->result:" + ret.toString());
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        return ret;
    }
    
    private Map<String, String> step2(String location) {
        
        Map<String, String> retMap = new HashMap<String, String>();
        String BSID = location.split("\\?")[1].replace("BSID=", "");
        retMap.put("BSID", BSID);
        log.info("step2--------------------->BSID:" + BSID);
        
        List<String> entryList = HttpRequest.sendGet1(location);
        
        StringBuffer sb1 = new StringBuffer();
        for (String entry : entryList) {
            sb1.append(entry).append(";");
        }
        retMap.put("entryList", sb1.toString());
        log.info("step2--------------------->entryList:" + entryList.toString());
        
        String device_uuid = "";
        
        for (String ele : entryList) {
            if (ele.contains("device_uuid")) {
                String replaceStr = ele.replace("device_uuid=", "");
                device_uuid = replaceStr.substring(0, replaceStr.indexOf(";"));
            }
        }
        retMap.put("device_uuid", device_uuid);
        log.info("step2--------------------->device_uuid:" + device_uuid);
        return retMap;
    }
    
    private String step3(Map<String, String> step2, String userName) {
        Map<String, String> retMap = HttpRequest.sendPost1(UrlUtil.MT_logon,
                "BSID=" + step2.get("BSID") + "&device_uuid="
                        + step2.get("device_uuid") + "&service=",
                step2.get("entryList"));
        log.info("step3--------------------->retMap:" + retMap);
        insertMongodbByUserName(userName,
                step2.get("entryList") + retMap.get("cookie"));
        return step2.get("entryList") + retMap.get("cookie");
    }
    
    private void insertMongodbByUserName(String userName, String cookies) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        BasicDBObject userNameBo = new BasicDBObject();
        BasicDBObject cookieBo = new BasicDBObject();
        userNameBo.put("userName", userName);
        cookieBo.put("userName", userName);
        cookieBo.put("cookies", cookies);
        DBCollection dbc = mt.getCollection("dn_cookies");
        dbc.remove(userNameBo);
        mt.remove(userNameBo, "dn_cookies");
        mt.insert(cookieBo, "dn_cookies");
    }
    
    private JSONArray queryOrderFromMt(JSONObject shop, String time) {
        String username = shop.getString("username");
        String password = shop.getString("password");
        
        JSONArray retJa = new JSONArray();
        
        if (JsonUtil.isBlank(username) || JsonUtil.isBlank(password)) {
            return retJa;
        }
        
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        
        DBCollection dbc = mt.getCollection("dn_cookies");
        BasicDBObject cond1 = new BasicDBObject();
        cond1.put("userName", username);
        DBCursor cursor = dbc.find(cond1);
        
        String cookies = null;
        
        while (cursor.hasNext()) {
            DBObject dbo = cursor.next();
            cookies = dbo.get("cookies").toString();
        }
        
        //如果cookies为null需要重新登录
        if (cookies == null) {
            cookies = login(username, password);
        }
        
        String queryStr = "?getNewVo=1&wmOrderPayType=2&wmOrderStatus=-2&sortField=1&lastLabel=&nextLabel=&_token="
                + getValue(cookies, "token");
        
        queryStr = queryStr + "&startDate="
                + (JsonUtil.isNotBlank(time) ? time : getPreDay())
                + "&endDate="
                + (JsonUtil.isNotBlank(time) ? time : getPreDay());
        
        String result = HttpRequest.sendGet2(UrlUtil.mtQuery + queryStr,
                cookies);
        
        log.info("-----------------------美团订单信息----------------------" + result);
        
        if (JsonUtil.isBlank(result)) {
            return retJa;
        }
        
        JSONObject retJo = null;
        if (isJson(result)) {
            retJo = JSON.parseObject(result);
        }
        else {
            return retJa;
        }
        
        if (JsonUtil.isBlank(retJo.get("wmOrderList"))) {
            return retJa;
        }
        
        JSONArray orderListJa = retJo.getJSONArray("wmOrderList");
        
        for (Object o : orderListJa) {
            JSONObject eachData = (JSONObject)o;
            String status = JsonUtil.getString(eachData, "status");
            
            /**
             * "status": 2 --未接单状态
                "status": 4 --已接单
                "status": 9 --订单取消(用户主动取消、超时未接单取消)
                08--15 大概7分钟未接单将取消订单
             */
            //            if ("9".equals(status)) {
            //                continue;
            //            }
            Map<String, String> chargeInfo = HttpRequest.sendPost1(UrlUtil.MT_CI,
                    "chargeInfo=[{wmOrderViewId:"
                            + JsonUtil.getString(eachData, "wm_order_id_view")
                            + ",wmPoiId:"
                            + JsonUtil.getString(eachData, "wm_poi_id") + "}]",
                    cookies);
            log.info("chargeInfo--------------------->result:"
                    + chargeInfo.get("result"));
            
            String chargeRet = chargeInfo.get("result");
            JSONObject chargeJo = JSON.parseObject(chargeRet);
            if (JsonUtil.isNotBlank(chargeJo.get("data"))) {
                retJa.add(fixData(eachData, chargeJo.getJSONArray("data")
                        .getJSONObject(0), username));
            }
        }
        return retJa;
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
    
    private String getValue(String cookies, String name) {
        
        if (JsonUtil.isBlank(cookies)) {
            return "";
        }
        
        String[] cookiesArr = cookies.split(";");
        
        for (String each : cookiesArr) {
            if (each.contains(name)) {
                return each.replace(name + "=", "");
            }
        }
        
        return null;
    }
    
    private String dateParse(long mesc) {
        SimpleDateFormat dateformat = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss");
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeInMillis(mesc);
        return dateformat.format(gc.getTime());
    }
    
    private JSONObject fixData(JSONObject jo, JSONObject chargeJo,
            String userName) {
        JSONObject fixJo = new JSONObject();
        fixJo.put("orderTime", JsonUtil.getString(jo, "order_time_fmt"));
        fixJo.put("orderNo", JsonUtil.getString(jo, "wm_order_id_view_str"));
        fixJo.put("userName", JsonUtil.getString(jo, "recipient_name"));
        fixJo.put("phone", JsonUtil.getString(jo, "recipient_phone"));
        fixJo.put("merchantId", userName);
        fixJo.put("platformCount", JsonUtil.getString(jo, "num"));
        
        String status = JsonUtil.getString(jo, "status");
        if ("9".equals(status)) {
            fixJo.put("is_invalid", "1");
        }
        else {
            fixJo.put("is_invalid", "0");
        }
        
        if (JsonUtil.isNotBlank(jo.get("cartDetailVos"))) {
            JSONArray dishesJa = new JSONArray();
            for (Object o : jo.getJSONArray("cartDetailVos")) {
                JSONObject cartDetailVosJo = (JSONObject)o;
                if (JsonUtil.isNotBlank(cartDetailVosJo.get("details"))) {
                    JSONArray detailsJa = cartDetailVosJo.getJSONArray("details");
                    for (Object detailso : detailsJa) {
                        JSONObject detailJo = (JSONObject)detailso;
                        JSONObject dishJo = new JSONObject();
                        dishJo.put("dishName",
                                JsonUtil.getString(detailJo, "food_name"));
                        //                        dishJo.put("activityName", "特价");
                        dishJo.put("count",
                                JsonUtil.getString(detailJo, "count"));
                        dishJo.put("price1", JsonUtil.getString(detailJo,
                                "origin_food_price"));
                        dishJo.put("price2",
                                JsonUtil.getString(detailJo, "food_price"));
                        dishJo.put("goods_id",
                                JsonUtil.getString(detailJo, "wm_food_id"));
                        dishJo.put("goods_price", JsonUtil.getString(detailJo,
                                "origin_food_price"));
                        dishesJa.add(dishJo);
                    }
                }
            }
            fixJo.put("dishes", dishesJa);
        }
        fixJo.put("boxPrice", JsonUtil.getString(jo, "boxPriceTotal"));
        fixJo.put("orderPrice",
                chargeJo.getInteger("foodAmount")
                        - jo.getInteger("boxPriceTotal"));
        fixJo.put("state", "0");
        fixJo.put("merchantName", JsonUtil.getString(jo, "poi_name"));
        fixJo.put("platform_type", "mt");
        fixJo.put("consignee_name", JsonUtil.getString(jo, "recipient_name"));
        fixJo.put("consigneeAddress",
                JsonUtil.getString(jo, "recipient_address"));
        fixJo.put("distance", JsonUtil.getString(jo, "distance"));
        fixJo.put("remark", JsonUtil.getString(jo, "remark"));
        
        //        boolean riderPay = JsonUtil.getBoolean(chargeJo, "riderPay");
        //        if (riderPay) {
        //            fixJo.put("platform_dist_charge",
        //                    JsonUtil.getString(chargeJo, "shippingAmount"));
        //            fixJo.put("distribution_mode", "CROWD");
        //        }
        //        else {
        //            fixJo.put("merchant_dist_charge",
        //                    JsonUtil.getString(jo, "shipping_fee"));
        //            fixJo.put("distribution_mode", "NONE");
        //        }
        //        if (JsonUtil.isNotBlank(chargeJo.get("commisionDetails"))) {
        //            fixJo.put("serviceFee",
        //                    JsonUtil.getString(chargeJo.getJSONArray("commisionDetails")
        //                            .getJSONObject(0),
        //                            "chargeAmount"));
        //        }
        //        String status = JsonUtil.getString(jo, "status");
        if ("2".equals(status)) {
            fixJo.put("orderLatestStatus", "等待接单");
        }
        else if ("4".equals(status)) {
            fixJo.put("orderLatestStatus", "等待配送");
        }
        else if ("8".equals(status)) {
            fixJo.put("orderLatestStatus", "用户已确认收餐");
        }
        else if ("9".equals(status)) {
            fixJo.put("orderLatestStatus", "订单取消");
        }
        else {
            fixJo.put("orderLatestStatus", "等待配送");
        }
        fixJo.put("activities_subsidy_bymerchant", jo.getDouble("total_before")
                - jo.getDouble("total_after"));
        
        //20170508新增
        fixJo.put("orderType", JsonUtil.getString(jo, "orderCopyContent")
                .contains("预订单") ? "BOOKING" : "NORMAL");
        fixJo.put("merchant_activities_subsidies",
                JsonUtil.getString(chargeJo, "activityAmount"));
        String shippingType = JsonUtil.getString(chargeJo, "shippingType");
        if ("0000".equals(shippingType)) {
            fixJo.put("merchant_dist_charge",
                    JsonUtil.getString(chargeJo, "shippingAmount"));
        }
        else {
            fixJo.put("merchant_dist_charge", "0");
        }
        
        if ("1001".equals(shippingType)) {
            fixJo.put("platform_dist_charge",
                    JsonUtil.getString(chargeJo, "shippingAmount"));
            fixJo.put("order_dist_charge ",
                    JsonUtil.getString(chargeJo, "shippingAmount"));
        }
        else {
            fixJo.put("platform_dist_charge", "0");
            fixJo.put("order_dist_charge ", "0");
        }
        fixJo.put("serviceFee", JsonUtil.getString(chargeJo, "commisionAmount"));
        fixJo.put("settlement_amount",
                JsonUtil.getString(chargeJo, "settleAmount"));
        fixJo.put("distribution_mode",
                JsonUtil.getString(chargeJo, "shippingType"));
        if (JsonUtil.isNotBlank(jo.get("discounts"))) {
            double infocount = 0.0;
            JSONArray discounts = jo.getJSONArray("discounts");
            for (Object o : discounts) {
                JSONObject discountJo = (JSONObject)o;
                String info = JsonUtil.getString(discountJo, "info");
                infocount += Double.valueOf(info.substring(info.indexOf("￥") + 1));
            }
            fixJo.put("platform_activities_subsidies",
                    infocount - chargeJo.getDouble("activityAmount"));
        }
        else {
            fixJo.put("platform_activities_subsidies", 0);
        }
        //20170508新增
        return fixJo;
    }
    
    public boolean execute(JSONObject[] arg0, String arg1) throws Exception {
        return false;
    }
    
    public static void main(String[] args) {
        MtCheckData aa = new MtCheckData();
        String cookies = "JSESSIONID=1043u4au55euwpx8nf2c02gk1;Path=/;shopCategory=food;Path=/;Expires=Thu, 04-May-2017 05:03:24 GMT;wpush_server_url=wss://wpush.meituan.com;Path=/;device_uuid_rebuild=;Path=/;Expires=Thu, 01-Jan-1970 00:00:00 GMT;uuid_update=true;Path=/;Expires=Tue, 03-May-2022 03:03:24 GMT;device_uuid=!7009afc1-e892-4562-9404-77fda8d2b3b3;Path=/;Expires=Tue, 03-May-2022 03:03:24 GMT;existBrandPoi=false;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;isChain=0;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;shopCategory=food;Path=/;Expires=Thu, 04-May-2017 04:03:24 GMT;city_id=430100;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;isOfflineSelfOpen=0;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;selfOpenWmPoiList=[];Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;wmPoiId=2030425;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;brandId=-1;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;token=0rbug-Aoz0Yl4pqSu2-8DRujX3W-iUXBXM1Sn396fJSY*;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;acctId=24990412;Path=/;Expires=Sat, 03-Jun-2017 03:03:24 GMT;wpush_server_url=wss://wpush.meituan.com;Path=/;";
        String queryStr = "?getNewVo=1&wmOrderPayType=2&wmOrderStatus=-2&sortField=1&lastLabel=&nextLabel=&_token="
                + aa.getValue(cookies, "token");
        queryStr = queryStr + "&startDate=" + "2017-05-03" + "&endDate="
                + "2017-05-03";
        String result = HttpRequest.sendGet2(UrlUtil.mtQuery + queryStr,
                cookies);
        log.info(result);
        //        String resul1 = HttpRequest.sendGet2("https://waimaie.meituan.com/finance/v2/finance/orderChecking/export/download/meituan_waimai_file_bill_export-2017-05-04-690320.xls",
        //                cookies);
        //        log.info(resul1);
        
        JSONObject retJo = JSON.parseObject(result);
        JSONArray orderListJa = retJo.getJSONArray("wmOrderList");
        JSONArray chargeJa = new JSONArray();
        for (Object o : orderListJa) {
            JSONObject eachData = (JSONObject)o;
            String status = JsonUtil.getString(eachData, "status");
            
            /**
             * "status": 2 --未接单状态
                "status": 4 --已接单
                "status": 9 --订单取消(用户主动取消、超时未接单取消)
                08--15 大概7分钟未接单将取消订单
             */
            //            if ("9".equals(status)) {
            //                continue;
            //            }
            Map<String, String> chargeInfo = HttpRequest.sendPost1(UrlUtil.MT_CI,
                    "chargeInfo=[{wmOrderViewId:"
                            + JsonUtil.getString(eachData, "wm_order_id_view")
                            + ",wmPoiId:"
                            + JsonUtil.getString(eachData, "wm_poi_id") + "}]",
                    cookies);
            log.info("chargeInfo--------------------->result:"
                    + chargeInfo.get("result"));
            
            String chargeRet = chargeInfo.get("result");
            JSONObject chargeJo = JSON.parseObject(chargeRet);
            //            if (JsonUtil.isNotBlank(chargeJo.get("data"))) {
            //                retJa.add(fixData(eachData, chargeJo.getJSONArray("data")
            //                        .getJSONObject(0), username));
            //            }
            chargeJa.add(chargeJo);
        }
        log.info(chargeJa.toString());
    }
    
}
