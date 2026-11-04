#!/usr/bin/env python3
"""Generate a Java 26.3 pack and a shared skin index from PatPat Mod packs.
Place any *.zip (PatPat resource pack), *.png (horizontal sprite strip), or
*.gif in resourcepack/skins/. ZIPs are inspected, never extracted to disk.
"""
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path
import hashlib, json, re, zipfile, os
from PIL import Image, ImageSequence

ROOT=Path(__file__).resolve().parents[1]
SKINS=ROOT/"resourcepack/skins"
OUT=ROOT/"resourcepack/dist"
SOURCE=ROOT/"src/main/resources/default-hand.png"
try:
    import json5
except ImportError:
    json5=None

@dataclass
class Skin:
    id:str
    label:str
    frames:list
    duration:int=300
    scale_x:float=1
    scale_y:float=1
    offset_x:float=0
    offset_y:float=0
    offset_z:float=0
    sound:bytes|None=None
    volume:float=0.65
    pitch_min:float=0.9
    pitch_max:float=1.1

def slug(s):
    result=re.sub(r'[^a-z0-9_-]+','_',s.lower()).strip('_')
    if not result or len(result)>36: raise ValueError("Invalid skin name: "+s)
    return result

def decode_json5(raw):
    text=raw.decode('utf-8-sig')
    try: return json.loads(text)
    except json.JSONDecodeError:
        if json5 is None: raise ValueError("Install json5 to read JSON5 skin configs")
        return json5.loads(text)

def bounded(d,key,default,low,high):
    try: n=float(d.get(key,default))
    except (TypeError,ValueError): return default
    return n if low<=n<=high else default

