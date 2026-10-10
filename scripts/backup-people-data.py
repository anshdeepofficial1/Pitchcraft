#!/usr/bin/env python3
"""
Non-destructive local backup for a debuggable Pitchcraft installation.
Usage: python scripts/backup-people-data.py --package com.anshdeep.pitchcraft.debug
Requires Android platform-tools (adb). Never uninstalls, clears data, or uploads files.
Stops the app first for SQLite consistency; run when face scanning is idle.
"""
import argparse
import datetime
from pathlib import Path
import subprocess
import sys
import tarfile


def adb(*args, **kwargs):
    return subprocess.run(["adb", *args], check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--package",
        choices=["com.anshdeep.pitchcraft.debug", "com.anshdeep.pitchcraft"],
        default="com.anshdeep.pitchcraft.debug",
    )
    parser.add_argument("--output", type=Path, help="Local tar backup path")
    args = parser.parse_args()

    default_dir = Path.home() / "Downloads"
    if not default_dir.is_dir():
        default_dir = Path.home()
    timestamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    output = args.output or default_dir / f"Pitchcraft-private-backup-{timestamp}.tar"
    output = output.expanduser().resolve()
    if output.exists():
        sys.exit(f"Refusing to overwrite an existing backup: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)

    adb("get-state", stdout=subprocess.DEVNULL)
    packages = adb("shell", "pm", "list", "packages", args.package, capture_output=True, text=True).stdout
    if f"package:{args.package}" not in packages.splitlines():
        sys.exit(f"App {args.package} is not installed; no changes made.")

    probe = subprocess.run(
        ["adb", "shell", "run-as", args.package, "id"],
        capture_output=True, text=True,
    )
    if probe.returncode != 0:
        sys.exit(
            "Android denied run-as. The installed APK may not be debuggable. "
            "No data was changed, and no backup was created. Detail: " + probe.stderr.strip()
        )

    # Force-stop only: flush outstanding SQLite writes and close DB WAL safely.
    # This does NOT uninstall or clear any app data.
    print("Stopping Pitchcraft temporarily so its local SQLite backup is consistent.")
    adb("shell", "am", "force-stop", args.package)

    paths = []
    for name in ("databases", "files", "shared_prefs", "cache", "no_backup"):
        p = subprocess.run(["adb", "shell", "run-as", args.package, "ls", "-d", name],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if p.returncode == 0:
            paths.append(name)
    if "databases" not in paths:
        sys.exit("No database directory present; refusing to produce a misleading backup.")

    try:
        with output.open("xb") as out:
            result = subprocess.run(
                ["adb", "exec-out", "run-as", args.package, "tar", "-cf", "-", *paths],
                stdout=out, stderr=subprocess.PIPE,
            )
        if result.returncode != 0:
            raise RuntimeError(f"ADB tar export failed: {result.stderr.decode(errors='replace')}")
        with tarfile.open(output, "r:") as archive:
            members = {m.name.lstrip("./") for m in archive.getmembers()}
            required = "databases/pitchcraft_gallery_v1.db"
            if required not in members:
                raise RuntimeError(f"Expected People database {required} was not in the backup")
            print(f"Backup contains {len(members)} entries including {required}.")
        print(f"SUCCESS. Local-only backup: {output}")
        print("KEEP THE INSTALLED APP. Do NOT uninstall until recovery is tested.")
    except Exception as exc:
        output.unlink(missing_ok=True)
        sys.exit(f"Backup failed and incomplete output was removed: {exc}")


if __name__ == "__main__":
    main()
