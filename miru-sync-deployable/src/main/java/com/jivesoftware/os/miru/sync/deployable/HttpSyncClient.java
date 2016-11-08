package com.jivesoftware.os.miru.sync.deployable;

import com.jivesoftware.os.miru.api.activity.MiruPartitionId;
import com.jivesoftware.os.miru.api.activity.MiruPartitionedActivity;
import com.jivesoftware.os.miru.api.base.MiruStreamId;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.miru.api.sync.MiruSyncClient;
import com.jivesoftware.os.routing.bird.http.client.HttpRequestHelper;
import java.util.List;

/**
 *
 */
public class HttpSyncClient implements MiruSyncClient {

    private final HttpRequestHelper httpRequestHelper;
    private final String activityPath;
    private final String readTrackingPath;

    public HttpSyncClient(HttpRequestHelper httpRequestHelper, String activityPath, String readTrackingPath) {
        this.httpRequestHelper = httpRequestHelper;
        this.activityPath = activityPath;
        this.readTrackingPath = readTrackingPath;
    }

    @Override
    public void writeActivity(MiruTenantId tenantId, MiruPartitionId partitionId, List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        String endpoint = activityPath + '/' + tenantId.toString() + '/' + partitionId.getId();
        String result = httpRequestHelper.executeRequest(partitionedActivities, endpoint, String.class, null);
        if (result == null) {
            throw new SyncClientException("Empty response from sync receiver");
        }
    }

    @Override
    public void writeReadTracking(MiruTenantId tenantId, MiruStreamId streamId, List<MiruPartitionedActivity> partitionedActivities) throws Exception {
        String endpoint = readTrackingPath + '/' + tenantId.toString() + '/' + streamId.toString();
        String result = httpRequestHelper.executeRequest(partitionedActivities, endpoint, String.class, null);
        if (result == null) {
            throw new SyncClientException("Empty response from sync receiver");
        }
    }
}