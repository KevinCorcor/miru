package com.jivesoftware.os.miru.wal.deployable.region;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.wal.MiruReadSipEntry;
import com.jivesoftware.os.miru.api.wal.MiruWALClient;
import com.jivesoftware.os.miru.api.wal.MiruWALEntry;
import com.jivesoftware.os.miru.ui.MiruPageRegion;
import com.jivesoftware.os.miru.ui.MiruSoyRenderer;
import com.jivesoftware.os.miru.wal.MiruWALDirector;
import com.jivesoftware.os.miru.wal.deployable.region.bean.WALBean;
import com.jivesoftware.os.miru.wal.deployable.region.input.MiruReadWALRegionInput;
import com.jivesoftware.os.mlogger.core.MetricLogger;
import com.jivesoftware.os.mlogger.core.MetricLoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang.exception.ExceptionUtils;

/**
 *
 */
public class MiruReadWALRegion implements MiruPageRegion<MiruReadWALRegionInput> {

    private static final MetricLogger log = MetricLoggerFactory.getLogger();

    private final String template;
    private final MiruSoyRenderer renderer;
    private final MiruWALDirector miruWALDirector;

    public MiruReadWALRegion(String template, MiruSoyRenderer renderer, MiruWALDirector miruWALDirector) {
        this.template = template;
        this.renderer = renderer;
        this.miruWALDirector = miruWALDirector;
    }

    @Override
    public String render(MiruReadWALRegionInput miruReadWALRegionInput) {
        Map<String, Object> data = Maps.newHashMap();

        Optional<MiruTenantId> optionalTenantId = miruReadWALRegionInput.getTenantId();
        Optional<String> optionalStreamId = miruReadWALRegionInput.getStreamId();

        if (optionalTenantId.isPresent()) {
            MiruTenantId miruTenantId = optionalTenantId.get();
            data.put("tenant", new String(miruTenantId.getBytes(), Charsets.UTF_8));

            if (optionalStreamId.isPresent()) {
                String streamId = optionalStreamId.get();
                data.put("streamId", streamId);

                boolean sip = miruReadWALRegionInput.getSip().or(false);
                final int limit = miruReadWALRegionInput.getLimit().or(100);
                long afterTimestamp = miruReadWALRegionInput.getAfterTimestamp().or(0l);
                List<WALBean> walReadEvents = Lists.newArrayList();
                final AtomicLong lastTimestamp = new AtomicLong();

                MiruStreamId miruStreamId = new MiruStreamId(streamId.getBytes(Charsets.UTF_8));

                try {
                    if (sip) {

                        final MiruWALClient.StreamBatch<MiruReadSipEntry, MiruWALClient.SipReadCursor> sipped = miruWALDirector.sipRead(miruTenantId,
                            miruStreamId,
                            new MiruWALClient.SipReadCursor(afterTimestamp, 0),
                            limit);

                        walReadEvents = Lists.transform(sipped.batch,
                            input -> new WALBean(input.timestamp, Optional.<MiruPartitionedActivity>absent(), input.eventId));
                        lastTimestamp.set(sipped.cursor != null ? sipped.cursor.sipId : Long.MAX_VALUE);

                    } else {
                        MiruWALClient.StreamBatch<MiruWALEntry, MiruWALClient.GetReadCursor> read = miruWALDirector.getRead(miruTenantId, miruStreamId,
                            new MiruWALClient.GetReadCursor(afterTimestamp), limit);

                        walReadEvents = Lists.transform(read.batch, input -> new WALBean(input.collisionId, Optional.of(input.activity), input.version));
                        lastTimestamp.set(read.cursor != null ? read.cursor.eventId : Long.MAX_VALUE);
                    }
                } catch (Exception e) {
                    log.error("Failed to read read-tracking WAL", e);
                    data.put("error", ExceptionUtils.getStackTrace(e));
                }

                data.put("sip", sip);
                data.put("limit", limit);
                data.put("afterTimestamp", String.valueOf(afterTimestamp));
                data.put("activities", walReadEvents);
                data.put("nextTimestamp", String.valueOf(lastTimestamp.get() + 1));
            }
        }

        return renderer.render(template, data);
    }

    @Override
    public String getTitle() {
        return "Read WAL";
    }
}
