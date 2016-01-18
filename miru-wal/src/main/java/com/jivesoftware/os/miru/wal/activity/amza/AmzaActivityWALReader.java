package com.jivesoftware.os.miru.wal.activity.amza;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jivesoftware.os.amza.api.partition.Consistency;
import com.jivesoftware.os.amza.api.partition.PartitionProperties;
import com.jivesoftware.os.amza.api.take.TakeCursors;
import com.jivesoftware.os.amza.service.EmbeddedClientProvider.EmbeddedClient;
import com.jivesoftware.os.amza.service.PropertiesNotPresentException;
import com.jivesoftware.os.filer.io.FilerIO;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.activity.TimeAndVersion;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.marshall.JacksonJsonObjectTypeMarshaller;
import com.jivesoftware.os.miru.api.topology.NamedCursor;
import com.jivesoftware.os.miru.api.wal.AmzaCursor;
import com.jivesoftware.os.miru.api.wal.AmzaSipCursor;
import com.jivesoftware.os.miru.api.wal.MiruActivityLookupEntry;
import com.jivesoftware.os.miru.api.wal.MiruActivityWALStatus;
import com.jivesoftware.os.miru.api.wal.MiruActivityWALStatus.WriterCount;
import com.jivesoftware.os.miru.api.wal.MiruVersionedActivityLookupEntry;
import com.jivesoftware.os.miru.api.wal.MiruWALClient.WriterCursor;
import com.jivesoftware.os.miru.wal.AmzaWALUtil;
import com.jivesoftware.os.miru.wal.activity.MiruActivityWALReader;
import com.jivesoftware.os.miru.wal.activity.rcvs.MiruActivityWALColumnKey;
import com.jivesoftware.os.miru.wal.activity.rcvs.MiruActivityWALColumnKeyMarshaller;
import com.jivesoftware.os.miru.wal.lookup.PartitionsStream;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import com.jivesoftware.os.routing.bird.shared.HostPort;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.mutable.MutableLong;

/**
 *
 */
public class AmzaActivityWALReader implements MiruActivityWALReader<AmzaCursor, AmzaSipCursor> {

    private static final MetricLogger LOG = MetricLoggerFactory.getLogger();

    private final AmzaWALUtil amzaWALUtil;
    private final MiruActivityWALColumnKeyMarshaller columnKeyMarshaller = new MiruActivityWALColumnKeyMarshaller();
    private final JacksonJsonObjectTypeMarshaller<MiruPartitionedActivity> partitionedActivityMarshaller;

    public AmzaActivityWALReader(AmzaWALUtil amzaWALUtil, ObjectMapper mapper) {
        this.amzaWALUtil = amzaWALUtil;
        this.partitionedActivityMarshaller = new JacksonJsonObjectTypeMarshaller<>(MiruPartitionedActivity.class, mapper);
    }

    private TakeCursors takeCursors(StreamMiruActivityWAL streamMiruActivityWAL,
        StreamSuppressed streamSuppressed,
        EmbeddedClient client,
        Map<String, NamedCursor> cursorsByName,
        Set<TimeAndVersion> lastSeen) throws Exception {
        return amzaWALUtil.take(client, cursorsByName,
            (long rowTxId, byte[] prefix, byte[] key, byte[] value, long valueTimestamp, boolean valueTombstoned, long valueVersion) -> {
                MiruPartitionedActivity partitionedActivity = partitionedActivityMarshaller.fromBytes(value);
                if (partitionedActivity != null
                    && partitionedActivity.type.isActivityType()
                    && !streamAlreadySeen(lastSeen, partitionedActivity, streamSuppressed)) {
                    if (!streamMiruActivityWAL.stream(partitionedActivity.timestamp, partitionedActivity, valueTimestamp)) {
                        return false;
                    }
                }
                return true;
            });
    }

    private boolean streamAlreadySeen(Set<TimeAndVersion> lastSeen,
        MiruPartitionedActivity partitionedActivity,
        StreamSuppressed streamSuppressed) throws Exception {
        if (lastSeen != null) {
            long version = partitionedActivity.activity.isPresent() ? partitionedActivity.activity.get().version : 0;
            TimeAndVersion timeAndVersion = new TimeAndVersion(partitionedActivity.timestamp, version);
            if (lastSeen.contains(timeAndVersion)) {
                if (streamSuppressed != null) {
                    streamSuppressed.stream(timeAndVersion);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isEndOfStream(EmbeddedClient client) throws Exception {
        byte[] fromKey = columnKeyMarshaller.toLexBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.END.getSort(), Long.MIN_VALUE));
        byte[] toKey = columnKeyMarshaller.toLexBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.BEGIN.getSort(), Long.MAX_VALUE));
        Set<Integer> begins = Sets.newHashSet();
        Set<Integer> ends = Sets.newHashSet();
        client.scan(null, fromKey, null, toKey,
            (byte[] prefix, byte[] key, byte[] value, long timestamp, long version) -> {
                MiruPartitionedActivity partitionedActivity = partitionedActivityMarshaller.fromBytes(value);
                if (partitionedActivity != null) {
                    if (partitionedActivity.type == MiruPartitionedActivity.Type.BEGIN) {
                        begins.add(partitionedActivity.writerId);
                    } else if (partitionedActivity.type == MiruPartitionedActivity.Type.END) {
                        ends.add(partitionedActivity.writerId);
                    }
                }
                return true;
            });
        return !begins.isEmpty() && ends.containsAll(begins);
    }

