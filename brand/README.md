# Sentinel AML — brand kit

The mark: a shield (protection) holding an "S" drawn as a flow of transactions, with the middle node flagged in amber (a detected suspicious transaction).

| Colour | Hex | Use |
|---|---|---|
| Deep teal | `#0f6b62` (gradient `#16897d` → `#0a4f48`) | Shield, "AML" |
| Amber | `#f5b942` | Flagged node only |
| Ink | `#17211f` | "Sentinel" on light backgrounds |
| Night | `#0b1c1a` | Dark backgrounds (LinkedIn images) |

Typeface: IBM Plex Sans (600 for "Sentinel", 500 for "AML"). In every SVG/PDF the text is converted to outlines, so no font is needed to open them.

## Files

| Folder | What | Use it for |
|---|---|---|
| `svg/` | Mark (colour / white / black), horizontal logo (dark text / white text), stacked logo | Anything that scales: web, slides, docs |
| `png/` | Mark 16–1024 px, horizontal logo 600/1200/2400 px, stacked 1024 px; transparent | Slides, README, docs |
| `jpg/`, `webp/` | Logo and mark on white | Places that don't accept transparency |
| `pdf/` | Mark, horizontal, stacked (vector) | Print, Word/Keynote |
| `linkedin/` | Profile/company logo (400, 300), banner 1584×396, post 1200×627, square post 1200×1200 (PNG, JPG, SVG) | LinkedIn; the banner keeps its content on the right because the profile photo covers the lower left |
| `favicon/` | `favicon.ico` (16/32/48), PNGs, `icon.svg`, `apple-touch-icon.png`, 192/512 icons | Browsers; copies live in `frontend/src/app/` |

## Regenerating

`build.js` writes this whole folder (except `pdf/`). It needs `opentype.js@1.3.4` and `@fontsource/ibm-plex-sans@5.1.0` installed next to it, and the `sharp` that ships with the console:

```bash
node build.js <output-dir> ../frontend/node_modules/sharp
```

PDFs were printed from the SVGs with headless Chrome.
