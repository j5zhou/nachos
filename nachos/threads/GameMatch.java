package nachos.threads;

import nachos.machine.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;

    private Gamelevel Beginer;
    private Gamelevel Intermediate;
    private Gamelevel Expert;

    private Lock lockB;
    private Lock lockI;
    private Lock lockE;

    private int numInMatch;

    private int matchNumber;

    private class Gamelevel {

        private Lock lock;
        private int count;
        private int output;
        private Condition CV;
        private int matchNumber;
        private KThread lastThread;

        public Gamelevel(Lock lock,int count,Condition cv) {
            this.lock = lock;
            this.count = count;
            this.output = count;
            this.CV = cv;
            this.lastThread = null;
        }

        public Lock getLock() {
            return lock;
        }

        public int getCount() {
            return count;
        }

        public Condition getCV() {
            return CV;
        }

        public void setLock(Lock lock) {
            this.lock = lock;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void setCV(Condition cv) {
            this.CV = cv;
        }

        public int getMatchNumber() {
            return matchNumber;
        }

        public void setMatchNumber(int matchNumber) {
            this.matchNumber = matchNumber;
        }

        public KThread getLastThread() {
            return lastThread;
        }

        public void setLastThread(KThread lastThread) {
            this.lastThread = lastThread;
        }

        public int getOutput() {
            return output;
        }

        public void setOutput(int output) {
            this.output = output;
        }
    }

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
        lockB = new Lock();
        lockI = new Lock();
        lockE = new Lock();
        numInMatch = numPlayersInMatch;
        Beginer =new Gamelevel(lockB,numInMatch,new Condition(lockB));
        Intermediate =new Gamelevel(lockI,numInMatch,new Condition(lockI));
        Expert =new Gamelevel(lockE,numInMatch,new Condition(lockE));

        matchNumber = 0;
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     * 
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {
//        System.out.println (KThread.currentThread().getName());
	    switch (ability){
            case abilityBeginner:
                playLevel(Beginer);
                return Beginer.getMatchNumber();
            case abilityIntermediate:
                playLevel(Intermediate);
                return Intermediate.getMatchNumber();
            case abilityExpert:
                playLevel(Expert);
                return Expert.getMatchNumber();
            default:
                return -1;
        }
    }

    public void playLevel (Gamelevel level) {
        int inMatch = level.getCount();
//        System.out.println ("start"+KThread.currentThread().getName());
        KThread lastThread = level.getLastThread();
        if(inMatch == 1){
            level.setCount(numInMatch);
            level.getLock().acquire();
            level.getCV().wakeAll();
            level.getLock().release();
        }
        else{
            level.setCount(level.getCount()-1);
            level.getLock().acquire();
            level.getCV().sleep();
            level.getLock().release();
        }
        level.setLastThread(KThread.currentThread());
        if(lastThread != null){
            lastThread.join();
        }

        if(level.getOutput() == numInMatch){
            matchNumber++;
            level.setMatchNumber(matchNumber);
        }
        level.setOutput(level.getOutput()-1);
        if(level.getOutput() == 0) level.setOutput(numInMatch);
//        System.out.println ("end"+KThread.currentThread().getName());

    }

    // Place GameMatch test code inside of the GameMatch class.

    public static void matchTest4 () {
        System.out.println ("match test");
        final GameMatch match = new GameMatch(2);

        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg1 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg1.setName("B1");

        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg2.setName("B2");

        KThread beg3= new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                System.out.println ("beg2 matched");
                // beginners should match with a match number of 1
                Lib.assertTrue(r == 1, "expected match number of 1");
            }
        });
        beg3.setName("B3");

        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int1 should not have matched!");
            }
        });
        int1.setName("I1");

        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                Lib.assertNotReached("exp1 should not have matched!");
            }
        });
        exp1.setName("E1");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
        beg3.fork();

        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
            KThread.currentThread().yield();
        }
    }

    public static void matchTest5 () {
        System.out.println ("match test");
        final GameMatch match = new GameMatch(4);

        // Instantiate the threads
        KThread beg1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                Lib.assertNotReached("beg1 should not have matched!");
            }
        });
        beg1.setName("B1");

        KThread beg2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityBeginner);
                Lib.assertNotReached("beg2 should not have matched!");
            }
        });
        beg2.setName("B2");

        KThread int1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int1 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");

            }
        });
        int1.setName("I1");

        KThread exp1 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp1 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp1.setName("E1");

        KThread int2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int2 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        int2.setName("I2");

        KThread int3 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int3 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        int3.setName("I3");

        KThread int4 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                System.out.println ("int4 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        int4.setName("I4");

        KThread int5 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityIntermediate);
                Lib.assertNotReached("int5 should not have matched!");
            }
        });
        int5.setName("I5");

        KThread exp2 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp2 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp2.setName("E2");

        KThread exp3 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp3 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp3.setName("E3");

        KThread exp4 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp4 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp4.setName("E4");

        KThread exp5 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp5 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp5.setName("E5");

        KThread exp6 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp6 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp6.setName("E6");

        KThread exp7 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp7 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp7.setName("E7");

        KThread exp8 = new KThread( new Runnable () {
            public void run() {
                int r = match.play(GameMatch.abilityExpert);
                System.out.println ("exp8 matched");
                System.out.println ("match number:"+r);
                Lib.assertTrue(r == 1 || r== 2 || r== 3, "expected match number of 2 or 1 or 3");
            }
        });
        exp8.setName("E8");

        // Run the threads.  The beginner threads should successfully
        // form a match, the other threads should not.  The outcome
        // should be the same independent of the order in which threads
        // are forked.
        beg1.fork();
        int1.fork();
        exp1.fork();
        beg2.fork();
        int2.fork();
        int3.fork();
        int4.fork();
        int5.fork();
        exp2.fork();
        exp3.fork();
        exp4.fork();
        exp5.fork();
        exp6.fork();
        exp7.fork();
        exp8.fork();
        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 30; i++) {
            KThread.currentThread().yield();
        }
    }
    public static void selfTest() {
        matchTest4();
    }
}
