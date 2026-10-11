# Überlebensmodus – Tabellen

Erzeugt von `tools/data/survival.py` – nicht von Hand bearbeiten. Begründung und Stufen: `docs/design/survival.md`.
`pw:` = `propwash:`, Vanilla ohne Namensraum, `#` = Item-Tag.

## Rezepte

| Ergebnis | Menge | Muster | Zutaten | Freischaltung durch |
|---|---|---|---|---|
| pw:carbon_plate | 2 | `CSC` / `CSC` | C = #coals, S = string | #coals, string, jedes Propwash-Item |
| pw:copper_coil | 2 | `·C·` / `CNC` / `·C·` | C = copper_ingot, N = iron_nugget | copper_ingot, iron_nugget, jedes Propwash-Item |
| pw:circuit_board | 2 | `NRN` / `GGG` | N = copper_nugget, R = redstone, G = glass_pane | copper_nugget, redstone, glass_pane, jedes Propwash-Item |
| pw:lipo_cell | 2 | `KNK` / `RLR` | K = dried_kelp, N = copper_nugget, R = redstone, L = lapis_lazuli | dried_kelp, copper_nugget, redstone, lapis_lazuli, jedes Propwash-Item |
| pw:hd_chip | 1 | `QAQ` / `GBG` | Q = quartz, A = amethyst_shard, G = gold_nugget, B = pw:circuit_board | quartz, amethyst_shard, gold_nugget, pw:circuit_board, jedes Propwash-Item |
| pw:whoop_65 | 1 | `K·K` / `·N·` / `K·K` | K = dried_kelp, N = iron_nugget | dried_kelp, iron_nugget, jedes Propwash-Item |
| pw:cinewhoop_3 | 1 | `K·K` / `CIC` / `K·K` | K = dried_kelp, C = pw:carbon_plate, I = iron_ingot | dried_kelp, pw:carbon_plate, iron_ingot, jedes Propwash-Item |
| pw:freestyle_5 | 1 | `C·C` / `CIC` / `C·C` | C = pw:carbon_plate, I = iron_ingot | pw:carbon_plate, iron_ingot, jedes Propwash-Item |
| pw:race_5 | 1 | `C·C` / `GIG` / `C·C` | C = pw:carbon_plate, G = gold_nugget, I = iron_ingot | pw:carbon_plate, gold_nugget, iron_ingot, jedes Propwash-Item |
| pw:long_range_7 | 1 | `CIC` / `C·C` / `CIC` | C = pw:carbon_plate, I = iron_ingot | pw:carbon_plate, iron_ingot, jedes Propwash-Item |
| pw:aio_whoop | 1 | formlos | pw:circuit_board, redstone, copper_nugget | pw:circuit_board, redstone, copper_nugget, jedes Propwash-Item |
| pw:aio_20 | 1 | `RBR` / `CBC` | R = redstone, B = pw:circuit_board, C = copper_ingot | redstone, pw:circuit_board, copper_ingot, jedes Propwash-Item |
| pw:stack_30 | 1 | `BRB` / `CGC` / `BRB` | B = pw:circuit_board, R = redstone, C = copper_ingot, G = gold_ingot | pw:circuit_board, redstone, copper_ingot, gold_ingot, jedes Propwash-Item |
| pw:m0802_22000 | 2 | `NLN` | N = iron_nugget, L = pw:copper_coil | iron_nugget, pw:copper_coil, jedes Propwash-Item |
| pw:m1404_4600 | 2 | `LNL` / `NRN` | L = pw:copper_coil, N = iron_nugget, R = redstone | pw:copper_coil, iron_nugget, redstone, jedes Propwash-Item |
| pw:m2004_3000 | 2 | `NLN` / `LRL` | N = iron_nugget, L = pw:copper_coil, R = redstone | iron_nugget, pw:copper_coil, redstone, jedes Propwash-Item |
| pw:m2207_1750 | 2 | `ILI` / `LRL` | I = iron_ingot, L = pw:copper_coil, R = redstone | iron_ingot, pw:copper_coil, redstone, jedes Propwash-Item |
| pw:m2306_1860 | 2 | `ILI` / `LQL` | I = iron_ingot, L = pw:copper_coil, Q = quartz | iron_ingot, pw:copper_coil, quartz, jedes Propwash-Item |
| pw:m2807_1300 | 2 | `ILI` / `LRL` / `ILI` | I = iron_ingot, L = pw:copper_coil, R = redstone | iron_ingot, pw:copper_coil, redstone, jedes Propwash-Item |
| pw:p31_4b | 4 | `·K·` / `KNK` / `·K·` | K = dried_kelp, N = iron_nugget | dried_kelp, iron_nugget, jedes Propwash-Item |
| pw:p31_3b | 4 | `K·K` / `·N·` | K = dried_kelp, N = iron_nugget | dried_kelp, iron_nugget, jedes Propwash-Item |
| pw:p3_3b | 4 | `·K·` / `KNK` | K = dried_kelp, N = iron_nugget | dried_kelp, iron_nugget, jedes Propwash-Item |
| pw:p51_3b | 4 | `K·K` / `·N·` / `·K·` | K = dried_kelp, N = iron_nugget | dried_kelp, iron_nugget, jedes Propwash-Item |
| pw:p7_2b | 4 | `K··` / `·C·` / `··K` | K = dried_kelp, C = pw:carbon_plate | dried_kelp, pw:carbon_plate, jedes Propwash-Item |
| pw:b1s_300_hv | 1 | formlos | pw:lipo_cell, redstone, copper_nugget | pw:lipo_cell, redstone, copper_nugget, jedes Propwash-Item |
| pw:b4s_650 | 1 | `LL` / `LL` / `NR` | L = pw:lipo_cell, N = copper_nugget, R = redstone | pw:lipo_cell, copper_nugget, redstone, jedes Propwash-Item |
| pw:b6s_1100 | 1 | `LLL` / `LLL` / `NGN` | L = pw:lipo_cell, N = copper_nugget, G = gold_ingot | pw:lipo_cell, copper_nugget, gold_ingot, jedes Propwash-Item |
| pw:b6s_1300 | 1 | `LLL` / `LLL` / `GSG` | L = pw:lipo_cell, G = gold_ingot, S = glowstone_dust | pw:lipo_cell, gold_ingot, glowstone_dust, jedes Propwash-Item |
| pw:b6s_4000_lion | 1 | `LAL` / `LDL` / `LAL` | L = pw:lipo_cell, A = amethyst_shard, D = diamond | pw:lipo_cell, amethyst_shard, diamond, jedes Propwash-Item |
| pw:analog_nano | 1 | formlos | pw:circuit_board, glass_pane, copper_nugget | pw:circuit_board, glass_pane, copper_nugget, jedes Propwash-Item |
| pw:analog_race | 1 | `GA` / `BL` | G = glass_pane, A = lightning_rod, B = pw:circuit_board, L = pw:copper_coil | glass_pane, lightning_rod, pw:circuit_board, pw:copper_coil, jedes Propwash-Item |
| pw:digital_lite | 1 | `TA` / `HB` | T = tinted_glass, A = lightning_rod, H = pw:hd_chip, B = pw:circuit_board | tinted_glass, lightning_rod, pw:hd_chip, pw:circuit_board, jedes Propwash-Item |
| pw:digital_hd | 1 | `TAT` / `GHG` / `·E·` | T = tinted_glass, A = lightning_rod, G = gold_ingot, H = pw:hd_chip, E = echo_shard | tinted_glass, lightning_rod, gold_ingot, pw:hd_chip, echo_shard, jedes Propwash-Item |
| pw:led_strip | 1 | `TTT` / `NBN` | T = redstone_torch, N = copper_nugget, B = pw:circuit_board | redstone_torch, copper_nugget, pw:circuit_board, jedes Propwash-Item |
| pw:action_cam_light | 1 | `CGC` / `BHB` | C = copper_ingot, G = glass_pane, B = pw:circuit_board, H = pw:hd_chip | copper_ingot, glass_pane, pw:circuit_board, pw:hd_chip, jedes Propwash-Item |
| pw:action_cam_heavy | 1 | `ITI` / `LHL` / `IBI` | I = iron_ingot, T = tinted_glass, L = pw:lipo_cell, H = pw:hd_chip, B = pw:circuit_board | iron_ingot, tinted_glass, pw:lipo_cell, pw:hd_chip, pw:circuit_board, jedes Propwash-Item |
| pw:action_cam_flashback | 1 | `·K·` / `GHG` / `CBC` | K = clock, G = glass_pane, H = pw:hd_chip, C = copper_ingot, B = pw:circuit_board | clock, glass_pane, pw:hd_chip, copper_ingot, pw:circuit_board, jedes Propwash-Item |
| pw:gripper | 1 | `·C·` / `NBN` / `H·H` | C = pw:copper_coil, N = iron_nugget, B = pw:circuit_board, H = tripwire_hook | pw:copper_coil, iron_nugget, pw:circuit_board, tripwire_hook, jedes Propwash-Item |
| pw:goggles_analog | 1 | `LIL` / `GBG` | L = leather, I = iron_ingot, G = glass_pane, B = pw:circuit_board | leather, iron_ingot, glass_pane, pw:circuit_board, jedes Propwash-Item |
| pw:goggles_digital | 1 | `LHL` / `TGT` | L = leather, H = pw:hd_chip, T = tinted_glass, G = gold_ingot | leather, pw:hd_chip, tinted_glass, gold_ingot, jedes Propwash-Item |
| pw:remote | 1 | `·T·` / `LGL` / `IBI` | T = redstone_torch, L = lever, G = glass_pane, I = iron_ingot, B = pw:circuit_board | redstone_torch, lever, glass_pane, iron_ingot, pw:circuit_board, jedes Propwash-Item |
| pw:drone_workbench | 1 | `IRI` / `PTP` / `P·P` | I = iron_ingot, R = redstone, P = #planks, T = crafting_table | iron_ingot, redstone, #planks, crafting_table, jedes Propwash-Item |
| pw:battery_charger | 1 | `IGI` / `CBC` / `SSS` | I = iron_ingot, G = glass_pane, C = copper_ingot, B = pw:circuit_board, S = smooth_stone_slab | iron_ingot, glass_pane, copper_ingot, pw:circuit_board, smooth_stone_slab, jedes Propwash-Item |
| pw:gate_small | 1 | `WTW` / `S·S` / `S·S` | W = #wool, T = redstone_torch, S = stick | #wool, redstone_torch, stick, jedes Propwash-Item |
| pw:gate_medium | 1 | `WGW` / `B·B` / `B·B` | W = #wool, G = glowstone_dust, B = iron_bars | #wool, glowstone_dust, iron_bars, jedes Propwash-Item |
| pw:gate_large | 1 | `GWG` / `I·I` / `I·I` | W = #wool, G = glowstone_dust, I = iron_ingot | #wool, glowstone_dust, iron_ingot, jedes Propwash-Item |
| pw:gate_pole | 2 | `G` / `B` / `B` | G = glowstone_dust, B = iron_bars | glowstone_dust, iron_bars, jedes Propwash-Item |
| pw:race_flag | 1 | `SW` / `SW` / `N·` | S = stick, W = #wool, N = iron_nugget | stick, #wool, iron_nugget, jedes Propwash-Item |
| pw:slalom_pole | 2 | `D` / `S` / `N` | D = red_dye, S = stick, N = iron_nugget | red_dye, stick, iron_nugget, jedes Propwash-Item |
| pw:dive_cube | 1 | `BGB` / `B·B` / `BBB` | B = iron_bars, G = glowstone_dust | iron_bars, glowstone_dust, jedes Propwash-Item |
| pw:freeform_gate | 1 | `GRG` / `R·R` / `GRG` | G = glowstone_dust, R = redstone | glowstone_dust, redstone, jedes Propwash-Item |
| pw:course_tool | 1 | `R` / `P` / `S` | R = redstone, P = paper, S = stick | redstone, paper, stick, jedes Propwash-Item |
| pw:signal_repeater | 2 | `·A·` / `LBL` / `·I·` | A = lightning_rod, L = pw:copper_coil, B = pw:circuit_board, I = iron_ingot | lightning_rod, pw:copper_coil, pw:circuit_board, iron_ingot, jedes Propwash-Item |

