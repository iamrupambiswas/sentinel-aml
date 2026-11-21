// Builds the Sentinel AML brand kit: SVG (text outlined), PNG, JPG, WebP, ICO. Usage: node build.js <outDir> <sharpPath>
const fs = require("fs");
const path = require("path");
const opentype = require("opentype.js");
const [, , OUT, SHARP] = process.argv;
const sharp = require(SHARP);

const C = { teal: "#0f6b62", tealTop: "#16897d", tealBot: "#0a4f48", amber: "#f5b942", ink: "#17211f", white: "#ffffff", night: "#0b1c1a" };
const fontDir = path.join(__dirname, "node_modules/@fontsource/ibm-plex-sans/files");
const font = (w) => opentype.loadSync(path.join(fontDir, `ibm-plex-sans-latin-${w}-normal.woff`));
const F600 = font(600), F500 = font(500), F400 = font(400);

// Text -> outlined path, with letter-spacing (em). Returns {d, width}
function text(f, str, x, y, size, track = 0) {
  let d = "", cx = x;
  for (const ch of str) {
    d += f.getPath(ch, cx, y, size).toPathData(2);
    cx += f.getAdvanceWidth(ch, size) + track * size;
  }
  return { d, width: cx - x - track * size };
}

// ---- The mark, drawn on a 64x64 grid: shield + "S" made of a transaction flow, the middle node flagged
const SHIELD = "M32 3.5C39 7 46 8.6 54 9.2V29C54 43.6 45 54.6 32 60.5 19 54.6 10 43.6 10 29V9.2C18 8.6 25 7 32 3.5Z";
const FLOW = "M42 20.5C39.5 17.6 36 16 32 16 26.5 16 22.5 19 22.5 23.6 22.5 28.6 27 30 32 31.5 37 33 41.5 34.5 41.5 39.5 41.5 44 37.5 47 32 47 28 47 24.5 45.5 22 42.5";
function markInner({ shield, line, node, ring, gradId }) {
  const fill = gradId ? `url(#${gradId})` : shield;
  return `${gradId ? `<defs><linearGradient id="${gradId}" x1="0" y1="0" x2="0.35" y2="1"><stop offset="0" stop-color="${C.tealTop}"/><stop offset="1" stop-color="${C.tealBot}"/></linearGradient></defs>` : ""}
<path d="${SHIELD}" fill="${fill}"/>
<path d="${FLOW}" fill="none" stroke="${line}" stroke-width="5" stroke-linecap="round"/>
<circle cx="42" cy="20.5" r="3.6" fill="${line}"/><circle cx="22" cy="42.5" r="3.6" fill="${line}"/>
<circle cx="32" cy="31.5" r="5.6" fill="${ring}"/><circle cx="32" cy="31.5" r="3.9" fill="${node}"/>`;
}
const MARK = { color: { gradId: "sentinel-g", line: C.white, node: C.amber, ring: C.teal },
  white: { shield: C.white, line: C.teal, node: C.amber, ring: C.white },   // white shield, for dark backgrounds
  black: { shield: C.ink, line: C.white, node: C.white, ring: C.ink } };   // one-colour, for print/stamps
