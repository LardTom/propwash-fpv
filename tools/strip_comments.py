import sys
import pathlib


def strip(source):
    out = []
    i = 0
    n = len(source)
    while i < n:
        c = source[i]
        if source.startswith('"""', i):
            end = source.find('"""', i + 3)
            while end != -1 and source[end - 1] == '\\':
                end = source.find('"""', end + 1)
            end = n if end == -1 else end + 3
            out.append(source[i:end])
            i = end
        elif c == '"' or c == "'":
            j = i + 1
            while j < n and source[j] != c and source[j] != '\n':
                j += 2 if source[j] == '\\' else 1
            out.append(source[i:j + 1])
            i = j + 1
        elif source.startswith('//', i):
            j = source.find('\n', i)
            i = n if j == -1 else j
        elif source.startswith('/*', i):
            j = source.find('*/', i + 2)
            j = n if j == -1 else j + 2
            out.append('\n' * source.count('\n', i, j))
            i = j
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def tidy(original, stripped):
    old = original.split('\n')
    new = stripped.split('\n')
    kept = []
    for index, line in enumerate(new):
        before = old[index] if index < len(old) else ''
        if line.strip() == '' and before.strip() != '':
            continue
        kept.append(line.rstrip())
    cleaned = []
    for line in kept:
        if line == '':
            if not cleaned or cleaned[-1] == '' or cleaned[-1].endswith('{'):
                continue
        elif line.lstrip().startswith('}') and cleaned and cleaned[-1] == '':
            cleaned.pop()
        cleaned.append(line)
    return '\n'.join(cleaned).rstrip('\n') + '\n'


def main(paths):
    changed = 0
    for root in paths:
        for path in pathlib.Path(root).rglob('*.java'):
            if '.ref' in path.parts or 'src' not in path.parts:
                continue
            original = path.read_text(encoding='utf-8')
            result = tidy(original, strip(original))
            if result != original:
                path.write_text(result, encoding='utf-8')
                changed += 1
    print(f'{changed} Dateien bereinigt')


if __name__ == '__main__':
    main(sys.argv[1:] or ['common/src', 'fabric/src', 'neoforge/src'])
