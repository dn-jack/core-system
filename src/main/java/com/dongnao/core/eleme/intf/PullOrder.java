package com.dongnao.core.eleme.intf;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import com.dongnao.core.util.ElemeHelper;
import com.dongnao.core.util.HttpRequest;

/** 
 * @Description 饿了么拉取订单接口 
 * @ClassName   PullOrder 
 * @Date        2017年2月8日 上午11:47:29 
 * @Author      luoyang 
 */
public class PullOrder extends ElemeBaseAbstractService implements ElemeService {
    
    private static String url = "order/pull/new/";
    
    private static String consumerKey = "0170804777";
    
    private static String consumerSecret = "87217cb263701f90316236c4df00d9352fb1da76";
    
    //    private static String restaurant_id = "62028381";
    
    public Comparator<Long> getComparator() {
        return null;
    }
    
    /**
     * http://v2.openapi.ele.me/
     * consumer_key: 0170804777
       consumer_secret: 87217cb263701f90316236c4df00d9352fb1da76

       restaurant_id: 62028381
        restaurant_name: 饿了么开放平台测试
                                餐厅下单测试地址: https://www.ele.me/shop/25381

                                注意：如遇到 "测试餐厅必须与测试帐号一起使用" 的错误提示，请清理cookies 
     */
    public String execute(String param) throws Exception {
        Map<String, String> params = new HashMap<String, String>();
        params.put("consumer_key", consumerKey);
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        //        params.put("restaurant_id", restaurant_id);
        
        String sig = ElemeHelper.genSig(ElemeHelper.COMMON_URL + url,
                params,
                consumerSecret);
        
        String requestUrl = fixUrl(ElemeHelper.COMMON_URL + url, sig, params);
        
        String retStr = HttpRequest.sendGet(requestUrl);
        
        return retStr;
    }
    
    public static void main(String[] args) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("consumer_key", consumerKey);
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));
        //        params.put("restaurant_id", "62028381");
        
        String sig = "";
        try {
            sig = ElemeHelper.genSig(ElemeHelper.COMMON_URL + url,
                    params,
                    consumerSecret);
        }
        catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        String requestUrl = fixUrl(ElemeHelper.COMMON_URL + url, sig, params);
        
        String retStr = HttpRequest.sendGet(requestUrl);
        
        System.out.println(retStr);
    }
    
}
