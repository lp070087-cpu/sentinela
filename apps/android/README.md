# Sentinela Android — 0.2.0 desenvolvimento

Aplicativo Kotlin / Jetpack Compose real, separado da apresentação demonstrativa. O código não é um WebView e não utiliza JavaScript para controlar recursos Android.

## Pré-requisitos

- Android Studio com JDK 17 e Android SDK Platform 35 / Build Tools 35.
- Android 12+ (minSdk31). Android físico com PIN/padrão/senha configurado para teste de bloqueio.
- Backend local do projeto e conta com e-mail confirmado.
- Gradle Wrapper 8.9 incluído. AGP 8.7.3 e Kotlin 2.0.21.

## Abrir e compilar no Windows

Abra esta pasta em Android Studio, aguarde o Sync e aceite/instale somente os SDKs oficiais necessários. O arquivo local.properties é local e não deve ir ao Git.

```powershell
cd "$([Environment]::GetFolderPath('Desktop'))\sentinela\apps\android"
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK após um build bem-sucedido: `app/build/outputs/apk/debug/app-debug.apk`. APK de desenvolvimento foi gerado e validado por assinatura nesta execução. Consulte BUILD_VALIDATION.md para o hash e os testes realmente realizados.

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb reverse tcp:4173 tcp:4173
```

No Android USB, use `http://localhost:4173` como origem. No emulador padrão use `http://10.0.2.2:4173`. Altere a porta se o backend estiver em outra porta e adapte adb reverse. Só o debug permite HTTP nos hosts de loopback acima. Release exige HTTPS com certificado válido.

## Conectar com o backend

1. Crie conta no painel web e confirme o e-mail. Em desenvolvimento, a caixa de saída local mostra a mensagem/código real gerado por evento; não há código fixo no app Android.
2. No Android, abra Conexão, informe a origem do backend, e-mail, senha (mínimo de 12 caracteres) e nome do aparelho.
3. O aplicativo faz login, obtém CSRF e cadastra o aparelho. A sessão de login fica somente em memória até terminar o cadastro. O token específico do aparelho é cifrado com chave AES/GCM no Android Keystore, com backup desabilitado.
4. Autorize explicitamente a sincronização remota. A notificação fica visível e há polling a cada 15 segundos enquanto o serviço estiver ativo. Este build ainda não contém FCM.
5. O comando remoto não depende de conhecer o número de telefone: exige sessão do proprietário + política do backend, e o Android recebe a fila com seu próprio token.

## O que está implementado

- Consentimento Android oficial de administrador solicitando apenas `force-lock`.
- Bloqueio nativo `DevicePolicyManager.lockNow()` com verificações de recurso, permissão e bloqueio seguro. O retorno distingue API aceita, bloqueio observado pelo Keyguard e falha; não promete bloqueio sem confirmação.
- Estados locais persistentes DISABLED, ACTIVE, EMERGENCY, LOST, RECOVERING, RESOLVED.
- Reconhecimento local em sessão única por `createOnDeviceSpeechRecognizer`, apenas se disponível. Não há fallback para reconhecimento de nuvem. Saindo do aplicativo, o microfone para e a autorização de bloquear por voz é desligada.
- Frases configuráveis, correspondência exata por padrão, opção de frase inteira na sentença e limiar de confiança de 70–100%. Sem confiança válida do motor, nenhum bloqueio automático. Teste de frase sem bloqueio é o padrão.
- Captura pontual real via LocationManager com permissão e consentimento, precisão/data/hora; sem captura contínua ou de background.
- Fila offline persistente (limite200) para eventos e posições; envio reconectado. Token revogado limpa fila e conexão para evitar associar dados antigos a outra conta.
- Ação de bloquear na notificação explícita. Não intercepta Power, volume nem SOS nativo.
- Serviço foreground dataSync para comandos LOCK, LOST, RECOVER, ALARM, STOP_ALARM. Comandos confirmados persistidos evitam repetição após ACK perdido; alarme para em30segundos ou no comando STOP_ALARM.
- Mensagem de perda aparece dentro do app, sem substituir a tela de bloqueio.
- Recuperação local com biometria forte ou credencial Android. Não remove PIN nem desbloqueia o sistema.
- Histórico local e exclusão local de posição/fila/histórico. Exclusão de dados já no servidor deve ocorrer no painel.

