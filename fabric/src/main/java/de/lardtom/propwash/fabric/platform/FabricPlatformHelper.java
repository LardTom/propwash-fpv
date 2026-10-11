package de.lardtom.propwash.fabric.platform;

import de.lardtom.propwash.Constants;
import de.lardtom.propwash.platform.IPlatformHelper;
import de.lardtom.propwash.platform.ModMeta;
import de.lardtom.propwash.registry.MenuDataFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class FabricPlatformHelper implements IPlatformHelper {
    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public String getModVersion() {
        return FabricLoader.getInstance().getModContainer(Constants.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getGameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public boolean isClientDist() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    @Override
    public Optional<String> getModName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getName());
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> container.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    public Optional<ModMeta> modMeta(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(container -> meta(container.getMetadata()));
    }

    @Override
    public List<ModMeta> dependentMods(String modId) {
        List<ModMeta> out = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata metadata = container.getMetadata();
            if (metadata.getId().equals(modId)) {
                continue;
            }
            for (ModDependency dependency : metadata.getDependencies()) {
                if (dependency.getModId().equals(modId) && dependency.getKind().isPositive()) {
                    out.add(meta(metadata));
                    break;
                }
            }
        }
        return out;
    }

    private static ModMeta meta(ModMetadata metadata) {
        String homepage = metadata.getContact().get("homepage").or(() -> metadata.getContact().get("sources")).orElse("");
        return new ModMeta(metadata.getId(), metadata.getName(), metadata.getVersion().getFriendlyString(), homepage);
    }

    @Override
    public <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(MenuDataFactory<T, D> factory,
                                                                          StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return new ExtendedMenuType<>(factory::create, codec);
    }

    @Override
    public <D> void openMenu(ServerPlayer player, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> codec, D data) {
        player.openMenu(new ExtendedMenuProvider<D>() {
            @Override
            public D getScreenOpeningData(ServerPlayer p) {
                return data;
            }

            @Override
            public Component getDisplayName() {
                return provider.getDisplayName();
            }

            @Override
            public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
                return provider.createMenu(containerId, inventory, p);
            }
        });
    }

    @Override
    public List<String> resourceModIds() {
        List<String> ids = new ArrayList<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (!"builtin".equals(container.getMetadata().getType())) {
                ids.add(container.getMetadata().getId());
            }
        }
        ids.sort(String::compareTo);
        return List.copyOf(ids);
    }

    @Override
    public SortedMap<String, byte[]> readModResources(String modId, String folder, String suffix) {
        ModContainer container = FabricLoader.getInstance().getModContainer(modId)
                .orElseThrow(() -> new IllegalArgumentException("Mod " + modId + " ist nicht geladen"));
        SortedMap<String, byte[]> out = new TreeMap<>();
        for (Path root : container.getRootPaths()) {
            Path start = root.resolve(folder);
            if (!Files.isDirectory(start)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(start)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
                    if (relative.endsWith(suffix) && !out.containsKey(relative)) {
                        out.put(relative, Files.readAllBytes(file));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Ressourcen von " + modId + " unter " + folder + " nicht lesbar", e);
            }
        }
        return out;
    }

    @Override
    public boolean allowBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
            return true;
        }
        PlayerBlockBreakEvents.CANCELED.invoker().onBlockBreakCanceled(level, player, pos, state, blockEntity);
        return false;
    }

    @Override
    public boolean probeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity);
    }

    @Override
    public void afterBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
    }
}