## Sonderrezepte

Berechnet im Code, ohne Eintrag im Rezeptbuch und ohne Freischaltung.

| Rezept | Typ | Klasse | Wirkung |
|---|---|---|---|
| pw:special/prop_repair | pw:prop_repair | `content.PropRepairRecipe` | zwei beschädigte Props derselben Sorte im 2×2- oder 3×3-Raster → ein Prop, Zustand addiert + 5 %, höchstens 100 %, Komponenten vom ersten; dasselbe am Schleifstein (`mixin.content.GrindstoneMenuMixin`) |

## Truhen-Beute

Je Truhe wird genau einmal aus der Propwash-Tabelle gewürfelt; „Chance“ = Wahrscheinlichkeit pro Truhe.

### `pw:inject/village_house` – Fundchance 17 %

Truhen: `chests/village/village_plains_house`, `chests/village/village_desert_house`, `chests/village/village_savanna_house`, `chests/village/village_snowy_house`, `chests/village/village_taiga_house`

| Item | Anzahl | Chance |
|---|---|---|
| pw:p31_4b | 2–4 | 3.9 % |
| pw:p31_3b | 2–4 | 2.0 % |
| pw:copper_coil | 1–2 | 2.9 % |
| pw:circuit_board | 1 | 2.9 % |
| pw:lipo_cell | 1–2 | 2.0 % |
| pw:carbon_plate | 1–2 | 2.0 % |
| pw:m0802_22000 | 2 | 1.0 % |

