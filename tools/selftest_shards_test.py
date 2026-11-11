import gzip
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import selftest_shards as shards

SMALL = """# Selbsttest-Schritte (Fabric, 12 Schritte)

| Präfix | in der Welt | nach dem Verlassen | angemeldet in |
|---|---:|---:|---|
| `dev:` | 1 | 0 | Test |

## Alle Schritte in Reihenfolge

### In der Welt

- dev: Basis *(läuft immer)*
- eins: a
- eins: b
- zwei: vorbereiten *(läuft mit jeder Auswahl aus `zwei:`)*
- zwei: x
- zwei: y
- drei: Deutsch *(läuft, wenn ein ausgewählter Schritt vor „drei: zurück“ folgt, samt diesem)*
- drei: z
- drei: zurück
- vier: w

### Nach dem Verlassen der Welt

- eins: nach dem Verlassen
- vier: nach dem Verlassen

"""

WIDE = """# Selbsttest-Schritte (Fabric, 16 Schritte)

## Alle Schritte in Reihenfolge

### In der Welt

- dev: Basis *(läuft immer)*
- a: 1
- bildschirmfotos: a-Bild
- a: 2
- b: 1
- bildschirmfotos (Hand-Items): vorbereiten *(läuft mit jeder Auswahl aus `bildschirmfotos (Hand-Items):`)*
- bildschirmfotos (Hand-Items): Bild
- b: 2
- c: 1
- c: 2
- bildschirmfotos: Szene vorbereiten *(läuft mit jeder Auswahl aus `bildschirmfotos:`)*
- bildschirmfotos: Rundgang 1
- bildschirmfotos: Rundgang 2

### Nach dem Verlassen der Welt

- a: nach dem Verlassen
- bildschirmfotos: Fenster für die Wiederholung
- c: Wiederholung

"""

SPREAD = """# Selbsttest-Schritte (Fabric, 7 Schritte)

## Alle Schritte in Reihenfolge

### In der Welt

- dev: Basis *(läuft immer)*
- x: 1
- a: 1
- b: 1
- c: 1
- a: Nachtrag
- a: Nachtrag aufräumen

"""


def fabric_log(path, entries, end=None):
    lines = []
    for i, (t, name) in enumerate(entries):
        lines.append(f"[{t}] [Render thread/INFO] (Propwash FPV) [Selbsttest] Schritt {i + 1}/{len(entries)}: {name}")
    if end:
        lines.append(f"[{end}] [Render thread/INFO] (Propwash FPV) PROPWASH SELFTEST PASS ({len(entries)} Schritte, Fabric)")
    text = "\n".join(lines) + "\n"
    if str(path).endswith(".gz"):
        with gzip.open(path, "wt", encoding="utf-8") as f:
            f.write(text)
    else:
        Path(path).write_text(text, encoding="utf-8")


def clock(seconds):
    return f"{seconds // 3600 % 24:02d}:{seconds // 60 % 60:02d}:{seconds % 60:02d}"


def names(text, steps):
    return shards.select(steps, text)


class ParseTest(unittest.TestCase):
    def test_markers_positions_and_phases(self):
        platform, steps = shards.parse_list(SMALL)
        self.assertEqual("Fabric", platform)
        self.assertEqual(12, len(steps))
        self.assertEqual(("dev: Basis", "base"), (steps[0].name, steps[0].kind))
        self.assertEqual(("zwei: vorbereiten", "setup"), (steps[3].name, steps[3].kind))
        self.assertEqual(("drei: Deutsch", "switch", "drei: zurück"), (steps[6].name, steps[6].kind, steps[6].until))
        self.assertEqual([11, 12], [s.pos for s in steps if s.phase == "after"])
        self.assertEqual({"drei: zurück"}, shards.until_names(steps))

    def test_count_mismatch_is_an_error(self):
        with self.assertRaises(ValueError):
            shards.parse_list(SMALL.replace("12 Schritte", "13 Schritte"))