    @Override
    public HostPort[] getRoutingGroup(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        return amzaWALUtil.getActivityRoutingGroup(tenantId, partitionId, Optional.<PartitionProperties>absent());
    }

    @Override
    public AmzaCursor stream(MiruTenantId tenantId,
        MiruPartitionId partitionId,
        AmzaCursor cursor,
        int batchSize,
        StreamMiruActivityWAL streamMiruActivityWAL) throws Exception {

        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        if (client == null) {
            return cursor;
        }

        try {
            Map<String, NamedCursor> cursorsByName = cursor != null ? amzaWALUtil.extractCursors(cursor.cursors) : Maps.newHashMap();
            Map<String, NamedCursor> sipCursorsByName = cursor != null && cursor.sipCursor != null
                ? amzaWALUtil.extractCursors(cursor.sipCursor.cursors) : Maps.newHashMap();

            TakeCursors takeCursors = takeCursors(streamMiruActivityWAL, null, client, cursorsByName, null);

            amzaWALUtil.mergeCursors(cursorsByName, takeCursors);
            amzaWALUtil.mergeCursors(sipCursorsByName, takeCursors);

            return new AmzaCursor(cursorsByName.values(), new AmzaSipCursor(sipCursorsByName.values(), false));
        } catch (PropertiesNotPresentException e) {
            LOG.warn("Empty stream for nonexistent partition {} {}", tenantId, partitionId);
            return cursor;
        }
    }

    @Override
    public AmzaSipCursor streamSip(MiruTenantId tenantId,
        MiruPartitionId partitionId,
        AmzaSipCursor sipCursor,
        Set<TimeAndVersion> lastSeen,
        int batchSize,
        StreamMiruActivityWAL streamMiruActivityWAL,
        StreamSuppressed streamSuppressed) throws Exception {

        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        if (client == null) {
            return sipCursor;
        }

        try {
            Map<String, NamedCursor> sipCursorsByName = sipCursor != null ? amzaWALUtil.extractCursors(sipCursor.cursors) : Maps.newHashMap();

            TakeCursors takeCursors = takeCursors(streamMiruActivityWAL, streamSuppressed, client, sipCursorsByName, lastSeen);
            if (takeCursors.tookToEnd) {
                streamMiruActivityWAL.stream(-1, null, -1);
            }
            boolean endOfStream = takeCursors.tookToEnd && isEndOfStream(client);

            amzaWALUtil.mergeCursors(sipCursorsByName, takeCursors);

            return new AmzaSipCursor(sipCursorsByName.values(), endOfStream);
        } catch (PropertiesNotPresentException e) {
            LOG.warn("Empty streamSip for nonexistent partition {} {}", tenantId, partitionId);
            return sipCursor;
        }
    }

