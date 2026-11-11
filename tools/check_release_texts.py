#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

HELP = """Prüft die Texte des Release-Pakets (Changelogs und Discord-Entwürfe) gegen CHANGELOG.md.

Aufruf im Release (R5):
    python3 tools/check_release_texts.py --paket <release-paket> --ref v0.6.0 \
        --jmp-changelog <propwash-justmoreparts>/CHANGELOG.md --jmp-ref v1.2.0

Probelauf gegen den heutigen Stand von CHANGELOG.md (baut ein Paket in einem temporären Ordner, mit --texte
kommen die Discord-Entwürfe dazu, mit --jmp-changelog der Abschnitt von Just More Parts):
    python3 tools/check_release_texts.py --probe [--texte <ordner>] [--jmp-changelog <datei>]

Geprüft wird: changelog-<version>.md gleich dem Abschnitt in CHANGELOG.md (beim Ref), 2–3 Highlights und die
Abschnitte New, Improved, Fixed, Compatibility, die sechs Punkte unter Compatibility, bluepanee im Druckplatten-Eintrag,
changelog-<jmp-version>.md gleich dem Abschnitt im JMP-CHANGELOG und mit „Propwash <version>“, discord-pw3.md mit dem
Link auf /convert/ und der Bitte, den alten Code stehen zu lassen, jede discord-*.md unter 2000 Zeichen und nirgends
„Propwash: Server“. Der Probelauf prüft dazu mit Gegenproben, dass jede Prüfung einen Fehler auch erkennt.
"""

ROOT = Path(__file__).resolve().parent.parent
SIBLING_JMP = ROOT.parent / "propwash-justmoreparts" / "CHANGELOG.md"
CONVERT_URL = "https://propwashfpv.com/convert/"
FORBIDDEN = "Propwash: Server"
SECTIONS = ["Highlights", "New", "Improved", "Fixed", "Compatibility"]
COMPATIBILITY = [
    ("Trennung beim Beitritt (0.5.x und 0.6.0)", [r"(can't|cannot|can not) join", r"0\.5\.x"]),
    ("Just More Parts 1.2.0 und 1.1.x", [r"Just More Parts", r"1\.2\.0", r"1\.1(\.x|\.\d)"]),
    ("0.5.x liest keine PW3-Codes", [r"0\.5(\.x|\.2)?[^.\n]*(can't|cannot|doesn't|does not|no longer)[^.\n]*PW3"]),
    ("Freecam-Regel standardmäßig aus", [r"freecam", r"\boff\b"]),
    ("RX-Blöcke verschwinden zurück unter 0.5.2", [r"RX block", r"0\.5\.2"]),
    ("Hinweis zu Noclip", [r"Noclip"]),
]
KEEP_OLD = [r"keep (the|your) old code", r"leave (the|your) old code", r"don't (delete|remove) (the|your) old code"]
DISCORD_LIMIT = 2000


@dataclass
class Result:
    name: str
    status: str
    detail: str = ""


@dataclass
class Report:
    results: list[Result] = field(default_factory=list)

    def ok(self, name: str, detail: str = "") -> None:
        self.results.append(Result(name, "OK", detail))

    def fail(self, name: str, detail: str) -> None:
        self.results.append(Result(name, "FEHLER", detail))

    def open(self, name: str, detail: str) -> None:
        self.results.append(Result(name, "OFFEN", detail))

    def check(self, name: str, good: bool, detail: str) -> None:
        if good:
            self.ok(name, detail)
        else:
            self.fail(name, detail)

    def failed(self) -> list[Result]:
        return [r for r in self.results if r.status == "FEHLER"]

    def text(self) -> str:
        width = max((len(r.name) for r in self.results), default=10)
        return "\n".join(f"{r.status:6} {r.name.ljust(width)}  {r.detail}".rstrip() for r in self.results)


