package com.jivesoftware.os.miru.service.partition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interners;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import com.jivesoftware.os.amza.service.AmzaService;
import com.jivesoftware.os.amza.service.EmbeddedClientProvider;
import com.jivesoftware.os.filer.chunk.store.transaction.TxCogs;
import com.jivesoftware.os.filer.io.HeapByteBufferFactory;
import com.jivesoftware.os.filer.io.StripingLocksProvider;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.JiveEpochTimestampProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.jive.utils.ordered.id.SnowflakeIdPacker;
import com.jivesoftware.os.miru.amza.MiruAmzaServiceConfig;
import com.jivesoftware.os.miru.amza.MiruAmzaServiceInitializer;
import com.jivesoftware.os.miru.api.HostPortProvider;
import com.jivesoftware.os.miru.api.MiruBackingStorage;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruPartitionState;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruActivity;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivityFactory;
import com.jivesoftware.os.miru.api.activity.schema.DefaultMiruSchemaDefinition;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchemaProvider;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchemaUnvailableException;
import com.jivesoftware.os.miru.api.base.MiruIBA;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.api.marshall.JacksonJsonObjectTypeMarshaller;
import com.jivesoftware.os.miru.api.topology.MiruClusterClient;
import com.jivesoftware.os.miru.api.topology.MiruIngressUpdate;
import com.jivesoftware.os.miru.api.topology.RangeMinMax;
import com.jivesoftware.os.miru.api.wal.MiruWALClient;
import com.jivesoftware.os.miru.api.wal.RCVSCursor;
import com.jivesoftware.os.miru.api.wal.RCVSSipCursor;
import com.jivesoftware.os.miru.bitmaps.roaring5.buffer.MiruBitmapsRoaringBuffer;
import com.jivesoftware.os.miru.cluster.MiruClusterRegistry;
import com.jivesoftware.os.miru.cluster.MiruRegistryClusterClient;
import com.jivesoftware.os.miru.cluster.MiruReplicaSetDirector;
import com.jivesoftware.os.miru.cluster.amza.AmzaClusterRegistry;
import com.jivesoftware.os.miru.plugin.MiruInterner;
import com.jivesoftware.os.miru.plugin.index.BloomIndex;
import com.jivesoftware.os.miru.plugin.index.MiruActivityInternExtern;
import com.jivesoftware.os.miru.plugin.index.MiruTermComposer;
import com.jivesoftware.os.miru.plugin.marshaller.RCVSSipIndexMarshaller;
import com.jivesoftware.os.miru.plugin.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.plugin.partition.TrackError;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestHandle;
import com.jivesoftware.os.miru.service.MiruServiceConfig;
import com.jivesoftware.os.miru.service.locator.MiruTempDirectoryResourceLocator;
import com.jivesoftware.os.miru.service.stream.MiruContextFactory;
import com.jivesoftware.os.miru.service.stream.MiruIndexAuthz;
import com.jivesoftware.os.miru.service.stream.MiruIndexBloom;
import com.jivesoftware.os.miru.service.stream.MiruIndexLatest;
import com.jivesoftware.os.miru.service.stream.MiruIndexPairedLatest;
import com.jivesoftware.os.miru.service.stream.MiruIndexPrimaryFields;
import com.jivesoftware.os.miru.service.stream.MiruIndexValueBits;
import com.jivesoftware.os.miru.service.stream.MiruIndexer;
import com.jivesoftware.os.miru.service.stream.MiruRebuildDirector;
import com.jivesoftware.os.miru.service.stream.allocator.InMemoryChunkAllocator;
import com.jivesoftware.os.miru.service.stream.allocator.MiruChunkAllocator;
import com.jivesoftware.os.miru.service.stream.allocator.OnDiskChunkAllocator;
import com.jivesoftware.os.miru.wal.MiruWALDirector;
import com.jivesoftware.os.miru.wal.RCVSWALInitializer;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALWriter;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALWriter;
import com.jivesoftware.os.miru.wal.lookup.MiruWALLookup;
import com.jivesoftware.os.miru.wal.lookup.RCVSWALLookup;
import com.jivesoftware.os.miru.wal.readtracking.MiruReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.MiruReadTrackingWALWriter;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALWriter;
import com.jivesoftware.os.rcvs.inmemory.InMemoryRowColumnValueStoreInitializer;
import com.jivesoftware.os.routing.bird.deployable.Deployable;
import com.jivesoftware.os.routing.bird.health.api.HealthCheckRegistry;
import com.jivesoftware.os.routing.bird.health.api.HealthChecker;
import com.jivesoftware.os.routing.bird.health.api.HealthFactory;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.merlin.config.BindInterfaceToConfiguration;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;
import org.roaringbitmap.buffer.MutableRoaringBitmap;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class MiruLocalHostedPartitionTest {

    MiruInterner<MiruTermId> termInterner = new MiruInterner<MiruTermId>(true) {
        @Override
        public MiruTermId create(byte[] bytes) {
            return new MiruTermId(bytes);
        }
    };

    private boolean useLabIndexes = true;

    private MiruContextFactory<RCVSSipCursor> contextFactory;
    private MiruSipTrackerFactory<RCVSSipCursor> sipTrackerFactory;
    private MiruSchema schema;
    private MiruSchemaProvider schemaProvider;
    private MiruClusterRegistry clusterRegistry;
    private MiruPartitionCoord coord;
    private MiruBackingStorage defaultStorage;
    private MiruWALClient<RCVSCursor, RCVSSipCursor> walClient;
    private MiruPartitionHeartbeatHandler partitionEventHandler;
    private MiruRebuildDirector rebuildDirector;
    private ScheduledExecutorService scheduledBootstrapService;
    private ScheduledExecutorService scheduledRebuildService;
    private ScheduledExecutorService scheduledSipMigrateService;
    private ExecutorService rebuildExecutor;
    private ExecutorService sipIndexExecutor;
    private ExecutorService persistentMergeExecutor;
    private ExecutorService transientMergeExecutor;
    private AtomicReference<MiruLocalHostedPartition.BootstrapRunnable> bootstrapRunnable;
    private AtomicReference<MiruLocalHostedPartition.RebuildIndexRunnable> rebuildIndexRunnable;
    private AtomicReference<MiruLocalHostedPartition.SipMigrateIndexRunnable> sipMigrateIndexRunnable;
    private MiruPartitionedActivityFactory factory;
    private MiruPartitionId partitionId;
    private MiruTenantId tenantId;
    private MiruHost host;
    private MiruBitmapsRoaringBuffer bitmaps;
    private TrackError trackError;
    private MiruIndexer<MutableRoaringBitmap, ImmutableRoaringBitmap> indexer;
    private MiruLocalHostedPartition.Timings timings;
    private long topologyIsStaleAfterMillis = TimeUnit.HOURS.toMillis(1);

    @BeforeMethod
    public void setUp() throws Exception {
        tenantId = new MiruTenantId("test".getBytes(Charsets.UTF_8));
        partitionId = MiruPartitionId.of(0);
        host = new MiruHost("logicalName");
        coord = new MiruPartitionCoord(tenantId, partitionId, host);
        defaultStorage = MiruBackingStorage.memory;

        HealthFactory.initialize(
            BindInterfaceToConfiguration::bindDefault,
            new HealthCheckRegistry() {
            @Override
            public void register(HealthChecker healthChecker) {
            }

            @Override
            public void unregister(HealthChecker healthChecker) {
            }
        });

        MiruServiceConfig config = mock(MiruServiceConfig.class);
        when(config.getBitsetBufferSize()).thenReturn(32);
        when(config.getPartitionNumberOfChunkStores()).thenReturn(1);
        when(config.getPartitionDeleteChunkStoreOnClose()).thenReturn(false);

        schema = new MiruSchema.Builder("test", 1)
            .setFieldDefinitions(DefaultMiruSchemaDefinition.FIELDS)
            .build();
        schemaProvider = mock(MiruSchemaProvider.class);
        when(schemaProvider.getSchema(any(MiruTenantId.class))).thenReturn(schema);

        bitmaps = new MiruBitmapsRoaringBuffer();
        trackError = new PartitionErrorTracker(BindInterfaceToConfiguration.bindDefault(PartitionErrorTracker.PartitionErrorTrackerConfig.class)).track(coord);

        indexer = new MiruIndexer<>(new MiruIndexAuthz<>(),
            new MiruIndexPrimaryFields<>(),
            new MiruIndexValueBits<>(),
            new MiruIndexBloom<>(new BloomIndex<>(bitmaps, Hashing.murmur3_128(), 100_000, 0.01f)),
            new MiruIndexLatest<>(),
            new MiruIndexPairedLatest<>());
        timings = new MiruLocalHostedPartition.Timings(5_000, 5_000, 5_000, 30_000, 3_000, 30_000, 300_000);

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

        MiruTermComposer termComposer = new MiruTermComposer(Charsets.UTF_8, termInterner);
        MiruActivityInternExtern activityInternExtern = new MiruActivityInternExtern(
            ibaInterner,
            tenantInterner,
            // makes sense to share string internment as this is authz in both cases
            Interners.<String>newWeakInterner(),
            termComposer);

        MiruTempDirectoryResourceLocator resourceLocator = new MiruTempDirectoryResourceLocator();
        MiruChunkAllocator hybridContextAllocator = new InMemoryChunkAllocator(
            resourceLocator,
            new HeapByteBufferFactory(),
            new HeapByteBufferFactory(),
            4_096,
            config.getPartitionNumberOfChunkStores(),
            config.getPartitionDeleteChunkStoreOnClose(),
            100,
            1_000,
            useLabIndexes);

        MiruChunkAllocator diskContextAllocator = new OnDiskChunkAllocator(
            resourceLocator,
            new HeapByteBufferFactory(),
            config.getPartitionNumberOfChunkStores(),
            100,
            1_000);

        TxCogs cogs = new TxCogs(256, 64, null, null, null);
        ObjectMapper mapper = new ObjectMapper();

        OrderIdProvider idProvider = new OrderIdProviderImpl(new ConstantWriterIdProvider(0));
        contextFactory = new MiruContextFactory<>(idProvider,
            cogs,
            cogs,
            schemaProvider,
            termComposer,
            activityInternExtern,
            ImmutableMap.<MiruBackingStorage, MiruChunkAllocator>builder()
            .put(MiruBackingStorage.memory, hybridContextAllocator)
            .put(MiruBackingStorage.disk, diskContextAllocator)
            .build(),
            new RCVSSipIndexMarshaller(),
            resourceLocator,
            config.getPartitionAuthzCacheSize(),
            new StripingLocksProvider<>(8),
            new StripingLocksProvider<>(8),
            new StripingLocksProvider<>(8),
            new PartitionErrorTracker(BindInterfaceToConfiguration.bindDefault(PartitionErrorTracker.PartitionErrorTrackerConfig.class)),
            termInterner,
            mapper,
            true,
            false);
        sipTrackerFactory = new RCVSSipTrackerFactory();

        InMemoryRowColumnValueStoreInitializer inMemoryRowColumnValueStoreInitializer = new InMemoryRowColumnValueStoreInitializer();

        RCVSWALInitializer.RCVSWAL wal = new RCVSWALInitializer().initialize("test", inMemoryRowColumnValueStoreInitializer, mapper);

        HostPortProvider hostPortProvider = host -> 10_000;

        MiruActivityWALWriter activityWALWriter = new RCVSActivityWALWriter(wal.getActivityWAL(), wal.getActivitySipWAL());
        MiruActivityWALReader<RCVSCursor, RCVSSipCursor> activityWALReader = new RCVSActivityWALReader(hostPortProvider,
            wal.getActivityWAL(),
            wal.getActivitySipWAL());
        MiruReadTrackingWALWriter readTrackingWALWriter = new RCVSReadTrackingWALWriter(wal.getReadTrackingWAL(), wal.getReadTrackingSipWAL());
        MiruReadTrackingWALReader<RCVSCursor, RCVSSipCursor> readTrackingWALReader = new RCVSReadTrackingWALReader(hostPortProvider,
            wal.getReadTrackingWAL(),
            wal.getReadTrackingSipWAL());
        MiruWALLookup walLookup = new RCVSWALLookup(wal.getWALLookupTable());

        File amzaDataDir = Files.createTempDir();
        MiruAmzaServiceConfig acrc = BindInterfaceToConfiguration.bindDefault(MiruAmzaServiceConfig.class);
        acrc.setWorkingDirectories(amzaDataDir.getAbsolutePath());
        Deployable deployable = new Deployable(new String[0]);
        AmzaService amzaService = new MiruAmzaServiceInitializer().initialize(deployable, "routesHosts", 1, "connectionHealthPath",
            1, "instanceKey", "serviceName", "datacenter", "rack", "localhost", 10000, null, acrc,
            rowsChanged -> {
            });

        EmbeddedClientProvider clientProvider = new EmbeddedClientProvider(amzaService);

        clusterRegistry = new AmzaClusterRegistry(amzaService,
            clientProvider,
            10_000L,
            new JacksonJsonObjectTypeMarshaller<>(MiruSchema.class, mapper),
            3,
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.HOURS.toMillis(1),
            TimeUnit.DAYS.toMillis(365),
            1);

        OrderIdProvider orderIdProvider = new OrderIdProviderImpl(new ConstantWriterIdProvider(0), new SnowflakeIdPacker(), new JiveEpochTimestampProvider());
        MiruReplicaSetDirector replicaSetDirector = new MiruReplicaSetDirector(orderIdProvider, clusterRegistry,
            stream -> stream.descriptor("datacenter", "rack", host));
        MiruClusterClient clusterClient = new MiruRegistryClusterClient(clusterRegistry, replicaSetDirector);
        replicaSetDirector.elect(host, tenantId, partitionId, System.currentTimeMillis());

        walClient = new MiruWALDirector<>(walLookup, activityWALReader, activityWALWriter, readTrackingWALReader, readTrackingWALWriter, clusterClient);

        partitionEventHandler = new MiruPartitionHeartbeatHandler(clusterClient);
        rebuildDirector = new MiruRebuildDirector(Long.MAX_VALUE);
        factory = new MiruPartitionedActivityFactory();

        scheduledBootstrapService = mock(ScheduledExecutorService.class);
        scheduledRebuildService = mock(ScheduledExecutorService.class);
        scheduledSipMigrateService = mock(ScheduledExecutorService.class);
        rebuildExecutor = Executors.newSingleThreadExecutor();
        sipIndexExecutor = Executors.newSingleThreadExecutor();
        transientMergeExecutor = Executors.newSingleThreadExecutor();
        persistentMergeExecutor = Executors.newSingleThreadExecutor();

        bootstrapRunnable = new AtomicReference<>();
        captureRunnable(scheduledBootstrapService, bootstrapRunnable, MiruLocalHostedPartition.BootstrapRunnable.class);

        rebuildIndexRunnable = new AtomicReference<>();
        captureRunnable(scheduledRebuildService, rebuildIndexRunnable, MiruLocalHostedPartition.RebuildIndexRunnable.class);

        sipMigrateIndexRunnable = new AtomicReference<>();
        captureRunnable(scheduledSipMigrateService, sipMigrateIndexRunnable, MiruLocalHostedPartition.SipMigrateIndexRunnable.class);

    }

    @Test
    public void testBootstrapToOnline() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        setActive(true);
        waitForRef(bootstrapRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.bootstrap);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.memory);

        waitForRef(rebuildIndexRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.memory);
        waitForRef(sipMigrateIndexRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);

    }

    @Test
    public void testBootstrapToOnlineToUpgradeToOnline() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        setActive(true);
        waitForRef(bootstrapRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.bootstrap);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.memory);

        waitForRef(rebuildIndexRunnable).run();

        assertTrue(localHostedPartition.needsToMigrate());
        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.memory);
        waitForRef(sipMigrateIndexRunnable).run();

        assertFalse(localHostedPartition.needsToMigrate());
        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
        assertTrue(localHostedPartition.rebuild());

        assertEquals(localHostedPartition.getState(), MiruPartitionState.obsolete);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
        waitForRef(rebuildIndexRunnable).run();

        assertTrue(localHostedPartition.needsToMigrate());
        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
        waitForRef(sipMigrateIndexRunnable).run();

        assertFalse(localHostedPartition.needsToMigrate());
        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);

    }

    @Test
    public void testInactiveToOffline() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // enters rebuilding
        waitForRef(sipMigrateIndexRunnable).run(); // enters online mem-mapped

        setActive(false);
        waitForRef(bootstrapRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
    }

    @Test
    public void testQueryHandleOfflineMemMappedHotDeploy() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();
        walClient.writeActivity(tenantId, partitionId, Lists.newArrayList(
            factory.begin(1, partitionId, tenantId, 0),
            factory.end(1, partitionId, tenantId, 0)
        ));

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // enters rebuilding
        waitForRef(sipMigrateIndexRunnable).run(); // enters online memory
        //indexBoundaryActivity(localHostedPartition); // eligible for disk
        waitForRef(sipMigrateIndexRunnable).run(); // enters online disk (hot deployable)

        setActive(false);
        waitForRef(bootstrapRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);

        try (MiruRequestHandle queryHandle = localHostedPartition.acquireQueryHandle()) {
            assertEquals(queryHandle.getCoord(), coord);
            assertNotNull(queryHandle.getRequestContext()); // would throw exception if offline
        }
    }

    @Test(expectedExceptions = MiruPartitionUnavailableException.class)
    public void testQueryHandleOfflineMemoryException() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // enters rebuilding

        setDestroyed(); // need to destroy in order to tear down
        waitForRef(bootstrapRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.memory);

        try (MiruRequestHandle queryHandle = localHostedPartition.acquireQueryHandle()) {
            queryHandle.getRequestContext(); // throws exception
        }
    }

    @Test
    public void testInactiveFinishesRebuilding() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // enters rebuilding

        setActive(false); // won't go offline yet
        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.memory);

        waitForRef(sipMigrateIndexRunnable).run();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);

        waitForRef(bootstrapRunnable).run(); // finally goes offline

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
    }

    @Test
    public void testRemove() throws Exception {
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // enters rebuilding
        waitForRef(sipMigrateIndexRunnable).run(); // enters online memory

        localHostedPartition.remove();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
    }

    @Test
    public void testSchemaNotRegistered_checkActive() throws Exception {
        when(schemaProvider.getSchema(any(MiruTenantId.class))).thenThrow(new MiruSchemaUnvailableException("test"));
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), defaultStorage);

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // stays bootstrap
        waitForRef(sipMigrateIndexRunnable).run(); // stays bootstrap

        assertEquals(localHostedPartition.getState(), MiruPartitionState.bootstrap);
        assertEquals(localHostedPartition.getStorage(), defaultStorage);
    }

    @Test
    public void testSchemaRegisteredLate() throws Exception {
        when(schemaProvider.getSchema(any(MiruTenantId.class))).thenThrow(new MiruSchemaUnvailableException("test"));
        MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> localHostedPartition =
            getRoaringLocalHostedPartition();

        assertEquals(localHostedPartition.getState(), MiruPartitionState.offline);
        assertEquals(localHostedPartition.getStorage(), defaultStorage);

        setActive(true);
        waitForRef(bootstrapRunnable).run(); // enters bootstrap
        waitForRef(rebuildIndexRunnable).run(); // stays bootstrap
        waitForRef(sipMigrateIndexRunnable).run(); // stays bootstrap

        assertEquals(localHostedPartition.getState(), MiruPartitionState.bootstrap);
        assertEquals(localHostedPartition.getStorage(), defaultStorage);

        reset(schemaProvider);
        when(schemaProvider.getSchema(any(MiruTenantId.class))).thenReturn(schema);

        waitForRef(bootstrapRunnable).run(); // stays bootstrap
        waitForRef(rebuildIndexRunnable).run(); // enters rebuilding, online memory
        waitForRef(sipMigrateIndexRunnable).run(); // enters online disk

        assertEquals(localHostedPartition.getState(), MiruPartitionState.online);
        assertEquals(localHostedPartition.getStorage(), MiruBackingStorage.disk);
    }

    private MiruLocalHostedPartition<MutableRoaringBitmap, ImmutableRoaringBitmap, RCVSCursor, RCVSSipCursor> getRoaringLocalHostedPartition()
        throws Exception {
        AtomicLong numberOfChitsRemaining = new AtomicLong(100_000);
        MiruMergeChits persistentMergeChits = new OrderedMergeChits("persistent", numberOfChitsRemaining, 100_000, 10_000);
        MiruMergeChits transientMergeChits = new OrderedMergeChits("transient", numberOfChitsRemaining, 100_000, 10_000);

        return new MiruLocalHostedPartition<>(new MiruStats(), bitmaps, trackError, coord, -1, contextFactory, sipTrackerFactory,
            walClient, partitionEventHandler, rebuildDirector, scheduledBootstrapService, scheduledRebuildService,
            scheduledSipMigrateService, rebuildExecutor, sipIndexExecutor, persistentMergeExecutor, transientMergeExecutor, 1, new NoOpMiruIndexRepairs(),
            indexer, true, 100, 100, persistentMergeChits, transientMergeChits, timings);
    }

    private void setActive(boolean active) throws Exception {
        partitionEventHandler.thumpthump(host); // flush any heartbeats
        long refreshTimestamp = System.currentTimeMillis();
        if (!active) {
            refreshTimestamp -= topologyIsStaleAfterMillis * 2;
        }
        clusterRegistry.updateIngress(new MiruIngressUpdate(tenantId, partitionId, new RangeMinMax(), refreshTimestamp, false));
        partitionEventHandler.thumpthump(host);
    }

    private void setDestroyed() throws Exception {
        clusterRegistry.destroyPartition(tenantId, partitionId);
        partitionEventHandler.thumpthump(host);
    }

    private void indexNormalActivity(MiruLocalHostedPartition localHostedPartition) throws Exception {
        localHostedPartition.index(Lists.newArrayList(
            factory.activity(1, partitionId, 0, new MiruActivity(
                tenantId, System.currentTimeMillis(), new String[0], 0,
                Collections.<String, List<String>>emptyMap(),
                Collections.<String, List<String>>emptyMap()))
        ).iterator());
    }

    private void indexBoundaryActivity(MiruLocalHostedPartition localHostedPartition) throws Exception {
        localHostedPartition.index(Lists.newArrayList(
            factory.begin(1, partitionId, tenantId, 0),
            factory.end(1, partitionId, tenantId, 0)
        ).iterator());
    }

    private <T extends Runnable> void captureRunnable(ScheduledExecutorService scheduledExecutor, final AtomicReference<T> ref, Class<T> runnableClass) {
        when(scheduledExecutor.scheduleWithFixedDelay(isA(runnableClass), anyLong(), anyLong(), any(TimeUnit.class)))
            .thenAnswer(invocation -> {
                ref.set((T) invocation.getArguments()[0]);
                return mockFuture();
            });
    }

    private <T> T waitForRef(AtomicReference<T> ref) throws InterruptedException {
        for (int i = 0; i < 100 && ref.get() == null; i++) {
            Thread.sleep(10);
        }
        if (ref.get() == null) {
            Assert.fail("Ref never caught");
        }
        return ref.get();
    }

    private ScheduledFuture<?> mockFuture() {
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        return future;
    }
}
