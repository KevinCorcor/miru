package com.jivesoftware.os.miru.reco.plugins;

import com.google.common.collect.Maps;
import com.jivesoftware.os.miru.api.activity.MiruActivity;
import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivityFactory;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class CollaborativeFilterUtil {

    private final MiruPartitionedActivityFactory partitionedActivityFactory = new MiruPartitionedActivityFactory();

    public MiruPartitionedActivity viewActivity(MiruTenantId tenantId, MiruPartitionId partitionId, long time, String user, String doc, int index) {
        Map<String, List<String>> fieldsValues = Maps.newHashMap();
        fieldsValues.put("user", Arrays.asList(user));
        fieldsValues.put("doc", Arrays.asList(doc));

        MiruActivity activity = new MiruActivity(tenantId, time, new String[0], 0, fieldsValues, Collections.<String, List<String>>emptyMap());
        return partitionedActivityFactory.activity(1, partitionId, index, activity);
    }

}
