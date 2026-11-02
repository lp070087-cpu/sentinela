"""Export development source only. Databases, credentials, caches and hosted-site identity are excluded."""
from pathlib import Path
import sys, zipfile
root = Path(__file__).resolve().parents[1]
output = Path(sys.argv[1]).resolve() if len(sys.argv)>1 else root/'sentinela-fase2.zip'
excluded = {'.git','.openai','node_modules','.gradle','.idea','build','data','__pycache__'}
files=[]
for path in sorted(root.rglob('*')):
    if not path.is_file() or path.is_symlink() or path.resolve()==output: continue
    rel=path.relative_to(root)
    if set(rel.parts)&excluded: continue
    if path.name.startswith('.env') and path.name!='.env.example': continue
    if path.name=='local.properties' or path.suffix.lower() in {'.sqlite','.db','.zip','.tar','.gz','.apk','.jks','.keystore','.log'}: continue
    files.append((path,rel))
output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as archive:
    for path,rel in files: archive.write(path,Path('sentinela')/rel)
print(f'{output}: {len(files)} source files')
