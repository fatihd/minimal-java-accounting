import { writeFileSync } from "node:fs";

const output = process.argv[2];
if (!output) {
  throw new Error("usage: node tools/render-architecture.mjs <output.svg>");
}

const ADAPTER_HEIGHT = 100;
const PORT_HEIGHT = Math.round(ADAPTER_HEIGHT * 2 / 3);
const CORE_HEIGHT = Math.round(ADAPTER_HEIGHT * 1.5);
const packageLabel = (moduleName) => `d.m.${moduleName.replaceAll("-", "")}`;

const kinds = {
  adapter: { fill: "#fff0eb", stroke: "#c65b43" },
  port: { fill: "#eaf3ff", stroke: "#3978b8" },
  core: { fill: "#fff3cd", stroke: "#a97900" },
  infrastructurePort: { fill: "#efeaff", stroke: "#7157b5" },
  infrastructureAdapter: { fill: "#eaf8ee", stroke: "#3e8b58" },
  anchor: { fill: "#f3f5f6", stroke: "#65747b" },
};

const nodes = [
  { id: "app", x: 35, y: 585, width: 220, height: ADAPTER_HEIGHT, kind: "anchor", label: "app", role: "composition root" },
  { id: "shared", x: 2245, y: 585, width: 220, height: ADAPTER_HEIGHT, kind: "anchor", label: "shared", role: "common values" },
  { id: "console", x: 585, y: 130, width: 500, height: ADAPTER_HEIGHT, kind: "adapter", label: "adapters.api.console", role: "command-line translation" },
  { id: "http", x: 1415, y: 130, width: 500, height: ADAPTER_HEIGHT, kind: "adapter", label: "adapters.api.http", role: "HTTP and JSON translation" },
  { id: "api", x: 1040, y: 340, width: 420, height: PORT_HEIGHT, kind: "port", label: "ports.api", role: "TransactionProcessor" },
  { id: "application", x: 480, y: 535, width: 450, height: CORE_HEIGHT, kind: "core", label: "application", role: "use cases and handlers" },
  { id: "core", x: 1040, y: 535, width: 420, height: CORE_HEIGHT, kind: "core", label: "core", role: "aggregates and process manager" },
  { id: "observability", x: 1570, y: 535, width: 450, height: CORE_HEIGHT, kind: "core", label: "observability", role: "boundary decorators" },
  { id: "persistencePorts", x: 590, y: 835, width: 520, height: PORT_HEIGHT, kind: "infrastructurePort", label: "ports.persistence", role: "repositories and transactions" },
  { id: "messagingPorts", x: 1390, y: 835, width: 520, height: PORT_HEIGHT, kind: "infrastructurePort", label: "ports.messaging", role: "send · receive · handle" },
  { id: "memoryPersistence", x: 305, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.persistence.in-memory", role: "detached memory repositories" },
  { id: "postgresqlPersistence", x: 685, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.persistence.postgresql", role: "plain JDBC repositories" },
  { id: "postgresqlMessaging", x: 1065, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.postgresql", role: "transactional outbox" },
  { id: "memoryMessaging", x: 1445, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.in-memory", role: "memory queues and topics" },
  { id: "artemis", x: 1825, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.artemis", role: "durable queues and topics" },
];

const node = (id) => nodes.find((candidate) => candidate.id === id);
const centerX = (id) => node(id).x + node(id).width / 2;
const centerY = (id) => node(id).y + node(id).height / 2;
const top = (id) => node(id).y;
const bottom = (id) => node(id).y + node(id).height;
const left = (id) => node(id).x;
const right = (id) => node(id).x + node(id).width;

const edges = [
  `M ${centerX("console")} ${bottom("console")} L ${centerX("console")} 280 L 1140 280 L 1140 ${top("api")}`,
  `M ${centerX("http")} ${bottom("http")} L ${centerX("http")} 280 L 1360 280 L 1360 ${top("api")}`,
  `M 650 ${top("application")} L 650 470 L 1100 470 L 1100 ${bottom("api")}`,
  `M 1795 ${top("observability")} L 1795 455 L 1400 455 L 1400 ${bottom("api")}`,
  `M ${right("api")} ${centerY("api")} L 2185 ${centerY("api")} L 2185 515 L ${centerX("shared")} 515 L ${centerX("shared")} ${top("shared")}`,
  `M ${right("application")} ${centerY("application")} L ${left("core")} ${centerY("core")}`,
  `M ${right("core")} ${centerY("core")} L 1505 ${centerY("core")} L 1505 745 L 2200 745 L 2200 ${centerY("shared")} L ${left("shared")} ${centerY("shared")}`,
  `M 650 ${bottom("application")} L 650 770 L 760 770 L 760 ${top("persistencePorts")}`,
  `M 850 ${bottom("application")} L 850 750 L 1510 750 L 1510 ${top("messagingPorts")}`,
  `M 1740 ${bottom("observability")} L 1740 ${top("messagingPorts")}`,
  `M 1040 ${top("persistencePorts")} L 1040 770 L 1190 770 L 1190 ${bottom("core")}`,
  `M ${centerX("memoryPersistence")} ${top("memoryPersistence")} L 700 ${bottom("persistencePorts")}`,
  `M ${centerX("postgresqlPersistence")} ${top("postgresqlPersistence")} L 900 ${bottom("persistencePorts")}`,
  `M 570 ${top("memoryPersistence")} C 650 1010 980 760 1160 ${bottom("core")}`,
  `M 930 ${top("postgresqlPersistence")} C 980 990 1080 780 1240 ${bottom("core")}`,
  `M 1170 ${top("postgresqlMessaging")} L 1050 965 L 1020 ${bottom("persistencePorts")}`,
  `M 1320 ${top("postgresqlMessaging")} L 1510 965 L 1510 ${bottom("messagingPorts")}`,
  `M ${left("postgresqlMessaging")} 1165 L ${right("postgresqlPersistence")} 1165`,
  `M ${centerX("memoryMessaging")} ${top("memoryMessaging")} L 1610 ${bottom("messagingPorts")}`,
  `M ${centerX("artemis")} ${top("artemis")} L 1810 ${bottom("messagingPorts")}`,
];

const bands = [
  { y: 105, height: 150, fill: "#fff8f5", stroke: "#e7b5a8", label: "API ADAPTERS" },
  { y: 295, height: 155, fill: "#f4f8ff", stroke: "#a9c7e8", label: "API PORT" },
  { y: 490, height: 235, fill: "#fffaf0", stroke: "#dbc88f", label: "CORE AND BOUNDARY LOGIC" },
  { y: 790, height: 175, fill: "#f8f5ff", stroke: "#c8b9e8", label: "INFRASTRUCTURE PORTS" },
  { y: 1040, height: 200, fill: "#f2fbf5", stroke: "#acd7b9", label: "INFRASTRUCTURE ADAPTERS" },
];

const escape = (value) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");

const bandSvg = bands.map((band) => `
  <rect class="band" x="290" y="${band.y}" width="1920" height="${band.height}" fill="${band.fill}" stroke="${band.stroke}"/>
  <text class="layer-label" x="315" y="${band.y + 28}">${band.label}</text>`).join("");

const edgeSvg = edges.map((path) => `  <path class="edge" d="${path}"/>`).join("\n");

const nodeSvg = nodes.map((item) => {
  const colors = kinds[item.kind];
  const midpoint = item.y + item.height / 2;
  return `
  <g>
    <title>${escape(item.label)} — ${escape(item.role)}</title>
    <rect class="node" x="${item.x}" y="${item.y}" width="${item.width}" height="${item.height}" fill="${colors.fill}" stroke="${colors.stroke}"/>
    <text class="module" x="${item.x + item.width / 2}" y="${midpoint - 6}">${escape(item.label)}</text>
    <text class="package" x="${item.x + item.width / 2}" y="${midpoint + 23}">${escape(packageLabel(item.label))}</text>
  </g>`;
}).join("");

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="2500" height="1375" viewBox="0 0 2500 1375" role="img" aria-labelledby="title description">
  <title id="title">Minimal Java Accounting module architecture</title>
  <desc id="description">Two-axis ports and adapters diagram pairing each Gradle module with its Java package root, with adapter height ${ADAPTER_HEIGHT}, port height ${PORT_HEIGHT}, and core height ${CORE_HEIGHT}.</desc>
  <defs>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="#263238"/>
    </marker>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="3" stdDeviation="4" flood-color="#000000" flood-opacity="0.14"/>
    </filter>
    <style>
      .canvas { fill: #ffffff; }
      .title { font: 700 34px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #172126; }
      .subtitle { font: 400 18px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; }
      .layer-label { font: 700 15px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; letter-spacing: 1.6px; fill: #65747b; }
      .module { font: 700 19px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #172126; text-anchor: middle; }
      .package { font: 400 12px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; fill: #526168; text-anchor: middle; }
      .note { font: 400 16px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; }
      .band { stroke-width: 1.5; stroke-dasharray: 7 9; rx: 18; }
      .node { stroke-width: 2.5; rx: 14; filter: url(#shadow); }
      .edge { fill: none; stroke: #263238; stroke-width: 3; marker-end: url(#arrow); stroke-linecap: round; stroke-linejoin: round; }
    </style>
  </defs>
  <rect class="canvas" x="0" y="0" width="2500" height="1375"/>
  <text class="title" x="55" y="58">Minimal Java Accounting — ports and adapters</text>
  <text class="subtitle" x="55" y="91">Composition left · shared foundation right · API at top · infrastructure at bottom</text>
${bandSvg}
${edgeSvg}
${nodeSvg}
  <line x1="55" y1="1290" x2="2445" y2="1290" stroke="#d7dee1" stroke-width="1.5"/>
  <text class="note" x="55" y="1328">Arrows point from a module to its production dependency; app arrows are omitted for legibility. d.m. = dev.minimalaccounting.</text>
  <text class="note" x="55" y="1355">Adapter : port : core height = ${ADAPTER_HEIGHT} : ${PORT_HEIGHT} : ${CORE_HEIGHT}. Java package names omit Gradle hyphens.</text>
</svg>
`;

writeFileSync(output, svg);
