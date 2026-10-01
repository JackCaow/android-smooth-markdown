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
            # Kotlin internal transports are JVM-public implementation classes, not consumer API.
            if re.match(r'^com\.jackcaow\.smoothmarkdown\.nativeparser\.(RustMarkdownBridge|RustMarkdownWire)(\$|$)', cls):
                continue
            if re.match(r'^com\.jackcaow\.smoothmarkdown\.StreamingMarkdown(Session|Document|SessionKt|Worker|WorkerKt|Request|Requests|Result|Backend|Publisher|Completion|Emission)(\$|$)', cls):
                continue
            classes.add(cls)
result = subprocess.check_output(['javap', '-public', '-classpath', ':'.join(map(str, jars)), *sorted(classes)], text=True)
lines = [line.rstrip() for line in result.splitlines() if not line.startswith('Compiled from')]

# Inspect class-file flags before ignoring compiler artifacts. A user-written public
# method named access$... remains API unless it actually has ACC_SYNTHETIC.
header = re.compile(r'^[^ ].*\b(?:class|interface) ([^\s<{]+)')

def internal_adapter_method(owner, line):
    # This explicitly internal Kotlin projection hook is hidden from Java by @JvmSynthetic.
    # Exclude it only after checking its class-file flag below, like generated accessors.
    return owner == 'com.jackcaow.smoothmarkdown.NativeMarkdownParser' and bool(
        re.search(r'\bconvertTree\$smoothmarkdown\(', line))

candidates = set()
current_class = None
for line in lines:
    match = header.match(line)
    if match:
        current_class = match.group(1)
        if current_class.endswith('$WhenMappings'):
            candidates.add(current_class)
    if current_class and (re.search(r'\baccess\$[^ (]+\(', line) or internal_adapter_method(current_class, line)):
        candidates.add(current_class)

synthetic_classes = set()
synthetic_accessors = set()
if candidates:
    verbose = subprocess.check_output(['javap', '-v', '-p', '-classpath', ':'.join(map(str, jars)), *sorted(candidates)], text=True)
    current_class = None
    class_synthetic = False
    pending_method = None
    for line in verbose.splitlines():
        if line.startswith('Classfile '):
            current_class = None
            class_synthetic = False
            pending_method = None
        if current_class is None and 'flags:' in line:
            class_synthetic = 'ACC_SYNTHETIC' in line
        match = re.search(r'this_class:.*// (\S+)', line)
        if match:
            current_class = match.group(1).replace('/', '.')
            if class_synthetic and current_class.endswith('$WhenMappings'):
                synthetic_classes.add(current_class)
        if line.startswith('  public ') and (re.search(r'\baccess\$[^ (]+\(', line) or internal_adapter_method(current_class, line)):
            pending_method = line.strip()
        elif pending_method and 'flags:' in line:
            if 'ACC_SYNTHETIC' in line:
                synthetic_accessors.add((current_class, pending_method))
            pending_method = None

# Pre-background compilation generated these closure state-getter bridges. Moving
# completion publication removed them; ordinals are compiler implementation details.
# The source declares only the two public StreamMarkdown overloads, no access$ methods.
# These tombstones apply ONLY to the historical baseline. A current user-written
# method with the same signature remains API unless its actual flag is ACC_SYNTHETIC.
historical_synthetic_accessors = {
    ('com.jackcaow.smoothmarkdown.StreamMarkdownKt',
     'public static final kotlin.jvm.functions.Function1 access$StreamMarkdown$lambda$4(androidx.compose.runtime.State);'),
    ('com.jackcaow.smoothmarkdown.StreamMarkdownKt',
     'public static final kotlin.jvm.functions.Function1 access$StreamMarkdown$lambda$11(androidx.compose.runtime.State);'),
}

def normalize_signatures(source, historical=False):
    normalized = []
    current_class = None
    skip_class = False
    for line in source:
        match = header.match(line)
        if match:
            current_class = match.group(1)
            skip_class = current_class in synthetic_classes
        if skip_class or (current_class, line.strip()) in synthetic_accessors or (
                historical and (current_class, line.strip()) in historical_synthetic_accessors):
            continue
        normalized.append(line)
    return '\n'.join(normalized) + '\n'

current = normalize_signatures(lines)
baseline = root/'api/public-jvm.txt'
if args.update:
    baseline.write_text(current)
    print('Public JVM signature baseline updated')
elif not baseline.exists() or normalize_signatures(baseline.read_text().splitlines(), historical=True) != current:
    old = normalize_signatures(baseline.read_text().splitlines(), historical=True).splitlines() if baseline.exists() else []
    print('\n'.join(difflib.unified_diff(old, current.splitlines(), fromfile='baseline', tofile='current')))
    raise SystemExit('Public API changed. Review compatibility before updating the baseline.')
else:
    print('Public JVM signatures match reviewed baseline (verified compiler artifacts excluded)')
