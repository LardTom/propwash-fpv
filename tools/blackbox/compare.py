import csv
import sys

TOLERANCE = {
    'vbatLatest': 0.0051,
    'amperageLatest': 0.0051,
}
SKIP = {'axisD[0]', 'axisD[1]', 'axisD[2]', 'rssi'}


def load(path):
    with open(path, newline='') as f:
        rows = list(csv.reader(f))
    header = [h.strip().split(' (')[0] for h in rows[0]]
    return header, [[c.strip() for c in r] for r in rows[1:] if r]


def number(text):
    try:
        return float(text)
    except ValueError:
        return None


SCALE = {}


def main(decoded_path, truth_path, label):
    dh, drows = load(decoded_path)
    th, trows = load(truth_path)
    index = {name: i for i, name in enumerate(dh)}
    missing = [name for name in th if name not in index]
    if missing:
        print(f'{label}: Spalten fehlen im Decoder: {missing}')
        return 1
    decoded = {int(float(r[index['loopIteration']])): r for r in drows}
    errors = 0
    worst = {}
    for row in trows:
        n = int(float(row[0]))
        got = decoded.get(n)
        if got is None:
            errors += 1
            if errors < 10:
                print(f'{label}: Frame {n} fehlt')
            continue
        for k, name in enumerate(th):
            if name in SKIP:
                continue
            expected = float(row[k])
            actual = number(got[index[name]])
            if actual is not None and name in SCALE:
                actual *= SCALE[name]
            if actual is None:
                errors += 1
                continue
            tol = TOLERANCE.get(name, 0.5000001)
            diff = abs(actual - expected)
            worst[name] = max(worst.get(name, 0.0), diff)
            if diff > tol:
                errors += 1
                if errors < 10:
                    print(f'{label}: Frame {n} {name}: erwartet {expected}, gelesen {actual}')
    extra = len(decoded) - len(trows)
    print(f'{label}: {len(trows)} Frames verglichen, {len(th)} Felder, Fehler {errors}, zusätzliche Frames {extra}')
    print(f'{label}: größte Abweichung je Feld: ' + ', '.join(f'{k}={v:.4f}' for k, v in sorted(worst.items())))
    return 1 if errors or extra else 0


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--raw-battery' in sys.argv:
        SCALE['vbatLatest'] = 0.01
        SCALE['amperageLatest'] = 0.01
    sys.exit(main(args[0], args[1], args[2] if len(args) > 2 else args[0]))
