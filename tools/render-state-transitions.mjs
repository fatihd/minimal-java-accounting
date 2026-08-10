import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const outputDirectory = process.argv[2];
if (!outputDirectory) {
  throw new Error(
    "usage: node tools/render-state-transitions.mjs <output-directory>",
  );
}

mkdirSync(outputDirectory, { recursive: true });

const colors = {
  transaction: { fill: "#fff3cd", stroke: "#a97900" },
  effect: { fill: "#eaf3ff", stroke: "#3978b8" },
  hold: { fill: "#eaf8ee", stroke: "#3e8b58" },
  terminal: { fill: "#f3f5f6", stroke: "#65747b" },
};

const escape = (value) => value
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");

const declaredStates = (sourcePath) => {
  const source = readFileSync(new URL(`../${sourcePath}`, import.meta.url), "utf8");
  const body = source.match(/public enum \w+\s*\{([\s\S]*?)\}/)?.[1];
  if (!body) {
    throw new Error(`cannot read enum constants from ${sourcePath}`);
  }
  return body
    .split(",")
    .map((constant) => constant.replace(/;.*/s, "").trim())
    .filter(Boolean);
};

const requireCompleteStateSet = (sourcePath, states) => {
  const declared = declaredStates(sourcePath);
  const rendered = states.map((state) => state.name);
  if (declared.join() !== rendered.join()) {
    throw new Error(
      `${sourcePath} declares [${declared}], but the diagram renders [${rendered}]`,
    );
  }
};

const textLines = (lines, x, y, cssClass, lineHeight = 22) => lines
  .map((line, index) =>
    `<text class="${cssClass}" x="${x}" y="${y + index * lineHeight}">${escape(line)}</text>`,
  )
  .join("\n");

const stateSvg = (state, palette) => {
  const stateColors = state.terminal ? colors.terminal : palette;
  const centerX = state.x + state.width / 2;
  const centerY = state.y + state.height / 2;
  const terminalBorder = state.terminal
    ? `<rect class="terminal-border" x="${state.x + 6}" y="${state.y + 6}" width="${state.width - 12}" height="${state.height - 12}"/>`
    : "";
  const caption = state.caption
    ? `<text class="state-caption" x="${centerX}" y="${centerY + 25}">${escape(state.caption)}</text>`
    : "";
  const elements = [
    `<rect class="state" x="${state.x}" y="${state.y}" width="${state.width}" height="${state.height}" fill="${stateColors.fill}" stroke="${stateColors.stroke}"/>`,
    terminalBorder,
    `<text class="state-name" x="${centerX}" y="${centerY + (state.caption ? -8 : 7)}">${escape(state.name)}</text>`,
    caption,
  ].filter(Boolean).map((element) => `  ${element}`).join("\n");
  return `<g>\n${elements}\n</g>`;
};

const edgeSvg = (edge) => {
  const labels = Array.isArray(edge.label) ? edge.label : [edge.label];
  const labelWidth = Math.max(...labels.map((line) => line.length)) * 8 + 24;
  const labelHeight = labels.length * 20 + 8;
  return `<g>
  <path class="edge${edge.dashed ? " same-state" : ""}" d="${edge.path}"/>
  <rect class="edge-label-background" x="${edge.labelX - labelWidth / 2}" y="${edge.labelY - 17}" width="${labelWidth}" height="${labelHeight}"/>
  ${textLines(labels, edge.labelX, edge.labelY, "edge-label", 20)}
</g>`;
};

const initialSvg = (initial) => `<g>
  <circle class="initial" cx="${initial.x}" cy="${initial.y}" r="10"/>
  ${initial.label ? `<text class="initial-label" x="${initial.labelX}" y="${initial.labelY}">${escape(initial.label)}</text>` : ""}
</g>`;

