import { readFile } from 'node:fs/promises';
for (const file of process.argv.slice(2)) {
  try { console.log(`FILE: ${file}\n${(await readFile(file, 'utf8')).split(/\r?\n/).slice(-35).join('\n')}`); }
  catch (e) { console.log(`${file}: ${e.message}`); }
}
