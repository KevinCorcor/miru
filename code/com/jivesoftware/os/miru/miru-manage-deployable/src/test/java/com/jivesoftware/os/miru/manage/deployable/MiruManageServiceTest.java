package com.jivesoftware.os.miru.manage.deployable;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jivesoftware.os.jive.utils.row.column.value.store.inmemory.InMemorySetOfSortedMapsImplInitializer;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartition;
import com.jivesoftware.os.miru.api.MiruPartitionState;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.cluster.MiruClusterRegistry;
import com.jivesoftware.os.miru.cluster.MiruRegistryStore;
import com.jivesoftware.os.miru.cluster.MiruRegistryStoreInitializer;
import com.jivesoftware.os.miru.cluster.MiruReplicaSet;
import com.jivesoftware.os.miru.cluster.rcvs.MiruRCVSClusterRegistry;
import com.jivesoftware.os.miru.manage.deployable.MiruManageInitializer.MiruManageConfig;
import com.jivesoftware.os.miru.wal.MiruWALInitializer;
import com.jivesoftware.os.miru.wal.MiruWALInitializer.MiruWAL;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.merlin.config.BindInterfaceToConfiguration;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MiruManageServiceTest {

    private MiruManageService miruManageService;
    private MiruTenantId tenantId;
    private List<MiruHost> hosts;
    private MiruPartitionId partitionId;

    @BeforeClass
    public void before() throws Exception {
        int numberOfReplicas = 3;

        MiruManageConfig config = BindInterfaceToConfiguration.bindDefault(MiruManageConfig.class);
        config.setPathToSoyResources("src/main/home/resources/soy");

        InMemorySetOfSortedMapsImplInitializer setOfSortedMapsImplInitializer = new InMemorySetOfSortedMapsImplInitializer();
        MiruRegistryStore registryStore = new MiruRegistryStoreInitializer().initialize("test", setOfSortedMapsImplInitializer);
        MiruClusterRegistry clusterRegistry = new MiruRCVSClusterRegistry(registryStore.getHostsRegistry(), registryStore.getExpectedTenantsRegistry(),
                registryStore.getExpectedTenantPartitionsRegistry(), registryStore.getReplicaRegistry(), registryStore.getTopologyRegistry(),
                registryStore.getConfigRegistry(), numberOfReplicas, TimeUnit.HOURS.toMillis(1));
        MiruWAL miruWAL = new MiruWALInitializer().initialize("test", setOfSortedMapsImplInitializer);
        miruManageService = new MiruManageInitializer().initialize(config, clusterRegistry, registryStore, miruWAL);

        tenantId = new MiruTenantId("test1".getBytes());
        partitionId = MiruPartitionId.of(0);
        hosts = Lists.newArrayList();

        for (int i = 0; i < numberOfReplicas; i++) {
            MiruHost host = new MiruHost("host" + i, 10_000 + i);
            clusterRegistry.sendHeartbeatForHost(host, i, i);
            hosts.add(host);
        }

        clusterRegistry.electToReplicaSetForTenantPartition(tenantId, partitionId,
                new MiruReplicaSet(ArrayListMultimap.<MiruPartitionState, MiruPartition>create(), Sets.<MiruHost>newHashSet(), numberOfReplicas));
    }

    @Test
    public void testRenderHostsWithFocus() throws Exception {
        String rendered = miruManageService.renderHostsWithFocus(hosts.get(0));
        for (int i = 0; i < hosts.size(); i++) {
            MiruHost host = hosts.get(i);
            assertTrue(rendered.contains("/miru/manage/hosts/" + host.getLogicalName() + "/" + host.getPort() + "#focus"));
            assertTrue(rendered.contains("<td>" + host.getPort() + "</td>"));
            if (i == 0) {
                assertTrue(rendered.contains(host.toStringForm()));
            } else {
                assertFalse(rendered.contains(host.toStringForm()));
            }
        }
    }

    @Test
    public void testRenderTenantsWithFocus() throws Exception {
        String rendered = miruManageService.renderTenantsWithFocus(tenantId);
        assertTrue(rendered.contains(tenantId.toString()));
        for (MiruHost host : hosts) {
            assertTrue(rendered.contains("/miru/manage/hosts/" + host.getLogicalName() + "/" + host.getPort() + "#focus"));
        }
    }

    @Test
    public void testRenderActivityWALWithTenant() throws Exception {
        String rendered = miruManageService.renderActivityWALWithTenant(tenantId);
        assertTrue(rendered.contains(tenantId.toString()));
        assertTrue(rendered.contains("/miru/manage/wal/activity/" + tenantId + "/" + partitionId + "#focus"));
    }

}