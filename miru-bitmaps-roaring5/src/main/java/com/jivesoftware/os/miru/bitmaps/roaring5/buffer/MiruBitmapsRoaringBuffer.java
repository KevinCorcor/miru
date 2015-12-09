/*
 * Copyright 2014 jivesoftware.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jivesoftware.os.miru.bitmaps.roaring5.buffer;

import com.google.common.base.Optional;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.jivesoftware.os.filer.io.FilerDataInput;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.filer.io.chunk.ChunkFiler;
import com.jivesoftware.os.miru.plugin.bitmap.CardinalityAndLastSetBit;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.bitmap.MiruIntIterator;
import com.jivesoftware.os.miru.plugin.index.IndexAlignedBitmapStream;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndex;
import com.jivesoftware.os.miru.plugin.index.MiruMultiTxIndex;
import com.jivesoftware.os.miru.plugin.index.MiruTimeIndex;
import com.jivesoftware.os.miru.plugin.index.MiruTxIndex;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import org.roaringbitmap.FastAggregation;
import org.roaringbitmap.IntIterator;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.buffer.BufferFastAggregation;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;
import org.roaringbitmap.buffer.RoaringBufferInspection;

/**
 * @author jonathan
 */
public class MiruBitmapsRoaringBuffer implements MiruBitmaps<MutableRoaringBitmap, ImmutableRoaringBitmap> {

    private boolean appendInPlace(MutableRoaringBitmap bitmap, int... indexes) {
        if (indexes.length == 1) {
            bitmap.add(indexes[0]);
        } else if (indexes.length > 1) {
            int rangeStart = 0;
            for (int rangeEnd = 1; rangeEnd < indexes.length; rangeEnd++) {
                if (indexes[rangeEnd - 1] + 1 != indexes[rangeEnd]) {
                    if (rangeStart == rangeEnd - 1) {
                        bitmap.add(indexes[rangeStart]);
                    } else {
                        bitmap.flip(indexes[rangeStart], indexes[rangeEnd - 1] + 1);
                    }
                    rangeStart = rangeEnd;
                }
            }
            if (rangeStart == indexes.length - 1) {
                bitmap.add(indexes[rangeStart]);
            } else {
                bitmap.flip(indexes[rangeStart], indexes[indexes.length - 1] + 1);
            }
        }
        return true;
    }

    @Override
    public MutableRoaringBitmap append(ImmutableRoaringBitmap bitmap, int... indexes) {
        MutableRoaringBitmap container = copy(bitmap);
        appendInPlace(container, indexes);
        return container;
    }

    @Override
    public MutableRoaringBitmap set(ImmutableRoaringBitmap bitmap, int... indexes) {
        MutableRoaringBitmap container = copy(bitmap);
        for (int index : indexes) {
            container.add(index);
        }
        return container;
    }

    @Override
    public MutableRoaringBitmap removeRange(MutableRoaringBitmap original, int rangeStartInclusive, int rangeEndExclusive) {
        return MutableRoaringBitmap.remove(original, rangeStartInclusive, rangeEndExclusive);
    }

    @Override
    public void inPlaceRemoveRange(MutableRoaringBitmap original, int rangeStartInclusive, int rangeEndExclusive) {
        original.remove(rangeStartInclusive, rangeEndExclusive);
    }

    @Override
    public MutableRoaringBitmap remove(ImmutableRoaringBitmap bitmap, int... indexes) {
        MutableRoaringBitmap container = copy(bitmap);
        for (int index : indexes) {
            container.remove(index);
        }
        return container;
    }

    @Override
    public boolean isSet(ImmutableRoaringBitmap bitmap, int i) {
        return bitmap.contains(i);
    }

    @Override
    public MutableRoaringBitmap extend(ImmutableRoaringBitmap bitmap, List<Integer> indexes, int extendToIndex) {
        MutableRoaringBitmap container = copy(bitmap);
        for (int index : indexes) {
            container.add(index);
        }
        return container;
    }

