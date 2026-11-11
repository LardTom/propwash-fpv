#!/usr/bin/env python3
from __future__ import annotations

import argparse
import io
import json
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
from pathlib import Path

HELP = """Prüft die Texte für Add-on-Bauer und die Fehlerbericht-Vorlage auf dem Export-Stand.

Aufruf aus einem Arbeitsbaum des Repositorys:
    python3 tools/public_export_texts_test.py [--ref HEAD]

Zuerst läuft python3 tools/public_export.py --check-only --no-build (falls das Werkzeug da ist) und muss bestehen.
Dann wird der Commit wie für den öffentlichen Stand ausgepackt (git archive ohne die Pfade unter "exclude" in
tools/public_export.json) und geprüft:
  - ADDONS.md ist da, nennt als offizielles Add-on nur Just More Parts, keine Server-Mod und nirgends „Propwash: Server“
  - README.md und NOTICE haben den Absatz „Reserved namespace“ mit Verweis auf ADDONS.md
  - .github/ISSUE_TEMPLATE/bug_report.yml: Feld addons mit required: true, Auswahl without-addons mit der Frage
    „Does it also happen without add-ons?“, genau drei Antworten, Pflicht
  - .github/ISSUE_TEMPLATE/config.yml: blank_issues_enabled: true
Mit --gegenprobe prüft das Werkzeug zusätzlich an veränderten Kopien, dass jede Prüfung einen Fehler erkennt.
"""

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "tools" / "public_export.json"
EXPORT_TOOL = ROOT / "tools" / "public_export.py"
OFFICIAL = "Just More Parts"
FORBIDDEN = ["Propwash: Server", "server mod"]
QUESTION = "Does it also happen without add-ons?"


def excluded(path: str, patterns: list[str]) -> bool:
    for pattern in patterns:
        if pattern.endswith("/"):
            if path.startswith(pattern) or path == pattern[:-1]:
                return True
        elif path == pattern:
            return True
    return False


def export_tree(ref: str, destination: Path) -> None:
    patterns = json.loads(CONFIG.read_text(encoding="utf-8")).get("exclude", []) if CONFIG.is_file() else []
    archive = subprocess.run(["git", "archive", "--format=tar", ref], cwd=ROOT, capture_output=True)
    if archive.returncode != 0:
        raise SystemExit("git archive: " + archive.stderr.decode("utf-8", "replace").strip())
    with tarfile.open(fileobj=io.BytesIO(archive.stdout)) as tar:
        members = [m for m in tar.getmembers() if not excluded(m.name, patterns)]
        tar.extractall(destination, members=members, filter="tar")


def paragraph(text: str, start: str) -> str | None:
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if start.lower() in line.lower():
            j = i
            while j < len(lines) and lines[j].strip():
                j += 1
            return "\n".join(lines[i:j])
    return None


def form_fields(text: str) -> list[dict]:
    fields: list[dict] = []
    current: dict | None = None
    in_options = False
    for raw in text.splitlines():
        line = raw.rstrip()
        if not line.strip() or line.strip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        stripped = line.strip()
        match = re.match(r"^- type:\s*(\S+)", stripped)
        if match and indent <= 4:
            current = {"type": match.group(1), "options": []}
            fields.append(current)
            in_options = False
            continue
        if current is None:
            continue
        if in_options and stripped.startswith("- "):
            current["options"].append(stripped[2:].strip().strip("\"'"))
            continue
        in_options = False
        key, _, value = stripped.partition(":")
        value = value.strip().strip("\"'")
        if key == "options" and not value:
            in_options = True
        elif key in ("id", "label", "required"):
            current[key] = value
    return fields


def check(tree: Path) -> list[tuple[str, bool, str]]:
    results: list[tuple[str, bool, str]] = []

    def add(name: str, good: bool, detail: str) -> None:
        results.append((name, good, detail))

    addons = tree / "ADDONS.md"
    text = addons.read_text(encoding="utf-8") if addons.is_file() else ""
    add("ADDONS.md vorhanden", addons.is_file(), str(len(text)) + " Zeichen")
    names = sorted({m.group(1).strip() for m in re.finditer(r"Propwash: ([A-Z][A-Za-z ]+[A-Za-z])", text)})
    others = [n for n in names if n != OFFICIAL]
    add("ADDONS.md nennt als offizielles Add-on nur Just More Parts", OFFICIAL in names and not others,
        "offizielle Namen: " + (", ".join("Propwash: " + n for n in names) or "keine"))
    hits = [term for term in FORBIDDEN if term.lower() in text.lower()]
    add("ADDONS.md ohne Server-Mod und „Propwash: Server“", not hits, "gefunden: " + ", ".join(hits) if hits else "keine Treffer")
    for name in ("README.md", "NOTICE"):
        path = tree / name
        block = paragraph(path.read_text(encoding="utf-8"), "Reserved namespace") if path.is_file() else None
        add(f"{name}: Absatz „Reserved namespace“ mit Verweis auf ADDONS.md", block is not None and "ADDONS.md" in block,
            (block.splitlines()[0][:80] + " …") if block else "Absatz fehlt")
    readme = tree / "README.md"
    if readme.is_file():
        block = paragraph(readme.read_text(encoding="utf-8"), "Reserved namespace") or ""
        add("README.md verlinkt ADDONS.md", "(ADDONS.md)" in block, "Link gefunden" if "(ADDONS.md)" in block else "kein Markdown-Link")
    bug = tree / ".github" / "ISSUE_TEMPLATE" / "bug_report.yml"
    fields = form_fields(bug.read_text(encoding="utf-8")) if bug.is_file() else []
    by_id = {f.get("id"): f for f in fields if f.get("id")}
    add("bug_report.yml vorhanden", bug.is_file(), f"{len(fields)} Felder")
    field = by_id.get("addons")
    add("bug_report.yml: Feld addons ist Pflicht", field is not None and field.get("required") == "true",
        f"{field.get('type')}, required: {field.get('required')}" if field else "Feld fehlt")
    choice = by_id.get("without-addons")
    add("bug_report.yml: without-addons fragt „" + QUESTION + "“", choice is not None and choice.get("type") == "dropdown"
        and choice.get("label") == QUESTION, f"{choice.get('type')}: {choice.get('label')}" if choice else "Feld fehlt")
    add("bug_report.yml: without-addons hat genau drei Antworten und ist Pflicht", choice is not None and len(choice["options"]) == 3
        and choice.get("required") == "true", f"{choice['options']}, required: {choice.get('required')}" if choice else "Feld fehlt")
    config = tree / ".github" / "ISSUE_TEMPLATE" / "config.yml"
    blank = re.search(r"^blank_issues_enabled:\s*true\s*$", config.read_text(encoding="utf-8"), re.MULTILINE) if config.is_file() else None
    add("config.yml: blank_issues_enabled: true", blank is not None, "gesetzt" if blank else "fehlt oder false")
    return results


