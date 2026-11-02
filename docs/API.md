# Contrato de integração local

Este documento descreve o contrato acordado entre painel, backend e Android. Consulte o código do servidor para o conjunto efetivamente implementado e os formatos adicionais. Campos desconhecidos não devem conceder privilégios.

## Convenções

JSON em UTF-8; erros no formato `{ "error": "mensagem" }`. Sessões web usam cookie HttpOnly e requisições autenticadas de escrita incluem `x-csrf-token` retornado por `GET /api/auth/me`. O cliente nunca pode definir `userId` para escolher o proprietário. Não habilitar CORS universal com credenciais.

### Conta e sessão

| Método | Rota | Dados / finalidade |
| --- | --- | --- |
| POST | `/api/auth/register` | `{email,password,name}`; cadastro local |
| POST | `/api/auth/login` | `{email,password}`; cria sessão |
| GET | `/api/auth/me` | `{user,csrfToken}` |
| POST | `/api/auth/logout` | Invalida sessão |
| POST | `/api/auth/request-email-verification` | Cria challenge de e-mail |
| POST | `/api/auth/verify-email` | `{token}`; expiração e uso único |
| POST | `/api/auth/request-reset` | `{email}`; resposta sem enumeração de contas |
| POST | `/api/auth/reset` | `{token,password}`; troca de senha, revogação de sessões |
| POST | `/api/auth/phone/request` | `{phone}`; solicita código do número principal |
| POST | `/api/auth/phone/verify` | `{code}`; confirma challenge do número principal |
| GET | `/api/auth/sessions` | `{sessions}` da conta |
| DELETE | `/api/auth/sessions/:id` | `{password}`; revogação autorizada |

A entrega local de token/código é uma ferramenta de desenvolvimento. Não prova posse de um e-mail ou número externo e não pode ser aceita como entrega real em produção.

### Painel

| Método | Rota | Dados / finalidade |
| --- | --- | --- |
| GET | `/api/devices` | `{devices}` pertencentes à conta |
| POST | `/api/devices/register` | `{name,platform:"ANDROID"}` → `{device,deviceToken}` |
| DELETE | `/api/devices/:id` | `{password}`; revoga o dispositivo |
| GET | `/api/devices/:id/commands` | `{commands}` com fila, concessão e resultado |
| POST | `/api/devices/:id/commands` | `{type,payload?,password}`; enfileira comando |
| GET | `/api/devices/:id/locations` | `{locations}` autorizadas |
| DELETE | `/api/devices/:id/locations` | `{password}`; exclusão do histórico autorizado |
| GET | `/api/incidents` | `{incidents}` da conta |
| GET/POST | `/api/contacts` | Lista ou cria `{name,phone,email?,allowAlerts}` |
| POST | `/api/contacts/:id/request-verification` | Solicita challenge |
| POST | `/api/contacts/:id/verify` | `{code}`; tentativas limitadas e expiração |
| POST | `/api/contacts/:id/request-email` | Solicita token ao e-mail do contato |
| POST | `/api/contacts/:id/verify-email` | `{token}`; confirma e-mail do contato |
| DELETE | `/api/contacts/:id` | `{password}`; remove contato pertencente à conta |
| GET | `/api/audit` | `{events}` sem credenciais |
| GET | `/api/subscription` | `{subscription,plans,payments,billingConfigured}` sem pagamentos ativos |
| PATCH | `/api/subscription` | `{plan:"ESSENTIAL"|"PLUS"|"FAMILY"}`; seleção de desenvolvimento sem cobrança |
| POST | `/api/subscription/cancel` | `{password}`; cancela recorrência sem encerrar emergência |
| GET/PATCH | `/api/settings` | `{retentionDays}` |
| GET | `/api/dev/outbox` | `{messages}`; somente registros do transporte local de desenvolvimento da conta atual |

### Cliente Android

Enviar `Authorization: Bearer <deviceToken>`; nunca armazenar a senha da conta no app. O token permite somente eventos e comandos do próprio dispositivo.

| Método | Rota | Corpo / resultado |
| --- | --- | --- |
| POST | `/api/device/events` | `{state,type,details?,lockResult?}` |
| POST | `/api/device/location` | `{latitude,longitude,accuracy,capturedAt}` |
| GET | `/api/device/commands` | Consulta autenticada da fila do dispositivo |
| POST | `/api/device/commands/:id/ack` | `{status:"SUCCEEDED"|"FAILED",result:string|object}` |

`capturedAt` aceita timestamp ISO 8601 ou epoch em milissegundos. O servidor normaliza para epoch em milissegundos. Latitude e longitude são números; `accuracy` é precisão em metros, não nível de confiança. Posições futuras além da tolerância de relógio são rejeitadas.

Comandos: `LOCK`, `LOST`, `RECOVER`, `ALARM`, `STOP_ALARM`. Nunca incluir comando para contornar o PIN ou silenciar o SOS nativo. A confirmação de chamada `lockNow()` e a observação de tela bloqueada são resultados distintos. Um comando falho deve preservar seu erro e não ser promovido a sucesso.

## Serviços externos

Neon, Firebase Cloud Messaging, e-mail e mensagem externos não podem ser apresentados como conectados sem configuração e validação. O endpoint de saúde deve indicar os transportes ativos e o tipo de banco. Um registro na outbox local não é um e-mail, SMS ou push enviado.

O backend retorna timestamps persistidos como epoch em milissegundos. O ACK de comando usa `ackedAt` e status `SUCCEEDED` ou `FAILED`; `LEASED` significa apenas que um cliente recebeu uma concessão temporária para executar, nunca sucesso confirmado.

`GET /api/health` informa `mode`, `database`, `emailTransport`, `smsTransport` e `billingConfigured`. O backend local usa `DEVELOPMENT` como status inicial de assinatura, `CANCELLED` após cancelamento, sem fatura ou cobrança real. Alterar plano no desenvolvimento não comprova pagamento.

Validação de e-mail do contato é separada da validação por telefone. Alertas de e-mail exigem `allowAlerts=true` e `emailVerified=true`; código de telefone sozinho não autoriza envio de e-mail. FCM e alertas push não estão configurados.

`GET /api/device/me` aceita a credencial Bearer já emitida e retorna `{device:{id,name,platform,state,...}}`, sem criar dispositivo novo; serve para pareamento de um token do painel. O primeiro evento inicializa o estado persistido do Android; os seguintes seguem a matriz de transições. `RECOVER` só é enfileirado para uma emergência ativa. Encerrar assinatura ou revogar uma sessão não encerra emergência.
