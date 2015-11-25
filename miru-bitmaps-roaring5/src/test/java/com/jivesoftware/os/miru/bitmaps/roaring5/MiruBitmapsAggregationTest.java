package com.jivesoftware.os.miru.bitmaps.roaring5;

import com.google.common.collect.Lists;
import com.jivesoftware.os.miru.plugin.bitmap.CardinalityAndLastSetBit;
import gnu.trove.list.array.TIntArrayList;
import java.util.List;
import org.roaringbitmap.RoaringBitmap;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MiruBitmapsAggregationTest {

    @Test
    public void testOr() throws Exception {
        MiruBitmapsRoaring bitmaps = new MiruBitmapsRoaring();
        List<RoaringBitmap> ors = Lists.newArrayList();
        int numBits = 10;
        for (int i = 0; i < numBits; i++) {
            RoaringBitmap or = bitmaps.createWithBits(i * 137);
            ors.add(or);
        }
        RoaringBitmap container = bitmaps.create();
        bitmaps.or(container, ors);
        for (int i = 0; i < numBits; i++) {
            assertFalse(bitmaps.isSet(container, i * 137 - 1));
            assertTrue(bitmaps.isSet(container, i * 137));
            assertFalse(bitmaps.isSet(container, i * 137 + 1));
        }
    }

    @Test
    public void testEmptyAnd() throws Exception {
        RoaringBitmap b1 = new RoaringBitmap();
        b1.add(1);
        b1.remove(1);
        RoaringBitmap b2 = RoaringBitmap.bitmapOf(1, 64*1024, 1024*1024, 64*1024*1024);

        b1.and(b2);
    }

    @Test
    public void testAnd() throws Exception {
        MiruBitmapsRoaring bitmaps = new MiruBitmapsRoaring();
        List<RoaringBitmap> ands = Lists.newArrayList();
        int numBits = 10;
        int andBits = 3;
        for (int i = 0; i < numBits - andBits; i++) {
            TIntArrayList bits = new TIntArrayList();
            for (int j = i + 1; j < numBits; j++) {
                bits.add(j * 137);
            }
            ands.add(bitmaps.createWithBits(bits.toArray()));
        }
        RoaringBitmap container = bitmaps.create();
        bitmaps.and(container, ands);
        for (int i = 0; i < numBits; i++) {
            if (i < (numBits - andBits)) {
                assertFalse(bitmaps.isSet(container, i * 137));
            } else {
                assertTrue(bitmaps.isSet(container, i * 137));
            }
        }
    }

    @Test
    public void testAndNot_2() throws Exception {
        MiruBitmapsRoaring bitmaps = new MiruBitmapsRoaring();
        int numOriginal = 10;
        int numNot = 3;
        TIntArrayList originalBits = new TIntArrayList();
        TIntArrayList notBits = new TIntArrayList();
        for (int i = 0; i < numOriginal; i++) {
            originalBits.add(i * 137);
            if (i < numNot) {
                notBits.add(i * 137);
            }
        }
        RoaringBitmap original = bitmaps.createWithBits(originalBits.toArray());
        RoaringBitmap not = bitmaps.createWithBits(notBits.toArray());
        RoaringBitmap container = bitmaps.create();
        bitmaps.andNot(container, original, not);
        for (int i = 0; i < numOriginal; i++) {
            if (i < numNot) {
                assertFalse(bitmaps.isSet(container, i * 137));
            } else {
                assertTrue(bitmaps.isSet(container, i * 137));
            }
        }
    }

    @Test
    public void testAndNot_multi() throws Exception {
        MiruBitmapsRoaring bitmaps = new MiruBitmapsRoaring();
        List<RoaringBitmap> nots = Lists.newArrayList();
        int numOriginal = 10;
        int numNot = 3;
        TIntArrayList originalBits = new TIntArrayList();
        for (int i = 0; i < numOriginal; i++) {
            originalBits.add(i * 137);
            if (i < numNot) {
                RoaringBitmap not = bitmaps.createWithBits(i * 137);
                nots.add(not);
            }
        }
        RoaringBitmap original = bitmaps.createWithBits(originalBits.toArray());
        RoaringBitmap container = bitmaps.create();
        bitmaps.andNot(container, original, nots);
        for (int i = 0; i < numOriginal; i++) {
            if (i < numNot) {
                assertFalse(bitmaps.isSet(container, i * 137));
            } else {
                assertTrue(bitmaps.isSet(container, i * 137));
            }
        }
    }

    @Test
    public void testAndNotWithCardinalityAndLastSetBit() throws Exception {
        MiruBitmapsRoaring bitmaps = new MiruBitmapsRoaring();
        int numOriginal = 10;
        int numNot = 3;
        TIntArrayList originalBits = new TIntArrayList();
        TIntArrayList notBits = new TIntArrayList();
        for (int i = 0; i < numOriginal; i++) {
            originalBits.add(i * 137);
            if (i < numNot) {
                notBits.add(i * 137);
            }
        }
        RoaringBitmap original = bitmaps.createWithBits(originalBits.toArray());
        RoaringBitmap not = bitmaps.createWithBits(notBits.toArray());
        RoaringBitmap container = bitmaps.create();
        CardinalityAndLastSetBit cardinalityAndLastSetBit = bitmaps.andNotWithCardinalityAndLastSetBit(container, original, not);
        for (int i = 0; i < numOriginal; i++) {
            if (i < numNot) {
                assertFalse(bitmaps.isSet(container, i * 137));
            } else {
                assertTrue(bitmaps.isSet(container, i * 137));
            }
        }
        assertEquals(cardinalityAndLastSetBit.cardinality, numOriginal - numNot);
        assertEquals(cardinalityAndLastSetBit.lastSetBit, (numOriginal - 1) * 137);
    }

    @Test
    public void testAndWithCardinalityAndLastSetBit() throws Exception {
        MiruBitmapsRoaring bitmaps = new MiruBitmapsRoaring();
        List<RoaringBitmap> ands = Lists.newArrayList();
        int numOriginal = 10;
        int numAnd = 3;
        for (int i = 0; i < numOriginal - numAnd; i++) {
            TIntArrayList andBits = new TIntArrayList();
            for (int j = i + 1; j < numOriginal; j++) {
                andBits.add(j * 137);
            }
            ands.add(bitmaps.createWithBits(andBits.toArray()));
        }
        RoaringBitmap container = bitmaps.create();
        CardinalityAndLastSetBit cardinalityAndLastSetBit = bitmaps.andWithCardinalityAndLastSetBit(container, ands);
        for (int i = 0; i < numOriginal; i++) {
            if (i < (numOriginal - numAnd)) {
                assertFalse(bitmaps.isSet(container, i * 137));
            } else {
                assertTrue(bitmaps.isSet(container, i * 137));
            }
        }
        assertEquals(cardinalityAndLastSetBit.cardinality, numAnd);
        assertEquals(cardinalityAndLastSetBit.lastSetBit, (numOriginal - 1) * 137);
    }

}
