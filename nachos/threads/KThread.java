package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 * 
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an argument
 * when creating <tt>KThread</tt>, and forked. For example, a thread that
 * computes pi could be written as follows:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * class PiRun implements Runnable {
 * 	public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre>
 * 
 * </blockquote>
 * <p>
 * The following code would then create a thread and start it running:
 * 
 * <p>
 * <blockquote>
 * 
 * <pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre>
 * 
 * </blockquote>
 */
public class KThread {
	/**
	 * Get the current thread.
	 * 
	 * @return the current thread.
	 */
	public static KThread currentThread() {
		Lib.assertTrue(currentThread != null);
		return currentThread;
	}

	/**
	 * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
	 * create an idle thread as well.
	 */
	public KThread() {
		if (currentThread != null) {
			tcb = new TCB();
		}
		else {
			readyQueue = ThreadedKernel.scheduler.newThreadQueue(false);
			readyQueue.acquire(this);

			currentThread = this;
			tcb = TCB.currentTCB();
			name = "main";
			restoreState();

			createIdleThread();
		}
	}

	/**
	 * Allocate a new KThread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 */
	public KThread(Runnable target) {
		this();
		this.target = target;
	}

	/**
	 * Set the target of this thread.
	 * 
	 * @param target the object whose <tt>run</tt> method is called.
	 * @return this thread.
	 */
	public KThread setTarget(Runnable target) {
		Lib.assertTrue(status == statusNew);

		this.target = target;
		return this;
	}

	/**
	 * Set the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @param name the name to give to this thread.
	 * @return this thread.
	 */
	public KThread setName(String name) {
		this.name = name;
		return this;
	}

	/**
	 * Get the name of this thread. This name is used for debugging purposes
	 * only.
	 * 
	 * @return the name given to this thread.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the full name of this thread. This includes its name along with its
	 * numerical ID. This name is used for debugging purposes only.
	 * 
	 * @return the full name given to this thread.
	 */
	public String toString() {
		return (name + " (#" + id + ")");
	}

	/**
	 * Deterministically and consistently compare this thread to another thread.
	 */
	public int compareTo(Object o) {
		KThread thread = (KThread) o;

		if (id < thread.id)
			return -1;
		else if (id > thread.id)
			return 1;
		else
			return 0;
	}

	/**
	 * Causes this thread to begin execution. The result is that two threads are
	 * running concurrently: the current thread (which returns from the call to
	 * the <tt>fork</tt> method) and the other thread (which executes its
	 * target's <tt>run</tt> method).
	 */
	public void fork() {
		Lib.assertTrue(status == statusNew);
		Lib.assertTrue(target != null);

		Lib.debug(dbgThread, "Forking thread: " + toString() + " Runnable: "
				+ target);

		boolean intStatus = Machine.interrupt().disable();

		tcb.start(new Runnable() {
			public void run() {
				runThread();
			}
		});

		ready();

		Machine.interrupt().restore(intStatus);
	}

	private void runThread() {
		begin();
		target.run();
		finish();
	}

	private void begin() {
		Lib.debug(dbgThread, "Beginning thread: " + toString());

		Lib.assertTrue(this == currentThread);

		restoreState();

		Machine.interrupt().enable();
	}

	/**
	 * Finish the current thread and schedule it to be destroyed when it is safe
	 * to do so. This method is automatically called when a thread's
	 * <tt>run</tt> method returns, but it may also be called directly.
	 * 
	 * The current thread cannot be immediately destroyed because its stack and
	 * other execution state are still in use. Instead, this thread will be
	 * destroyed automatically by the next thread to run, when it is safe to
	 * delete this thread.
	 */
	public static void finish() {
		Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());

		Machine.interrupt().disable();

		Machine.autoGrader().finishingCurrentThread();

		Lib.assertTrue(toBeDestroyed == null);
		toBeDestroyed = currentThread;

		currentThread.status = statusFinished;

		// deal with join operation
		if(currentThread.ParentThread !=null){
			currentThread.ParentThread.ready();
			System.out.println("The child Thread is ready again");
		}

		sleep();
	}

	/**
	 * Relinquish the CPU if any other thread is ready to run. If so, put the
	 * current thread on the ready queue, so that it will eventually be
	 * rescheuled.
	 * 
	 * <p>
	 * Returns immediately if no other thread is ready to run. Otherwise returns
	 * when the current thread is chosen to run again by
	 * <tt>readyQueue.nextThread()</tt>.
	 * 
	 * <p>
	 * Interrupts are disabled, so that the current thread can atomically add
	 * itself to the ready queue and switch to the next thread. On return,
	 * restores interrupts to the previous state, in case <tt>yield()</tt> was
	 * called with interrupts disabled.
	 */
	public static void  yield() {
		Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());

		Lib.assertTrue(currentThread.status == statusRunning);

		boolean intStatus = Machine.interrupt().disable();

		currentThread.ready();

		runNextThread();

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Relinquish the CPU, because the current thread has either finished or it
	 * is blocked. This thread must be the current thread.
	 * 
	 * <p>
	 * If the current thread is blocked (on a synchronization primitive, i.e. a
	 * <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
	 * some thread will wake this thread up, putting it back on the ready queue
	 * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
	 * scheduled this thread to be destroyed by the next thread to run.
	 */
	public static void sleep() {
		Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());

		if (currentThread.status != statusFinished)
			currentThread.status = statusBlocked;

		runNextThread();
	}

	/**
	 * Moves this thread to the ready state and adds this to the scheduler's
	 * ready queue.
	 */
	public void ready() {
		Lib.debug(dbgThread, "Ready thread: " + toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(status != statusReady);

		status = statusReady;
		if (this != idleThread)
			readyQueue.waitForAccess(this);

		Machine.autoGrader().readyThread(this);
	}

	/**
	 * Waits for this thread to finish. If this thread is already finished,
	 * return immediately. This method must only be called once; the second call
	 * is not guaranteed to return. This thread must not be the current thread.
	 */
	public void join() {
		Lib.debug(dbgThread, "Joining to thread: " + toString());

		Lib.assertTrue(this != currentThread,"Thread cannot call join on itself");

		// make sure only join for once
		Lib.assertTrue(joinCount == 1,"Thread can only be called on join once" );
		// joinCount should be 0 even if it is finished.
		joinCount = 0;
		if(this.status==4){
			return;
		}
		boolean intStatus = Machine.interrupt().disable();
		ParentThread = currentThread;
//		joinCount = 0;
		currentThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Create the idle thread. Whenever there are no threads ready to be run,
	 * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
	 * idle thread must never block, and it will only be allowed to run when all
	 * other threads are blocked.
	 * 
	 * <p>
	 * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
	 */
	private static void createIdleThread() {
		Lib.assertTrue(idleThread == null);

		idleThread = new KThread(new Runnable() {
			public void run() {
				while (true)
					KThread.yield();
			}
		});
		idleThread.setName("idle");

		Machine.autoGrader().setIdleThread(idleThread);

		idleThread.fork();
	}

	/**
	 * Determine the next thread to run, then dispatch the CPU to the thread
	 * using <tt>run()</tt>.
	 */
	private static void runNextThread() {
		KThread nextThread = readyQueue.nextThread();
		if (nextThread == null)
			nextThread = idleThread;

		nextThread.run();
	}

	/**
	 * Dispatch the CPU to this thread. Save the state of the current thread,
	 * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
	 * load the state of the new thread. The new thread becomes the current
	 * thread.
	 * 
	 * <p>
	 * If the new thread and the old thread are the same, this method must still
	 * call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
	 * <tt>restoreState()</tt>.
	 * 
	 * <p>
	 * The state of the previously running thread must already have been changed
	 * from running to blocked or ready (depending on whether the thread is
	 * sleeping or yielding).
	 * 
	 * finishing <tt>true</tt> if the current thread is finished, and
	 * should be destroyed by the new thread.
	 */
	private void  run() {
		Lib.assertTrue(Machine.interrupt().disabled());

		Machine.yield();

		currentThread.saveState();

		Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
				+ " to: " + toString());

		currentThread = this;

		tcb.contextSwitch();

		currentThread.restoreState();
	}

	/**
	 * Prepare this thread to be run. Set <tt>status</tt> to
	 * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
	 */
	protected void restoreState() {
		Lib.debug(dbgThread, "Running thread: " + currentThread.toString());

		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
		Lib.assertTrue(tcb == TCB.currentTCB());

		Machine.autoGrader().runningThread(this);

		status = statusRunning;

		if (toBeDestroyed != null) {
			toBeDestroyed.tcb.destroy();
			toBeDestroyed.tcb = null;
			toBeDestroyed = null;
		}
	}

	/**
	 * Prepare this thread to give up the processor. Kernel threads do not need
	 * to do anything here.
	 */
	protected void saveState() {
		Lib.assertTrue(Machine.interrupt().disabled());
		Lib.assertTrue(this == currentThread);
	}

	private static class PingTest implements Runnable {
		PingTest(int which) {
			this.which = which;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
				System.out.println("awesome *** thread " + which + " looped " + i
						+ " times");
				currentThread.yield();
			}
		}

		private int which;
	}

	/**
	 * Tests whether this module is working.
	 */
	public static void selfTest() {
		Lib.debug(dbgThread, "Enter KThread.selfTest");
//		joinTest4();
//		GameMatch.matchTest4();
//		Alarm.alarmTest1();
//		Condition2.selfTest();
//		cvTest5();
		GameMatch.matchTest5();

	}

	/**
	 * gien test example
	 */
	private static void joinTest1 () {
		KThread child1 = new KThread( new Runnable () {
			public void run() {
				System.out.println("I (heart) Nachos!");
			}
		});
		child1.setName("child1").fork();

		// We want the child to finish before we call join.  Although
		// our solutions to the problems cannot busy wait, our test
		// programs can!

		for (int i = 0; i < 5; i++) {
			System.out.println ("busy...");
			KThread.currentThread().yield();
		}

		child1.join();
		System.out.println("After joining, child1 should be finished.");
		System.out.println("is it? " + (child1.status == statusFinished));
		Lib.assertTrue((child1.status == statusFinished), " Expected child1 to be finished.");
	}

	/**
	 * no work
	 */
	private static void joinTest2 () {
		KThread child1 = new KThread( new Runnable () {
			public void run() {
				System.out.println("I (heart) Nachos!");
			}
		});
//		KThread child2 = new KThread( new Runnable () {
//			public void run() {
//				System.out.println("I (heart) Nachos!");
//			}
//		});
		child1.setName("child1").fork();
//		child2.setName("child2").fork();

		child1.join();
		for (int i = 0; i < 5; i++) {
			System.out.println("i = " + i);
			child1.currentThread().yield();
			System.out.println("child1 status = " + child1.status);
			System.out.println("currentThread status = " + currentThread.status);
		}

//		System.out.println("After joining, child1 should be finished.");
//		System.out.println("is it? " + (child1.status == statusFinished));
		Lib.assertTrue((child1.status == statusFinished), " Expected child1 to be finished.");
	}


	private static void joinTest3 () {
		KThread child1 = new KThread( new Runnable () {
			public void run() {

				System.out.println("I (heart) Nachos!");
				KThread child2 = new KThread( new Runnable () {
					public void run() {
						System.out.println("I (heart) Nachos2!");
					}
				});
				child2.setName("child2").fork();
				child2.yield();
				child2.join();
				child2.join();
			}
		});
		child1.setName("child1").fork();
	}


	public static void cvTest5() {
		final Lock lock = new Lock();
//		 final Condition empty = new Condition(lock);
		final Condition2 empty = new Condition2(lock);
		final LinkedList<Integer> list = new LinkedList<>();

		KThread consumer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				while(list.isEmpty()){
					empty.sleep();
				}
				Lib.assertTrue(list.size() == 5, "List should have 5 values.");
				while(!list.isEmpty()) {
					// context swith for the fun of it
					KThread.currentThread().yield();
					System.out.println("Removed " + list.removeFirst());
				}
				lock.release();
			}
		});

		KThread producer = new KThread( new Runnable () {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 5; i++) {
					list.add(i);
					System.out.println("Added " + i);
					// context swith for the fun of it
					KThread.currentThread().yield();
				}
				empty.wake();
				lock.release();
			}
		});

		consumer.setName("Consumer");
		producer.setName("Producer");
		consumer.fork();
		producer.fork();

		// We need to wait for the consumer and producer to finish,
		// and the proper way to do so is to join on them.  For this
		// to work, join must be implemented.  If you have not
		// implemented join yet, then comment out the calls to join
		// and instead uncomment the loop with yield; the loop has the
		// same effect, but is a kludgy way to do it.
		consumer.join();
		producer.join();
		//for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
	}

	private static void joinTest4() {
		KThread child4 = new KThread( new Runnable () {
			public void run() {
				System.out.println("the current running thread is :"+currentThread.toString());
				System.out.println("Thread 4 here");

				KThread child4_1 = new KThread( new Runnable () {
					public void run() {
						System.out.println("the current running thread is :"+currentThread.toString());
						System.out.println("Thread 4_1 here");
						for (int i = 0; i <10; i++) {
							System.out.println ("working on thread 4_1");
						}


					}
				});
				child4_1.setName("child4_1").fork();
				child4_1.join();

				for (int i = 0; i <10; i++) {
					System.out.println ("working on thread 4");
				}


			}
		});
		child4.setName("child4").fork();


		KThread child5 = new KThread( new Runnable () {
			public void run() {
				System.out.println("the current running thread is :"+currentThread.toString());
				System.out.println("Thread 5 here");
				for (int i = 0; i <20; i++) {
					System.out.println ("working on thread 5");
				}


				KThread child5_1 = new KThread( new Runnable () {
					public void run() {
						System.out.println("the current running thread is :"+currentThread.toString());
						System.out.println("Thread 5_1 here");
						for (int i = 0; i <10; i++) {
							System.out.println ("working on thread 5_1");
						}



					}
				});
				child5_1.setName("child3_1").fork();

				child5_1.join();

			}
		});
		child5.setName("child5").fork();


		System.out.println("before joining, the current thread is :"+currentThread.toString());
		System.out.println(currentThread.toString()+ "'s status: " + currentThread.status);

		//Lib.assertTrue((currentThread.toString() == "main (#0)"));
		child4.join();
		//Lib.assertTrue((currentThread.toString() == "main (#0)"));
		child5.join();


		System.out.println("after joining, the current thread is :"+currentThread.toString());

		System.out.println("After joining, child1 should not be finished. And switch to child first");
		System.out.println("is it? " + (child4.status == statusFinished));

		System.out.println("After done the child thread, switch back to the parent thread: \n current thread is : " + currentThread.toString());
		//Lib.assertTrue((child2.status == statusFinished), " Expected child1 to be Running.");


	}




	private static final char dbgThread = 't';

	/**
	 * Additional state used by schedulers.
	 * 
	 * @see nachos.threads.PriorityScheduler.ThreadState
	 */
	public Object schedulingState = null;

	private static final int statusNew = 0;

	private static final int statusReady = 1;

	private static final int statusRunning = 2;

	private static final int statusBlocked = 3;

	private static final int statusFinished = 4;

	public int getStatus() {
		return status;
	}

	/**
	 * The status of this thread. A thread can either be new (not yet forked),
	 * ready (on the ready queue but not running), running, or blocked (not on
	 * the ready queue and not running).
	 */
	private int status = statusNew;

	private String name = "(unnamed thread)";

	private Runnable target;

	private TCB tcb;

	/**
	 * Unique identifer for this thread. Used to deterministically compare
	 * threads.
	 */
	private int id = numCreated++;

	/** Number of times the KThread constructor was called. */
	private static int numCreated = 0;

	private static ThreadQueue readyQueue = null;

	private static KThread currentThread = null;

	private static KThread toBeDestroyed = null;

	private static KThread idleThread = null;

	// count the number of join
	private int joinCount = 1;

	// store father and child for join operation
//	private Map<KThread,KThread> joinMap = new HashMap<>();
	private  KThread ParentThread = null;
}
