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
  shared: { fill: "#f3f5f6", stroke: "#65747b" },
};

const nodes = [
  { id: "app", x: 35, y: 560, width: 220, height: ADAPTER_HEIGHT, kind: "shared", label: "app", role: "composition root" },
  { id: "infrastructure", x: 35, y: 700, width: 220, height: CORE_HEIGHT, kind: "shared", label: "infrastructure", role: "boundary decorators and runtime signals" },
  { id: "http", x: 1080, y: 125, width: 440, height: ADAPTER_HEIGHT, kind: "adapter", label: "adapters.api.http", role: "HTTP and JSON translation" },
  { id: "apiPort", x: 1090, y: 335, width: 420, height: PORT_HEIGHT, kind: "port", label: "api", detail: "Handler<SubmitTransaction>", role: "logical inbound API port", logical: true },
  { id: "application", x: 545, y: 535, width: 520, height: CORE_HEIGHT, kind: "core", label: "application", role: "use cases and handlers" },
  { id: "model", x: 1250, y: 535, width: 500, height: CORE_HEIGHT, kind: "core", label: "model", role: "aggregates and process manager" },
  { id: "messagingPort", x: 480, y: 835, width: 650, height: PORT_HEIGHT, kind: "infrastructurePort", label: "messaging", detail: "Sender<T> · Receiver<T> · Handler<T>", role: "logical messaging ports", logical: true },
  { id: "persistencePort", x: 1200, y: 835, width: 650, height: PORT_HEIGHT, kind: "infrastructurePort", label: "persistence", detail: "Repository<Identifier, Aggregate> · TransactionBoundary", role: "logical persistence ports", logical: true },
  { id: "sharedPorts", x: 2220, y: 835, width: 240, height: ADAPTER_HEIGHT, kind: "shared", label: "shared.ports", role: "generic port contracts" },
  { id: "outbox", x: 480, y: 1085, width: 650, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.messaging.outbox", role: "PostgreSQL outbox to Artemis" },
  { id: "postgresqlPersistence", x: 1220, y: 1085, width: 610, height: ADAPTER_HEIGHT, kind: "infrastructureAdapter", label: "adapters.persistence.postgresql", role: "plain JDBC repositories" },
  { id: "sharedPostgresql", x: 1985, y: 1085, width: 475, height: ADAPTER_HEIGHT, kind: "shared", label: "shared.adapters.postgresql", role: "shared JDBC transactions and migrations" },
];

const resources = [
  { id: "artemisBroker", x: 550, y: 1350, width: 500, height: 70, orientation: "horizontal", label: "Apache Artemis", detail: "broker" },
  { id: "postgresqlDatabase", x: 1440, y: 1305, width: 170, height: 165, orientation: "vertical", label: "PostgreSQL", detail: "database" },
];

const node = (id) => nodes.find((candidate) => candidate.id === id);
const resource = (id) => resources.find((candidate) => candidate.id === id);
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
  `M 700 ${bottom("application")} L 700 ${top("messagingPort")}`,
  `M 950 ${bottom("application")} L 950 770 L 1450 770 L 1450 ${top("persistencePort")}`,
  `M 805 ${bottom("outbox")} L 805 ${resource("artemisBroker").y}`,
  `M 1080 ${bottom("outbox")} L 1080 1260 L 1400 1260 L 1400 ${resource("postgresqlDatabase").y + resource("postgresqlDatabase").height / 2} L ${resource("postgresqlDatabase").x} ${resource("postgresqlDatabase").y + resource("postgresqlDatabase").height / 2}`,
  `M ${centerX("postgresqlPersistence")} ${bottom("postgresqlPersistence")} L ${centerX("postgresqlPersistence")} ${resource("postgresqlDatabase").y}`,
  `M 1650 ${top("persistencePort")} L 1650 ${bottom("model")}`,
];

const implementationEdges = [
  `M 760 ${top("application")} L 760 470 L 1150 470 L 1150 ${bottom("apiPort")}`,
  `M ${centerX("outbox")} ${top("outbox")} L ${centerX("outbox")} ${bottom("messagingPort")}`,
  `M ${centerX("postgresqlPersistence")} ${top("postgresqlPersistence")} L ${centerX("postgresqlPersistence")} ${bottom("persistencePort")}`,
];

