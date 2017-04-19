package com.dongnao.core.eleme.intf;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.dongnao.core.intf.OrderHandlerIntf;
import com.taobao.pamirs.schedule.IScheduleTaskDealMulti;
import com.taobao.pamirs.schedule.TaskItemDefine;

/** 
 * @Description 饿了么订单处理线程模板 
 * @ClassName   OrderHandler 
 * @Date        2017年3月20日 下午4:34:26 
 * @Author      luoyang 
 */
@Component("orderHandler")
public class OrderHandler implements IScheduleTaskDealMulti<String> {
    
    OrderHandlerIntf intf;
    
    public OrderHandlerIntf getIntf() {
        return intf;
    }
    
    public void setIntf(OrderHandlerIntf intf) {
        this.intf = intf;
    }
    
    private static transient Logger log = LoggerFactory.getLogger(OrderHandler.class);
    
    public Comparator<String> getComparator() {
        return null;
    }
    
    public List<String> selectTasks(String arg0, String arg1, int arg2,
            List<TaskItemDefine> arg3, int arg4) throws Exception {
        
        log.info("-----------------selectTasks-param----------------" + arg0);
        
        //        Map<String, OrderHandlerIntf> beans = SpringContextHolder.getWebApplicationContext()
        //                .getBeansOfType(OrderHandlerIntf.class);
        //        
        //        for (Map.Entry<String, OrderHandlerIntf> entry : beans.entrySet()) {
        //            entry.getValue().execute(arg0);
        //        }
        intf.execute(arg0);
        return null;
    }
    
    public boolean execute(String[] arg0, String arg1) throws Exception {
        return false;
    }
}
