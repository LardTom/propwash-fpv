package de.lardtom.propwash.platform;

import de.lardtom.propwash.registry.MenuDataFactory;
import net.minecraft.core.BlockPos;
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
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;

public interface IPlatformHelper {
    String getPlatformName();

    boolean isModLoaded(String modId);

    String getModVersion();

    boolean isDevelopmentEnvironment();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    Path getConfigDir();

    Path getGameDir();

    boolean isClientDist();

    CreativeModeTab.Builder creativeTabBuilder();

    default CreativeModeTab.Builder creativeTabBuilderAfter(Identifier previous) {
        return creativeTabBuilder();
    }

    default List<Identifier> sortedCreativeTabs() {
        return List.of();
    }

    default Optional<String> getModName(String modId) {
        return Optional.empty();
    }

    default Optional<String> modVersion(String modId) {
        return Optional.empty();
    }

    default Optional<ModMeta> modMeta(String modId) {
        return Optional.empty();
    }

    default List<ModMeta> dependentMods(String modId) {
        return List.of();
    }

    <T extends AbstractContainerMenu, D> MenuType<T> extendedMenuType(MenuDataFactory<T, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec);

    <D> void openMenu(ServerPlayer player, MenuProvider provider, StreamCodec<? super RegistryFriendlyByteBuf, D> codec, D data);

    SortedMap<String, byte[]> readModResources(String modId, String folder, String suffix);

    default List<String> resourceModIds() {
        return List.of();
    }

    default boolean allowBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        return true;
    }

    default boolean probeBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        return true;
    }

    default void afterBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
    }
}
