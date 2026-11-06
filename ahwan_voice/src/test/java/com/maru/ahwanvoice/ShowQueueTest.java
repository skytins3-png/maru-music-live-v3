package com.maru.ahwanvoice;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShowQueueTest {
    @Test public void skipsMissingAndDoesNotLoopAtEnd() {
        boolean[] ready={false,true,false,false,true,false};
        assertEquals(1,ShowQueue.nextAvailable(ready,0));
        assertEquals(4,ShowQueue.nextAvailable(ready,2));
        assertEquals(-1,ShowQueue.nextAvailable(ready,5));
        assertEquals(2,ShowQueue.registeredCount(ready));
    }
    @Test public void emptyQueueStopsWithoutTtsFallback() {
        boolean[] missing={false,false,false,false,false,false};
        assertEquals(-1,ShowQueue.nextAvailable(missing,0));
        assertEquals(0,ShowQueue.registeredCount(missing));
    }
    @Test public void allSixAvailableInOrder() {
        boolean[] all={true,true,true,true,true,true};
        for(int i=0;i<6;i++)assertEquals(i,ShowQueue.nextAvailable(all,i));
        assertEquals(6,ShowQueue.registeredCount(all));
        assertEquals(-1,ShowQueue.nextAvailable(all,6));
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsWrongQueueSize(){ShowQueue.nextAvailable(new boolean[2],0);}
    @Test public void noNegativeArrayIndex(){assertEquals(0,ShowQueue.nextAvailable(new boolean[]{true,false,false,false,false,false},-9));}
}
