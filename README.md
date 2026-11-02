# SENTINELA — Fase 2 / desenvolvimento local

A apresentação premium original foi preservada em `dist/`, sem alteração visual. O backend e o painel conectado foram adicionados separadamente. Esta versão não é um sistema de segurança de produção.

## Abrir no Windows

1. Extraia `sentinela-fase2.zip` em uma pasta temporária.
2. Para integrar à pasta **sentinela** já criada no Desktop real, execute o script `docs/IMPORTAR_WINDOWS.ps1` com o caminho do ZIP. Ele consulta o Desktop do Windows e o OneDrive; arquivos conflitantes recebem sufixo `.incoming-*`, sem substituir os originais. Se houver conflitos, revise antes de executar.
3. Abra a pasta resultante no VS Code. Requer **Node.js 24+** (o Node22.11 anteriormente utilizado não é o runtime validado nesta fase).
4. Na raiz:

```powershell
Copy-Item .env.example .env
npm run dev
```

Não há dependências npm para instalar no backend local. O arquivo `.env` é opcional para a configuração padrão e nunca deve ser publicado.

- Apresentação existente: http://localhost:4173/
- Painel com backend: http://localhost:4173/panel.html
- Saúde: http://localhost:4173/api/health

Neste ambiente remoto o preview é http://terminal.local:4173/ e o painel http://terminal.local:4173/panel.html. O ambiente Linux remoto **não tem acesso ao Desktop do Windows**. A transferência para a pasta local depende de executar a importação acima no Windows; ela não foi realizada remotamente.

## Acessar sem cadastro

Abra `/panel.html` e clique em **Entrar na demonstração**, ou abra `/demo-app.html`. A demo é isolada, persistente e restaurável; não acessa APIs, GPS, microfone ou bloqueio reais. No APK, o mesmo botão abre a experiência offline. Consulte [STATUS-PROJETO.md](STATUS-PROJETO.md) para testes, diferenças e pendências.

## Primeiro teste completo

1. Crie uma conta de teste pelo painel; senha de pelo menos12caracteres. Não há senha fixa nem conta pré-criada.
2. Na caixa de entrega local, use o token aleatório gerado para confirmar o e-mail. Isso valida o fluxo, não entrega real de e-mail.
3. Cadastre telefone principal e contato de confiança, confirme códigos locais. Para alertas, confirme também o e-mail do contato e autorize explicitamente seu recebimento.
4. Instale o APK debug quando disponível, ou abra `apps/android` no Android Studio e compile conforme seu README.
5. No aplicativo, configure a URL local e entre com a conta verificada para cadastrar o aparelho. Alternativamente, registre um dispositivo no painel e copie manualmente a credencial individual para o app. Não cadastre o mesmo aparelho duas vezes no plano de um dispositivo.
6. Para telefone via USB, com depuração explicitamente autorizada, use `adb reverse tcp:4173 tcp:4173` e URL `http://127.0.0.1:4173`. No emulador oficial, `http://10.0.2.2:4173`. HTTP em rede externa não é aceito pelo aplicativo; debug permite somente origens locais explícitas. Produção precisa de HTTPS.
7. Autorize administração no diálogo Android se desejar testar bloqueio. Configure PIN no sistema antes. Autorize microfone e localização somente nas ações desejadas.
8. Teste voz inicialmente sem bloquear, com frases negativas; a sessão é local e finita. Nenhum áudio vai ao servidor.
9. Ative voluntariamente o serviço de sincronização do Android para receber comandos do painel. Sem esse serviço ativo/online o comando aguarda ou expira, sem prometer execução.
10. Confira estados, resultados de comando e posições no painel. Recuperar encerra a emergência do Sentinela; não remove PIN ou bloqueio nativo.

Nunca compartilhe o token do dispositivo. Apenas o hash é mantido no servidor, e a credencial no Android é criptografada com Android Keystore. Se desconectar, o token deixa de funcionar.

