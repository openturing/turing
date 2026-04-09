/**
 * Mock data fixtures for Storybook stories.
 *
 * These fixtures mirror the Turing Semantic Navigation API response format.
 * Each dataset is inspired by one of the turing-marketplace projects:
 * - Mythical Creatures (bestiary)
 * - Space Missions (exploration)
 * - Vinyl Records (music store)
 *
 * @since 2026.2.5
 */

import type {
  TurSearchResponse,
  TurDocument,
  TurPaginationItem,
  TurFacetGroup,
  ResolvedDocument,
  TurChatResponse,
  TurSortOption,
} from "../../core/types";

/* ═══════════════════════════════════════════════════════════════
   Default Fields — defines which fields map to title, url, etc.
   ═══════════════════════════════════════════════════════════════ */

const defaultFields = {
  title: "title",
  url: "url",
  description: "description",
  date: "date",
  image: "image",
  text: "text",
};

/* ═══════════════════════════════════════════════════════════════
   MYTHICAL CREATURES
   ═══════════════════════════════════════════════════════════════ */

export const creatureDocuments: TurDocument[] = [
  {
    elevate: false,
    fields: {
      id: "creature-1",
      title: "Dragon",
      url: "/creatures/dragon",
      description:
        "A majestic fire-breathing reptilian creature found in myths across the world. Known for their immense wisdom and devastating power.",
      image: "https://placehold.co/400x300/1a1a2e/e94560?text=Dragon",
      date: "2024-01-15",
      text: "Dragons are legendary creatures...",
      element: ["Fire", "Air"],
      creature_type: ["Reptilian", "Winged"],
      danger_level: 9,
      origin: "European",
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "creature-2",
      title: "Phoenix",
      url: "/creatures/phoenix",
      description:
        "An immortal bird that cyclically regenerates by bursting into flames and rising from its own ashes.",
      image: "https://placehold.co/400x300/1a1a2e/ff6b35?text=Phoenix",
      date: "2024-02-20",
      text: "The Phoenix symbolizes renewal...",
      element: ["Fire", "Spirit"],
      creature_type: ["Avian"],
      danger_level: 6,
      origin: "Greek",
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "creature-3",
      title: "Kraken",
      url: "/creatures/kraken",
      description:
        "A colossal sea monster that dwells in the deepest ocean trenches, capable of dragging entire ships beneath the waves.",
      image: "https://placehold.co/400x300/0a192f/64ffda?text=Kraken",
      date: "2024-03-10",
      text: "The Kraken is a legendary cephalopod...",
      element: ["Water"],
      creature_type: ["Cephalopod", "Aquatic"],
      danger_level: 10,
      origin: "Norse",
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "creature-4",
      title: "Unicorn",
      url: "/creatures/unicorn",
      description:
        "A gentle, magical equine with a single spiraling horn. Said to purify water and heal wounds with its presence.",
      image: "https://placehold.co/400x300/f0f0f0/9b59b6?text=Unicorn",
      date: "2024-04-05",
      text: "Unicorns have appeared in art and literature...",
      element: ["Spirit"],
      creature_type: ["Equine"],
      danger_level: 1,
      origin: "European",
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "creature-5",
      title: "Thunderbird",
      url: "/creatures/thunderbird",
      description:
        "A legendary avian creature whose wingbeats create thunder and whose eyes flash lightning across the sky.",
      image: "https://placehold.co/400x300/1a1a2e/f1c40f?text=Thunderbird",
      date: "2024-05-12",
      text: "The Thunderbird is a supernatural being...",
      element: ["Lightning", "Air"],
      creature_type: ["Avian"],
      danger_level: 8,
      origin: "Native American",
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "creature-6",
      title: "Cerberus",
      url: "/creatures/cerberus",
      description:
        "The three-headed dog that guards the gates of the Underworld, preventing the dead from leaving.",
      image: "https://placehold.co/400x300/2d1b69/e74c3c?text=Cerberus",
      date: "2024-06-18",
      text: "Cerberus, also known as the Hound of Hades...",
      element: ["Shadow"],
      creature_type: ["Canine"],
      danger_level: 7,
      origin: "Greek",
    },
    metadata: [],
  },
];