    @Override
    public void clear(MutableRoaringBitmap bitmap) {
        bitmap.clear();
    }

    @Override
    public long cardinality(ImmutableRoaringBitmap bitmap) {
        return bitmap.getCardinality();
    }

    @Override
    public void boundedCardinalities(ImmutableRoaringBitmap bitmap, int[] indexBoundaries, long[] rawWaveform) {
        RoaringBufferInspection.cardinalityInBuckets(bitmap, indexBoundaries, rawWaveform);
    }

    @Override
    public MutableRoaringBitmap create() {
        return new MutableRoaringBitmap();
    }

    @Override
    public MutableRoaringBitmap createWithBits(int... indexes) {
        return MutableRoaringBitmap.bitmapOf(indexes);
    }

    @Override
    public MutableRoaringBitmap[] createArrayOf(int size) {
        MutableRoaringBitmap[] bitmaps = new MutableRoaringBitmap[size];
        for (int i = 0; i < size; i++) {
            bitmaps[i] = new MutableRoaringBitmap();
        }
        return bitmaps;
    }

    @Override
    public boolean supportsInPlace() {
        return true;
    }

    @Override
    public void inPlaceOr(MutableRoaringBitmap original, ImmutableRoaringBitmap or) {
        original.or(or);
    }

    @Override
    public MutableRoaringBitmap or(Collection<ImmutableRoaringBitmap> bitmaps) {
        return BufferFastAggregation.or(bitmaps.iterator());
    }

    @Override
    public MutableRoaringBitmap orTx(List<MiruTxIndex<ImmutableRoaringBitmap>> indexes, StackBuffer stackBuffer) throws Exception {
        if (indexes.isEmpty()) {
            return new MutableRoaringBitmap();
        }

        MutableRoaringBitmap container = indexes.get(0).txIndex((bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                MutableRoaringBitmap mutable = new MutableRoaringBitmap();
                mutable.or(bitmap);
                return mutable;
            } else if (filer != null) {
                return bitmapFromFiler(filer, offset, stackBuffer1);
            } else {
                return new MutableRoaringBitmap();
            }
        }, stackBuffer);

        for (MiruTxIndex<ImmutableRoaringBitmap> index : indexes.subList(1, indexes.size())) {
            index.txIndex((bitmap, filer, offset, stackBuffer1) -> {
                if (bitmap != null) {
                    container.or(bitmap);
                } else if (filer != null) {
                    container.or(bitmapFromFiler(filer, offset, stackBuffer1));
                }
                return null;
            }, stackBuffer);
        }

