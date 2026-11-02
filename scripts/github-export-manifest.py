"""Prepare a credential-free source manifest for the authorized GitHub integration."""
from pathlib import Path
import json,base64,hashlib,sys,re
root=Path(__file__).resolve().parents[1]
ignored={'.git','.openai','node_modules','.gradle','.kotlin','.idea','build','data','__pycache__'}
items=[]
patterns=[rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',rb'ghp_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{40,}',rb'sk_live_[A-Za-z0-9]{15,}']
for f in sorted(root.rglob('*')):
    if not f.is_file() or f.is_symlink():continue
    rel=f.relative_to(root)
    if set(rel.parts)&ignored:continue
    if f.name.startswith('.env') and f.name!='.env.example':continue
    if f.name=='local.properties' or f.suffix.lower() in {'.apk','.zip','.tar','.gz','.db','.sqlite','.log','.jks','.keystore'}:continue
    b=f.read_bytes()
    if any(re.search(p,b) for p in patterns):raise SystemExit('Potential secret blocked: '+str(rel))
    try:c=b.decode('utf-8');encoding='utf-8'
    except UnicodeDecodeError:c=base64.b64encode(b).decode('ascii');encoding='base64'
    items.append({'path':rel.as_posix(),'mode':'100755' if f.name=='gradlew' else '100644','content':c,'encoding':encoding,'blobHash':hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()})
output=Path(sys.argv[1]);output.write_text(json.dumps(items,ensure_ascii=True),encoding='utf-8')
print(json.dumps({'files':len(items),'bytes':output.stat().st_size,'output':str(output)}))