export const creatureFacets: TurFacetGroup[] = [
  {
    name: "element",
    label: { lang: "en", text: "Element" },
    description: "Elemental affinity",
    type: "DEFAULT",
    multivalued: true,
    cleanUpLink: "?q=*&_setlocale=en_US",
    facets: [
      { label: "Fire", count: 2, link: "?q=*&fq[]=element:Fire", selected: false },
      { label: "Water", count: 1, link: "?q=*&fq[]=element:Water", selected: false },
      { label: "Air", count: 2, link: "?q=*&fq[]=element:Air", selected: false },
      { label: "Spirit", count: 2, link: "?q=*&fq[]=element:Spirit", selected: false },
      { label: "Lightning", count: 1, link: "?q=*&fq[]=element:Lightning", selected: false },
      { label: "Shadow", count: 1, link: "?q=*&fq[]=element:Shadow", selected: false },
    ],
  },
  {
    name: "creature_type",
    label: { lang: "en", text: "Creature Type" },
    description: "Classification",
    type: "DEFAULT",
    multivalued: true,
    cleanUpLink: "?q=*&_setlocale=en_US",
    facets: [
      { label: "Reptilian", count: 1, link: "?q=*&fq[]=creature_type:Reptilian", selected: false },
      { label: "Avian", count: 2, link: "?q=*&fq[]=creature_type:Avian", selected: false },
      { label: "Equine", count: 1, link: "?q=*&fq[]=creature_type:Equine", selected: false },
      { label: "Cephalopod", count: 1, link: "?q=*&fq[]=creature_type:Cephalopod", selected: false },
      { label: "Canine", count: 1, link: "?q=*&fq[]=creature_type:Canine", selected: false },
    ],
  },
  {
    name: "origin",
    label: { lang: "en", text: "Origin" },
    description: "Mythological origin",
    type: "DEFAULT",
    multivalued: false,
    cleanUpLink: "?q=*&_setlocale=en_US",
    facets: [
      { label: "European", count: 2, link: "?q=*&fq[]=origin:European", selected: false },
      { label: "Greek", count: 2, link: "?q=*&fq[]=origin:Greek", selected: false },
      { label: "Norse", count: 1, link: "?q=*&fq[]=origin:Norse", selected: false },
      { label: "Native American", count: 1, link: "?q=*&fq[]=origin:Native+American", selected: false },
    ],
  },
];

/* ═══════════════════════════════════════════════════════════════
   SPACE MISSIONS
   ═══════════════════════════════════════════════════════════════ */

export const missionDocuments: TurDocument[] = [
  {
    elevate: false,
    fields: {
      id: "mission-1",
      title: "Apollo 11",
      url: "/missions/apollo-11",
      description:
        "First crewed mission to land on the Moon. Neil Armstrong and Buzz Aldrin made history on July 20, 1969.",
      image: "https://placehold.co/400x300/0a0a23/00d4ff?text=Apollo+11",
      date: "1969-07-16",
      text: "Apollo 11 was the spaceflight...",
      agency: ["NASA"],
      outcome: "Success",
      program: "Apollo",
      launch_date: "Jul 16, 1969",
      duration: "8 days",
      crew_size: 3,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "mission-2",
      title: "Voyager 1",
      url: "/missions/voyager-1",
      description:
        "Space probe launched to study the outer Solar System. Now the most distant human-made object from Earth.",
      image: "https://placehold.co/400x300/0a0a23/ffd700?text=Voyager+1",
      date: "1977-09-05",
      text: "Voyager 1 is a space probe...",
      agency: ["NASA"],
      outcome: "Success",
      program: "Voyager",
      launch_date: "Sep 5, 1977",
      duration: "Ongoing",
      crew_size: 0,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "mission-3",
      title: "Challenger STS-51-L",
      url: "/missions/challenger-sts-51-l",
      description:
        "The Space Shuttle Challenger disintegrated 73 seconds after launch, resulting in the loss of all seven crew members.",
      image: "https://placehold.co/400x300/1a0000/ff4444?text=Challenger",
      date: "1986-01-28",
      text: "The Space Shuttle Challenger disaster...",
      agency: ["NASA"],
      outcome: "Failure",
      program: "Space Shuttle",
      launch_date: "Jan 28, 1986",
      duration: "73 seconds",
      crew_size: 7,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "mission-4",
      title: "Mars Perseverance",
      url: "/missions/mars-perseverance",
      description:
        "NASA rover exploring Mars' Jezero Crater, searching for signs of ancient microbial life.",
      image: "https://placehold.co/400x300/331a00/ff8c00?text=Perseverance",
      date: "2021-02-18",
      text: "Perseverance is a car-sized Mars rover...",
      agency: ["NASA"],
      outcome: "Success",
      program: "Mars Exploration",
      launch_date: "Jul 30, 2020",
      duration: "Ongoing",
      crew_size: 0,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "mission-5",
      title: "Rosetta",
      url: "/missions/rosetta",
      description:
        "ESA mission that performed the first landing on a comet nucleus (67P/Churyumov–Gerasimenko).",
      image: "https://placehold.co/400x300/0a0a23/66ccff?text=Rosetta",
      date: "2004-03-02",
      text: "Rosetta was a space probe built by ESA...",
      agency: ["ESA"],
      outcome: "Success",
      program: "Horizon 2000",
      launch_date: "Mar 2, 2004",
      duration: "12 years",
      crew_size: 0,
    },
    metadata: [],
  },
];

