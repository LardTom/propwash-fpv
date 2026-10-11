package de.lardtom.propwash.sim;

import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.build.BatteryType;
import de.lardtom.propwash.build.DroneBuild;
import de.lardtom.propwash.build.FrameType;
import de.lardtom.propwash.build.MotorType;
import de.lardtom.propwash.build.PartIndex;
import de.lardtom.propwash.build.PropType;
import de.lardtom.propwash.build.StackType;
import de.lardtom.propwash.build.VideoType;
import de.lardtom.propwash.parts.BundledParts;
import de.lardtom.propwash.parts.PartDataProblems;
import de.lardtom.propwash.parts.PartJson;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

final class CalibrationHarness {
    static final String ADDON_ENV = "PROPWASH_CALIBRATION_ADDON";
    static final String ADDON_TAG = "addon-daten";
    static final String ADDON_HINT = "Just-More-Parts-Daten fehlen: tools/calibration.sh benutzen oder " + ADDON_ENV + " setzen";
    static final String OUT_ENV = "PROPWASH_CALIBRATION_OUT";
    static final String FIXTURE = "propwash_calib";
    static final String[][] ADDON_PRESETS = {
            {"whoop75_1s", "whoop_75_duct", "aio_whoop_1s_6a", "m0802_19000", "p40_17_3b_pc", "analog_whoop_25mw", "b1s_450_hv"},
            {"whoop85_2s", "whoop_85_duct", "aio_whoop_2s_12a", "m1102_13500", "p45_17_4b_bi", "digital_micro", "b2s_450_hv"},
            {"toothpick2_2s", "tp_2_x", "aio_whoop_2s_12a", "m1103_11000", "p51_20_3b_pc", "analog_whoop_25mw", "b2s_450_hv"},
            {"toothpick3_4s", "tp_3_x", "aio_tp_4s_20a", "m1404_3800", "p76_25_3b_hard", "digital_micro", "propwash:b4s_650"},
            {"toothpick35_6s", "tp_35_stretched", "aio_tp_6s_20a", "m1505_2500", "p89_30_3b_pc", "digital_micro", "b6s_750"},
            {"cinewhoop25_4s", "cw_25_duct", "aio_tp_4s_20a", "m1404_3800", "p65_22_4b_bi", "digital_micro", "b4s_850"},
            {"cinewhoop35_6s", "cw_35_duct", "aio_20_6s_45a", "m2004_1700", "p89_25_5b_pc_duct", "digital_hd_pro", "b6s_850"},
            {"bash5_6s", "fs_5_bash", "stack_30_6s_60a", "m2207_1950", "p130_41_3b_soft", "digital_hd_pro", "propwash:b6s_1300"},
            {"squashed5_6s", "fs_5_squashed", "stack_30_6s_60a", "m2207_1950", "p130_36_3b_bi", "digital_hd_pro", "propwash:b6s_1300"},
            {"deadcat5_6s", "deadcat_5", "stack_30_6s_60a", "m22075_1850", "p130_36_3b_bi", "digital_hd_pro", "propwash:b6s_1300"},
            {"race5_4s", "race_5_stretched", "stack_30_4s_55a", "m23065_2450", "p130_46_3b_hard", "digital_low_latency", "b4s_1500"},
            {"race5_6s", "race_5_stretched", "stack_30_6s_60a", "m23065_2020", "p130_50_3b_gfn", "analog_race_800", "propwash:b6s_1300"},
            {"hframe5_6s", "hframe_5", "stack_30_6s_60a", "m2207_1950", "p130_35_4b_pc", "digital_hd_pro", "propwash:b6s_1300"},
            {"lr4_6s_lion", "lr_4_x", "aio_20_6s_45a", "m2004_1700", "p102_30_3b_gfn", "digital_micro", "b6s_3000_lion"},
            {"lr6_6s", "lr_6_x", "stack_30_6s_60a", "m2507_1500", "p152_33_3b_pc", "digital_lr", "b6s_2200"},
            {"lr7_deadcat_lion", "lr_7_deadcat", "stack_30_6s_60a", "m28065_1300", "p178_40_2b_cfn", "digital_lr", "b6s_4500_lion"},
            {"lr7_8s", "lr_7_deadcat", "stack_30_8s_65a", "m2807_1100", "p178_40_2b_cfn", "digital_lr", "b8s_2200"},
            {"lr8_6s_lion", "lr_8_x", "stack_30_8s_65a", "m2807_1100", "p203_45_2b_cfn", "digital_lr", "b6s_8400_lion"},
            {"xclass10_6s", "xclass_10", "stack_30_8s_100a", "m3115_900", "p254_50_2b_carbon", "digital_hd_pro", "b6s_5000"},
            {"xclass12_8s", "xclass_12", "stack_30_8s_100a", "m4214_400", "p305_60_2b_cfn", "digital_lr", "b8s_5000"},
            {"xclass13_8s", "xclass_13", "stack_30_8s_100a", "m4214_400", "p330_70_2b_carbon", "digital_lr", "b8s_5000"},
            {"xclass13h_8s", "xclass_13_h", "stack_30_8s_100a", "m4214_400", "p330_60_3b_cfn", "digital_lr", "b8s_5000"}};