const render = ({
  fileName,
  width,
  height,
  title,
  subtitle,
  palette,
  initial,
  states,
  edges,
  footer,
  enumSource,
}) => {
  requireCompleteStateSet(enumSource, states);
  const svg = `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-labelledby="title description">
  <title id="title">${escape(title)}</title>
  <desc id="description">${escape(subtitle)}</desc>
  <defs>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="10" markerHeight="10" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="#263238"/>
    </marker>
    <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
      <feDropShadow dx="0" dy="3" stdDeviation="4" flood-color="#000000" flood-opacity="0.12"/>
    </filter>
    <style>
      .canvas { fill: #ffffff; }
      .title { font: 700 32px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #172126; }
      .subtitle { font: 400 17px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; }
      .state { stroke-width: 2.5; rx: 16; filter: url(#shadow); }
      .terminal-border { fill: none; stroke: #65747b; stroke-width: 1.5; rx: 11; }
      .state-name { font: 700 18px ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; fill: #172126; text-anchor: middle; }
      .state-caption { font: 400 13px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; text-anchor: middle; }
      .edge { fill: none; stroke: #263238; stroke-width: 2.75; marker-end: url(#arrow); stroke-linecap: round; stroke-linejoin: round; }
      .same-state { stroke-dasharray: 8 7; }
      .edge-label-background { fill: #ffffff; rx: 7; }
      .edge-label { font: 600 14px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #263238; text-anchor: middle; }
      .initial { fill: #263238; }
      .initial-label { font: 500 14px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; text-anchor: middle; }
      .footer { font: 400 14px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; fill: #526168; }
      .legend-line { stroke: #263238; stroke-width: 2.75; }
      .legend-dashed { stroke-dasharray: 8 7; }
    </style>
  </defs>
  <rect class="canvas" x="0" y="0" width="${width}" height="${height}"/>
  <text class="title" x="50" y="54">${escape(title)}</text>
  <text class="subtitle" x="50" y="84">${escape(subtitle)}</text>
  ${edges.map(edgeSvg).join("\n  ")}
  ${initialSvg(initial)}
  ${states.map((state) => stateSvg(state, palette)).join("\n  ")}
  <line x1="50" y1="${height - 62}" x2="${width - 50}" y2="${height - 62}" stroke="#d7dee1" stroke-width="1.5"/>
  <text class="footer" x="50" y="${height - 30}">${escape(footer)}</text>
</svg>
`;
  writeFileSync(join(outputDirectory, fileName), svg);
};

render({
  fileName: "transaction.svg",
  width: 1900,
  height: 920,
  title: "Transaction lifecycle",
  subtitle: "Aggregate-root state changes; captions identify accepted operations that leave the state unchanged.",
  palette: colors.transaction,
  initial: { x: 90, y: 405, label: "state = null", labelX: 90, labelY: 440 },
  states: [
    { name: "PLACING_HOLDS", caption: "partial placeEffect", x: 250, y: 350, width: 250, height: 110 },
    { name: "HELD", caption: "duplicate placed event", x: 680, y: 160, width: 235, height: 110 },
    { name: "RELEASING_HOLDS", caption: "outcome progress · late place", x: 680, y: 540, width: 270, height: 110 },
    { name: "SETTLING", caption: "partial settle · repeat journal", x: 1110, y: 160, width: 245, height: 110 },
    { name: "COMPLETED", caption: "stale place · repeat completion", terminal: true, x: 1530, y: 160, width: 245, height: 110 },
    { name: "REJECTED", caption: "duplicate decline", terminal: true, x: 1175, y: 540, width: 245, height: 110 },
  ],
  edges: [
    { path: "M 100 405 L 250 405", label: ["start", "effects exist"], labelX: 175, labelY: 365 },
    { path: "M 97 397 C 235 170 430 150 680 207", label: ["start", "no net effects"], labelX: 365, labelY: 150 },
    { path: "M 500 370 C 570 330 610 250 680 215", label: ["placeEffect", "all effects held"], labelX: 585, labelY: 292 },
    { path: "M 500 440 C 570 485 610 555 680 585", label: ["declineEffect", "release work remains"], labelX: 585, labelY: 505 },
    { path: "M 500 448 C 690 750 980 760 1175 620", label: ["declineEffect", "all effects finished"], labelX: 825, labelY: 742 },
    { path: "M 950 595 L 1175 595", label: ["releaseEffect / declineEffect", "all effects finished"], labelX: 1062, labelY: 550 },
    { path: "M 915 215 L 1110 215", label: ["beginSettlement", "effects exist"], labelX: 1012, labelY: 178 },
    { path: "M 880 160 C 1030 105 1385 105 1605 160", label: ["beginSettlement", "no effects"], labelX: 1240, labelY: 120 },
    { path: "M 1355 215 L 1530 215", label: ["settleEffect", "all effects settled"], labelX: 1442, labelY: 178 },
  ],
  footer: "Solid arrows change TransactionState. Full-state adapter reconstruction is not a business transition.",
  enumSource: "model/src/main/java/dev/minimalaccounting/model/transaction/TransactionState.java",
});

