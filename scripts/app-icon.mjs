// Parhlou's app icon: the owner's logo, whole, fitted inside the icon's border. Ustaad (glasses, beard, white kameez,
// yellow waistcoat with a green outline, a yellow spark), "Parhlou" in green, and "پڑھ لو" between two yellow dashes.
// The figure is drawn here as shapes; the lettering uses Fredoka Bold and Baloo Bhaijaan 2 (open fonts, fetched from npm).
// This script writes every icon from them:
//   - the adaptive icon (Android 8+): white background, the logo in front, and a one-colour version for themed icons;
//   - the square and round icons for Android 7.1 (minSdk 25);
//   - the 512 px Play Store icon and a large logo (docs/brand/).
// Run: node scripts/app-icon.mjs [preview-folder]   (uses the pre-installed Playwright Chromium to draw the PNGs)
import { mkdirSync, rmSync, writeFileSync, existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import { tmpdir } from "node:os";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const RES = join(ROOT, "android/app/src/main/res");

const GREEN = "#0A6350", YELLOW = "#FFCF0F", HAIR = "#4B2E1E", HAIR_HI = "#7A4B2F", SKIN = "#F9BF87", EAR = "#F2AE77";
const GLASS = "#1F3B34", WHITE = "#FFFFFF", MOUTH = "#7E2E22", TONGUE = "#EE9B5B";

const circle = (cx, cy, r) => `M${cx - r},${cy} A${r},${r} 0 1 0 ${cx + r},${cy} A${r},${r} 0 1 0 ${cx - r},${cy} Z`;
const ellipse = (cx, cy, rx, ry) => `M${cx - rx},${cy} A${rx},${ry} 0 1 0 ${cx + rx},${cy} A${rx},${ry} 0 1 0 ${cx - rx},${cy} Z`;
const roundRect = (x, y, w, h, r) =>
  `M${x + r},${y} H${x + w - r} A${r},${r} 0 0 1 ${x + w},${y + r} V${y + h - r} A${r},${r} 0 0 1 ${x + w - r},${y + h} ` +
  `H${x + r} A${r},${r} 0 0 1 ${x},${y + h - r} V${y + r} A${r},${r} 0 0 1 ${x + r},${y} Z`;

// The figure, in the logo's own coordinates (about 1256 units wide). Back to front.
const BUST = "M380,690 C380,640 420,606 468,592 L548,562 L708,562 L788,592 C836,606 876,640 876,690 " +
  "C800,724 716,740 628,740 C540,740 456,724 380,690 Z";
const shapes = {
  sparks: [
    { d: "M772,182 L806,124", stroke: YELLOW, width: 18, mono: true },
    { d: "M812,194 L872,160", stroke: YELLOW, width: 18, mono: true },
    { d: "M836,246 L893,231", stroke: YELLOW, width: 18, mono: true },
  ],
  // Inside the bust: the white kameez, the yellow waistcoat's two fronts, and the green edges of its V.
  body: [
    { d: BUST, fill: WHITE },
    { d: "M458,588 L566,566 L631,748 L452,748 Z", fill: YELLOW },
    { d: "M798,588 L690,566 L641,748 L804,748 Z", fill: YELLOW },
    { d: "M566,570 L631,742", stroke: GREEN, width: 8, mono: true },
    { d: "M690,570 L641,742", stroke: GREEN, width: 8, mono: true },
  ],
  front: [
    { d: BUST, stroke: GREEN, width: 10, mono: true },
    // The kameez collar, white with a green edge.
    { d: "M552,540 C566,574 600,590 628,590 C656,590 690,574 704,540 L694,528 C676,552 654,562 628,562 C602,562 580,552 562,528 Z",
      fill: WHITE, stroke: GREEN, width: 7, mono: true },
    { d: ellipse(478, 372, 20, 34), fill: EAR },
    { d: ellipse(777, 372, 20, 34), fill: EAR },
    { d: "M495,300 C495,240 560,230 628,230 C696,230 758,240 758,300 L758,420 C758,500 700,540 628,540 C556,540 495,500 495,420 Z", fill: SKIN },
    { d: "M468,345 C462,290 466,235 500,195 C535,152 585,138 632,138 C690,138 738,160 760,200 C782,240 780,300 772,345 L758,345 " +
      "C756,310 752,285 738,268 C700,255 560,252 515,268 C502,285 498,310 497,345 Z", fill: HAIR, monoFill: true },
    { d: "M534,236 C550,204 576,182 610,170", stroke: HAIR_HI, width: 7 },
    { d: "M602,226 C628,198 662,184 702,182", stroke: HAIR_HI, width: 7 },
    // Beard and moustache, with the smile inside.
    { d: "M495,345 L497,420 C500,502 555,554 628,554 C701,554 756,502 758,420 L758,345 L748,345 C746,388 736,406 710,424 " +
      "C684,416 656,412 628,420 C600,412 572,416 546,424 C520,406 510,388 508,345 Z", fill: HAIR, monoFill: true },
    { d: "M588,452 C610,445 646,445 668,452 C664,479 647,494 628,494 C609,494 592,479 588,452 Z", fill: MOUTH },
    { d: "M593,454 C614,448 642,448 663,454 L661,465 C640,460 616,460 595,465 Z", fill: WHITE },
    { d: "M605,483 C616,474 640,474 651,483 C645,490 637,494 628,494 C619,494 611,490 605,483 Z", fill: TONGUE },
    // Eyebrows, eyes and glasses.
    { d: "M538,324 C552,316 572,315 591,320", stroke: HAIR, width: 9 },
    { d: "M665,320 C684,315 704,316 718,324", stroke: HAIR, width: 9 },
    { d: circle(563, 362, 9), fill: GLASS },
    { d: circle(683, 362, 9), fill: GLASS },
    { d: roundRect(523, 337, 80, 49, 10), stroke: GLASS, width: 8, mono: true },
    { d: roundRect(643, 337, 80, 49, 10), stroke: GLASS, width: 8, mono: true },
    { d: "M603,352 L643,352", stroke: GLASS, width: 7, mono: true },
    { d: "M523,350 L490,347", stroke: GLASS, width: 7, mono: true },
    { d: "M723,350 L764,347", stroke: GLASS, width: 7, mono: true },
  ],
};

// ── The logo as SVG, in the logo's own coordinates (1256 × 1256) ─────────────────────────────────────────────────
const FONT_DIR = join(tmpdir(), "parhlou-icon-fonts");
function font(pkg, file) {
  const dir = join(FONT_DIR, pkg.replace("/", "-"));
  if (!existsSync(join(dir, "package/files", file))) {
    mkdirSync(dir, { recursive: true });
    execFileSync("npm", ["pack", pkg, "--silent", "--pack-destination", dir], { stdio: "ignore" });
    const tgz = readdirSync(dir).find((f) => f.endsWith(".tgz"));
    execFileSync("tar", ["-xzf", join(dir, tgz), "-C", dir]);
  }
  return readFileSync(join(dir, "package/files", file)).toString("base64");
}
const FONTS = `@font-face{font-family:'Fredoka';font-weight:700;src:url(data:font/woff2;base64,${font("@fontsource/fredoka@5.3.0", "fredoka-latin-700-normal.woff2")}) format('woff2');}` +
  `@font-face{font-family:'Baloo Bhaijaan 2';font-weight:700;src:url(data:font/woff2;base64,${font("@fontsource/baloo-bhaijaan-2@5.3.0", "baloo-bhaijaan-2-arabic-700-normal.woff2")}) format('woff2');}`;

function svgShape(s, mono) {
  if (mono && !s.mono && !s.monoFill) return "";
  const color = (c) => (mono ? "#000" : c);
  const fill = s.fill && (!mono || s.monoFill) ? color(s.fill) : "none";
  const stroke = s.stroke ? ` stroke="${color(s.stroke)}" stroke-width="${s.width}" stroke-linecap="round" stroke-linejoin="round"` : "";
  return `<path d="${s.d}" fill="${fill}"${stroke}/>`;
}
/** The whole logo; [sizes] are the lettering's font sizes, measured so each line is as wide as in the original. */
function logo(mono, sizes) {
  const all = (list) => list.map((s) => svgShape(s, mono)).join("");
  const ink = mono ? "#000" : GREEN, dash = mono ? "#000" : YELLOW;
  return `${all(shapes.sparks)}<clipPath id="bust"><path d="${BUST}"/></clipPath><g clip-path="url(#bust)">${all(shapes.body)}</g>${all(shapes.front)}` +
    `<text id="word" x="628" y="945" text-anchor="middle" font-family="Fredoka" font-weight="700" font-size="${sizes.word}" fill="${ink}">Parhlou</text>` +
    `<text id="urdu" x="628" y="1100" text-anchor="middle" direction="rtl" font-family="Baloo Bhaijaan 2" font-weight="700" font-size="${sizes.urdu}" fill="${ink}">پڑھ لو</text>` +
    `<path d="M242,1063 L398,1063 M858,1063 L1014,1063" stroke="${dash}" stroke-width="17" stroke-linecap="round"/>`;
}
/** The logo seen through [view] = [x, y, width] (logo units), on a white tile: a square, a rounded square, a circle, or none. */
function svg(px, view, tile, mono, sizes) {
  const [x, y, w] = view;
  const shape = tile === "circle" ? `<circle cx="${x + w / 2}" cy="${y + w / 2}" r="${w / 2}"/>`
    : tile === "rounded" ? `<rect x="${x}" y="${y}" width="${w}" height="${w}" rx="${w * 0.22}"/>`
    : tile === "square" ? `<rect x="${x}" y="${y}" width="${w}" height="${w}"/>` : "";
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}" viewBox="${x} ${y} ${w} ${w}"><style>${FONTS}</style>` +
    (shape ? `<clipPath id="tile">${shape}</clipPath><g clip-path="url(#tile)"><rect x="${x}" y="${y}" width="${w}" height="${w}" fill="#FFFFFF"/>` : "<g>") +
    `${logo(mono, sizes)}</g></svg>`;
}