    static final String MOTOR_27000 = """
            {"format": 1, "mass_g": 1.8, "stator": "0702", "kv": 27000, "mount": "propwash:m08", "max_current_a": 3.4,
             "resistance_ohm": 0.16, "cells": [1, 1], "thermal": {"derate_start_c": 115.0, "max_temp_c": 135.0}}""";
    static final String MOTOR_23000 = """
            {"format": 1, "mass_g": 1.8, "stator": "0702", "kv": 23000, "mount": "propwash:m08", "max_current_a": 3.0,
             "resistance_ohm": 0.21, "cells": [1, 1]}""";
    static final String STACK_WHOOP = """
            {"format": 1, "mass_g": 2.6, "mount": "propwash:whoop_aio", "esc_continuous_a": 6.0, "esc_burst_a": 8.0, "cells": [1, 1],
             "all_in_one": true, "brownout_v": 2.9}""";
    static final String VIDEO_DIGITAL_MICRO = """
            {"format": 1, "mass_g": 4.8, "link": "digital", "latency_ms": 24, "range_blocks": 220, "image_quality": 0.78,
             "camera_width_mm": 14.0, "camera_fov_deg": 126, "vtx_power_mw": 200, "power_w": 5.1}""";
    static final String VIDEO_ANALOG_WHOOP = """
            {"format": 1, "mass_g": 2.2, "link": "analog", "latency_ms": 12, "range_blocks": 200, "image_quality": 0.5,
             "camera_width_mm": 14.0, "camera_fov_deg": 126, "vtx_power_mw": 25, "power_w": 0.7}""";
    static final String PROP_2B = """
            {"format": 1, "mass_g": 0.22, "diameter_mm": 31, "pitch_in": 1.6, "blades": 2, "ct": 0.0802, "cp": 0.0569}""";

    enum Style {
        HOVER("Schweben"),
        CRUISE("Reisen"),
        MIXED("Gemischt"),
        AGGRESSIVE("Aggressiv");

        final String label;

        Style(String label) {
            this.label = label;
        }
    }

