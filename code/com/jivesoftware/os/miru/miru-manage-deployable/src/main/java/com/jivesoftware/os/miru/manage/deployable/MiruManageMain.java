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
package com.jivesoftware.os.miru.manage.deployable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.collect.Lists;
import com.jivesoftware.os.jive.utils.ordered.id.ConstantWriterIdProvider;
import com.jivesoftware.os.jive.utils.ordered.id.OrderIdProviderImpl;
import com.jivesoftware.os.miru.cluster.MiruClusterRegistry;
import com.jivesoftware.os.miru.cluster.MiruRegistryConfig;
import com.jivesoftware.os.miru.cluster.MiruRegistryStore;
import com.jivesoftware.os.miru.cluster.MiruRegistryStoreInitializer;
import com.jivesoftware.os.miru.cluster.rcvs.MiruRCVSClusterRegistry;
import com.jivesoftware.os.miru.manage.deployable.MiruSoyRendererInitializer.MiruSoyRendererConfig;
import com.jivesoftware.os.miru.manage.deployable.region.AnalyticsPluginRegion;
import com.jivesoftware.os.miru.manage.deployable.region.MiruManagePlugin;
import com.jivesoftware.os.miru.wal.MiruWALInitializer;
import com.jivesoftware.os.rcvs.api.SetOfSortedMapsImplInitializer;
import com.jivesoftware.os.rcvs.api.timestamper.CurrentTimestamper;
import com.jivesoftware.os.rcvs.hbase.HBaseSetOfSortedMapsImplInitializer;
import com.jivesoftware.os.rcvs.hbase.HBaseSetOfSortedMapsImplInitializer.HBaseSetOfSortedMapsConfig;
import com.jivesoftware.os.server.http.jetty.jersey.server.util.Resource;
import com.jivesoftware.os.upena.main.Deployable;
import com.jivesoftware.os.upena.main.InstanceConfig;
import java.io.File;
import java.util.List;
import org.merlin.config.defaults.StringDefault;

public class MiruManageMain {

    public static void main(String[] args) throws Exception {
        new MiruManageMain().run(args);
    }

    private interface DevInstanceConfig extends InstanceConfig {

        @StringDefault("dev")
        String getClusterName();
    }


    public void run(String[] args) throws Exception {

        Deployable deployable = new Deployable(args);
        deployable.buildStatusReporter(null).start();
        deployable.buildManageServer().start();

        InstanceConfig instanceConfig = deployable.config(DevInstanceConfig.class);

        HBaseSetOfSortedMapsConfig hbaseConfig = deployable.config(HBaseSetOfSortedMapsConfig.class);
        hbaseConfig.setHBaseZookeeperQuorum("soa-prime-data1.phx1.jivehosted.com");
        SetOfSortedMapsImplInitializer<Exception> setOfSortedMapsInitializer = new HBaseSetOfSortedMapsImplInitializer(hbaseConfig);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new GuavaModule());

        MiruSoyRendererConfig rendererConfig = deployable.config(MiruSoyRendererConfig.class);
        MiruRegistryConfig registryConfig = deployable.config(MiruRegistryConfig.class);

        MiruRegistryStore registryStore = new MiruRegistryStoreInitializer().initialize(instanceConfig.getClusterName(), setOfSortedMapsInitializer, mapper);
        MiruClusterRegistry clusterRegistry = new MiruRCVSClusterRegistry(new CurrentTimestamper(),
            registryStore.getHostsRegistry(),
            registryStore.getExpectedTenantsRegistry(),
            registryStore.getExpectedTenantPartitionsRegistry(),
            registryStore.getReplicaRegistry(),
            registryStore.getTopologyRegistry(),
            registryStore.getConfigRegistry(),
            registryConfig.getDefaultNumberOfReplicas(),
            registryConfig.getDefaultTopologyIsStaleAfterMillis());

        MiruWALInitializer.MiruWAL wal = new MiruWALInitializer().initialize(instanceConfig.getClusterName(), setOfSortedMapsInitializer, mapper);

        MiruSoyRenderer renderer = new MiruSoyRendererInitializer().initialize(rendererConfig);

        MiruManageService miruManageService = new MiruManageInitializer().initialize(renderer,
            clusterRegistry,
            registryStore,
            wal);

        ReaderRequestHelpers readerRequestHelpers = new ReaderRequestHelpers(clusterRegistry, mapper);

        List<MiruManagePlugin> plugins = Lists.newArrayList(
            new MiruManagePlugin("Analytics",
                "/miru/manage/analytics",
                AnalyticsPluginEndpoints.class,
                new AnalyticsPluginRegion("soy.miru.page.analyticsPluginRegion", renderer, readerRequestHelpers)));

        MiruRebalanceDirector rebalanceDirector = new MiruRebalanceInitializer().initialize(clusterRegistry,
            new OrderIdProviderImpl(new ConstantWriterIdProvider(instanceConfig.getInstanceName())), readerRequestHelpers);

        File staticResourceDir = new File(System.getProperty("user.dir"));
        System.out.println("Static resources rooted at " + staticResourceDir.getAbsolutePath());
        Resource sourceTree = new Resource(staticResourceDir)
            //.addResourcePath("../../../../../src/main/resources") // fluff?
            .addResourcePath(rendererConfig.getPathToStaticResources())
            .setContext("/static");

        deployable.addEndpoints(MiruManageEndpoints.class);
        deployable.addInjectables(MiruManageService.class, miruManageService);
        deployable.addInjectables(MiruRebalanceDirector.class, rebalanceDirector);

        for (MiruManagePlugin plugin : plugins) {
            miruManageService.registerPlugin(plugin);
            deployable.addEndpoints(plugin.endpointsClass);
            deployable.addInjectables(plugin.region.getClass(), plugin.region);
        }

        deployable.addResource(sourceTree);
        deployable.buildServer().start();

    }
}
