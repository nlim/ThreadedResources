/* Nathaniel Lim
 * 4/1/2012 
 * Threaded Resource Problem
 * 
 * The ResourceUser class helps with testing the Threaded Resource Pool
 * 
 * A ResourceUser is initialized with a reference to the ResourcePool
 * It tries to acquire a resource within the waitMax number of seconds
 * Then it sleeps while it has the resource: random [1-useMax] seconds
 * Then it releases the resource.
 */

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ResourceUser<R> extends Thread {
	private ResourcePool<R> resourcePool;
	private int id;
	private final Random random = new Random();
	private static final int useMax = 10;
	private static final int waitMax = 5;
	public ResourceUser(int id, ResourcePool<R> resourcePool) {
		this.id = id;
		this.resourcePool = resourcePool;
	}
	
	public void run() {
		System.out.printf("ResourceUser #%d trying to acquire a resource\n", id);
		boolean didAcquire = false;
		R r = null;
		int seconds = 0;
		try {
			long started_wait = System.currentTimeMillis();
			r = resourcePool.acquire(waitMax*1000, TimeUnit.MILLISECONDS);
			long time_waited = System.currentTimeMillis() - started_wait;
			didAcquire = r != null;
			seconds = random.nextInt(useMax)+1;
			if (didAcquire) {
				System.out.printf("ResourceUser #%d acquired resource: %s for %d seconds\n", id, r, seconds);
				Thread.sleep(seconds*1000);
				resourcePool.release(r);
				System.out.printf("ResourceUser #%d released resource %s\n", id, r);
			} else {
				System.out.printf("ResourceUser #%d could not get a resource in %d milliseconds.\n", id, time_waited);
			}
		} catch (InterruptedException e) {
			System.out.printf("ResourceUser #%d was interrupted.\n", id);
		}
		
		
	}
}
