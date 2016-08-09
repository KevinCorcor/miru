package com.jivesoftware.os.miru.cluster.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivesoftware.os.miru.api.MiruHost;
import com.jivesoftware.os.miru.api.MiruPartition;
import com.jivesoftware.os.miru.api.MiruPartitionCoord;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.topology.HostHeartbeat;
import com.jivesoftware.os.miru.api.topology.MiruClusterClient;
import com.jivesoftware.os.miru.api.topology.MiruHeartbeatRequest;
import com.jivesoftware.os.miru.api.topology.MiruHeartbeatResponse;
import com.jivesoftware.os.miru.api.topology.MiruIngressUpdate;
import com.jivesoftware.os.miru.api.topology.MiruPartitionStatus;
import com.jivesoftware.os.miru.api.topology.MiruTenantConfig;
import com.jivesoftware.os.miru.api.topology.MiruTopologyResponse;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.http.client.HttpClient;
import com.jivesoftware.os.routing.bird.http.client.HttpClientException;
import com.jivesoftware.os.routing.bird.http.client.HttpResponse;
import com.jivesoftware.os.routing.bird.http.client.HttpResponseMapper;
import com.jivesoftware.os.routing.bird.http.client.RoundRobinStrategy;
import com.jivesoftware.os.routing.bird.http.client.TenantAwareHttpClient;
import com.jivesoftware.os.routing.bird.shared.ClientCall;
import com.jivesoftware.os.routing.bird.shared.ClientCall.ClientResponse;
import java.util.Arrays;
import java.util.List;

public class MiruHttpClusterClient implements MiruClusterClient {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final MiruStats miruStats;
    private final String routingTenantId;
    private final TenantAwareHttpClient<String> clusterClient;
    private final RoundRobinStrategy roundRobinStrategy;
    private final ObjectMapper requestMapper;
    private final HttpResponseMapper responseMapper;

    public MiruHttpClusterClient(MiruStats miruStats,
        String routingTenantId,
        TenantAwareHttpClient<String> clusterClient,
        RoundRobinStrategy roundRobinStrategy,
        ObjectMapper requestMapper,
        HttpResponseMapper responseMapper) {

        this.miruStats = miruStats;
        this.routingTenantId = routingTenantId;
        this.clusterClient = clusterClient;
        this.roundRobinStrategy = roundRobinStrategy;
        this.requestMapper = requestMapper;
        this.responseMapper = responseMapper;
    }

    @Override
    public MiruTopologyResponse routingTopology(final MiruTenantId tenantId) throws Exception {
        return sendRoundRobin("routingTopology", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.get("/miru/topology/routing/" + tenantId.toString(), null);
            MiruTopologyResponse miruTopologyResponse = responseMapper.extractResultFromResponse(response, MiruTopologyResponse.class, null);
            miruStats.egressed("/miru/topology/routing/" + tenantId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(miruTopologyResponse, true);
        });
    }

