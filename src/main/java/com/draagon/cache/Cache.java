/*
 * Copyright 2001 Draagon Software LLC. All Rights Reserved.
 *
 * This software is the proprietary information of Draagon Software LLC.
 * Use is subject to license terms.
 */
package com.draagon.cache;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The Cache works very similar to a HashMap in that you can insert and retrieve
 * objects from it. However, unlike a normal HashMap, the Cache will expire
 * entries from the Map as they become old. Eventually after a specified period
 * of time all entries would eventually be removed. The removal of entries is
 * handled by the CacheManager and the expiration times are specified in the
 * Constructor. The resetOnRead parameter means the last read time of the entry
 * will be reset when it has been accessed. Otherwise, the time will be when it
 * was first read, so it will be removed when the expiration times occurs. Only
 * use the resetOnRead true if you don't mind having data that could be stale
 * indefinitely if read often. Please read the constructor documentation for
 * more details.
 * 
 * @author Doug Mealing
 * 
 * @param <F> The class for the key
 * @param <E> The class for the cached value
 * 
 * @see com.draagon.util.cache.CacheManager
 */
public class Cache<F, E> implements Map<F, E> {

    private final static Log log = LogFactory.getLog(Cache.class);

    private ConcurrentHashMap<F, CacheEntry> entryMap;

    private volatile boolean resetCache = true;
    private final int timeoutSeconds;
    private final int checkSeconds;

    /**
     * This inner class provides the ability to enumerate the cache object. It
     * implements the Enumeration interface.
     */
    public class CacheEnumeration implements Enumeration<E> {
        private WeakReference<Cache<F, E>> cache = null;
        private Enumeration<CacheEntry> mElements;

        public CacheEnumeration(Cache<F, E> c) {
            cache = new WeakReference<Cache<F, E>>(c);
            mElements = c.entryMap.elements();
        }

        public boolean hasMoreElements() {
            return mElements.hasMoreElements();
        }

        public E nextElement() {
            CacheEntry tmp = (CacheEntry) mElements.nextElement();
            if (tmp == null)
                return null;
            return tmp.getValue();
        }

        public String toString() {
            return cache.get().toString();
        }
    }

    /* End of inner class CacheEnumeration */

    /*
     * This inner class stores each of the entries in the cache
     */
    public class CacheEntry implements Map.Entry<F, E> {
        
        public final F key;
        public volatile E value;
        public volatile long timestamp;

        public CacheEntry(F key, E value) {

            if (key == null)
                throw new IllegalArgumentException("You may not have a null key in a Cache object");

            this.key = key;
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        public synchronized String toString() {
            if (value == null)
                return "";
            return value.toString();
        }

        public F getKey() {
            return key;
        }

        public E getValue() {
            return value;
        }

        public synchronized E setValue(E value) {

            E oldVal = this.value;
            this.value = value;
            return oldVal;
        }

        @Override
        public int hashCode() {
            String s = "##CACHE##" + key.toString() + ":" + value;
            return s.hashCode();
        }

        @Override
        public synchronized boolean equals(Object o) {

            if (o == null)
                return false;
            if (!(o.getClass().isAssignableFrom(CacheEntry.class)))
                return false;
            CacheEntry ce = (CacheEntry) o;
            if (!ce.getKey().equals(getKey()))
                return false;
            if (ce.getValue() == null && getValue() == null)
                return true;
            if (ce.getValue() == null || !ce.getValue().equals(getValue()))
                return false;
            return true;
        }
    }

    /* End of the CacheItem inner cache */

    /**
     * This creates the cache specifing the check value and the element timeout
     * value. The initial size and loading of the cache HashTable are set to
     * default vaules of '1'.
     * 
     * @param reset Whether a cache item's expiration is reset after a get call
     * @param checkSeconds Number of seconds between the timeout check cycles
     * @param timeoutSeconds Number of seconds before and inactive object times out.
     */
    public Cache(boolean reset, int checkSeconds, int timeoutSeconds) {
        this(reset, checkSeconds, timeoutSeconds, 1);
    }

    /* End of constructor() method */

