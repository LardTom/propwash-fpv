package de.lardtom.propwash.join;

import de.lardtom.propwash.join.JoinReport.AddonLine;
import de.lardtom.propwash.join.JoinReport.PropwashLine;
import de.lardtom.propwash.join.JoinReport.Relevance;
import de.lardtom.propwash.join.JoinReport.UnknownParts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class JoinText {
    public static final String KEY = "propwash.join.";
    public static final String DISCONNECT_KEY = KEY + "disconnect.title";
    public static final String PROPWASH_NAME = "Propwash FPV";
    public static final int MAX_MESSAGE_LINES = 8;
    public static final int MAX_LISTED_PARTS = 5;

    private JoinText() {
    }

    private static MutableComponent t(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(KEY + key, fallback, args);
    }

    public static Component title() {
        return t("title", "Add-ons don't match this server");
    }

    public static Component intro(boolean propwashBlocked) {
        return propwashBlocked
                ? t("intro.blocked", "To join, your Propwash add-ons have to match the server's. This is what differs:")
                : t("intro.closed", "The connection was closed. Your Propwash add-ons differ from the server's – most likely that's the reason:");
    }

    public static Component version(@Nullable String version) {
        return version == null ? t("not_installed", "not installed") : Component.literal(version.isBlank() ? "?" : version);
    }

    public static Component versions(AddonLine line) {
        return t("versions", "Server: %s · You: %s", version(line.serverVersion()), version(line.clientVersion()));
    }

    public static Component propwashVersions(PropwashLine line) {
        return t("versions", "Server: %s · You: %s",
                t("version_protocol", "%s (network protocol %s)", line.serverVersion(), String.valueOf(line.serverProtocol())),
                t("version_protocol", "%s (network protocol %s)", line.clientVersion(), String.valueOf(line.clientProtocol())));
    }

    public static Component status(AddonLine line, Relevance relevance) {
        return switch (line.action()) {
            case INSTALL -> relevance == Relevance.INFO ? t("status.server_only", "Only on the server") : t("status.missing", "Missing on your side");
            case REMOVE -> t("status.extra", "Not on the server");
            case UPDATE -> t("status.older", "Older version");
            case SWITCH -> t("status.other_version", "Other version");
            case REINSTALL -> t("status.changed", "Different part data");
        };
    }

    public static Component propwashStatus(PropwashLine line) {
        return line.kind() == PropwashLine.Kind.PROTOCOL
                ? t("status.protocol", "Other network protocol")
                : t("status.builtins", "Other built-in parts");
    }

    public static Component action(AddonLine line, Relevance relevance) {
        Component name = Component.literal(line.name());
        Component target = version(line.serverVersion());
        if (relevance == Relevance.INFO) {
            return switch (line.action()) {
                case INSTALL -> t("note.server_only", "The server also has %s %s. Propwash doesn't need it on your side, but the server might.", name, target);
                case REMOVE -> line.inactive() > 0 || line.providesParts()
                        ? t("note.client_only_inactive", "The server doesn't have it – Propwash lets you join anyway, %s stays inactive while you play there.", name)
                        : t("note.client_only", "Only on your side – Propwash allows that.");
                default -> t("note.same_parts", "Same parts in both versions – Propwash allows that. To match the server exactly, install %s %s.", name, target);
            };
        }
        return switch (line.action()) {
            case INSTALL -> t("action.install", "Install %s %s.", name, target);
            case REMOVE -> t("action.remove", "Remove %s or ask the server owner to install it.", name);
            case UPDATE -> t("action.update", "Update %s to %s.", name, target);
            case SWITCH -> t("action.switch", "Install %s %s, the server's version.", name, target);
            case REINSTALL -> t("action.reinstall", "Reinstall %s %s – your copy has other part data than the server's (changed files?).", name, target);
        };
    }

    public static Component propwashAction(PropwashLine line) {
        if (line.kind() == PropwashLine.Kind.BUILTINS && line.serverVersion().equals(line.clientVersion())) {
            return t("action.propwash_reinstall", "Reinstall Propwash FPV %s – the built-in parts differ from the server's.", line.serverVersion());
        }
        return t("action.propwash", "Install Propwash FPV %s, the server's version.", line.serverVersion());
    }

    public static @Nullable Component parts(AddonLine line) {
        List<Component> parts = new ArrayList<>();
        if (line.missingOnClient() == 1) {
            parts.add(t("parts.missing.one", "1 part missing on your side"));
        } else if (line.missingOnClient() > 1) {
            parts.add(t("parts.missing", "%s parts missing on your side", String.valueOf(line.missingOnClient())));
        }
        if (line.missingOnServer() == 1) {
            parts.add(t("parts.extra.one", "1 part the server doesn't have"));
        } else if (line.missingOnServer() > 1) {
            parts.add(t("parts.extra", "%s parts the server doesn't have", String.valueOf(line.missingOnServer())));
        }
        if (line.inactive() == 1) {
            parts.add(t("parts.inactive.one", "1 part inactive on this server"));
        } else if (line.inactive() > 1) {
            parts.add(t("parts.inactive", "%s parts inactive on this server", String.valueOf(line.inactive())));
        }
        if (line.different() == 1) {
            parts.add(t("parts.different.one", "1 part with other values"));
        } else if (line.different() > 1) {
            parts.add(t("parts.different", "%s parts with other values", String.valueOf(line.different())));
        }
        if (parts.isEmpty()) {
            return null;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }

    public static Component unknownTitle(UnknownParts group) {
        return t("unknown.title", "Unknown parts (%s)", group.namespace());
    }

    public static List<Component> unknownLines(UnknownParts group) {
        List<Component> out = new ArrayList<>();
        if (!group.missingOnClient().isEmpty()) {
            out.add(t("unknown.missing", "Only on the server: %s", listed(group.missingOnClient())));
        }
        if (!group.missingOnServer().isEmpty()) {
            out.add(t("unknown.extra", "Only on your side: %s", listed(group.missingOnServer())));
        }
        if (!group.different().isEmpty()) {
            out.add(t("unknown.different", "Other values: %s", listed(group.different())));
        }
        if (!group.inactive().isEmpty()) {
            out.add(t("unknown.inactive", "Only on your side, inactive on this server: %s", listed(group.inactive())));
        }
        return out;
    }

    public static Component unknownAction() {
        return t("unknown.action", "No add-on on your side or on the server names these parts – ask the server owner which add-on they come from.");
    }

    public static Component unknownAction(UnknownParts group) {
        return group.blocking() ? unknownAction()
                : t("unknown.inactive_action", "Propwash keeps these parts inactive while you play on this server – nothing to do.");
    }

    public static Component sessionTitle(int count) {
        return count == 1 ? t("session.title.one", "Add-on inactive on this server") : t("session.title", "Add-ons inactive on this server");
    }

    public static Component sessionMessage(List<String> names) {
        if (names.size() == 1) {
            return t("session.message.one", "%s is not installed on this server and is inactive for this session.", names.getFirst());
        }
        return t("session.message", "%s are not installed on this server and are inactive for this session.", String.join(", ", names));
    }

    public static Component unavailable() {
        return t("session.unavailable", "Not available on this server");
    }

    public static Component unavailableParts(List<String> ids) {
        return t("session.unavailable_parts", "Parts not available on this server: %s", listed(ids));
    }

    public static Component blockedItem(Component name) {
        return t("session.blocked_item", "%s is not available on this server – Propwash kept it out of your inventory.", name);
    }

    public static Component codeParts(List<String> ids) {
        return t("session.code_parts", "This code uses parts that are not available on this server: %s", listed(ids));
    }

    public static Component listed(List<String> ids) {
        List<String> shown = ids.subList(0, Math.min(ids.size(), MAX_LISTED_PARTS));
        MutableComponent out = Component.literal(String.join(", ", shown));
        if (ids.size() > shown.size()) {
            out.append(" ").append(t("more", "(+%s more)", String.valueOf(ids.size() - shown.size())));
        }
        return out;
    }

    public static Component missingPropwash(String version, int protocol, List<AddonInfo> addons) {
        MutableComponent text = t("missing_propwash", "This server requires Propwash FPV %s (network protocol %s). Install exactly this version.",
                version, String.valueOf(protocol));
        if (!addons.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (AddonInfo addon : addons) {
                names.add(addon.displayName() + " " + addon.version());
            }
            text.append("\n").append(t("missing_propwash.addons", "Propwash add-ons on this server: %s", String.join(", ", names)));
        }
        return text;
    }

    public static Component disconnect(JoinReport report) {
        MutableComponent text = Component.translatableWithFallback(DISCONNECT_KEY, "Your Propwash add-ons don't match this server.");
        List<Component> lines = new ArrayList<>();
        PropwashLine propwash = report.propwash();
        if (propwash != null) {
            lines.add(bullet(Component.literal(PROPWASH_NAME), propwashVersions(propwash), propwashAction(propwash)));
        }
        for (AddonLine line : report.lines(true)) {
            lines.add(bullet(Component.literal(line.name()), versions(line), action(line, report.relevance(line))));
        }
        for (UnknownParts group : report.unknown()) {
            if (!group.blocking()) {
                continue;
            }
            List<Component> details = unknownLines(group);
            lines.add(bullet(unknownTitle(group), details.isEmpty() ? Component.empty() : details.getFirst(), unknownAction()));
        }
        int shown = Math.min(lines.size(), MAX_MESSAGE_LINES);
        for (int i = 0; i < shown; i++) {
            text.append("\n").append(lines.get(i));
        }
        if (lines.size() > shown) {
            text.append("\n").append(t("more", "(+%s more)", String.valueOf(lines.size() - shown)));
        }
        return text;
    }

    private static Component bullet(Component name, Component versions, Component action) {
        return t("disconnect.line", "• %s (%s): %s", name, versions, action);
    }

    public static boolean ours(@Nullable Component reason) {
        if (reason == null) {
            return false;
        }
        if (reason.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            return key.startsWith(KEY) || key.startsWith("propwash.parts.handshake.") || key.startsWith("propwash.network.version_");
        }
        return false;
    }

    public static String details(JoinReport report, @Nullable Component reason) {
        StringBuilder out = new StringBuilder();
        out.append(t("details.header", "Propwash FPV – add-on check").getString()).append('\n');
        PropwashLine propwash = report.propwash();
        if (propwash != null) {
            out.append("- ").append(PROPWASH_NAME).append(" (propwash): ").append(propwashStatus(propwash).getString()).append(". ")
                    .append(propwashVersions(propwash).getString()).append(". ").append(propwashAction(propwash).getString()).append('\n');
        }
        for (AddonLine line : report.addons()) {
            Relevance relevance = report.relevance(line);
            out.append("- ").append(line.name()).append(" (").append(line.modId()).append("): ").append(status(line, relevance).getString())
                    .append(". ").append(versions(line).getString()).append(". ").append(action(line, relevance).getString());
            Component parts = parts(line);
            if (parts != null) {
                out.append(" [").append(parts.getString()).append(']');
            }
            if (!line.homepage().isEmpty()) {
                out.append(' ').append(line.homepage());
            }
            out.append('\n');
        }
        for (UnknownParts group : report.unknown()) {
            out.append("- ").append(unknownTitle(group).getString()).append(':');
            for (Component detail : unknownLines(group)) {
                out.append(' ').append(detail.getString()).append('.');
            }
            out.append(' ').append(unknownAction(group).getString()).append('\n');
        }
        if (reason != null && !ours(reason)) {
            out.append(t("details.reason", "Message: %s", reason.getString().replace('\n', ' ')).getString()).append('\n');
        }
        return out.toString();
    }
}
