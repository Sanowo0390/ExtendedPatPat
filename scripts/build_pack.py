#!/usr/bin/env python3
"""Build a tiny, original vector/pixel hand glyph atlas. Requires Pillow only.

Bedrock glyph pages are 16x16 cells. U+E2A0 is row A, column 0 in glyph_E2.
The whole glyph_E2 page is replaced: merge the atlas if another pack owns it.
"""
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'dist'
PACK = ROOT / 'resource-pack'
(PACK / 'font').mkdir(parents=True, exist_ok=True)
(ROOT / 'art').mkdir(exist_ok=True)
OUT.mkdir(exist_ok=True)

# Palm facing down, fingertips to the left, wrist to the right.
outline = [(14,4),(10,4),(8,3),(4,3),(4,4),(2,4),(2,5),(1,5),(1,7),
           (2,7),(2,9),(3,9),(3,10),(5,10),(5,11),(7,11),(8,10),
           (10,10),(11,9),(14,9)]
points = ' '.join(f'{x},{y}' for x,y in outline)
(ROOT / 'art' / 'hand.svg').write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" shape-rendering="crispEdges">'
    f'<polygon points="{points}" fill="#fff4e6" stroke="#54473c" stroke-width="1"/>'
    '<path d="M3 6H7 M4 8H8 M6 10H9" fill="none" stroke="#d1ae90"/>'
    '<path d="M12 4V9" stroke="#84bca0" stroke-width="2"/></svg>', encoding='utf-8')

atlas = Image.new('RGBA', (256,256))
frames = []
for index, dy in enumerate((0,1,2,1)):
    im = Image.new('RGBA', (16,16))
    d = ImageDraw.Draw(im)
    d.polygon([(x,y+dy) for x,y in outline], fill='#fff4e6', outline='#54473c')
    d.line([(3,6+dy),(7,6+dy)], fill='#d1ae90')
    d.line([(4,8+dy),(8,8+dy)], fill='#d1ae90')
    d.line([(6,10+dy),(9,10+dy)], fill='#d1ae90')
    d.rectangle((12,4+dy,13,9+dy), fill='#84bca0')
    if index == 2:
        d.line([(3,14),(6,14)], fill='#a8dec1')
        d.point((2,13), fill='#a8dec1'); d.point((7,13), fill='#a8dec1')
    atlas.alpha_composite(im, (index*16,10*16))
    frames.append(im.resize((128,128), Image.Resampling.NEAREST))
atlas.save(PACK / 'font' / 'glyph_E2.png')
manifest = {
  'format_version': 2,
  'header': {'name':'ExtendedPatPat Bedrock Hands', 'description':'ExtendedPatPat: animated hand glyphs E2A0-E2A3',
             'uuid':'1da1e03c-6367-490b-8e54-870226bc57fa','version':[0,1,0], 'min_engine_version':[1,21,0]},
  'modules':[{'type':'resources','uuid':'f3ac7ed9-ee8b-42bd-aa6f-b7540d18ce31','version':[0,1,0]}]
}
(PACK / 'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
icon = Image.new('RGBA',(128,128),'#214e3e')
icon.alpha_composite(frames[0]); icon.save(PACK/'pack_icon.png')
with ZipFile(OUT / 'PatPatBedrock-0.1.0.mcpack','w',ZIP_DEFLATED) as z:
    for path in sorted(PACK.rglob('*')):
        if path.is_file(): z.write(path,path.relative_to(PACK))
frames[0].save(OUT/'hand-preview.gif',save_all=True,append_images=frames[1:],duration=100,loop=0,disposal=2)
print('Built',OUT / 'PatPatBedrock-0.1.0.mcpack')
