package de.lardtom.propwash.api.client;

import de.lardtom.propwash.api.build.TuneSpec;

public interface ClientTune {
    boolean available();

    TuneSpec current();

    TuneSpec defaults();

    boolean apply(TuneSpec tune);
}
