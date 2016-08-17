package com.jivesoftware.os.miru.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interners;
import com.google.common.io.Files;
import com.google.common.util.concurrent.MoreExecutors;
import com.jivesoftware.os.filer.chunk.store.ChunkStoreInitializer;
import com.jivesoftware.os.filer.chunk.store.transaction.TxCogs;
import com.jivesoftware.os.filer.chunk.store.transaction.TxNamedMapOfFiler;
import com.jivesoftware.os.filer.io.ByteBufferFactory;
import com.jivesoftware.os.filer.io.HeapByteBufferFactory;
import com.jivesoftware.os.filer.io.StripingLocksProvider;
import com.jivesoftware.os.filer.io.api.KeyedFilerStore;
import com.jivesoftware.os.filer.io.api.StackBuffer;
import com.jivesoftware.os.filer.io.chunk.ChunkStore;
import com.jivesoftware.os.filer.keyed.store.TxKeyedFilerStore;
import com.jivesoftware.os.jive.utils.collections.bah.LRUConcurrentBAHLinkedHash;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.lab.LABEnvironment;
import com.jivesoftware.os.lab.LABRawhide;
import com.jivesoftware.os.lab.LabHeapPressure;
import com.jivesoftware.os.lab.api.MemoryRawEntryFormat;
import com.jivesoftware.os.lab.api.NoOpFormatTransformerProvider;
import com.jivesoftware.os.lab.api.ValueIndex;
import com.jivesoftware.os.lab.api.ValueIndexConfig;
import com.jivesoftware.os.lab.guts.Leaps;
import com.jivesoftware.os.miru.api.MiruBackingStorage;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.activity.schema.DefaultMiruSchemaDefinition;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchemaProvider;
import com.jivesoftware.os.miru.api.base.MiruIBA;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.api.wal.RCVSSipCursor;
import com.jivesoftware.os.miru.plugin.MiruInterner;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.index.MiruActivityInternExtern;
import com.jivesoftware.os.miru.plugin.index.MiruTermComposer;
import com.jivesoftware.os.miru.plugin.marshaller.RCVSSipIndexMarshaller;
import com.jivesoftware.os.miru.plugin.schema.SingleSchemaProvider;
import com.jivesoftware.os.miru.service.index.lab.LabTimeIdIndex;
import com.jivesoftware.os.miru.service.index.lab.LabTimeIdIndexInitializer;
import com.jivesoftware.os.miru.service.locator.MiruResourceLocator;
import com.jivesoftware.os.miru.service.locator.MiruTempDirectoryResourceLocator;
import com.jivesoftware.os.miru.service.partition.PartitionErrorTracker;
import com.jivesoftware.os.miru.service.stream.LabPluginCacheProvider;
import com.jivesoftware.os.miru.service.stream.MiruContext;
import com.jivesoftware.os.miru.service.stream.MiruContextFactory;
import com.jivesoftware.os.miru.service.stream.allocator.InMemoryChunkAllocator;
import com.jivesoftware.os.miru.service.stream.allocator.MiruChunkAllocator;
import com.jivesoftware.os.miru.service.stream.allocator.OnDiskChunkAllocator;
import java.io.File;
import java.util.concurrent.atomic.AtomicLong;
import org.merlin.config.BindInterfaceToConfiguration;

/**
 *
 */
public class IndexTestUtil {

    private static MiruInterner<MiruTermId> termInterner = new MiruInterner<MiruTermId>(true) {
        @Override
        public MiruTermId create(byte[] bytes) {
            return new MiruTermId(bytes);
        }
    };

    public static TxCogs cogs = new TxCogs(256, 64, null, null, null);

    private static final MiruSchema schema = new MiruSchema.Builder("test", 1)
        .setFieldDefinitions(DefaultMiruSchemaDefinition.FIELDS)
        .build();

