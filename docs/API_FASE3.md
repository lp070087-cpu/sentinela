# Contratos adicionais — fase 3

A API local continua em Node 24 + SQLite. Todos os endpoints abaixo exigem sessão válida; mutações exigem `X-CSRF-Token`. IDs alheios retornam 404. Nenhum deles usa dados DEMO.

| Método e rota | Corpo / retorno |
| --- | --- |
| GET `/api/profile` | `{user, secondaryPhone, avatar}` |
| PATCH `/api/profile` | campos opcionais `{name, secondaryPhone, avatar}`; avatar usa iniciais de até 12 caracteres, sem URL/upload |
| PATCH `/api/devices/:id` | `{name}`; renomeia sem trocar token ou estado |
| PATCH `/api/contacts/:id` | campos opcionais `{name, phone, email, allowAlerts}`; retorna `{contact}` |
| GET `/api/incidents/:id` | `{incident, location, commands, association}` |
| GET `/api/notifications` | `{notifications:[{id,type,title,body,deviceId,readAt,createdAt}]}` |
| PATCH `/api/notifications/:id` | `{read:boolean}` |
| POST `/api/notifications/read-all` | `{}` |

Alterar identidade do contato invalida verificações de telefone/e-mail. Alterar consentimento invalida a verificação de e-mail. Ambos inutilizam challenges pendentes; a entrega de alertas continua condicionada a consentimento e nova verificação. Não há envio provocado por uma simples edição.

A central recebe notificações persistentes quando a telemetria de um dispositivo cadastrado entra em EMERGENCY/LOST. Não simula eventos. As posições e comandos do detalhe de incidente são uma associação temporal, explicitamente identificada; não comprovam causalidade, entrega de alerta ou execução física. Revogar um aparelho mantém seu histórico para a conta.

As novas tabelas SQLite são criadas de modo aditivo (`profiles`, `notifications`), sem apagar tabelas/dados existentes. A retenção de notificações acompanha a retenção configurada para a conta, aplicada no login e nos fluxos que já executam a limpeza.

## Preparação de migração Neon

O esquema Prisma continua **proposto, sem adapter ativo e sem migração executada**. Foram acrescentados os campos de perfil e o modelo `InboxNotification`, separado do registro de entrega `Notification`. O esquema não é cópia literal do SQLite: IDs SQLite hexadecimais devem ser convertidos/mapear para UUID, timestamps de milissegundos para DateTime, estados DEVELOPMENT para uma política de assinatura compatível, e hashes/CSRF/sessões exigem adaptação explícita.

Migração segura: backup criptografado fora do repositório; criar adapter PostgreSQL com transações equivalentes; ensaiar importação em banco isolado sem credenciais reais; conferir contagens, ownership e integridade referencial; executar testes HTTP contra o adapter; validar TLS, segredos, backups e retenção; só então executar corte com escrita pausada e plano de reversão. Não publicar API SQLite em filesystem efêmero. O bloqueio de produção permanece ativo.

## Privacidade da conta

- `POST /api/account/export {password}` exige confirmação de senha e e-mail verificado; exporta dados funcionais da conta sem hashes, tokens, códigos de verificação ou segredos de sessão.
- `DELETE /api/account {password, confirmation:"EXCLUIR"}` exige step-up e confirmação textual. Apaga transacionalmente todos os dados locais da conta, sessões e credenciais de dispositivos; expira o cookie. Não remove PIN/bloqueio nativo e não limpa dados já existentes no aparelho. O usuário precisa de acesso ao aparelho para reconfigurá-lo.

Essas operações passam pelos mesmos limites de autenticação, CSRF e rate limiting. Não foi executada exclusão em conta do usuário; os testes usam banco temporário com dados fictícios.