class SelectTest(unittest.TestCase):
    def setUp(self):
        _, self.steps = shards.parse_list(SMALL)

    def test_same_results_as_the_java_selection(self):
        cases = {
            None: [s.name for s in self.steps],
            "#2-3": ["dev: Basis", "eins: a", "eins: b"],
            "# 3": ["dev: Basis", "eins: b"],
            "#3-2": ["dev: Basis", "eins: a", "eins: b"],
            "#6": ["dev: Basis", "zwei: vorbereiten", "zwei: y"],
            "#8": ["dev: Basis", "drei: Deutsch", "drei: z", "drei: zurück"],
            "#10-12": ["dev: Basis", "vier: w", "eins: nach dem Verlassen", "vier: nach dem Verlassen"],
            "vier:,#2": ["dev: Basis", "eins: a", "vier: w", "vier: nach dem Verlassen"],
            "#2-3|#11": ["dev: Basis", "eins: a", "eins: b", "eins: nach dem Verlassen"],
            "!#5-6": ["dev: Basis", "eins: a", "eins: b", "zwei: vorbereiten", "drei: Deutsch", "drei: z", "drei: zurück",
                      "vier: w", "eins: nach dem Verlassen", "vier: nach dem Verlassen"],
            "!eins: a,#2-3": ["dev: Basis", "eins: b"],
            "!eins:,#2": [],
            "!eins: a,!eins: b,!zwei:": ["dev: Basis", "drei: Deutsch", "drei: z", "drei: zurück", "vier: w",
                                         "eins: nach dem Verlassen", "vier: nach dem Verlassen"],
            "#99": [],
            "#0": [],
            "#zwei": [],
            "EINS: A": ["dev: Basis", "eins: a"],
        }
        for text, expected in cases.items():
            self.assertEqual(expected, names(text, self.steps), text)

    def test_coverage_allows_repeated_framing_and_switch_targets_only(self):
        selections = [names(t, self.steps) for t in ("#1-6", "#7-9,#11", "#10,#12")]
        self.assertEqual(([], []), shards.coverage(self.steps, selections))
        missing, twice = shards.coverage(self.steps, [names("#1-6", self.steps), names("#6-12", self.steps)])
        self.assertEqual([], missing)
        self.assertEqual(["zwei: y"], twice)
        missing, _ = shards.coverage(self.steps, [names("#1-6", self.steps)])
        self.assertIn("vier: w", missing)


