package com.jivesoftware.os.miru.service.index.filer;

import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.filer.io.StripingLocksProvider;
import com.jivesoftware.os.filer.io.api.HintAndTransaction;
import com.jivesoftware.os.filer.io.api.KeyRange;
import com.jivesoftware.os.filer.io.api.KeyedFilerStore;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.filer.io.map.MapContext;
import com.jivesoftware.os.filer.io.map.MapStore;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.plugin.MiruInterner;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.BitmapAndLastId;
import com.jivesoftware.os.miru.plugin.index.IndexAlignedBitmapMerger;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInvertedIndex;
import com.jivesoftware.os.miru.plugin.index.MultiIndexTx;
import com.jivesoftware.os.miru.plugin.index.TermIdStream;
import com.jivesoftware.os.miru.plugin.partition.TrackError;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang.mutable.MutableLong;

/**
 * @author jonathan
 */
public class MiruFilerFieldIndex<BM extends IBM, IBM> implements MiruFieldIndex<BM, IBM> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruBitmaps<BM, IBM> bitmaps;
    private final TrackError trackError;
    private final KeyedFilerStore<Long, Void>[] indexes;
    private final KeyedFilerStore<Integer, MapContext>[] cardinalities;
    // We could lock on both field + termId for improved hash/striping, but we favor just termId to reduce object creation
    private final StripingLocksProvider<MiruTermId> stripingLocksProvider;
    private final MiruInterner<MiruTermId> termInterner;

    public MiruFilerFieldIndex(MiruBitmaps<BM, IBM> bitmaps,
        TrackError trackError,
        KeyedFilerStore<Long, Void>[] indexes,
        KeyedFilerStore<Integer, MapContext>[] cardinalities,
        StripingLocksProvider<MiruTermId> stripingLocksProvider,
        MiruInterner<MiruTermId> termInterner) throws Exception {
        this.bitmaps = bitmaps;
        this.trackError = trackError;
        this.indexes = indexes;
        this.cardinalities = cardinalities;
        this.stripingLocksProvider = stripingLocksProvider;
        this.termInterner = termInterner;
    }

    @Override
    public void append(int fieldId, MiruTermId termId, int[] ids, long[] counts, StackBuffer stackBuffer) throws Exception {
        getIndex(fieldId, termId).append(stackBuffer, ids);
        mergeCardinalities(fieldId, termId, ids, counts, stackBuffer);
    }

    @Override
    public void set(int fieldId, MiruTermId termId, int[] ids, long[] counts, StackBuffer stackBuffer) throws Exception {
        getIndex(fieldId, termId).set(stackBuffer, ids);
        mergeCardinalities(fieldId, termId, ids, counts, stackBuffer);
    }

    @Override
    public void setIfEmpty(int fieldId, MiruTermId termId, int id, long count, StackBuffer stackBuffer) throws Exception {
        if (getIndex(fieldId, termId).setIfEmpty(stackBuffer, id)) {
            mergeCardinalities(fieldId, termId, new int[] { id }, new long[] { count }, stackBuffer);
        }
    }

    @Override
    public void remove(int fieldId, MiruTermId termId, int id, StackBuffer stackBuffer) throws Exception {
        getIndex(fieldId, termId).remove(id, stackBuffer);
        mergeCardinalities(fieldId, termId, new int[] { id }, cardinalities[fieldId] != null ? new long[1] : null, stackBuffer);
    }

    @Override
    public void streamTermIdsForField(int fieldId, List<KeyRange> ranges, final TermIdStream termIdStream, StackBuffer stackBuffer) throws Exception {
        MutableLong bytes = new MutableLong();
        indexes[fieldId].streamKeys(ranges, rawKey -> {
            try {
                bytes.add(rawKey.length);
                return termIdStream.stream(termInterner.intern(rawKey));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, stackBuffer);
        LOG.inc("count>streamTermIdsForField>" + fieldId);
        LOG.inc("bytes>streamTermIdsForField>" + fieldId, bytes.longValue());
    }

    @Override
    public MiruInvertedIndex<BM, IBM> get(int fieldId, MiruTermId termId) throws Exception {
        return getIndex(fieldId, termId);
    }

    @Override
    public MiruInvertedIndex<BM, IBM> getOrCreateInvertedIndex(int fieldId, MiruTermId term) throws Exception {
        return getIndex(fieldId, term);
    }

    private MiruInvertedIndex<BM, IBM> getIndex(int fieldId, MiruTermId termId) throws Exception {
        return new MiruFilerInvertedIndex<>(bitmaps, trackError, fieldId, termId.getBytes(), indexes[fieldId], stripingLocksProvider.lock(termId, 0));
    }

    @Override
    public void multiGet(int fieldId, MiruTermId[] termIds, BitmapAndLastId<BM>[] results, StackBuffer stackBuffer) throws Exception {
        byte[][] termIdBytes = new byte[termIds.length][];
        for (int i = 0; i < termIds.length; i++) {
            if (termIds[i] != null) {
                termIdBytes[i] = termIds[i].getBytes();
            }
        }
        MutableLong bytes = new MutableLong();
        indexes[fieldId].readEach(termIdBytes, null, (monkey, filer, _stackBuffer, lock, index) -> {
            if (filer != null) {
                bytes.add(filer.length());
                BitmapAndLastId<BM> bitmapAndLastId = MiruFilerInvertedIndex.deser(bitmaps, trackError, filer, -1, _stackBuffer);
                if (bitmapAndLastId != null) {
                    return bitmapAndLastId.bitmap;
                }
            }
            return null;
        }, results, stackBuffer);
        LOG.inc("count>multiGet>" + fieldId);
        LOG.inc("bytes>multiGet>" + fieldId, bytes.longValue());
    }

    @Override
    public void multiTxIndex(int fieldId,
        MiruTermId[] termIds,
        int considerIfLastIdGreaterThanN,
        StackBuffer stackBuffer,
        MultiIndexTx<IBM> indexTx) throws Exception {

        byte[][] termIdBytes = new byte[termIds.length][];
        for (int i = 0; i < termIds.length; i++) {
            if (termIds[i] != null) {
                termIdBytes[i] = termIds[i].getBytes();
            }
        }
        MutableLong bytes = new MutableLong();
        indexes[fieldId].readEach(termIdBytes, null, (monkey, filer, _stackBuffer, lock, index) -> {
            if (filer != null) {
                try {
                    bytes.add(filer.length());
                    int lastId = -1;
                    if (considerIfLastIdGreaterThanN >= 0) {
                        lastId = filer.readInt();
                        filer.seek(0);
                    }
                    if (lastId < 0 || lastId > considerIfLastIdGreaterThanN) {
                        indexTx.tx(index, null, filer, MiruFilerInvertedIndex.LAST_ID_LENGTH, _stackBuffer);
                    }
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
            return null;
        }, new Void[termIds.length], stackBuffer);
        LOG.inc("count>multiTxIndex>" + fieldId);
        LOG.inc("bytes>multiTxIndex>" + fieldId, bytes.longValue());
    }

    @Override
    public long getCardinality(int fieldId, MiruTermId termId, int id, StackBuffer stackBuffer) throws Exception {
        if (cardinalities[fieldId] != null) {
            Long count = cardinalities[fieldId].read(termId.getBytes(), null, (monkey, filer, _stackBuffer, lock) -> {
                if (filer != null) {
                    synchronized (lock) {
                        byte[] payload = MapStore.INSTANCE.getPayload(filer, monkey, FilerIO.intBytes(id), _stackBuffer);
                        if (payload != null) {
                            return FilerIO.bytesLong(payload);
                        }
                    }
                }
                return null;
            }, stackBuffer);
            return count != null ? count : 0;
        }
        return -1;
    }

    @Override
    public long[] getCardinalities(int fieldId, MiruTermId termId, int[] ids, StackBuffer stackBuffer) throws Exception {
        long[] counts = new long[ids.length];
        if (cardinalities[fieldId] != null) {
            cardinalities[fieldId].read(termId.getBytes(), null, (monkey, filer, _stackBuffer, lock) -> {
                if (filer != null) {
                    synchronized (lock) {
                        for (int i = 0; i < ids.length; i++) {
                            if (ids[i] >= 0) {
                                byte[] payload = MapStore.INSTANCE.getPayload(filer, monkey, FilerIO.intBytes(ids[i]), _stackBuffer);
                                if (payload != null) {
                                    counts[i] = FilerIO.bytesLong(payload);
                                }
                            }
                        }
                    }
                }
                return null;
            }, stackBuffer);
        } else {
            Arrays.fill(counts, -1);
        }
        return counts;
    }

    @Override
    public long getGlobalCardinality(int fieldId, MiruTermId termId, StackBuffer stackBuffer) throws Exception {
        return getCardinality(fieldId, termId, -1, stackBuffer);
    }

    @Override
    public void multiMerge(int fieldId, MiruTermId[] termIds, IndexAlignedBitmapMerger<BM> merger, StackBuffer stackBuffer) throws Exception {
        byte[][] termIdBytes = new byte[termIds.length][];
        for (int i = 0; i < termIds.length; i++) {
            termIdBytes[i] = termIds[i].getBytes();
        }

        MutableLong bytesRead = new MutableLong();
        MutableLong bytesWrite = new MutableLong();
        indexes[fieldId].multiWriteNewReplace(termIdBytes, (monkey, filer, stackBuffer1, lock, index) -> {
            BitmapAndLastId<BM> backing = null;
            if (filer != null) {
                bytesRead.add(filer.length());
                synchronized (lock) {
                    filer.seek(0);
                    backing = MiruFilerInvertedIndex.deser(bitmaps, trackError, filer, -1, stackBuffer1);
                }
            }
            BitmapAndLastId<BM> merged = merger.merge(index, backing);
            if (merged == null) {
                return null;
            }
            try {
                MiruFilerInvertedIndex.SizeAndBytes sizeAndBytes = MiruFilerInvertedIndex.getSizeAndBytes(bitmaps, merged.bitmap, merged.lastId);
                bytesWrite.add(sizeAndBytes.filerSizeInBytes);
                return new HintAndTransaction<>(sizeAndBytes.filerSizeInBytes, new MiruFilerInvertedIndex.SetTransaction(sizeAndBytes.bytes));
            } catch (Exception e) {
                throw new IOException("Failed to serialize bitmap", e);
            }
        }, new Void[termIdBytes.length], stackBuffer);
        LOG.inc("count>multiMerge>" + fieldId);
        LOG.inc("bytes>multiMergeRead>" + fieldId, bytesRead.longValue());
        LOG.inc("bytes>multiMergeWrite>" + fieldId, bytesWrite.longValue());
    }

    @Override
    public void mergeCardinalities(int fieldId, MiruTermId termId, int[] ids, long[] counts, StackBuffer stackBuffer) throws Exception {
        if (cardinalities[fieldId] != null && counts != null) {
            cardinalities[fieldId].readWriteAutoGrow(termId.getBytes(), ids.length, (monkey, filer, _stackBuffer, lock) -> {
                synchronized (lock) {
                    long delta = 0;
                    for (int i = 0; i < ids.length; i++) {
                        byte[] key = FilerIO.intBytes(ids[i]);
                        long keyHash = MapStore.INSTANCE.hash(key, 0, key.length);
                        byte[] payload = MapStore.INSTANCE.getPayload(filer, monkey, keyHash, key, stackBuffer);
                        long existing = payload != null ? FilerIO.bytesLong(payload) : 0;
                        MapStore.INSTANCE.add(filer, monkey, (byte) 1, keyHash, key, FilerIO.longBytes(counts[i]), _stackBuffer);
                        delta += counts[i] - existing;
                    }

                    byte[] globalKey = FilerIO.intBytes(-1);
                    byte[] globalPayload = MapStore.INSTANCE.getPayload(filer, monkey, globalKey, _stackBuffer);
                    long globalExisting = globalPayload != null ? FilerIO.bytesLong(globalPayload) : 0;
                    MapStore.INSTANCE.add(filer, monkey, (byte) 1, globalKey, FilerIO.longBytes(globalExisting + delta), _stackBuffer);
                }
                return null;
            }, stackBuffer);
        }
    }

}
