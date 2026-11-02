// Keep the offline Android experience identical to the isolated web demo.
import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const root=new URL('../',import.meta.url);
const files=['demo-app.html','demo-app.css','demo-app.js','demo-store.mjs','demo-Manrope.ttf','demo-InstrumentSerif.ttf'];
const check=process.argv.includes('--check');
let mismatches=0;
for(const name of files){
 const source=await readFile(new URL(`apps/web/public/${name}`,root));
 const destination=new URL(`apps/android/app/src/main/assets/demo/${name}`,root);
 if(check){const bundled=await readFile(destination);if(!source.equals(bundled)){console.error(`DEMO desatualizada no Android: ${name}`);mismatches++}}
 else await writeFile(destination,source);
}
if(mismatches)process.exitCode=1;
else console.log(`${files.length} arquivos DEMO ${check?'verificados':'sincronizados'} em ${fileURLToPath(root)}`);