class PlanTest(unittest.TestCase):
    def setUp(self):
        _, self.steps = shards.parse_list(WIDE)

    def test_cuts_only_where_no_group_crosses(self):
        world, cuts = shards.clean_cuts(self.steps)
        self.assertEqual(["b: 1", "c: 1", "bildschirmfotos: Szene vorbereiten"], [world[k].name for k in cuts])

    def test_after_leave_steps_follow_their_group_and_wrapped_neighbour(self):
        world, cuts = shards.clean_cuts(self.steps)
        segs = shards.segments_for(self.steps, world, cuts)
        by_name = [[self.steps[p - 1].name for p in seg] for seg in segs]
        self.assertIn("a: nach dem Verlassen", by_name[0])
        self.assertIn("c: Wiederholung", by_name[2])
        self.assertIn("bildschirmfotos: Fenster für die Wiederholung", by_name[2])
        self.assertEqual("bildschirmfotos: Szene vorbereiten", by_name[3][0])
        self.assertEqual(["bildschirmfotos: Szene vorbereiten", "bildschirmfotos: Rundgang 1", "bildschirmfotos: Rundgang 2"], by_name[3])

    def test_leading_setup_step_moves_to_the_next_shard(self):
        world, _ = shards.clean_cuts(self.steps)
        segs = shards.segments_for(self.steps, world, [world.index(next(s for s in world if s.name == "bildschirmfotos: Rundgang 1"))])
        first = [self.steps[p - 1].name for p in segs[1]]
        self.assertEqual("bildschirmfotos: Szene vorbereiten", first[0])

    def test_balanced_plan_from_durations(self):
        durations = {s.name: 1.0 for s in self.steps}
        durations.update({"c: 1": 900.0, "c: 2": 900.0})
        result = shards.plan(self.steps, 2, durations)
        self.assertEqual(["c: 1"], result["cuts"])
        self.assertEqual("#1-8,#14", result["shards"][0]["filter"])
        self.assertEqual("#9-13,#15-16", result["shards"][1]["filter"])
        missing, twice = shards.coverage(self.steps, [s["selected"] for s in result["shards"]])
        self.assertEqual(([], []), (missing, twice))

    def test_anchor_plan_and_unclean_anchor(self):
        result = shards.plan(self.steps, ["b:"], {})
        self.assertEqual(["b: 1"], result["cuts"])
        with self.assertRaises(ValueError):
            shards.plan(self.steps, ["a: 2"], {})
        forced = shards.plan(self.steps, ["a: 2"], {}, allow_unclean=True)
        self.assertEqual(["a: 2"], forced["cuts"])

    def test_too_many_shards(self):
        with self.assertRaises(ValueError):
            shards.plan(self.steps, 5, {})

    def test_unmeasured_steps_count_like_a_full_run_not_like_the_few_measured(self):
        weight, measured = shards.weights(self.steps, {"a: 1": 0.2, "a: 2": 0.3})
        self.assertEqual(2, measured)
        self.assertEqual(0.2, weight["a: 1"])
        self.assertEqual(4.5, weight["c: 1"])
        self.assertEqual(4.5, weight["bildschirmfotos: Rundgang 2"])

    def test_too_few_cuts_names_the_groups_that_prevent_them(self):
        _, steps = shards.parse_list(SPREAD.replace("- x: 1\n", "").replace("7 Schritte", "6 Schritte"))
        with self.assertRaises(ValueError) as caught:
            shards.plan(steps, 2, {})
        self.assertIn("Schnitte verhindern", str(caught.exception))
        self.assertIn("a #2–#6", str(caught.exception))

    def plan_output(self, text, durations):
        with tempfile.TemporaryDirectory() as tmp:
            list_file = Path(tmp, "steps.md")
            list_file.write_text(text, encoding="utf-8")
            log = Path(tmp, "latest.log")
            t = 36000
            entries = []
            _, steps = shards.parse_list(text)
            for s in steps:
                entries.append((clock(t), s.name))
                t += durations.get(s.name, 1)
            fabric_log(log, entries, end=clock(t))
            out, err = io.StringIO(), io.StringIO()
            with redirect_stdout(out):
                from contextlib import redirect_stderr
                with redirect_stderr(err):
                    rc = shards.main(["plan", "--list", str(list_file), "--shards", "2", "--timing", str(log),
                                      "--out", str(Path(tmp, "plan.json"))])
            plan = json.loads(Path(tmp, "plan.json").read_text(encoding="utf-8")) if rc == 0 else None
        return rc, out.getvalue() + err.getvalue(), plan

    def test_unbalanced_plan_names_the_groups_that_prevent_a_better_cut(self):
        rc, out, plan = self.plan_output(SPREAD, {"b: 1": 900, "c: 1": 900})
        self.assertEqual(0, rc, out)
        self.assertEqual(["a: 1"], plan["cuts"])
        self.assertIn("Schnitte verhindern", out)
        self.assertIn("a #3–#7", out)
        self.assertEqual([{"group": "a", "first": 3, "last": 7}], plan["blockers"])
        rc, out, plan = self.plan_output(WIDE, {"c: 1": 30, "a: 1": 30})
        self.assertEqual(0, rc, out)
        self.assertNotIn("Schnitte verhindern", out)
        self.assertEqual([], plan["blockers"])

    def test_plan_warns_when_a_part_would_run_into_the_limit_in_the_game(self):
        rc, out, plan = self.plan_output(WIDE, {"c: 1": 5000, "a: 1": 5000})
        self.assertEqual(0, rc, out)
        self.assertIn("über der Zeitgrenze im Spiel (80 min)", out)
        rc, out, _ = self.plan_output(WIDE, {"c: 1": 30, "a: 1": 30})
        self.assertNotIn("Zeitgrenze", out)


