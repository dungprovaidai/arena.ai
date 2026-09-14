"""Build a scaled contact sheet of a texture folder so the art can be reviewed at a glance."""
import os, sys, math
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixelart import upscale

def sheet(folder, out, scale=6, cols=10, cell_pad=8, label=True):
    files = sorted(f for f in os.listdir(folder) if f.endswith('.png'))
    if not files: raise SystemExit('no textures in '+folder)
    size = 16*scale + cell_pad*2
    rows = math.ceil(len(files)/cols)
    img = Image.new('RGBA', (cols*size, rows*size), (58,54,62,255))
    d = ImageDraw.Draw(img)
    for i,f in enumerate(files):
        t = Image.open(os.path.join(folder,f)).convert('RGBA')
        t = upscale(t, scale)
        cx, cy = (i%cols)*size+cell_pad, (i//cols)*size+cell_pad
        # checker so transparency reads
        for yy in range(0, t.height, 12):
            for xx in range(0, t.width, 12):
                if (xx//12+yy//12)%2==0:
                    d.rectangle([cx+xx,cy+yy,cx+xx+11,cy+yy+11], fill=(78,74,84,255))
        img.alpha_composite(t, (cx,cy))
        if label:
            d.text((cx, cy+16*scale+1), f[:-4][:18], fill=(232,226,238))
    img.save(out)
    print(f'{out}  ({len(files)} sprites, {img.width}x{img.height})')

if __name__ == '__main__':
    sheet(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv)>3 else 6)