    enum Kind {
        WHOOP_65("65-mm-Whoop 1S", new double[][]{{0.45, 0.72}, {0.50, 0.85}, {0.75, 1.10}, {1.10, 1.50}}),
        WHOOP_75("75–85-mm-Whoop", new double[][]{{0.33, 0.45}, {0.37, 0.55}, {0.47, 0.70}, {0.60, 0.90}}),
        TOOTHPICK("Toothpick 2–3,5″", new double[][]{{0.20, 0.40}, {0.25, 0.50}, {0.38, 0.75}, {0.50, 1.00}}),
        CINEWHOOP("Cinewhoop 2,5–3″", new double[][]{{0.20, 0.35}, {0.25, 0.39}, {0.30, 0.55}, {0.45, 0.75}}),
        CINEWHOOP_35("Cinewhoop 3,5″", new double[][]{{0.20, 0.30}, {0.25, 0.30}, {0.28, 0.50}, {0.35, 0.60}}),
        FREESTYLE("5″ Freestyle", new double[][]{{0.22, 0.30}, {0.27, 0.40}, {0.40, 0.55}, {0.50, 0.70}}),
        RACE("5″ Race", new double[][]{{0.20, 0.30}, {0.27, 0.40}, {0.80, 1.00}, {1.00, 1.40}}),
        LONG_RANGE("Long Range 4–8″", new double[][]{{0.15, 0.28}, {0.22, 0.37}, {0.40, 0.55}, {0.55, 0.80}}),
        X_CLASS("X-Class 10–13″", new double[][]{{0.12, 0.20}, {0.14, 0.30}, {0.40, 0.55}, {0.55, 0.90}});

        static final double LOW_KV = 25000.0;
        static final double LOW_KV_BONUS = 1.25;
        static final double CINEWHOOP_35_MM = 80.0;
        static final double[] LOW_RATE_LONG_RANGE_MIXED = {0.30, 0.50};

        final String label;
        final double[][] wattsPerGram;

        Kind(String label, double[][] wattsPerGram) {
            this.label = label;
            this.wattsPerGram = wattsPerGram;
        }

        static Kind of(DroneBuild build) {
            AirframeSpec p = AirframeSpec.of(build);
            return switch (p.role) {
                case WHOOP -> p.propDiameter <= EnergyModel.WHOOP_SMALL_PROP_M ? WHOOP_65 : WHOOP_75;
                case TOOTHPICK -> TOOTHPICK;
                case CINEWHOOP, CINELIFTER -> p.propDiameter * 1000.0 > CINEWHOOP_35_MM ? CINEWHOOP_35 : CINEWHOOP;
                case FREESTYLE -> FREESTYLE;
                case RACE -> RACE;
                case LONG_RANGE -> LONG_RANGE;
                case X_CLASS -> X_CLASS;
            };
        }

        static double[] window(DroneBuild build, Style style) {
            Kind kind = of(build);
            AirframeSpec p = AirframeSpec.of(build);
            double[] w = kind.wattsPerGram[style.ordinal()].clone();
            boolean hard = style == Style.MIXED || style == Style.AGGRESSIVE;
            if (kind == WHOOP_65 && hard && build.motor().kv < LOW_KV) {
                w[0] /= LOW_KV_BONUS;
            }
            boolean lowRate = kind == LONG_RANGE && p.usableFraction >= EnergyModel.LOW_RATE_USABLE;
            if (lowRate && style == Style.MIXED) {
                w = LOW_RATE_LONG_RANGE_MIXED.clone();
            }
            if (lowRate && style == Style.AGGRESSIVE) {
                w[0] = kind.wattsPerGram[Style.MIXED.ordinal()][0];
            }
            return w;
        }
    }

    record Entry(String name, DroneBuild build) {
    }

    record Range(double low, double high) {
        boolean contains(double value) {
            return value >= this.low - 1e-9 && value <= this.high + 1e-9;
        }
    }

    private static List<Entry> addons;
    private static List<Entry> owner;

    private CalibrationHarness() {
    }

    static Identifier id(String namespace, String text) {
        return text.indexOf(':') < 0 ? Identifier.fromNamespaceAndPath(namespace, text) : Identifier.parse(text);
    }

    static List<Entry> builtins() {
        List<Entry> out = new ArrayList<>();
        for (DroneBuild build : DroneBuild.PRESETS) {
            out.add(new Entry("propwash:" + build.frame().key(), build));
        }
        return out;
    }

