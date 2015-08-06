package com.jivesoftware.os.miru.wal.deployable.endpoints;

import com.google.common.base.Charsets;
import com.jivesoftware.os.miru.api.MiruStats;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.wal.AmzaCursor;
import com.jivesoftware.os.miru.api.wal.AmzaSipCursor;
import com.jivesoftware.os.miru.api.wal.MiruActivityWALStatus;
import com.jivesoftware.os.miru.api.wal.MiruVersionedActivityLookupEntry;
import com.jivesoftware.os.miru.api.wal.MiruWALClient;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.MiruLookupEntry;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.RoutingGroupType;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.StreamBatch;
import com.jivesoftware.os.miru.api.wal.MiruWALEntry;
import com.jivesoftware.os.miru.wal.MiruWALDirector;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.shared.HostPort;
import com.jivesoftware.os.routing.bird.shared.ResponseHelper;
import java.util.List;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author jonathan.colt
 */
@Singleton
@Path("/miru/wal/amza")
public class AmzaWALEndpoints {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final MiruWALDirector<AmzaCursor, AmzaSipCursor> walDirector;
    private final MiruStats stats;
    private final ResponseHelper responseHelper = ResponseHelper.INSTANCE;

    public AmzaWALEndpoints(@Context MiruWALDirector walDirector, @Context MiruStats stats) {
        this.walDirector = walDirector;
        this.stats = stats;
    }

