package de.lardtom.propwash.share.client.chat;

import de.lardtom.propwash.config.JsonConfig;

public final class ShareChatConfig {
    public static final JsonConfig<ShareChatConfig> CONFIG = JsonConfig.create("share_chat", ShareChatConfig.class, ShareChatConfig::new);

    public boolean clickableCodes = true;

    public static ShareChatConfig get() {
        return CONFIG.get();
    }
}
