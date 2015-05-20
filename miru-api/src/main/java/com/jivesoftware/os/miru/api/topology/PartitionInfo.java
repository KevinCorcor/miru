package com.jivesoftware.os.miru.api.topology;

import com.jivesoftware.os.miru.api.MiruPartitionCoordInfo;
import com.jivesoftware.os.miru.api.base.MiruTenantId;

/**
 *
 */
public class PartitionInfo {

    public MiruTenantId tenantId;
    public int partitionId;
    public long queryTimestamp; // -1 means unchanged
    public MiruPartitionCoordInfo info;

    public PartitionInfo() {
    }

    public PartitionInfo(MiruTenantId tenantId, int partitionId, long queryTimestamp, MiruPartitionCoordInfo info) {
        this.tenantId = tenantId;
        this.partitionId = partitionId;
        this.queryTimestamp = queryTimestamp;
        this.info = info;
    }

}
