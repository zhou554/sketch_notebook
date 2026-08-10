"""Remove solid white paper background from tomato sticker."""
from PIL import Image
import os

src = r"C:\Users\18215\.cursor\projects\d-cursor-peoject-ACursor-sketch\assets\c__Users_18215_AppData_Roaming_Cursor_User_workspaceStorage_empty-window_images_image-e7f78398-6884-4a2f-923d-6f2f1bb3e337.png"
out_android = r"D:\cursor_peoject\.ACursor\sketch\sketch_notebook\app\src\main\res\drawable\tomato_fanqie.png"
out_preview = r"D:\cursor_peoject\.ACursor\sketch\sketch_notebook\preview\images\stickers\tomato_fanqie.png"

os.makedirs(os.path.dirname(out_android), exist_ok=True)
os.makedirs(os.path.dirname(out_preview), exist_ok=True)

im = Image.open(src).convert("RGBA")
w, h = im.size
pixels = im.load()


def is_bg(r, g, b):
    if r >= 248 and g >= 248 and b >= 248:
        return True
    if r >= 240 and g >= 240 and b >= 240 and abs(r - g) <= 6 and abs(g - b) <= 6:
        return True
    # soft paper fringe
    if min(r, g, b) >= 230 and max(r, g, b) - min(r, g, b) <= 10:
        return True
    return False


# Flood-fill from edges so interior white (highlights) stays
visited = [[False] * w for _ in range(h)]
stack = []
for x in range(w):
    stack.append((x, 0))
    stack.append((x, h - 1))
for y in range(h):
    stack.append((0, y))
    stack.append((w - 1, y))

while stack:
    x, y = stack.pop()
    if x < 0 or y < 0 or x >= w or y >= h or visited[y][x]:
        continue
    visited[y][x] = True
    r, g, b, a = pixels[x, y]
    if not is_bg(r, g, b):
        # soft edge: fade near-white pixels adjacent to bg
        if r > 220 and g > 220 and b > 220 and max(r, g, b) - min(r, g, b) < 18:
            whiteness = (r + g + b) / 3.0
            alpha = int(max(0, min(255, (245 - whiteness) * 12)))
            pixels[x, y] = (r, g, b, alpha)
        continue
    pixels[x, y] = (r, g, b, 0)
    stack.extend(((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)))

bbox = im.getbbox()
if bbox:
    pad = 6
    im = im.crop(
        (
            max(0, bbox[0] - pad),
            max(0, bbox[1] - pad),
            min(w, bbox[2] + pad),
            min(h, bbox[3] + pad),
        )
    )

im.save(out_android, "PNG")
im.save(out_preview, "PNG")
print("size", im.size, "mode", im.mode)
print("saved", out_android)
print("saved", out_preview)
