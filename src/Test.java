/* Nathaniel Lim
 * 4/1/2012 
 * Threaded Resource Problem
 * 
 * This class creates bunch of ResourceUsers, initializes them
 * with an open ResourcePool, and lets the ResourceUser threads run.
 * Then blocks until the ResourcePool can be closed. 
 * 
 * 
 */
import java.util.ArrayList;
public class Test {
	private static final int numUsers = 10;
	
	public static void main (String [] args) throws InterruptedException{
		ResourcePool<String> rp = new ResourcePool<String>();
		String r1 = "one";
		String r2 = "two";
		String r3 = "three";
		String r4 = "four";
		rp.open();
		
		ArrayList<ResourceUser<String>> users = new ArrayList<ResourceUser<String>>();
		
		for (int i = 0; i < numUsers; i++){
			users.add(new ResourceUser<String>(i+1, rp));
		}
		for (ResourceUser<String> ru: users){
			ru.start();
		}
		//rp.close();
		Thread.sleep(3*1000);
		rp.add(r1);
		rp.add(r2);
		rp.add(r3);
		rp.add(r4);
		
		Thread.sleep(1*1000);
		rp.closeNow();
		
	}

}