const g = (inner, x, y, s) => `<g transform="translate(${x} ${y}) scale(${s})">${inner}</g>`;
const svg = (w, h, body, bg) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${bg ? `<rect width="${w}" height="${h}" fill="${bg}"/>` : ""}${body}</svg>`;

// ---- Lockups
function horizontal(variant) { // variant: "color" (dark text) | "white" (for dark backgrounds)
  const textCol = variant === "white" ? C.white : C.ink, amlCol = variant === "white" ? "#7fd6c9" : C.teal;
  const s = text(F600, "Sentinel", 84, 46, 40, -0.01);
  const a = text(F500, "AML", 84 + s.width + 12, 46, 40, 0.04);
  const w = Math.ceil(84 + s.width + 12 + a.width + 8);
  return { w, h: 64, svg: svg(w, 64, g(markInner(variant === "white" ? MARK.white : MARK.color), 0, 0, 1) +
    `<path d="${s.d}" fill="${textCol}"/><path d="${a.d}" fill="${amlCol}"/>`) };
}
function stacked() {
  const s = text(F600, "Sentinel", 0, 0, 40, -0.01), a = text(F500, "AML", 0, 0, 40, 0.04);
  const tw = s.width + 12 + a.width, w = Math.ceil(tw + 16), x0 = (w - tw) / 2;
  const s2 = text(F600, "Sentinel", x0, 150, 40, -0.01), a2 = text(F500, "AML", x0 + s.width + 12, 150, 40, 0.04);
  return { w, h: 166, svg: svg(w, 166, g(markInner(MARK.color), (w - 104) / 2, 0, 1.625) + `<path d="${s2.d}" fill="${C.ink}"/><path d="${a2.d}" fill="${C.teal}"/>`) };
}

// ---- LinkedIn images
const grid = (w, h, step, col) => { let d = ""; for (let x = step; x < w; x += step) d += `M${x} 0V${h}`; for (let y = step; y < h; y += step) d += `M0 ${y}H${w}`; return `<path d="${d}" stroke="${col}" stroke-width="1" fill="none"/>`; };
const darkBg = (w, h) => `<defs><radialGradient id="bg" cx="0.85" cy="0.1" r="1.1"><stop offset="0" stop-color="#0f3a35"/><stop offset="1" stop-color="${C.night}"/></radialGradient></defs><rect width="${w}" height="${h}" fill="url(#bg)"/>${grid(w, h, 44, "rgba(127,214,201,0.06)")}`;
function banner(w, h, scale, tagline, tagSize, x, yLogo) { // logo on dark canvas + tagline
  const hz = horizontal("white"), inner = hz.svg.replace(/^<svg[^>]*>|<\/svg>$/g, "");
  if (x < 0) x = w + x - Math.max(hz.w * scale, text(F400, tagline, 0, 0, tagSize).width); // negative x = right margin
  const t = text(F400, tagline, x, yLogo + 64 * scale + tagSize * 1.9, tagSize);
  return svg(w, h, darkBg(w, h) + g(inner, x, yLogo, scale) + `<path d="${t.d}" fill="#b9d4cf"/>`);
}
const TAG = "Real-time anti-money-laundering detection";

// ---- Writers
const out = (p) => { const f = path.join(OUT, p); fs.mkdirSync(path.dirname(f), { recursive: true }); return f; };
const raster = (s, w, h) => sharp(Buffer.from(s), { density: 72 * 8 }).resize(w, h, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } });
const flatten = (img) => img.flatten({ background: "#ffffff" });
function ico(pngs) { // PNG-in-ICO, supported by every current browser
  const head = Buffer.alloc(6 + 16 * pngs.length); head.writeUInt16LE(1, 2); head.writeUInt16LE(pngs.length, 4);
  let off = head.length;
  pngs.forEach(({ size, buf }, i) => { const e = 6 + 16 * i; head[e] = size % 256; head[e + 1] = size % 256; head.writeUInt16LE(1, e + 4); head.writeUInt16LE(32, e + 6); head.writeUInt32LE(buf.length, e + 8); head.writeUInt32LE(off, e + 12); off += buf.length; });
  return Buffer.concat([head, ...pngs.map((p) => p.buf)]);
}

(async () => {
  const markSvg = (v) => svg(64, 64, markInner(MARK[v]));
  const hz = horizontal("color"), hzW = horizontal("white"), st = stacked();
  const svgs = { "sentinel-mark": markSvg("color"), "sentinel-mark-white": markSvg("white"), "sentinel-mark-black": markSvg("black"),
    "sentinel-logo-horizontal": hz.svg, "sentinel-logo-horizontal-white": hzW.svg, "sentinel-logo-stacked": st.svg };
  for (const [n, s] of Object.entries(svgs)) fs.writeFileSync(out(`svg/${n}.svg`), s);

  for (const px of [16, 32, 48, 64, 128, 256, 512, 1024]) await raster(svgs["sentinel-mark"], px, px).png().toFile(out(`png/sentinel-mark-${px}.png`));
  for (const px of [512, 1024]) await raster(svgs["sentinel-mark-white"], px, px).png().toFile(out(`png/sentinel-mark-white-${px}.png`));
  for (const W of [600, 1200, 2400]) {
    const H = Math.round((W * hz.h) / hz.w);
    await raster(hz.svg, W, H).png().toFile(out(`png/sentinel-logo-horizontal-${W}.png`));
    await raster(hzW.svg, W, Math.round((W * hzW.h) / hzW.w)).png().toFile(out(`png/sentinel-logo-horizontal-white-${W}.png`));
  }
  await raster(st.svg, 1024, Math.round((1024 * st.h) / st.w)).png().toFile(out("png/sentinel-logo-stacked-1024.png"));

  // White-background copies for places that reject transparency
  const padded = (s, w, h, W, H) => svg(W, H, g(s.replace(/^<svg[^>]*>|<\/svg>$/g, ""), (W - w * (H * 0.5 / h)) / 2, H * 0.25, H * 0.5 / h), "#ffffff");
  const hzOnWhite = padded(hz.svg, hz.w, hz.h, 2400, 800), markOnWhite = padded(svgs["sentinel-mark"], 64, 64, 1024, 1024);
  for (const [n, s, W, H] of [["sentinel-logo-horizontal-on-white", hzOnWhite, 2400, 800], ["sentinel-mark-on-white", markOnWhite, 1024, 1024]]) {
    await flatten(raster(s, W, H)).jpeg({ quality: 92 }).toFile(out(`jpg/${n}.jpg`));
    await raster(s, W, H).webp({ quality: 92 }).toFile(out(`webp/${n}.webp`));
  }
  await raster(hz.svg, 1200, Math.round((1200 * hz.h) / hz.w)).webp({ lossless: true }).toFile(out("webp/sentinel-logo-horizontal-1200.webp"));
  await raster(svgs["sentinel-mark"], 512, 512).webp({ lossless: true }).toFile(out("webp/sentinel-mark-512.webp"));

  // LinkedIn: profile (shown as a circle, so the mark sits well inside), company logo, banner, post images
  const profile = svg(400, 400, darkBg(400, 400) + g(markInner(MARK.color), 90, 86, 3.45));
  const li = { "linkedin-profile-400x400": [profile, 400, 400], "linkedin-company-logo-300x300": [profile, 300, 300],
    "linkedin-banner-1584x396": [banner(1584, 396, 1.5, TAG, 26, -120, 120), 1584, 396],
    "linkedin-post-1200x627": [banner(1200, 627, 1.9, TAG, 30, 96, 210), 1200, 627],
    "linkedin-post-square-1200x1200": [svg(1200, 1200, darkBg(1200, 1200) + g(markInner(MARK.color), 440, 300, 5) +
      (() => { const s = text(F600, "Sentinel", 0, 0, 84, -0.01), a = text(F500, "AML", 0, 0, 84, 0.04), x = (1200 - s.width - 24 - a.width) / 2;
        const t = text(F400, TAG, 0, 0, 36), tx = (1200 - t.width) / 2;
        return `<path d="${text(F600, "Sentinel", x, 790, 84, -0.01).d}" fill="#fff"/><path d="${text(F500, "AML", x + s.width + 24, 790, 84, 0.04).d}" fill="#7fd6c9"/><path d="${text(F400, TAG, tx, 860, 36).d}" fill="#b9d4cf"/>`; })()), 1200, 1200] };
  for (const [n, [s, W, H]] of Object.entries(li)) {
    fs.writeFileSync(out(`linkedin/${n}.svg`), s);
    await raster(s, W, H).png().toFile(out(`linkedin/${n}.png`));
    await flatten(raster(s, W, H)).jpeg({ quality: 92 }).toFile(out(`linkedin/${n}.jpg`));
  }

  // Favicons
  const favs = [];
  for (const px of [16, 32, 48]) { const buf = await raster(svgs["sentinel-mark"], px, px).png().toBuffer(); favs.push({ size: px, buf }); fs.writeFileSync(out(`favicon/favicon-${px}.png`), buf); }
  fs.writeFileSync(out("favicon/favicon.ico"), ico(favs));
  fs.writeFileSync(out("favicon/icon.svg"), svgs["sentinel-mark"]);
  await flatten(raster(svg(180, 180, g(markInner(MARK.color), 26, 24, 2.0), "#ffffff"), 180, 180)).png().toFile(out("favicon/apple-touch-icon.png"));
  for (const px of [192, 512]) await raster(svgs["sentinel-mark"], px, px).png().toFile(out(`favicon/icon-${px}.png`));
  console.log("done");
})().catch((e) => { console.error(e); process.exit(1); });