    static Optional<Path> addonRoot() {
        String root = System.getenv(ADDON_ENV);
        if (root == null || root.isBlank()) {
            return Optional.empty();
        }
        Path path = Path.of(root);
        return Files.isDirectory(path.resolve("data/justmoreparts/propwash_parts")) ? Optional.of(path) : Optional.empty();
    }

    static synchronized List<Entry> addons() {
        if (addons != null) {
            return addons;
        }
        List<Entry> out = new ArrayList<>();
        Optional<Path> root = addonRoot();
        if (root.isPresent()) {
            Path base = root.get();
            TreeMap<String, byte[]> files = new TreeMap<>();
            try (Stream<Path> walk = Files.walk(base.resolve("data/justmoreparts/propwash_parts"))) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    files.put(base.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            BundledParts.load("justmoreparts", files, true);
            for (String[] e : ADDON_PRESETS) {
                out.add(new Entry("justmoreparts:" + e[0], build("justmoreparts", e[1], e[2], e[3], e[4], e[5], e[6])));
            }
        }
        addons = List.copyOf(out);
        return addons;
    }

    static DroneBuild build(String namespace, String frame, String stack, String motor, String prop, String video, String battery) {
        return new DroneBuild(FrameType.byId(id(namespace, frame)).orElseThrow(), StackType.byId(id(namespace, stack)).orElseThrow(),
                MotorType.byId(id(namespace, motor)).orElseThrow(), PropType.byId(id(namespace, prop)).orElseThrow(),
                VideoType.byId(id(namespace, video)).orElseThrow(), BatteryType.byId(id(namespace, battery)).orElseThrow());
    }

    @SuppressWarnings("unchecked")
    static <T> T fixture(PartKind kind, String path, String json) {
        PartDataProblems problems = new PartDataProblems(path);
        PartDefinition definition = PartJson.decode(kind, Identifier.fromNamespaceAndPath(FIXTURE, path), PartJson.parse(json, problems), problems);
        if (definition == null) {
            throw new IllegalStateException(problems.errorLines().toString());
        }
        return (T) PartIndex.detached(definition);
    }

    static synchronized List<Entry> owner() {
        if (owner != null) {
            return owner;
        }
        MotorType m27 = fixture(PartKind.MOTOR, "m0702_27000", MOTOR_27000);
        MotorType m23 = fixture(PartKind.MOTOR, "m0702_23000", MOTOR_23000);
        StackType aio = fixture(PartKind.STACK, "aio_whoop_1s_6a", STACK_WHOOP);
        VideoType digital = fixture(PartKind.VIDEO, "digital_micro", VIDEO_DIGITAL_MICRO);
        VideoType analog = fixture(PartKind.VIDEO, "analog_whoop_25mw", VIDEO_ANALOG_WHOOP);
        PropType twoBlade = fixture(PartKind.PROP, "p31_16_2b_pc", PROP_2B);
        FrameType frame = FrameType.WHOOP_65;
        BatteryType battery = BatteryType.B1S_300_HV;
        owner = List.of(
                new Entry("owner", new DroneBuild(frame, aio, m27, PropType.P31_4B, digital, battery)),
                new Entry("owner 23000 KV", new DroneBuild(frame, aio, m23, PropType.P31_4B, digital, battery)),
                new Entry("owner 3-Blatt", new DroneBuild(frame, aio, m27, PropType.P31_3B, digital, battery)),
                new Entry("owner 2-Blatt", new DroneBuild(frame, aio, m27, twoBlade, digital, battery)),
                new Entry("owner analog", new DroneBuild(frame, aio, m27, PropType.P31_4B, analog, battery)));
        return owner;
    }

    static double usableWattHours(AirframeSpec p) {
        return p.usableFraction * p.capacityMah / 1000.0 * p.cells * p.chemistry.definition().nominalCellVoltage();
    }

    static Range reference(DroneBuild build, Style style) {
        AirframeSpec p = AirframeSpec.of(build);
        double[] wpg = Kind.window(build, style);
        double wh = usableWattHours(p);
        double grams = p.massKg * 1000.0;
        return new Range(wh * 60.0 / (wpg[1] * grams), wh * 60.0 / (wpg[0] * grams));
    }

    static double minutes(BuildAnalysis a, Style style) {
        return switch (style) {
            case HOVER -> a.hoverFlightTimeMin();
            case CRUISE -> a.cruiseFlightTimeMin();
            case MIXED -> a.mixedFlightTimeMin();
            case AGGRESSIVE -> a.aggressiveFlightTimeMin();
        };
    }

    static String clock(double minutes) {
        if (!Double.isFinite(minutes)) {
            return "–";
        }
        long seconds = Math.round(minutes * 60.0);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    static double wattsPerGram(DroneBuild build, double minutes) {
        AirframeSpec p = AirframeSpec.of(build);
        return usableWattHours(p) * 60.0 / (minutes * p.massKg * 1000.0);
    }

    static List<String> violations(Entry entry) {
        List<String> out = new ArrayList<>();
        BuildAnalysis a = BuildAnalysis.of(entry.build());
        for (Style style : Style.values()) {
            Range r = reference(entry.build(), style);
            double value = minutes(a, style);
            if (!r.contains(value)) {
                out.add(String.format(Locale.ROOT, "%s %s %s außerhalb %s–%s", entry.name(), style.label, clock(value), clock(r.low()),
                        clock(r.high())));
            }
        }
        return out;
    }

    static String report(List<Entry> entries) {
        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT, "%-30s %-20s %7s %5s %5s %5s | %-22s %-22s %-22s %-22s | %6s %6s %6s %5s %5s %6s %5s %5s %5s%n",
                "Aufbau", "Klasse", "g", "S/G", "Gas%", "W/g", "Schweben  W/g", "Reisen    W/g", "Gemischt  W/g", "Aggressiv W/g", "I_s", "I_gem", "I_voll",
                "U_Land", "P_Av", "km/h", "T60", "TFlug", "TDauer"));
        for (Entry e : entries) {
            AirframeSpec p = AirframeSpec.of(e.build());
            BuildAnalysis a = BuildAnalysis.of(e.build());
            StringBuilder times = new StringBuilder();
            for (Style style : Style.values()) {
                Range r = reference(e.build(), style);
                double value = minutes(a, style);
                String mark = r.contains(value) ? " " : "!";
                times.append(String.format(Locale.ROOT, "%-17s %4.2f ", clock(value) + mark + "[" + clock(r.low()) + "–" + clock(r.high()) + "]",
                        wattsPerGram(e.build(), value)));
            }
            OperatingPoints.Point hover = new OperatingPoints.Point();
            OperatingPoints.hover(p, 1.0, true, 0.0, true, hover);
            double wpg = EnergyModel.packCurrent(p, hover) * hover.busVoltage / a.massGrams();
            s.append(String.format(Locale.ROOT, "%-30s %-20s %7.1f %5.1f %5.1f %5.3f | %s| %6.2f %6.2f %6.1f %5.2f %5.1f %6.0f %5.0f %5.0f %5.0f%n",
                    e.name(), Kind.of(e.build()).label, a.massGrams(), a.thrustToWeight(), a.hoverThrottlePercent(), wpg, times,
                    a.hoverCurrentA(), a.mixedCurrentA(), a.fullThrottleCurrentA(), a.landingCellVoltage(), a.avionicsPowerW(),
                    a.topSpeedAcroKmh(), a.motorTempMinuteC(), a.mixedMotorTempC(), a.sustainedMotorTempC()));
        }
        return s.toString();
    }

    static void write(String name, String text) {
        String out = System.getenv(OUT_ENV);
        if (out == null || out.isBlank()) {
            return;
        }
        try {
            Path dir = Path.of(out);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
