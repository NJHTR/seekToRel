package com.reportai.storage;

import com.reportai.common.tenant.TenantId;

import java.util.List;

public interface ObjectIntegrityRepository {
    ObjectIntegrity save(ObjectIntegrity integrity);

    List<ObjectIntegrity> findByOwner(TenantId tenantId, String ownerType, String ownerId);

    void deleteByOwner(TenantId tenantId, String ownerType, String ownerId);
}
