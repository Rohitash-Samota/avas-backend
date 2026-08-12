package com.avas.platform.common.persistence;

import jakarta.persistence.MappedSuperclass;

/**
 * Compatibility name used by the project and pricing persistence modules.
 *
 * <p>The generated identity mapping remains centralized in
 * {@link AbstractLongIdEntity}.</p>
 */
@MappedSuperclass
public abstract class LongIdentityEntity extends AbstractLongIdEntity {
}
