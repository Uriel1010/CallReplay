#!/usr/bin/env python3
"""Generate release assets from the actual signed APK, never a claimed version."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

apk = Path(sys.argv[1]).resolve()
sdk = Path(os.environ["ANDROID_HOME"])
aapt = sdk / "build-tools/36.0.0/aapt"
badging = subprocess.check_output([aapt, "dump", "badging", apk], text=True)
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
minimum = re.search(r"sdkVersion:'(\d+)'", badging)
if not package or not minimum or "application-debuggable" in badging:
    raise SystemExit("Expected a non-debuggable APK with package metadata")
if package[1] != "io.github.uriel1010.callreplay" or not apk.name.endswith("-release.apk"):
    raise SystemExit("Unexpected package or release filename")
digest = hashlib.file_digest(apk.open("rb"), "sha256").hexdigest()
manifest = dict(schemaVersion=1, packageName=package[1], versionCode=int(package[2]),
                versionName=package[3], minSdk=int(minimum[1]), apk=apk.name,
                sha256=digest, size=apk.stat().st_size)
(apk.parent / "callreplay-update.json").write_text(json.dumps(manifest, indent=2) + "\n")
(apk.parent / "SHA256SUMS").write_text(f"{digest}  {apk.name}\n")
print(json.dumps(manifest, indent=2))
