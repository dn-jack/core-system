import org.quartz.SchedulerException;


public class HelloQuartzScheduling<E> {

	public static void main(String[] args)throws SchedulerException {
		HelloQuartzScheduling aa = new HelloQuartzScheduling();
//        SchedulerFactory schedulerFactory = new StdSchedulerFactory();
//        Scheduler scheduler = schedulerFactory.getScheduler();
//
//        JobDetail jobDetail = new JobDetail("helloQuartzJob", 
//                Scheduler.DEFAULT_GROUP, HelloQuartz.class);
//
//        SimpleTrigger simpleTrigger = new SimpleTrigger("simpleTrigger", 
//                Scheduler.DEFAULT_GROUP);
//
//        simpleTrigger.setStartTime(new Date(System.currentTimeMillis()));
//        simpleTrigger.setRepeatInterval(5000);
//        simpleTrigger.setRepeatCount(10);
//
//        scheduler.scheduleJob(jobDetail, simpleTrigger);
//
//        scheduler.start();
    }
	
}
