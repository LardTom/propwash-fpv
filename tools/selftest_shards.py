import argparse
import gzip
import json
import re
import sys
from datetime import datetime
from itertools import combinations
from pathlib import Path

HELP = """Teilt den Selbsttest in zusammenhängende Teile und prüft hinterher, ob jeder Schritt genau einmal lief.

  python3 tools/selftest_shards.py plan --list <propwash-selftest-steps.md> [--shards <n|Anker,Anker>]
                                        [--timing <log> …] [--out <plan.json>] [--allow-unclean]
  python3 tools/selftest_shards.py check --plan <plan.json> --list <steps.md> --shard <kennung>=<run-verzeichnis> …
                                         --exit <kennung>=<gradle-exit> … --started <kennung>=<startzeit, epoch s> …

Teile sind Bereiche der Schrittliste (Filter-Token #von-bis, docs/testing.md). Geschnitten wird nur an sauberen
Stellen: keine Gruppe (Text vor dem ersten Doppelpunkt) hat Schritte vor und hinter dem Schnitt. Bildschirmfoto-
Schritte (Gruppen bildschirmfotos…) gehören zur Gruppe davor; eine Folge von ihnen, die mit einem eigenen
Vorbereitungs-Schritt beginnt (Rundgang, Hand-Items, …), gilt als eigene Gruppe. Schritte nach dem Verlassen der Welt
laufen in dem Teil, der die Schritte ihrer Gruppe in der Welt hat; Bildschirmfoto-Schritte dort gehen zu dem Nachbarn,
den sie umschließen (Fenster vor und nach der Flashback-Wiederholung). --shards <n> sucht die n Teile mit der kürzesten
längsten Laufzeit (Dauer je Schritt aus den --timing-Logs, ungemessene Schritte 4,5 s), --shards guide:,bildschirmfotos
schneidet vor dem ersten Schritt mit diesem Namensanfang. Verhindern Gruppen mit Schritten weit auseinander bessere
Schnitte, nennt der Plan sie; ein Teil über der Zeitgrenze im Spiel (SelfTestRunner: 6 s je Schritt in der Welt,
mindestens 80 min) gibt eine Warnung. Die Prüfung zählt einen Teil nur, wenn sein Gradle mit 0 endete
und Ergebnisdatei, Schrittliste und Log jünger sind als sein Start; ein Ergebnis aus einem früheren Lauf zählt nie."""

WRAPPERS = ("bildschirmfotos",)
RANGE = re.compile(r"#\s*([0-9]{1,9})\s*(?:-\s*([0-9]{1,9})\s*)?")
MARK_BASE = re.compile(r" \*\(läuft immer\)\*$")
MARK_SETUP = re.compile(r" \*\(läuft mit jeder Auswahl aus `.*:`\)\*$")
MARK_SWITCH = re.compile(r" \*\(läuft, wenn ein ausgewählter Schritt vor „(.*)“ folgt, samt diesem\)\*$")
STEP_LINE = re.compile(r"\[Selbsttest\] Schritt (\d+)/(\d+): (.*)$")
PASS_LINE = re.compile(r"\[Selbsttest\] PASS (.*) \((\d+) Ticks\)$")
FABRIC_TIME = re.compile(r"^\[(\d\d):(\d\d):(\d\d)\]")
NEOFORGE_TIME = re.compile(r"^\[(\d{1,2}\w{3}\d{4} \d\d:\d\d:\d\d)\.(\d+)\]")
RESULT = re.compile(r"PASS gefiltert: (\d+) von (\d+) Schritten")
SHARD_OVERHEAD_S = 120
UNMEASURED_STEP_S = 4.5
RUN_LIMIT_FLOOR_S = 80 * 60
RUN_LIMIT_PER_STEP_S = 6
UNBALANCED = 1.3


class Step:
    def __init__(self, pos, name, phase, kind, until=None):
        self.pos = pos
        self.name = name
        self.phase = phase
        self.kind = kind
        self.until = until

    @property
    def group(self):
        return group(self.name)

    @property
    def framing(self):
        return self.kind in ("base", "setup", "switch")


def group(name):
    colon = name.find(":")
    return (name if colon < 0 else name[:colon]).strip()


def wrapper(name):
    return group(name).lower().startswith(WRAPPERS)


