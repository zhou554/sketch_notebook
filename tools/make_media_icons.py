from PIL import Image
from pathlib import Path

SOURCES = [
    (
        Path(r"C:\Users\18215\.cursor\projects\d-cursor-peoject-ACursor-sketch\assets\c__Users_18215_AppData_Roaming_Cursor_User_workspaceStorage_3b459b758b7fd91f89c99dc6a3ee6fd5_images_image-957386c1-af8a-4950-8d1a-d6e06a246109.png"),
        "ic_note_camera.png",
    ),
    (
        Path(r"C:\Users\18215\.cursor\projects\d-cursor-peoject-ACursor-sketch\assets\c__Users_18215_AppData_Roaming_Cursor_User_workspaceStorage_3b459b758b7fd91f89c99dc6a3ee6fd5_images_image-c085238d-e581-4eed-85bc-41b77eeee895.png"),
        "ic_note_gallery.png",
    ),
]

OUT_DIRS = [
    Path(r"D:\cursor_peoject\.ACursor\sketch\sketch_notebook\app\src\main\res\drawable"),
    Path(r"D:\cursor_peoject\.ACursor\sketch\sketch_notebook\preview\images\stickers"),
    Path(r"D:\cursor_peoject\.ACursor\sketch\images\stickers"),
]


def make_transparent(src: Path, dst: Path, threshold: int = 245) -> None:
    im = Image.open(src).convert("RGBA")
    pixels = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = pixels[x, y]
            if r >= threshold and g >= threshold and b >= threshold:
                pixels[x, y] = (r, g, b, 0)
            elif r >= 230 and g >= 230 and b >= 230:
                brightness = (r + g + b) / 3
                alpha = int(max(0, min(255, (255 - brightness) * 8)))
                pixels[x, y] = (r, g, b, alpha)
    bbox = im.getbbox()
    if bbox:
        im = im.crop(bbox)
    max_edge = 128
    scale = min(max_edge / im.width, max_edge / im.height, 1.0)
    if scale < 1.0:
        im = im.resize(
            (max(1, int(im.width * scale)), max(1, int(im.height * scale))),
            Image.Resampling.LANCZOS,
        )
    dst.parent.mkdir(parents=True, exist_ok=True)
    im.save(dst, "PNG")
    print(f"{dst} {im.size}")


def main() -> None:
    for src, name in SOURCES:
        if not src.exists():
            raise SystemExit(f"missing: {src}")
        for out_dir in OUT_DIRS:
            make_transparent(src, out_dir / name)


if __name__ == "__main__":
    main()
