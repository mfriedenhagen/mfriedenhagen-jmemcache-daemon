package com.thimbleware.jmemcached.storage.bytebuffer;

import com.thimbleware.jmemcached.MCElement;
import com.thimbleware.jmemcached.Cache;
import com.thimbleware.jmemcached.storage.CacheStorage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Cache storage delegate for the memory mapped storage mechanism.
 */
public final class ByteBufferCacheStorage implements CacheStorage {
    private int maximumItems;
    private long ceilingBytes;
    private int curItems = 0;


    /**
     * Read-write lock allows maximal concurrency, since readers can share access;
     * only writers need sole access.
     */
    private final ReadWriteLock cacheReadWriteLock = new ReentrantReadWriteLock();
    
    /**
     * Blocks of code in which the contents of the cache
     * are examined in any way must be surrounded by calls to <code>startRead</code>
     * and <code>finishRead</code>. See documentation for ReadWriteLock.
     * @param cache
     */
    public void finishCacheRead(Cache cache) {
        cacheReadWriteLock.readLock().unlock();
    }

    /**
     * Blocks of code in which the contents of the cache
     * are examined in any way must be surrounded by calls to <code>startRead</code>
     * and <code>finishRead</code>. See documentation for ReadWriteLock.
     * @param cache
     */
    public void startCacheRead(Cache cache) {
        cacheReadWriteLock.readLock().lock();

    }

    /**
     * Blocks of code in which the contents of the cache
     * are changed in any way must be surrounded by calls to <code>startWrite</code> and
     * <code>finishWrite</code>. See documentation for ReadWriteLock.
     * protect the higher layers from implementation details.
     * @param cache
     */
    public void startCacheWrite(Cache cache) {
        cacheReadWriteLock.writeLock().lock();

    }

    /**
     * Blocks of code in which the contents of the cache
     * are changed in any way must be surrounded by calls to <code>startWrite</code> and
     * <code>finishWrite</code>. See documentation for ReadWriteLock.
     * @param cache
     */
    public void finishCacheWrite(Cache cache) {
        cacheReadWriteLock.writeLock().unlock();
    }

    class StoredValue {
        int flags;
        int expire;
        Region region;

        StoredValue(int flags, int expire, Region region) {
            this.flags = flags;
            this.expire = expire;
            this.region = region;
        }
    }

    private ByteBufferBlockStore store;
    private Map<String, StoredValue> index;

    public ByteBufferCacheStorage(final ByteBufferBlockStore store, int maximumItems, long ceilingBytes) {
        this.maximumItems = maximumItems;
        this.ceilingBytes = ceilingBytes;
        this.store = store;
        this.index = new LinkedHashMap<String, StoredValue>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, StoredValue> stringStoredValueEntry) {
                if (store.getFreeBytes() < getCeilingBytes() || curItems > getMaximumItems()) {
                    store.free(stringStoredValueEntry.getValue().region);
                    curItems--;
                    return true;
                } else return false;
            }
        };
    }

    public MCElement get(String keystring) {
        if (keystring == null) throw new IllegalArgumentException("Id must not be null.");

        StoredValue result = index.get(keystring);
        if (result == null) return null;
        
        MCElement el = new MCElement(keystring, result.flags, result.expire, result.region.size);
        el.data = store.get(result.region);

        return el;
    }

    public void put(String id, MCElement item, int dataLength) {
        if (id == null) throw new IllegalArgumentException("Id must not be null.");
        if (item == null) throw new IllegalArgumentException("Item must not be null.");

        // if the item already exists in the store, free it and replace it
        StoredValue val = index.get(id);
        if (val != null) {
            store.free(val.region);
        }

        Region region = store.alloc(dataLength, item.data);

        curItems++;
        index.put(id, new StoredValue(item.flags, item.expire, region));
    }

    public void remove(String keystring) {
        StoredValue item = index.get(keystring);

        if (item != null) {
            index.remove(keystring);
            store.free(item.region);
        }
        curItems--;
    }

    public Set<String> keys() {
        return index.keySet();
    }

    public void clear() {
        index.clear();
        store.clear();
        curItems = 0;
    }

    public void close() {
        clear();
        try {
            store.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public final long getCurrentSizeBytes() {
        return store.getStoreSizeBytes() - store.getFreeBytes();
    }

    public final int getMaximumItems() {
        return maximumItems;
    }

    public final long getMaximumSizeBytes() {
        return store.getStoreSizeBytes();
    }

    public final long getCurrentItemCount() {
        return index.size();
    }

    public final long getCeilingBytes() {
        return ceilingBytes;
    }
}