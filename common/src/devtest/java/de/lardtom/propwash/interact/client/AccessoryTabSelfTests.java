package de.lardtom.propwash.interact.client;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.content.ContentItems;
import de.lardtom.propwash.content.ContentTab;
import de.lardtom.propwash.dev.SelfTest;
import de.lardtom.propwash.dev.SelfTest.StepResult;
import de.lardtom.propwash.dev.example.ExampleAddon;
import de.lardtom.propwash.parts.AddonItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AccessoryTabSelfTests {
    private static final String GROUP = AccessoryFixSelfTests.GROUP + "Kreativ-Tab: ";
    private static final String PROBE = "accfix_tab_probe";
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private static final AtomicBoolean ADDED = new AtomicBoolean();

    private AccessoryTabSelfTests() {
    }

    public static void register() {
        SelfTest.clientStep(GROUP + "Teile eines Add-ons mit eigenem Tab, die es zusätzlich über creativeTab() anmeldet, stehen nicht im "
                + "Propwash-Tab; Einträge aus propwash und minecraft bleiben dort", 200, AccessoryTabSelfTests::step);
    }

    private static StepResult step(SelfTest.StepContext ctx) {
        if (Minecraft.getInstance().level == null) {
            return ctx.tick() > 100 ? StepResult.fail("keine Welt") : StepResult.pending();
        }
        if (ADDED.compareAndSet(false, true)) {
            PropwashApi.creativeTab().add(AccessoryTabSelfTests::entries);
        }
        ExtensionTab tab = PropwashApi.extensionTab(ExampleAddon.NAMESPACE);
        List<String> errors = new ArrayList<>();
        List<Identifier> parts = parts();
        if (!tab.registered()) {
            errors.add("Beispiel-Tab nicht registriert: " + tab);
        }
        if (parts.size() < 3) {
            errors.add("Beispiel-Add-on mit nur " + parts.size() + " Teil-Items");
        }
        List<ItemStack> main;
        ACTIVE.set(true);
        try {
            main = ContentTab.contents();
        } finally {
            ACTIVE.set(false);
        }
        TreeSet<String> listed = new TreeSet<>();
        for (ItemStack stack : main) {
            Identifier item = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (ExampleAddon.NAMESPACE.equals(item.getNamespace())) {
                listed.add(item.toString());
            }
        }
        if (!listed.isEmpty()) {
            errors.add(listed.size() + " von " + parts.size() + " Teilen des Beispiels stehen im Propwash-Tab, obwohl es den Tab "
                    + tab.tabId() + " hat: " + listed);
        }
        for (ItemStack probe : probes()) {
            if (main.stream().noneMatch(stack -> ItemStack.isSameItemSameComponents(stack, probe))) {
                errors.add("Eintrag " + BuiltInRegistries.ITEM.getKey(probe.getItem()) + " fehlt im Propwash-Tab");
            }
        }
        if (!errors.isEmpty()) {
            return StepResult.fail(String.join("; ", errors));
        }
        ctx.log("Propwash-Tab mit " + main.size() + " Einträgen ohne die " + parts.size() + " Teile aus " + ExampleAddon.NAMESPACE
                + ", beide Kontroll-Einträge da");
        return StepResult.pass();
    }

    private static List<Identifier> parts() {
        List<Identifier> parts = new ArrayList<>();
        for (Identifier id : AddonItems.INSTANCE.registeredItems().keySet()) {
            if (ExampleAddon.NAMESPACE.equals(id.getNamespace())) {
                parts.add(id);
            }
        }
        parts.sort(Identifier::compareTo);
        return parts;
    }

    private static List<ItemStack> probes() {
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(PROBE));
        ItemStack goggles = new ItemStack(ContentItems.GOGGLES_ANALOG.get());
        goggles.set(DataComponents.CUSTOM_NAME, Component.literal(PROBE));
        return List.of(paper, goggles);
    }

    private static List<ItemStack> entries() {
        if (!ACTIVE.get()) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Identifier part : parts()) {
            stacks.add(PropwashApi.partItems().stack(part));
        }
        stacks.addAll(probes());
        return stacks;
    }
}
