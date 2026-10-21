"""Produce a small contact sheet of real Robolectric renders for connector-based visual review."""
import base64
from pathlib import Path
from io import BytesIO
from PIL import Image, ImageOps, ImageDraw

names = ['01_main_menu', '02_world_map', '03_chapter_1', '20_collection', '21_shop', '22_premium',
         '23_settings', '24_daily', '27_achievements', '30_ar_menu', '31_ar_shop', '32_ar_games']
root = Path('app/build/screenshots')
sheet = Image.new('RGB', (4 * 240, 3 * 550), '#102b32')
for i, name in enumerate(names):
    path = root / (name + '.png')
    if not path.exists(): continue
    image = Image.open(path).convert('RGB')
    image.thumbnail((236, 520))
    x, y = i % 4 * 240, i // 4 * 550
    sheet.paste(image, (x + (240 - image.width) // 2, y + 25))
    ImageDraw.Draw(sheet).text((x + 4, y + 4), name, fill='white')
buffer = BytesIO()
sheet.save(buffer, format='JPEG', quality=80)
(root/'review.jpg').write_bytes(buffer.getvalue())
print('VISUAL_REVIEW_BASE64=' + base64.b64encode(buffer.getvalue()).decode())
