#!/usr/bin/env node
'use strict';
// Optional design-time export. Android Studio uses the committed resources;
// it does not require Node.js, sharp, ImageMagick, or a font installation.
const fs = require('fs');
const path = require('path');
const {execFileSync} = require('child_process');
const moduleRoots = process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES;
const sharp = require(require.resolve('sharp', moduleRoots ? {paths: [moduleRoots]} : undefined));
const root = path.resolve(__dirname, '..');
const art = path.join(root, 'design', 'icons');
const res = path.join(root, 'app', 'src', 'main', 'res');
const geometry = JSON.parse(fs.readFileSync(path.join(art, 'geometry.json'), 'utf8'));
const converter = process.env.QIPING_CONVERT || 'convert';
const font = process.env.QIPING_BANNER_FONT;
if (!font || !fs.existsSync(font)) {
  throw new Error('Set QIPING_BANNER_FONT to a Chinese-capable font file before exporting the banner.');
}
function write(file, content) {
  fs.mkdirSync(path.dirname(file), {recursive: true});
  fs.writeFileSync(file, content);
}
const svgPaths = geometry.paths.map(p => `<path fill="${p.fill}" fill-rule="${p.fillRule || 'nonzero'}" d="${p.d}"/>`).join('\n');
const mark = `<g transform="translate(0 ${geometry.opticalOffsetY})">${svgPaths}</g>`;
function svg(body, width = 1024, height = 1024) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">${body}</svg>\n`;
}
async function raster(source, destination, width, height = width) {
  fs.mkdirSync(path.dirname(destination), {recursive: true});
  await sharp(source, {density: 288}).resize(width, height).png().toFile(destination);
}
async function main() {
const legacy = svg(`<rect width="1024" height="1024" rx="190" fill="${geometry.background}"/>${mark}`);
write(path.join(art, 'icon_master.svg'), legacy);
write(path.join(art, 'mark_master.svg'), svg(mark));
await raster(path.join(art, 'icon_master.svg'), path.join(art, 'icon_preview.png'), 1024);
const densities = [['mdpi',80,160,90], ['hdpi',120,240,135], ['xhdpi',160,320,180], ['xxhdpi',240,480,270], ['xxxhdpi',320,640,360]];
for (const [density, size] of densities) {
  await raster(path.join(art, 'icon_master.svg'), path.join(res, 'mipmap-' + density, 'ic_launcher.png'), size);
}
// Same source geometry for legacy, adaptive, monochrome, and TV banner.
const vectorPaths = geometry.paths.map(p => `            <path android:fillColor="${p.fill}" android:fillType="${p.fillRule === 'evenodd' ? 'evenOdd' : 'nonZero'}" android:pathData="${p.d}" />`).join('\n');
const vector = `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="1024" android:viewportHeight="1024">
    <group android:pivotX="512" android:pivotY="512" android:scaleX="${geometry.adaptiveScale}" android:scaleY="${geometry.adaptiveScale}">
        <group android:translateY="${geometry.opticalOffsetY}">
${vectorPaths}
        </group>
    </group>
</vector>
`;
write(path.join(res, 'drawable-anydpi-v26', 'ic_launcher_foreground.xml'), vector);
for (const api of [26,33]) {
  write(path.join(res, `mipmap-anydpi-v${api}`, 'ic_launcher.xml'), `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />${api >= 33 ? '\n    <monochrome android:drawable="@drawable/ic_launcher_foreground" />' : ''}
</adaptive-icon>
`);
}
write(path.join(res, 'values', 'icon_colors.xml'), `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="ic_launcher_background">${geometry.background}</color>\n</resources>\n`);
// Banner stays a full-bleed 16:9 rectangle; no baked frame or outer shadow.
const banner = svg(`<rect width="640" height="360" fill="${geometry.background}"/><g transform="translate(28 70) scale(0.22)">${mark}</g>`,640,360);
write(path.join(art, 'banner_background.svg'), banner);
const bannerPng = path.join(art, 'tv_banner_preview.png');
await raster(path.join(art, 'banner_background.svg'), bannerPng, 640, 360);
execFileSync(converter, [bannerPng, '-font', font, '-fill', '#ECEEF1', '-pointsize', '64', '-gravity', 'NorthWest', '-annotate', '+270+127', '栖屏桌面', '-strip', 'PNG32:' + bannerPng]);
for (const [density, size, width, height] of densities) {
  const file = path.join(res, 'drawable-' + density, 'tv_banner.png');
  fs.mkdirSync(path.dirname(file), {recursive: true});
  execFileSync(converter, [bannerPng, '-resize', `${width}x${height}!`, '-strip', 'PNG32:' + file]);
}
// Static previews of the system's central 72/108 viewport. These are design
// checks, not screenshots of a real device or Android emulator.
const adaptive = `<g transform="translate(512 512) scale(${geometry.adaptiveScale}) translate(-512 -512)">${mark}</g>`;
const crop = `<g transform="translate(-256 -256) scale(1.5)"><rect width="1024" height="1024" fill="${geometry.background}"/>${adaptive}</g>`;
for (const shape of ['circle','square','rounded']) {
  const mask = shape === 'circle' ? '<circle cx="512" cy="512" r="512"/>' : `<rect width="1024" height="1024" rx="${shape === 'rounded' ? 190 : 0}"/>`;
  const source = path.join(art, `mask_${shape}.svg`);
  write(source, svg(`<defs><clipPath id="mask">${mask}</clipPath></defs><g clip-path="url(#mask)">${crop}</g>`));
  await raster(source, path.join(art, `mask_${shape}.png`), 320);
}
// Legacy monochrome and actual foreground raster for alpha/safe-zone QA only.
write(path.join(art, 'adaptive_foreground.svg'), svg(adaptive));
await raster(path.join(art, 'adaptive_foreground.svg'), path.join(art, 'adaptive_foreground.png'), 1080);
console.log('Exported icon resources, native adaptive layers, TV banners and mask previews.');
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
