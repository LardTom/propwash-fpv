package de.lardtom.propwash.dev;

import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.dev.network.SelfTestNet;
import de.lardtom.propwash.join.AddonInfo;
import de.lardtom.propwash.join.LocalJoinInfo;
import de.lardtom.propwash.network.ProtocolVersionPayload;

import java.util.List;

public final class DevBootstrap {
    public static final String EXAMPLE_NAME = "Propwash Example Add-on";
    public static final String EXAMPLE_VERSION = "1.0.0";
    public static final String EXAMPLE_HOMEPAGE = "https://propwashfpv.com/docs/api/";

    private DevBootstrap() {
    }

    public static void init() {
        SelfTestNet.init();
        ServerSmoke.init();
        if (JoinTestEnv.protocolOverride() > 0) {
            ProtocolVersionPayload.overrideLocalProtocol(JoinTestEnv.protocolOverride());
        }
        if (JoinTestEnv.exampleAddon()) {
            ExampleAddon.init();
            LocalJoinInfo.declare(new AddonInfo(ExampleAddon.NAMESPACE, EXAMPLE_NAME, EXAMPLE_VERSION, EXAMPLE_HOMEPAGE,
                    List.of(ExampleAddon.NAMESPACE)));
        }
    }
}