def parse_list(text):
    steps = []
    phase = None
    platform = None
    declared = None
    for line in text.split("\n"):
        head = re.match(r"# Selbsttest-Schritte \((.*), (\d+) Schritte\)", line)
        if head:
            platform, declared = head.group(1), int(head.group(2))
        if line.startswith("### In der Welt"):
            phase = "world"
        elif line.startswith("### Nach dem Verlassen"):
            phase = "after"
        elif line.startswith("#"):
            phase = None
        elif line.startswith("- ") and phase:
            name, kind, until = line[2:], "normal", None
            for mark, mark_kind in ((MARK_BASE, "base"), (MARK_SETUP, "setup"), (MARK_SWITCH, "switch")):
                m = mark.search(name)
                if m:
                    kind = mark_kind
                    until = m.group(1) if mark_kind == "switch" else None
                    name = name[:m.start()]
                    break
            steps.append(Step(len(steps) + 1, name, phase, kind, until))
    if declared is not None and declared != len(steps):
        raise ValueError(f"Schrittliste nennt {declared} Schritte, gelesen {len(steps)}")
    names = [s.name for s in steps]
    if len(set(names)) != len(names):
        raise ValueError("Schrittliste mit doppelten Namen")
    for s in steps:
        if s.kind == "switch":
            follows = [o for o in steps if o.phase == s.phase and o.pos > s.pos and o.name == s.until]
            if not follows:
                raise ValueError(f"Umschalt-Schritt „{s.name}“ ohne folgendes „{s.until}“")
    return platform, steps


def until_names(steps):
    return {s.until for s in steps if s.kind == "switch"}


def starts_with(name, prefix):
    if len(name) < len(prefix):
        return False
    for a, b in zip(name, prefix):
        if a != b and a.upper() != b.upper() and a.lower() != b.lower():
            return False
    return True


def parse_filter(text):
    include, include_ranges, exclude, exclude_ranges = [], [], [], []
    for token in re.split(r"[,|]", text or ""):
        t = token.strip()
        excluded = t.startswith("!")
        if excluded:
            t = t[1:].strip()
        if not t:
            continue
        m = RANGE.fullmatch(t)
        if m:
            a = int(m.group(1))
            b = int(m.group(2)) if m.group(2) else a
            (exclude_ranges if excluded else include_ranges).append((min(a, b), max(a, b)))
        else:
            (exclude if excluded else include).append(t)
    return include, include_ranges, exclude, exclude_ranges


def select(steps, text):
    include, include_ranges, exclude, exclude_ranges = parse_filter(text)
    active = bool(include or include_ranges or exclude or exclude_ranges)

    def excluded(s):
        return any(starts_with(s.name, p) for p in exclude) or any(a <= s.pos <= b for a, b in exclude_ranges)

    def matches(s):
        included = (not include and not include_ranges) or any(starts_with(s.name, p) for p in include) \
            or any(a <= s.pos <= b for a, b in include_ranges)
        return included and not excluded(s)

    if not active:
        return [s.name for s in steps]
    groups = {s.group for s in steps if matches(s) and not s.framing}
    any_match = any(matches(s) for s in steps)
    switched = set()
    for phase in ("world", "after"):
        seq = [s for s in steps if s.phase == phase]
        for i, s in enumerate(seq):
            if s.kind != "switch":
                continue
            keep = matches(s)
            j = i + 1
            while j < len(seq) and not keep:
                other = seq[j]
                if other.name == s.until:
                    break
                keep = not other.framing and matches(other)
                j += 1
            if keep:
                switched.add(s.name)
                switched.add(s.until)
    kept = []
    for s in steps:
        if matches(s):
            kept.append(s.name)
        elif excluded(s):
            continue
        elif s.name in switched or s.kind == "base" and any_match or s.kind == "setup" and s.group in groups:
            kept.append(s.name)
    return kept


def read_lines(path):
    path = Path(path)
    opener = gzip.open if path.suffix == ".gz" else open
    try:
        with opener(path, "rt", encoding="utf-8", errors="replace") as f:
            return f.read().split("\n")
    except OSError:
        return []


def line_time(line):
    m = NEOFORGE_TIME.match(line)
    if m:
        return datetime.strptime(m.group(1), "%d%b%Y %H:%M:%S").timestamp() + int(m.group(2)) / 10 ** len(m.group(2))
    m = FABRIC_TIME.match(line)
    if m:
        return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3))
    return None


