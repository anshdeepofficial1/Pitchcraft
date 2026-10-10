#!/usr/bin/env python3
"""
Restore a locally backed-up Pitchcraft People database into an ALREADY INSTALLED
and debuggable app. This does NOT uninstall, install, or clear anything.

Only use after verifying the backup and deciding how to handle an incompatible
signing key on your own phone. Some AndroidKeyStore-backed secrets do NOT survive
an uninstall; this cannot fully preserve encrypted vault data.
Usage: python scripts/restore-people-data.py --backup FILE.tar --package com.anshdeep.pitchcraft.debug
"""
import argparse
from pathlib import Path
import sqlite3
import subprocess
import sys
import tarfile
import tempfile


def call(*args, **kwargs):
    return subprocess.run(["adb", *args], check=True, **kwargs)


def verify_database_backup(path):
    with tarfile.open(path, "r:") as tf:
        members = tf.getmembers()
        names = {member.name.lstrip("./"): member for member in members}
        needed = "databases/pitchcraft_gallery_v1.db"
        if needed not in names:
            raise ValueError("Backup has no Pitchcraft People database; refusing to restore")
        for member in members:
            name = member.name.lstrip("./")
            pieces = Path(name).parts
            if member.name.startswith("/") or ".." in pieces or not (member.isfile() or member.isdir()):
                raise ValueError("Unsafe archive member: " + member.name)
        with tempfile.TemporaryDirectory() as tmp:
            for key in [needed, needed + "-wal", needed + "-shm"]:
                member = names.get(key)
                if member is None:
                    continue
                dest = Path(tmp) / key
                dest.parent.mkdir(parents=True, exist_ok=True)
                with tf.extractfile(member) as src, dest.open("wb") as dst:
                    if src is None:
                        raise ValueError("Unreadable database backup")
                    while True:
                        chunk = src.read(1024 * 1024)
                        if not chunk:
                            break
                        dst.write(chunk)
            uri = (Path(tmp) / needed).as_uri() + "?mode=ro"
            with sqlite3.connect(uri, uri=True) as db:
                row = db.execute("PRAGMA integrity_check").fetchone()
                if not row or row[0] != "ok":
                    raise ValueError("SQLite integrity check failed: " + repr(row))
                tables = {x[0] for x in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
                if "faces" not in tables or "person_clusters" not in tables:
                    raise ValueError("Backup lacks People tables; refusing to restore")
                clusters = db.execute("SELECT COUNT(*) FROM person_clusters").fetchone()[0]
                faces = db.execute("SELECT COUNT(*) FROM faces").fetchone()[0]
                version = db.execute("PRAGMA user_version").fetchone()[0]
                print(f"Verified backup: SQLite schema v{version}, {clusters} People groups, {faces} face records.")
                return faces, clusters


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--package", choices=["com.anshdeep.pitchcraft.debug", "com.anshdeep.pitchcraft"],
                        required=True)
    parser.add_argument("--restore", action="store_true", help="Explicitly perform restoration; without this, validate only")
    args = parser.parse_args()

    backup = args.backup.expanduser().resolve()
    if not backup.is_file():
        sys.exit("Backup file not found")
    try:
        verify_database_backup(backup)
    except (tarfile.TarError, OSError, sqlite3.DatabaseError, ValueError) as e:
        sys.exit("Backup validation failed; no changes made: " + str(e))
    if not args.restore:
        print("READ-ONLY check succeeded. To restore to an already-installed debug app, add --restore.")
        return

    call("get-state", stdout=subprocess.DEVNULL)
    installed = call("shell", "pm", "list", "packages", args.package, capture_output=True, text=True).stdout
    if "package:" + args.package not in installed.splitlines():
        sys.exit("Target app not installed. No changes made.")

    test = subprocess.run(["adb", "shell", "run-as", args.package, "id"],
                          capture_output=True, text=True)
    if test.returncode:
        sys.exit("Target app is not debuggable / run-as unavailable. No changes made.")
    # Re-run integrity verification before mutating any data.
    verify_database_backup(backup)
    print("Stopping target app. Restoring its private app files from local backup.")
    call("shell", "am", "force-stop", args.package)
    with backup.open("rb") as data:
        result = subprocess.run(["adb", "exec-in", "run-as", args.package, "tar", "-xf", "-"],
                                stdin=data, capture_output=True)
    if result.returncode:
        sys.exit("Restore failed; do NOT launch until resolved. " + result.stderr.decode(errors="replace"))
    print("RESTORED. Data was written to the new app. Verify People names and groups after launch.")
    print("Caution: AndroidKeyStore secrets do not necessarily survive uninstall/reinstall.")


if __name__ == "__main__":
    main()