    @Override
    public void updateIngress(MiruIngressUpdate ingressUpdate) throws Exception {
        String jsonWarmIngress = requestMapper.writeValueAsString(ingressUpdate);
        sendRoundRobin("updateIngress", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/update/ingress", jsonWarmIngress, null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed("/miru/topology/update/ingress", 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    @Override
    public void removeIngress(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        sendRoundRobin("removeIngress", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/remove/ingress/" + tenantId.toString() + "/" + partitionId.toString(), "null", null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed("/miru/topology/remove/ingress/" + tenantId.toString() + "/" + partitionId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    @Override
    public void updateLastId(MiruPartitionCoord coord, int lastId) throws Exception {
        sendRoundRobin("updateLastId", client -> {
            long start = System.currentTimeMillis();
            String endpointPrefix = "/miru/topology/update/lastId" +
                "/" + coord.tenantId.toString() +
                "/" + coord.partitionId.toString() +
                "/" + coord.host.toString();
            HttpResponse response = client.postJson(endpointPrefix + "/" + lastId, "null", null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed(endpointPrefix, 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    @Override
    public void destroyPartition(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        sendRoundRobin("destroyPartition", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/destroy/partition/" + tenantId.toString() + "/" + partitionId.toString(), "null", null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed("/miru/topology/destroy/partition/" + tenantId.toString() + "/" + partitionId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    @Override
    public List<MiruPartitionStatus> getPartitionStatus(MiruTenantId tenantId, MiruPartitionId largestPartitionId) throws Exception {
        return sendRoundRobin("getPartitionStatus", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.get("/miru/topology/partition/status/" + tenantId.toString() + "/" + largestPartitionId.toString(), null);
            MiruPartitionStatus[] statuses = responseMapper.extractResultFromResponse(response, MiruPartitionStatus[].class, null);
            miruStats.egressed("/miru/topology/partition/status/" + tenantId.toString() + "/" + largestPartitionId.toString(), 1,
                System.currentTimeMillis() - start);
            return new ClientResponse<>(Arrays.asList(statuses), true);
        });
    }

    @Override
    public MiruHeartbeatResponse thumpthump(final MiruHost host, final MiruHeartbeatRequest heartbeatRequest) throws Exception {
        String jsonHeartbeatRequest = requestMapper.writeValueAsString(heartbeatRequest);
        return sendRoundRobin("thumpthump", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/thumpthump/" + host.getLogicalName(), jsonHeartbeatRequest, null);
            MiruHeartbeatResponse heartbeatResponse = responseMapper.extractResultFromResponse(response, MiruHeartbeatResponse.class, null);
            miruStats.egressed("/miru/topology/thumpthump/" + host.getLogicalName(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(heartbeatResponse, true);
        });
    }

    @Override
    public List<HostHeartbeat> allhosts() {
        return sendRoundRobin("allhosts", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/allHosts", "null", null);
            HostHeartbeat[] heartbeats = responseMapper.extractResultFromResponse(response, HostHeartbeat[].class, null);
            miruStats.egressed("/miru/topology/allHosts", 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(Arrays.asList(heartbeats), true);
        });
    }

    @Override
    public MiruTenantConfig tenantConfig(final MiruTenantId tenantId) {
        return sendRoundRobin("tenantConfig", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.get("/miru/topology/tenantConfig/" + tenantId.toString(), null);
            MiruTenantConfig tenantConfig = responseMapper.extractResultFromResponse(response, MiruTenantConfig.class, null);
            miruStats.egressed("/miru/topology/tenantConfig/" + tenantId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(tenantConfig, true);
        });
    }

    @Override
    public List<MiruPartition> partitions(final MiruTenantId tenantId) {
        return sendRoundRobin("partitions", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/partitions/" + tenantId.toString(), "null", null);
            MiruPartition[] partitions = responseMapper.extractResultFromResponse(response, MiruPartition[].class, null);
            miruStats.egressed("/miru/topology/partitions/" + tenantId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(Arrays.asList(partitions), true);
        });
    }

    @Override
    public List<PartitionRange> getIngressRanges(MiruTenantId tenantId) throws Exception {
        return sendRoundRobin("getIngressRanges", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.get("/miru/topology/ingress/ranges/" + tenantId.toString(), null);
            PartitionRange[] partitionRanges = responseMapper.extractResultFromResponse(response, PartitionRange[].class, null);
            miruStats.egressed("/miru/topology/ingress/ranges/" + tenantId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(Arrays.asList(partitionRanges), true);
        });
    }

    @Override
    public void removeHost(final MiruHost host) {
        sendRoundRobin("removeHost", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/remove/" + host.getLogicalName(), "null", null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed("/miru/topology/remove/" + host.getLogicalName(),
                1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    @Override
    public void removeTopology(final MiruHost host, final MiruTenantId tenantId, final MiruPartitionId partitionId) {
        sendRoundRobin("removeTopology", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/remove/"
                + host.getLogicalName() + "/"
                + tenantId + "/"
                + partitionId.getId(), "null", null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed("/miru/topology/remove/"
                + host.getLogicalName() + "/"
                + tenantId + "/"
                + partitionId.getId(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    @Override
    public MiruSchema getSchema(final MiruTenantId tenantId) {
        return sendRoundRobin("getSchema", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.get("/miru/topology/schema/" + tenantId.toString(), null);
            MiruSchema schema = responseMapper.extractResultFromResponse(response, MiruSchema.class, null);
            miruStats.egressed("/miru/topology/schema/" + tenantId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(schema, true);
        });
    }

    @Override
    public void registerSchema(final MiruTenantId tenantId, final MiruSchema schema) throws Exception {
        String jsonSchema = requestMapper.writeValueAsString(schema);
        sendRoundRobin("registerSchema", client -> {
            long start = System.currentTimeMillis();
            HttpResponse response = client.postJson("/miru/topology/schema/" + tenantId.toString(), jsonSchema, null);
            String r = responseMapper.extractResultFromResponse(response, String.class, null);
            miruStats.egressed("/miru/topology/schema/" + tenantId.toString(), 1, System.currentTimeMillis() - start);
            return new ClientResponse<>(r, true);
        });
    }

    private <R> R sendRoundRobin(String family, ClientCall<HttpClient, R, HttpClientException> call) {
        try {
            return clusterClient.call(routingTenantId, roundRobinStrategy, family, call);
        } catch (Exception x) {
            throw new RuntimeException("Failed to send.", x);
        }
    }
}
