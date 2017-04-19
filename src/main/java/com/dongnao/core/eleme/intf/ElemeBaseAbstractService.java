package com.dongnao.core.eleme.intf;

import java.util.Arrays;
import java.util.Map;

public abstract class ElemeBaseAbstractService {
    
    public static String fixUrl(String url, String sig,
            Map<String, String> params) {
        String paramStr = concatParams(params);
        
        return url + "?" + paramStr + "&sig=" + sig;
    }
    
    private static String concatParams(Map<String, String> params2) {
        Object[] key_arr = params2.keySet().toArray();
        Arrays.sort(key_arr);
        String str = "";
        
        for (Object key : key_arr) {
            String val = params2.get(key);
            str += "&" + key + "=" + val;
        }
        
        return str.replaceFirst("&", "");
    }
}