    @Override
    public WriterCursor getCursorForWriterId(MiruTenantId tenantId, MiruPartitionId partitionId, int writerId) throws Exception {
        LOG.inc("getCursorForWriterId");
        LOG.inc("getCursorForWriterId>" + writerId, tenantId.toString());
        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        if (client != null) {
            try {
                byte[] key = columnKeyMarshaller.toLexBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.BEGIN.getSort(), (long) writerId));
                byte[] valueBytes = client.getValue(Consistency.leader_quorum, null, key);
                if (valueBytes != null) {
                    MiruPartitionedActivity latestBoundaryActivity = partitionedActivityMarshaller.fromBytes(valueBytes);
                    return new WriterCursor(latestBoundaryActivity.getPartitionId(), latestBoundaryActivity.index);
                }
            } catch (PropertiesNotPresentException e) {
                // ignored
            }
        }
        return new WriterCursor(0, 0);
    }

    @Override
    public MiruActivityWALStatus getStatus(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        final Map<Integer, WriterCount> counts = Maps.newHashMap();
        final List<Integer> begins = Lists.newArrayList();
        final List<Integer> ends = Lists.newArrayList();
        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        if (client != null) {
            try {
                byte[] fromKey = columnKeyMarshaller.toLexBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.END.getSort(), 0));
                byte[] toKey = columnKeyMarshaller.toLexBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.BEGIN.getSort(), Long.MAX_VALUE));
                client.scan(null, fromKey, null, toKey,
                    (byte[] prefix, byte[] key, byte[] value, long timestamp, long version) -> {
                        if (value != null) {
                            MiruPartitionedActivity partitionedActivity = partitionedActivityMarshaller.fromBytes(value);
                            if (partitionedActivity.type == MiruPartitionedActivity.Type.BEGIN) {
                                counts.put(partitionedActivity.writerId, new WriterCount(partitionedActivity.writerId, partitionedActivity.index));
                                begins.add(partitionedActivity.writerId);
                            } else if (partitionedActivity.type == MiruPartitionedActivity.Type.END) {
                                ends.add(partitionedActivity.writerId);
                            }
                        }
                        return true;
                    });
            } catch (PropertiesNotPresentException e) {
                // ignored
            }
        }
        return new MiruActivityWALStatus(partitionId, Lists.newArrayList(counts.values()), begins, ends);
    }

    @Override
    public long oldestActivityClockTimestamp(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        final MutableLong oldestClockTimestamp = new MutableLong(-1);
        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        if (client != null) {
            try {
                byte[] fromKey = columnKeyMarshaller.toBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.ACTIVITY.getSort(), 0L));
                byte[] toKey = columnKeyMarshaller.toBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.END.getSort(), 0L));
                client.scan(null, fromKey, null, toKey,
                    (byte[] prefix, byte[] key, byte[] value, long timestamp, long version) -> {
                        if (value != null) {
                            MiruPartitionedActivity partitionedActivity = partitionedActivityMarshaller.fromBytes(value);
                            if (partitionedActivity != null && partitionedActivity.type.isActivityType()) {
                                oldestClockTimestamp.setValue(partitionedActivity.clockTimestamp);
                                return false;
                            }
                        }
                        return true;
                    });
            } catch (PropertiesNotPresentException e) {
                // ignored
            }
        }
        return oldestClockTimestamp.longValue();
    }

    @Override
    public List<MiruVersionedActivityLookupEntry> getVersionedEntries(MiruTenantId tenantId, MiruPartitionId partitionId, Long[] timestamps) throws Exception {
        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        MiruVersionedActivityLookupEntry[] entries = new MiruVersionedActivityLookupEntry[timestamps.length];
        if (client != null) {
            try {
                int[] index = new int[1];
                client.get(Consistency.leader_quorum, null, unprefixedWALKeyStream -> {
                    for (Long timestamp : timestamps) {
                        if (timestamp != null && !unprefixedWALKeyStream.stream(FilerIO.longBytes(timestamp))) {
                            return false;
                        }
                        index[0]++;
                    }
                    return true;
                }, (byte[] prefix, byte[] key, byte[] value, long timestamp, long version) -> {
                    if (value != null) {
                        MiruPartitionedActivity partitionedActivity = partitionedActivityMarshaller.fromBytes(value);
                        entries[index[0]] = new MiruVersionedActivityLookupEntry(
                            partitionedActivity.timestamp,
                            timestamp,
                            new MiruActivityLookupEntry(
                                partitionedActivity.partitionId.getId(),
                                partitionedActivity.index,
                                partitionedActivity.writerId,
                                !partitionedActivity.activity.isPresent()));
                    }
                    return true;
                });
            } catch (PropertiesNotPresentException e) {
                // ignored
            }
        }
        return Arrays.asList(entries);
    }

    @Override
    public void allPartitions(PartitionsStream partitionsStream) throws Exception {
        amzaWALUtil.allActivityPartitions(partitionsStream);
    }

    @Override
    public long clockMax(MiruTenantId tenantId, MiruPartitionId partitionId) throws Exception {
        EmbeddedClient client = amzaWALUtil.getActivityClient(tenantId, partitionId);
        long[] clockTimestamp = { -1L };
        if (client != null) {
            try {
                byte[] fromKey = columnKeyMarshaller.toBytes(new MiruActivityWALColumnKey(MiruPartitionedActivity.Type.END.getSort(), Long.MIN_VALUE));
                client.scan(null, fromKey, null, null, (byte[] prefix, byte[] key, byte[] value, long timestamp, long version) -> {
                    if (value != null) {
                        MiruPartitionedActivity partitionedActivity = partitionedActivityMarshaller.fromBytes(value);
                        if (partitionedActivity != null) {
                            clockTimestamp[0] = Math.max(clockTimestamp[0], partitionedActivity.clockTimestamp);
                        }
                    }
                    return true;
                });
            } catch (PropertiesNotPresentException e) {
                // ignored
            }
        }
        return clockTimestamp[0];
    }
}
