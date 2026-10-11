package de.lardtom.propwash.api.client;

import de.lardtom.propwash.apiimpl.client.ClientApiImpl;

public interface OsdLayoutAccess {
    OsdLayoutSpec current();

    OsdLayoutSpec defaults();

    boolean apply(OsdLayoutSpec layout);

    default boolean validate(OsdLayoutSpec layout) {
        return ClientApiImpl.validOsd(layout);
    }
}
