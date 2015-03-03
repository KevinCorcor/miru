package com.jivesoftware.os.miru.plugin.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.Interners;
import com.google.common.io.Files;
import com.jivesoftware.os.amza.service.AmzaService;
import com.jivesoftware.os.jive.utils.health.api.HealthCheckConfigBinder;
import com.jivesoftware.os.jive.utils.health.api.HealthCheckRegistry;
import com.jivesoftware.os.jive.utils.health.api.HealthChecker;
import com.jivesoftware.os.jive.utils.health.api.HealthFactory;
import com.jivesoftware.os.jive.utils.http.client.HttpClientConfiguration;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactory;
import com.jivesoftware.os.jive.utils.http.client.HttpClientFactoryProvider;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.miru.api.MiruBackingStorage;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruLifecyle;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruPartitionCoordInfo;
import com.jivesoftware.os.miru.api.MiruPartitionState;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruIBA;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.base.MiruTermId;
import com.jivesoftware.os.miru.api.marshall.JacksonJsonObjectTypeMarshaller;
import com.jivesoftware.os.miru.api.topology.MiruReplicaHosts;
import com.jivesoftware.os.miru.cluster.MiruClusterRegistry;
import com.jivesoftware.os.miru.cluster.MiruRegistryClusterClient;
import com.jivesoftware.os.miru.cluster.MiruRegistryStore;
import com.jivesoftware.os.miru.cluster.MiruRegistryStoreInitializer;
import com.jivesoftware.os.miru.cluster.amza.AmzaClusterRegistry;
import com.jivesoftware.os.miru.cluster.amza.AmzaClusterRegistryInitializer;
import com.jivesoftware.os.miru.cluster.amza.AmzaClusterRegistryInitializer.AmzaClusterRegistryConfig;
import com.jivesoftware.os.miru.cluster.client.MiruReplicaSetDirector;
import com.jivesoftware.os.miru.cluster.rcvs.MiruRCVSClusterRegistry;
import com.jivesoftware.os.miru.plugin.MiruProvider;
import com.jivesoftware.os.miru.plugin.bitmap.MiruBitmaps;
import com.jivesoftware.os.miru.plugin.bitmap.SingleBitmapsProvider;
import com.jivesoftware.os.miru.plugin.index.MiruActivityInternExtern;
import com.jivesoftware.os.miru.plugin.index.MiruJustInTimeBackfillerizer;
import com.jivesoftware.os.miru.plugin.index.MiruTermComposer;
import com.jivesoftware.os.miru.plugin.schema.SingleSchemaProvider;
import com.jivesoftware.os.miru.service.MiruBackfillerizerInitializer;
import com.jivesoftware.os.miru.service.MiruService;
import com.jivesoftware.os.miru.service.MiruServiceConfig;
import com.jivesoftware.os.miru.service.MiruServiceInitializer;
import com.jivesoftware.os.miru.service.locator.MiruTempDirectoryResourceLocator;
import com.jivesoftware.os.miru.wal.MiruWALInitializer;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALWriter;
import com.jivesoftware.os.miru.wal.activity.MiruWriteToActivityAndSipWAL;
import com.jivesoftware.os.rcvs.api.timestamper.CurrentTimestamper;
import com.jivesoftware.os.rcvs.inmemory.InMemoryRowColumnValueStoreInitializer;
import com.jivesoftware.os.upena.main.Deployable;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.merlin.config.BindInterfaceToConfiguration;
import org.merlin.config.Config;
import org.testng.Assert;

/**
 *
 */
public class MiruPluginTestBootstrap {

