#!/usr/bin/env python3
"""Check the published consumer contract, rather than only the source build."""
from pathlib import Path
import zipfile
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
aar = root / "smoothmarkdown/build/outputs/aar/smoothmarkdown-release.aar"
with zipfile.ZipFile(aar) as archive:
    text = archive.read("META-INF/com/android/build/gradle/aar-metadata.properties").decode()
properties = dict(line.split("=", 1) for line in text.splitlines() if "=" in line)
assert properties["minCompileSdk"] == "35", properties
assert properties["minAndroidGradlePluginVersion"] == "8.6.0", properties
poms = list((root / "build/central-staging/io/github/jackcaow/smooth-markdown").glob("*/*.pom"))
assert poms, "Publish the local Maven bundle first"
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
for pom in poms:
    document = ET.parse(pom)
    exported_boms = document.findall(".//m:dependency[m:artifactId='compose-bom']", ns)
    assert not exported_boms, f"Consumer Compose BOM leaked from {pom}"
print("Consumer metadata: SDK 35, AGP 8.6.0, no exported Compose BOM")
