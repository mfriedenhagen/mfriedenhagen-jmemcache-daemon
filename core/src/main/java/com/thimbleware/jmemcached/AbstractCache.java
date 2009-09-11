package com.thimbleware.jmemcached;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

/**
 */
public abstract class AbstractCache {
    public final AtomicInteger curr_conns = new AtomicInteger();
    public final AtomicInteger total_conns = new AtomicInteger();
    public final AtomicInteger started = new AtomicInteger();          /* when the process was started */
    public final AtomicLong bytes_read = new AtomicLong();
    public final AtomicLong bytes_written = new AtomicLong();
    public final AtomicLong curr_bytes = new AtomicLong();
    protected AtomicInteger getCmds = new AtomicInteger();
    protected AtomicInteger setCmds = new AtomicInteger();
    protected AtomicInteger getHits = new AtomicInteger();
    protected AtomicInteger getMisses = new AtomicInteger();
    protected AtomicLong casCounter = new AtomicLong(1);

    /**
     * Initialize base values for status.
     */
    {
        curr_bytes.set(0);
        curr_conns.set(0);
        total_conns.set(0);
        bytes_read.set(0);
        bytes_written.set(0);
        started.set(Now());
    }

    public AbstractCache() {

    }

    /**
     * @return the current time in seconds (from epoch), used for expiries, etc.
     */
    protected final int Now() {
        return (int) (System.currentTimeMillis() / 1000);
    }

    public abstract Set<String> keys();

    public abstract long getCurrentItems();

    public abstract long getLimitMaxBytes();

    public abstract long getCurrentBytes();


    public int getGetCmds() {
        return getCmds.get();
    }

    public int getSetCmds() {
        return setCmds.get();
    }

    public int getGetHits() {
        return getHits.get();
    }

    public int getGetMisses() {
        return getMisses.get();
    }

    /**
     * Return runtime statistics
     *
     * @param arg additional arguments to the stats command
     * @return the full command response
     */
    public Map<String, Set<String>> stat(String arg) {
        Map<String, Set<String>> result = new HashMap<String, Set<String>>();

        if ("keys".equals(arg)) {
            for (String key : this.keys()) {
                multiSet(result, "key", key);
            }

            return result;
        }

        // stats we know
        multiSet(result, "version", MemCacheDaemon.memcachedVersion);
        multiSet(result, "cmd_gets", java.lang.String.valueOf(getGetCmds()));
        multiSet(result, "cmd_sets", java.lang.String.valueOf(getSetCmds()));
        multiSet(result, "get_hits", java.lang.String.valueOf(getGetHits()));
        multiSet(result, "get_misses", java.lang.String.valueOf(getGetMisses()));
        multiSet(result, "curr_connections", java.lang.String.valueOf(curr_conns));
        multiSet(result, "total_connections", java.lang.String.valueOf(total_conns));
        multiSet(result, "time", java.lang.String.valueOf(java.lang.String.valueOf(Now())));
        multiSet(result, "uptime", java.lang.String.valueOf(Now() - this.started.intValue()));
        multiSet(result, "cur_items", java.lang.String.valueOf(this.getCurrentItems()));
        multiSet(result, "limit_maxbytes", java.lang.String.valueOf(this.getLimitMaxBytes()));
        multiSet(result, "current_bytes", java.lang.String.valueOf(this.getCurrentBytes()));
        multiSet(result, "free_bytes", java.lang.String.valueOf(Runtime.getRuntime().freeMemory()));

        // Not really the same thing precisely, but meaningful nonetheless. potentially this should be renamed
        multiSet(result, "pid", java.lang.String.valueOf(Thread.currentThread().getId()));

        // stuff we know nothing about; gets faked only because some clients expect this
        multiSet(result, "rusage_user", "0:0");
        multiSet(result, "rusage_system", "0:0");
        multiSet(result, "connection_structures", "0");

        // TODO we could collect these stats
        multiSet(result, "bytes_read", "0");
        multiSet(result, "bytes_written", "0");

        return result;
    }

    private void multiSet(Map<String, Set<String>> map, String key, String val) {
        Set<String> cur = map.get(key);
        if (cur == null) {
            cur = new HashSet<String>();
        }
        cur.add(val);
        map.put(key, cur);
    }

    /**
     * Initialize all statistic counters
     */
    protected void initStats() {
        getCmds.set(0);
        setCmds.set(0);
        getHits.set(0);
        getMisses.set(0);
    }

}