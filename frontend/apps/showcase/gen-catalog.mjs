/**
 * Atlas Store catalog generator.
 *
 * Deterministically synthesizes ~200 typed commerce products and writes them to
 * `export/atlas-store_content.json` in the Turing content-import shape
 * (`{ "<site>": { "<locale>": [ ...docs ] } }`). The schema is hand-authored in
 * `export/export.json` (SN site + typed fields + facets); this file only
 * produces the documents that fill it.
 *
 * The data is the "seed" half of T451: a feature-dense typed catalog — price as
 * CURRENCY, rating as FLOAT, review_count as INT, availability/brand/category as
 * faceted STRING, colors/tags as multi-valued STRING, release_date as DATE,
 * on_sale as BOOL — exactly the surface the manifest + hybrid ranking + facets
 * are meant to stress.
 *
 * Deterministic: a seeded LCG drives every choice, so repeated runs produce a
 * byte-identical catalog (stable git diffs, reproducible demo).
 *
 * Usage: node gen-catalog.mjs   (run from the app directory)
 */
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const SITE = "atlas-store";
const LOCALE = "en_US";
const TARGET_COUNT = 200;
const CURRENCY = "USD";

/* ── Seeded PRNG (mulberry32) — deterministic across runs ── */
function makeRng(seed) {
  let a = seed >>> 0;
  return function rng() {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const rng = makeRng(0xa71a5);
const pick = (arr) => arr[Math.floor(rng() * arr.length)];
const pickN = (arr, n) => {
  const pool = [...arr];
  const out = [];
  for (let i = 0; i < n && pool.length; i++) {
    out.push(pool.splice(Math.floor(rng() * pool.length), 1)[0]);
  }
  return out;
};
const between = (min, max) => min + rng() * (max - min);
const round2 = (n) => Math.round(n * 100) / 100;

/* ── Domain vocabulary ── */
const CATEGORIES = [
  {
    name: "Electronics",
    subcategories: ["Headphones", "Laptops", "Smartphones", "Cameras", "Smartwatches", "Speakers"],
    brands: ["Sondr", "Pixelbloom", "Northwave", "Kestrel", "Volt&Co", "Lumen"],
    materials: ["Aluminum", "Recycled Plastic", "Glass", "Carbon Fiber"],
    priceRange: [49, 1899],
    tags: ["wireless", "noise-canceling", "5G", "fast-charge", "OLED", "water-resistant"],
  },
  {
    name: "Home & Kitchen",
    subcategories: ["Coffee Makers", "Cookware", "Vacuum Cleaners", "Lighting", "Air Purifiers"],
    brands: ["Hearth", "Copperleaf", "Nimbus", "Verena", "Brightside"],
    materials: ["Stainless Steel", "Cast Iron", "Ceramic", "Bamboo", "Tempered Glass"],
    priceRange: [19, 699],
    tags: ["energy-efficient", "dishwasher-safe", "smart-home", "compact", "BPA-free"],
  },
  {
    name: "Outdoors",
    subcategories: ["Tents", "Backpacks", "Water Bottles", "Hiking Boots", "Sleeping Bags"],
    brands: ["Summit&Pine", "Trailhead", "Granite", "Wildcrest", "Basecamp"],
    materials: ["Ripstop Nylon", "Gore-Tex", "Merino Wool", "Anodized Aluminum", "Cork"],
    priceRange: [14, 549],
    tags: ["waterproof", "ultralight", "insulated", "packable", "UV-protected"],
  },
  {
    name: "Fashion",
    subcategories: ["Sneakers", "Jackets", "Watches", "Sunglasses", "Backpacks"],
    brands: ["Marlowe", "Astrid", "Cobalt", "Fernweh", "Lúa"],
    materials: ["Full-Grain Leather", "Organic Cotton", "Recycled Polyester", "Suede", "Titanium"],
    priceRange: [29, 459],
    tags: ["unisex", "limited-edition", "vegan", "handmade", "all-season"],
  },
  {
    name: "Toys & Games",
    subcategories: ["Board Games", "Building Sets", "Puzzles", "Plush", "Outdoor Play"],
    brands: ["Meeple&Co", "Brickhaus", "Tinker", "Hoot", "Galleon"],
    materials: ["FSC Wood", "Recycled Plastic", "Cardboard", "Organic Cotton"],
    priceRange: [9, 129],
    tags: ["award-winning", "ages-8-plus", "family", "STEM", "cooperative"],
  },
];

const ADJECTIVES = ["Pro", "Lite", "Max", "Edge", "Aero", "Studio", "Field", "Classic", "Nova", "Prime", "Mini", "Ultra"];
const COLORS = ["Midnight Black", "Arctic White", "Forest Green", "Slate Gray", "Sunset Orange", "Ocean Blue", "Sand", "Crimson", "Lavender", "Graphite"];
const OUTCOMES = ["In Stock", "Low Stock", "Out of Stock", "Pre-order", "Backorder"];
const OUTCOME_WEIGHTS = [0.55, 0.18, 0.1, 0.1, 0.07];

function weightedOutcome() {
  const r = rng();
  let acc = 0;
  for (let i = 0; i < OUTCOMES.length; i++) {
    acc += OUTCOME_WEIGHTS[i];
    if (r <= acc) return OUTCOMES[i];
  }
  return OUTCOMES[0];
}

function modelName() {
  const stems = ["Atlas", "Orbit", "Vega", "Drift", "Echo", "Helix", "Onyx", "Flux", "Cirrus", "Terra", "Halo", "Quill"];
  return `${pick(stems)} ${pick(ADJECTIVES)}`;
}

function slug(s) {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function isoDate(yearMin, yearMax) {
  const year = Math.floor(between(yearMin, yearMax + 1));
  const month = Math.floor(between(1, 13));
  const day = Math.floor(between(1, 28));
  const mm = String(month).padStart(2, "0");
  const dd = String(day).padStart(2, "0");
  return `${year}-${mm}-${dd}T00:00:00Z`;
}

/* ── Document factory ── */
function makeProduct(i) {
  const cat = pick(CATEGORIES);
  const sub = pick(cat.subcategories);
  const brand = pick(cat.brands);
  const model = modelName();
  const title = `${brand} ${model} ${sub.replace(/s$/, "")}`;
  const sku = `ATL-${String(i + 1).padStart(4, "0")}-${slug(brand).slice(0, 3).toUpperCase()}`;
  const colors = pickN(COLORS, 1 + Math.floor(rng() * 3));
  const materials = pickN(cat.materials, 1 + Math.floor(rng() * 2));
  const tags = pickN(cat.tags, 2 + Math.floor(rng() * 2));
  const price = round2(between(cat.priceRange[0], cat.priceRange[1]));
  const onSale = rng() < 0.3;
  const listPrice = onSale ? round2(price * between(1.1, 1.45)) : price;
  const rating = round2(between(3.1, 5.0));
  const reviewCount = Math.floor(between(3, 2400));
  const availability = weightedOutcome();
  const weight = round2(between(0.1, 12));
  const releaseDate = isoDate(2019, 2026);

  const description =
    `${title} in ${colors[0]}. ${materials.join(" & ")} build` +
    `${tags.length ? `, ${tags.join(", ")}.` : "."}` +
    ` Rated ${rating}/5 across ${reviewCount} reviews.`;

  const text =
    `The ${title} is part of Atlas Store's ${cat.name} → ${sub} line by ${brand}. ` +
    `Crafted from ${materials.join(" and ")} and available in ${colors.join(", ")}, ` +
    `it is designed for everyday use with ${tags.join(", ")} features. ` +
    `It currently retails at ${price.toFixed(2)} ${CURRENCY}` +
    `${onSale ? ` (down from ${listPrice.toFixed(2)} ${CURRENCY})` : ""}, ` +
    `weighs ${weight} kg, and is ${availability.toLowerCase()}. ` +
    `Customers rate it ${rating} out of 5 stars based on ${reviewCount} verified reviews. ` +
    `Released in ${releaseDate.slice(0, 4)}, it remains one of the most searched ${sub.toLowerCase()} in the catalog.`;

  return {
    id: `prod-${slug(sku)}`,
    type: "product",
    title,
    description,
    text,
    url: `/products/${slug(title)}-${slug(sku)}`,
    image: `https://picsum.photos/seed/${slug(sku)}/600/600`,
    sku,
    brand,
    category: cat.name,
    subcategory: sub,
    // CURRENCY fields use the Solr "amount,ISO4217" payload format. We also
    // keep plain FLOAT mirrors (price_amount/list_price_amount) so numeric
    // sort + range facets work on every engine (incl. the offline Lucene
    // sample), while the CURRENCY-typed fields exercise the manifest.
    price: `${price.toFixed(2)},${CURRENCY}`,
    list_price: `${listPrice.toFixed(2)},${CURRENCY}`,
    price_amount: price,
    list_price_amount: listPrice,
    currency: CURRENCY,
    rating,
    review_count: reviewCount,
    availability,
    on_sale: onSale,
    color: colors,
    material: materials,
    tags,
    weight_kg: weight,
    release_date: releaseDate,
  };
}

const docs = [];
for (let i = 0; i < TARGET_COUNT; i++) docs.push(makeProduct(i));

const content = { [SITE]: { [LOCALE]: docs } };
const outDir = join(process.cwd(), "export");
mkdirSync(outDir, { recursive: true });
const outPath = join(outDir, `${SITE}_content.json`);
writeFileSync(outPath, JSON.stringify(content, null, 2) + "\n");

const inStock = docs.filter((d) => d.availability === "In Stock").length;
const onSale = docs.filter((d) => d.on_sale).length;
console.log(
  `Atlas Store catalog generated: ${docs.length} products → ${outPath}\n` +
    `  ${inStock} in stock · ${onSale} on sale · ${CATEGORIES.length} categories`
);