class TimingTest(unittest.TestCase):
    def test_both_log_formats_and_midnight(self):
        with tempfile.TemporaryDirectory() as tmp:
            fabric = Path(tmp, "fabric.log")
            fabric.write_text("\n".join([
                "[23:59:50] [Render thread/INFO] (Propwash FPV) [Selbsttest] Schritt 1/3: a: 1",
                "[23:59:58] [Render thread/INFO] (Propwash FPV) [Selbsttest] Schritt 2/3: a: 2",
                "[00:00:10] [Render thread/INFO] (Propwash FPV) [Selbsttest] Schritt 3/3: b: 1",
                "[00:00:13] [Render thread/INFO] (Propwash FPV) PROPWASH SELFTEST PASS (3 Schritte, Fabric)",
            ]), encoding="utf-8")
            neo = Path(tmp, "neo.log.gz")
            with gzip.open(neo, "wt", encoding="utf-8") as f:
                f.write("\n".join([
                    "[15Oct2026 18:56:28.608] [Render thread/INFO] [Propwash FPV/]: [Selbsttest] Schritt 1/2: c: 1",
                    "[15Oct2026 18:56:30.108] [Render thread/INFO] [Propwash FPV/]: [Selbsttest] Schritt 2/2: c: 2",
                    "[15Oct2026 18:56:31.108] [Render thread/INFO] [Propwash FPV/]: PROPWASH SELFTEST PASS (2 Schritte, NeoForge)",
                ]))
            d = shards.timings([fabric, neo])
        self.assertEqual({"a: 1": 8, "a: 2": 12, "b: 1": 3, "c: 1": 1.5, "c: 2": 1.0}, {k: round(v, 3) for k, v in d.items()})


class CheckTest(unittest.TestCase):
    START = 1_800_000_000

    def run_shard(self, root, tag, selected, total, list_text, filter_text, drop=None, old=()):
        rundir = Path(root, tag)
        (rundir / "logs").mkdir(parents=True)
        files = {"list": rundir / "propwash-selftest-steps.md", "result": rundir / "propwash-selftest-result.txt",
                 "log": rundir / "logs" / "latest.log"}
        files["list"].write_text(list_text, encoding="utf-8")
        ran = [n for n in selected if n != drop]
        files["result"].write_text(f"PASS gefiltert: {len(ran)} von {total} Schritten ({filter_text})\n", encoding="utf-8")
        lines = ["[10:00:00] [Render thread/INFO] (Propwash FPV) [Selbsttest] Selbsttest aktiv (Fabric)"]
        lines += [f"[10:00:01] [Render thread/INFO] (Propwash FPV) [Selbsttest] PASS {n} (3 Ticks)" for n in ran]
        files["log"].write_text("\n".join(lines) + "\n", encoding="utf-8")
        for name, f in files.items():
            t = self.START - 3600 if name in old else self.START + 60
            os.utime(f, (t, t))
        return f"{tag}={rundir}"

    def check(self, drop=None, exits=("0", "0"), started=True, old=()):
        _, steps = shards.parse_list(WIDE)
        result = shards.plan(steps, 2, {})
        with tempfile.TemporaryDirectory() as tmp:
            plan_file = Path(tmp, "plan.json")
            plan_file.write_text(json.dumps(result), encoding="utf-8")
            list_file = Path(tmp, "steps.md")
            list_file.write_text(WIDE, encoding="utf-8")
            args = []
            for i, s in enumerate(result["shards"]):
                args += ["--shard", self.run_shard(tmp, s["tag"], s["selected"], len(steps), WIDE, s["filter"],
                                                   drop if i == 1 else None, old if i == 1 else ())]
                if exits[i] is not None:
                    args += ["--exit", f"{s['tag']}={exits[i]}"]
                if started:
                    args += ["--started", f"{s['tag']}={self.START}"]
            out = io.StringIO()
            with redirect_stdout(out):
                rc = shards.main(["check", "--plan", str(plan_file), "--list", str(list_file)] + args)
        return rc, out.getvalue()

    def test_all_steps_once_passes(self):
        rc, out = self.check()
        self.assertEqual(0, rc, out)
        self.assertIn("PASS – alle 16 Schritte", out)

    def test_missing_step_fails(self):
        rc, out = self.check(drop="c: 2")
        self.assertEqual(1, rc)
        self.assertIn("FAIL", out)
        self.assertIn("c: 2", out)

    def test_nonzero_gradle_exit_fails_despite_pass_result(self):
        rc, out = self.check(exits=("0", "1"))
        self.assertEqual(1, rc, out)
        self.assertIn("shard-2: FAIL", out)
        self.assertIn("Gradle-Exit 1", out)

    def test_missing_exit_or_start_fails(self):
        rc, out = self.check(exits=("0", None))
        self.assertEqual(1, rc, out)
        self.assertIn("kein Gradle-Exit", out)
        rc, out = self.check(started=False)
        self.assertEqual(1, rc, out)
        self.assertIn("keine Startzeit", out)

    def test_result_older_than_the_start_fails(self):
        rc, out = self.check(old=("result",))
        self.assertEqual(1, rc, out)
        self.assertIn("Ergebnisdatei älter als der Start", out)

    def test_log_older_than_the_start_fails(self):
        rc, out = self.check(old=("log",))
        self.assertEqual(1, rc, out)
        self.assertIn("latest.log älter als der Start", out)

    def test_step_list_older_than_the_start_fails(self):
        rc, out = self.check(old=("list",))
        self.assertEqual(1, rc, out)
        self.assertIn("Schrittliste des Teils älter", out)