const write = (path, text) => { mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, text); };
const require = createRequire(import.meta.url);
const { chromium } = require("playwright");
const browser = await chromium.launch();
const page = await browser.newPage();
async function show(markup, px) {
  await page.setViewportSize({ width: px, height: px });
  await page.setContent(`<html><body style="margin:0;background:transparent">${markup}</body></html>`);
  await page.evaluate(() => document.fonts.ready);
}
async function png(markup, px, out) {
  await show(markup, px);
  mkdirSync(dirname(out), { recursive: true });
  await page.locator("svg").screenshot({ path: out, omitBackground: true });
}

// 1. Size the lettering like the original: "Parhlou" 935 units wide, "پڑھ لو" 350.
await show(svg(1256, [0, 0, 1256], "", false, { word: 260, urdu: 150 }), 1256);
const widths = await page.evaluate(() => ({ word: document.getElementById("word").getBBox().width, urdu: document.getElementById("urdu").getBBox().width }));
const sizes = { word: Math.round(260 * 935 / widths.word), urdu: Math.round(150 * 350 / widths.urdu) };

// 2. Find how far the drawing reaches from its middle, from the pixels themselves, so it can be fitted inside a circle.
await show(svg(1256, [0, 0, 1256], "", false, sizes), 1256);
const shot = (await page.locator("svg").screenshot({ omitBackground: true })).toString("base64");
const reach = await page.evaluate(async (b64) => {
  const img = new Image(); img.src = "data:image/png;base64," + b64; await img.decode();
  const c = document.createElement("canvas"); c.width = img.width; c.height = img.height;
  const g = c.getContext("2d"); g.drawImage(img, 0, 0);
  const { data, width, height } = g.getImageData(0, 0, c.width, c.height);
  let x0 = width, y0 = height, x1 = 0, y1 = 0;
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) if (data[(y * width + x) * 4 + 3] > 8) {
    if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
  }
  const cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
  let r = 0;
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) if (data[(y * width + x) * 4 + 3] > 8) r = Math.max(r, Math.hypot(x - cx, y - cy));
  return { cx, cy, r, w: x1 - x0, h: y1 - y0 };
}, shot);
const { cx, cy, r } = reach;
const square = Math.max(reach.w, reach.h);
/** A view centred on the drawing: [k] × its circle (for round masks) or [k] × its widest side (for square tiles). */
const round = (k) => { const w = 2 * r * k; return [cx - w / 2, cy - w / 2, w]; };
const boxed = (k) => { const w = square * k; return [cx - w / 2, cy - w / 2, w]; };

