package com.jivesoftware.os.miru.api.marshall;

import com.google.common.base.Preconditions;
import com.jivesoftware.os.miru.api.base.MiruTenantId;
import com.jivesoftware.os.rcvs.marshall.api.TypeMarshaller;

/**
 *
 */
public class MiruTenantIdMarshaller implements TypeMarshaller<MiruTenantId> {

    @Override
    public MiruTenantId fromBytes(byte[] bytes) throws Exception {
        return fromLexBytes(bytes);
    }

    @Override
    public byte[] toBytes(MiruTenantId tenantId) throws Exception {
        return toLexBytes(tenantId);
    }

    @Override
    public MiruTenantId fromLexBytes(byte[] bytes) throws Exception {
        Preconditions.checkNotNull(bytes);
        return new MiruTenantId(bytes);
    }

    @Override
    public byte[] toLexBytes(MiruTenantId tenantId) throws Exception {
        Preconditions.checkNotNull(tenantId);
        return tenantId.getBytes();
    }
}
