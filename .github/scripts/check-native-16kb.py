#!/usr/bin/env python3
"""Fail CI if any bundled 64-bit Android native library lacks 16 KiB ELF LOAD alignment."""
import pathlib
import re
import shutil
import subprocess
import sys
import zipfile

if len(sys.argv) != 2:
    sys.exit("Usage: check-native-16kb.py <apk>")
apk = pathlib.Path(sys.argv[1])
readelf = shutil.which("readelf")
if not apk.is_file() or not readelf:
    sys.exit("Missing APK or binutils readelf")
checked = 0
bad = []
with zipfile.ZipFile(apk) as archive:
    for member in archive.infolist():
        name = member.filename
        if not name.endswith(".so") or not (name.startswith("lib/arm64-v8a/") or name.startswith("lib/x86_64/")):
            continue
        checked += 1
        # Read ELF program headers from the actual ZIP bytes, rather than assuming AAR support.
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / pathlib.Path(name).name
            path.write_bytes(archive.read(member))
            result = subprocess.run([readelf, "-lW", str(path)], text=True, capture_output=True)
        if result.returncode != 0:
            bad.append((name, "invalid ELF: " + result.stderr.strip()))
            continue
        alignments = []
        for line in result.stdout.splitlines():
            tokens = line.strip().split()
            if tokens and tokens[0] == "LOAD":
                try:
                    alignments.append(int(tokens[-1], 16))
                except ValueError:
                    bad.append((name, "unrecognized LOAD alignment: " + line))
        if not alignments:
            bad.append((name, "no loadable ELF segments"))
            continue
        minimum = min(alignments)
        print(f"{name}: minimum ELF LOAD alignment = {minimum} bytes")
        if minimum < 16384:
            bad.append((name, f"ELF LOAD alignment {minimum} < 16384"))
print(f"Examined {checked} 64-bit native libraries from {apk}")
if checked == 0:
    sys.exit("No 64-bit native .so present: cannot verify runtime native compatibility.")
if bad:
    for name, detail in bad:
        print(f"ERROR: {name}: {detail}", file=sys.stderr)
    sys.exit("FAILED: This APK cannot claim native 16 KiB compatibility.")
print("PASS: Every included 64-bit native library has 16 KiB or greater ELF LOAD alignment.")