    @GET
    @Path("/routing/tenant/{type}/{tenantId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)));
            stats.ingressed("/routing/tenant/" + routingGroupType.name() + "/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getTenantRoutingGroup({},{})", new Object[] { routingGroupType, tenantId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/routing/tenantPartition/{type}/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantPartitionRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantPartitionRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId));
            stats.ingressed("/routing/tenantPartition/" + routingGroupType.name() + "/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getTenantPartitionRoutingGroup({},{},{})", new Object[] { routingGroupType, tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/routing/tenantStream/{type}/{tenantId}/{streamId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTenantStreamRoutingGroup(@PathParam("type") RoutingGroupType routingGroupType,
        @PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId) {
        try {
            long start = System.currentTimeMillis();
            HostPort[] routingGroup = walDirector.getTenantStreamRoutingGroup(routingGroupType, new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                new MiruStreamId(streamId.getBytes(Charsets.UTF_8)));
            stats.ingressed("/routing/tenantStream/" + routingGroupType.name() + "/" + tenantId + "/" + streamId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(routingGroup);
        } catch (Exception x) {
            log.error("Failed calling getTenantStreamRoutingGroup({},{},{})", new Object[] { routingGroupType, tenantId, streamId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/repairBoundaries")
    @Produces(MediaType.APPLICATION_JSON)
    public Response repairBoundaries() throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.repairBoundaries();
            stats.ingressed("/repairBoundaries", 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling repairBoundaries()", x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/repairRanges")
    @Produces(MediaType.APPLICATION_JSON)
    public Response repairRanges() throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.repairRanges();
            stats.ingressed("/repairRanges", 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling repairRanges()", x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/sanitize/activity/wal/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sanitizeActivityWAL(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.sanitizeActivityWAL(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId));
            stats.ingressed("/sanitize/activity/wal/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling sanitizeActivityWAL({}, {})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/sanitize/sip/wal/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sanitizeActivitySipWAL(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.sanitizeActivitySipWAL(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId));
            stats.ingressed("/sanitize/sip/wal/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling sanitizeActivitySipWAL({}, {})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/tenants/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllTenantIds() throws Exception {
        try {
            long start = System.currentTimeMillis();
            List<MiruTenantId> allTenantIds = walDirector.getAllTenantIds();
            stats.ingressed("/tenants/all", 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(allTenantIds);
        } catch (Exception x) {
            log.error("Failed calling getAllTenantIds()", x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/write/activities/{tenantId}/{partitionId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeActivity(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.writeActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId), partitionedActivities);
            stats.ingressed("/write/activities/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling writeActivity({},count:{})",
                new Object[] { tenantId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/write/lookup/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeLookup(@PathParam("tenantId") String tenantId,
        List<MiruVersionedActivityLookupEntry> entries) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.writeLookup(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), entries);
            stats.ingressed("/write/lookup/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling writeLookup({},count:{})",
                new Object[] { tenantId, entries != null ? entries.size() : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/write/reads/{tenantId}/{streamId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response writeReadTracking(@PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId,
        List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        try {
            long start = System.currentTimeMillis();
            walDirector.writeReadTracking(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), new MiruStreamId(streamId.getBytes(Charsets.UTF_8)),
                partitionedActivities);
            stats.ingressed("/write/reads/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse("ok");
        } catch (Exception x) {
            log.error("Failed calling writeReadTracking({},{},count:{})",
                new Object[] { tenantId, streamId, partitionedActivities != null ? partitionedActivities.size() : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/largestPartitionId/{tenantId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getLargestPartitionIdAcrossAllWriters(@PathParam("tenantId") String tenantId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            MiruPartitionId partitionId = walDirector.getLargestPartitionId(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)));
            stats.ingressed("/largestPartitionId/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(partitionId);
        } catch (Exception x) {
            log.error("Failed calling getLargestPartitionId({})", new Object[] { tenantId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/cursor/writer/{tenantId}/{writerId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCursorForWriterId(@PathParam("tenantId") String tenantId,
        @PathParam("writerId") int writerId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            MiruWALClient.WriterCursor cursor = walDirector.getCursorForWriterId(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), writerId);
            stats.ingressed("/cursor/writer/" + tenantId + "/" + writerId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(cursor);
        } catch (Exception x) {
            log.error("Failed calling getCursorForWriterId({},{})", new Object[] { tenantId, writerId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/activity/wal/status/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivityWALStatus(@PathParam("tenantId") String tenantId, @PathParam("partitionId") int partitionId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            MiruActivityWALStatus walStatus = walDirector.getActivityWALStatusForTenant(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId));
            stats.ingressed("/activity/wal/status/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(walStatus);
        } catch (Exception x) {
            log.error("Failed calling getActivityWALStatus({},{})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/oldest/activity/{tenantId}/{partitionId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response oldestActivityClockTimestamp(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId) throws Exception {
        try {
            long start = System.currentTimeMillis();
            long timestamp = walDirector.oldestActivityClockTimestamp(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), MiruPartitionId.of(partitionId));
            stats.ingressed("/oldest/activity/" + tenantId + "/" + partitionId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(timestamp);
        } catch (Exception x) {
            log.error("Failed calling oldestActivityClockTimestamp({},{})", new Object[] { tenantId, partitionId }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/versioned/entries/{tenantId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getVersionedEntries(@PathParam("tenantId") String tenantId,
        Long[] timestamps) throws Exception {
        try {
            long start = System.currentTimeMillis();
            List<MiruVersionedActivityLookupEntry> versionedEntries = walDirector.getVersionedEntries(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                timestamps);
            stats.ingressed("/versioned/entries/" + tenantId, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(versionedEntries);
        } catch (Exception x) {
            log.error("Failed calling getVersionedEntries({},count:{})", new Object[] { tenantId, timestamps != null ? timestamps.length : null }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @GET
    @Path("/lookup/activity/{tenantId}/{batchSize}/{afterTimestamp}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response lookupActivity(@PathParam("tenantId") String tenantId,
        @PathParam("batchSize") int batchSize,
        @PathParam("afterTimestamp") long afterTimestamp) throws Exception {
        try {
            long start = System.currentTimeMillis();
            List<MiruLookupEntry> lookupActivity = walDirector.lookupActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)), afterTimestamp, batchSize);
            stats.ingressed("/lookup/activity/" + tenantId + "/" + batchSize + "/" + afterTimestamp, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(lookupActivity);
        } catch (Exception x) {
            log.error("Failed calling lookupActivity({},{},{})", new Object[] { tenantId, afterTimestamp, batchSize }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/sip/activity/{tenantId}/{partitionId}/{batchSize}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sipActivity(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("batchSize") int batchSize,
        AmzaSipCursor cursor)
        throws Exception {
        try {
            long start = System.currentTimeMillis();
            StreamBatch<MiruWALEntry, AmzaSipCursor> sipActivity = walDirector.sipActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), cursor, batchSize);
            stats.ingressed("/sip/activity/" + tenantId + "/" + partitionId + "/" + batchSize, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(sipActivity);
        } catch (Exception x) {
            log.error("Failed calling sipActivity({},{},{},{})", new Object[] { tenantId, partitionId, batchSize, cursor }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/activity/{tenantId}/{partitionId}/{batchSize}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getActivity(@PathParam("tenantId") String tenantId,
        @PathParam("partitionId") int partitionId,
        @PathParam("batchSize") int batchSize,
        AmzaCursor cursor)
        throws Exception {
        try {
            long start = System.currentTimeMillis();
            StreamBatch<MiruWALEntry, AmzaCursor> activity = walDirector.getActivity(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                MiruPartitionId.of(partitionId), cursor, batchSize);
            stats.ingressed("/activity/" + tenantId + "/" + partitionId + "/" + batchSize, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(activity);
        } catch (Exception x) {
            log.error("Failed calling getActivity({},{},{},{})", new Object[] { tenantId, partitionId, batchSize, cursor }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

    @POST
    @Path("/read/{tenantId}/{streamId}/{oldestEventId}/{batchSize}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRead(@PathParam("tenantId") String tenantId,
        @PathParam("streamId") String streamId,
        @PathParam("oldestEventId") long oldestEventId,
        @PathParam("batchSize") int batchSize,
        AmzaSipCursor cursor) throws Exception {
        try {
            long start = System.currentTimeMillis();
            StreamBatch<MiruWALEntry, AmzaSipCursor> read = walDirector.getRead(new MiruTenantId(tenantId.getBytes(Charsets.UTF_8)),
                new MiruStreamId(streamId.getBytes(Charsets.UTF_8)), cursor, oldestEventId, batchSize);
            stats.ingressed("/read/" + tenantId + "/" + streamId + "/" + batchSize, 1, System.currentTimeMillis() - start);
            return responseHelper.jsonResponse(read);
        } catch (Exception x) {
            log.error("Failed calling getRead({},{},{},{},{})", new Object[] { tenantId, streamId, oldestEventId, batchSize, cursor }, x);
            return responseHelper.errorResponse("Server error", x);
        }
    }

}
