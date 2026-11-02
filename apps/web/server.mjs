import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {resolve,extname,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createService,HttpError} from './service.mjs';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
export function createApp({database=process.env.SENTINELA_DB||resolve(root,'apps/web/data/sentinela.sqlite'),development=process.env.NODE_ENV!=='production',delivery={email:process.env.EMAIL_DELIVERY_URL,sms:process.env.SMS_DELIVERY_URL,token:process.env.DELIVERY_BEARER_TOKEN}}={}){
 if(!development)throw new Error('Produção bloqueada: adapter PostgreSQL/Neon e políticas de entrega/infraestrutura ainda precisam ser configurados e validados.');
 const service=createService(database,{delivery,development}),rates=new Map();
 
 const limit=(key,max,window=60e3)=>{const t=Date.now();let r=rates.get(key);if(!r||r.until<t){r={n:0,until:t+window};rates.set(key,r)}if(++r.n>max)throw new HttpError(429,'Muitas tentativas. Aguarde antes de tentar novamente.');if(rates.size>5000)for(const[k,v]of rates)if(v.until<t)rates.delete(k)};
 const server=http.createServer(async(req,res)=>{
 const requestId=crypto.randomUUID();res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('X-Frame-Options','SAMEORIGIN');res.setHeader('Permissions-Policy','camera=(), microphone=(), geolocation=()');res.setHeader('Cache-Control','no-store');
 const send=(status,data)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(data))};
 try{
 const url=new URL(req.url,'http://localhost'),p=url.pathname,m=req.method;
 if(!p.startsWith('/api/')){if(!['GET','HEAD'].includes(m))throw new HttpError(405,'Método não permitido.');let target;if(p==='/'||p==='/index.html')target=resolve(root,'dist/index.html');else if(p==='/demo'||p==='/demo/')target=resolve(root,'dist/index.html');else if(['/style.css','/app.js','/demo/style.css','/demo/app.js'].includes(p))target=resolve(root,'dist',p.split('/').pop());else if(['/panel.html','/panel.css','/panel.js','/demo-app.html','/demo-app.css','/demo-app.js','/demo-store.mjs','/demo-Manrope.ttf','/demo-InstrumentSerif.ttf'].includes(p))target=resolve(root,'apps/web/public',p.slice(1));else if(p==='/qa-responsive.html')target=resolve(root,'qa-responsive.html');else if(p==='/health')return send(200,{ok:true});else throw new HttpError(404,'Não encontrado.');if(p.startsWith('/demo-app')||p==='/demo-store.mjs'){res.setHeader('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'none'; img-src 'self' data:; media-src blob:; object-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'self'")}const data=await readFile(target);const type={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.mjs':'text/javascript; charset=utf-8','.ttf':'font/ttf','.css':'text/css; charset=utf-8'}[extname(target)]||'application/octet-stream';res.writeHead(200,{'Content-Type':type});return res.end(m==='HEAD'?undefined:data)}
 const ip=req.socket.remoteAddress||'unknown';limit(ip+':all',300);
 if(m!=='GET'&&m!=='HEAD'){
 if(req.headers.origin){let origin;try{origin=new URL(req.headers.origin)}catch{throw new HttpError(403,'Origem inválida.')}if(origin.host!==req.headers.host)throw new HttpError(403,'Origem não autorizada.');}
 if(req.headers['sec-fetch-site']==='cross-site')throw new HttpError(403,'Requisição externa bloqueada.');
 }
 let b={};if(['POST','PATCH','DELETE'].includes(m)){if(!req.headers['content-type']?.startsWith('application/json'))throw new HttpError(415,'Envie JSON.');let chunks=[],size=0;for await(const chunk of req){size+=chunk.length;if(size>20000)throw new HttpError(413,'Corpo muito grande.');chunks.push(chunk)}try{b=JSON.parse(Buffer.concat(chunks).toString()||'{}')}catch{throw new HttpError(400,'JSON inválido.')}if(!b||typeof b!=='object'||Array.isArray(b))throw new HttpError(400,'Objeto JSON obrigatório.');}
 if(p==='/api/health'&&m==='GET')return send(200,{ok:true,mode:'development',database:'sqlite',emailTransport:delivery.email?'https-provider':'local-outbox',smsTransport:delivery.sms?'https-provider':'local-outbox',billingConfigured:false});
 if(p.startsWith('/api/auth/')&&m!=='GET')limit(ip+':auth',30);if(p==='/api/auth/login')limit(ip+':login:'+String(b.email).toLowerCase(),10,15*60e3);
 if(p==='/api/auth/register'&&m==='POST')return send(201,await service.register(b));
 if(p==='/api/auth/login'&&m==='POST'){const data=service.login(b,req.headers['user-agent']||'');res.setHeader('Set-Cookie',`sentinela_session=${data.token}; HttpOnly; SameSite=Strict; Path=/; Max-Age=43200${req.socket.encrypted?'; Secure':''}`);delete data.token;return send(200,data)}
 if(p==='/api/auth/verify-email'&&m==='POST')return send(200,service.verifyEmail(b));
 if(p==='/api/auth/request-reset'&&m==='POST')return send(200,await service.requestReset(b));
 if(p==='/api/auth/reset'&&m==='POST')return send(200,service.reset(b));
 if(p.startsWith('/api/device/')){const match=req.headers.authorization?.match(/^Bearer ([A-Za-z0-9_-]{30,100})$/);const d=service.deviceAuth(match?.[1]);limit(d.id+':device',120);if(p==='/api/device/me'&&m==='GET')return send(200,service.identity(d));if(p==='/api/device/events'&&m==='POST')return send(200,await service.event(d,b));if(p==='/api/device/location'&&m==='POST')return send(201,service.location(d,b));if(p==='/api/device/commands'&&m==='GET')return send(200,service.poll(d));let a=p.match(/^\/api\/device\/commands\/([a-f0-9]{32})\/ack$/);if(a&&m==='POST')return send(200,service.ack(d,a[1],b));throw new HttpError(404,'Rota de dispositivo não encontrada.')}
 const raw=req.headers.cookie?.split(';').map(v=>v.trim()).find(v=>v.startsWith('sentinela_session='))?.slice('sentinela_session='.length),s=service.session(raw);if(!s)throw new HttpError(401,'Entre na sua conta.');
 if(!['GET','HEAD'].includes(m)&&req.headers['x-csrf-token']!==s.csrf)throw new HttpError(403,'Token CSRF inválido. Atualize sua sessão.');
 if(m!=='GET')limit(s.user_id+':write',80);if(p.endsWith('/commands')&&m==='POST')limit(s.user_id+':remote',10);
 if(p==='/api/auth/me'&&m==='GET')return send(200,service.me(s));
 if(p==='/api/auth/logout'&&m==='POST'){res.setHeader('Set-Cookie','sentinela_session=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0');return send(200,service.logout(s))}
 if(p==='/api/auth/sessions'&&m==='GET')return send(200,service.sessions(s));let match=p.match(/^\/api\/auth\/sessions\/([a-f0-9]{32})$/);if(match&&m==='DELETE')return send(200,service.revokeSession(s,match[1],b));
 if(p==='/api/auth/request-email-verification'&&m==='POST')return send(200,await service.requestEmail(s));
 if(p==='/api/auth/phone/request'&&m==='POST')return send(200,await service.requestPhone(s,b));if(p==='/api/auth/phone/verify'&&m==='POST')return send(200,service.verifyPhone(s,b));
 if(p==='/api/devices'&&m==='GET')return send(200,service.devices(s));if(p==='/api/devices/register'&&m==='POST')return send(201,service.registerDevice(s,b));
 match=p.match(/^\/api\/devices\/([a-f0-9]{32})(?:\/(commands|locations))?$/);if(match){const[,did,sub]=match;if(!sub&&m==='DELETE')return send(200,service.revokeDevice(s,did,b));if(sub==='commands'&&m==='POST')return send(201,service.queue(s,did,b));if(sub==='commands'&&m==='GET')return send(200,service.commands(s,did));if(sub==='locations'&&m==='GET')return send(200,service.locations(s,did));if(sub==='locations'&&m==='DELETE')return send(200,service.clearLocations(s,did,b));}
 if(p==='/api/incidents'&&m==='GET')return send(200,service.incidents(s));if(p==='/api/contacts'&&m==='GET')return send(200,service.contacts(s));if(p==='/api/contacts'&&m==='POST')return send(201,service.addContact(s,b));
 match=p.match(/^\/api\/contacts\/([a-f0-9]{32})(?:\/(request-verification|verify|request-email|verify-email))?$/);if(match){const[,cid,sub]=match;if(!sub&&m==='DELETE')return send(200,service.deleteContact(s,cid,b));if(sub==='request-verification'&&m==='POST')return send(200,await service.requestContact(s,cid));if(sub==='verify'&&m==='POST')return send(200,service.verifyContact(s,cid,b));if(sub==='request-email'&&m==='POST')return send(200,await service.requestContactEmail(s,cid));if(sub==='verify-email'&&m==='POST')return send(200,service.verifyContactEmail(s,cid,b));}
 if(p==='/api/audit'&&m==='GET')return send(200,service.audit(s));if(p==='/api/dev/outbox'&&m==='GET')return send(200,service.outbox(s));if(p==='/api/settings'&&['GET','PATCH'].includes(m))return send(200,service.settings(s,m==='PATCH'?b:null));
 if(p==='/api/subscription'&&m==='GET')return send(200,service.subscription(s));if(p==='/api/subscription'&&m==='PATCH')return send(200,service.setPlan(s,b));if(p==='/api/subscription/cancel'&&m==='POST')return send(200,service.cancelPlan(s,b));throw new HttpError(404,'Rota não encontrada.');
 }catch(e){if(e instanceof HttpError)return send(e.status,{error:e.message});if(e.code==='ENOENT')return send(404,{error:'Arquivo indisponível.'});console.error('Request failed',requestId,e.name);return send(500,{error:'Erro interno.',requestId})}
 });server.requestTimeout=15000;server.headersTimeout=10000;return{server,service};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
const cliArg=name=>{const i=process.argv.indexOf(name);return i>=0?process.argv[i+1]:undefined};const port=Number(cliArg('--port')||process.env.PORT||4173),host=cliArg('--host')||process.env.HOST||'127.0.0.1';const {server,service}=createApp();server.listen(port,host,()=>console.log(`SENTINELA development server http://${host}:${port} — real local SQLite; delivery providers ${process.env.EMAIL_DELIVERY_URL?'configured':'local-only'}.`));const shutdown=()=>server.close(()=>{service.close();process.exit(0)});process.on('SIGTERM',shutdown);process.on('SIGINT',shutdown);
}
