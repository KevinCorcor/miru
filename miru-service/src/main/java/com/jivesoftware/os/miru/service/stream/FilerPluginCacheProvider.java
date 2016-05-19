package com.jivesoftware.os.miru.service.stream;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.jivesoftware.os.filer.chunk.store.transaction.MapCreator;
import com.jivesoftware.os.filer.chunk.store.transaction.MapOpener;
import com.jivesoftware.os.filer.chunk.store.transaction.TxCogs;
import com.jivesoftware.os.filer.chunk.store.transaction.TxMapGrower;
import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.filer.io.chunk.ChunkStore;
import com.jivesoftware.os.filer.io.map.MapContext;
import com.jivesoftware.os.filer.keyed.store.TxKeyedFilerStore;
import com.jivesoftware.os.miru.plugin.cache.MiruFilerCacheKeyValues;
import com.jivesoftware.os.miru.plugin.cache.MiruPluginCacheProvider;
import java.util.Map;

/**
 *
 */
public class FilerPluginCacheProvider implements MiruPluginCacheProvider {

    private final TxCogs cogs;
    private final int seed;
    private final ChunkStore[] chunkStores;

    private final Map<String, CacheKeyValues> pluginPersistentCache = Maps.newConcurrentMap();

    public FilerPluginCacheProvider(TxCogs cogs, int seed, ChunkStore[] chunkStores) {
        this.cogs = cogs;
        this.seed = seed;
        this.chunkStores = chunkStores;
    }

    @Override
    public CacheKeyValues getKeyValues(String name, int payloadSize, boolean variablePayloadSize, long maxHeapPressureInBytes) {
        return pluginPersistentCache.computeIfAbsent(name, (key) -> {
            @SuppressWarnings("unchecked")
            TxKeyedFilerStore<Integer, MapContext>[] powerIndex = new TxKeyedFilerStore[16];
            for (int power = 0; power < powerIndex.length; power++) {
                powerIndex[power] = new TxKeyedFilerStore<>(cogs,
                    seed,
                    chunkStores,
                    keyBytes("cache-" + power + "-" + key),
                    false,
                    new MapCreator(100, (int) FilerIO.chunkLength(power), true, payloadSize, variablePayloadSize),
                    MapOpener.INSTANCE,
                    TxMapGrower.MAP_OVERWRITE_GROWER,
                    TxMapGrower.MAP_REWRITE_GROWER);
            }

            return new MiruFilerCacheKeyValues(name, powerIndex);
        });
    }

    @Override
    public TimestampedCacheKeyValues getTimestampedKeyValues(String name, int payloadSize, boolean variablePayloadSize, long maxHeapPressureInBytes) {
        return new TimestampedCacheKeyValues() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean get(byte[] cacheId, byte[][] keys, TimestampedIndexKeyValueStream stream, StackBuffer stackBuffer) throws Exception {
                return true;
            }

            @Override
            public boolean rangeScan(byte[] cacheId, byte[] fromInclusive, byte[] toExclusive, TimestampedKeyValueStream stream) throws Exception {
                return true;
            }

            @Override
            public boolean put(byte[] cacheId,
                boolean commitOnUpdate,
                boolean fsyncOnCommit,
                ConsumeTimestampedKeyValueStream consume,
                StackBuffer stackBuffer) throws Exception {
                return consume.consume((key, value, timestamp) -> true); // lol
            }

            @Override
            public Object lock(byte[] cacheId) {
                return new Object(); // eat shit and die, filer
            }
        };
    }

    private byte[] keyBytes(String key) {
        return key.getBytes(Charsets.UTF_8);
    }
}
