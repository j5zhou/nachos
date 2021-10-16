package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private Queue<Wait> threadQ;

	private static class Wait{
		private KThread kthread;
		private long waketime;

		public void setKThread(KThread kthread){
			this.kthread = kthread;
		}

		public KThread getKThread(){
			return this.kthread;
		}

		public void setWakeTime(long waketime){
			this.waketime = waketime;
		}

		public long getWakeTime(){
			return this.waketime;
		}
	}
	static Comparator<Wait> cmp = new Comparator<Wait>() {
		public int compare(Wait w1, Wait w2) {
			return (int)(w1.getWakeTime() - w2.getWakeTime());
		}
	};
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		this.threadQ = new PriorityQueue<Wait>(cmp);
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		long currentTime = Machine.timer().getTime();
		Wait recent = this.threadQ.peek();
		boolean intStatus = Machine.interrupt().disable();
		while (recent != null && recent.getWakeTime() <= currentTime){
			Wait now = this.threadQ.poll();
			KThread ready = now.getKThread();
			ready.ready();
			recent = this.threadQ.peek();
		}
		Machine.interrupt().restore(intStatus);
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if(x<=0){
			return;
		}
		// for now, cheat just to get something working (busy waiting is bad)
//		long wakeTime = Machine.timer().getTime() + x;
//		while (wakeTime > Machine.timer().getTime())
//			KThread.yield();
		long wakeTime = Machine.timer().getTime() + x;
		Wait waitThread = new Wait();
		waitThread.setKThread(KThread.currentThread());
		waitThread.setWakeTime(wakeTime);

		this.threadQ.add(waitThread);
		boolean intStatus = Machine.interrupt().disable();
		KThread.sleep();
		Machine.interrupt().restore(intStatus);

	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 *
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) {
//		Iterator iter = this.threadQ.iterator();
//		while(iter.hasNext()){
//			if(iter.next().)
//		}
//		Wait[] threadqueue = (Wait[]) threadQ.toArray()
//		boolean intStatus = Machine.interrupt().disable();
		for (Wait w:threadQ){
			boolean intStatus = Machine.interrupt().disable();
			if (w.getKThread()!=null && w.getKThread() == thread){
				threadQ.remove(w);
				Machine.interrupt().restore(intStatus);
				return true;
			}
			Machine.interrupt().restore(intStatus);
		}
//		Machine.interrupt().restore(intStatus);
		return false;
	}

	// Add Alarm testing code to the Alarm class

	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;

		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// Implement more test methods here ...

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		alarmTest1();

		// Invoke your other test methods here ...

	}
}


