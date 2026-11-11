# -*- coding: utf-8 -*-
"""
高质量图片压缩：把 frontend/public/img 里的大 PNG 压到适合 Web 的体积。
- 最长边超过 MAX_SIDE 的等比缩小到 MAX_SIDE（LANCZOS）
- 256 色量化 + 抖动（对 3D 渲染/贴纸类图片肉眼几乎无差别）
- 保留透明通道；小于 MIN_BYTES 的文件跳过
用法: python scripts/compress_images.py
"""
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
IMG_DIR = ROOT / "frontend" / "public" / "img"
MAX_SIDE = 800
MIN_BYTES = 150 * 1024  # 只处理 >150KB 的文件

try:
    Image.quantize  # noqa: B018
    QUANT_METHOD = Image.Quantize.FASTOCTREE  # 支持 RGBA；libimagequant 未编译时用这个
except AttributeError:  # pragma: no cover
    QUANT_METHOD = Image.Quantize.FASTOCTREE


def process(path: Path) -> tuple[int, int]:
    old = path.stat().st_size
    img = Image.open(path)
    has_alpha = img.mode in ("RGBA", "LA", "P") and (
        img.mode != "P" or "transparency" in img.info
    )
    img = img.convert("RGBA" if has_alpha else "RGB")
    w, h = img.size
    if max(w, h) > MAX_SIDE:
        scale = MAX_SIDE / max(w, h)
        img = img.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
    if has_alpha:
        img = img.quantize(colors=256, method=QUANT_METHOD, dither=Image.Dither.FLOYDSTEINBERG)
    else:
        img = img.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
    img.save(path, optimize=True)
    return old, path.stat().st_size


def main() -> None:
    if not IMG_DIR.exists():
        sys.exit(f"目录不存在: {IMG_DIR}")
    total_old = total_new = count = 0
    for p in sorted(IMG_DIR.glob("*.png")):
        if p.stat().st_size <= MIN_BYTES:
            continue
        old, new = process(p)
        total_old += old
        total_new += new
        count += 1
        print(f"{p.name}: {old/1024:.0f}KB -> {new/1024:.0f}KB")
    print(f"\n共 {count} 张: {total_old/1048576:.1f}MB -> {total_new/1048576:.1f}MB")


if __name__ == "__main__":
    main()