    private static MiruContextFactory<RCVSSipCursor> factory(int numberOfChunkStores, boolean useLabIndexes) throws Exception {

        StripingLocksProvider<MiruTermId> fieldIndexStripingLocksProvider = new StripingLocksProvider<>(1024);
        StripingLocksProvider<MiruStreamId> streamStripingLocksProvider = new StripingLocksProvider<>(1024);
        StripingLocksProvider<String> authzStripingLocksProvider = new StripingLocksProvider<>(1024);

        MiruSchemaProvider schemaProvider = new SingleSchemaProvider(
            schema);
        MiruTermComposer termComposer = new MiruTermComposer(Charsets.UTF_8, termInterner);

        MiruInterner<MiruIBA> ibaInterner = new MiruInterner<MiruIBA>(true) {
            @Override
            public MiruIBA create(byte[] bytes) {
                return new MiruIBA(bytes);
            }
        };
        MiruInterner<MiruTenantId> tenantInterner = new MiruInterner<MiruTenantId>(true) {
            @Override
            public MiruTenantId create(byte[] bytes) {
                return new MiruTenantId(bytes);
            }
        };
        MiruActivityInternExtern activityInternExtern = new MiruActivityInternExtern(
            ibaInterner,
            tenantInterner,
            Interners.<String>newWeakInterner(),
            termComposer);

        final MiruResourceLocator diskResourceLocator = new MiruTempDirectoryResourceLocator();
        LabHeapPressure labHeapPressure = new LabHeapPressure(LABEnvironment.buildLABHeapSchedulerThreadPool(1),
            1024 * 1024 * 10,
            1024 * 1024 * 20,
            new AtomicLong());
        long labMaxWALSizeInBytes = 1024 * 1024 * 10;
        long labMaxEntriesPerWAL = 1000;
        long labMaxEntrySizeInBytes = 1024 * 1024 * 10;
        LRUConcurrentBAHLinkedHash<Leaps> leapCache = LABEnvironment.buildLeapsCache(1_000_000, 10);
        MiruChunkAllocator inMemoryChunkAllocator = new InMemoryChunkAllocator(
            diskResourceLocator,
            new HeapByteBufferFactory(),
            new HeapByteBufferFactory(),
            4_096,
            numberOfChunkStores,
            true,
            100,
            1_000,
            new LabHeapPressure[] { labHeapPressure },
            labMaxWALSizeInBytes,
            labMaxEntriesPerWAL,
            labMaxEntrySizeInBytes,
            useLabIndexes,
            leapCache);

        MiruChunkAllocator onDiskChunkAllocator = new OnDiskChunkAllocator(diskResourceLocator,
            new HeapByteBufferFactory(),
            numberOfChunkStores,
            100,
            1_000,
            new LabHeapPressure[] { labHeapPressure },
            labMaxWALSizeInBytes,
            labMaxEntriesPerWAL,
            labMaxEntrySizeInBytes,
            leapCache);

        LabTimeIdIndex[] timeIdIndexes = new LabTimeIdIndexInitializer().initialize(4, 1_000, 1024 * 1024, diskResourceLocator, onDiskChunkAllocator);

        OrderIdProvider idProvider = new OrderIdProviderImpl(new ConstantWriterIdProvider(1));
        return new MiruContextFactory<>(idProvider,
            cogs,
            cogs,
            timeIdIndexes,
            LabPluginCacheProvider.allocateLocks(64),
            schemaProvider,
            termComposer,
            activityInternExtern,
            ImmutableMap.<MiruBackingStorage, MiruChunkAllocator>builder()
                .put(MiruBackingStorage.memory, inMemoryChunkAllocator)
                .put(MiruBackingStorage.disk, onDiskChunkAllocator)
                .build(),
            new RCVSSipIndexMarshaller(),
            new MiruTempDirectoryResourceLocator(),
            1024,
            fieldIndexStripingLocksProvider,
            streamStripingLocksProvider,
            authzStripingLocksProvider,
            new PartitionErrorTracker(BindInterfaceToConfiguration.bindDefault(PartitionErrorTracker.PartitionErrorTrackerConfig.class)),
            termInterner,
            new ObjectMapper(),
            1024 * 1024 * 10,
            useLabIndexes,
            false);
    }

    public static <BM extends IBM, IBM> MiruContext<BM, IBM, RCVSSipCursor> buildInMemoryContext(int numberOfChunkStores,
        boolean useLabIndexes,
        MiruBitmaps<BM, IBM> bitmaps,
        MiruPartitionCoord coord) throws Exception {
        return factory(numberOfChunkStores, useLabIndexes).allocate(bitmaps, schema, coord, MiruBackingStorage.memory, null);

    }

    public static <BM extends IBM, IBM> MiruContext<BM, IBM, RCVSSipCursor> buildOnDiskContext(int numberOfChunkStores,
        boolean useLabIndexes,
        MiruBitmaps<BM, IBM> bitmaps,
        MiruPartitionCoord coord) throws Exception {
        return factory(numberOfChunkStores, useLabIndexes).allocate(bitmaps, schema, coord, MiruBackingStorage.disk, null);

    }

    public static ValueIndex buildValueIndex(String name) throws Exception {
        File root = Files.createTempDir();
        LABEnvironment environment = new LABEnvironment(LABEnvironment.buildLABSchedulerThreadPool(1),
            LABEnvironment.buildLABCompactorThreadPool(1),
            LABEnvironment.buildLABDestroyThreadPool(1),
            "wal",
            -1,
            -1,
            -1,
            root,
            new LabHeapPressure(MoreExecutors.sameThreadExecutor(), 1024 * 1024, 2 * 1024 * 1024, new AtomicLong()),
            4,
            16,
            LABEnvironment.buildLeapsCache(1_000, 4));
        return environment.open(new ValueIndexConfig(name, 64, 1024 * 1024, -1, -1, 10 * 1024 * 1024, NoOpFormatTransformerProvider.NAME, LABRawhide.NAME,
            MemoryRawEntryFormat.NAME));
    }

    public static KeyedFilerStore<Long, Void> buildKeyedFilerStore(String name, ChunkStore[] chunkStores) throws Exception {
        return new TxKeyedFilerStore<>(cogs, 0, chunkStores, keyBytes(name), false,
            TxNamedMapOfFiler.CHUNK_FILER_CREATOR,
            TxNamedMapOfFiler.CHUNK_FILER_OPENER,
            TxNamedMapOfFiler.OVERWRITE_GROWER_PROVIDER,
            TxNamedMapOfFiler.REWRITE_GROWER_PROVIDER);
    }

    public static ChunkStore[] buildByteBufferBackedChunkStores(int numberOfChunkStores, ByteBufferFactory byteBufferFactory, long segmentSize)
        throws Exception {

        StackBuffer stackBuffer = new StackBuffer();
        ChunkStore[] chunkStores = new ChunkStore[numberOfChunkStores];
        ChunkStoreInitializer chunkStoreInitializer = new ChunkStoreInitializer();
        for (int i = 0; i < numberOfChunkStores; i++) {
            chunkStores[i] = chunkStoreInitializer.create(byteBufferFactory, segmentSize, new HeapByteBufferFactory(), 500, 5_000, stackBuffer);
        }

        return chunkStores;
    }

    private static byte[] keyBytes(String key) {
        return key.getBytes(Charsets.UTF_8);
    }

    private IndexTestUtil() {
    }
}
