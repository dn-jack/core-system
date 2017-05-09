package com.dongnao.core.util;

/** 
 * @Description URL工具类 
 * @ClassName   UrlUtil 
 * @Date        2017年4月9日 下午2:55:05 
 * @Author      luoyang 
 */
public class UrlUtil {
    /**
     * 美团外卖url
     */
    public static String MT_QO = "http://e.waimai.meituan.com/v2/order/receive/unprocessed/r/ofq";
    
    public static String MT_CI = "http://e.waimai.meituan.com/v2/order/receive/r/chargeInfo";
    
    public static String MT_loginurl = "https://epassport.meituan.com/account/loginv2?service=waimai&continue=http://e.waimai.meituan.com/v2/epassport/entry&part_type=0&bg_source=3";
    
    public static String MT_logon = "http://e.waimai.meituan.com/v2/epassport/logon";
    
    public static String mtQuery = "https://waimaie.meituan.com/v2/order/history/r/query";
    
    /**
     * 百度外卖url
     */
    public static String BD_neworderlist = "https://wmcrm.baidu.com/crm?hhs=secure&qt=neworderlist&display=json";
    
    /**
     * 掉用商铺平台接口
     */
    public static String queryOrderUrl = "http://180.76.134.65:8090/merchant-system/order/queryOrder";
    
    public static String orderInsertBatchDb = "http://180.76.134.65:8090/merchant-system/order/orderInsertBatchDb";
    
    //"http://localhost:8080/merchant-system/order/orderInsertBatchDb";
    
    //"http://180.76.134.65:8090/merchant-system/order/orderInsertBatchDb";
    
    public static String queryShop = "http://180.76.134.65:8080/dncyz/store/listStoreByPlatform.do";
    
}
