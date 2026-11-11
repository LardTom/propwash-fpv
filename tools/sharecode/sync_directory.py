#!/usr/bin/env python3
"""Erzeugt den Verzeichnis-Abzug data/propwash/propwash_share/directory.json.

Quelle sind die Verzeichnisdaten der Website (assets/data/addons/directory.json) und je offiziellem Add-on dessen
Jar oder Ressourcen-Ordner (Teile-Tabelle und Sprachdateien). Ohne --site gelten die festen Einträge 1 = propwash und
2 = justmoreparts. Mit --check wird nur verglichen (Exit-Code 1 bei Abweichung).

Aufruf:
  python3 tools/sharecode/sync_directory.py --addon justmoreparts=<jar oder ordner> [--site <directory.json>]
      [--version justmoreparts=1.2.0] [--propwash-version 0.6.0] [--out <datei>] [--check]
"""
import argparse
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "common/src/main/resources/data/propwash/propwash_share/directory.json"
KINDS = ["frame", "stack", "motor", "prop", "video", "battery", "accessory"]
LANGUAGES = ["en_us", "de_de"]
FIXED = [
    {"namespace": "propwash", "short_id": 1, "name": {"en_us": "Propwash FPV", "de_de": "Propwash FPV"},
     "modrinth": "propwash-fpv", "curseforge": "propwash-fpv"},
    {"namespace": "justmoreparts", "short_id": 2,
     "name": {"en_us": "Propwash: Just More Parts", "de_de": "Propwash: Just More Parts"},
     "modrinth": "propwash-just-more-parts", "curseforge": "propwash-just-more-parts"},
]


class Source:
    def __init__(self, path):
        self.path = pathlib.Path(path)
        self.zip = zipfile.ZipFile(self.path) if self.path.is_file() else None

    def read(self, name):
        if self.zip is not None:
            try:
                return self.zip.read(name).decode("utf-8")
            except KeyError:
                return None
        file = self.path / name
        return file.read_text(encoding="utf-8") if file.is_file() else None

    def version(self):
        text = self.read("fabric.mod.json")
        if text:
            try:
                return json.loads(text).get("version")
            except ValueError:
                return None
        return None


def version_key(text):
    core = text.split("+", 1)[0]
    head, _, suffix = core.partition("-")
    numbers = [int(x) for x in head.split(".")]
    return numbers, suffix == "", suffix


def entry_line(entry):
    return "{" + ", ".join(json.dumps(k) + ": " + json.dumps(v, ensure_ascii=False) for k, v in entry.items()) + "}"


def render(addons):
    out = ["{", '  "format": 1,', '  "source": "propwashfpv.com/addons/",', '  "addons": [']
    for a, addon in enumerate(addons):
        table = addon.get("table")
        head = {k: v for k, v in addon.items() if k != "table"}
        if table is None:
            out.append("    " + entry_line(head) + ("," if a + 1 < len(addons) else ""))
            continue
        out.append("    {" + ", ".join(json.dumps(k) + ": " + json.dumps(v, ensure_ascii=False) for k, v in head.items()) + ",")
        out.append('     "table": {"format": 1, "namespace": ' + json.dumps(table["namespace"])
                   + (', "short_id": ' + str(table["short_id"]) if "short_id" in table else "") + ', "parts": {')
        kinds = [k for k in KINDS if table["parts"].get(k)]
        for k, kind in enumerate(kinds):
            out.append("       " + json.dumps(kind) + ": [")
            entries = table["parts"][kind]
            for e, entry in enumerate(entries):
                out.append("         " + entry_line(entry) + ("," if e + 1 < len(entries) else ""))
            out.append("       ]" + ("," if k + 1 < len(kinds) else ""))
        out.append("     }}}" + ("," if a + 1 < len(addons) else ""))
    out.append("  ]")
    out.append("}")
    return "\n".join(out) + "\n"