const bands = [
  { x: 290, y: 100, width: 1900, height: 150, fill: "#fff8f5", stroke: "#e7b5a8", label: "API ADAPTERS" },
  { x: 290, y: 295, width: 1900, height: 150, fill: "#f4f8ff", stroke: "#a9c7e8", label: "LOGICAL API PORT" },
  { x: 290, y: 490, width: 1900, height: 235, fill: "#fffaf0", stroke: "#dbc88f", label: "CORE" },
  { x: 290, y: 790, width: 1900, height: 175, fill: "#f8f5ff", stroke: "#c8b9e8", label: "LOGICAL INFRASTRUCTURE PORTS" },
  { x: 290, y: 1040, width: 1900, height: 190, fill: "#f2fbf5", stroke: "#acd7b9", label: "INFRASTRUCTURE ADAPTERS" },
  { x: 290, y: 1300, width: 1900, height: 180, fill: "#f7f8f9", stroke: "#c4cdd1", label: "EXTERNAL SYSTEMS" },
];

const title = "Minimal Java Accounting — logical ports, adapters, and resources";
const subtitle = "One selected adapter per logical port · shared technical support stays neutral · external systems are cylinders";
const description = "Two-axis ports and adapters diagram showing the durable outbox messaging adapter using PostgreSQL and Artemis, and the logical persistence capability depending on the accounting model.";
const firstNote = "Solid boxes are Gradle modules; dashed boxes are logical typed ports; cylinders are independently deployed external systems.";
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

const resourceSvg = resources.map((item) => {
  const midpointX = item.x + item.width / 2;
  const midpointY = item.y + item.height / 2;
  if (item.orientation === "horizontal") {
    const cap = 24;
    return `
  <g>
    <title>${escape(item.label)} — external ${escape(item.detail)}</title>
    <rect class="resource-body" x="${item.x + cap}" y="${item.y}" width="${item.width - cap * 2}" height="${item.height}"/>
    <ellipse class="resource-cap" cx="${item.x + cap}" cy="${midpointY}" rx="${cap}" ry="${item.height / 2}"/>
    <ellipse class="resource-cap" cx="${item.x + item.width - cap}" cy="${midpointY}" rx="${cap}" ry="${item.height / 2}"/>
    <text class="module" x="${midpointX}" y="${midpointY + 6}">${escape(item.label)} · ${escape(item.detail)}</text>
  </g>`;
  }
  const cap = 20;
  return `
  <g>
    <title>${escape(item.label)} — external ${escape(item.detail)}</title>
    <rect class="resource-body" x="${item.x}" y="${item.y + cap}" width="${item.width}" height="${item.height - cap * 2}"/>
    <ellipse class="resource-cap" cx="${midpointX}" cy="${item.y + cap}" rx="${item.width / 2}" ry="${cap}"/>
    <ellipse class="resource-cap" cx="${midpointX}" cy="${item.y + item.height - cap}" rx="${item.width / 2}" ry="${cap}"/>
    <text class="module resource-label" x="${midpointX}" y="${midpointY - 3}">${escape(item.label)}</text>
    <text class="package" x="${midpointX}" y="${midpointY + 23}">${escape(item.detail)}</text>
  </g>`;
}).join("");

const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="2500" height="1615" viewBox="0 0 2500 1615" role="img" aria-labelledby="title description">
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
      .resource-body, .resource-cap { fill: #eceff1; stroke: #65747b; stroke-width: 2.5; }
      .resource-body { filter: url(#shadow); }
      .resource-label { font-size: 17px; }
    </style>
  </defs>
  <rect class="canvas" x="0" y="0" width="2500" height="1615"/>
  <text class="title" x="55" y="58">${escape(title)}</text>
  <text class="subtitle" x="55" y="91">${escape(subtitle)}</text>
${bandSvg}
${edgeSvg}
${nodeSvg}
${resourceSvg}
  <line x1="55" y1="1530" x2="2445" y2="1530" stroke="#d7dee1" stroke-width="1.5"/>
  <text class="note" x="55" y="1568">${escape(firstNote)}</text>
  <text class="note" x="55" y="1595">${escape(secondNote)}</text>
</svg>
`;

writeFileSync(output, svg);