FAKE_GRADLEW = r"""#!/usr/bin/env python3
import json, os, sys, time
from pathlib import Path
sys.path.insert(0, os.environ["FAKE_TOOLS"])
import selftest_shards as sh
args = sys.argv[1:]
t0 = time.time()
task = next(a for a in args if a.startswith(":"))
tag = next(a.split("=", 1)[1] for a in args if a.startswith("-PpropwashRunTag="))
try:
    os.fstat(9)
    fd9 = True
except OSError:
    fd9 = False
with open(os.environ["FAKE_CALLS"], "a", encoding="utf-8") as f:
    f.write(json.dumps({"tag": tag, "begin": t0, "guarded": os.environ.get("MCSLOT_GUARDED_CLIENT"), "fd9": fd9}) + "\n")
if tag == os.environ.get("FAKE_FAIL_TAG"):
    sys.exit(1)
if tag == os.environ.get("FAKE_NOOP_TAG"):
    sys.exit(0)
rundir = Path(task.split(":")[1], "runs", "selftest-client-" + tag)
(rundir / "logs").mkdir(parents=True, exist_ok=True)
text = Path(os.environ["FAKE_LIST"]).read_text(encoding="utf-8")
(rundir / "propwash-selftest-steps.md").write_text(text, encoding="utf-8")
_, steps = sh.parse_list(text)
log = rundir / "logs" / "latest.log"
head = "[10:00:00] [Render thread/INFO] (Propwash FPV) [Selbsttest] "
if "-PpropwashSelftestList=true" in args:
    log.write_text(head + "Liste\n", encoding="utf-8")
    (rundir / "propwash-selftest-result.txt").write_text("PASS Liste: %d Schritte\n" % len(steps), encoding="utf-8")
    sys.exit(0)
flt = os.environ.get("PROPWASH_SELFTEST_ONLY", "")
lines = [head + "Selbsttest aktiv (Fabric)"]
log.write_text("\n".join(lines) + "\n", encoding="utf-8")
time.sleep(float(os.environ.get("FAKE_SECONDS", "1")))
ran = [n for n in sh.select(steps, flt) if n != os.environ.get("FAKE_DROP")]
for i, n in enumerate(ran):
    lines += [head + "Schritt %d/%d: %s" % (i + 1, len(ran), n), head + "PASS %s (2 Ticks)" % n]
log.write_text("\n".join(lines) + "\n", encoding="utf-8")
(rundir / "propwash-selftest-result.txt").write_text(
    "PASS gefiltert: %d von %d Schritten (%s)\n" % (len(ran), len(steps), flt), encoding="utf-8")
with open(os.environ["FAKE_CALLS"], "a", encoding="utf-8") as f:
    f.write(json.dumps({"tag": tag, "filter": flt, "start": t0, "end": time.time(), "nodaemon": "--no-daemon" in args}) + "\n")
"""