export const missionFacets: TurFacetGroup[] = [
  {
    name: "agency",
    label: { lang: "en", text: "Agency" },
    description: "Space agency",
    type: "DEFAULT",
    multivalued: true,
    cleanUpLink: "?q=*",
    facets: [
      { label: "NASA", count: 4, link: "?q=*&fq[]=agency:NASA", selected: false },
      { label: "ESA", count: 1, link: "?q=*&fq[]=agency:ESA", selected: false },
    ],
  },
  {
    name: "outcome",
    label: { lang: "en", text: "Outcome" },
    description: "Mission outcome",
    type: "DEFAULT",
    multivalued: false,
    cleanUpLink: "?q=*",
    facets: [
      { label: "Success", count: 4, link: "?q=*&fq[]=outcome:Success", selected: false },
      { label: "Failure", count: 1, link: "?q=*&fq[]=outcome:Failure", selected: false },
    ],
  },
];

/* ═══════════════════════════════════════════════════════════════
   VINYL RECORDS
   ═══════════════════════════════════════════════════════════════ */

export const vinylDocuments: TurDocument[] = [
  {
    elevate: false,
    fields: {
      id: "vinyl-1",
      title: "Abbey Road",
      url: "/records/abbey-road",
      description:
        "The Beatles' iconic eleventh studio album featuring the legendary crosswalk cover and medley suite.",
      image: "https://placehold.co/400x400/2d2d2d/ffd700?text=Abbey+Road",
      cover_art: "https://placehold.co/400x400/2d2d2d/ffd700?text=Abbey+Road",
      date: "1969-09-26",
      text: "Abbey Road is the eleventh studio album...",
      artist: "The Beatles",
      genre: ["Rock", "Pop"],
      condition: "Near Mint",
      format: "12\" LP",
      price: 89.99,
      rating: 5,
      is_first_pressing: true,
      year: 1969,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "vinyl-2",
      title: "Kind of Blue",
      url: "/records/kind-of-blue",
      description:
        "Miles Davis' masterpiece that defined modal jazz. The best-selling jazz album of all time.",
      image: "https://placehold.co/400x400/1a1a3e/4ecdc4?text=Kind+of+Blue",
      cover_art: "https://placehold.co/400x400/1a1a3e/4ecdc4?text=Kind+of+Blue",
      date: "1959-08-17",
      text: "Kind of Blue is a studio album...",
      artist: "Miles Davis",
      genre: ["Jazz"],
      condition: "Very Good Plus",
      format: "12\" LP",
      price: 120.0,
      rating: 5,
      is_first_pressing: true,
      year: 1959,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "vinyl-3",
      title: "Rumours",
      url: "/records/rumours",
      description:
        "Fleetwood Mac's magnum opus — an emotional rollercoaster woven from the band's personal turmoil.",
      image: "https://placehold.co/400x400/2d1b2e/ff69b4?text=Rumours",
      cover_art: "https://placehold.co/400x400/2d1b2e/ff69b4?text=Rumours",
      date: "1977-02-04",
      text: "Rumours is the eleventh studio album...",
      artist: "Fleetwood Mac",
      genre: ["Rock", "Pop"],
      condition: "Very Good",
      format: "12\" LP",
      price: 45.0,
      rating: 4,
      is_first_pressing: false,
      year: 1977,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "vinyl-4",
      title: "A Love Supreme",
      url: "/records/a-love-supreme",
      description:
        "John Coltrane's spiritual jazz masterwork, a four-part suite of devotion and transcendence.",
      image: "https://placehold.co/400x400/0a0a2e/ff6347?text=A+Love+Supreme",
      cover_art: "https://placehold.co/400x400/0a0a2e/ff6347?text=A+Love+Supreme",
      date: "1965-02-01",
      text: "A Love Supreme is a studio album...",
      artist: "John Coltrane",
      genre: ["Jazz", "Spiritual Jazz"],
      condition: "Good Plus",
      format: "12\" LP",
      price: 200.0,
      rating: 5,
      is_first_pressing: true,
      year: 1965,
    },
    metadata: [],
  },
  {
    elevate: false,
    fields: {
      id: "vinyl-5",
      title: "OK Computer",
      url: "/records/ok-computer",
      description:
        "Radiohead's third album — a prescient meditation on technology, alienation, and modern anxiety.",
      image: "https://placehold.co/400x400/1a2a3a/00bcd4?text=OK+Computer",
      cover_art: "https://placehold.co/400x400/1a2a3a/00bcd4?text=OK+Computer",
      date: "1997-06-16",
      text: "OK Computer is the third studio album...",
      artist: "Radiohead",
      genre: ["Alternative Rock", "Art Rock"],
      condition: "Near Mint",
      format: "12\" LP",
      price: 65.0,
      rating: 5,
      is_first_pressing: false,
      year: 1997,
    },
    metadata: [],
  },
];

