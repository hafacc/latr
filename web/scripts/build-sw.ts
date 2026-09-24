// Writes out/sw.js after `next build`: scripts/sw.js plus the list of built files, and a version that changes whenever any of them does.
import { createHash } from "node:crypto";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { join, relative } from "node:path";

const OUT = "out";

const worker = await readFile(join(import.meta.dir, "sw.js"), "utf8");
const entries = await readdir(OUT, { recursive: true, withFileTypes: true });
const files = entries
  .filter((entry) => entry.isFile())
  .map((entry) => relative(OUT, join(entry.parentPath, entry.name)))
  // One missing file fails the whole install, so skip what Pages may not serve.
  .filter(
    (file) =>
      file !== "sw.js" &&
      !file.endsWith(".txt") &&
      !file.startsWith(".") &&
      !file.startsWith("404"),
  )
  .sort();

const hash = createHash("sha256").update(worker);
for (const file of files) {
  hash.update(file).update(await readFile(join(OUT, file)));
}
const version = hash.digest("hex").slice(0, 16);
// The page itself is cached under the scope URL, which is what navigations look up.
const precache = files.map((file) => (file === "index.html" ? "./" : file));

await writeFile(
  join(OUT, "sw.js"),
  `self.__VERSION = ${JSON.stringify(version)};\nself.__FILES = ${JSON.stringify(precache)};\n${worker}`,
);
console.log(`sw.js ${version}: ${precache.length} files`);