def timings(paths):
    durations = {}
    for path in sorted((Path(p) for p in paths), key=lambda p: p.stat().st_mtime if p.exists() else 0):
        starts = []
        last = None
        shift = 0
        end = None
        for line in read_lines(path):
            t = line_time(line)
            if t is None:
                continue
            if last is not None and t + shift < last - 43200:
                shift += 86400
            t += shift
            last = t
            m = STEP_LINE.search(line)
            if m:
                starts.append((t, m.group(3)))
            elif "PROPWASH SELFTEST" in line or "Liste:" in line and "[Selbsttest]" in line:
                end = t
        for i, (t, name) in enumerate(starts):
            nxt = starts[i + 1][0] if i + 1 < len(starts) else end
            if nxt is not None and nxt >= t:
                durations[name] = nxt - t
    return durations


def weights(steps, durations):
    known = sum(1 for s in steps if s.name in durations)
    return {s.name: durations.get(s.name, UNMEASURED_STEP_S) for s in steps}, known


def key_spans(world):
    span = {}
    real = None
    run = None
    for i, s in enumerate(world):
        if wrapper(s.name):
            if i == 0 or not wrapper(world[i - 1].name):
                run = f"#{i}" if s.kind == "setup" or real is None else real
            key = run
        elif s.framing:
            continue
        else:
            key = real = s.group
        first, _ = span.get(key, (i, i))
        span[key] = (first, i)
    return span


def clean_cuts(steps):
    world = [s for s in steps if s.phase == "world"]
    span = key_spans(world)
    first_normal = next((i for i, s in enumerate(world) if not s.framing), len(world))
    last_normal = max((i for i, s in enumerate(world) if not s.framing), default=-1)
    cuts = []
    for k in range(first_normal + 1, last_normal + 1):
        if world[k].framing and not any(not o.framing and o.group == world[k].group for o in world[k + 1:]):
            continue
        if all(not (first < k <= last) for first, last in span.values()):
            cuts.append(k)
    return world, cuts


def segments_for(steps, world, cuts):
    bounds = [0]
    for k in cuts:
        later = {s.group for s in world[k:] if not s.framing}
        while k - 1 > bounds[-1] and world[k - 1].framing and world[k - 1].group in later:
            k -= 1
        bounds.append(k)
    bounds.append(len(world))
    segs = [[s.pos for s in world[bounds[i]:bounds[i + 1]]] for i in range(len(bounds) - 1)]
    home = {}
    for index, seg in enumerate(segs):
        for pos in seg:
            s = steps[pos - 1]
            if not s.framing and not wrapper(s.name):
                home[s.group] = index
    after = [s for s in steps if s.phase == "after"]
    owner = [None if wrapper(s.name) else home.get(s.group) for s in after]
    resolved = list(owner)
    i = 0
    while i < len(after):
        if owner[i] is not None:
            i += 1
            continue
        j = i
        while j + 1 < len(after) and owner[j + 1] is None:
            j += 1
        before = i - 1 if i > 0 else None
        behind = j + 1 if j + 1 < len(after) else None
        if before is not None and behind is not None and owner[before] != owner[behind]:
            wrapped_before = before > 0 and owner[before - 1] is None
            wrapped_behind = behind + 1 < len(after) and owner[behind + 1] is None
            pick = owner[before] if wrapped_before and not wrapped_behind else owner[behind]
        elif behind is not None:
            pick = owner[behind]
        elif before is not None:
            pick = owner[before]
        else:
            pick = len(segs) - 1
        for k in range(i, j + 1):
            resolved[k] = pick
        i = j + 1
    owner = resolved
    for s, o in zip(after, owner):
        segs[o].append(s.pos)
    return segs


def ranges_filter(positions):
    positions = sorted(positions)
    parts = []
    start = prev = None
    for p in positions:
        if start is None:
            start = prev = p
        elif p == prev + 1:
            prev = p
        else:
            parts.append((start, prev))
            start = prev = p
    if start is not None:
        parts.append((start, prev))
    return ",".join(f"#{a}" if a == b else f"#{a}-{b}" for a, b in parts)


def estimate(steps, weight, filter_text):
    return SHARD_OVERHEAD_S + sum(weight[name] for name in select(steps, filter_text))


