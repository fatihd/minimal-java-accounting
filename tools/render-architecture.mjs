import { writeFileSync } from "node:fs";

const output = process.argv[2];
if (!output) {
  throw new Error("usage: node tools/render-architecture.mjs <output.svg>");
}

const ADAPTER_HEIGHT = 100;
const PORT_HEIGHT = Math.round(ADAPTER_HEIGHT * 2 / 3);
const CORE_HEIGHT = Math.round(ADAPTER_HEIGHT * 1.5);
const packageLabel = (moduleName) =>
  `d.m.${moduleName.replaceAll("-", "")}`;

const kinds = {
  adapter: { fill: "#fff0eb", stroke: "#c65b43" },
  port: { fill: "#eaf3ff", stroke: "#3978b8" },
  core: { fill: "#fff3cd", stroke: "#a97900" },
  infrastructurePort: { fill: "#efeaff", stroke: "#7157b5" },
  infrastructureAdapter: { fill: "#eaf8ee", stroke: "#3e8b58" },
  anchor: { fill: "#f3f5f6", stroke: "#65747b" },
};

const nodes = [
  { id: "app", x: 45, y: 565, width: 220, height: ADAPTER_HEIGHT, kind: "anchor", label: "app", role: "composition root" },
  { id: "shared", x: 1740, y: 565, width: 220, height: ADAPTER_HEIGHT, kind: "anchor", label: "shared", role: "common values" },
  { id: "console", x: 760, y: 130, width: 500, height: ADAPTER_HEIGHT, kind: "adapter", label: "adapters.api.console", role: "command-line translation" },
  { id: "api", x: 825, y: 340, width: 370, height: PORT_HEIGHT, kind: "port", label: "ports.api", role: "TransactionProcessor" },
  { id: "application", x: 560, y: 540, width: 380, height: CORE_HEIGHT, kind: "core", label: "application", role: "use cases and workflows" },
  { id: "core", x: 1070, y: 540, width: 330, height: CORE_HEIGHT, kind: "core", label: "core", role: "entities and rules" },
  { id: "persistencePorts", x: 440, y: 825, width: 470, height: PORT_HEIGHT, kind: "infrastructurePort", label: "ports.persistence", role: "repository contracts" },
  { id: "messagingPorts", x: 1100, y: 825, width: 470, height: PORT_HEIGHT, kind: "infrastructurePort", label: "ports.messaging", role: "send · receive · handle" },
  { id: "memoryPersistence", x: 360, y: 1065, width: 600, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.persistence.in-memory", role: "ConcurrentHashMap repositories" },
  { id: "memoryMessaging", x: 1040, y: 1065, width: 600, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.in-memory", role: "queues, topics and pollers" },
];

const node = (id) => nodes.find((candidate) => candidate.id === id);
const centerX = (id) => node(id).x + node(id).width / 2;
const centerY = (id) => node(id).y + node(id).height / 2;
const top = (id) => node(id).y;
const bottom = (id) => node(id).y + node(id).height;
const left = (id) => node(id).x;
const right = (id) => node(id).x + node(id).width;

const edges = [
  `M ${centerX("console")} ${bottom("console")} L ${centerX("api")} ${top("api")}`,
  `M ${centerX("application")} ${top("application")} L ${centerX("application")} 470 L 900 470 L 900 ${bottom("api")}`,
  `M ${right("api")} ${centerY("api")} L 1640 ${centerY("api")} L 1640 485 L ${centerX("shared")} 485 L ${centerX("shared")} ${top("shared")}`,
  `M ${right("application")} ${centerY("application")} L ${left("core")} ${centerY("core")}`,
  `M ${right("core")} ${centerY("core")} L ${left("shared")} ${centerY("shared")}`,
  `M 680 ${bottom("application")} L 680 ${top("persistencePorts")}`,
  `M 830 ${bottom("application")} L 830 755 L ${centerX("messagingPorts")} 755 L ${centerX("messagingPorts")} ${top("messagingPorts")}`,
  `M ${right("persistencePorts")} ${centerY("persistencePorts")} C 1020 785 1080 735 1170 ${bottom("core")}`,
  `M ${centerX("memoryPersistence")} ${top("memoryPersistence")} L ${centerX("persistencePorts")} ${bottom("persistencePorts")}`,
  `M 930 ${top("memoryPersistence") + 25} C 1010 970 1000 800 1195 ${bottom("core")}`,
  `M ${centerX("memoryMessaging")} ${top("memoryMessaging")} L ${centerX("messagingPorts")} ${bottom("messagingPorts")}`,
];

const bands = [
  { y: 105, height: 150, fill: "#fff8f5", stroke: "#e7b5a8", label: "API ADAPTER" },
  { y: 295, height: 155, fill: "#f4f8ff", stroke: "#a9c7e8", label: "API PORT" },
  { y: 495, height: 230, fill: "#fffaf0", stroke: "#dbc88f", label: "CORE" },
  { y: 775, height: 175, fill: "#f8f5ff", stroke: "#c8b9e8", label: "INFRASTRUCTURE PORTS" },
  { y: 1015, height: 180, fill: "#f2fbf5", stroke: "#acd7b9", label: "INFRASTRUCTURE ADAPTERS" },
];

const escape = (value) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");

const bandSvg = bands.map((band) => `
  <rect class="band" x="320" y="${band.y}" width="1370" height="${band.height}" fill="${band.fill}" stroke="${band.stroke}"/>
  <text class="layer-label" x="345" y="${band.y + 28}">${band.label}</text>`).join("");

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
<svg xmlns="http://www.w3.org/2000/svg" width="2000" height="1300" viewBox="0 0 2000 1300" role="img" aria-labelledby="title description">
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
      .module { font: 700 22px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #172126; text-anchor: middle; }
      .package { font: 400 13px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; fill: #526168; text-anchor: middle; }
      .note { font: 400 16px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; }
      .band { stroke-width: 1.5; stroke-dasharray: 7 9; rx: 18; }
      .node { stroke-width: 2.5; rx: 14; filter: url(#shadow); }
      .edge { fill: none; stroke: #263238; stroke-width: 3.25; marker-end: url(#arrow); stroke-linecap: round; stroke-linejoin: round; }
    </style>
  </defs>
  <rect class="canvas" x="0" y="0" width="2000" height="1300"/>
  <text class="title" x="55" y="58">Minimal Java Accounting — ports and adapters</text>
  <text class="subtitle" x="55" y="91">Composition left · shared foundation right · API at top · infrastructure at bottom</text>
${bandSvg}
${edgeSvg}
${nodeSvg}
  <line x1="55" y1="1235" x2="1945" y2="1235" stroke="#d7dee1" stroke-width="1.5"/>
  <text class="note" x="55" y="1272">d.m. = dev.minimalaccounting; hyphens are omitted in Java. Adapter : port : core height = ${ADAPTER_HEIGHT} : ${PORT_HEIGHT} : ${CORE_HEIGHT}.</text>
</svg>
`;

writeFileSync(output, svg);