def table_of(namespace, source):
    text = source.read(f"data/{namespace}/propwash_share/table.json")
    if text is None:
        raise SystemExit(f"{namespace}: keine Teile-Tabelle data/{namespace}/propwash_share/table.json in {source.path}")
    table = json.loads(text)
    if table.get("format") != 1 or table.get("namespace") != namespace:
        raise SystemExit(f"{namespace}: Tabelle mit falschem Format oder Namensraum")
    lang = {}
    for language in LANGUAGES:
        raw = source.read(f"assets/{namespace}/lang/{language}.json")
        lang[language] = json.loads(raw) if raw else {}
    parts = {}
    for kind in KINDS:
        entries = []
        for entry in table.get("parts", {}).get(kind, []):
            out = {"id": entry["id"], "since": entry["since"]}
            for key in ("deprecated", "replaced_by", "fallback"):
                if entry.get(key):
                    out[key] = entry[key]
            names = {}
            for language in LANGUAGES:
                name = lang[language].get(f"part.{namespace}.{entry['id']}")
                if name:
                    names[language] = name
            if names:
                out["name"] = names
            entries.append(out)
        if entries:
            parts[kind] = entries
    result = {"format": 1, "namespace": namespace}
    if table.get("short_id"):
        result["short_id"] = table["short_id"]
    result["parts"] = parts
    return result


def main():
    parser = argparse.ArgumentParser(description="Verzeichnis-Abzug für Propwash erzeugen")
    parser.add_argument("--site", help="assets/data/addons/directory.json der Website")
    parser.add_argument("--addon", action="append", default=[], help="<namensraum>=<jar oder ressourcen-ordner>")
    parser.add_argument("--version", action="append", default=[], help="<namensraum>=<version>, wenn der Jar keine nennt")
    parser.add_argument("--propwash-version", default="0.6.0")
    parser.add_argument("--out", default=str(OUT))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.site:
        site = json.loads(pathlib.Path(args.site).read_text(encoding="utf-8"))
        listed = []
        for addon in site.get("addons", []):
            if not addon.get("official"):
                continue
            entry = {"namespace": addon["namespace"], "short_id": addon["short_id"], "name": addon["name"]}
            for key in ("modrinth", "curseforge"):
                if addon.get(key) and not addon[key].startswith("<"):
                    entry[key] = addon[key]
            versions = [v["version"] for v in addon.get("versions", []) if v.get("version")]
            if versions:
                entry["version"] = max(versions, key=version_key)
            listed.append(entry)
    else:
        listed = [dict(a) for a in FIXED]
    ids = [a["short_id"] for a in listed]
    if len(ids) != len(set(ids)) or 1 not in ids or 2 not in ids:
        raise SystemExit("Kurzkennungen doppelt oder 1/2 fehlen")
    by_ns = {a["namespace"]: a for a in listed}
    if by_ns.get("propwash", {}).get("short_id") != 1 or by_ns.get("justmoreparts", {}).get("short_id") != 2:
        raise SystemExit("1 = propwash und 2 = justmoreparts sind fest")
    versions = dict(v.split("=", 1) for v in args.version)
    by_ns["propwash"]["version"] = args.propwash_version
    for spec in args.addon:
        namespace, path = spec.split("=", 1)
        if namespace not in by_ns:
            raise SystemExit(f"{namespace} steht nicht im Verzeichnis")
        source = Source(path)
        version = versions.get(namespace) or source.version()
        if version:
            by_ns[namespace]["version"] = version
        by_ns[namespace]["table"] = table_of(namespace, source)
    order = ["namespace", "short_id", "name", "modrinth", "curseforge", "version", "table"]
    addons = [{k: a[k] for k in order if k in a} for a in sorted(listed, key=lambda a: a["short_id"])]
    text = render(addons)
    json.loads(text)
    out = pathlib.Path(args.out)
    if args.check:
        current = out.read_text(encoding="utf-8") if out.is_file() else ""
        if current != text:
            print(f"Verzeichnis-Abzug weicht ab: {out}")
            return 1
        print("Verzeichnis-Abzug aktuell")
        return 0
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")
    print(f"geschrieben: {out} ({len(text.encode('utf-8'))} Bytes, {len(addons)} Add-ons)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