// 3. The adaptive icon (108 units; launchers show the middle 72 in a circle, squircle or rounded square). The drawing's
//    circle is 68 units across, inside the 72 every launcher shows, so nothing is ever cut off.
const ADAPTIVE = round(108 / 68);
write(join(RES, "drawable/ic_launcher_background.xml"), `<?xml version="1.0" encoding="utf-8"?>
<!-- The app icon's background: plain white, like the Parhlou logo (scripts/app-icon.mjs). -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#FFFFFF" />
</shape>
`);
const adaptive = `<?xml version="1.0" encoding="utf-8"?>
<!-- The Parhlou logo on white, fitted inside the icon's border (scripts/app-icon.mjs). -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
`;
write(join(RES, "mipmap-anydpi-v26/ic_launcher.xml"), adaptive);
write(join(RES, "mipmap-anydpi-v26/ic_launcher_round.xml"), adaptive);
for (const f of ["drawable/ic_launcher_gradient.xml", "drawable/ic_launcher_foreground.xml", "drawable/ic_launcher_monochrome.xml"]) {
  if (existsSync(join(RES, f))) rmSync(join(RES, f));
}
const DENSITIES = { mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 };
for (const [name, d] of Object.entries(DENSITIES)) {
  const dir = join(RES, `mipmap-${name}`);
  await png(svg(Math.round(108 * d), ADAPTIVE, "", false, sizes), Math.round(108 * d), join(dir, "ic_launcher_foreground.png"));
  await png(svg(Math.round(108 * d), ADAPTIVE, "", true, sizes), Math.round(108 * d), join(dir, "ic_launcher_monochrome.png"));
  // Android 7.1: a white rounded square and a white circle, the logo filling them with a small margin.
  await png(svg(Math.round(48 * d), boxed(1.12), "rounded", false, sizes), Math.round(48 * d), join(dir, "ic_launcher.png"));
  await png(svg(Math.round(48 * d), round(1.06), "circle", false, sizes), Math.round(48 * d), join(dir, "ic_launcher_round.png"));
}
// Google Play rounds the corners of the 512 px icon itself: keep the logo inside its middle.
await png(svg(512, boxed(1.2), "square", false, sizes), 512, join(ROOT, "docs/brand/parhlou-icon-512.png"));
await png(svg(1024, boxed(1.1), "square", false, sizes), 1024, join(ROOT, "docs/brand/parhlou-logo.png"));
const preview = process.argv[2];
if (preview) {
  await png(svg(432, ADAPTIVE, "square", false, sizes), 432, join(preview, "icon-full-canvas.png"));
  await png(svg(288, round(108 / 68 * 72 / 108), "circle", false, sizes), 288, join(preview, "icon-circle.png"));
  await png(svg(144, round(108 / 68 * 72 / 108), "circle", false, sizes), 144, join(preview, "icon-48dp-xxhdpi.png"));
  await png(svg(288, ADAPTIVE, "", true, sizes), 288, join(preview, "icon-mono.png"));
}
await browser.close();
console.log("App icon written.", JSON.stringify({ sizes, reach: { cx: Math.round(cx), cy: Math.round(cy), r: Math.round(r) } }));
