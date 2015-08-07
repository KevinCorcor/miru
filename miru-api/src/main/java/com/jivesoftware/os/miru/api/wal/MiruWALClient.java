package com.jivesoftware.os.miru.api.wal;

import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.routing.bird.shared.HostPort;
import java.util.List;

/**
 * @author jonathan.colt
 */
public interface MiruWALClient<C extends MiruCursor<C, S>, S extends MiruSipCursor<S>> {

    HostPort[] getTenantRoutingGroup(RoutingGroupType routingGroupType, MiruTenantId tenantId) throws Exception;

    HostPort[] getTenantPartitionRoutingGroup(RoutingGroupType routingGroupType, MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception;

    HostPort[] getTenantStreamRoutingGroup(RoutingGroupType routingGroupType, MiruTenantId tenantId, MiruStreamId streamId) throws Exception;

    enum RoutingGroupType {
        activity,
        readTracking
    }

    List<MiruTenantId> getAllTenantIds() throws Exception;

    void writeActivity(MiruTenantId tenantId, MiruPartitionId partitionId, List<MiruPartitionedActivity> partitionedActivities) throws Exception;

    void writeReadTracking(MiruTenantId tenantId, MiruStreamId streamId, List<MiruPartitionedActivity> partitionedActivities) throws Exception;

    MiruPartitionId getLargestPartitionId(MiruTenantId tenantId) throws Exception;

    WriterCursor getCursorForWriterId(MiruTenantId tenantId, MiruPartitionId partitionId, int writerId) throws Exception;

    class WriterCursor {

        public int partitionId;
        public int index;

        public WriterCursor() {
        }

        public WriterCursor(int partitionId, int index) {
            this.partitionId = partitionId;
            this.index = index;
        }
    }

    MiruActivityWALStatus getActivityWALStatusForTenant(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception;

    long oldestActivityClockTimestamp(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception;

    List<MiruVersionedActivityLookupEntry> getVersionedEntries(MiruTenantId tenantId, MiruPartitionId partitionId, Long[] timestamps) throws Exception;

    StreamBatch<MiruWALEntry, C> getActivity(MiruTenantId tenantId,
        MiruPartitionId partitionId, C cursor, int batchSize) throws Exception;

    StreamBatch<MiruWALEntry, S> sipActivity(MiruTenantId tenantId,
        MiruPartitionId partitionId, S cursor, int batchSize) throws Exception;

    class StreamBatch<T, C> {

        public List<T> activities; // non final for json ser-der
        public List<T> boundaries; // non final for json ser-der
        public C cursor; // non final for json ser-der
        public boolean endOfStream; // non final for json ser-der

        public StreamBatch() {
        }

        public StreamBatch(List<T> activities, List<T> boundaries, C cursor, boolean endOfStream) {
            this.activities = activities;
            this.boundaries = boundaries;
            this.cursor = cursor;
            this.endOfStream = endOfStream;
        }

        @Override
        public String toString() {
            return "StreamBatch{" +
                "activities=" + activities +
                ", boundaries=" + boundaries +
                ", cursor=" + cursor +
                ", endOfStream=" + endOfStream +
                '}';
        }
    }

    StreamBatch<MiruWALEntry, S> getRead(MiruTenantId tenantId, MiruStreamId streamId, S cursor, long oldestEventId, int batchSize) throws Exception;

}