render({
  fileName: "account-effect.svg",
  width: 1750,
  height: 800,
  title: "AccountEffect lifecycle",
  subtitle: "Transaction-owned child lifecycle; these package-private operations are invoked only through Transaction.",
  palette: colors.effect,
  initial: { x: 80, y: 365, label: "created by start", labelX: 92, labelY: 400 },
  states: [
    { name: "PENDING", x: 190, y: 320, width: 210, height: 90 },
    { name: "HELD", x: 530, y: 320, width: 210, height: 90 },
    { name: "DECLINED", terminal: true, x: 530, y: 570, width: 210, height: 90 },
    { name: "RELEASING", caption: "repeat beginRelease", x: 900, y: 135, width: 240, height: 100 },
    { name: "RELEASED", caption: "repeat release", terminal: true, x: 1350, y: 135, width: 230, height: 100 },
    { name: "SETTLING", caption: "repeat beginSettlement", x: 900, y: 430, width: 240, height: 100 },
    { name: "SETTLED", caption: "repeat settle", terminal: true, x: 1350, y: 430, width: 230, height: 100 },
  ],
  edges: [
    { path: "M 90 365 L 190 365", label: "start", labelX: 140, labelY: 335 },
    { path: "M 400 365 L 530 365", label: "hold", labelX: 465, labelY: 340 },
    { path: "M 340 410 C 390 535 450 615 530 615", label: "decline", labelX: 410, labelY: 540 },
    { path: "M 360 320 C 500 125 710 115 900 185", label: ["beginRelease", "late placement"], labelX: 620, labelY: 125 },
    { path: "M 700 320 C 760 250 815 205 900 185", label: "beginRelease", labelX: 790, labelY: 245 },
    { path: "M 740 380 C 800 420 835 460 900 480", label: "beginSettlement", labelX: 810, labelY: 430 },
    { path: "M 1140 185 L 1350 185", label: "release", labelX: 1245, labelY: 160 },
    { path: "M 1140 480 L 1350 480", label: "settle", labelX: 1245, labelY: 455 },
  ],
  footer: "Terminal states have a double border. Captions show explicitly idempotent same-state operations.",
  enumSource: "model/src/main/java/dev/minimalaccounting/model/transaction/AccountEffectState.java",
});

render({
  fileName: "account-hold.svg",
  width: 1600,
  height: 700,
  title: "AccountHold lifecycle",
  subtitle: "Account-owned child lifecycle; Account is the public mutation boundary.",
  palette: colors.hold,
  initial: { x: 85, y: 330, label: "new hold", labelX: 85, labelY: 365 },
  states: [
    { name: "HELD", x: 220, y: 285, width: 220, height: 90 },
    { name: "DECLINED", caption: "retains refusal reason", terminal: true, x: 700, y: 105, width: 250, height: 100 },
    { name: "RELEASED", caption: "repeat release", terminal: true, x: 700, y: 285, width: 250, height: 100 },
    { name: "SETTLED", caption: "repeat settle", terminal: true, x: 700, y: 465, width: 250, height: 100 },
  ],
  edges: [
    { path: "M 95 330 L 220 330", label: "placeHold", labelX: 157, labelY: 300 },
    { path: "M 410 285 C 500 170 575 155 700 155", label: "decline(reason)", labelX: 555, labelY: 155 },
    { path: "M 440 330 L 700 335", label: "release", labelX: 570, labelY: 305 },
    { path: "M 410 375 C 500 485 575 515 700 515", label: "settle", labelX: 555, labelY: 500 },
  ],
  footer: "Duplicate placement is handled by Account without mutating the existing hold. Other invalid transitions throw.",
  enumSource: "model/src/main/java/dev/minimalaccounting/model/account/AccountHoldState.java",
});
