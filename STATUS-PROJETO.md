# SENTINELA — Fase 3

## Executar

Na raiz do checkout, com Node.js 24+, execute `npm run dev`. A apresentação original permanece em `http://localhost:4173/`. Abra `http://localhost:4173/panel.html` para cadastro e login local ou selecione **Entrar na demonstração**. A URL direta é `http://localhost:4173/demo-app.html`.

## Mudanças desta fase

- **DEMO:** 16 módulos com rotas por fragmento: `dashboard`, `onboarding`, `protection`, `voice`, `physical`, `location`, `lost`, `contacts`, `recovery`, `web`, `devices`, `incidents`, `notifications`, `plans`, `profile` e `help`. Emergência, alarme, voz, posição, recuperação e assinatura são simulados. Alterações persistem no navegador. O perfil oferece restauração, exportação e exclusão dos dados fictícios. O pacote Android carrega os mesmos arquivos offline.
- **Painel local:** 16 áreas em `/panel.html` com menu superior direito no celular e barra lateral no desktop. Edição de perfil, contatos e nome do aparelho, notificações, detalhes de incidentes e gestão de dados usam SQLite autenticado. Os contratos estão em [API_FASE3.md](docs/API_FASE3.md).
- **Android:** navegação inferior Compose, diagnóstico, histórico, localização consentida, modo perdido local com alarme voluntário e recuperação autenticada. As áreas de conta, contatos e assinatura permanecem no painel web autenticado. [Validação Android](apps/android/BUILD_VALIDATION.md).

## Verificação

- `npm test`: 42 testes aprovados, incluindo persistência, isolamento entre contas, CSRF, autenticação, revogação, exclusão de conta e cenários DEMO.
- `npm run check` e `node scripts/sync-demo.mjs --check`: sintaxe e igualdade dos seis arquivos DEMO entre web e Android.
- A renderização das 16 áreas do painel foi exercitada com um ambiente DOM de teste. O navegador remoto não alcançou o servidor local. Validação visual nas seis larguras e Safari/iPhone segue pendente.
- Android: JDK 17 disponível. Sem SDK/cache Gradle e com download bloqueado pela rede, o wrapper não chegou à compilação. Testes JVM, APK da Fase 3 e validação física pendentes.

## Continuidade

No Windows, preserve mudanças locais e sincronize o checkout oficial. Se estiver usando um ZIP, [siga a importação segura para Desktop/OneDrive](docs/CONTINUAR_NO_WINDOWS.md). Para compilar após instalar JDK 17 e SDK 35, execute `node scripts/sync-demo.mjs --check` na raiz e `gradlew.bat testDebugUnitTest assembleDebug` em `apps/android`.

Para produção faltam adaptação PostgreSQL/Neon, validação de e-mail/SMS e push, cobrança e teste em Android físico. O SQLite e a caixa de entrega são de desenvolvimento. A API recusa iniciar em produção. Combinações Power/Volume permanecem simulações.