def mutations() -> list[tuple[str, str, callable]]:
    def edit(relative: str, change):
        def apply(tree: Path) -> None:
            path = tree / relative
            path.write_text(change(path.read_text(encoding="utf-8")), encoding="utf-8")
        return apply

    return [
        ("ADDONS.md nennt als offizielles Add-on nur Just More Parts", "zweites offizielles Add-on",
         edit("ADDONS.md", lambda t: t + "\nOfficial add-ons: *Propwash: Extra Parts*.\n")),
        ("ADDONS.md ohne Server-Mod und „Propwash: Server“", "„Propwash: Server“ eingefügt",
         edit("ADDONS.md", lambda t: t + "\nSee also Propwash: Server.\n")),
        ("NOTICE: Absatz „Reserved namespace“ mit Verweis auf ADDONS.md", "Verweis entfernt",
         edit("NOTICE", lambda t: t.replace("ADDONS.md", "the guidelines"))),
        ("README.md: Absatz „Reserved namespace“ mit Verweis auf ADDONS.md", "Absatz entfernt",
         edit("README.md", lambda t: t.replace("Reserved namespace", "Namespace"))),
        ("bug_report.yml: Feld addons ist Pflicht", "required: false",
         edit(".github/ISSUE_TEMPLATE/bug_report.yml", lambda t: re.sub(r"(id: addons[\s\S]*?required: )true", r"\1false", t, count=1))),
        ("bug_report.yml: without-addons hat genau drei Antworten und ist Pflicht", "vierte Antwort",
         edit(".github/ISSUE_TEMPLATE/bug_report.yml", lambda t: t.replace("        - Not tested yet\n", "        - Not tested yet\n        - Maybe\n"))),
        ("config.yml: blank_issues_enabled: true", "leere Issues aus",
         edit(".github/ISSUE_TEMPLATE/config.yml", lambda t: t.replace("blank_issues_enabled: true", "blank_issues_enabled: false"))),
    ]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=HELP, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--ref", default="HEAD")
    parser.add_argument("--ohne-export", action="store_true", help="public_export.py --check-only nicht aufrufen")
    parser.add_argument("--gegenprobe", action="store_true", help="jede Prüfung an einer veränderten Kopie gegenprüfen")
    args = parser.parse_args(argv)
    failures = 0
    if EXPORT_TOOL.is_file() and not args.ohne_export:
        command = [sys.executable, str(EXPORT_TOOL), "--check-only", "--no-build", "--ref", args.ref]
        done = subprocess.run(command, cwd=ROOT, capture_output=True, text=True)
        tail = (done.stdout + done.stderr).strip().splitlines()
        passed = done.returncode == 0 and any("Prüfung bestanden" in line for line in tail)
        print(f"{'OK    ' if passed else 'FEHLER'} public_export.py --check-only --no-build  " + (tail[-1] if tail else f"Exit {done.returncode}"))
        if not passed:
            failures += 1
            for line in tail[-12:]:
                print("       " + line)
    with tempfile.TemporaryDirectory(prefix="public-texts-") as tmp:
        tree = Path(tmp) / "tree"
        tree.mkdir()
        export_tree(args.ref, tree)
        results = check(tree)
        width = max(len(name) for name, _, _ in results)
        for name, good, detail in results:
            print(f"{'OK    ' if good else 'FEHLER'} {name.ljust(width)}  {detail}")
            failures += 0 if good else 1
        if args.gegenprobe:
            for index, (name, what, apply) in enumerate(mutations()):
                work = Path(tmp) / f"m{index}"
                shutil.copytree(tree, work)
                apply(work)
                hit = next((good for check_name, good, _ in check(work) if check_name == name), None)
                caught = hit is False
                print(f"Gegenprobe {'OK    ' if caught else 'FEHLER'} {what}: „{name}“")
                failures += 0 if caught else 1
    print(f"{'bestanden' if failures == 0 else str(failures) + ' Fehler'} (Stand {args.ref})")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
