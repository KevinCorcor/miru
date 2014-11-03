package com.jivesoftware.os.miru.plugin.context;

import com.jivesoftware.os.jive.utils.base.util.locks.StripingLocksProvider;
import com.jivesoftware.os.miru.api.activity.schema.MiruSchema;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.plugin.index.MiruActivityIndex;
import com.jivesoftware.os.miru.plugin.index.MiruAuthzIndex;
import com.jivesoftware.os.miru.plugin.index.MiruFieldIndex;
import com.jivesoftware.os.miru.plugin.index.MiruInboxIndex;
import com.jivesoftware.os.miru.plugin.index.MiruRemovalIndex;
import com.jivesoftware.os.miru.plugin.index.MiruTimeIndex;
import com.jivesoftware.os.miru.plugin.index.MiruUnreadTrackingIndex;
import com.jivesoftware.os.miru.wal.readtracking.MiruReadTrackingWALReader;

/**
 * @author jonathan
 */
public interface MiruRequestContext<BM> {

    MiruSchema getSchema();

    MiruTimeIndex getTimeIndex();

    MiruActivityIndex getActivityIndex();

    MiruFieldIndex<BM> getFieldIndex();

    MiruAuthzIndex<BM> getAuthzIndex();

    MiruRemovalIndex<BM> getRemovalIndex();

    MiruUnreadTrackingIndex<BM> getUnreadTrackingIndex();

    MiruInboxIndex<BM> getInboxIndex();

    MiruReadTrackingWALReader getReadTrackingWALReader();

    StripingLocksProvider<MiruStreamId> getStreamLocks();
}
