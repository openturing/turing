import { test } from "node:test";
import assert from "node:assert/strict";

import { buildZip, crc32 } from "../src/zip.js";

const enc = (s: string) => new TextEncoder().encode(s);

test("crc32 matches the known IEEE check value", () => {
  assert.equal(crc32(enc("123456789")), 0xcbf43926);
  assert.equal(crc32(enc("")), 0);
});

test("buildZip emits a valid local header + EOCD", () => {
  const zip = buildZip([{ name: "SKILL.md", data: enc("# hi\n") }]);
  const view = new DataView(zip.buffer, zip.byteOffset, zip.byteLength);
  // Local file header signature at offset 0.
  assert.equal(view.getUint32(0, true), 0x04034b50);
  // EOCD signature in the trailing 22 bytes.
  assert.equal(view.getUint32(zip.length - 22, true), 0x06054b50);
  // One entry recorded in the EOCD.
  assert.equal(view.getUint16(zip.length - 22 + 10, true), 1);
});

test("buildZip stores the file name and content uncompressed", () => {
  const data = enc("hello world");
  const zip = buildZip([{ name: "a.txt", data }]);
  const text = new TextDecoder().decode(zip);
  assert.ok(text.includes("a.txt"));
  assert.ok(text.includes("hello world"));
});