export const vinylFacets: TurFacetGroup[] = [
  {
    name: "genre",
    label: { lang: "en", text: "Genre" },
    description: "Music genre",
    type: "DEFAULT",
    multivalued: true,
    cleanUpLink: "?q=*",
    facets: [
      { label: "Rock", count: 2, link: "?q=*&fq[]=genre:Rock", selected: false },
      { label: "Jazz", count: 2, link: "?q=*&fq[]=genre:Jazz", selected: false },
      { label: "Pop", count: 2, link: "?q=*&fq[]=genre:Pop", selected: false },
      { label: "Alternative Rock", count: 1, link: "?q=*&fq[]=genre:Alternative+Rock", selected: false },
    ],
  },
  {
    name: "condition",
    label: { lang: "en", text: "Condition" },
    description: "Vinyl condition",
    type: "DEFAULT",
    multivalued: false,
    cleanUpLink: "?q=*",
    facets: [
      { label: "Near Mint", count: 2, link: "?q=*&fq[]=condition:Near+Mint", selected: false },
      { label: "Very Good Plus", count: 1, link: "?q=*&fq[]=condition:Very+Good+Plus", selected: false },
      { label: "Very Good", count: 1, link: "?q=*&fq[]=condition:Very+Good", selected: false },
      { label: "Good Plus", count: 1, link: "?q=*&fq[]=condition:Good+Plus", selected: false },
    ],
  },
];

/* ═══════════════════════════════════════════════════════════════
   Pagination fixtures
   ═══════════════════════════════════════════════════════════════ */

export const samplePagination: TurPaginationItem[] = [
  { href: "?q=*&p=1", page: 1, text: "FIRST", type: "FIRST" },
  { href: "?q=*&p=1", page: 1, text: "PREVIOUS", type: "PREVIOUS" },
  { href: "", page: 1, text: "1", type: "CURRENT" },
  { href: "?q=*&p=2", page: 2, text: "2", type: "PAGE" },
  { href: "?q=*&p=3", page: 3, text: "3", type: "PAGE" },
  { href: "?q=*&p=4", page: 4, text: "4", type: "PAGE" },
  { href: "?q=*&p=5", page: 5, text: "5", type: "PAGE" },
  { href: "", page: 0, text: "...", type: "ELLIPSIS" },
  { href: "?q=*&p=10", page: 10, text: "10", type: "PAGE" },
  { href: "?q=*&p=2", page: 2, text: "NEXT", type: "NEXT" },
  { href: "?q=*&p=10", page: 10, text: "LAST", type: "LAST" },
];