### `pw:inject/village_toolsmith` – Fundchance 51 %

Truhen: `chests/village/village_toolsmith`

| Item | Anzahl | Chance |
|---|---|---|
| pw:copper_coil | 1–3 | 9.7 % |
| pw:circuit_board | 1–2 | 7.8 % |
| pw:carbon_plate | 1–3 | 6.8 % |
| pw:lipo_cell | 1–3 | 5.8 % |
| pw:m0802_22000 | 2–4 | 4.9 % |
| pw:m1404_4600 | 2 | 3.9 % |
| pw:aio_whoop | 1 | 2.9 % |
| pw:whoop_65 | 1 | 2.9 % |
| pw:analog_nano | 1 | 2.9 % |
| pw:led_strip | 1 | 2.9 % |
| pw:battery_charger | 1 | 1.0 % |

### `pw:inject/village_cartographer` – Fundchance 42 %

Truhen: `chests/village/village_cartographer`

| Item | Anzahl | Chance |
|---|---|---|
| pw:analog_nano | 1 | 11.7 % |
| pw:circuit_board | 1–2 | 9.7 % |
| pw:carbon_plate | 1–2 | 7.8 % |
| pw:analog_race | 1 | 5.8 % |
| pw:goggles_analog | 1 | 3.9 % |
| pw:action_cam_light | 1 | 2.9 % |