## Estrutura

- `dist/`: apresentação/simulador original, dados fictícios via localStorage.
- `apps/web/`: API Node.js24, SQLite persistente e painel autenticado.
- `apps/android/`: Kotlin, Compose, DevicePolicyManager, SpeechRecognizer local, LocationManager, biometria e sincronização.
- `packages/shared/`: contratos JS/TypeScript e esquema Prisma de destino para Neon.
- `docs/`: arquitetura, integração, privacidade, testes e importação Windows.
- `tests/`: testes HTTP com banco temporário, isolado da conta e dos dados do usuário.

SQLite é usado exclusivamente para desenvolvimento real local, em `apps/web/data/sentinela.sqlite`. Esse banco não é distribuído no ZIP. Cada instalação cria suas próprias contas e tokens. Não há dados de demonstração dentro do painel real.

## O que já funciona no backend

Cadastro e login, hash scrypt, sessão HttpOnly, CSRF, verificação de códigos aleatórios com expiração/uso único/tentativas limitadas, recuperação de senha com revogação de sessões, cadastro e revogação de dispositivos, token independente por aparelho, ownership no servidor, contatos e consentimento, estados/eventos deduplicados, posições com precisão/data, retenção e exclusão, fila autenticada de comandos com expiração/lease/ACK, auditoria, estrutura local de planos/assinaturas e cancelamento sem desbloqueio.

A confirmação de comando não altera o estado por conta própria: a telemetria nativa atualiza os estados. Um ACK é um resultado informado pelo cliente, não comprovação independente de hardware ou atestação do dispositivo.

## Serviços externos e limites

- **Neon não conectado**: existe esquema Prisma proposto, ainda sem adapter ativo/migração. SQLite não pode ser usado em Vercel como banco durável desta API.
- **E-mail/SMS**: por padrão, somente outbox local. Adapters opcionais HTTPS de entrega estão preparados por variáveis de ambiente, mas nenhum provedor foi configurado ou validado. O retorno do provedor é aceitação, não prova de entrega. A outbox não expõe mensagens de provedores externos.
- **FCM/push**: não configurado. Esta versão consulta comandos em serviço foreground iniciado pelo usuário, sujeito aos limites do Android.
- **Confiança/recuperação**: contato pode receber alertas consentidos, mas não aprovar recuperação de outra conta nesta versão. Step-up web usa confirmação de senha e e-mail previamente verificado; MFA/passkey de produção ainda é pendência.
- **Localização**: captura pontual consentida no app; não existe GPS contínuo, rastreamento após desligar ou captura remota de background. O painel mostra coordenadas reais recebidas em um mapa esquemático identificado; tiles de mapa geográfico ainda não foram integrados.
- **Voz**: apenas reconhecimento no dispositivo quando disponível, sessão explícita finita, não monitoramento permanente. Saindo do app, a escuta é interrompida.
- **Botões**: não intercepta Power, combinações com volume ou SOS. Alternativas implementadas: botão no app e ação na notificação. Widget/tile pendentes.
- **Modo perdido**: mensagem no próprio app; tela nativa preservada. Alarme autorizado é audível e não é acionado automaticamente pelo fluxo silencioso de emergência.
- **Cobranças**: não configuradas; planos locais permitem testar limites, sem pagamento. Google Play Billing/Apple pendentes. O vercel.json preservado publica apenas a apresentação estática; não hospeda a API local.
- **Produção**: o servidor recusa iniciar com NODE_ENV=production. Publicação da apresentação anterior não equivale à publicação desta API. Não foi feito push/deploy nesta fase.

## Testar

```powershell
npm test
npm run check
cd apps/android
.\gradlew.bat testDebugUnitTest assembleDebug
```

Resultados e limitações desta execução estão em `docs/VALIDACAO_FASE2.md`. Build/APK não equivale a teste em emulador ou telefone físico.
