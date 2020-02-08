/*
 * Copyright 2001 Draagon Software LLC. All Rights Reserved.
 *
 * This software is the proprietary information of Draagon Software LLC.
 * Use is subject to license terms.
 */

package com.draagon.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Test the Expiring Cache
 * 
 * @see com.draagon.cache.Cache
 */
public class CacheTest
{
    @Test
    public void testCacheExpires() throws Exception {
        
        Cache<String,String> c = new Cache<String,String>( true, 1, 1 );
        
        c.put( "key", "value" );
        assertEquals( "value", c.get("key"));
        
        // Wait 2 seconds
        Thread.sleep( 2000 );
        
        assertNull( "value no longer exists", c.get("key"));
    }
    
    @Test
    @Ignore("From main method, needs asserts")
    public void testCache() throws Exception {
    
        Cache<Long, String> c1 = new Cache<Long, String>(true, 5, 20);
        Cache<Long, String> c2 = new Cache<Long, String>(false, 2, 5);

        c2.put(1L, "One");
        c1.put(100L, "One Hundred");
        System.out.println("ONE: " + c1 + " - " + c2);
        Thread.sleep(2000L);

        c2.put(2L, "Two");
        c1.put(200L, "Two Hundred");
        System.out.println("TWO: " + c1 + " - " + c2);
        Thread.sleep(2000L);

        c2.put(3L, "Three");
        c1.put(300L, "Three Hundred");
        System.out.println("THREE: " + c1 + " - " + c2);
        Thread.sleep(2000L);

        c2.put(4L, "Four");
        c1.put(400L, "Four Hundred");
        System.out.println("FOUR: " + c1 + " - " + c2);
        Thread.sleep(2000L);

        // Let's see what happens!
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000L);
            System.out.println("WAIT: " + c1 + " - " + c2);
        }

        c2.put(5L, "Five");
        System.out.println("FIVE: " + c1 + " - " + c2);
        Thread.sleep(2000L);

        // Let's see what happens!
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000L);
            System.out.println("WAIT: " + c1 + " - " + c2);
        }
    }    
}
