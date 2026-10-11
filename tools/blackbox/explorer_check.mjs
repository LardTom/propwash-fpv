import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

const [viewerSrc, nodeModules, outDir, ...logs] = process.argv.slice(2);
if (!viewerSrc || !nodeModules || !outDir || logs.length === 0) {
  console.error('Aufruf: node explorer_check.mjs <blackbox-log-viewer/src> <node_modules mit esbuild und semver> <Ausgabeordner> <log.bbl>...');
  process.exit(2);
}
const require = createRequire(path.join(nodeModules, 'package.json'));
const esbuild = require('esbuild');
const FIRMWARE_TYPE_BETAFLIGHT = 3;
const expectedFirmware = process.env.PROPWASH_BLACKBOX_FIRMWARE || '4.5.1';

function browserGlobals() {
  const element = new Proxy(function () {}, {
    get: (_, key) => (key === 'then' || typeof key === 'symbol' ? undefined : key === 'length' ? 0 : element),
    apply: () => element,
  });
  const plain = (value) => value !== null && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype;
  const extend = (...args) => {
    const deep = typeof args[0] === 'boolean' ? args.shift() : false;
    const target = args.shift() ?? {};
    for (const source of args) {
      if (source === null || source === undefined) {
        continue;
      }
      for (const key in source) {
        const value = source[key];
        if (value === target || value === undefined) {
          continue;
        }
        if (deep && (plain(value) || Array.isArray(value))) {
          const base = target[key];
          const start = Array.isArray(value) ? (Array.isArray(base) ? base : []) : (plain(base) ? base : {});
          target[key] = extend(true, start, value);
        } else {
          target[key] = value;
        }
      }
    }
    return target;
  };
  const jquery = () => element;
  jquery.extend = extend;
  globalThis.$ = jquery;
  globalThis.jQuery = jquery;
  globalThis.semver = require('semver');
}
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-explorer-'));
const stub = path.join(work, 'settings-stub.js');
fs.writeFileSync(stub, 'export function useSettingsStore() { return { userSettings: {} }; }\n');
const entry = path.join(work, 'entry.js');
fs.writeFileSync(entry, `export { FlightLog } from ${JSON.stringify(path.join(path.resolve(viewerSrc), 'flightlog.js'))};\n`
  + `export { FlightLogParser } from ${JSON.stringify(path.join(path.resolve(viewerSrc), 'flightlog_parser.js'))};\n`);
const bundle = path.join(work, 'explorer.mjs');
await esbuild.build({
  entryPoints: [entry],
  bundle: true,
  platform: 'node',
  format: 'esm',
  outfile: bundle,
  nodePaths: [path.resolve(nodeModules)],
  logLevel: 'error',
  plugins: [{
    name: 'stub-settings',
    setup(build) {
      build.onResolve({ filter: /stores\/settings(\.js)?$/ }, () => ({ path: stub }));
    },
  }],
});
browserGlobals();
const { FlightLog } = await import(pathToFileURL(bundle).href);
fs.mkdirSync(outDir, { recursive: true });
let failed = 0;
const required = ['loopIteration', 'time', 'axisP[0]', 'axisI[0]', 'axisF[0]', 'rcCommand[3]', 'setpoint[0]', 'vbatLatest', 'amperageLatest',
  'rssi', 'gyroADC[0]', 'gyroUnfilt[0]', 'motor[0]', 'motor[3]', 'eRPM[0]', 'flightModeFlags', 'stateFlags', 'failsafePhase'];
for (const file of logs) {
  const name = path.basename(file, '.bbl');
  const data = new Uint8Array(fs.readFileSync(file));
  const log = new FlightLog(data);
  const count = log.getLogCount();
  const expected = fs.existsSync(file.replace(/\.bbl$/, '.frames'))
    ? fs.readFileSync(file.replace(/\.bbl$/, '.frames'), 'utf8').trim().split(/\s+/).map(Number) : null;
  if (count < 1 || (expected && expected.length !== count)) {
    console.log(`${name}: ${count} Logs gefunden`);
    failed++;
    continue;
  }
  for (let index = 0; index < count; index++) {
    const label = `${name}.${String(index + 1).padStart(2, '0')}`;
    const error = log.getLogError(index);
    const opened = !error && log.openLog(index);
    if (!opened) {
      console.log(`${label}: Fehler ${error}`);
      failed++;
      continue;
    }
    const sys = log.getSysConfig();
    const names = log.getMainFieldNames();
    const stats = log.getStats();
    const chunks = log.getChunksInTimeRange(log.getMinTime(), log.getMaxTime());
    const rows = [];
    let frames = 0;
    for (const chunk of chunks) {
      for (const frame of chunk.frames) {
        frames++;
        rows.push(names.map((_, i) => frame[i]).join(','));
      }
    }
    fs.writeFileSync(path.join(outDir, `${label}.explorer.csv`), names.join(',') + '\n' + rows.join('\n') + '\n');
    const corrupt = Object.values(stats.frame).reduce((sum, f) => sum + (f.corruptCount || 0) + (f.desyncCount || 0), 0);
    const frameCounts = Object.entries(stats.frame).map(([k, f]) => `${k}=${f.validCount}`).join(' ');
    console.log(`${label}: Firmware ${sys.firmwareType}/${sys.firmwareVersion}, Felder ${names.length}, Frames ${frames}, `
      + `Frame-Typen ${frameCounts}, kaputt ${corrupt}, GPS ${log.hasGpsData()}, Motoren ${log.getNumMotors()}, Zellen ${log.getNumCellsEstimate()}, `
      + `rollPID ${JSON.stringify(sys.rollPID)}, rates_type ${sys.rates_type}, gyro_lpf1 ${sys.gyro_lowpass_hz}, `
      + `Looptime ${sys.looptime}, P ${sys.frameIntervalPNum}/${sys.frameIntervalPDenom}, Zeit ${((log.getMaxTime() - log.getMinTime()) / 1e6).toFixed(3)} s`);
    if (corrupt > 0 || (expected && expected[index] !== frames)) {
      console.log(`${label}: erwartet ${expected ? expected[index] : '?'} Frames, gelesen ${frames}, kaputt ${corrupt}`);
      failed++;
    }
    if (sys.firmwareType !== FIRMWARE_TYPE_BETAFLIGHT || sys.firmwareVersion !== expectedFirmware) {
      console.log(`${label}: Firmware ${sys.firmwareType}/${sys.firmwareVersion}, erwartet ${FIRMWARE_TYPE_BETAFLIGHT}/${expectedFirmware} (Betaflight)`);
      failed++;
    }
    const missing = required.filter((f) => !names.includes(f));
    if (missing.length) {
      console.log(`${label}: Felder fehlen: ${missing.join(', ')}`);
      failed++;
    }
  }
}
fs.rmSync(work, { recursive: true, force: true });
process.exit(failed ? 1 : 0);
