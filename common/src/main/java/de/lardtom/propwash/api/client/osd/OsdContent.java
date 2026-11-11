package de.lardtom.propwash.api.client.osd;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface OsdContent {
    @Nullable OsdText text(OsdContext context);
}
