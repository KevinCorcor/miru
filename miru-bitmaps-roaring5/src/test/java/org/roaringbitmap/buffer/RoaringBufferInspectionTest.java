package org.roaringbitmap.buffer;

import com.jivesoftware.os.miru.bitmaps.roaring5.buffer.MiruBitmapsRoaringBuffer;
import com.jivesoftware.os.miru.plugin.bitmap.CardinalityAndLastSetBit;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class RoaringBufferInspectionTest {

    @Test
    public void testCardinalityAndLastSetBit() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i * 37 < 5 * Short.MAX_VALUE; i++) {
            bitmap.add(i * 37);
            CardinalityAndLastSetBit cardinalityAndLastSetBit = RoaringBufferInspection.cardinalityAndLastSetBit(bitmap);
            assertEquals(cardinalityAndLastSetBit.cardinality, i + 1);
            assertEquals(cardinalityAndLastSetBit.lastSetBit, i * 37);
        }
    }

    @Test
    public void testBoundary() throws Exception {
        MiruBitmapsRoaringBuffer bitmaps = new MiruBitmapsRoaringBuffer();

        MutableRoaringBitmap bitmap = bitmaps.createWithBits(0);
        CardinalityAndLastSetBit cardinalityAndLastSetBit = RoaringBufferInspection.cardinalityAndLastSetBit(bitmap);

        System.out.println("cardinalityAndLastSetBit=" + cardinalityAndLastSetBit.lastSetBit);

        MutableRoaringBitmap remove = bitmaps.createWithBits(0);

        MutableRoaringBitmap answer = bitmaps.andNot(bitmap, remove);

        cardinalityAndLastSetBit = RoaringBufferInspection.cardinalityAndLastSetBit(answer);
        System.out.println("cardinalityAndLastSetBit=" + cardinalityAndLastSetBit.lastSetBit);

    }

    @Test
    public void testSizeInBits() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();

        assertEquals(RoaringBufferInspection.sizeInBits(bitmap), 0);
        bitmap.add(0);
        assertEquals(RoaringBufferInspection.sizeInBits(bitmap), 1 << 16);
        bitmap.add(1 << 16 - 1);
        assertEquals(RoaringBufferInspection.sizeInBits(bitmap), 1 << 16);
        bitmap.add(1 << 16);
        assertEquals(RoaringBufferInspection.sizeInBits(bitmap), 2 << 16);
        bitmap.add(2 << 16 - 1);
        assertEquals(RoaringBufferInspection.sizeInBits(bitmap), 2 << 16);
        bitmap.add(2 << 16);
        assertEquals(RoaringBufferInspection.sizeInBits(bitmap), 3 << 16);
    }

    @Test
    public void testCardinalityInBuckets_dense_uncontained() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i < 100_000; i++) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{0, 10_000, 20_000, 30_000, 40_000, 50_000, 60_000, 70_000, 80_000, 90_000, 100_000};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 10);
        for (long cardinalityInBucket : cardinalityInBuckets) {
            assertEquals(cardinalityInBucket, 10_000);
        }
    }

    @Test
    public void testCardinalityInBuckets_sparse_uncontained() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i < 100_000; i += 100) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{0, 10_000, 20_000, 30_000, 40_000, 50_000, 60_000, 70_000, 80_000, 90_000, 100_000};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 10);
        for (long cardinalityInBucket : cardinalityInBuckets) {
            assertEquals(cardinalityInBucket, 100);
        }
    }

    @Test
    public void testCardinalityInBuckets_dense_contained() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i < 131_072; i++) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{0, 65_536, 131_072};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 2);
        for (long cardinalityInBucket : cardinalityInBuckets) {
            assertEquals(cardinalityInBucket, 65_536);
        }
    }

    @Test
    public void testCardinalityInBuckets_sparse_contained() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i < 131_072; i += 128) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{0, 65_536, 131_072};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 2);
        for (long cardinalityInBucket : cardinalityInBuckets) {
            assertEquals(cardinalityInBucket, 512);
        }
    }

    @Test
    public void testCardinalityInBuckets_advance_outer() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i < 100_000; i++) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{40_000, 50_000, 60_000};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 2);
        for (long cardinalityInBucket : cardinalityInBuckets) {
            assertEquals(cardinalityInBucket, 10_000);
        }
    }

    @Test
    public void testCardinalityInBuckets_advance_inner() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 90_000; i < 100_000; i++) {
            bitmap.add(i);
        }
        bitmap.add(150_000);
        for (int i = 210_000; i < 220_000; i++) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{0, 80_000, 110_000, 200_000, 230_000, 300_000};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 5);
        assertEquals(cardinalityInBuckets[0], 0);
        assertEquals(cardinalityInBuckets[1], 10_000);
        assertEquals(cardinalityInBuckets[2], 1);
        assertEquals(cardinalityInBuckets[3], 10_000);
        assertEquals(cardinalityInBuckets[4], 0);
    }

    @Test
    public void testCardinalityInBuckets_same_buckets() throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        for (int i = 0; i < 10; i++) {
            bitmap.add(i);
        }
        int[] indexes = new int[]{2, 2, 3, 3, 4, 4, 5, 5, 6};
        long[] cardinalityInBuckets = new long[indexes.length - 1];
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexes, cardinalityInBuckets);
        assertEquals(cardinalityInBuckets.length, 8);
        assertEquals(cardinalityInBuckets[0], 0);
        assertEquals(cardinalityInBuckets[1], 1);
        assertEquals(cardinalityInBuckets[2], 0);
        assertEquals(cardinalityInBuckets[3], 1);
        assertEquals(cardinalityInBuckets[4], 0);
        assertEquals(cardinalityInBuckets[5], 1);
        assertEquals(cardinalityInBuckets[6], 0);
        assertEquals(cardinalityInBuckets[7], 1);
    }
}
