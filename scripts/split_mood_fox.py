# -*- coding: utf-8 -*-
"""把 6 宫格狐狸表情图裁成 6 张透明底 PNG，输出到安卓 drawable 与网页预览目录。"""
from PIL import Image
from collections import deque
import os

SRC = r"C:\Users\18215\AppData\Roaming\Qoder\SharedClientCache\cache\images\a5e0d964\pntv3vre-735dfa61.png"
OUT_ANDROID = r"d:\Qoder_project\note_sketch\app\src\main\res\drawable"
OUT_PREVIEW = r"d:\Qoder_project\note_sketch\preview\images\stickers"

COLS, ROWS = 2, 3


def is_bg(p):
    r, g, b = p[0], p[1], p[2]
    # 白色 / 浅灰阴影：亮度高且近似灰
    return r >= 158 and g >= 158 and b >= 158 and (max(r, g, b) - min(r, g, b)) <= 32


def remove_bg(cell):
    cell = cell.convert("RGBA")
    px = cell.load()
    w, h = cell.size
    visited = [[False] * h for _ in range(w)]
    q = deque()
    for x in range(w):
        for y in (0, h - 1):
            if not visited[x][y] and is_bg(px[x, y]):
                visited[x][y] = True
                q.append((x, y))
    for y in range(h):
        for x in (0, w - 1):
            if not visited[x][y] and is_bg(px[x, y]):
                visited[x][y] = True
                q.append((x, y))
    while q:
        x, y = q.popleft()
        p = px[x, y]
        px[x, y] = (p[0], p[1], p[2], 0)
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not visited[nx][ny] and is_bg(px[nx, ny]):
                visited[nx][ny] = True
                q.append((nx, ny))
    return cell


def drop_edge_fragments(cell):
    """删除接触格子边界且面积很小的不透明连通块（邻格狐狸溢出的残片）。"""
    px = cell.load()
    w, h = cell.size
    labels = [[0] * h for _ in range(w)]
    comps = []  # (pixels, touches_edge)
    nid = 0
    total = 0
    for sx in range(w):
        for sy in range(h):
            if px[sx, sy][3] > 0 and labels[sx][sy] == 0:
                nid += 1
                pixels = []
                touches = False
                q = deque([(sx, sy)])
                labels[sx][sy] = nid
                while q:
                    x, y = q.popleft()
                    pixels.append((x, y))
                    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                        touches = True
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        if 0 <= nx < w and 0 <= ny < h and labels[nx][ny] == 0 and px[nx, ny][3] > 0:
                            labels[nx][ny] = nid
                            q.append((nx, ny))
                comps.append((pixels, touches))
                total += len(pixels)
    for pixels, touches in comps:
        if touches and len(pixels) < total * 0.2:
            for x, y in pixels:
                p = px[x, y]
                px[x, y] = (p[0], p[1], p[2], 0)
    return cell


def main():
    img = Image.open(SRC).convert("RGBA")
    w, h = img.size
    print("source size:", w, h)
    os.makedirs(OUT_ANDROID, exist_ok=True)
    os.makedirs(OUT_PREVIEW, exist_ok=True)
    idx = 0
    for r in range(ROWS):
        for c in range(COLS):
            idx += 1
            box = (c * w // COLS, r * h // ROWS, (c + 1) * w // COLS, (r + 1) * h // ROWS)
            cell = remove_bg(img.crop(box))
            cell = drop_edge_fragments(cell)
            bbox = cell.getbbox()
            if bbox:
                m = 4  # 留一点边距
                bbox = (max(0, bbox[0] - m), max(0, bbox[1] - m),
                        min(cell.width, bbox[2] + m), min(cell.height, bbox[3] + m))
                cell = cell.crop(bbox)
            a_path = os.path.join(OUT_ANDROID, "mood_fox_%d.png" % idx)
            p_path = os.path.join(OUT_PREVIEW, "mood-fox-%d.png" % idx)
            cell.save(a_path)
            cell.save(p_path)
            print("saved", idx, cell.size, "->", a_path)


if __name__ == "__main__":
    main()
