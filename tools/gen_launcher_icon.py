from PIL import Image, ImageDraw
from pathlib import Path

src_path = Path(
    r"C:\Users\18215\.cursor\projects\d-cursor-peoject-ACursor-sketch\assets"
    r"\c__Users_18215_AppData_Roaming_Cursor_User_workspaceStorage_3b459b758b7fd91f89c99dc6a3ee6fd5_images_image-ea5b2fd2-c79d-4977-81fa-42384031cd4c.png"
)
res = Path(r"D:\cursor_peoject\.ACursor\sketch\sketch_notebook\app\src\main\res")

src = Image.open(src_path).convert("RGBA")


def make_icon(size: int, round_mask: bool = False) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), (246, 240, 228, 255))  # #F6F0E4
    art_size = max(1, int(size * 0.86))
    art = src.resize((art_size, art_size), Image.Resampling.LANCZOS)
    off = (size - art_size) // 2
    canvas.alpha_composite(art, (off, off))
    if round_mask:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(canvas, (0, 0))
        out.putalpha(mask)
        return out
    return canvas


densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in densities.items():
    d = res / folder
    d.mkdir(parents=True, exist_ok=True)
    make_icon(size, False).save(d / "ic_launcher.png", optimize=True)
    make_icon(size, True).save(d / "ic_launcher_round.png", optimize=True)
    print("wrote", folder, size)

fg_dir = res / "drawable"
fg_dir.mkdir(parents=True, exist_ok=True)
make_icon(432, False).save(fg_dir / "ic_launcher_foreground.png", optimize=True)
print("wrote foreground")
print("done")
