"""Draws the Control Vehicles icon: a blue rounded square with a white car and an amber fuel drop.

Run from the repository root (needs Pillow, and ImageMagick's `convert` for the .ico):

    python3 scripts/draw_icon.py

It writes src/main/resources/com/alejandro/controlvehicles/ui/icons/icon-<size>.png and app.ico.
"""
import pathlib
import subprocess

from PIL import Image, ImageDraw

SIZE = 1024
BLUE_TOP = (47, 129, 247)
BLUE_BOTTOM = (22, 88, 196)
WHITE = (255, 255, 255)
DARK = (29, 39, 51)
AMBER = (245, 166, 35)
OUT = pathlib.Path("src/main/resources/com/alejandro/controlvehicles/ui/icons")
SIZES = [16, 24, 32, 48, 64, 128, 256]


def draw() -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # Fondo: cuadrado redondeado con un degradado vertical.
    gradient = Image.new("RGBA", (SIZE, SIZE))
    pixels = ImageDraw.Draw(gradient)
    for y in range(SIZE):
        t = y / (SIZE - 1)
        colour = tuple(round(a + (b - a) * t) for a, b in zip(BLUE_TOP, BLUE_BOTTOM))
        pixels.line([(0, y), (SIZE, y)], fill=colour + (255,))
    mask = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, SIZE - 1, SIZE - 1], radius=220, fill=255)
    image.paste(gradient, (0, 0), mask)

    d = ImageDraw.Draw(image)
    # Coche de perfil: carrocería, cabina con ventanillas y dos ruedas.
    d.rounded_rectangle([120, 520, 900, 740], radius=90, fill=WHITE)
    d.polygon([(270, 540), (370, 330), (650, 330), (790, 540)], fill=WHITE)
    d.rounded_rectangle([340, 310, 680, 420], radius=60, fill=WHITE)
    d.polygon([(330, 520), (400, 380), (500, 380), (500, 520)], fill=BLUE_TOP)
    d.polygon([(540, 380), (630, 380), (720, 520), (540, 520)], fill=BLUE_TOP)
    for cx in (300, 720):
        d.ellipse([cx - 110, 640, cx + 110, 860], fill=DARK)
        d.ellipse([cx - 50, 700, cx + 50, 800], fill=(190, 198, 208))

    # Gota de combustible arriba a la derecha.
    d.ellipse([700, 190, 900, 390], fill=AMBER)
    d.polygon([(720, 270), (800, 90), (880, 270)], fill=AMBER)
    d.ellipse([750, 260, 790, 300], fill=(255, 220, 150))
    return image


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    big = draw()
    files = []
    for size in SIZES:
        path = OUT / f"icon-{size}.png"
        big.resize((size, size), Image.LANCZOS).save(path, optimize=True)
        files.append(str(path))
    subprocess.run(["convert", *files, str(OUT / "app.ico")], check=True)


if __name__ == "__main__":
    main()
