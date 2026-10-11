package de.lardtom.propwash.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.lardtom.propwash.registry.client.ClientRegistrar;

public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        de.lardtom.propwash.registry.client.ConfigScreenFactory factory = ClientRegistrar.configScreenFactory();
        if (factory == null) {
            return ModMenuApi.super.getModConfigScreenFactory();
        }
        return factory::create;
    }
}
