// Operador local: recuperar uma entrega de desenvolvimento quando não há sessão.
// Não executar em servidor público. Exibe segredos de teste para UMA conta local.
import {DatabaseSync} from 'node:sqlite';
import {resolve} from 'node:path';
if(process.env.NODE_ENV==='production')throw new Error('Indisponível em produção.');
const email=process.argv[2];if(!email){console.error('Uso: node scripts/outbox-local.mjs email-da-conta');process.exit(1)}
const db=new DatabaseSync(resolve(process.env.SENTINELA_DB||'apps/web/data/sentinela.sqlite'),{readOnly:true});
const messages=db.prepare("SELECT m.kind,m.subject,m.body,m.created_at FROM messages m JOIN users u ON u.id=m.user_id WHERE u.email=? AND m.transport='local-outbox' AND m.created_at>? ORDER BY m.created_at DESC LIMIT 10").all(email.toLowerCase(),Date.now()-864e5);console.log(JSON.stringify(messages,null,2));db.close();
