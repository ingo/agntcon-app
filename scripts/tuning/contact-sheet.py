"""Before/after contact sheet: contact-sheet.py <out.jpg> <image>... (needs Pillow)."""
import sys, subprocess, os
from pathlib import Path
# contact sheet via sips + python PIL if available
try:
    from PIL import Image, ImageDraw, ImageOps
except ImportError:
    print("no PIL"); sys.exit(1)
files = sys.argv[2:]; out = sys.argv[1]
W = 240; cols = 6
rows = (len(files) + cols - 1) // cols
sheet = Image.new('RGB', (cols * W, rows * (W * 3 // 4 + 14)), 'white')
d = ImageDraw.Draw(sheet)
for i, f in enumerate(files):
    im = ImageOps.exif_transpose(Image.open(f)); im.thumbnail((W - 4, W * 3 // 4 - 4))
    x = (i % cols) * W; y = (i // cols) * (W * 3 // 4 + 14)
    sheet.paste(im, (x + 2, y + 2)); d.text((x + 2, y + W * 3 // 4), f"{i} {Path(f).name[:22]}", fill='black')
sheet.save(out, quality=80)
