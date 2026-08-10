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

const commonNodes = [
  { id: "app", x: 35, y: 585, width: 220, height: ADAPTER_HEIGHT, kind: "anchor", label: "app", role: "composition root" },
  { id: "infrastructure", x: 35, y: 715, width: 220, height: CORE_HEIGHT, kind: "anchor", label: "infrastructure", role: "boundary decorators and runtime signals" },
  { id: "http", x: 1000, y: 130, width: 500, height: ADAPTER_HEIGHT, kind: "adapter", label: "adapters.api.http", role: "HTTP and JSON translation" },
  { id: "application", x: 480, y: 535, width: 450, height: CORE_HEIGHT, kind: "core", label: "application", role: "use cases and handlers" },
  { id: "model", x: 1040, y: 535, width: 420, height: CORE_HEIGHT, kind: "core", label: "model", role: "aggregates and process manager" },
  { id: "artemis", x: 360, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.artemis", role: "durable queues and topics" },
  { id: "postgresqlMessaging", x: 730, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.postgresql", role: "transactional outbox" },
  { id: "postgresqlPersistence", x: 1275, y: 1090, width: 350, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.persistence.postgresql", role: "plain JDBC repositories" },
];

const nodes = [
  ...commonNodes,
  { id: "apiPort", x: 1040, y: 340, width: 420, height: PORT_HEIGHT, kind: "port", label: "api", detail: "Handler<SubmitTransaction>", role: "logical inbound API port", logical: true },
  { id: "messagingPort", x: 360, y: 835, width: 660, height: PORT_HEIGHT, kind: "infrastructurePort", label: "messaging", detail: "Sender<T> · Receiver<T> · Handler<T>", role: "logical messaging ports", logical: true },
  { id: "persistencePort", x: 1080, y: 835, width: 740, height: PORT_HEIGHT, kind: "infrastructurePort", label: "persistence", detail: "Repository<Identifier, Aggregate> · TransactionBoundary", role: "logical persistence ports", logical: true },
  { id: "sharedPorts", x: 2245, y: 835, width: 220, height: ADAPTER_HEIGHT, kind: "anchor", label: "shared.ports", role: "generic port contracts" },
];

const node = (id) => nodes.find((candidate) => candidate.id === id);
const centerX = (id) => node(id).x + node(id).width / 2;
const centerY = (id) => node(id).y + node(id).height / 2;
const top = (id) => node(id).y;
const bottom = (id) => node(id).y + node(id).height;
const left = (id) => node(id).x;
const right = (id) => node(id).x + node(id).width;

const edges = [
  `M ${centerX("app")} ${bottom("app")} L ${centerX("infrastructure")} ${top("infrastructure")}`,
  `M ${centerX("http")} ${bottom("http")} L ${centerX("http")} ${top("apiPort")}`,
  `M ${right("application")} ${centerY("application")} L ${left("model")} ${centerY("model")}`,
  `M 650 ${bottom("application")} L 650 770 L 690 770 L 690 ${top("messagingPort")}`,
  `M 850 ${bottom("application")} L 850 750 L 1450 750 L 1450 ${top("persistencePort")}`,
  `M ${right("postgresqlMessaging")} ${centerY("postgresqlMessaging")} L ${left("postgresqlPersistence")} ${centerY("postgresqlPersistence")}`,
];

const implementationEdges = [
  `M 650 ${top("application")} L 650 470 L 1100 470 L 1100 ${bottom("apiPort")}`,
  `M ${centerX("artemis")} ${top("artemis")} L 550 ${bottom("messagingPort")}`,
  `M ${centerX("postgresqlMessaging")} ${top("postgresqlMessaging")} L 900 ${bottom("messagingPort")}`,
  `M ${centerX("postgresqlPersistence")} ${top("postgresqlPersistence")} L 1450 ${bottom("persistencePort")}`,
];

const bands = [
  { x: 290, y: 105, width: 1920, height: 150, fill: "#fff8f5", stroke: "#e7b5a8", label: "API ADAPTERS" },
  { x: 290, y: 295, width: 1920, height: 155, fill: "#f4f8ff", stroke: "#a9c7e8", label: "LOGICAL API PORT" },
  { x: 290, y: 490, width: 1920, height: 235, fill: "#fffaf0", stroke: "#dbc88f", label: "CORE" },
  { x: 290, y: 790, width: 1920, height: 175, fill: "#f8f5ff", stroke: "#c8b9e8", label: "LOGICAL INFRASTRUCTURE PORTS" },
  { x: 290, y: 1040, width: 1920, height: 200, fill: "#f2fbf5", stroke: "#acd7b9", label: "INFRASTRUCTURE ADAPTERS" },
];

const title = "Minimal Java Accounting — logical ports and adapters";
const subtitle = "Dashed logical ports · generic contracts in shared.ports · Gradle modules remain solid";
const description = "Two-axis ports and adapters diagram with dashed logical API, persistence, and messaging ports backed by generic contracts in the shared ports module.";
const firstNote = "Solid boxes are Gradle modules; dashed boxes are logical typed ports backed by the generic contracts in shared.ports.";
const secondNote = `Hollow triangles mean implements; filled arrows mean uses or depends on. Logical ports are ${PORT_HEIGHT} units high. d.m. = dev.minimalaccounting.`;

const escape = (value) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");

const bandSvg = bands.map((band) => `
  <rect class="band" x="${band.x}" y="${band.y}" width="${band.width}" height="${band.height}" fill="${band.fill}" stroke="${band.stroke}"/>
  <text class="layer-label" x="${band.x + 25}" y="${band.y + 28}">${band.label}</text>`).join("");

const edgeSvg = [
  ...edges.map((path) => `  <path class="edge" d="${path}"/>`),
  ...implementationEdges.map(
    (path) => `  <path class="edge implementation-edge" d="${path}"/>`,
  ),
].join("\n");

const nodeSvg = nodes.map((item) => {
  const colors = kinds[item.kind];
  const midpoint = item.y + item.height / 2;
  return `
  <g>
    <title>${escape(item.label)} — ${escape(item.role)}</title>
    <rect class="node${item.logical ? " logical-node" : ""}" x="${item.x}" y="${item.y}" width="${item.width}" height="${item.height}" fill="${colors.fill}" stroke="${colors.stroke}"/>
    <text class="module" x="${item.x + item.width / 2}" y="${midpoint - 6}">${escape(item.label)}</text>
    <text class="package" x="${item.x + item.width / 2}" y="${midpoint + 23}">${escape(item.detail ?? packageLabel(item.label))}</text>
  </g>`;
}).join("");

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="2500" height="1375" viewBox="0 0 2500 1375" role="img" aria-labelledby="title description">
  <title id="title">${escape(title)}</title>
  <desc id="description">${escape(description)}</desc>
  <defs>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="#263238"/>
    </marker>
    <marker id="implementation-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="#ffffff" stroke="#263238" stroke-width="1.5"/>
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
      .logical-node { stroke-dasharray: 10 7; filter: none; }
      .edge { fill: none; stroke: #263238; stroke-width: 3; marker-end: url(#arrow); stroke-linecap: round; stroke-linejoin: round; }
      .implementation-edge { marker-end: url(#implementation-arrow); }
    </style>
  </defs>
  <rect class="canvas" x="0" y="0" width="2500" height="1375"/>
  <text class="title" x="55" y="58">${escape(title)}</text>
  <text class="subtitle" x="55" y="91">${escape(subtitle)}</text>
${bandSvg}
${edgeSvg}
${nodeSvg}
  <line x1="55" y1="1290" x2="2445" y2="1290" stroke="#d7dee1" stroke-width="1.5"/>
  <text class="note" x="55" y="1328">${escape(firstNote)}</text>
  <text class="note" x="55" y="1355">${escape(secondNote)}</text>
</svg>
`;

writeFileSync(output, svg);
