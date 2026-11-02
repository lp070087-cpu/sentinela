# Validação desta execução — 02 novembro 2026

## Ambiente e preservação

- Linux remoto; projeto recuperado em `/workspace/sites/sentinela`.
- Nenhum volume Windows/Desktop/OneDrive acessível; importação local não realizada.
- `dist/index.html`, `dist/style.css`, `dist/app.js` preservados sem diff em relação à apresentação aprovada.
- Nenhum push, commit ou deploy desta fase. A URL publicada anteriormente continua sendo a apresentação, não este backend.
- Não foram usados segredos de produção. Contas dos testes automatizados usam bancos temporários apagados ao terminar.

## Backend e contratos

`npm test`: **17 testes aprovados** (13 HTTP/persistência +4 contratos). Cobre cadastro/senha; verificação aleatória e expiração; autenticação/CSRF/origem; isolamento entre contas; autorização e revogação; fila/lease/ACK e falha de comando; deduplicação atômica; retenção/exclusão de localização; OTP e tentativas; cancelamento sem desbloqueio; produção bloqueada; recuperação e revogação de sessões; reinício com persistência SQLite; ordenação de recuperação; pareamento sem duplicar dispositivo; outbox sem exposição de provedor externo.

`npm run check`: sintaxe dos módulos backend, painel e demo validada. Não há compilador TypeScript nesta implementação JavaScript; contratos `.d.ts` foram preparados para os próximos consumidores.

Os testes de integração enviam telemetria de teste para a API; isso não é teste de GPS, microfone, sensor ou bloqueio real de Android.

## Servidor e navegador

`sites-preview start /workspace/sites/sentinela` informou servidor em `http://terminal.local:4173/`, com o painel em `/panel.html`.

A primeira abertura encontrou conexão recusada. O servidor foi ajustado para aceitar os argumentos `--host` e `--port` do preview e reiniciado. A ferramenta de navegador então recusou adquirir acesso à aba por sua política de segurança. Não foram usados caminhos alternativos para contornar essa recusa. **A inspeção visual/interativa do painel novo no navegador não foi concluída.** A apresentação original teve sua validação visual na fase anterior; isso não substitui o teste do painel novo.

## Android

Consulte `apps/android/BUILD_VALIDATION.md` para resultado efetivo de compilação, testes JVM e APK. Não houve Android físico conectado nem execução em emulador nesta fase. Mesmo com APK compilado, permissões, voz local, lockNow, localização, notificação/foreground e biometria ainda precisam do roteiro manual no telefone.

## Externos não validados

Neon/Prisma ativo, FCM, entrega de e-mail/SMS, mapas geográficos, Google Play Billing, MFA/passkey web, aprovação de recuperação por contato, iOS nativo, background location e monitoramento permanente de voz continuam fora da validação desta entrega. Os limites estão no README e nos documentos de arquitetura/privacidade.


## Atualização após recuperação do ambiente
32testes backend/contratos/DEMO e6JVM Android aprovados. Navegador DEMO agora validado:10módulos e fluxos principais, persistência, restauração e larguras320–1440px sem overflow horizontal. O bloqueio de acesso da ferramenta registrado acima pertence à tentativa anterior; nesta execução a DEMO pública isolada foi aberta e testada. Painel real: testes HTTP, sem navegação autenticada visual completa. APK final recompilado e verificado; consulte BUILD_VALIDATION.md. Nenhum hardware/emulador testado.