## Limites claros desta versão

- Não foi testado em aparelho físico/emulador neste ambiente; a assinatura e o conteúdo do APK foram verificados, mas não sua execução em Android.
- Não existe monitoramento permanente de microfone. SpeechRecognizer local pode não existir ou não ter o idioma pt-BR instalado.
- O serviço foreground de sincronização pode ser encerrado pelo Android e tem limites de tempo do sistema. Não se reinicia escondido após reboot e não garante entrega quando encerrado. FCM, widget e tile de configurações rápidas são próximos incrementos.
- Localização remota em background não foi solicitada nem implementada; abra o app e capture uma posição autorizada. Em LOST, permanece a última posição sincronizada até nova captura.
- Não impede desligamento nem rastreia GPS de aparelho desligado. Redes oficiais Buscar/Find Hub são capacidades independentes.
- Cadastro, verificação de números, contatos, planos e gestão de sessões são feitos pelo painel web nesta fase.
- Não há Billing, produção, coleta contínua ou bypass de PIN.

## Testes manuais obrigatórios no aparelho

1. Recuse a administração: botão de bloquear indisponível e mensagem correta.
2. Ative administração somente pelo diálogo oficial. Sem PIN, bloqueio deve permanecer indisponível.
3. Configure PIN. Autorize explicitamente o teste de bloqueio. Após lockNow, confira tela física e retorno de resultado no painel.
4. Negue microfone; confirme estado inativo. Autorize e teste uma frase correta e pelo menos20frases negativas em modo sem bloqueio. Só então habilite bloqueio real nesta sessão.
5. Saia do app durante fala e confirme que o microfone fecha. Não recomeça automaticamente.
6. Negue localização. Depois autorize captura aproximada e precisa; verifique precisão e timestamps no painel. Desligue GPS e teste timeout20segundos.
7. Conecte e sincronize. Desligue rede, registre evento, religue e confira fila sem duplicação por eventId.
8. Pelo painel envie LOCK/LOST e confirme retorno separado de envio. Alarme inicia apenas por comando autenticado, respeita volume/DND e termina.
9. Recupere localmente com autenticação Android ou remotamente após step-up no painel. O PIN nativo deve continuar obrigatório.
10. Revogue o dispositivo pelo painel e sincronize: app deve remover token e parar ações remotas.
11. Interrompa o serviço pelo Android. Reabra e confirme que o aplicativo indica inatividade.
12. Execute testes automatizados acima. A suíte pura valida normalização, falsos positivos e política de origem HTTP/HTTPS; não substitui testes de hardware.

## Fontes técnicas oficiais consultadas

- https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- https://developer.android.com/reference/android/app/admin/DeviceAdminInfo
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/reference/android/location/LocationManager
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/identity/sign-in/biometric-auth

## Entrar sem conta: demonstração offline

Toque em **Entrar na demonstração** no início da tela nativa. Ela abre o aplicativo demonstrativo completo com dados fictícios, armazenamento local próprio, restauração, saída e os mesmos arquivos da DEMO web. Não pede cadastro, e-mail, telefone, assinatura ou permissões.

A DEMO roda em um WebView offline com origem HTTPS virtual e arquivos embutidos, sem ponte JavaScript, sem rede, sem microfone/GPS e sem bloqueio nativo. As ações reais são pausadas enquanto a DEMO está aberta; uma emergência real existente é preservada. Ao retornar, reative a sincronização nativa explicitamente se desejar.

A entrada na DEMO não equivale a um login nas APIs reais. **BUILD_VALIDATION.md** registra build, testes e o limite de não ter sido executada em hardware/emulador nesta entrega.
