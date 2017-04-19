package com.dongnao.core.meituan.intf;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.dongnao.core.intf.OrderHandlerIntf;
import com.dongnao.core.util.HttpRequest;
import com.dongnao.core.util.HttpUtil;
import com.dongnao.core.util.HttpsRequestUtil;
import com.dongnao.core.util.JsonUtil;
import com.dongnao.core.util.MeituanUtil;
import com.dongnao.core.util.SpringContextHolder;
import com.dongnao.core.util.UrlUtil;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;

@Service
public class MeituanOrderHandler implements OrderHandlerIntf {
    
    private static transient Logger log = LoggerFactory.getLogger(MeituanOrderHandler.class);
    
    private Map<String, String> cache = new HashMap<String, String>();
    
    public String execute(String param) throws Exception {
        
        JSONObject tbParamJo = JSON.parseObject(param);
        
        if (!cache.containsKey(tbParamJo.getString("userName"))) {
            log.info("step1--------------------->invoke");
            JSONObject step1Ret = step1(tbParamJo.getString("userName"),
                    tbParamJo.getString("password"));
            
            if (step1Ret.containsKey("Location")) {
                log.info("step2--------------------->invoke");
                Map<String, String> step2Ret = step2(step1Ret.getString("Location"));
                if (step2Ret.containsKey("BSID")
                        && step2Ret.containsKey("entryList")
                        && step2Ret.containsKey("device_uuid")) {
                    log.info("step3--------------------->invoke");
                    step3(step2Ret, tbParamJo.getString("userName"));
                }
            }
        }
        
        String getStr = "?time=" + System.currentTimeMillis()
                + "&isQuery=0&getNewVo=1";
        log.info("ofq--------------------->queryString:" + getStr);
        
        String getret = HttpRequest.sendGet(UrlUtil.MT_QO + getStr,
                cache.get(tbParamJo.getString("userName")));
        log.info("ofq--------------------->result:" + getret);
        
        JSONObject orderJo = JSON.parseObject(getret);
        if (JsonUtil.isNotBlank(orderJo.get("data"))) {
            JSONArray dataJa = orderJo.getJSONArray("data");
            for (Object o : dataJa) {
                JSONObject eachData = (JSONObject)o;
                
                log.info("chargeInfo--------------------->queryString:"
                        + getStr);
                
                Map<String, String> chargeInfo = HttpRequest.sendPost1(UrlUtil.MT_CI,
                        "chargeInfo=[{wmOrderViewId:"
                                + JsonUtil.getString(eachData,
                                        "wm_order_id_view") + ",wmPoiId:"
                                + JsonUtil.getString(eachData, "wm_poi_id")
                                + "}]",
                        cache.get(tbParamJo.getString("userName")));
                log.info("chargeInfo--------------------->result:"
                        + chargeInfo.get("result"));
                
                String chargeRet = chargeInfo.get("result");
                JSONObject chargeJo = JSON.parseObject(chargeRet);
                if (JsonUtil.isNotBlank(chargeJo.get("data"))) {
                    insertOrderToMongodb(eachData,
                            chargeJo.getJSONArray("data").getJSONObject(0),
                            tbParamJo.getString("userName"));
                }
            }
        }
        
        return null;
    }
    
