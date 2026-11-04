#!/usr/bin/env python3
"""Build the common Java hand asset pack from the project's own 5-frame PNG.

Supported target: Java 26.3 (resource pack 97.1); requires Pillow.
This produces models patpat:hand_0...hand_4 for ItemDisplay. Each animation
frame is advanced by the server, not by a globally asynchronous texture timer.
"""
from pathlib import Path
from PIL import Image
import hashlib
import io
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"src/main/resources/default-hand.png"
OUTPUT=ROOT/"resourcepack/dist/PatPatJava-26.3.zip"
FRAMES=5

def encoded(value):
    return (json.dumps(value,ensure_ascii=False,indent=2)+"\n").encode("utf-8")

def generate():
    OUTPUT.parent.mkdir(parents=True,exist_ok=True)
    with Image.open(SOURCE) as src:
        rgba=src.convert("RGBA")
    if rgba.width!=rgba.height*FRAMES:
        raise ValueError(f"Expected 5-square-frame horizontal PNG, got {rgba.size}")
    files={
        "pack.mcmeta":encoded({"pack":{
            "description":"PatPat: Animated hand for Java Edition 26.3",
            "min_format":[97,1],"max_format":97
        }}),
    }
    w=rgba.height
    for n in range(FRAMES):
        frame=rgba.crop((n*w,0,(n+1)*w,w))
        pic=io.BytesIO()
        frame.save(pic,format="PNG",optimize=True)
        files[f"assets/patpat/textures/item/hand_{n}.png"]=pic.getvalue()
        files[f"assets/patpat/models/item/hand_{n}.json"]=encoded({
            "parent":"minecraft:item/generated",
            "textures":{"layer0":f"patpat:item/hand_{n}"},
            "display":{"fixed":{"rotation":[0,0,0],"translation":[0,0,0],
                "scale":[1,1,1]}}
        })
        files[f"assets/patpat/items/hand_{n}.json"]=encoded({
            "model":{"type":"minecraft:model","model":f"patpat:item/hand_{n}"}
        })
    with zipfile.ZipFile(OUTPUT,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        for name in sorted(files):
            out.writestr(name,files[name])
    with zipfile.ZipFile(OUTPUT) as pack:
        assert pack.testzip() is None
        assert len(pack.namelist())==16
        assert json.loads(pack.read("pack.mcmeta"))["pack"]["min_format"]==[97,1]
        for n in range(FRAMES):
            with Image.open(io.BytesIO(pack.read(f"assets/patpat/textures/item/hand_{n}.png"))) as image:
                assert image.size==(w,w)
    sha=hashlib.sha1(OUTPUT.read_bytes()).hexdigest()
    (OUTPUT.parent/(OUTPUT.name+".sha1")).write_text(sha+"  "+OUTPUT.name+"\n")
    print(f"Built {OUTPUT} | 5 frames, {w}x{w} | SHA1={sha} | {OUTPUT.stat().st_size} bytes")

if __name__=="__main__":
    generate()