### `pw:inject/abandoned_mineshaft` – Fundchance 30 %

Truhen: `chests/abandoned_mineshaft`

| Item | Anzahl | Chance |
|---|---|---|
| pw:copper_coil | 1–3 | 7.0 % |
| pw:lipo_cell | 1–2 | 5.2 % |
| pw:circuit_board | 1–2 | 4.3 % |
| pw:m1404_4600 | 2 | 2.6 % |
| pw:m2004_3000 | 2 | 1.7 % |
| pw:m2207_1750 | 2 | 1.7 % |
| pw:b4s_650 | 1 | 1.7 % |
| pw:p3_3b | 4 | 1.7 % |
| pw:whoop_65 | 1 | 1.7 % |
| pw:led_strip | 1 | 2.6 % |

### `pw:inject/simple_dungeon` – Fundchance 41 %

Truhen: `chests/simple_dungeon`

| Item | Anzahl | Chance |
|---|---|---|
| pw:circuit_board | 1–2 | 7.8 % |
| pw:lipo_cell | 1–2 | 7.8 % |
| pw:copper_coil | 1–2 | 5.9 % |
| pw:b1s_300_hv | 1 | 3.9 % |
| pw:b4s_650 | 1 | 2.9 % |
| pw:analog_race | 1 | 2.9 % |
| pw:m2207_1750 | 2 | 2.9 % |
| pw:drone (fertige Whoop-Drohne) | 1 | 2.0 % |
| pw:goggles_analog | 1 | 2.0 % |
| pw:led_strip | 1 | 2.0 % |
| pw:remote | 1 | 1.0 % |