    public <BM> MiruProvider<MiruService> bootstrap(MiruTenantId tenantId,
        MiruPartitionId partitionId,
        MiruHost miruHost,
        MiruSchema miruSchema,
        MiruBackingStorage desiredStorage,
        final MiruBitmaps<BM> bitmaps,
        List<MiruPartitionedActivity> partitionedActivities)
        throws Exception {

        HealthFactory.initialize(
            new HealthCheckConfigBinder() {
                @Override
                public <C extends Config> C bindConfig(Class<C> configurationInterfaceClass) {
                    return BindInterfaceToConfiguration.bindDefault(configurationInterfaceClass);
                }
            },
            new HealthCheckRegistry() {
                @Override
                public void register(HealthChecker healthChecker) {
                }

                @Override
                public void unregister(HealthChecker healthChecker) {
                }
            });

        MiruServiceConfig config = BindInterfaceToConfiguration.bindDefault(MiruServiceConfig.class);
        config.setDefaultStorage(desiredStorage.name());
        config.setDefaultFailAfterNMillis(TimeUnit.HOURS.toMillis(1));
        config.setMergeChitCount(10_000);

        HttpClientFactory httpClientFactory = new HttpClientFactoryProvider()
            .createHttpClientFactory(Collections.<HttpClientConfiguration>emptyList());

        ObjectMapper mapper = new ObjectMapper();

        InMemoryRowColumnValueStoreInitializer inMemoryRowColumnValueStoreInitializer = new InMemoryRowColumnValueStoreInitializer();
        MiruRegistryStore registryStore = new MiruRegistryStoreInitializer().initialize("test", inMemoryRowColumnValueStoreInitializer, mapper);

        MiruClusterRegistry clusterRegistry = null;

        boolean useAmza = true;
        if (useAmza) {
            File amzaDir = Files.createTempDir();
            AmzaClusterRegistryConfig acrc = BindInterfaceToConfiguration.bindDefault(AmzaClusterRegistryConfig.class);
            acrc.setWorkingDirectory(amzaDir.getAbsolutePath());
            acrc.setReplicationFactor(0);
            acrc.setTakeFromFactor(0);
            Deployable deployable = new Deployable(new String[0]);
            AmzaService amzaService = new AmzaClusterRegistryInitializer().initialize(deployable, 1, "localhost", 10000, "test-cluster", acrc);
            clusterRegistry = new AmzaClusterRegistry(amzaService,
                new JacksonJsonObjectTypeMarshaller<>(MiruSchema.class, mapper),
                3,
                TimeUnit.HOURS.toMillis(1),
                TimeUnit.HOURS.toMillis(1));
        } else {
            clusterRegistry = new MiruRCVSClusterRegistry(
                new CurrentTimestamper(),
                registryStore.getHostsRegistry(),
                registryStore.getExpectedTenantsRegistry(),
                registryStore.getTopologyUpdatesRegistry(),
                registryStore.getExpectedTenantPartitionsRegistry(),
                registryStore.getReplicaRegistry(),
                registryStore.getTopologyRegistry(),
                registryStore.getConfigRegistry(),
                registryStore.getSchemaRegistry(),
                3,
                TimeUnit.HOURS.toMillis(1),
                TimeUnit.HOURS.toMillis(1));
        }

        MiruRegistryClusterClient clusterClient = new MiruRegistryClusterClient(clusterRegistry);
        MiruReplicaSetDirector replicaSetDirector = new MiruReplicaSetDirector(new OrderIdProviderImpl(new ConstantWriterIdProvider(1)), clusterClient);

        clusterRegistry.sendHeartbeatForHost(miruHost);
        replicaSetDirector.electToReplicaSetForTenantPartition(tenantId, partitionId,
            new MiruReplicaHosts(false, new HashSet<MiruHost>(), 3),
            System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        clusterRegistry.updateTopology(new MiruPartitionCoord(tenantId, partitionId, miruHost), Optional.<MiruPartitionCoordInfo>absent(),
            Optional.of(System.currentTimeMillis()));

        MiruWALInitializer.MiruWAL wal = new MiruWALInitializer().initialize("test", inMemoryRowColumnValueStoreInitializer, mapper);
        if (!partitionedActivities.isEmpty()) {
            MiruActivityWALWriter activityWALWriter = new MiruWriteToActivityAndSipWAL(wal.getActivityWAL(), wal.getActivitySipWAL());
            activityWALWriter.write(tenantId, partitionedActivities);
        }

        MiruLifecyle<MiruJustInTimeBackfillerizer> backfillerizerLifecycle = new MiruBackfillerizerInitializer().initialize(config, miruHost);

        backfillerizerLifecycle.start();
        final MiruJustInTimeBackfillerizer backfillerizer = backfillerizerLifecycle.getService();

        final MiruTermComposer termComposer = new MiruTermComposer(Charsets.UTF_8);
        final MiruActivityInternExtern activityInternExtern = new MiruActivityInternExtern(Interners.<MiruIBA>newWeakInterner(),
            Interners.<MiruTermId>newWeakInterner(),
            Interners.<MiruTenantId>newWeakInterner(),
            Interners.<String>newWeakInterner(),
            termComposer);

        MiruLifecyle<MiruService> miruServiceLifecyle = new MiruServiceInitializer().initialize(config,
            clusterClient,
            miruHost,
            new SingleSchemaProvider(miruSchema),
            wal,
            httpClientFactory,
            new MiruTempDirectoryResourceLocator(),
            termComposer,
            activityInternExtern,
            new SingleBitmapsProvider<>(bitmaps));

        miruServiceLifecyle.start();
        final MiruService miruService = miruServiceLifecyle.getService();

        long t = System.currentTimeMillis();
        int maxSecondsToComeOnline = 5 + partitionedActivities.size() / 1_000; // suppose 1K activities/sec
        while (!miruService.checkInfo(tenantId, partitionId, new MiruPartitionCoordInfo(MiruPartitionState.online, desiredStorage))) {
            Thread.sleep(10);
            if (System.currentTimeMillis() - t > TimeUnit.SECONDS.toMillis(maxSecondsToComeOnline)) {
                Assert.fail("Partition failed to come online");
            }
        }

        return new MiruProvider<MiruService>() {
            @Override
            public MiruService getMiru(MiruTenantId tenantId) {
                return miruService;
            }

            @Override
            public MiruActivityInternExtern getActivityInternExtern(MiruTenantId tenantId) {
                return activityInternExtern;
            }

            @Override
            public MiruJustInTimeBackfillerizer getBackfillerizer(MiruTenantId tenantId) {
                return backfillerizer;
            }

            @Override
            public MiruTermComposer getTermComposer() {
                return termComposer;
            }
        };
    }
}
