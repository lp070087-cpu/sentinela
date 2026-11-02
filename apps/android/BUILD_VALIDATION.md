# Validação Android — 02 novembro 2026

## Resultado efetivo desta execução

- `testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**.
- Primeira execução completa: 43 tarefas executadas, 4m58s.
- Após incorporar os arquivos finais da DEMO e uma correção de nulabilidade: execução incremental final bem-sucedida, 43 tarefas (9 executadas,34 atualizadas),24s.
- JUnit: **6 testes,0 falhas,0 erros**. Relatório em `app/build/test-results/testDebugUnitTest/TEST-com.unitrix.sentinela.ProtectionCoreTest.xml`.
- Verificação de assinatura com `apksigner verify --verbose --print-certs`: aprovada, esquema v2, assinatura de desenvolvimento Android Debug.
- Os6 arquivos HTML/CSS/JS/MJS/fontes da DEMO embutidos no APK foram comparados byte por byte com os fontes web finais: todos iguais.
- Arquivos XML do Android parseados sem erro.

APK gerado: `app/build/outputs/apk/debug/app-debug.apk`.

Cópia de entrega no ambiente desta execução: `/workspace/scratch/c2b5bab15cc3/sentinela-dev.apk`.

Tamanho: **26.782.098 bytes**.

SHA-256:

```text
a4051748f9ccc21f9cba8f70125521fa4c9519e137b9fa0c02542a5b4ee9d4d2
```

Application ID debug: `com.unitrix.sentinela.dev`; Android12+ (minSdk31), target/compile SDK35.

## O que os testes JVM verificam

Correspondência exata de frases, limites de palavras, acentos e espaços, falsos positivos por sentenças incidentais, confiança mínima, recusa de confiança desconhecida/NaN, autorização explícita e proteção ativa para gatilho, e política de origem HTTPS/HTTP local de desenvolvimento.

Esses testes não exercitam DevicePolicyManager, microfone, GPS, permissões, biometria, notificações ou WebView num Android em execução.

## Isolamento da DEMO implementado no código

- Activity interna, não exportada; arquivos da demonstração embutidos no APK.
- Origem HTTPS virtual `appassets.androidplatform.net`, sem acesso real à rede. Toda requisição é interceptada e apenas os6 arquivos locais exatos são servidos.
- Sem ponte JavaScript; permissões WebView negadas; geolocalização desativada; acesso a arquivos/conteúdo e acesso universal de file URLs desativados.
- CSP com `connect-src 'none'`, scripts e fontes locais.
- Entrada na DEMO cancela captura de microfone/localização e interrompe sincronização real, sem apagar emergência em andamento.
- Guarda `demoActive` impede lock nativo, chamadas reais à API, captura de posição e processamento de comandos enquanto a DEMO está aberta.
- Voltar ao aplicativo real remove a guarda; sincronização real precisa ser reativada explicitamente. O modo demonstrativo não altera tokens ou contas reais.

## Ambiente de build

Gradle8.9 e wrapper oficial, Kotlin2.0.21, Android Gradle Plugin8.7.3, Temurin JDK17 completo, SDK Platform35. Build Tools35 foram disponibilizados; o AGP selecionou e instalou Build Tools34.0.0 para esta execução.

SHA-256 da distribuição Gradle verificado e fixado no wrapper. JDK validado com SHA-256 publicado pela Adoptium. Pacotes Android baixados de `dl.google.com` e validados com checksums do XML oficial do repositório Google. A JVM usou o proxy normal do ambiente e o truststore do sistema; verificação TLS permaneceu habilitada.

Um cache de transformação Gradle gerado apresentou metadata ausente na primeira tentativa incremental; foi preservado em quarentena e regenerado. A execução final passou. Aviso de biblioteca nativa não removida de símbolos no debug não impediu a geração do APK.

## Limitações e testes ainda obrigatórios

`adb devices` executado: **nenhum aparelho conectado**. Não existe emulador instalado nesta execução. **Não houve instalação nem navegação do APK em Android físico ou emulador.**

Ainda é necessário testar a interface Android/DEMO WebView, conceder e recusar permissões, observar bloqueio real, verificar disponibilidade e idioma do reconhecimento local, testar voz e falsos positivos no mundo real, conferir GPS/precisão, biometria, recuperação, sincronização/limites do serviço foreground e comandos remotos num aparelho autorizado.

O APK é de desenvolvimento, não distribuição Play Store. Não garante proteção contra desligamento, rastreamento desligado, microfone permanente ou captura contínua de localização. iPhone não instala APK; use a DEMO web nesse dispositivo.