### `pw:inject/temple` – Fundchance 45 %

Truhen: `chests/desert_pyramid`, `chests/jungle_temple`

| Item | Anzahl | Chance |
|---|---|---|
| pw:circuit_board | 2–3 | 8.0 % |
| pw:carbon_plate | 2–4 | 8.0 % |
| pw:aio_20 | 1 | 5.0 % |
| pw:m2207_1750 | 2 | 5.0 % |
| pw:p51_3b | 4 | 5.0 % |
| pw:analog_race | 1 | 4.0 % |
| pw:stack_30 | 1 | 3.0 % |
| pw:b6s_1100 | 1 | 3.0 % |
| pw:hd_chip | 1 | 1.0 % |
| pw:digital_lite | 1 | 1.0 % |
| pw:action_cam_light | 1 | 2.0 % |

### `pw:inject/pillager_outpost` – Fundchance 36 %

Truhen: `chests/pillager_outpost`

| Item | Anzahl | Chance |
|---|---|---|
| pw:carbon_plate | 1–3 | 7.8 % |
| pw:p51_3b | 4 | 4.9 % |
| pw:m2207_1750 | 2 | 4.9 % |
| pw:analog_race | 1 | 4.9 % |
| pw:freestyle_5 | 1 | 2.9 % |
| pw:remote | 1 | 2.9 % |
| pw:goggles_analog | 1 | 2.9 % |
| pw:drone (fertige Whoop-Drohne) | 1 | 2.9 % |
| pw:action_cam_light | 1 | 2.0 % |

### `pw:inject/stronghold` – Fundchance 46 %

Truhen: `chests/stronghold_corridor`, `chests/stronghold_crossing`

| Item | Anzahl | Chance |
|---|---|---|
| pw:circuit_board | 2–4 | 8.8 % |
| pw:lipo_cell | 2–4 | 7.8 % |
| pw:stack_30 | 1 | 3.9 % |
| pw:b6s_1100 | 1 | 3.9 % |
| pw:hd_chip | 1 | 2.9 % |
| pw:digital_lite | 1 | 2.9 % |
| pw:m2807_1300 | 2 | 2.9 % |
| pw:p7_2b | 4 | 2.9 % |
| pw:m2306_1860 | 2 | 2.0 % |
| pw:b6s_1300 | 1 | 2.0 % |
| pw:race_5 | 1 | 2.0 % |
| pw:long_range_7 | 1 | 2.0 % |
| pw:action_cam_heavy | 1 | 2.0 % |

### `pw:inject/shipwreck_supply` – Fundchance 30 %

