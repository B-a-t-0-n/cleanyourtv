#!/usr/bin/env python3
"""Copy repository themes into a new, standalone offline catalogue directory."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

REPO = Path(__file__).resolve().parents[1]
NAMES = {
    "stockholm": "Стокгольм", "aerial": "Орбита · Aerial",
    "ambient-glass": "Пейзажи · Ambient Glass", "aurora": "Аврора",
    "blocky": "Пиксельный мир", "christmas": "Рождество", "cinematic": "Кинотеатр",
    "default": "Стандартная", "diwali": "Дивали", "dock-glass": "Стеклянный док",
    "dussehra": "Дуссехра", "family": "Семья", "glass": "Стекло",
    "glass-gallery": "Стеклянная галерея", "halloween": "Хэллоуин",
    "istanbul": "Стамбул", "istanbul_v2": "Стамбул II", "minimal": "Минимализм",
    "nature-calm": "Спокойная природа", "red-rain": "Красный код", "retro": "Ретро",
    "ridgelines": "Горные хребты", "ruska": "Осенняя Финляндия", "scenes": "Сцены",
    "six-columns": "Шесть колонок", "storm": "Гроза", "sweden": "Швеция",
    "twilight": "Сумерки", "weeb": "Неон",
}


def wanted(path):
    if len(path.parts) == 1:
        return path.name in {"config.json", "screenshot.jpg", "wallpaper.jpg",
                             "CREDITS.md", "LICENSE", "LICENSE.txt"}
    return (len(path.parts) == 2 and path.parts[0] == "wallpapers"
            and (path.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}
                 or path.name == "captions.txt"))


def write_json(path, value):
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def package(output):
    sources = sorted((p for p in (REPO / "themes").iterdir() if (p / "config.json").is_file()),
                     key=lambda p: (p.name != "stockholm", p.name))
    payload = []
    index = []
    # Validate inputs before creating the output; configurations and artwork are copied byte for byte.
    for source in sources:
        json.loads((source / "config.json").read_text(encoding="utf-8-sig"))
        index.append({"id": source.name, "name": NAMES.get(source.name, source.name)})
        for file in sorted(source.rglob("*")):
            relative = file.relative_to(source)
            if file.is_file() and wanted(relative):
                file.resolve().relative_to(source.resolve())
                payload.append((file, Path("themes") / source.name / relative))
    license_file = REPO / "LICENSE"
    if not license_file.is_file():
        raise FileNotFoundError(license_file)
    output.mkdir(parents=True, exist_ok=False)
    themes = output / "themes"
    themes.mkdir()
    for file, relative in payload:
        destination = output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(file, destination)
    write_json(themes / "index.json", {"themes": index})
    (output / "CREDITS.md").write_bytes((
        "# CleanYourTV offline themes\n\n"
        "Source: https://github.com/Gathin23/cleanyourtv/tree/main/themes\n\n"
        "Original theme configurations, pictures, captions and credit files are preserved. "
        "Artwork licences differ by theme; consult each themes/<id>/CREDITS.md before redistribution. "
        "Russian catalogue labels are local presentation metadata.\n"
    ).encode("utf-8"))
    shutil.copy2(license_file, output / "LICENSE")
    files = []
    for file in sorted(output.rglob("*")):
        if file.is_file():
            data = file.read_bytes()
            files.append({"path": file.relative_to(output).as_posix(), "bytes": len(data),
                          "sha256": hashlib.sha256(data).hexdigest()})
    record = {"themes": index, "files": files, "total_payload_bytes": sum(f["bytes"] for f in files)}
    write_json(output / "theme-package-manifest.json", record)
    return {"themes": len(index), "files": len(files), "bytes": record["total_payload_bytes"],
            "output": str(output)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path,
                        help="new output directory; must not already exist")
    args = parser.parse_args()
    if args.output.exists() or args.output.is_symlink():
        parser.error("--output must not exist; no files were changed")
    try:
        result = package(args.output)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Cannot package themes: {error}. Existing files were not removed or overwritten.\n")
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
