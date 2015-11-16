package com.jivesoftware.os.miru.reco.plugins.distincts;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.plugin.partition.MiruPartitionUnavailableException;
import com.jivesoftware.os.miru.plugin.solution.MiruPartitionResponse;
import com.jivesoftware.os.miru.plugin.solution.MiruRequest;
import com.jivesoftware.os.miru.plugin.solution.MiruRequestAndReport;
import com.jivesoftware.os.miru.plugin.solution.MiruResponse;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.shared.ResponseHelper;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.nustaq.serialization.FSTConfiguration;

import static com.jivesoftware.os.miru.reco.plugins.distincts.DistinctsConstants.CUSTOM_QUERY_ENDPOINT;
import static com.jivesoftware.os.miru.reco.plugins.distincts.DistinctsConstants.DISTINCTS_PREFIX;

@Path(DISTINCTS_PREFIX)
@Singleton
public class DistinctsEndpoints {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();
    private static final FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();

    private final DistinctsInjectable injectable;
    private final ObjectMapper objectMapper;
    private final JavaType resultType;
    private final ResponseHelper responseHelper = ResponseHelper.INSTANCE;

    public DistinctsEndpoints(
        @Context DistinctsInjectable injectable,
        @Context ObjectMapper objectMapper) {
        this.injectable = injectable;
        this.objectMapper = objectMapper;
        this.resultType = objectMapper.getTypeFactory().constructParametricType(MiruRequestAndReport.class, DistinctsQuery.class, DistinctsReport.class);
    }

    @POST
    @Path(CUSTOM_QUERY_ENDPOINT)
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response gatherDistincts(MiruRequest<DistinctsQuery> request) {
        try {
            long t = System.currentTimeMillis();
            MiruResponse<DistinctsAnswer> response = injectable.gatherDistincts(request);

            log.info("gatherDistincts: " + response.answer.results.size() + " / " + response.answer.collectedDistincts
                + " in " + (System.currentTimeMillis() - t) + " ms");
            return responseHelper.jsonResponse(response);
        } catch (MiruPartitionUnavailableException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Partition unavailable").build();
        } catch (Exception e) {
            log.error("Failed to gather distincts.", e);
            return Response.serverError().build();
        }
    }

    @POST
    @Path(CUSTOM_QUERY_ENDPOINT + "/{partitionId}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response gatherDistincts(@PathParam("partitionId") int id, byte[] rawBytes) {
        MiruPartitionId partitionId = MiruPartitionId.of(id);
        try {
            //byte[] jsonBytes = Snappy.uncompress(rawBytes);
            //MiruRequestAndReport<DistinctsQuery, DistinctsReport> requestAndReport = objectMapper.readValue(jsonBytes, resultType);
            //MiruPartitionResponse<DistinctsAnswer> result = injectable.gatherDistincts(partitionId, requestAndReport);
            MiruPartitionResponse<DistinctsAnswer> result = (MiruPartitionResponse<DistinctsAnswer>) conf.asObject(rawBytes);
            //byte[] responseBytes = result != null ? Snappy.compress(objectMapper.writeValueAsBytes(result)) : new byte[0];
            byte[] responseBytes = result != null ? conf.asByteArray(result) : new byte[0];
            return Response.ok(responseBytes, MediaType.APPLICATION_OCTET_STREAM).build();
        } catch (MiruPartitionUnavailableException e) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity("Partition unavailable").build();
        } catch (Exception e) {
            log.error("Failed to gather distincts for partition: " + partitionId.getId(), e);
            return Response.serverError().build();
        }
    }
}
