# SENTINELA — arquitetura da fase 2

Esta fase estende a apresentação aprovada. A apresentação e as 14 telas continuam sendo uma experiência demonstrativa isolada; seus dados de localStorage não são importados para contas reais.

## Execução local

Na raiz `/workspace/sites/sentinela`, execute `npm run dev`. A porta padrão é 4173. `/` mantém a apresentação e `/panel.html` abre o painel conectado à API local. No ambiente gerenciado, o preview é controlado pelo daemon Sites; não execute um segundo servidor em paralelo.

## Componentes

- `dist/`: apresentação premium original preservada.
- `apps/web`: servidor local Node.js 24, API, painel funcional e banco SQLite de desenvolvimento persistido em disco.
- `apps/android`: projeto Kotlin/Jetpack Compose e integrações nativas oficiais.
- `packages/shared/domain.mjs` + `.d.ts`: estados, comandos e validação de posições.
- `packages/shared/prisma/schema.prisma`: **proposta de schema PostgreSQL/Neon para migração futura**, não conectado ao backend local.
- `docs`: limites, contrato e roteiro de testes.

A escolha de SQLite permite testar persistência real sem inventar uma conexão Neon. Produção deve permanecer bloqueada enquanto faltarem um adaptador PostgreSQL revisado, credenciais, provedores de entrega e configuração segura de hospedagem.

## Modelo de confiança

O painel identifica a conta por sessão autenticada. Todos os acessos a dispositivos, posições, comandos, contatos e histórico precisam verificar propriedade no servidor, inclusive rotas cujo identificador foi fornecido pelo cliente. O telefone cadastrado é dado de contato, não uma credencial para controlar aparelho.

O Android se registra após login e validação de e-mail. Recebe um token próprio, limitado ao dispositivo. Tokens de sessão e de dispositivo são distintos. Revogar um dispositivo deve invalidar seu token imediatamente e impedir novas posições, eventos e consultas de comandos.

Comandos remotos exigem uma sessão válida, CSRF e confirmação adicional de identidade (nesta base local, nova verificação de senha). Ações remotas entram em fila. “Enviado” significa que a solicitação foi registrada; não significa bloqueio executado. O estado de execução só pode ser confirmado por resultado autenticado do Android.

## Estados

| Estado | Significado |
| --- | --- |
| DISABLED | Proteção do SENTINELA desativada |
| ACTIVE | Proteção do aplicativo habilitada |
| EMERGENCY | Emergência registrada; resultado do bloqueio é informação separada |
| LOST | Modo perdido solicitado/aplicado conforme reconhecimento do cliente |
| RECOVERING | Recuperação em andamento |
| RESOLVED | Emergência do SENTINELA encerrada |

Desconexão, vencimento de plano ou encerramento da sessão não podem encerrar silenciosamente emergência ativa. A recuperação não remove PIN, biometria ou bloqueio nativo do Android.

## Sincronização

O cliente Android envia eventos e posições com autorização do usuário. O backend registra o horário informado de captura e o horário de recebimento. Um horário sem conectividade não deve ser apresentado como “ao vivo”. No painel, o último contato e a última posição precisam continuar visíveis quando offline.

A base usa consulta autenticada de comandos enquanto o cliente pode executar. Push FCM, execução confiável em segundo plano e entregas externas precisam de configuração e validação adicionais; não são substituídos por uma animação ou cronômetro web.

## Evolução PostgreSQL/Neon

1. Configurar uma instância Neon de testes e `DATABASE_URL` somente no servidor.
2. Selecionar uma versão Prisma compatível, revisar schema e gerar migração SQL sem acessar produção.
3. Adaptar as consultas do backend local para Prisma; o arquivo de schema sozinho não realiza essa migração.
4. Aplicar constraints e transações para sessões, challenges de uso único, arrendamento/ACK de comandos e limites de dispositivos.
5. Migrar apenas dados de teste autorizados. Nunca importar registros da apresentação pública.
6. Reexecutar testes de isolamento entre usuários, retenção, revogação e concorrência usando PostgreSQL real.
7. Configurar HTTPS, domínio próprio, segredo, cookies seguros e provedores externos antes de habilitar produção.

## Assinaturas

Os planos são estruturas de autorização preparadas. Não há cobrança autorizada nesta fase. Google Play Billing e as condições da Apple precisam ser implementados com verificação de recibos no servidor. O cancelamento de plano não pode retirar segurança ou encerrar uma emergência já ativa.