def decode_frames(data,frames=None):
    if len(data)>25_000_000: raise ValueError("Image too large")
    with Image.open(BytesIO(data)) as im:
        if max(im.size)>8192: raise ValueError("Image dimensions too large")
        if im.format=="GIF":
            images=[f.convert('RGBA') for f in ImageSequence.Iterator(im)]
        else:
            n=frames or (im.width//im.height if im.width%im.height==0 else 1)
            if n<1 or im.width%n: raise ValueError("Incorrect horizontal frame layout")
            w=im.width//n
            images=[im.crop((i*w,0,(i+1)*w,im.height)).convert('RGBA') for i in range(n)]
    if not 1<=len(images)<=48: raise ValueError("Expected 1..48 animation frames")
    return images

def archive(path):
    with zipfile.ZipFile(path) as z:
        infos=z.infolist()
        if len(infos)>256 or sum(f.file_size for f in infos)>25_000_000:
            raise ValueError("Archive exceeds maximum size")
        names=[f.filename for f in infos if not f.is_dir()]
        config=next((n for n in names if n.endswith('/textures/config.json5')),None)
        if config is None: config=next((n for n in names if n.endswith('config.json5')),None)
        cfg=decode_json5(z.read(config)) if config else {}
        anim=cfg.get('animation',{})
        shape=anim.get('frame',{})
        candidate=str(anim.get('texture','')).replace('patpat:','assets/patpat/')
        if candidate not in names:
            matches=[n for n in names if n.lower().endswith(('.png','.gif')) and 'textures/' in n]
            if not matches: matches=[n for n in names if n.lower().endswith(('.png','.gif')) and not n.endswith('pack.png')]
            if not matches: raise ValueError("Archive contains no usable sprite image")
            candidate=next((n for n in matches if 'animation' in n.lower()),matches[0])
        count=shape.get('totalFrames')
        frames=decode_frames(z.read(candidate),int(count) if count is not None else None)
        id=slug(path.stem)
        sound=None
        sound_defs=next((n for n in names if n.endswith('/sounds.json')),None)
        entries=decode_json5(z.read(sound_defs)) if sound_defs else {}
        snd=anim.get('sound',{})
        key=str(snd.get('id','')).split(':')[-1]
        for entry in entries.get(key,{}).get('sounds',[]):
            value=entry if isinstance(entry,str) else entry.get('name','')
            filepath='assets/patpat/sounds/'+value.split(':')[-1]+'.ogg'
            if filepath in names:
                sound=z.read(filepath)
                break
        return Skin(id,path.stem,frames,
            duration=int(bounded(anim,'duration',len(frames)*50,50,10000)),
            scale_x=bounded(shape,'scaleX',1,0.1,8),
            scale_y=bounded(shape,'scaleY',1,0.1,8),
            offset_x=bounded(shape,'offsetX',0,-4,4),
            offset_y=bounded(shape,'offsetY',0,-4,4),
            offset_z=bounded(shape,'offsetZ',0,-4,4),
            sound=sound,
            volume=bounded(snd,'volume',0.65,0,4),
            pitch_min=bounded(snd,'min_pitch',0.9,0.1,4),
            pitch_max=bounded(snd,'max_pitch',1.1,0.1,4))

def j(data):
    return (json.dumps(data,ensure_ascii=False,indent=2)+'\n').encode('utf-8')

def image_bytes(im):
    # Generated vanilla item textures are square; pad non-square sprite frames.
    side=max(im.size)
    dst=Image.new('RGBA',(side,side),(0,0,0,0))
    dst.alpha_composite(im,((side-im.width)//2,(side-im.height)//2))
    io=BytesIO()
    dst.save(io,format='PNG',optimize=True)
    return io.getvalue()


# Minecraft Java map palette: 61 standard base colours, 4 brightness variants
# (180, 220, 255, 135)/255. Transparent map pixels use color ID 0.
# Colors are protocol data, not third-party artwork.
MAP_BASES=[
    (0,0,0),(127,178,56),(247,233,163),(199,199,199),(255,0,0),
    (160,160,255),(167,167,167),(0,124,0),(255,255,255),(164,168,184),
    (151,109,77),(112,112,112),(64,64,255),(143,119,72),(255,252,245),
    (216,127,51),(178,76,216),(102,153,216),(229,229,51),(127,204,25),
    (242,127,165),(76,76,76),(153,153,153),(76,127,153),(127,63,178),
    (51,76,178),(102,76,51),(102,127,51),(153,51,51),(25,25,25),
    (250,238,77),(92,219,213),(74,128,255),(0,217,58),(129,86,49),
    (112,2,0),(209,177,161),(159,82,36),(149,87,108),(112,108,138),
    (186,133,36),(103,117,53),(160,77,78),(57,41,35),(135,107,98),
    (87,92,92),(122,73,88),(76,62,92),(76,50,35),(76,82,42),
    (142,60,46),(37,22,16),(189,48,49),(148,63,97),(92,25,29),
    (22,126,134),(58,142,140),(86,44,62),(20,180,133),(100,100,100),
    (216,175,147),(127,167,150)
]
MAP_PALETTE=[(base_id*4+shade,tuple(v*factor//255 for v in rgb))
             for base_id,rgb in enumerate(MAP_BASES) if base_id
             for shade,factor in enumerate((180,220,255,135))]
def map_pixels(frame):
    # Alpha threshold preserves the original transparent hand silhouette.
    # Pixel-art interpolation is disabled to avoid introducing muddy edges.
    side=max(frame.size)
    canvas=Image.new('RGBA',(side,side),(0,0,0,0))
    canvas.alpha_composite(frame,((side-frame.width)//2,(side-frame.height)//2))
    canvas=canvas.resize((128,128),resample=Image.Resampling.NEAREST)
    lut={}
    data=bytearray(128*128)
    for i,(red,green,blue,alpha) in enumerate(canvas.getdata()):
        if alpha<128:continue
        color=(red,green,blue)
        choice=lut.get(color)
        if choice is None:
            choice=min(MAP_PALETTE,key=lambda item:
                2*(item[1][0]-red)**2 + 4*(item[1][1]-green)**2
                + 3*(item[1][2]-blue)**2)[0]
            lut[color]=choice
        data[i]=choice
    return bytes(data)

def main():
    with Image.open(SOURCE) as im:
        rgba=im.convert('RGBA')
        b=BytesIO();rgba.save(b,format='PNG')
        first=Skin('default','Default',decode_frames(b.getvalue(),5))
    styles=[first]
    seen={'default'}
    if SKINS.exists() and os.environ.get("PATPAT_DEFAULT_ONLY","0")!="1":
        for path in sorted(SKINS.iterdir()):
            if path.name.startswith('.') or not path.is_file(): continue
            if path.suffix.lower()=='.zip':
                skin=archive(path)
            elif path.suffix.lower() in ('.png','.gif'):
                skin=Skin(slug(path.stem),path.stem,decode_frames(path.read_bytes()))
            else: continue
            if skin.id in seen: raise ValueError('Duplicate skin ID: '+skin.id)
            seen.add(skin.id);styles.append(skin)
    if len(styles)>32: raise ValueError('Too many skin packs')
    pack={'pack.mcmeta':j({'pack':{
        'description':'ExtendedPatPat animated hands | Minecraft Java 26.3',
        'pack_format':46,
        'supported_formats':{'min_inclusive':46,'max_inclusive':97},
        'min_format':46,'max_format':97}})}
    index=['# Generated: both plugins embed this exact manifest.',
        'skins='+','.join(s.id for s in styles)]
    sounds={}
    for s in styles:
        prefix='skin.'+s.id+'.'
        values={'label':s.label,'frames':len(s.frames),'duration-ms':s.duration,
            'scale-x':s.scale_x,'scale-y':s.scale_y,'offset-x':s.offset_x,
            'offset-y':s.offset_y,'offset-z':s.offset_z,
            'sound-volume':s.volume,'sound-pitch-min':s.pitch_min,'sound-pitch-max':s.pitch_max}
        if s.sound:
            sounds['skin_'+s.id]={'sounds':['patpat:skin_'+s.id]}
            pack['assets/patpat/sounds/skin_'+s.id+'.ogg']=s.sound
            values['sound']='patpat:skin_'+s.id
        for key,value in values.items(): index.append(prefix+key+'='+str(value))
        for n,frame in enumerate(s.frames):
            model='hand_'+s.id+'_'+str(n)
            pack['assets/patpat/textures/item/'+model+'.png']=image_bytes(frame)
            pack['assets/patpat/models/item/'+model+'.json']=j({
                'parent':'minecraft:item/generated',
                'textures':{'layer0':'patpat:item/'+model}})
            pack['assets/patpat/items/'+model+'.json']=j({
                'model':{'type':'minecraft:model','model':'patpat:item/'+model}})
            if s.id=='default':
                legacy='hand_'+str(n)
                pack['assets/patpat/models/item/'+legacy+'.json']=pack['assets/patpat/models/item/'+model+'.json']
                pack['assets/patpat/items/'+legacy+'.json']=pack['assets/patpat/items/'+model+'.json']
    # Java map animation: one 128x128-byte palette frame per source frame.
    # This does not require a URL, resource pack or client mod.
    for style in styles:
        directory=OUT/'maps'/style.id
        directory.mkdir(parents=True,exist_ok=True)
        for i,frame in enumerate(style.frames):
            payload=map_pixels(frame)
            assert len(payload)==16384
            (directory/(str(i)+'.bin')).write_bytes(payload)
    print('Compiled '+str(sum(len(s.frames) for s in styles))+
          ' map frames (128x128), no client resource pack required')
    if sounds: pack['assets/patpat/sounds.json']=j(sounds)
    OUT.mkdir(parents=True,exist_ok=True)
    dest=OUT/'PatPatJava-26.3.zip'
    # Fixed timestamps/order: identical assets yield byte-identical ZIP and SHA-1
    # across the independently built Velocity and Paper artifacts.
    with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        for name,data in sorted(pack.items()):
            info=zipfile.ZipInfo(name,date_time=(2020,1,1,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr=(0o100644 << 16)
            out.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
    with zipfile.ZipFile(dest) as check:
        assert check.testzip() is None
        for s in styles:
            for n in range(len(s.frames)):
                assert 'assets/patpat/items/hand_'+s.id+'_'+str(n)+'.json' in check.namelist()
    (OUT/'patpat-skins.properties').write_text('\n'.join(index)+'\n',encoding='utf-8')
    sha=hashlib.sha1(dest.read_bytes()).hexdigest()
    (OUT/'PatPatJava-26.3.zip.sha1').write_text(sha+'  '+dest.name+'\n')
    print('Packed '+', '.join(s.id+'('+str(len(s.frames))+' frames)' for s in styles)+
          ' | zip='+str(dest.stat().st_size)+' bytes | sha1='+sha)

if __name__=='__main__':
    main()
