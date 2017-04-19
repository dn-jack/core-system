
public class Test {

	public static void main(String[] args) {
		HelloQuartzScheduling<String> aa = new HelloQuartzScheduling<String>();
		
		aa.getClass().getTypeParameters();
		
		System.out.println(aa.getClass().getTypeParameters()[0].getName());
		
		System.out.println(aa.getClass().getTypeParameters()[0].getName().equals(Integer.class.getName()));
	}
}
