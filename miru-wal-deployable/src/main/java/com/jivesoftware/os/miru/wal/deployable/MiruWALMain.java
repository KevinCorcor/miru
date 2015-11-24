/*
 * Copyright 2013 Jive Software, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.jivesoftware.os.miru.wal.deployable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.jivesoftware.os.amza.client.AmzaClientProvider;
import com.jivesoftware.os.amza.service.AmzaService;
import com.jivesoftware.os.amza.shared.partition.PartitionProperties;
import com.jivesoftware.os.amza.shared.partition.PrimaryIndexDescriptor;
import com.jivesoftware.os.amza.shared.wal.WALStorageDescriptor;
import com.jivesoftware.os.miru.amza.MiruAmzaServiceConfig;
import com.jivesoftware.os.miru.amza.MiruAmzaServiceInitializer;
import com.jivesoftware.os.miru.api.HostPortProvider;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.topology.MiruClusterClient;
import com.jivesoftware.os.miru.api.wal.AmzaCursor;
import com.jivesoftware.os.miru.api.wal.AmzaSipCursor;
import com.jivesoftware.os.miru.api.wal.MiruWALConfig;
import com.jivesoftware.os.miru.api.wal.RCVSCursor;
import com.jivesoftware.os.miru.api.wal.RCVSSipCursor;
import com.jivesoftware.os.miru.cluster.client.MiruClusterClientInitializer;
import com.jivesoftware.os.miru.logappender.MiruLogAppender;
import com.jivesoftware.os.miru.logappender.MiruLogAppenderInitializer;
import com.jivesoftware.os.miru.logappender.RoutingBirdLogSenderProvider;
import com.jivesoftware.os.miru.metric.sampler.MiruMetricSampler;
import com.jivesoftware.os.miru.metric.sampler.MiruMetricSamplerInitializer;
import com.jivesoftware.os.miru.metric.sampler.MiruMetricSamplerInitializer.MiruMetricSamplerConfig;
import com.jivesoftware.os.miru.metric.sampler.RoutingBirdMetricSampleSenderProvider;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.miru.ui.MiruSoyRendererInitializer;
import com.jivesoftware.os.miru.ui.MiruSoyRendererInitializer.MiruSoyRendererConfig;
import com.jivesoftware.os.miru.wal.AmzaWALUtil;
import com.jivesoftware.os.miru.wal.MiruWALDirector;
import com.jivesoftware.os.miru.wal.RCVSWALInitializer;
import com.jivesoftware.os.miru.wal.activity.ForkingActivityWALWriter;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.amza.AmzaActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.amza.AmzaActivityWALWriter;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.rcvs.RCVSActivityWALWriter;
import com.jivesoftware.os.miru.wal.deployable.endpoints.AmzaWALEndpoints;
import com.jivesoftware.os.miru.wal.deployable.endpoints.RCVSWALEndpoints;
import com.jivesoftware.os.miru.wal.lookup.AmzaWALLookup;
import com.jivesoftware.os.miru.wal.lookup.ForkingWALLookup;
import com.jivesoftware.os.miru.wal.lookup.RCVSWALLookup;
import com.jivesoftware.os.miru.wal.readtracking.ForkingReadTrackingWALWriter;
import com.jivesoftware.os.miru.wal.readtracking.amza.AmzaReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.amza.AmzaReadTrackingWALWriter;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALReader;
import com.jivesoftware.os.miru.wal.readtracking.rcvs.RCVSReadTrackingWALWriter;
import com.jivesoftware.os.rcvs.api.RowColumnValueStoreInitializer;
import com.jivesoftware.os.rcvs.api.RowColumnValueStoreProvider;
import com.jivesoftware.os.routing.bird.deployable.Deployable;
import com.jivesoftware.os.routing.bird.deployable.InstanceConfig;
import com.jivesoftware.os.routing.bird.endpoints.base.HasUI;
import com.jivesoftware.os.routing.bird.health.api.HealthCheckRegistry;
import com.jivesoftware.os.routing.bird.health.api.HealthChecker;
import com.jivesoftware.os.routing.bird.health.api.HealthFactory;
import com.jivesoftware.os.routing.bird.health.checkers.GCLoadHealthChecker;
import com.jivesoftware.os.routing.bird.health.checkers.ServiceStartupHealthCheck;
import com.jivesoftware.os.routing.bird.http.client.HttpDeliveryClientHealthProvider;
import com.jivesoftware.os.routing.bird.http.client.HttpRequestHelperUtils;
import com.jivesoftware.os.routing.bird.http.client.TenantAwareHttpClient;
import com.jivesoftware.os.routing.bird.http.client.TenantRoutingHttpClientInitializer;
import com.jivesoftware.os.routing.bird.server.util.Resource;
import com.jivesoftware.os.routing.bird.shared.ConnectionDescriptor;
import com.jivesoftware.os.routing.bird.shared.HostPort;
import com.jivesoftware.os.routing.bird.shared.TenantRoutingProvider;
import com.jivesoftware.os.routing.bird.shared.TenantsServiceConnectionDescriptorProvider;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.merlin.config.defaults.IntDefault;
import org.merlin.config.defaults.LongDefault;
import org.merlin.config.defaults.StringDefault;

public class MiruWALMain {

    public static void main(String[] args) throws Exception {
        new MiruWALMain().run(args);
    }

    public interface WALAmzaServiceConfig extends MiruAmzaServiceConfig {

        @StringDefault("./var/amza/wal/data/")
        @Override
        String getWorkingDirectories();

        @StringDefault("./var/amza/wal/index/")
        @Override
        String getIndexDirectories();

        @StringDefault("225.5.6.23")
        @Override
        String getAmzaDiscoveryGroup();

        @IntDefault(1223)
        @Override
        int getAmzaDiscoveryPort();

        @IntDefault(1)
        int getReplicateLookupQuorum();

        @IntDefault(1)
        int getReplicateActivityQuorum();

        @IntDefault(1)
        int getReplicateReadTrackingQuorum();

        @LongDefault(60_000L)
        long getReplicateTimeoutMillis();
    }

    public void run(String[] args) throws Exception {
        ServiceStartupHealthCheck serviceStartupHealthCheck = new ServiceStartupHealthCheck();
        try {
            final Deployable deployable = new Deployable(args);
            HealthFactory.initialize(deployable::config,
                new HealthCheckRegistry() {

                @Override
                public void register(HealthChecker healthChecker) {
                    deployable.addHealthCheck(healthChecker);
                }

                @Override
                public void unregister(HealthChecker healthChecker) {
                    throw new UnsupportedOperationException("Not supported yet.");
                }
            });
            deployable.addErrorHealthChecks();
            deployable.addManageInjectables(HasUI.class, new HasUI(Arrays.asList(
                new HasUI.UI("Reset Errors", "manage", "/manage/resetErrors"),
                new HasUI.UI("Tail", "manage", "/manage/tail"),
                new HasUI.UI("Thread Dump", "manage", "/manage/threadDump"),
                new HasUI.UI("Health", "manage", "/manage/ui"),
                new HasUI.UI("Miru-WAL", "main", "/miru/wal"),
                new HasUI.UI("Miru-WAL-Amza", "main", "/amza"))));

            deployable.buildStatusReporter(null).start();
            deployable.addHealthCheck(new GCLoadHealthChecker(deployable.config(GCLoadHealthChecker.GCLoadHealthCheckerConfig.class)));
            deployable.addHealthCheck(serviceStartupHealthCheck);
            deployable.buildManageServer().start();

            InstanceConfig instanceConfig = deployable.config(InstanceConfig.class);

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.registerModule(new GuavaModule());

            MiruLogAppenderInitializer.MiruLogAppenderConfig miruLogAppenderConfig = deployable.config(MiruLogAppenderInitializer.MiruLogAppenderConfig.class);
            TenantsServiceConnectionDescriptorProvider logConnections = deployable.getTenantRoutingProvider().getConnections("miru-stumptown", "main");
            MiruLogAppender miruLogAppender = new MiruLogAppenderInitializer().initialize(null, //TODO datacenter
                instanceConfig.getClusterName(),
                instanceConfig.getHost(),
                instanceConfig.getServiceName(),
                String.valueOf(instanceConfig.getInstanceName()),
                instanceConfig.getVersion(),
                miruLogAppenderConfig,
                new RoutingBirdLogSenderProvider<>(logConnections, "", miruLogAppenderConfig.getSocketTimeoutInMillis()));
            miruLogAppender.install();

            MiruMetricSamplerConfig metricSamplerConfig = deployable.config(MiruMetricSamplerConfig.class);
            TenantsServiceConnectionDescriptorProvider metricConnections = deployable.getTenantRoutingProvider().getConnections("miru-anomaly", "main");
            MiruMetricSampler sampler = new MiruMetricSamplerInitializer().initialize(null, //TODO datacenter
                instanceConfig.getClusterName(),
                instanceConfig.getHost(),
                instanceConfig.getServiceName(),
                String.valueOf(instanceConfig.getInstanceName()),
                instanceConfig.getVersion(),
                metricSamplerConfig,
                new RoutingBirdMetricSampleSenderProvider<>(metricConnections, "", miruLogAppenderConfig.getSocketTimeoutInMillis()));
            sampler.start();

            RCVSWALConfig rcvsWALConfig = deployable.config(RCVSWALConfig.class);
            MiruWALConfig walConfig = deployable.config(MiruWALConfig.class);

            RowColumnValueStoreInitializer<? extends Exception> rowColumnValueStoreInitializer = null;
            if (RowColumnValueStoreProvider.class.isAssignableFrom(rcvsWALConfig.getRowColumnValueStoreProviderClass())) {
                RowColumnValueStoreProvider rowColumnValueStoreProvider = rcvsWALConfig.getRowColumnValueStoreProviderClass().newInstance();
                @SuppressWarnings("unchecked")
                RowColumnValueStoreInitializer<? extends Exception> initializer = rowColumnValueStoreProvider
                    .create(deployable.config(rowColumnValueStoreProvider.getConfigurationClass()));
                rowColumnValueStoreInitializer = initializer;
            }

            MiruStats miruStats = new MiruStats();

            WALAmzaServiceConfig amzaServiceConfig = deployable.config(WALAmzaServiceConfig.class);
            AmzaService amzaService = new MiruAmzaServiceInitializer().initialize(deployable,
                instanceConfig.getInstanceName(),
                instanceConfig.getInstanceKey(),
                instanceConfig.getServiceName(),
                instanceConfig.getHost(),
                instanceConfig.getMainPort(),
                null, //"miru-wal-" + instanceConfig.getClusterName(),
                amzaServiceConfig,
                changes -> {
                });
            WALStorageDescriptor storageDescriptor = new WALStorageDescriptor(new PrimaryIndexDescriptor("berkeleydb", 0, false, null),
                null, 1000, 1000);

            AmzaClientProvider amzaClientProvider = new AmzaClientProvider(amzaService);
            AmzaWALUtil amzaWALUtil = new AmzaWALUtil(amzaService,
                amzaClientProvider,
                new PartitionProperties(storageDescriptor, amzaServiceConfig.getTakeFromFactor(), false));

            HttpDeliveryClientHealthProvider clientHealthProvider = new HttpDeliveryClientHealthProvider(instanceConfig.getInstanceKey(),
                HttpRequestHelperUtils.buildRequestHelper(instanceConfig.getRoutesHost(), instanceConfig.getRoutesPort()),
                instanceConfig.getConnectionsHealth(), 5_000, 100);

            TenantRoutingProvider tenantRoutingProvider = deployable.getTenantRoutingProvider();
            TenantsServiceConnectionDescriptorProvider walConnectionDescriptorProvider = tenantRoutingProvider.getConnections("miru-wal", "main");
            HostPortProvider hostPortProvider = host -> {
                //TODO cache?
                List<ConnectionDescriptor> connectionDescriptors = walConnectionDescriptorProvider.getConnections("").getConnectionDescriptors();
                for (ConnectionDescriptor connectionDescriptor : connectionDescriptors) {
                    HostPort hostPort = connectionDescriptor.getHostPort();
                    if (hostPort.getHost().equals(host)) {
                        return hostPort.getPort();
                    }
                }
                return -1;
            };

            TenantRoutingHttpClientInitializer<String> tenantRoutingHttpClientInitializer = new TenantRoutingHttpClientInitializer<>();
            TenantAwareHttpClient<String> manageHttpClient = tenantRoutingHttpClientInitializer.initialize(tenantRoutingProvider
                .getConnections("miru-manage", "main"),
                clientHealthProvider,
                10, 10_000); // TODO expose to conf

            MiruClusterClient clusterClient = new MiruClusterClientInitializer().initialize(miruStats, "", manageHttpClient, mapper);

            MiruActivityWALReader<?, ?> activityWALReader;
            MiruWALDirector<RCVSCursor, RCVSSipCursor> rcvsWALDirector = null;
            MiruWALDirector<AmzaCursor, AmzaSipCursor> amzaWALDirector = null;
            MiruWALDirector<?, ?> miruWALDirector;
            Class<?> walEndpointsClass;

            if (walConfig.getActivityWALType().equals("rcvs")) {
                RCVSWALInitializer.RCVSWAL rcvsWAL = new RCVSWALInitializer().initialize(instanceConfig.getClusterName(),
                    rowColumnValueStoreInitializer,
                    mapper);

                RCVSActivityWALWriter rcvsActivityWALWriter = new RCVSActivityWALWriter(rcvsWAL.getActivityWAL(), rcvsWAL.getActivitySipWAL());
                RCVSActivityWALReader rcvsActivityWALReader = new RCVSActivityWALReader(hostPortProvider,
                    rcvsWAL.getActivityWAL(),
                    rcvsWAL.getActivitySipWAL());
                RCVSWALLookup rcvsWALLookup = new RCVSWALLookup(rcvsWAL.getWALLookupTable());

                RCVSReadTrackingWALWriter readTrackingWALWriter = new RCVSReadTrackingWALWriter(rcvsWAL.getReadTrackingWAL(), rcvsWAL.getReadTrackingSipWAL());
                RCVSReadTrackingWALReader readTrackingWALReader = new RCVSReadTrackingWALReader(hostPortProvider,
                    rcvsWAL.getReadTrackingWAL(),
                    rcvsWAL.getReadTrackingSipWAL());

                rcvsWALDirector = new MiruWALDirector<>(rcvsWALLookup,
                    rcvsActivityWALReader,
                    rcvsActivityWALWriter,
                    readTrackingWALReader,
                    readTrackingWALWriter,
                    clusterClient);

                activityWALReader = rcvsActivityWALReader;
                miruWALDirector = rcvsWALDirector;
                walEndpointsClass = RCVSWALEndpoints.class;
            } else if (walConfig.getActivityWALType().equals("amza")) {
                AmzaActivityWALWriter amzaActivityWALWriter = new AmzaActivityWALWriter(amzaWALUtil,
                    amzaServiceConfig.getReplicateActivityQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis(),
                    mapper);
                AmzaActivityWALReader amzaActivityWALReader = new AmzaActivityWALReader(amzaWALUtil, mapper);
                AmzaWALLookup amzaWALLookup = new AmzaWALLookup(amzaWALUtil,
                    amzaServiceConfig.getReplicateLookupQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis());

                AmzaReadTrackingWALWriter readTrackingWALWriter = new AmzaReadTrackingWALWriter(amzaWALUtil,
                    amzaServiceConfig.getReplicateReadTrackingQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis(),
                    mapper);
                AmzaReadTrackingWALReader readTrackingWALReader = new AmzaReadTrackingWALReader(amzaWALUtil, mapper);

                amzaWALDirector = new MiruWALDirector<>(amzaWALLookup,
                    amzaActivityWALReader,
                    amzaActivityWALWriter,
                    readTrackingWALReader,
                    readTrackingWALWriter,
                    clusterClient);

                activityWALReader = amzaActivityWALReader;
                miruWALDirector = amzaWALDirector;
                walEndpointsClass = AmzaWALEndpoints.class;
            } else if (walConfig.getActivityWALType().equals("rcvs_amza")) {
                RCVSWALInitializer.RCVSWAL rcvsWAL = new RCVSWALInitializer().initialize(instanceConfig.getClusterName(),
                    rowColumnValueStoreInitializer,
                    mapper);

                RCVSActivityWALWriter rcvsActivityWALWriter = new RCVSActivityWALWriter(rcvsWAL.getActivityWAL(), rcvsWAL.getActivitySipWAL());
                AmzaActivityWALWriter amzaActivityWALWriter = new AmzaActivityWALWriter(amzaWALUtil,
                    amzaServiceConfig.getReplicateActivityQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis(),
                    mapper);
                ForkingActivityWALWriter forkingActivityWALWriter = new ForkingActivityWALWriter(rcvsActivityWALWriter, amzaActivityWALWriter);
                RCVSActivityWALReader rcvsActivityWALReader = new RCVSActivityWALReader(hostPortProvider,
                    rcvsWAL.getActivityWAL(),
                    rcvsWAL.getActivitySipWAL());

                RCVSWALLookup rcvsWALLookup = new RCVSWALLookup(rcvsWAL.getWALLookupTable());
                AmzaWALLookup amzaWALLookup = new AmzaWALLookup(amzaWALUtil,
                    amzaServiceConfig.getReplicateLookupQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis());
                ForkingWALLookup forkingWALLookup = new ForkingWALLookup(rcvsWALLookup, amzaWALLookup);

                RCVSReadTrackingWALWriter rcvsReadTrackingWALWriter = new RCVSReadTrackingWALWriter(rcvsWAL.getReadTrackingWAL(),
                    rcvsWAL.getReadTrackingSipWAL());
                AmzaReadTrackingWALWriter amzaReadTrackingWALWriter = new AmzaReadTrackingWALWriter(amzaWALUtil,
                    amzaServiceConfig.getReplicateReadTrackingQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis(),
                    mapper);
                ForkingReadTrackingWALWriter forkingReadTrackingWALWriter = new ForkingReadTrackingWALWriter(rcvsReadTrackingWALWriter,
                    amzaReadTrackingWALWriter);
                RCVSReadTrackingWALReader readTrackingWALReader = new RCVSReadTrackingWALReader(hostPortProvider,
                    rcvsWAL.getReadTrackingWAL(),
                    rcvsWAL.getReadTrackingSipWAL());

                rcvsWALDirector = new MiruWALDirector<>(forkingWALLookup,
                    rcvsActivityWALReader,
                    forkingActivityWALWriter,
                    readTrackingWALReader,
                    forkingReadTrackingWALWriter,
                    clusterClient);

                activityWALReader = rcvsActivityWALReader;
                miruWALDirector = rcvsWALDirector;
                walEndpointsClass = RCVSWALEndpoints.class;
            } else if (walConfig.getActivityWALType().equals("amza_rcvs")) {
                RCVSWALInitializer.RCVSWAL rcvsWAL = new RCVSWALInitializer().initialize(instanceConfig.getClusterName(),
                    rowColumnValueStoreInitializer,
                    mapper);

                RCVSActivityWALWriter rcvsActivityWALWriter = new RCVSActivityWALWriter(rcvsWAL.getActivityWAL(), rcvsWAL.getActivitySipWAL());
                AmzaActivityWALWriter amzaActivityWALWriter = new AmzaActivityWALWriter(amzaWALUtil,
                    amzaServiceConfig.getReplicateActivityQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis(),
                    mapper);
                ForkingActivityWALWriter forkingActivityWALWriter = new ForkingActivityWALWriter(amzaActivityWALWriter, rcvsActivityWALWriter);
                AmzaActivityWALReader amzaActivityWALReader = new AmzaActivityWALReader(amzaWALUtil, mapper);

                RCVSWALLookup rcvsWALLookup = new RCVSWALLookup(rcvsWAL.getWALLookupTable());
                AmzaWALLookup amzaWALLookup = new AmzaWALLookup(amzaWALUtil,
                    amzaServiceConfig.getReplicateLookupQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis());
                ForkingWALLookup forkingWALLookup = new ForkingWALLookup(amzaWALLookup, rcvsWALLookup);

                RCVSReadTrackingWALWriter rcvsReadTrackingWALWriter = new RCVSReadTrackingWALWriter(rcvsWAL.getReadTrackingWAL(),
                    rcvsWAL.getReadTrackingSipWAL());
                AmzaReadTrackingWALWriter amzaReadTrackingWALWriter = new AmzaReadTrackingWALWriter(amzaWALUtil,
                    amzaServiceConfig.getReplicateReadTrackingQuorum(),
                    amzaServiceConfig.getReplicateTimeoutMillis(),
                    mapper);
                ForkingReadTrackingWALWriter forkingReadTrackingWALWriter = new ForkingReadTrackingWALWriter(amzaReadTrackingWALWriter,
                    rcvsReadTrackingWALWriter);
                AmzaReadTrackingWALReader readTrackingWALReader = new AmzaReadTrackingWALReader(amzaWALUtil, mapper);

                amzaWALDirector = new MiruWALDirector<>(forkingWALLookup,
                    amzaActivityWALReader,
                    forkingActivityWALWriter,
                    readTrackingWALReader,
                    forkingReadTrackingWALWriter,
                    clusterClient);

                activityWALReader = amzaActivityWALReader;
                miruWALDirector = amzaWALDirector;
                walEndpointsClass = AmzaWALEndpoints.class;
            } else {
                throw new IllegalStateException("Invalid activity WAL type: " + walConfig.getActivityWALType());
            }

            MiruSoyRendererConfig rendererConfig = deployable.config(MiruSoyRendererConfig.class);

            File staticResourceDir = new File(System.getProperty("user.dir"));
            System.out.println("Static resources rooted at " + staticResourceDir.getAbsolutePath());
            Resource sourceTree = new Resource(staticResourceDir)
                //.addResourcePath("../../../../../src/main/resources") // fluff?
                .addResourcePath(rendererConfig.getPathToStaticResources())
                .setContext("/static");

            MiruSoyRenderer renderer = new MiruSoyRendererInitializer().initialize(rendererConfig);

            MiruWALUIService miruWALUIService = new MiruWriterUIServiceInitializer()
                .initialize(renderer, rcvsWALDirector, amzaWALDirector, activityWALReader, miruStats);

            deployable.addEndpoints(MiruWALEndpoints.class);
            deployable.addInjectables(MiruWALUIService.class, miruWALUIService);
            deployable.addInjectables(MiruWALDirector.class, miruWALDirector);

            deployable.addEndpoints(walEndpointsClass);
            deployable.addInjectables(MiruStats.class, miruStats);

            deployable.addResource(sourceTree);
            deployable.buildServer().start();
            clientHealthProvider.start();
            serviceStartupHealthCheck.success();
        } catch (Throwable t) {
            serviceStartupHealthCheck.info("Encountered the following failure during startup.", t);
        }
    }
}
