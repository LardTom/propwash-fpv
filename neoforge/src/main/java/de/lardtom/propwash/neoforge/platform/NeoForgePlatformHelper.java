package de.lardtom.propwash.neoforge.platform;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.IPlatformHelper;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.registry.MenuDataFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

public final class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public String getModVersion() {
        return ModList.get().getModContainerById(Constants.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isClientDist() {
        return FMLEnvironment.getDist() == Dist.CLIENT;
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilderAfter(Identifier previous) {
        return CreativeModeTab.builder().withTabsBefore(previous);
    }

    @Override
    public List<Identifier> sortedCreativeTabs() {
        List<Identifier> ids = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabRegistry.getSortedCreativeModeTabs()) {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public Optional<String> getModName(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getDisplayName());
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> container.getModInfo().getVersion().toString());
    }

    @Override
    public Optional<ModMeta> modMeta(String modId) {
        return ModList.get().getModContainerById(modId).map(container -> meta(container.getModInfo()));
    }

    @Override
    public List<ModMeta> dependentMods(String modId) {
        List<ModMeta> out = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            if (info.getModId().equals(modId)) {
                continue;
            }
            for (IModInfo.ModVersion dependency : info.getDependencies()) {
                if (dependency.getModId().equals(modId) && (dependency.getType() == IModInfo.DependencyType.REQUIRED
                        || dependency.getType() == IModInfo.DependencyType.OPTIONAL)) {
                    out.add(meta(info));
                    break;
                }
            }
        }
        return out;
    }

    private static ModMeta meta(IModInfo info) {
        Optional<Object> display = info.getConfig().getConfigElement("displayURL");
        String homepage = display.map(String::valueOf).filter(url -> !url.isBlank())
                .or(() -> info.getModURL().map(Object::toString)).orElse("");
        return new ModMeta(info.getModId(), info.getDisplayName(), info.getVersion().toString(), homepage);
    }

    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(MenuDataFactory<T, D> factory,
                                                                          StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return IMenuTypeExtension.create((containerId, inventory, buf) -> factory.create(containerId, inventory, codec.decode(buf)));
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> codec, D data) {
        player.openMenu(provider, buf -> codec.encode(buf, data));
    }

    @Override
    public List<String> resourceModIds() {
        List<String> ids = new ArrayList<>();
        for (IModInfo info : ModList.get().getMods()) {
            if (!"minecraft".equals(info.getModId())) {
                ids.add(info.getModId());
            }
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public SortedMap<String, byte[]> readModResources(String modId, String folder, String suffix) {
        IModFileInfo info = ModList.get().getModFileById(modId);
        if (info == null) {
            throw new IllegalArgumentException("Mod " + modId + " ist nicht geladen");
        }
        SortedMap<String, byte[]> out = new TreeMap<>();
        info.getFile().getContents().visitContent(folder, (path, resource) -> {
            if (path.endsWith(suffix) && !out.containsKey(path)) {
                try {
                    out.put(path, resource.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException("Ressource " + path + " von " + modId + " nicht lesbar", e);
                }
            }
        });
        return out;
    }

    @Override
    public boolean allowBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        BreakBlockEvent event = new BreakBlockEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    @Override
    public boolean probeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        return this.allowBlockBreak(level, player, pos, state, blockEntity);
    }
}
