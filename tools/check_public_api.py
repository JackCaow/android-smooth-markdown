#!/usr/bin/env python3
"""Compare public JVM signatures; update only after an intentional API review."""
import argparse, difflib, re, subprocess, zipfile
from pathlib import Path
root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--update', action='store_true')
args = parser.parse_args()
jars = list((root/'smoothmarkdown/build/intermediates/compile_library_classes_jar/debug').glob('**/classes.jar'))
jars += list((root/'smoothmarkdown-core/build/libs').glob('*.jar'))
jars = [p for p in jars if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
if len(jars) != 2:
    raise SystemExit('Build :smoothmarkdown:assembleDebug and :smoothmarkdown-core:jar first')
classes = set()
for jar in jars:
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if not name.endswith('.class') or not name.startswith('com/jackcaow/smoothmarkdown/'):
                continue
            cls = name[:-6].replace('/', '.')
            # Anonymous closures and generated Compose singleton classes are implementation details.
            if re.search(r'\$\d|\$.*\$|Kt\$|ComposableSingletons', cls):
                continue
            classes.add(cls)
result = subprocess.check_output(['javap', '-public', '-classpath', ':'.join(map(str, jars)), *sorted(classes)], text=True)
lines = [line.rstrip() for line in result.splitlines() if not line.startswith('Compiled from')]
current = '\n'.join(lines) + '\n'
baseline = root/'api/public-jvm.txt'
if args.update:
    baseline.write_text(current)
    print('Public JVM signature baseline updated')
elif not baseline.exists() or baseline.read_text() != current:
    old = baseline.read_text().splitlines() if baseline.exists() else []
    print('\n'.join(difflib.unified_diff(old, current.splitlines(), fromfile='baseline', tofile='current')))
    raise SystemExit('Public API changed. Review compatibility before updating the baseline.')
else:
    print('Public JVM signatures match reviewed baseline')
