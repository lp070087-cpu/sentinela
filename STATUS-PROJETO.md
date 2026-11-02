# SENTINELA — fase 2 e demonstração

## Iniciar
Requer Node.js24+. Na raiz: `npm run dev` (sem dependências npm). Opcional: copie `.env.example` para `.env`.
- Apresentação preservada: http://localhost:4173/
- Login: http://localhost:4173/panel.html
- Entre sem conta pelo botão **Entrar na demonstração**, ou abra http://localhost:4173/demo-app.html.

## DEMO isolada
Dez módulos interativos: dashboard, comandos de voz, acionamentos, localização fictícia, celular perdido, contatos, recuperação, painel de recuperação, assinatura e perfil. A apresentação original mantém suas14telas.

Voz/contatos têm CRUD; emergência possui6etapas; plano mensal/anual oferece seleção, contratação, renovação e cancelamento simulados; há alarme voluntário, fila de comandos simulados, preferências, notificações, persistência e **Restaurar dados de demonstração**. Usa chave própria localStorage, sem fetch/API, GPS ou microfone. CSP bloqueia conexões de API.

No Android, **Entrar na demonstração** abre os mesmos arquivos offline em WebView isolado: sem ponte JavaScript, rede, acesso a arquivos externos ou permissões. As ações nativas reais são bloqueadas enquanto a demo está aberta; sair não encerra uma emergência real.

## Backend local real
Cadastro/login, scrypt, sessões HttpOnly, CSRF, ownership, códigos aleatórios expirantes e uso único, recuperação/revogação, cadastro/pareamento de dispositivos, tokens por aparelho, eventos/estados deduplicados, posições com precisão/data, retenção/exclusão, contatos com consentimento, fila/lease/ACK, auditoria e estrutura de assinatura sem cobrança. SQLite persistente; produção bloqueada até migração/infraestrutura.

E-mail e SMS usam caixa de entrega de desenvolvimento. Isso testa o fluxo, não comprova posse ou entrega real. Nenhuma senha/código fixo libera APIs reais.

## Android nativo
Kotlin/Compose, DevicePolicyManager apenas force-lock autorizado, sessão local finita de voz quando suportada, localização pontual consentida, biometria/credencial nativa, token Keystore, fila offline, serviço foreground voluntário para comandos e notificação de bloqueio, modo perdido dentro do aplicativo, alarme autenticado e recuperação sem remover PIN.

APK debug Android12+ em `apps/android/app/build/outputs/apk/debug/app-debug.apk`. Build e6testes JVM passaram; assinatura verificada. Consulte `apps/android/BUILD_VALIDATION.md`.

## Testes efetivos
32testes automatizados backend/contratos/demo, checks de sintaxe e6testes JVM Android. Navegador Chromium: entrada DEMO pelo login; navegação nos10módulos; emergência6etapas; comandos CRUD; contato; perda/alarme; recuperação; contratação/cancelamento/renovação; persistência/reload; perfil; logout/entrada; restauração; comando remoto com retorno explícito. Larguras de iframe320,360,375,390,768,1440px sem overflow horizontal; menu móvel fecha após navegação.

Não houve emulador/aparelho físico nem Safari/iPhone real. Compilação não comprova lockNow, microfone, GPS ou biometria em hardware. A área autenticada real foi validada via HTTP, sem teste visual completo de conta no navegador.

## Ambiente Windows e continuidade
Este trabalho ocorreu em Linux remoto `/workspace/sites/sentinela`. Desktop/OneDrive do Windows não está montado e não foi conectado. Baixe o código e use `docs/IMPORTAR_WINDOWS.ps1` com ZIP para importar na pasta sentinela existente, preservando conflitos como `.incoming-*`; depois abra no VSCode/Claude/Codex. Consulte README.md e docs/CONTINUAR_NO_WINDOWS.md.

Para USB autorizado: `adb reverse tcp:4173 tcp:4173`; URL do app http://127.0.0.1:4173. Emulador oficial: http://10.0.2.2:4173. Compile em apps/android com JDK17/SDK35: `gradlew.bat testDebugUnitTest assembleDebug`.

## Pendências reais
Neon/adapter PostgreSQL (schema Prisma preparado), FCM/push e entregabilidade e-mail/SMS, mapa geográfico (esquema com coordenadas recebido hoje), MFA/passkeys web, aprovação de recuperação por contato, Billing real, widget/tile, teste físico Android e iOS nativo. Não há GPS depois de desligar, escuta permanente nem interceptação livre de Power/volume/SOS.

## Publicação
Destino autorizado: https://github.com/lp070087-cpu/sentinela.git, branch main. Git do terminal sem credencial de escrita: publicação pela integração GitHub autenticada, sem force. Histórico original exato preservado no bundle e referência local, descrito em docs/HISTORICO-PRESERVADO.md. Nenhum deploy Vercel nesta fase. vercel.json original só publica a apresentação estática, não a API SQLite.

## Recuperação do ambiente
A manutenção automática removeu arquivos temporários entre turnos. O checkpoint de62fontes e o histórico original foram recuperados; a DEMO foi recomposta e o APK foi recompilado e novamente testado. Não confundir resultados antigos com os testes desta execução. Cópias finais e GitHub evitam depender do ambiente temporário.