Truhen: `chests/shipwreck_supply`

| Item | Anzahl | Chance |
|---|---|---|
| pw:lipo_cell | 1–3 | 8.0 % |
| pw:p3_3b | 4 | 6.0 % |
| pw:carbon_plate | 1–2 | 6.0 % |
| pw:p51_3b | 4 | 5.0 % |
| pw:b4s_650 | 1 | 3.0 % |
| pw:cinewhoop_3 | 1 | 2.0 % |

### `pw:inject/trial_chambers` – Fundchance 31 %

Truhen: `chests/trial_chambers/supply`, `chests/trial_chambers/intersection`

| Item | Anzahl | Chance |
|---|---|---|
| pw:copper_coil | 2–4 | 7.8 % |
| pw:circuit_board | 1–3 | 5.9 % |
| pw:lipo_cell | 1–3 | 5.9 % |
| pw:m2207_1750 | 2 | 3.9 % |
| pw:aio_20 | 1 | 2.9 % |
| pw:analog_race | 1 | 2.9 % |
| pw:led_strip | 1 | 2.0 % |

### `pw:inject/vault` – Fundchance 26 %

Truhen: `chests/trial_chambers/reward`

| Item | Anzahl | Chance |
|---|---|---|
| pw:hd_chip | 1 | 4.9 % |
| pw:digital_lite | 1 | 4.9 % |
| pw:m2306_1860 | 2 | 4.9 % |
| pw:stack_30 | 1 | 3.9 % |
| pw:b6s_1300 | 1 | 3.9 % |
| pw:drone (fertige Whoop-Drohne) | 1 | 2.0 % |
| pw:action_cam_heavy | 1 | 2.0 % |

### `pw:inject/vault_ominous` – Fundchance 52 %

Truhen: `chests/trial_chambers/reward_ominous`

| Item | Anzahl | Chance |
|---|---|---|
| pw:digital_hd | 1 | 7.7 % |
| pw:m2306_1860 | 2 | 7.7 % |
| pw:b6s_1300 | 1 | 5.8 % |
| pw:race_5 | 1 | 5.8 % |
| pw:hd_chip | 2 | 5.8 % |
| pw:b6s_4000_lion | 1 | 4.8 % |
| pw:goggles_digital | 1 | 4.8 % |
| pw:drone (fertige Whoop-Drohne) | 1 | 5.8 % |
| pw:action_cam_heavy | 1 | 3.8 % |

### `pw:inject/ancient_city` – Fundchance 56 %

Truhen: `chests/ancient_city`

| Item | Anzahl | Chance |
|---|---|---|
| pw:hd_chip | 1–2 | 7.8 % |
| pw:lipo_cell | 2–4 | 7.8 % |
| pw:digital_lite | 1 | 5.8 % |
| pw:stack_30 | 1 | 5.8 % |
| pw:m2306_1860 | 2 | 5.8 % |
| pw:digital_hd | 1 | 4.9 % |
| pw:b6s_1300 | 1 | 4.9 % |
| pw:b6s_4000_lion | 1 | 3.9 % |
| pw:race_5 | 1 | 3.9 % |
| pw:goggles_digital | 1 | 2.9 % |
| pw:action_cam_heavy | 1 | 2.9 % |

### `pw:inject/end_city` – Fundchance 52 %

Truhen: `chests/end_city_treasure`

| Item | Anzahl | Chance |
|---|---|---|
| pw:digital_hd | 1 | 7.7 % |
| pw:m2306_1860 | 2 | 7.7 % |
| pw:b6s_1300 | 1 | 5.8 % |
| pw:b6s_4000_lion | 1 | 5.8 % |
| pw:race_5 | 1 | 5.8 % |
| pw:goggles_digital | 1 | 5.8 % |
| pw:long_range_7 | 1 | 4.8 % |
| pw:m2807_1300 | 2 | 4.8 % |
| pw:action_cam_heavy | 1 | 3.8 % |
