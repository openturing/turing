/**
 * Creates a ZIP archive from the Vite dist/ output directory.
 * The ZIP name is derived from the package.json name field
 * (stripping the "turing-example-" prefix).
 *
 * Usage: node zip-dist.mjs
 * Run from within the app/ directory (where dist/ and package.json live).
 */
import { createWriteStream, readFileSync, readdirSync, statSync } from "node:fs";
import { join, relative, basename } from "node:path";
import { pipeline } from "node:stream/promises";
import { createDeflateRaw } from "node:zlib";

const DIST_DIR = join(process.cwd(), "dist");
const pkg = JSON.parse(readFileSync(join(process.cwd(), "package.json"), "utf-8"));
const siteName = pkg.name.replace(/^turing-example-/, "");
const zipPath = join(process.cwd(), `${siteName}.zip`);

function collectFiles(dir) {
  const entries = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      entries.push(...collectFiles(fullPath));
    } else {
      entries.push(fullPath);
    }
  }
  return entries;
}

function crc32(buf) {
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) {
    crc ^= buf[i];
    for (let j = 0; j < 8; j++) {
      crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function dosDateTime(date) {
  const time =
    ((date.getHours() & 0x1f) << 11) |
    ((date.getMinutes() & 0x3f) << 5) |
    ((date.getSeconds() >> 1) & 0x1f);
  const dateVal =
    (((date.getFullYear() - 1980) & 0x7f) << 9) |
    (((date.getMonth() + 1) & 0x0f) << 5) |
    (date.getDate() & 0x1f);
  return { time, date: dateVal };
}

async function createZip() {
  const files = collectFiles(DIST_DIR);
  if (files.length === 0) {
    console.error("dist/ directory is empty or missing. Run build first.");
    process.exit(1);
  }

  const out = createWriteStream(zipPath);
  const centralHeaders = [];
  let offset = 0;
  const now = new Date();
  const dt = dosDateTime(now);

  function writeBuffer(buf) {
    out.write(buf);
    offset += buf.length;
  }

  for (const filePath of files) {
    const relPath = relative(DIST_DIR, filePath).replace(/\\/g, "/");
    const data = readFileSync(filePath);
    const crc = crc32(data);

    // Compress
    const compressed = await new Promise((resolve, reject) => {
      const chunks = [];
      const deflater = createDeflateRaw();
      deflater.on("data", (chunk) => chunks.push(chunk));
      deflater.on("end", () => resolve(Buffer.concat(chunks)));
      deflater.on("error", reject);
      deflater.end(data);
    });

    const nameBuffer = Buffer.from(relPath, "utf-8");
    const localHeaderOffset = offset;

    // Local file header
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0); // signature
    local.writeUInt16LE(20, 4); // version needed
    local.writeUInt16LE(0, 6); // flags
    local.writeUInt16LE(8, 8); // compression (deflate)
    local.writeUInt16LE(dt.time, 10);
    local.writeUInt16LE(dt.date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(compressed.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuffer.length, 26);
    local.writeUInt16LE(0, 28); // extra length

    writeBuffer(local);
    writeBuffer(nameBuffer);
    writeBuffer(compressed);

    // Central directory header
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4); // version made by
    central.writeUInt16LE(20, 6); // version needed
    central.writeUInt16LE(0, 8); // flags
    central.writeUInt16LE(8, 10); // compression
    central.writeUInt16LE(dt.time, 12);
    central.writeUInt16LE(dt.date, 14);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(compressed.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuffer.length, 28);
    central.writeUInt16LE(0, 30); // extra length
    central.writeUInt16LE(0, 32); // comment length
    central.writeUInt16LE(0, 34); // disk start
    central.writeUInt16LE(0, 36); // internal attrs
    central.writeUInt32LE(0, 38); // external attrs
    central.writeUInt32LE(localHeaderOffset, 42);

    centralHeaders.push({ header: central, name: nameBuffer });
  }

  const centralStart = offset;
  for (const { header, name } of centralHeaders) {
    writeBuffer(header);
    writeBuffer(name);
  }
  const centralSize = offset - centralStart;

  // End of central directory
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4); // disk number
  eocd.writeUInt16LE(0, 6); // central dir disk
  eocd.writeUInt16LE(centralHeaders.length, 8);
  eocd.writeUInt16LE(centralHeaders.length, 10);
  eocd.writeUInt32LE(centralSize, 12);
  eocd.writeUInt32LE(centralStart, 16);
  eocd.writeUInt16LE(0, 20); // comment length
  writeBuffer(eocd);

  out.end();
  await new Promise((resolve) => out.on("finish", resolve));
  console.log(`\nZIP created: ${zipPath} (${files.length} files)`);
}

createZip().catch((err) => {
  console.error("Failed to create ZIP:", err);
  process.exit(1);
});
