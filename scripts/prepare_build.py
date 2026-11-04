#!/usr/bin/env python3
"""Download and SHA-256-verify the upstream Geyser Velocity build used for compiling.

GEYSER_VERSION=2.12.1, GEYSER_BUILD=latest (or 1280, 1284, ...).
The dependency is 'provided' and never embedded into the extension.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
VERSION=os.environ.get("GEYSER_VERSION","2.12.1").strip()
BUILD=os.environ.get("GEYSER_BUILD","latest").strip()
if not re.fullmatch(r"\d+\.\d+\.\d+",VERSION):
    raise SystemExit("Invalid GEYSER_VERSION")
if BUILD != "latest" and not re.fullmatch(r"[0-9]+",BUILD):
    raise SystemExit("Invalid GEYSER_BUILD")

BASE=f"https://download.geysermc.org/v2/projects/geyser/versions/{VERSION}"
with urllib.request.urlopen(f"{BASE}/builds/{BUILD}",timeout=90) as response:
    info=json.load(response)
number=str(info["build"])
downloads=info["downloads"]
download=downloads.get("velocity")
if not download:
    download=next((v for k,v in downloads.items() if
        "velocity" in k.lower() or "velocity" in v.get("name","").lower()),None)
if not download:
    raise SystemExit(f"No Geyser-Velocity artifact for {VERSION}-b{number}")
expected=download["sha256"]
if not re.fullmatch(r"[a-f0-9]{64}",expected):
    raise SystemExit("Geyser metadata did not include a valid SHA-256")

source=ROOT/".deps"/f"Geyser-{VERSION}-b{number}-Velocity.jar"
source.parent.mkdir(parents=True,exist_ok=True)
if not source.exists() or hashlib.sha256(source.read_bytes()).hexdigest()!=expected:
    tmp=source.with_suffix(".download")
    try:
        with urllib.request.urlopen(f"{BASE}/builds/{number}/downloads/velocity",timeout=180) as response,tmp.open("wb") as output:
            shutil.copyfileobj(response,output,1024*1024)
        if hashlib.sha256(tmp.read_bytes()).hexdigest()!=expected:
            raise SystemExit("Geyser artifact SHA-256 mismatch")
        tmp.replace(source)
    finally:
        tmp.unlink(missing_ok=True)
local_version="2.12.1-compat"
directory=ROOT/".deps/repository/org/geysermc/local/geyser-velocity"/local_version
directory.mkdir(parents=True,exist_ok=True)
jar=directory/f"geyser-velocity-{local_version}.jar"
pom=directory/f"geyser-velocity-{local_version}.pom"
shutil.copyfile(source,jar)
pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>org.geysermc.local</groupId><artifactId>geyser-velocity</artifactId><version>'+local_version+'</version></project>\n')
for file in (jar,pom):
    file.with_suffix(file.suffix+".sha1").write_text(hashlib.sha1(file.read_bytes()).hexdigest())
(ROOT/".deps/compiled-geyser-version.txt").write_text(f"{VERSION}-b{number}\n")
print(f"Verified Geyser {VERSION}-b{number} (SHA256: {expected[:12]}...)")
