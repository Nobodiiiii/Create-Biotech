"""Build a portable 16px voxel preview without modifying game assets."""
from pathlib import Path
from PIL import Image
import base64
import json
import math
import random
import re
import subprocess
import sys
import shutil

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parents[1]
SOURCE = PROJECT / 'src/main/resources/assets/create_biotech/textures/block'
INLINE = Path('C:/Users/Nobodiiiii/.codex/visualizations/2026/09/16/01a0aa46-dd9f-70c2-88f1-c77bc01e2366/frog-stomach-3d.html')
PALETTES = {
    'wall': ['#8d4853','#944e59','#a15660','#a85d65'],
    'mucosa':['#b86b76','#c57a82','#ce868b','#d68e93'],
    'fold':['#884956','#955461','#a35f6c','#aa6b74'],
    'fold_top':['#884956','#955461','#a35f6c','#aa6b74'],
    'stem':['#b9a280','#c4ae8e','#d1bf9f','#dacbad'],
    'stem_top':['#b7a080','#c4ae8d','#d1c0a0','#dcccad'],
    'cap':['#a45257','#b96161','#c76c69','#d17b73'],
    'gill':['#aa926e','#bfab83','#d3c298','#e1d1ac'],
    'light':['#658e7a','#83b69c','#b0d8b7','#d8e9ca'],
    'secretion':['#a97028','#ba8431','#d1a248','#e0b65d'],
    'water':['#a77a29','#b58b32','#c3993f','#cda64a'],
    'frame':['#80464d','#945358','#a96263','#bd7873'],
    'portal':['#995635','#b2703c','#c89451','#d6b476'],
    'slime':['#69a448','#7eb757','#95c56a','#acda7c'],
    'eye':['#2c4732','#2c4732','#385539','#385539'],
    'shirt':['#3e8f94','#429ba2','#429ba2','#52a6ad'],
    'pants':['#484e75','#555c84','#555c84','#636b90'],
    'skin':['#b79074','#c7a184','#c7a184','#d4ad90'],
}

def texture(name, colors):
    rng = random.Random(932 + sum(map(ord,name)))
    tile = Image.new('RGBA',(16,16))
    # Coherent two-pixel patches, not high-frequency photo noise.
    coarse = [[rng.randrange(4) for _ in range(8)] for _ in range(8)]
    for y in range(16):
        for x in range(16):
            v = coarse[y//2][x//2]
            if name in ('wall','mucosa'): v = min(3,max(0,1+(v==3)-(v==0)))
            if name in ('fold','stem'): v = int((math.sin(x*.85+math.sin(y*.4)) + 1)*1.45)
            if name=='gill': v=3 if x%4<2 else 1
            if name=='stem_top': v=int(max(abs(x-7.5),abs(y-7.5)))%4
            if name=='cap': v=min(3,max(0,(v+1)//2 + (y<2)))
            if name=='water': v=(coarse[(y//4)%8][x//4]+1)//2
            if name=='secretion':
                edge=min(x,y,15-x,15-y)
                v=2 if edge==0 else 1
                if (x,y) in ((2,2),(3,2),(2,3),(9,6),(10,6)):v=3
                if 10<x<14 and 10<y<14:v=0
            if name=='light': v=3 if 4<x<11 and 3<y<12 else min(2,v)
            if name=='portal': v=int((math.sin(x*.4+math.sin(y*.4)) + 1)*1.45)
            rgb=tuple(bytes.fromhex(colors[v][1:]))
            tile.putpixel((x,y),rgb+(255,))
    return tile

original_names = {
    'wall':'frog_stomach_wall', 'mucosa':'frog_stomach_mucosa',
    'fold':'frog_stomach_fold', 'fold_top':'frog_stomach_fold_top', 'stem':'frog_stomach_fungus_stem',
    'stem_top':'frog_stomach_fungus_stem_top', 'cap':'frog_stomach_fungus_cap',
    'gill':'frog_stomach_fungus_stem_top', 'light':'frog_stomach_fungus_light',
    'secretion':'frog_stomach_secretion', 'frame':'frog_digestive_tract_wall_side',
}
data = {'concept':{},'original':{}}
manifest = {'size':[16,16], 'filter':'NearestFilter', 'mipmaps':False, 'textures':[]}
for name, colors in PALETTES.items():
    concept = texture(name,colors)
    source = SOURCE/(original_names[name]+'.png') if name in original_names else None
    original = Image.open(source).convert('RGBA') if source else concept.copy()
    if name in ('fold','fold_top'):
        concept = original.copy()
    assert original.size==(16,16), (source,original.size)
    for palette,img in [('concept',concept),('original',original)]:
        folder=HERE/'textures'/palette
        folder.mkdir(parents=True,exist_ok=True)
        img.save(folder/(name+'.png'))
        data[palette][name]=base64.b64encode(img.tobytes()).decode('ascii')
    manifest['textures'].append({'name':name,'size':[16,16],'original_source':str(source.relative_to(PROJECT)) if source else 'Preview-only procedural texture; same in both palettes'})

(HERE/'texture-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
javac = Path(shutil.which('javac'))
java = javac.with_name('java.exe' if sys.platform=='win32' else 'java')
geometry_classes = HERE/'.geometry-classes'
geometry_classes.mkdir(exist_ok=True)
geometry_source = PROJECT/'src/main/java/com/nobodiiiii/createbiotech/content/frogportal/FrogStomachFoldGeometry.java'
subprocess.run([str(javac),'-d',str(geometry_classes),str(geometry_source),str(HERE/'FoldGeometryExport.java')],check=True)
folds = subprocess.check_output([str(java),'-cp',str(geometry_classes),'com.nobodiiiii.createbiotech.content.frogportal.FoldGeometryExport'],text=True)
folds = json.loads(folds)
three=(HERE/'node_modules/three/build/three.min.js').read_text(encoding='utf-8')
fragment=(HERE/'preview.html').read_text(encoding='utf-8')
fragment=fragment.replace('<!--THREE_LIBRARY-->','<script>\n'+three+'\n</script>')
fragment=fragment.replace('/*TEXTURE_DATA*/','window.FROG_TEXTURES='+json.dumps(data,separators=(',',':'))+';\nwindow.FROG_FOLD_DEPTHS='+json.dumps(folds,separators=(',',':'))+';')
fragment=fragment.replace('/*PREVIEW_SCRIPT*/',(HERE/'preview.js').read_text(encoding='utf-8'))
assert not re.search(r'<!doctype\s|<\s*(?:html|head|body)(?:\s|>)',fragment,re.I)
assert len(fragment.encode('utf-8'))<1_000_000
INLINE.parent.mkdir(parents=True,exist_ok=True)
INLINE.write_text(fragment,encoding='utf-8')
render=Path('C:/Users/Nobodiiiii/.codex/plugins/cache/openai-bundled/visualize/1.0.37/skills/visualize/scripts/render.py')
standalone=HERE/'preview-standalone.html'
subprocess.run([sys.executable,str(render),str(INLINE),str(standalone),'--force'],check=True)
# This preview uses neither icons nor tooltips. Remove the wrapper's optional CDN helpers.
document=standalone.read_text(encoding='utf-8')
document=re.sub(r'&lt;script[^\n]*?src=&quot;https://unpkg\.com/[^\n]*?&lt;/script&gt;','',document)
standalone.write_text(document,encoding='utf-8')
print(json.dumps({'inline':str(INLINE),'bytes':len(fragment.encode('utf-8')),'textures':len(PALETTES)*2,'dimensions':[48,48,32]}))