export const simplePagination: TurPaginationItem[] = [
  { href: "", page: 1, text: "1", type: "CURRENT" },
  { href: "?q=*&p=2", page: 2, text: "2", type: "PAGE" },
  { href: "?q=*&p=3", page: 3, text: "3", type: "PAGE" },
  { href: "?q=*&p=2", page: 2, text: "NEXT", type: "NEXT" },
];

/* ═══════════════════════════════════════════════════════════════
   Helper: Build a full TurSearchResponse
   ═══════════════════════════════════════════════════════════════ */

export function buildSearchResponse(
  documents: TurDocument[],
  facets: TurFacetGroup[] = [],
  pagination: TurPaginationItem[] = samplePagination,
  query = "*",
): TurSearchResponse {
  return {
    queryContext: {
      count: documents.length,
      defaultFields,
      index: "sample",
      limit: 10,
      offset: 0,
      page: 1,
      pageCount: Math.ceil(documents.length / 10),
      pageEnd: Math.min(documents.length, 10),
      pageStart: 1,
      query: { queryString: query, sort: "relevance", locale: "en_US" },
      responseTime: 42,
      facetType: "DEFAULT",
      facetItemType: "AND",
    },
    results: { document: documents },
    pagination,
    widget: {
      facet: facets,
      facetToRemove: {
        name: "",
        label: { lang: "en", text: "" },
        description: "",
        type: "",
        multivalued: false,
        cleanUpLink: "",
        facets: [],
      },
      similar: "",
      spellCheck: {
        correctedText: false,
        usingCorrectedText: false,
        original: { link: "", text: "" },
        corrected: { link: "", text: "" },
      },
      locales: [
        { locale: "en_US", link: "?q=*&_setlocale=en_US" },
        { locale: "pt_BR", link: "?q=*&_setlocale=pt_BR" },
      ],
      cleanUpFacets: "?q=*&_setlocale=en_US",
    },
  };
}

/* ═══════════════════════════════════════════════════════════════
   Helper: Resolve documents (same as SDK's resolveDocuments)
   ═══════════════════════════════════════════════════════════════ */

export function resolveDocsMock(documents: TurDocument[]): ResolvedDocument[] {
  return documents.map((doc) => ({
    url: doc.fields.url ?? "",
    title: doc.fields.title ?? "",
    description: doc.fields.description ?? "",
    date: doc.fields.date ?? "",
    image: doc.fields.image ?? "",
    text: doc.fields.text ?? "",
    raw: doc,
  }));
}

/* ═══════════════════════════════════════════════════════════════
   Chat responses
   ═══════════════════════════════════════════════════════════════ */

export const sampleChatResponse: TurChatResponse = {
  text: "Dragons are legendary creatures found in many world mythologies. They are typically depicted as large, serpentine creatures with wings and the ability to breathe fire.",
  enabled: true,
};

/* ═══════════════════════════════════════════════════════════════
   Sort options
   ═══════════════════════════════════════════════════════════════ */

export const sampleSortOptions: TurSortOption[] = [
  { value: "relevance", label: "Relevance" },
  { value: "newest", label: "Newest First" },
  { value: "oldest", label: "Oldest First" },
  { value: "title_str:asc", label: "Title A–Z" },
  { value: "title_str:desc", label: "Title Z–A" },
];

/* ═══════════════════════════════════════════════════════════════
   Pre-built complete responses for each theme
   ═══════════════════════════════════════════════════════════════ */

export const creaturesResponse = buildSearchResponse(
  creatureDocuments,
  creatureFacets,
  samplePagination,
  "*",
);

export const missionsResponse = buildSearchResponse(
  missionDocuments,
  missionFacets,
  samplePagination,
  "*",
);

export const vinylResponse = buildSearchResponse(
  vinylDocuments,
  vinylFacets,
  samplePagination,
  "*",
);
