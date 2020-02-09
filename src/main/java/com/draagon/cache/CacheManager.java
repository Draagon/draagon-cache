package com.draagon.cache;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The CacheManager is used to flush out the Cache object's entries. Based on
 * the times spent
 * 
 * @author Doug
 * 
 */
public final class CacheManager implements Runnable {
    
    private final static Log log = LogFactory.getLog(CacheManager.class);
    
    private final static long SLEEP = 1000L * 60L * 60L;
    
    private final class CacheWrap {
        
        private long sweepTime = 0L;
        private Cache<?, ?> cache;

        public CacheWrap(Cache<?, ?> c) {
            cache = c;
            updateSweepTime();
        }

        public Cache<?, ?> getCache() {
            return cache;
        }

        public final long getSweepTime() {
            return sweepTime;
        }

        public final void updateSweepTime() {
            long delay = (long) cache.getCheckSeconds() * 1000L;
            sweepTime = System.currentTimeMillis() + delay;
        }
    }

    private final List<CacheWrap> entities = new CopyOnWriteArrayList<CacheWrap>();
    // private boolean stop = true;

    private static CacheManager instance;
    
    private final Thread thread;
    
    private CacheManager() {
        thread = new Thread( instance );
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        thread.setName("CacheManager");    
    }
    
    private void start() {
        thread.start();
    }

    /**
     * Retrieves the 1 and only instance of the CacheManager
     */
    static synchronized CacheManager getInstance() {
        if (instance == null) { // || !mHandler.isAlive() ) 
            instance = new CacheManager();
            instance.start();
        }
        return instance;
    }

    /**
     * Used to start the cache manager thread.
     */
    @Override
    public void run() {
        // stop = false;
        // if ( DEBUG ) System.out.println( "#CACHE# Cache handler starting..." );

        while (true) {
            
            CacheWrap next = null;
            CacheWrap sweepNext = null;

            // Sleeps for 1 hour by default
            long sleepDelay = SLEEP;

            // Get the next entity if one exists
            synchronized (entities) {
                if (entities.isEmpty()) {
                    sleepDelay = SLEEP;
                } else {
                    next = entities.get(0);

                    // If we should sweep, then do it
                    if (next.getSweepTime() <= System.currentTimeMillis()) {
                        sweepNext = next;
                    } else {
                        sleepDelay = next.getSweepTime() - System.currentTimeMillis();
                    }
                }
            }

            // Flush it and continue
            if (sweepNext != null) {
                if (log.isDebugEnabled()) log.debug("--- FLUSHING");
                sweepNext.getCache().flush();
                sweepNext.updateSweepTime();
                insertByTime(sweepNext);
                continue;
            }

            // If we didn't seep, then let's sleep
            try {
                if (log.isDebugEnabled()) log.debug("--- SLEEPING (" + sleepDelay + ")");
                if (sleepDelay > 0)
                    Thread.sleep(sleepDelay);
            } catch (InterruptedException e) {
                if (log.isDebugEnabled()) log.debug("### INTERRUPT");
                // Better check the first one if we get interrupted
            }
        }

        // ystem.out.println( "--- STOPPING" );

        // if ( DEBUG ) System.out.println( "#CACHE# Cache handler stopping..." );
    }

    /**
     * Inserts a Cache by it's timing order
     *
     * @param cw CacheWrap used to extract the time
     */
    protected void insertByTime(CacheWrap cw) {

        synchronized( entities ) {

            int startSize = entities.size();

            if (entities.contains(cw)) entities.remove(cw);

            if (cw.getCache().size() == 0) return;

            // Cache<?,?> c = cw.getCache();
            long time = cw.getSweepTime();

            for (int i = 0; i < entities.size(); i++) {
                if (entities.get(i).getSweepTime() >= time) {
                    entities.add(i, cw);
                    if (i == 0) {
                        if (log.isDebugEnabled()) log.debug(">>>>> Send INTERRUPT - Inserted into front spot");
                        thread.interrupt();
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("--- INSERT INTO POS (" + i + ")");
                        log.debug("--- ENTITIES: " + entities);
                    }
                    return;
                }
            }

            entities.add(cw);

            // Wake up a sleeping thread
            if (startSize == 0) {
                if (log.isDebugEnabled()) log.debug(">>>>> Send INTERRUPT - Added the only cache item in the system");
                thread.interrupt();
            }
        }
    }

    // /**
    // * Used to stop the cache manager thread
    // */
    // public void end()
    // {
    // stop = true;
    // }

    /** 
     * Registers the Cache object with the CacheManager 
     * 
     * @return true if registered, false if already existed
     */
    synchronized boolean registerCache(Cache<?, ?> c) {
        
        synchronized( entities ) {
            if (!entities.contains(c)) {
                CacheWrap cw = new CacheWrap(c);
                insertByTime(cw);
                // if ( stop ) start();
                return true;
            } 
            else return false;
        }
    }

    /** Unregisters the Cache object */
    synchronized void unregisterCache(Cache<?, ?> c) {

        synchronized( entities ) {

            CacheWrap found = null;

            for (CacheWrap cw : entities) {
                if (cw.getCache() == c) {
                    found = cw;
                    break;
                }
            }

            if (found != null) {
                entities.remove(found);
                if (entities.size() == 0) {
                    if (log.isDebugEnabled()) log.debug(">>>>> Send INTERRUPT - Unregistered a Cache");
                    thread.interrupt();
                }
            }
        }
    }
}
