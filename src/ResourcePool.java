/* Nathaniel Lim
 * 4/1/2012
 * Threaded Resource Pool Problem
 * 
 * The ResourcePool is parameterized by the resource type R.
 * Allows for Thread-safe acquiring and releasing a collection of R resources,
 * adding a resource, removing a resource, opening and closing the ResourcePool
 *
 */

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;
import java.util.Queue;


public class ResourcePool<R> {
	// Acquiring a resource will be O(1), just pop off head of queue, since
	// the Thread doesn't care what resource it gets
	Queue<R> availableResources = new LinkedList<R>();
	// Releasing a resource will be O(n), we need to linearly search to find
	// the resource that we are making available, Can't really hash resources.
	// (may have same hashcode())
	Queue<R> resourcesInUse = new LinkedList<R>();
	private boolean open = false;
	//The mutex we use whenever we need to manipulate the resource queues.
	final Lock lock = new ReentrantLock();
	// Await for and Signal when a new resource is available
	final Condition resourceAvailable = lock.newCondition(); 
	// Await for and Signal when a new 
	final Condition noResourcesInUse = lock.newCondition();

	//  Open the ResourcePool
	public void open(){
		open = true;
	}
	
	//  Tests if the ResourcePool is open
	public boolean isOpen(){
		return open;
	}
	
	/* If closed return
	 * Block until there are no more resourcesInUse
	 * Close the ResourcePool
	 */
	public void close() {
		if (!open) return;
		try {
			lock.lock();
			if (!resourcesInUse.isEmpty()){
				System.out.println("Waiting to close the ResourcePool");
				noResourcesInUse.await();
			} 
			open = false;
			System.out.println("Closed the ResourcePool");
		} catch (InterruptedException e) {
			System.err.println("Interrupted while trying to close ResourcePool");
		} finally {
			lock.unlock();
		}
	}
	
	/*  Doesn't wait for resourcesInUse to be emptied.
	 *  Simply Closes the ResourcePool and 
	 */ 
	public void closeNow() {
		open = false;
		System.out.println("Closed Resource Pool");
	}
	
	// Private method blocking until there are available resources
	private void waitForResource() throws InterruptedException {
		while(availableResources.isEmpty()){
			resourceAvailable.await();
		}
		return;
	}
	
	/* Private method returning when their are available resources within some timeout (milliseconds)
	 * checking with the time unit
 	 */
	private boolean waitForResource(long timeout, TimeUnit timeUnit) throws InterruptedException {
		if (availableResources.isEmpty()){
			return resourceAvailable.await(timeout, timeUnit);
		} else {
			return true;
		}
	}
	
	/* Private method that pops off a resource from the availableResources Queue
	 * Not Thread-safe it itself, only called in Thread-safe public methods
	 */
	private R getAvailableResource() {
		if (!isOpen()) return null;
		 R r = availableResources.poll();
		 if (r!=null) resourcesInUse.add(r);
		 return r;
	}
	
	
	/* Returns if the ResourcePool isn't open
	 * Blocks until there is an available resource
	 */
	@SuppressWarnings("finally")
	public R acquire(){
		if (!isOpen()) return null;
		lock.lock();
		R output = null;
		try {
			waitForResource();
			output = getAvailableResource();
		} catch(InterruptedException e) {
			System.err.println("Thread was Interrupted Waiting to Acquire Lock or Resource");
		} finally {
			lock.unlock();
			return output;
		}
	}
	
	/*  Returns if the ResourcePool is closed
	 *  Trys to get a resource within a timeout
	 *  
	 *  Time elapses waiting for the lock, decrement the timeout
	 *  by the amount of time the Thread spend waiting in tryLock
	 *  
	 *  Blocks until there is available resource within a new timeout
	 */
	@SuppressWarnings("finally")
	public R acquire(long timeout, TimeUnit timeUnit){
		if(!open) {
			System.out.println("Oh no the resource pool is closed");
			return null;
		}
		R output = null;
		long tryLockStart = System.currentTimeMillis();
		long getLockTime;
		boolean haveLock = false;
		try {
			haveLock = lock.tryLock(timeout, timeUnit);
			if (haveLock){
				getLockTime = System.currentTimeMillis() - tryLockStart;
				timeout -= getLockTime;
				if(waitForResource(timeout, timeUnit)){
					output = getAvailableResource();
				} 
			} 
		} catch (InterruptedException e) {
			System.err.println("Thread was Interrupted Waiting to Acquire Lock or Resource");
		} finally {
			if (haveLock) lock.unlock();
			return output;
		}
	
	}
	
	/* Release the resource back to the ResourcePool
	 * Doesn't care if the ResourcePool is closed
	 * Closed means resources can't be acquired,
	 * But they can be released (returned)
	 * 
	 * Check if there are no resourcesInUse and signal Threads trying to
	 * close the ResourcePool
	 */
	public void release(R resource) {
		lock.lock();
		if(resourcesInUse.remove(resource) && availableResources.add(resource)){
			resourceAvailable.signalAll();
			if (resourcesInUse.isEmpty()) noResourcesInUse.signal();
		}
		lock.unlock();
	}
	
	/*
	 * Add a resource to the availableResources Queue
	 * Signal any Thread waiting on an available resource
	 */
	public boolean add(R resource) {
		lock.lock();
		boolean didAdd;
		if (didAdd = availableResources.add(resource)){
			System.out.printf("Added resource: %s\n", resource);
			resourceAvailable.signal();
		}
		lock.unlock();
		return didAdd;
		
	}
	
	/*
	 * Immediately tests whether or not the resource is in use.
	 * If it is in use, can't remove it from the ResourcePool
	 * If it is not in use, remove it
	 */
	public boolean removeNow(R resource) {
		lock.lock();
		boolean didRemove = false;
		if (!availableResources.isEmpty()){
			didRemove = availableResources.remove(resource);
		}
		lock.unlock();
		return didRemove;
		
	}
	
	/* 
	 * If the resources isn't in the ResourcePool at all, return false
	 * Otherwise, waits around until it has removed a specific resource
	 * 
	 * Block until a new resource is added, checks its the one, if not blocks again
	 * If it is the one to be removed, then remove it.
	 * 
	 */
	@SuppressWarnings("finally")
	public boolean remove(R resource) {
		lock.lock();
		boolean didRemove = false;
		try {
			if(!availableResources.contains(resource) && !resourcesInUse.contains(resource)) return false;
			while(!availableResources.contains(resource)){
				System.out.printf("Waiting to remove resource: %s\n", resource);
				resourceAvailable.await();
			}
			didRemove =  availableResources.remove(resource);
			if (didRemove) System.out.printf("Removed resource: %s\n", resource);
		}catch (InterruptedException e) {
			System.err.printf("Interupted while waiting to remove %s\n", resource);
		} finally {
			lock.unlock();
		}
		return didRemove;
	}
}
