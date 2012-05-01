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
	Queue<R> availableResources = new LinkedList<R>();
	Queue<R> resourcesInUse = new LinkedList<R>();
	private boolean open = false;
	final Lock lock = new ReentrantLock();
	final Condition resourceAvailable = lock.newCondition(); 
	final Condition resourceToRemove = lock.newCondition();
	final Condition noResourcesInUse = lock.newCondition();

	public void open(){
		open = true;
	}
	
	public boolean isOpen(){
		return open;
	}
	
	//Needs to block
	public void close() throws InterruptedException {
		if (!open) return;
		lock.lock();
		if (!resourcesInUse.isEmpty()){
			System.out.println("Waiting to close the Resource Pool");
			noResourcesInUse.await();
		}
		open = false;
		System.out.println("Closed the Resource Pool");
		lock.unlock();
	}
	
	public void closeNow() {
		open = false;
		System.out.println("Closed Resource Pool");
	}
	
	
	private void waitForResource() throws InterruptedException {
		while(availableResources.isEmpty()){
			resourceAvailable.await();
		}
		return;
	}
	private boolean waitForResource(long timeout, TimeUnit timeUnit) throws InterruptedException {
		if (availableResources.isEmpty()){
			return resourceAvailable.await(timeout, timeUnit);
		} else {
			return true;
		}
	}
	
	private R getAvailableResource() {
		if (!isOpen()) return null;
		 R r = availableResources.poll();
		 if (r!=null) resourcesInUse.add(r);
		 return r;
	}
	
	@SuppressWarnings("finally")
	public R acquire(){
		if (!isOpen()) return null;
		lock.lock();
		R output = null;
		try {
			waitForResource();
			output = getAvailableResource();
			
		} catch(InterruptedException e) {
			System.out.println("Thread was Interrupted Waiting to Acquire Lock or Resource");
		} finally {
			lock.unlock();
			return output;
		}
	}
	
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
			System.out.println("Thread was Interrupted Waiting to Acquire Lock or Resource");
		} finally {
			if (haveLock) lock.unlock();
			return output;
		}
	
	}
	
	public void release(R resource) {
		lock.lock();
		if(resourcesInUse.remove(resource) && availableResources.add(resource)){
			resourceAvailable.signal();
			if (resourcesInUse.isEmpty()) noResourcesInUse.signal();
		}
		lock.unlock();
	}
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
	
	//doesn't block
	public boolean removeNow(R resource) {
		lock.lock();
		boolean didRemove = false;
		if (!availableResources.isEmpty()){
			didRemove = availableResources.remove(resource);
		}
		lock.unlock();
		return didRemove;
		
	}
	
	//Should block until available to remove
	public boolean remove(R resource) throws InterruptedException {
		lock.lock();
		while(!availableResources.contains(resource)){
			System.out.printf("Waiting to remove resource: %s\n", resource);
			resourceAvailable.await();
		}
		boolean didRemove =  availableResources.remove(resource);
		if (didRemove) System.out.printf("Removed resource: %s\n", resource);
		lock.unlock();
		return didRemove;
	}
}
