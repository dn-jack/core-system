package com.dongnao.core.meituan.intf;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.dongnao.core.intf.OrderHandlerIntf;
import com.taobao.pamirs.schedule.IScheduleTaskDealMulti;
import com.taobao.pamirs.schedule.TaskItemDefine;

@Service("meituanThreadTemp")
public class MeituanThreadTemp implements IScheduleTaskDealMulti<String> {
    
    OrderHandlerIntf intf;
    
    public OrderHandlerIntf getIntf() {
        return intf;
    }
    
    public void setIntf(OrderHandlerIntf intf) {
        this.intf = intf;
    }
    
    private static transient Logger log = LoggerFactory.getLogger(MeituanThreadTemp.class);
    
    public Comparator<String> getComparator() {
        return null;
    }
    
    public List<String> selectTasks(String arg0, String arg1, int arg2,
            List<TaskItemDefine> arg3, int arg4) throws Exception {
        log.info("-----------------selectTasks-param----------------" + arg0);
        
        intf.execute(arg0);
        return null;
    }
    
    public boolean execute(String[] arg0, String arg1) throws Exception {
        return false;
    }
}