def run_limit(steps, selected):
    world = {s.name for s in steps if s.phase == "world"}
    return max(RUN_LIMIT_FLOOR_S, RUN_LIMIT_PER_STEP_S * sum(1 for name in selected if name in world))


def blockers(steps, weight, count):
    world = [s for s in steps if s.phase == "world"]
    total = sum(weight[s.name] for s in world)
    targets = []
    acc = 0.0
    for k, s in enumerate(world):
        acc += weight[s.name]
        while len(targets) < count - 1 and acc >= total * (len(targets) + 1) / count:
            targets.append(k + 1)
    wide = max(2, len(world) // 10)
    found = []
    for key, (first, last) in sorted(key_spans(world).items(), key=lambda kv: kv[1]):
        if last - first + 1 >= wide and any(first < t <= last for t in targets):
            label = world[first].group if key.startswith("#") else key
            found.append({"group": label, "first": world[first].pos, "last": world[last].pos,
                          "from": world[first].name, "to": world[last].name})
    return found


def describe_blockers(found):
    return ", ".join(f"{b['group']} #{b['first']}–#{b['last']} („{b['from'][:40]}“ … „{b['to'][:40]}“)" for b in found)


def anchor_cuts(world, cuts, anchors, allow_unclean):
    chosen = []
    start = 0
    for anchor in anchors:
        k = next((i for i in range(max(start, 1), len(world))
                  if not world[i].framing and starts_with(world[i].name, anchor)), None)
        if k is None:
            raise ValueError(f"kein Schritt in der Welt nach dem vorigen Schnitt beginnt mit „{anchor}“")
        if k not in cuts and not allow_unclean:
            raise ValueError(f"Schnitt vor „{world[k].name}“ ist nicht sauber (eine Gruppe hat Schritte davor und danach); "
                             f"--allow-unclean erzwingt ihn")
        chosen.append(k)
        start = k + 1
    return chosen


def plan(steps, shards, durations, allow_unclean=False):
    world, cuts = clean_cuts(steps)
    weight, measured = weights(steps, durations)
    if isinstance(shards, int):
        count = max(1, shards)
        if count - 1 > len(cuts):
            raise ValueError(f"{count} Teile verlangt, es gibt nur {len(cuts)} saubere Schnitte; Schnitte verhindern: "
                             + (describe_blockers(blockers(steps, weight, count)) or "keine Gruppe"))
        best = None
        prefix = [0.0]
        for s in world:
            prefix.append(prefix[-1] + weight[s.name])
        for combo in combinations(cuts, count - 1):
            bounds = [0] + list(combo) + [len(world)]
            rough = max(prefix[bounds[i + 1]] - prefix[bounds[i]] for i in range(count))
            if best is not None and rough + SHARD_OVERHEAD_S > best[0]:
                continue
            segs = segments_for(steps, world, combo)
            est = [estimate(steps, weight, ranges_filter(seg)) for seg in segs]
            key = (max(est), sum(e * e for e in est))
            if best is None or key < best[1]:
                best = (max(est), key, combo)
        chosen = list(best[2])
    else:
        chosen = anchor_cuts(world, cuts, shards, allow_unclean)
    segs = segments_for(steps, world, chosen)
    result = []
    for index, seg in enumerate(segs):
        text = ranges_filter(seg)
        own = [steps[p - 1] for p in seg]
        selected = select(steps, text)
        result.append({
            "tag": f"shard-{index + 1}",
            "filter": text,
            "first": own[0].name,
            "last": [s for s in own if s.phase == "world"][-1].name,
            "after": sum(1 for s in own if s.phase == "after"),
            "selected": selected,
            "estimate_s": round(estimate(steps, weight, text)),
            "limit_s": run_limit(steps, selected),
        })
    longest = max(s["estimate_s"] for s in result)
    mean = sum(s["estimate_s"] for s in result) / len(result)
    found = blockers(steps, weight, len(result)) if len(result) > 1 and longest > UNBALANCED * mean else []
    return {
        "total": len(steps),
        "world": len(world),
        "cuts": [world[k].name for k in chosen],
        "clean_cuts": len(cuts),
        "measured_steps": measured,
        "blockers": [{"group": b["group"], "first": b["first"], "last": b["last"]} for b in found],
        "blocker_text": describe_blockers(found),
        "shards": result,
    }


def coverage(steps, selections):
    repeatable = {s.name for s in steps if s.framing} | until_names(steps)
    seen = {}
    for names in selections:
        for name in names:
            seen[name] = seen.get(name, 0) + 1
    missing = [s.name for s in steps if seen.get(s.name, 0) == 0]
    twice = [s.name for s in steps if seen.get(s.name, 0) > 1 and s.name not in repeatable]
    return missing, twice


def fresh(path, since):
    try:
        return since is None or path.stat().st_mtime >= since
    except FileNotFoundError:
        return False


def run_log(rundir, since=None):
    logs = Path(rundir) / "logs"
    files = []
    if (logs / "latest.log").exists() and fresh(logs / "latest.log", since):
        files.append(logs / "latest.log")
    rotated = sorted(logs.glob("*.log.gz"), key=lambda p: p.stat().st_mtime, reverse=True)[:3] if logs.is_dir() else []
    files += [f for f in rotated if fresh(f, since)]
    collected = []
    for f in files:
        lines = read_lines(f)
        collected = lines + collected
        marks = [i for i, line in enumerate(collected) if "[Selbsttest] Selbsttest aktiv" in line]
        if marks:
            return collected[marks[-1]:], True
    return collected, False


def passed_in_log(lines):
    names = []
    for line in lines:
        m = PASS_LINE.search(line)
        if m:
            names.append(m.group(1))
    return names


def fmt_min(seconds):
    seconds = int(round(seconds))
    return f"{seconds // 60}:{seconds % 60:02d} min"


def seconds_text(seconds):
    return f"{seconds:g} s".replace(".", ",")


def cmd_plan(args):
    platform, steps = parse_list(Path(args.list).read_text(encoding="utf-8"))
    spec = args.shards.strip()
    shards = int(spec) if spec.isdigit() else [a.strip() for a in re.split(r"[,;]", spec) if a.strip()]
    durations = timings(args.timing or [])
    result = plan(steps, shards, durations, args.allow_unclean)
    missing, twice = coverage(steps, [s["selected"] for s in result["shards"]])
    if missing or twice:
        raise ValueError(f"Plan deckt nicht ab: fehlend {missing[:3]}, doppelt {twice[:3]}")
    result["platform"] = platform
    if result["measured_steps"] == 0:
        print(f"Hinweis: keine Zeitmessung gefunden, jeder Schritt zählt {seconds_text(UNMEASURED_STEP_S)}", file=sys.stderr)
    print(f"{platform}: {result['total']} Schritte ({result['world']} in der Welt), {result['clean_cuts']} saubere Schnitte, "
          f"Dauer von {result['measured_steps']} Schritten gemessen")
    for s in result["shards"]:
        print(f"  {s['tag']}: {s['filter']}  ≈ {fmt_min(s['estimate_s'])}, {len(s['selected'])} Schritte, "
              f"„{s['first'][:60]}“ … „{s['last'][:60]}“, {s['after']} nach dem Verlassen")
    if result["measured_steps"] < result["total"]:
        print(f"  Hinweis: {result['total'] - result['measured_steps']} Schritte ohne Messung, je {seconds_text(UNMEASURED_STEP_S)} geschätzt")
    if result["blockers"]:
        longest = max(s["estimate_s"] for s in result["shards"])
        mean = sum(s["estimate_s"] for s in result["shards"]) / len(result["shards"])
        print(f"  Hinweis: Teile ungleich (längster ≈ {fmt_min(longest)}, im Mittel ≈ {fmt_min(mean)}); Schnitte verhindern "
              f"Gruppen mit Schritten vor und hinter der Mitte: {result['blocker_text']}")
    for s in result["shards"]:
        if s["estimate_s"] > s["limit_s"]:
            print(f"  Warnung: {s['tag']} ≈ {fmt_min(s['estimate_s'])} liegt über der Zeitgrenze im Spiel "
                  f"({s['limit_s'] // 60} min); mehr Teile mit --shards oder Schnitte vor Ankern")
    if args.out:
        Path(args.out).write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return 0


def pairs(items, option):
    out = {}
    for item in items or []:
        if "=" not in item:
            raise ValueError(f"{option} {item}: erwartet <kennung>=<wert>")
        tag, value = item.split("=", 1)
        out[tag] = value
    return out


def cmd_check(args):
    data = json.loads(Path(args.plan).read_text(encoding="utf-8"))
    list_text = Path(args.list).read_text(encoding="utf-8")
    platform, steps = parse_list(list_text)
    shards = {s["tag"]: s for s in data["shards"]}
    dirs = pairs(args.shard, "--shard")
    exits = pairs(args.exit, "--exit")
    starts = pairs(args.started, "--started")
    problems = []
    selections = []
    for tag, shard in shards.items():
        rundir = Path(dirs.get(tag, ""))
        expected = shard["selected"]
        state = []
        code = exits.get(tag)
        if code is None:
            state.append("kein Gradle-Exit (Teil nicht zu Ende gelaufen)")
        elif code.strip() != "0":
            state.append(f"Gradle-Exit {code.strip()}")
        since = starts.get(tag)
        if since is None:
            state.append("keine Startzeit des Teils")
        else:
            try:
                since = int(since)
            except ValueError:
                raise ValueError(f"--started {tag}={since}: keine Zahl")
        result_file = rundir / "propwash-selftest-result.txt"
        if not result_file.exists():
            result = "keine Ergebnisdatei"
        elif not fresh(result_file, since):
            result = "Ergebnisdatei älter als der Start des Teils (aus einem früheren Lauf)"
        else:
            result = result_file.read_text(encoding="utf-8").strip()
        own_list = rundir / "propwash-selftest-steps.md"
        latest = rundir / "logs" / "latest.log"
        if latest.exists() and not fresh(latest, since):
            state.append("latest.log älter als der Start des Teils")
        lines, complete = run_log(rundir, since)
        passed = passed_in_log(lines)
        m = RESULT.search(result)
        if not result.startswith("PASS"):
            state.append(result[:300])
        elif not m:
            state.append(f"Ergebnis ohne Zahl: {result[:120]}")
        else:
            if int(m.group(1)) != len(expected):
                state.append(f"{m.group(1)} Schritte gelaufen, geplant {len(expected)}")
            if int(m.group(2)) != len(steps):
                state.append(f"{m.group(2)} Schritte angemeldet, in der Liste {len(steps)}")
        if not own_list.exists() or own_list.read_text(encoding="utf-8") != list_text:
            state.append("Schrittliste des Teils weicht von der Liste ab")
        elif not fresh(own_list, since):
            state.append("Schrittliste des Teils älter als sein Start")
        if complete:
            if sorted(passed) != sorted(expected):
                extra = sorted(set(passed) - set(expected))[:3]
                lost = sorted(set(expected) - set(passed))[:3]
                state.append(f"Log: {len(passed)} PASS statt {len(expected)} (fehlend {lost}, zusätzlich {extra})")
            selections.append(passed)
        else:
            state.append("Log ohne Start des Laufs")
            selections.append(expected if not state[:-1] else [])
        problems += [f"{tag}: {p}" for p in state]
        print(f"  {tag}: {'PASS' if not state else 'FAIL'} {len(passed)} Schritte ({shard['filter']})"
              + ("" if not state else " – " + "; ".join(state)))
    missing, twice = coverage(steps, selections)
    if missing:
        problems.append(f"{len(missing)} Schritte in keinem Teil bestanden, z. B. „{missing[0]}“")
    if twice:
        problems.append(f"{len(twice)} Schritte in mehreren Teilen, z. B. „{twice[0]}“")
    if problems:
        print(f"Selbsttest in Teilen ({platform}): FAIL – " + " | ".join(problems))
        return 1
    print(f"Selbsttest in Teilen ({platform}): PASS – alle {len(steps)} Schritte bestanden, jeder genau einmal "
          f"(Rahmen-Schritte in jedem Teil), {len(shards)} Teile")
    return 0


def main(argv):
    parser = argparse.ArgumentParser(description=HELP, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("plan")
    p.add_argument("--list", required=True)
    p.add_argument("--shards", default="2")
    p.add_argument("--timing", action="append")
    p.add_argument("--out")
    p.add_argument("--allow-unclean", action="store_true")
    c = sub.add_parser("check")
    c.add_argument("--plan", required=True)
    c.add_argument("--list", required=True)
    c.add_argument("--shard", action="append", default=[])
    c.add_argument("--exit", action="append", default=[])
    c.add_argument("--started", action="append", default=[])
    args = parser.parse_args(argv)
    try:
        return cmd_plan(args) if args.command == "plan" else cmd_check(args)
    except ValueError as e:
        print(f"selftest_shards: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
