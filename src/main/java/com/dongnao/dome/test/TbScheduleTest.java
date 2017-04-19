package com.dongnao.dome.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.taobao.pamirs.schedule.IScheduleTaskDealMulti;
import com.taobao.pamirs.schedule.TaskItemDefine;

@Component("tbScheduleTest")
public class TbScheduleTest implements IScheduleTaskDealMulti<Long> {

	private static transient Logger log = LoggerFactory
			.getLogger(TbScheduleTest.class);

	public Comparator<Long> getComparator() {
		// TODO Auto-generated method stub
		return null;
	}

	public List<Long> selectTasks(String arg0, String arg1, int arg2,
			List<TaskItemDefine> arg3, int arg4) throws Exception {

		List<Long> list = new ArrayList<Long>();

		log.info("taskParameter : " + arg0);
		
		for (int i = 0; i < 10; i++) {
			list.add(Long.valueOf(i));
		}

		return list;
	}

	public boolean execute(Long[] arg0, String arg1) throws Exception {

		log.info("execute[" + arg0 + "]:" + arg1);

		return true;
	}

}
