import argparse
import gzip
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

MAGIC = b'H Product:Blackbox flight data recorder'
MAX_DOWNLOAD = 64 * 2 ** 20
MAX_MEMBER = 256 * 2 ** 20


def log(msg):
    print(msg, flush=True)


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 20), b''):
            h.update(chunk)
    return h.hexdigest()


def download(url, target):
    tmp = target + '.part'
    req = urllib.request.Request(url, headers={'User-Agent': 'propwash-bbcal'})
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, 'wb') as f:
        total = 0
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_DOWNLOAD:
                raise RuntimeError('Download größer als 64 MB')
            f.write(chunk)
    os.replace(tmp, target)


def safe_name(name):
    return re.sub(r'[^\w.-]', '_', name.replace('\\', '/').split('/')[-1])


def log_file(raw_dir, archive, member):
    with open(archive, 'rb') as f:
        head = f.read(len(MAGIC))
    if head == MAGIC:
        return archive
    if not zipfile.is_zipfile(archive):
        raise RuntimeError('weder Blackbox-Log noch ZIP')
    out_dir = os.path.join(raw_dir, 'x')
    os.makedirs(out_dir, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for info in z.infolist():
            if info.is_dir() or info.file_size > MAX_MEMBER:
                continue
            name = safe_name(info.filename)
            if member and name != safe_name(member):
                continue
            target = os.path.join(out_dir, name)
            if not os.path.exists(target):
                with z.open(info) as src, open(target, 'wb') as dst:
                    shutil.copyfileobj(src, dst, 1 << 20)
            with open(target, 'rb') as f:
                if f.read(len(MAGIC)) == MAGIC:
                    return target
    raise RuntimeError('Log im Archiv nicht gefunden: ' + (member or '?'))


def decode(decoder, source, index, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    work = os.path.join(out_dir, 'work')
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work)
    link = os.path.join(work, 'log.bbl')
    shutil.copyfile(source, link)
    proc = subprocess.run([decoder, '--index', str(index), '--unit-vbat', 'raw', '--unit-amperage', 'raw', '--unit-flags', 'raw', '--merge-gps', '--save-headers', 'log.bbl'],
                          cwd=work, capture_output=True, text=True, timeout=600)
    stats = proc.stderr.strip().splitlines()
    csv = os.path.join(work, 'log.%02d.csv' % index)
    headers = os.path.join(work, 'log.%02d.headers.csv' % index)
    if not os.path.exists(csv) or not os.path.exists(headers):
        raise RuntimeError('blackbox_decode ohne Ausgabe: ' + ' / '.join(stats[-3:]))
    with open(csv, 'rb') as a, gzip.open(os.path.join(out_dir, 'log.csv.gz'), 'wb', compresslevel=3) as b:
        shutil.copyfileobj(a, b, 1 << 20)
    shutil.move(headers, os.path.join(out_dir, 'log.headers.csv'))
    with open(os.path.join(out_dir, 'decode.txt'), 'w') as f:
        f.write('\n'.join(stats) + '\n')
    shutil.rmtree(work, ignore_errors=True)
    return [s for s in stats if 'frames failed' in s or 'missing in total' in s]


def main():
    ap = argparse.ArgumentParser(description='Lädt die öffentlichen Logs aus sources.json in den Cache und dekodiert sie.')
    ap.add_argument('--sources', required=True)
    ap.add_argument('--cache', required=True)
    ap.add_argument('--decoder', required=True)
    ap.add_argument('--only', default='')
    args = ap.parse_args()
    with open(args.sources, encoding='utf-8') as f:
        sources = json.load(f)['logs']
    only = {x for x in args.only.split(',') if x}
    status = 0
    for s in sources:
        if only and s['id'] not in only:
            continue
        out_dir = os.path.join(args.cache, 'decoded', s['id'])
        if os.path.exists(os.path.join(out_dir, 'log.csv.gz')) and os.path.exists(os.path.join(out_dir, 'log.headers.csv')):
            log('%-26s im Cache' % s['id'])
            continue
        raw_dir = os.path.join(args.cache, 'raw', s['sha256'][:16])
        os.makedirs(raw_dir, exist_ok=True)
        archive = os.path.join(raw_dir, safe_name(s['url'].rsplit('/', 1)[-1]))
        try:
            if not os.path.exists(archive):
                download(s['url'], archive)
            digest = sha256(archive)
            if digest != s['sha256']:
                os.remove(archive)
                raise RuntimeError('Prüfsumme weicht ab (%s)' % digest[:12])
            source = log_file(raw_dir, archive, s.get('member', ''))
            notes = decode(args.decoder, source, s.get('index', 1), out_dir)
            log('%-26s dekodiert %s' % (s['id'], '; '.join(notes)))
        except Exception as e:
            status = 1
            log('%-26s FEHLER %s' % (s['id'], e))
    sys.exit(status)


if __name__ == '__main__':
    main()