    private void insertOrderToMongodb(JSONObject jo, JSONObject chargeJo,
            String userName) {
        MongoTemplate mt = (MongoTemplate)SpringContextHolder.getWebApplicationContext()
                .getBean("mongoTemplate");
        DBCollection dbc = mt.getCollection("dn_order");
        BasicDBObject cond1 = new BasicDBObject();
        cond1.put("orderNo", JsonUtil.getString(jo, "wm_order_id_view_str"));
        DBCursor cursor = dbc.find(cond1);
        if (!cursor.hasNext()) {
            mt.insert(fixData(jo, chargeJo, userName).toString(), "dn_order");
        }
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
        fixJo.put("orderTime",
                dateParse(Long.parseLong(JsonUtil.getLong(jo, "order_time")
                        + "000")));
        fixJo.put("orderNo", JsonUtil.getString(jo, "wm_order_id_view_str"));
        fixJo.put("userName", JsonUtil.getString(jo, "recipient_name"));
        fixJo.put("phone", JsonUtil.getString(jo, "recipient_phone"));
        fixJo.put("merchantId", userName);
        
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
        fixJo.put("orderPrice", JsonUtil.getString(jo, "total_before"));
        fixJo.put("state", "0");
        fixJo.put("merchantName", JsonUtil.getString(jo, "poi_name"));
        fixJo.put("platform_type", "mt");
        fixJo.put("consignee_name", JsonUtil.getString(jo, "recipient_name"));
        fixJo.put("consigneeAddress",
                JsonUtil.getString(jo, "recipient_address"));
        fixJo.put("distance", JsonUtil.getString(jo, "distance"));
        fixJo.put("remark", JsonUtil.getString(jo, "remark"));
        
        boolean riderPay = JsonUtil.getBoolean(chargeJo, "riderPay");
        if (riderPay) {
            fixJo.put("platform_dist_charge",
                    JsonUtil.getString(chargeJo, "shippingAmount"));
            fixJo.put("distribution_mode", "CROWD");
        }
        else {
            fixJo.put("merchant_dist_charge",
                    JsonUtil.getString(jo, "shipping_fee"));
            fixJo.put("distribution_mode", "NONE");
        }
        if (JsonUtil.isNotBlank(chargeJo.get("commisionDetails"))) {
            fixJo.put("serviceFee",
                    JsonUtil.getString(chargeJo.getJSONArray("commisionDetails")
                            .getJSONObject(0),
                            "chargeAmount"));
        }
        String status = JsonUtil.getString(jo, "remark");
        if ("2".equals(status)) {
            fixJo.put("orderLatestStatus", "等待配送");
        }
        else if ("8".equals(status)) {
            fixJo.put("orderLatestStatus", "用户已确认收餐");
        }
        else {
            fixJo.put("orderLatestStatus", "等待配送");
        }
        fixJo.put("activities_subsidy_bymerchant", jo.getDouble("total_before")
                - jo.getDouble("total_after"));
        return fixJo;
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
    
    private Map<String, String> step3(Map<String, String> step2, String userName) {
        Map<String, String> retMap = HttpRequest.sendPost1(UrlUtil.MT_logon,
                "BSID=" + step2.get("BSID") + "&device_uuid="
                        + step2.get("device_uuid") + "&service=",
                step2.get("entryList"));
        log.info("step3--------------------->retMap:" + retMap);
        cache.put(userName, step2.get("entryList") + retMap.get("cookie"));
        insertMongodbByUserName(userName,
                step2.get("entryList") + retMap.get("cookie"));
        return retMap;
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
    
    public static void main(String[] args) {
        JSONObject jo = new JSONObject();
        jo.put("login", 1);
        jo.put("password", 1);
        jo.put("part_key", "");
        jo.put("captcha_code", "");
        jo.put("captcha_v_token", "");
        jo.put("sms_verify", 0);
        jo.put("sms_code", "");
        JSONObject ret = null;
        
        StringBuffer sb = new StringBuffer();
        sb.append("login=wmblyk279142")
                .append("&password=a12345")
                .append("&part_key=")
                .append("&captcha_code=")
                .append("&captcha_v_token=")
                .append("&sms_verify=" + 0)
                .append("&sms_code=");
        log.info(sb.toString());
        try {
            ret = HttpsRequestUtil.doPost1(MeituanUtil.loginurl
                    + "?service=waimai&continue=http://e.waimai.meituan.com/v2/epassport/entry&part_type=0&bg_source=3",
                    sb.toString(),
                    "UTF-8",
                    300000,
                    300000);
            log.info(ret.toString());
            
            String BSID = ret.getString("Location").split("\\?")[1].replace("BSID=",
                    "");
            log.info(BSID);
            
            List<String> entryList = HttpRequest.sendGet1(ret.getString("Location"));
            log.info(entryList.toString());
            
            String device_uuid = "";
            
            for (String ele : entryList) {
                if (ele.contains("device_uuid")) {
                    String replaceStr = ele.replace("device_uuid=", "");
                    device_uuid = replaceStr.substring(0,
                            replaceStr.indexOf(";"));
                }
            }
            log.info(device_uuid);
            StringBuffer sb1 = new StringBuffer();
            
            for (String entry : entryList) {
                sb1.append(entry).append(";");
            }
            Map<String, String> retMap = HttpRequest.sendPost1("http://e.waimai.meituan.com/v2/epassport/logon",
                    "BSID=" + BSID + "&device_uuid=" + device_uuid
                            + "&service=",
                    sb1.toString());
            log.info(retMap.toString());
            
            String getStr = "?time=" + System.currentTimeMillis()
                    + "&isQuery=0&getNewVo=1";
            log.info(getStr);
            log.info("--------" + sb1.toString() + retMap.get("cookie"));
            String getret = HttpUtil.doGet("http://e.waimai.meituan.com/v2/order/receive/unprocessed/r/ofq"
                    + getStr,
                    sb1.toString() + retMap.get("cookie"),
                    "");
            log.info(getret);
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
}
