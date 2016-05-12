package com.jivesoftware.os.miru.wal.readtracking.amza;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.jivesoftware.os.amza.api.FailedToAchieveQuorumException;
import com.jivesoftware.os.amza.api.partition.Consistency;
import com.jivesoftware.os.amza.service.PartitionIsDisposedException;
import com.jivesoftware.os.amza.service.PropertiesNotPresentException;
import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.marshall.JacksonJsonObjectTypeMarshaller;
import com.jivesoftware.os.miru.wal.AmzaWALUtil;
import com.jivesoftware.os.miru.wal.MiruWALWrongRouteException;
import com.jivesoftware.os.miru.wal.readtracking.MiruReadTrackingWALWriter;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class AmzaReadTrackingWALWriter implements MiruReadTrackingWALWriter {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final AmzaWALUtil amzaWALUtil;
    private final long replicateTimeoutMillis;
    private final Function<MiruPartitionedActivity, byte[]> readTrackingWALKeyFunction;
    private final Function<MiruPartitionedActivity, byte[]> activitySerializerFunction;

    public AmzaReadTrackingWALWriter(AmzaWALUtil amzaWALUtil,
        long replicateTimeoutMillis,
        ObjectMapper mapper) {
        this.amzaWALUtil = amzaWALUtil;
        this.replicateTimeoutMillis = replicateTimeoutMillis;

        JacksonJsonObjectTypeMarshaller<MiruPartitionedActivity> partitionedActivityMarshaller =
            new JacksonJsonObjectTypeMarshaller<>(MiruPartitionedActivity.class, mapper);
        this.readTrackingWALKeyFunction = (partitionedActivity) -> FilerIO.longBytes(partitionedActivity.timestamp);
        this.activitySerializerFunction = partitionedActivity -> {
            try {
                return partitionedActivityMarshaller.toBytes(partitionedActivity);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    public void write(MiruTenantId tenantId, MiruStreamId streamId, List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        try {
            amzaWALUtil.getReadTrackingClient(tenantId).commit(Consistency.leader_quorum, streamId.getBytes(),
                (txKeyValueStream) -> {
                    for (MiruPartitionedActivity activity : partitionedActivities) {
                        byte[] key = readTrackingWALKeyFunction.apply(activity);
                        byte[] value = activitySerializerFunction.apply(activity);
                        if (!txKeyValueStream.commit(key, value, System.currentTimeMillis(), false)) {
                            return false;
                        }
                    }
                    return true;
                },
                replicateTimeoutMillis,
                TimeUnit.MILLISECONDS);
        } catch (PropertiesNotPresentException | PartitionIsDisposedException e) {
            LOG.warn("Write dropped on floor because properties missing or partition is dispose. tenant:{} streamId:{}", tenantId, streamId);
        } catch (FailedToAchieveQuorumException e) {
            throw new MiruWALWrongRouteException(e);
        }
    }
}