class ShardedScriptTest(unittest.TestCase):
    def run_script(self, root, *extra, drop=None, **fake):
        env = dict(os.environ, PROPWASH_GRADLEW=str(Path(root, "fake-gradlew")), PROPWASH_MC_LAUNCH="env",
                   FAKE_TOOLS=str(Path(root, "tools")), FAKE_LIST=str(Path(root, "list.md")),
                   FAKE_CALLS=str(Path(root, "calls.jsonl")), FAKE_SECONDS="4", PROPWASH_SHARD_LOGS=str(Path(root, "out")))
        for name in ("PROPWASH_SELFTEST_ONLY", "MCSLOT_GUARDED_CLIENT", "FAKE_FAIL_TAG", "FAKE_NOOP_TAG", "FAKE_DROP"):
            env.pop(name, None)
        if drop:
            env["FAKE_DROP"] = drop
        env.update({f"FAKE_{k.upper()}": v for k, v in fake.items()})
        Path(root, "calls.jsonl").write_text("", encoding="utf-8")
        done = subprocess.run(["bash", "tools/selftest-sharded.sh", "fabric", *extra], cwd=root, env=env,
                              capture_output=True, text=True, timeout=120)
        calls = [json.loads(line) for line in Path(root, "calls.jsonl").read_text(encoding="utf-8").splitlines()]
        return done, [c for c in calls if "end" in c], [c for c in calls if "begin" in c]

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        root = self.tmp.name
        subprocess.run(["git", "init", "-q", root], check=True)
        Path(root, "tools").mkdir()
        here = Path(__file__).resolve().parent
        for name in ("selftest-sharded.sh", "selftest_shards.py"):
            shutil.copy2(here / name, Path(root, "tools", name))
        Path(root, "list.md").write_text(WIDE, encoding="utf-8")
        fake = Path(root, "fake-gradlew")
        fake.write_text(FAKE_GRADLEW, encoding="utf-8")
        fake.chmod(0o755)

    def tearDown(self):
        self.tmp.cleanup()

    def test_shards_run_in_parallel_and_cover_every_step(self):
        done, calls, begins = self.run_script(self.tmp.name, "--shards", "c:")
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertIn("Selbsttest in Teilen (Fabric): PASS – alle 16 Schritte", done.stdout)
        shard_calls = sorted((c for c in calls if c["tag"] != "shard-list"), key=lambda c: c["tag"])
        self.assertEqual(["shard-1", "shard-2"], [c["tag"] for c in shard_calls])
        self.assertEqual(["#1-8,#14", "#9-13,#15-16"], [c["filter"] for c in shard_calls])
        self.assertTrue(all(c["nodaemon"] for c in shard_calls))
        first, second = sorted(shard_calls, key=lambda c: c["start"])
        self.assertLess(second["start"], first["end"])
        self.assertTrue(Path(self.tmp.name, "fabric", "runs", "selftest-client-shard-list", "propwash-selftest-steps.md").exists())
        by_tag = {c["tag"]: c for c in begins}
        self.assertEqual("1", by_tag["shard-1"]["guarded"])
        self.assertEqual("1", by_tag["shard-2"]["guarded"])
        self.assertIsNone(by_tag["shard-list"]["guarded"])
        self.assertFalse(any(c["fd9"] for c in begins), "Gradle darf den Deskriptor der Start-Sperre nicht erben")

    def run_twice(self, **fake):
        first, _, _ = self.run_script(self.tmp.name, "--shards", "c:")
        self.assertEqual(0, first.returncode, first.stdout + first.stderr)
        result = Path(self.tmp.name, "fabric", "runs", "selftest-client-shard-2", "propwash-selftest-result.txt")
        self.assertTrue(result.read_text(encoding="utf-8").startswith("PASS"))
        old = time.time() - 3600
        for f in Path(self.tmp.name, "fabric", "runs").rglob("*"):
            if f.is_file():
                os.utime(f, (old, old))
        second, _, _ = self.run_script(self.tmp.name, "--shards", "c:", **fake)
        return second

    def test_shard_that_failed_before_the_game_fails_despite_old_pass(self):
        done = self.run_twice(fail_tag="shard-2")
        self.assertEqual(1, done.returncode, done.stdout + done.stderr)
        self.assertIn("shard-2: FAIL", done.stdout)
        self.assertIn("Gradle-Exit 1", done.stdout)
        self.assertIn("keine Ergebnisdatei", done.stdout)
        self.assertIn("Selbsttest in Teilen: FAIL", done.stdout)

    def test_shard_without_new_result_fails_even_with_exit_0(self):
        done = self.run_twice(noop_tag="shard-1")
        self.assertEqual(1, done.returncode, done.stdout + done.stderr)
        self.assertIn("shard-1: FAIL", done.stdout)
        self.assertIn("keine Ergebnisdatei", done.stdout)
        self.assertIn("latest.log älter als der Start", done.stdout)
        self.assertNotIn("shard-2: FAIL", done.stdout)

    def test_missing_step_fails_the_run(self):
        done, _, _ = self.run_script(self.tmp.name, "--shards", "c:", drop="b: 2")
        self.assertEqual(1, done.returncode, done.stdout + done.stderr)
        self.assertIn("FAIL", done.stdout)
        self.assertIn("b: 2", done.stdout)

    def test_plan_measures_with_rotated_logs_of_earlier_shards(self):
        root = Path(self.tmp.name)
        base = root / "fabric" / "runs" / "selftest-client"
        (base / "logs").mkdir(parents=True)
        (base / "propwash-selftest-steps.md").write_text(WIDE, encoding="utf-8")
        old = root / "fabric" / "runs" / "selftest-client-shard-2" / "logs"
        old.mkdir(parents=True)
        _, steps = shards.parse_list(WIDE)
        fabric_log(old / "2026-10-19-1.log.gz", [(f"10:00:{2 * i:02d}", s.name) for i, s in enumerate(steps)], end="10:01:00")
        t = time.time() - 7200
        os.utime(old / "2026-10-19-1.log.gz", (t, t))
        for i in range(15):
            filtered = base / "logs" / f"2026-10-19-{i + 2}.log.gz"
            fabric_log(filtered, [])
            os.utime(filtered, (t + 60 * (i + 1), t + 60 * (i + 1)))
        env = dict(os.environ)
        env.pop("PROPWASH_MC_LAUNCH", None)
        done = subprocess.run(["bash", "tools/selftest-sharded.sh", "fabric", "--plan"], cwd=root, env=env,
                              capture_output=True, text=True, timeout=60)
        self.assertEqual(0, done.returncode, done.stdout + done.stderr)
        self.assertIn(f"Dauer von {len(steps)} Schritten gemessen", done.stdout)

    def test_launcher_is_required(self):
        env = dict(os.environ)
        env.pop("PROPWASH_MC_LAUNCH", None)
        done = subprocess.run(["bash", "tools/selftest-sharded.sh", "fabric"], cwd=self.tmp.name, env=env,
                              capture_output=True, text=True, timeout=30)
        self.assertEqual(2, done.returncode)
        self.assertIn("PROPWASH_MC_LAUNCH", done.stderr)



if __name__ == "__main__":
    unittest.main()
