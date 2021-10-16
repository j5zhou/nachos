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
        private Condition list;

        public Gamelevel(Lock lock,int count,Condition list) {
            this.lock = lock;
            this.count = count;
            this.list = list;
        }

        public Lock getLock() {
            return lock;
        }

        public int getCount() {
            return count;
        }

        public Condition getList() {
            return list;
        }

        public void setLock(Lock lock) {
            this.lock = lock;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public void setList(Condition list) {
            this.list = list;
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
                return matchNumber;
            case abilityIntermediate:
                playLevel(Intermediate);
                return matchNumber;
            case abilityExpert:
                playLevel(Expert);
                return matchNumber;
            default:
                return -1;
        }
    }

    public void playLevel (Gamelevel level) {
        int inMatch = level.getCount();
//        System.out.println (KThread.currentThread().getName());
//        System.out.println (inMatch);
        if(inMatch == 1){
            level.getLock().acquire();
            level.getList().wakeAll();
            level.getLock().release();
            level.setCount(numInMatch);
            matchNumber++;
        }
        else{
            level.setCount(level.getCount()-1);
            level.getLock().acquire();
            level.getList().sleep();
            level.getLock().release();
        }
        return;
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

        // Assume join is not implemented, use yield to allow other
        // threads to run
        for (int i = 0; i < 10; i++) {
            KThread.currentThread().yield();
        }
    }

    public static void selfTest() {
        matchTest4();
    }
}
