package de.lardtom.propwash.guard;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.FileUtil;
import net.minecraft.world.flag.FeatureFlagSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePathsTest {
    private static final Set<String> OWN = Set.of("data/propwash/tags/item/parts/frame.json", "data/propwash/tags/item/parts/motor.json",
            "assets/propwash/textures/item/goggles.png", "data/propwash/recipe/goggles.json", "data/propwash/propwash_parts/stock.json");
    private static final List<String> PACK_FILES = List.of("pack.mcmeta", "assets/propwash/textures/item/new.png",
            "assets/propwash/textures/item/goggles.png", "assets/propwash/lang/fr_fr.json", "assets/minecraft/textures/item/stick.png",
            "data/propwash/recipe/new.json", "data/propwash/tags/item/parts/frame.json", "data/propwash/propwash_parts/battery.json",
            "data/minecraft/recipe/stick.json");
    private static final List<String> NEW_ASSETS = List.of("assets/propwash/textures/item/new.png");
    private static final List<String> NEW_DATA = List.of("data/propwash/propwash_parts/battery.json", "data/propwash/recipe/new.json");

    @TempDir
    Path temp;

    @Test
    void existingPathIsAllowedIncludingTagAdditions() {
        assertEquals(ResourcePaths.Kind.ALLOWED, ResourcePaths.classify("data/propwash/tags/item/parts/frame.json", true, OWN));
        assertEquals(ResourcePaths.Kind.ALLOWED, ResourcePaths.classify("data/propwash/tags/item/parts/motor.json", true, OWN));
        assertEquals(ResourcePaths.Kind.ALLOWED, ResourcePaths.classify("assets/propwash/textures/item/goggles.png", true, OWN));
    }

    @Test
    void newPathInAModIsAFinding() {
        assertEquals(ResourcePaths.Kind.FINDING, ResourcePaths.classify("data/propwash/recipe/nsprobe.json", true, OWN));
        assertEquals(ResourcePaths.Kind.FINDING, ResourcePaths.classify("assets/propwash/textures/item/nsprobe.png", true, OWN));
        assertEquals(ResourcePaths.Kind.FINDING, ResourcePaths.classify("data/propwash/tags/item/parts/nsprobe.json", true, OWN));
        assertEquals(GuardCategory.DATA, ResourcePaths.category("data/propwash/recipe/nsprobe.json"));
        assertEquals(GuardCategory.ASSET, ResourcePaths.category("assets/propwash/textures/item/nsprobe.png"));
    }

    @Test
    void playerPacksAreOnlyInfo() {
        assertEquals(ResourcePaths.Kind.INFO, ResourcePaths.classify("data/propwash/recipe/nsprobe.json", false, OWN));
        assertEquals(ResourcePaths.Kind.INFO, ResourcePaths.classify("assets/propwash/textures/item/new.png", false, OWN));
        assertEquals(ResourcePaths.Kind.ALLOWED, ResourcePaths.classify("assets/propwash/textures/item/goggles.png", false, OWN));
    }

    @Test
    void otherNamespacesLanguageFilesAndFoldersAreIgnored() {
        assertEquals(ResourcePaths.Kind.IGNORED, ResourcePaths.classify("data/justmoreparts/recipe/frame.json", true, OWN));
        assertEquals(ResourcePaths.Kind.IGNORED, ResourcePaths.classify("data/propwash_example/recipe/box.json", true, OWN));
        assertEquals(ResourcePaths.Kind.IGNORED, ResourcePaths.classify("assets/propwash/lang/fr_fr.json", true, OWN));
        assertEquals(ResourcePaths.Kind.IGNORED, ResourcePaths.classify("data/propwash/recipe/", true, OWN));
        assertEquals(ResourcePaths.Kind.IGNORED, ResourcePaths.classify("data/propwash/", true, OWN));
        assertEquals(ResourcePaths.Kind.IGNORED, ResourcePaths.classify("pack.mcmeta", true, OWN));
    }

    @Test
    void playerPacksAreListedFolderByFolderWithValidPaths() {
        for (String root : List.of(ResourcePaths.ASSETS, ResourcePaths.DATA)) {
            Set<String> folders = ResourcePaths.folders(root, OWN);
            assertFalse(folders.isEmpty(), root);
            for (String folder : folders) {
                assertTrue(FileUtil.decomposePath(folder).isSuccess(), root + folder);
                assertTrue(ResourcePaths.relevant(root + folder + "/file.json"), root + folder);
            }
        }
        assertTrue(ResourcePaths.folders(ResourcePaths.ASSETS, OWN).contains("textures"));
        assertFalse(ResourcePaths.folders(ResourcePaths.ASSETS, Set.of("assets/propwash/lang/en_us.json")).contains("lang"));
        assertTrue(ResourcePaths.folders(ResourcePaths.DATA, OWN).containsAll(List.of("recipe", "tags", "propwash_parts")));
        assertFalse(ResourcePaths.folders(ResourcePaths.DATA, Set.of("data/propwash/drone.json", "data/propwash/../x.json")).contains(".."));
    }

    @Test
    void folderPackListsItsNewFilesAsInfo() throws IOException {
        Path root = this.temp.resolve("v3dir");
        for (String file : PACK_FILES) {
            Path path = root.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "{}");
        }
        try (PackResources pack = new PathPackResources(location("file/v3dir"), root)) {
            assertEquals(NEW_ASSETS, FileScan.packFiles(pack, PackType.CLIENT_RESOURCES, OWN));
            assertEquals(NEW_DATA, FileScan.packFiles(pack, PackType.SERVER_DATA, OWN));
        }
    }

    @Test
    void zipPackListsItsNewFilesAsInfo() throws IOException {
        Path zip = this.temp.resolve("v3info.zip");
        try (OutputStream file = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(file)) {
            for (String entry : PACK_FILES) {
                out.putNextEntry(new ZipEntry(entry));
                out.write("{}".getBytes());
                out.closeEntry();
            }
        }
        Pack.Metadata metadata = new Pack.Metadata(Component.literal("v3info"), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        try (PackResources pack = new FilePackResources.FileResourcesSupplier(zip).openResources(location("file/v3info.zip"), metadata)
                .findFirst().orElseThrow()) {
            assertEquals(NEW_ASSETS, FileScan.packFiles(pack, PackType.CLIENT_RESOURCES, OWN));
            assertEquals(NEW_DATA, FileScan.packFiles(pack, PackType.SERVER_DATA, OWN));
        }
    }

    private static PackLocationInfo location(String id) {
        return new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty());
    }
}