    /**
     * Create the cache specifing the check value, the element timeout, the
     * initial map size, and whether to reste on a read.
     * 
     * @param resetOnRead Whether a cache item's expiration is reset after a get call
     * @param checkSeconds Number of seconds between timeout check cycles.
     * @param timeoutSeconds  Number of seconds before an inactive object times out.
     * @param initialCapacity Number of entries to initially put in the HashMap.
     */
    // * @param growth Number of elements to grow the cache by when it needs to
    // resize
    public Cache(boolean resetOnRead, int checkSeconds, int timeoutSeconds, int initialCapacity) {
        
        this.resetCache = resetOnRead;
        this.checkSeconds = checkSeconds;
        this.timeoutSeconds = timeoutSeconds;
        this.entryMap = new ConcurrentHashMap<F, CacheEntry>(initialCapacity);
        
        // Work around for dead-lock issue when first initializing the CacheManager
        CacheManager.getInstance();
    }

    // End of constructors

    /**
     * This sets the state of whether to reset the cache when the get() method
     * is call. If the state is set to false, then a get() request will not
     * reset the counter for the item in the cache, which means it will timeout
     * after the specified interval. If set to true, then each time the get()
     * method is called, it will reset the timer to prevent a timeout.
     * 
     * @param boolean Set the state of the reset cache
     */
    public void setResetCache(boolean state) {
        resetCache = state;
    }

    /**
     * Retrieves the state of whether to refresh the cache.
     * 
     * @return the state of the reset cache flag
     */
    public boolean getResetCache() {
        return resetCache;
    }

    /**
     * Returns the number of seconds to allow an object to be inactive before
     * flushing it from the cache.
     * 
     * @return <code>int</code> - object timeout value in seconds
     */
    public int getTOSeconds() {
        return timeoutSeconds;
    }

    /* End of getToSeconds() method */

    /**
     * Returns the number of seconds between inactivity checks
     * 
     * @return <code>int</code> - inactivity check cycle in seconds
     */
    public int getCheckSeconds() {
        return checkSeconds;
    }

    /* End of getCheckSeconds method */

    /**
     * Clears the cache of any inactive objects
     */
    public void flush() {
        synchronized( entryMap ) {
            // Determine whether to actually flush the rooms or not
            if (log.isDebugEnabled())
                log.debug("#CACHE# Flushing cache...");

            // Enumerate through the rooms and remove the empty ones
            for (F key : entryMap.keySet()) {
                flush(key);
            }
        }
    }

    /* End of flush() method */

    /**
     * Clears the cache of the specified object if it is expired
     * 
     * @param key The key of the item to flush
     */
    private void flush(Object key) {
        CacheEntry tmp = (CacheEntry) entryMap.get(key);
        if (tmp == null)
            return;

        long t = System.currentTimeMillis();
        long timeout = getTOSeconds() * 1000;

        if (tmp.timestamp + timeout < t) {
            if (log.isDebugEnabled())
                log.debug("#CACHE# Removing item " + key + ": " + tmp.timestamp + "-" + t);
            remove(key);
        }
    }

    /**
     * Caches the passed item, identifying it by the passed key value.
     * 
     * @param Object Key object used to identify the property
     * @param Object Value object used to hold the property value
     * 
     * @return <code>Object</code> - A cache object containing the key and value
     *         objects
     */
    public E put(F key, E value) {
        
        if (key == null) return null;

        if (log.isDebugEnabled())
            log.debug("#CACHE# adding item " + key + ": " + value);

        CacheEntry item = new CacheEntry(key, value);
        item.timestamp = System.currentTimeMillis();
        CacheEntry tmp = (CacheEntry) entryMap.put(key, item);
        if (entryMap.size() == 1)
            startHandler();
        if (tmp != null)
            return tmp.getValue();
        else
            return null;
    }

    /* End of put( Object, Object ) method */

    /**
     * Retrieves the cached item specified by the passed key object. If the
     * reset cache flag is set to true, it will also reset the timestamp to
     * prevent the item from timing out.
     * 
     * @param Object Key object used to identify the property
     * 
     * @return <code>Object</code> - A cache object containing the key and value
     *         objects
     */
    public E get(Object key) {
        CacheEntry tmp = getEntry(key);
        if (tmp == null)
            return null;

        // Only reset the timestamp if the reset cache flag is true
        if (resetCache)
            tmp.timestamp = System.currentTimeMillis();

        return tmp.getValue();
    }