def read_ref(path: Path, ref: str | None) -> str:
    if not ref:
        return path.read_text(encoding="utf-8")
    repo = path.resolve().parent
    top = subprocess.run(["git", "-C", str(repo), "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()
    relative = path.resolve().relative_to(Path(top).resolve())
    return subprocess.run(["git", "-C", top, "show", f"{ref}:{relative.as_posix()}"], capture_output=True, text=True, check=True).stdout


def section(changelog: str, version: str) -> str | None:
    lines = changelog.splitlines()
    start = None
    for i, line in enumerate(lines):
        if re.match(rf"^## \[{re.escape(version)}\]", line):
            start = i
            break
    if start is None:
        return None
    end = len(lines)
    for j in range(start + 1, len(lines)):
        if lines[j].startswith("## "):
            end = j
            break
    return "\n".join(lines[start:end]).strip() + "\n"


def normalize(text: str) -> str:
    return "\n".join(line.rstrip() for line in text.strip().splitlines()) + "\n"


def without_heading(text: str) -> str:
    lines = normalize(text).splitlines()
    if lines and lines[0].startswith("## "):
        lines = lines[1:]
    return "\n".join(lines).strip() + "\n"


def subsections(text: str) -> dict[str, str]:
    out: dict[str, str] = {}
    current = None
    for line in text.splitlines():
        match = re.match(r"^### (.+?)\s*$", line)
        if match:
            current = match.group(1)
            out[current] = ""
        elif current is not None:
            out[current] += line + "\n"
    return out


def bullets(text: str) -> list[str]:
    items: list[str] = []
    for line in text.splitlines():
        if line.startswith("- "):
            items.append(line[2:])
        elif items and line.startswith("  "):
            items[-1] += " " + line.strip()
    return items


def check_changelog(report: Report, paket: Path, source: str, version: str) -> None:
    file = paket / f"changelog-{version}.md"
    wanted = section(source, version)
    if wanted is None:
        report.fail(f"CHANGELOG.md [{version}]", "kein Abschnitt ## [" + version + "] in CHANGELOG.md")
        return
    if not file.is_file():
        report.open(f"changelog-{version}.md gleich CHANGELOG.md", f"{file} fehlt")
    else:
        given = file.read_text(encoding="utf-8")
        same = normalize(given) == normalize(wanted) or without_heading(given) == without_heading(wanted)
        detail = "gleich dem Abschnitt" if same else first_difference(without_heading(wanted), without_heading(given))
        report.check(f"changelog-{version}.md gleich CHANGELOG.md", same, detail)
    parts = subsections(wanted)
    order = [name for name in parts if name in SECTIONS]
    report.check("Abschnitte Highlights, New, Improved, Fixed, Compatibility", order == SECTIONS, "gefunden: " + ", ".join(parts))
    highlights = bullets(parts.get("Highlights", ""))
    report.check("2–3 Highlights", 2 <= len(highlights) <= 3, f"{len(highlights)} Highlights")
    compat = parts.get("Compatibility", "")
    compat_items = bullets(compat)
    for name, patterns in COMPATIBILITY:
        hit = next((item for item in compat_items if all(re.search(p, item, re.IGNORECASE) for p in patterns)), None)
        report.check(f"Kompatibilität: {name}", hit is not None, short(hit) if hit else "kein Punkt mit " + " + ".join(patterns))
    fixed = bullets(parts.get("Fixed", "")) + bullets(parts.get("New", "")) + bullets(parts.get("Improved", ""))
    plate = next((item for item in fixed if re.search(r"pressure plate", item, re.IGNORECASE)), None)
    report.check("Druckplatten-Eintrag nennt bluepanee", plate is not None and "bluepanee" in plate,
                 short(plate) if plate else "kein Eintrag zu pressure plates")


def check_jmp(report: Report, paket: Path, jmp_source: str | None, version: str, propwash: str) -> None:
    file = paket / f"changelog-{version}.md"
    wanted = section(jmp_source, version) if jmp_source else None
    if not file.is_file():
        if wanted is None:
            report.open(f"changelog-{version}.md nennt „Propwash {propwash}“", f"{file.name} fehlt, Abschnitt [{version}] im JMP-CHANGELOG noch nicht da")
            return
        given = wanted
        report.open(f"changelog-{version}.md gleich JMP-CHANGELOG.md", f"{file.name} fehlt, geprüft wird der Abschnitt aus dem JMP-CHANGELOG")
    else:
        given = file.read_text(encoding="utf-8")
        if wanted is None:
            report.open(f"changelog-{version}.md gleich JMP-CHANGELOG.md", f"Abschnitt [{version}] im JMP-CHANGELOG fehlt")
        else:
            same = normalize(given) == normalize(wanted) or without_heading(given) == without_heading(wanted)
            report.check(f"changelog-{version}.md gleich JMP-CHANGELOG.md", same,
                         "gleich dem Abschnitt" if same else first_difference(without_heading(wanted), without_heading(given)))
    report.check(f"changelog-{version}.md nennt „Propwash {propwash}“", f"Propwash {propwash}" in given or f"Propwash FPV {propwash}" in given,
                 "gefunden" if f"Propwash {propwash}" in given or f"Propwash FPV {propwash}" in given else "kein „Propwash " + propwash + "“")


def discord_files(*folders: Path) -> list[Path]:
    seen: dict[str, Path] = {}
    for folder in folders:
        if folder and folder.is_dir():
            for path in sorted(folder.glob("discord-*.md")):
                seen.setdefault(path.name, path)
    return list(seen.values())


def check_discord(report: Report, files: list[Path]) -> None:
    pw3 = next((p for p in files if p.name == "discord-pw3.md"), None)
    if pw3 is None:
        report.open("discord-pw3.md", "Datei fehlt")
    else:
        text = pw3.read_text(encoding="utf-8")
        report.check("discord-pw3.md verlinkt " + CONVERT_URL, CONVERT_URL in text, "gefunden" if CONVERT_URL in text else "Link fehlt")
        keep = next((m.group(0) for p in KEEP_OLD for m in [re.search(p, text, re.IGNORECASE)] if m), None)
        report.check("discord-pw3.md: alten Code stehen lassen, PW3 darunter", keep is not None and re.search(r"\bbelow\b|\bunder\b", text, re.IGNORECASE)
                     is not None, f"„{keep}“" if keep else "keine Bitte, den alten Code stehen zu lassen")
    for path in files:
        length = len(path.read_text(encoding="utf-8"))
        report.check(f"{path.name} unter {DISCORD_LIMIT} Zeichen", length < DISCORD_LIMIT, f"{length} Zeichen")


def check_forbidden(report: Report, files: list[Path]) -> None:
    hits = []
    for path in files:
        if path.is_file():
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if FORBIDDEN in line:
                    hits.append(f"{path.name}:{number}")
    report.check(f"nirgends „{FORBIDDEN}“", not hits, f"{len(files)} Dateien geprüft" if not hits else ", ".join(hits))


def first_difference(wanted: str, given: str) -> str:
    a = wanted.splitlines()
    b = given.splitlines()
    for i in range(max(len(a), len(b))):
        x = a[i] if i < len(a) else "<Ende>"
        y = b[i] if i < len(b) else "<Ende>"
        if x != y:
            return f"Zeile {i + 1}: erwartet „{short(x)}“, steht „{short(y)}“"
    return "gleich"


def short(text: str | None, limit: int = 90) -> str:
    if text is None:
        return ""
    return text if len(text) <= limit else text[:limit - 1] + "…"


def run(paket: Path, changelog: Path, ref: str | None, jmp_changelog: Path | None, jmp_ref: str | None, version: str, jmp_version: str,
        texte: Path | None) -> Report:
    report = Report()
    source = read_ref(changelog, ref)
    check_changelog(report, paket, source, version)
    jmp_source = None
    if jmp_changelog is not None and jmp_changelog.is_file():
        try:
            jmp_source = read_ref(jmp_changelog, jmp_ref)
        except subprocess.CalledProcessError as error:
            report.fail("JMP-CHANGELOG lesen", error.stderr.strip() or str(error))
    check_jmp(report, paket, jmp_source, jmp_version, version)
    discord = discord_files(paket, texte) if texte else discord_files(paket)
    check_discord(report, discord)
    markdown = sorted({*paket.glob("*.md"), *discord})
    check_forbidden(report, markdown)
    return report


def mutations(paket: Path) -> list[tuple[str, str, callable]]:
    def edit(name: str, change):
        def apply(folder: Path) -> None:
            path = folder / name
            path.write_text(change(path.read_text(encoding="utf-8")), encoding="utf-8")
        return apply

    version_file = next(iter(sorted(paket.glob("changelog-0.*.md"))), None)
    out = []
    if version_file is not None:
        name = version_file.name
        out += [
            (f"{name} gleich CHANGELOG.md", "eine Zeile geändert", edit(name, lambda t: t.replace("### New", "### New\n- Probe.", 1))),
            ("Druckplatten-Eintrag nennt bluepanee", "bluepanee entfernt", None),
            ("Kompatibilität: Hinweis zu Noclip", "Noclip-Punkt entfernt", None),
            ("2–3 Highlights", "viertes Highlight", None),
        ]
    if (paket / "discord-pw3.md").is_file():
        out += [
            ("discord-pw3.md verlinkt " + CONVERT_URL, "Link entfernt", edit("discord-pw3.md", lambda t: t.replace(CONVERT_URL, "https://example.org/"))),
            ("discord-pw3.md: alten Code stehen lassen, PW3 darunter", "Bitte entfernt",
             edit("discord-pw3.md", lambda t: re.sub("|".join(KEEP_OLD), "Delete it", t, flags=re.IGNORECASE))),
            (f"nirgends „{FORBIDDEN}“", "verbotener Name eingefügt", edit("discord-pw3.md", lambda t: t + "\n" + FORBIDDEN + "\n")),
        ]
    return out


def probe(changelog: Path, jmp_changelog: Path | None, version: str, jmp_version: str, texte: Path | None) -> int:
    source = changelog.read_text(encoding="utf-8")
    current = section(source, version)
    with tempfile.TemporaryDirectory(prefix="release-texts-") as tmp:
        paket = Path(tmp) / "paket"
        paket.mkdir()
        if current is not None:
            (paket / f"changelog-{version}.md").write_text(current, encoding="utf-8")
        if jmp_changelog is not None and jmp_changelog.is_file():
            jmp = section(jmp_changelog.read_text(encoding="utf-8"), jmp_version)
            if jmp is not None:
                (paket / f"changelog-{jmp_version}.md").write_text(jmp, encoding="utf-8")
        for path in discord_files(texte) if texte else []:
            shutil.copy(path, paket / path.name)
        print(f"Probelauf: Paket aus {changelog.name} (Abschnitt [{version}]), JMP {jmp_changelog or '-'}, Entwürfe {texte or '-'}")
        report = run(paket, changelog, None, jmp_changelog, None, version, jmp_version, None)
        print(report.text())
        problems = 0
        for name, what, apply in mutations(paket):
            work = Path(tmp) / ("m" + str(problems) + "-" + re.sub(r"\W+", "_", name)[:20])
            shutil.copytree(paket, work)
            changed_source = changelog
            if apply is None:
                text = source
                if what.startswith("bluepanee"):
                    text = text.replace("bluepanee", "someone")
                elif what.startswith("Noclip"):
                    text = "\n".join(line for line in text.splitlines() if not (line.startswith("- Noclip") or "Noclip 0.1.2" in line)) + "\n"
                elif what.startswith("viertes"):
                    text = text.replace("### Highlights\n", "### Highlights\n- **Probe:** extra.\n- **Probe 2:** extra.\n", 1)
                changed_source = Path(tmp) / f"CHANGELOG-{problems}.md"
                changed_source.write_text(text, encoding="utf-8")
                new_section = section(text, version)
                if new_section is not None:
                    (work / f"changelog-{version}.md").write_text(new_section, encoding="utf-8")
            else:
                apply(work)
            mutated = run(work, changed_source, None, jmp_changelog, None, version, jmp_version, None)
            hit = next((r for r in mutated.results if r.name == name), None)
            caught = hit is not None and hit.status == "FEHLER"
            print(f"Gegenprobe {'OK    ' if caught else 'FEHLER'} {what}: „{name}“ " + (hit.status if hit else "nicht geprüft"))
            if not caught:
                problems += 1
        failed = report.failed()
        print(f"Probelauf: {len(report.results)} Prüfungen, {len(failed)} Fehler, "
              f"{sum(1 for r in report.results if r.status == 'OFFEN')} offen; Gegenproben {'alle erkannt' if problems == 0 else str(problems) + ' nicht erkannt'}")
        return 2 if problems else (1 if failed else 0)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=HELP, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--paket", type=Path, default=None, help="Ordner des Release-Pakets (Pflicht ohne --probe)")
    parser.add_argument("--texte", type=Path, default=None, help="weiterer Ordner mit discord-*.md (Standard: nur das Paket)")
    parser.add_argument("--changelog", type=Path, default=ROOT / "CHANGELOG.md")
    parser.add_argument("--ref", default=None, help="Git-Ref für CHANGELOG.md, im Release v0.6.0")
    parser.add_argument("--jmp-changelog", type=Path, default=None, help="CHANGELOG.md von Just More Parts (Standard: Nachbar-Repo)")
    parser.add_argument("--jmp-ref", default=None, help="Git-Ref für das JMP-CHANGELOG, im Release v1.2.0")
    parser.add_argument("--version", default="0.6.0")
    parser.add_argument("--jmp-version", default="1.2.0")
    parser.add_argument("--probe", action="store_true", help="Probelauf mit einem Paket aus dem heutigen CHANGELOG.md und Gegenproben")
    args = parser.parse_args(argv)
    jmp = args.jmp_changelog or (SIBLING_JMP if SIBLING_JMP.is_file() else None)
    if args.probe:
        return probe(args.changelog, jmp, args.version, args.jmp_version, args.texte)
    if args.paket is None or not args.paket.is_dir():
        print(f"Release-Paket fehlt: {args.paket}", file=sys.stderr)
        return 2
    report = run(args.paket, args.changelog, args.ref, jmp, args.jmp_ref, args.version, args.jmp_version, args.texte)
    print(report.text())
    failed = report.failed()
    print(f"{len(report.results)} Prüfungen, {len(failed)} Fehler, {sum(1 for r in report.results if r.status == 'OFFEN')} offen")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
