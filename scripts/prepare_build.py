#!/usr/bin/env python3
"""Download the exact provided compile dependency; never include it in the extension JAR."""
from pathlib import Path
import hashlib
import urllib.request
import shutil

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'.deps'/'Geyser-Velocity-b1280.jar'
SHA='fb936077d44a4f765e0905119e1fa841b8c8c01148290711410367f0a38e3edb'
URL='https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/1280/downloads/velocity'
if not DEST.exists() or hashlib.sha256(DEST.read_bytes()).hexdigest()!=SHA:
    DEST.parent.mkdir(parents=True,exist_ok=True)
    temp=DEST.with_suffix('.download')
    with urllib.request.urlopen(URL,timeout=180) as response, temp.open('wb') as out:
        while data:=response.read(1024*1024):out.write(data)
    if hashlib.sha256(temp.read_bytes()).hexdigest()!=SHA:
        temp.unlink();raise SystemExit('Geyser SHA-256 mismatch')
    temp.replace(DEST)
repo=ROOT/'.deps/repository/org/geysermc/local/geyser-velocity/2.12.1-b1280'
repo.mkdir(parents=True,exist_ok=True)
jar=repo/'geyser-velocity-2.12.1-b1280.jar'
shutil.copyfile(DEST,jar)
pom=repo/'geyser-velocity-2.12.1-b1280.pom'
pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>org.geysermc.local</groupId><artifactId>geyser-velocity</artifactId><version>2.12.1-b1280</version></project>\n')
for path in (jar,pom):
    path.with_suffix(path.suffix+'.sha1').write_text(hashlib.sha1(path.read_bytes()).hexdigest())
print('Verified Geyser 2.12.1-b1280 compile dependency')