    /* End of get( Object ) method */

    /**
     * Retrieves the cached item specified by the passed key object. If the
     * reset cache flag is set to true, it will also reset the timestamp to
     * prevent the item from timing out.
     * 
     * @param Object Key object used to identify the property
     * 
     * @return <code>Object</code> - A cache object containing the key and value
     *         objects
     */
    public CacheEntry getEntry(Object key) {
        if (key == null)
            return null;

        if (log.isDebugEnabled())
            log.debug("#CACHE# getting item " + key);

        // Flush the key if it exists
        flush(key);

        CacheEntry tmp = (CacheEntry) entryMap.get(key);
        if (tmp == null)
            return null;

        // Only reset the timestamp if the reset cache flag is true
        if (resetCache)
            tmp.timestamp = System.currentTimeMillis();

        return tmp;
    }

    /* End of get( Object ) method */

    /**
     * Removes the cached item specified by the passed key object from the cache
     * 
     * @param Object Key object used to identify the property
     * 
     * @return <code>Object</code> - A cache object containing the key and value
     *         objects
     */
    public E remove(Object key) {
        if (key == null)
            return null;

        if (log.isDebugEnabled())
            log.debug("#CACHE# removing item " + key);

        CacheEntry tmp = (CacheEntry) entryMap.remove(key);
        if (entryMap.size() == 0)
            stopHandler();
        if (tmp != null)
            return tmp.getValue();
        else
            return null;
    }

    /* End of remove( Object ) method */

    /**
     * Removes all objects from the Cache
     */
    public void removeAll() {
        entryMap.clear();
        stopHandler();
    }

    /* Used to get the cache handler up and going */
    private synchronized void startHandler() {
        // Register this Cache
        if (log.isDebugEnabled()) log.debug("#CACHE# register");
        CacheManager.getInstance().registerCache(this);
    }

    /* Used to terminate the Cache Handler thread */
    private synchronized void stopHandler() {
        if (log.isDebugEnabled()) log.debug("#CACHE# unregister");
        CacheManager.getInstance().unregisterCache(this);
    }

    /**
     * Returns the current size of the cache HashTable
     * 
     * @return <code>int</code> - The number of items in cache
     */
    public int size() {
        return entryMap.size();
    }

    /* End of size() method */

    /**
     * Returns an enumeration object for the cache table allowing traversing of
     * all cache items.
     * 
     * @return <code>Enumeration</code> - Enumeration object for the cache table
     */
    // @SuppressWarnings("unchecked")
    public Enumeration<E> elements() {
        return new CacheEnumeration(this);
    }

    /* End of elements() method */

    /**
     * Returns an enumeration object for the cache table allowing traversing of
     * all keys.
     * 
     * @return <code>Enumeration</code> - Enumeration object for the keys
     */
    public Enumeration<F> keys() {
        return entryMap.keys();
    }

    public void clear() {
        entryMap.clear();
    }

    public boolean containsKey(Object key) {
        return entryMap.containsKey(key);
    }

    public boolean isEmpty() {
        return entryMap.isEmpty();
    }

    public Set<F> keySet() {
        return entryMap.keySet();
    }

    public void putAll(Map<? extends F, ? extends E> t) {

        for (F key : t.keySet()) {
            E val = t.get(key);
            put(key, val);
        }
    }

    public Set<Map.Entry<F, E>> entrySet() {
        return new HashSet<Map.Entry<F, E>>( entryMap.values() );
    }

    public Collection<E> values() {

        List<E> result = new ArrayList<E>();
        for (Enumeration<E> e = elements(); e.hasMoreElements();) {
            result.add(e.nextElement());
        }
        return result;
    }

    public boolean containsValue(Object value) {

        for (Enumeration<E> e = elements(); e.hasMoreElements();) {
            Object v = e.nextElement();
            if (v.equals(value))
                return true;
        }
        return false;
    }

    public String toString() {
        return entryMap.toString();
    }
}
/* End of Cache class */