        return container;
    }

    @Override
    public MutableRoaringBitmap orMultiTx(MiruMultiTxIndex<ImmutableRoaringBitmap> multiTermTxIndex, StackBuffer stackBuffer) throws Exception {
        MutableRoaringBitmap container = new MutableRoaringBitmap();
        multiTermTxIndex.txIndex((index, bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                container.or(bitmap);
            } else if (filer != null) {
                container.or(bitmapFromFiler(filer, offset, stackBuffer1));
            }
        }, stackBuffer);
        return container;
    }

    @Override
    public void inPlaceAnd(MutableRoaringBitmap original, ImmutableRoaringBitmap bitmap) {
        original.and(bitmap);
    }

    @Override
    public MutableRoaringBitmap and(Collection<ImmutableRoaringBitmap> bitmaps) {
        return BufferFastAggregation.and(bitmaps.iterator());
    }

    @Override
    public MutableRoaringBitmap andTx(List<MiruTxIndex<ImmutableRoaringBitmap>> indexes, StackBuffer stackBuffer) throws Exception {
        if (indexes.isEmpty()) {
            return new MutableRoaringBitmap();
        }

        MutableRoaringBitmap container = indexes.get(0).txIndex((bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                MutableRoaringBitmap mutable = new MutableRoaringBitmap();
                mutable.or(bitmap);
                return mutable;
            } else if (filer != null) {
                return bitmapFromFiler(filer, offset, stackBuffer1);
            } else {
                return new MutableRoaringBitmap();
            }
        }, stackBuffer);

        if (container.isEmpty()) {
            return container;
        }

        for (MiruTxIndex<ImmutableRoaringBitmap> index : indexes.subList(1, indexes.size())) {
            index.txIndex((bitmap, filer, offset, stackBuffer1) -> {
                if (bitmap != null) {
                    container.and(bitmap);
                } else if (filer != null) {
                    container.and(bitmapFromFiler(filer, offset, stackBuffer1));
                } else {
                    container.clear();
                }
                return null;
            }, stackBuffer);

            if (container.isEmpty()) {
                return container;
            }
        }

        return container;
    }

    @Override
    public void inPlaceAndNot(MutableRoaringBitmap original, ImmutableRoaringBitmap not) {
        original.andNot(not);
    }

    @Override
    public void inPlaceAndNot(MutableRoaringBitmap original,
        MiruInvertedIndex<MutableRoaringBitmap, ImmutableRoaringBitmap> not,
        StackBuffer stackBuffer) throws Exception {

        not.txIndex((bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                original.andNot(bitmap);
            } else if (filer != null) {
                original.andNot(bitmapFromFiler(filer, offset, stackBuffer1));
            }
            return null;
        }, stackBuffer);
    }

    @Override
    public MutableRoaringBitmap andNotMultiTx(MutableRoaringBitmap original,
        MiruMultiTxIndex<ImmutableRoaringBitmap> multiTermTxIndex,
        StackBuffer stackBuffer) throws Exception {

        MutableRoaringBitmap container = copy(original);
        inPlaceAndNotMultiTx(container, multiTermTxIndex, stackBuffer);
        return container;
    }

    @Override
    public void inPlaceAndNotMultiTx(MutableRoaringBitmap original,
        MiruMultiTxIndex<ImmutableRoaringBitmap> multiTermTxIndex,
        StackBuffer stackBuffer) throws Exception {
        multiTermTxIndex.txIndex((index, bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                original.andNot(bitmap);
            } else if (filer != null) {
                original.andNot(bitmapFromFiler(filer, offset, stackBuffer1));
            }
        }, stackBuffer);
    }

    @Override
    public void multiTx(MiruMultiTxIndex<ImmutableRoaringBitmap> multiTermTxIndex,
        IndexAlignedBitmapStream<MutableRoaringBitmap> stream,
        StackBuffer stackBuffer) throws Exception {

        multiTermTxIndex.txIndex((index, bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                stream.stream(index, copy(bitmap));
            } else if (filer != null) {
                stream.stream(index, bitmapFromFiler(filer, offset, stackBuffer1));
            }
        }, stackBuffer);
    }

    @Override
    public MutableRoaringBitmap andNot(ImmutableRoaringBitmap original, ImmutableRoaringBitmap bitmap) {
        return MutableRoaringBitmap.andNot(original, bitmap);
    }

    @Override
    public MutableRoaringBitmap andNot(ImmutableRoaringBitmap original, List<ImmutableRoaringBitmap> bitmaps) {
        if (bitmaps.isEmpty()) {
            return copy(original);
        } else {
            MutableRoaringBitmap container = MutableRoaringBitmap.andNot(original, bitmaps.get(0));
            for (int i = 1; i < bitmaps.size(); i++) {
                container.andNot(bitmaps.get(i));
                if (container.isEmpty()) {
                    break;
                }
            }
            return container;
        }
    }

    @Override
    public MutableRoaringBitmap andNotTx(MiruTxIndex<ImmutableRoaringBitmap> original,
        List<MiruTxIndex<ImmutableRoaringBitmap>> not,
        StackBuffer stackBuffer) throws Exception {

        MutableRoaringBitmap container = original.txIndex((bitmap, filer, offset, stackBuffer1) -> {
            if (bitmap != null) {
                MutableRoaringBitmap mutable = new MutableRoaringBitmap();
                mutable.or(bitmap);
                return mutable;
            } else if (filer != null) {
                return bitmapFromFiler(filer, offset, stackBuffer1);
            } else {
                return new MutableRoaringBitmap();
            }
        }, stackBuffer);

        if (container.isEmpty()) {
            return container;
        }

        for (MiruTxIndex<ImmutableRoaringBitmap> index : not) {
            index.txIndex((bitmap, filer, offset, stackBuffer1) -> {
                if (bitmap != null) {
                    container.andNot(bitmap);
                } else if (filer != null) {
                    container.andNot(bitmapFromFiler(filer, offset, stackBuffer1));
                }
                return null;
            }, stackBuffer);

            if (container.isEmpty()) {
                break;
            }
        }

        return container;
    }

    @Override
    public CardinalityAndLastSetBit<MutableRoaringBitmap> inPlaceAndNotWithCardinalityAndLastSetBit(MutableRoaringBitmap original, ImmutableRoaringBitmap not) {
        original.andNot(not);
        return RoaringBufferInspection.cardinalityAndLastSetBit(original);
    }

    @Override
    public CardinalityAndLastSetBit<MutableRoaringBitmap> andNotWithCardinalityAndLastSetBit(ImmutableRoaringBitmap original, ImmutableRoaringBitmap not) {
        MutableRoaringBitmap container = andNot(original, not);
        return RoaringBufferInspection.cardinalityAndLastSetBit(container);
    }

    @Override
    public CardinalityAndLastSetBit<MutableRoaringBitmap> andWithCardinalityAndLastSetBit(List<ImmutableRoaringBitmap> ands) {
        MutableRoaringBitmap container = and(ands);
        return RoaringBufferInspection.cardinalityAndLastSetBit(container);
    }

    @Override
    public MutableRoaringBitmap orToSourceSize(ImmutableRoaringBitmap source, ImmutableRoaringBitmap mask) {
        return or(Arrays.asList(source, mask));
    }

    @Override
    public MutableRoaringBitmap andNotToSourceSize(ImmutableRoaringBitmap source, ImmutableRoaringBitmap mask) {
        return andNot(source, mask);
    }

    @Override
    public MutableRoaringBitmap andNotToSourceSize(ImmutableRoaringBitmap source, List<ImmutableRoaringBitmap> masks) {
        return andNot(source, masks);
    }

    @Override
    public MutableRoaringBitmap deserialize(DataInput dataInput) throws Exception {
        MutableRoaringBitmap bitmap = new MutableRoaringBitmap();
        bitmap.deserialize(dataInput);
        return bitmap;
    }

    @Override
    public void serialize(ImmutableRoaringBitmap bitmap, DataOutput dataOutput) throws Exception {
        bitmap.serialize(dataOutput);
    }

    @Override
    public boolean isEmpty(ImmutableRoaringBitmap bitmap) {
        return bitmap.isEmpty();
    }

    @Override
    public long sizeInBytes(ImmutableRoaringBitmap bitmap) {
        return bitmap.getSizeInBytes();
    }

    @Override
    public long sizeInBits(ImmutableRoaringBitmap bitmap) {
        return RoaringBufferInspection.sizeInBits(bitmap);
    }

    @Override
    public long serializedSizeInBytes(ImmutableRoaringBitmap bitmap) {
        return bitmap.serializedSizeInBytes();
    }

    @Override
    public MutableRoaringBitmap buildIndexMask(int largestIndex, Optional<? extends ImmutableRoaringBitmap> andNotMask) {
        MutableRoaringBitmap mask = new MutableRoaringBitmap();
        if (largestIndex < 0) {
            return mask;
        }

        mask.flip(0, largestIndex + 1);
        if (andNotMask.isPresent()) {
            mask.andNot(andNotMask.get());
        }
        return mask;
    }

    @Override
    public MutableRoaringBitmap buildTimeRangeMask(MiruTimeIndex timeIndex, long smallestTimestamp, long largestTimestamp, StackBuffer stackBuffer) throws
        IOException, InterruptedException {
        int smallestInclusiveId = timeIndex.smallestExclusiveTimestampIndex(smallestTimestamp, stackBuffer);
        int largestExclusiveId = timeIndex.largestInclusiveTimestampIndex(largestTimestamp, stackBuffer) + 1;

        MutableRoaringBitmap mask = new MutableRoaringBitmap();

        if (largestExclusiveId < 0 || smallestInclusiveId > largestExclusiveId) {
            return mask;
        }
        mask.flip(smallestInclusiveId, largestExclusiveId);
        return mask;
    }

    @Override
    public MutableRoaringBitmap copy(ImmutableRoaringBitmap original) {
        MutableRoaringBitmap container = new MutableRoaringBitmap();
        container.or(original);
        return container;
    }

    @Override
    public MiruIntIterator intIterator(ImmutableRoaringBitmap bitmap) {
        final IntIterator intIterator = bitmap.getIntIterator();
        return new MiruIntIterator() {

            @Override
            public boolean hasNext() {
                return intIterator.hasNext();
            }

            @Override
            public int next() {
                return intIterator.next();
            }
        };
    }

    @Override
    public MiruIntIterator descendingIntIterator(ImmutableRoaringBitmap bitmap) {
        final IntIterator intIterator = bitmap.getReverseIntIterator();
        return new MiruIntIterator() {

            @Override
            public boolean hasNext() {
                return intIterator.hasNext();
            }

            @Override
            public int next() {
                return intIterator.next();
            }
        };
    }

    @Override
    public int[] indexes(ImmutableRoaringBitmap bitmap) {
        return bitmap.toArray();
    }

    @Override
    public int lastSetBit(ImmutableRoaringBitmap bitmap) {
        MiruIntIterator iterator = intIterator(bitmap);
        int last = -1;
        while (iterator.hasNext()) {
            last = iterator.next();
        }
        return last;
    }

    private MutableRoaringBitmap bitmapFromFiler(ChunkFiler filer, int offset, StackBuffer stackBuffer1) throws IOException {
        if (filer.canLeakUnsafeByteBuffer()) {
            ByteBuffer buf = filer.leakUnsafeByteBuffer();
            buf.position(offset);
            return new ImmutableRoaringBitmap(buf).toMutableRoaringBitmap();
        } else {
            filer.seek(offset);
            MutableRoaringBitmap mutable = new MutableRoaringBitmap();
            mutable.deserialize(new FilerDataInput(filer, stackBuffer1));
            return mutable;
        }
    }

    public static void main(String[] args) throws Exception {

        /*for (int i = 0; i < 100; i++) {

            RoaringBitmap bitmap = new RoaringBitmap();
            Random r = new Random(1234);
            for (int j = 0; j < 1_000_000; j++) {
                if (r.nextBoolean()) {
                    bitmap.add(j);
                }
            }

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            bitmap.serialize(out);
            byte[] bytes = out.toByteArray();

            long start = System.currentTimeMillis();
            int count = 0;
            for (int j = 0; j < 1_000; j++) {
                RoaringBitmap bmp = new RoaringBitmap();
                bmp.deserialize(ByteStreams.newDataInput(bytes));
                count++;
            }
            System.out.println("---- regular ----");
            System.out.println((System.currentTimeMillis() - start) + " ms, " + count + " iter");

            start = System.currentTimeMillis();
            for (int j = 0; j < 1_000; j++) {
                ImmutableRoaringBitmap buf = new ImmutableRoaringBitmap(ByteBuffer.wrap(bytes));
                count++;
            }
            System.out.println("---- buffers ----");
            System.out.println((System.currentTimeMillis() - start) + " ms, " + count + " iter");
        }*/
        for (int i = 0; i < 1000; i++) {

            RoaringBitmap bitmap1 = new RoaringBitmap();
            RoaringBitmap bitmap2 = new RoaringBitmap();
            Random r = new Random(1234);
            for (int j = 0; j < 1_000_000; j++) {
                if (r.nextBoolean()) {
                    bitmap1.add(j);
                }
                if (r.nextBoolean()) {
                    bitmap2.add(j);
                }
            }

            ByteArrayDataOutput out1 = ByteStreams.newDataOutput();
            ByteArrayDataOutput out2 = ByteStreams.newDataOutput();
            bitmap1.serialize(out1);
            bitmap2.serialize(out2);
            byte[] bytes1 = out1.toByteArray();
            byte[] bytes2 = out2.toByteArray();

            long start;
            int count;

            start = System.currentTimeMillis();
            count = 0;
            for (int j = 0; j < 1_000; j++) {
                RoaringBitmap bmp1 = new RoaringBitmap();
                bmp1.deserialize(ByteStreams.newDataInput(bytes1));
                RoaringBitmap bmp2 = new RoaringBitmap();
                bmp2.deserialize(ByteStreams.newDataInput(bytes2));

                FastAggregation.or(bmp1, bmp2);
                //FastAggregation.and(bmp1, bmp2);
                count++;
            }
            System.out.println("---- regular ----");
            System.out.println((System.currentTimeMillis() - start) + " ms, " + count + " iter");

            start = System.currentTimeMillis();
            count = 0;
            for (int j = 0; j < 1_000; j++) {
                MutableRoaringBitmap buf1 = new MutableRoaringBitmap();
                buf1.deserialize(ByteStreams.newDataInput(bytes1));
                MutableRoaringBitmap buf2 = new MutableRoaringBitmap();
                buf2.deserialize(ByteStreams.newDataInput(bytes2));

                BufferFastAggregation.or(buf1, buf2);
                //BufferFastAggregation.and(buf1, buf2);
            }
            System.out.println("---- mutable ---- "); // + buf1.getCardinality() + ", " + buf2.getCardinality());
            System.out.println((System.currentTimeMillis() - start) + " ms, " + count + " iter");

            start = System.currentTimeMillis();
            count = 0;
            for (int j = 0; j < 1_000; j++) {
                ImmutableRoaringBitmap ibuf1 = new ImmutableRoaringBitmap(ByteBuffer.wrap(bytes1));
                ImmutableRoaringBitmap ibuf2 = new ImmutableRoaringBitmap(ByteBuffer.wrap(bytes2));

                BufferFastAggregation.or(ibuf1, ibuf2);
                //BufferFastAggregation.and(ibuf1, ibuf2);
            }
            System.out.println("---- immutable ---- "); // + ibuf1.getCardinality() + ", " + ibuf2.getCardinality());
            System.out.println((System.currentTimeMillis() - start) + " ms, " + count + " iter");

        }

        /*for (int i = 0; i < 100; i++) {

            long start = System.currentTimeMillis();
            RoaringBitmap b1 = new RoaringBitmap();
            RoaringBitmap b2 = new RoaringBitmap();
            Random r = new Random(1234);
            for (int j = 0; j < 100_000; j++) {
                if (r.nextBoolean()) {
                    b1.add(j);
                }
                if (r.nextBoolean()) {
                    b2.add(j);
                }
                RoaringBitmap b3 = new RoaringBitmap();
                RoaringAggregation.or(b3, b1, b2);
            }
            System.out.println("---- regular ----");
            System.out.println((System.currentTimeMillis() - start) + " ms");

            start = System.currentTimeMillis();
            MutableRoaringBitmap m1 = new MutableRoaringBitmap();
            MutableRoaringBitmap m2 = new MutableRoaringBitmap();
            r = new Random(1234);
            for (int j = 0; j < 100_000; j++) {
                if (r.nextBoolean()) {
                    m1.add(j);
                }
                if (r.nextBoolean()) {
                    m2.add(j);
                }
                MutableRoaringBitmap m3 = BufferFastAggregation.or(m1, m2);
            }
            System.out.println("---- buffers ----");
            System.out.println((System.currentTimeMillis() - start) + " ms");

        }*/
    }
}
