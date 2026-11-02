# Roteiro de validação — fase 2

Marcar um teste como aprovado exige evidência da execução no ambiente indicado. Compilação, inspeção estática e aparelho físico são categorias diferentes.

## Automatizado local

`node --test packages/shared/domain.test.mjs`

Os testes do domínio verificam coordenadas/timestamp inválidos, comandos desconhecidos, transições indevidas e a distinção entre fila e execução. Os demais testes da API devem usar banco descartável separado, nunca credenciais de produção.

| Cenário | Evidência esperada |
| --- | --- |
| Cadastro | Conta persistida; senha não armazenada em texto |
| Login/logout | Senha incorreta falha, cookie seguro de sessão, revogação real |
| Verificação | Token aleatório, expiração, uso único, limite de tentativas; entrega local identificada |
| Recuperação de conta | Código/token errado falha; consumo único; sessões antigas revogadas |
| CSRF | Escrita autenticada sem token rejeitada |
| Isolamento | Usuário B não lê/aciona/revoga dispositivo, localização ou contato de A |
| Dispositivo | Token separado da sessão; revogação invalida o bearer |
| Comando | Enfileirado não consta como executado; falha preservada; replay de ACK rejeitado |
| Localização | Valores fora do intervalo/futuro rejeitados; autorização obrigatória |
| Offline | Última posição e horário permanecem; nenhuma falsa atualização ao vivo |
| Retenção | Registros fora da janela excluídos no banco |
| Produção | Outbox e serviços não configurados bloqueados; nenhum modo admin demo |

## Navegador

1. Abrir apresentação preservada e comparar visual aprovado.
2. Abrir painel e confirmar identificação de desenvolvimento local.
3. Criar duas contas e validar fluxos de forma independente.
4. Conferir e-mail por token aleatório da outbox local, sem envio externo presumido.
5. Registrar dispositivo; conferir token exibido só na criação e não nos históricos.
6. Criar contato, solicitar e consumir código; testar código errado e expirado.
7. Enfileirar modo perdido; verificar rótulo “aguardando dispositivo”, não “executado”.
8. Verificar última posição real autorizada e horário quando houver Android conectado.
9. Recarregar painel para validar persistência; sair e testar revogação.
10. Testar em larguras 320,360,375,390,768 e desktop; Safari/iPhone físico separadamente.

## Android em emulador

Se SDK/emulador disponíveis: compilar `assembleDebug`, instalar APK, testar navegação e permissões negadas/concedidas. Registrar versão do Android, ABI e mecanismo de voz disponível. Emulador sem hardware/serviço local de reconhecimento não valida voz em aparelho físico.

## Android físico — somente aparelho autorizado

1. Confirmar backup/acesso ao PIN antes do teste; não alterar credenciais nativas.
2. Entrar com conta de teste verificada; cadastrar o aparelho.
3. Ler e ativar explicitamente Device Admin; testar revogação e erro sem permissão.
4. Tocar em bloqueio; verificar tela realmente bloqueada e resultado separado da chamada API.
5. Autorizar microfone; iniciar uma sessão local de reconhecimento e frase configurada. Registrar falsos positivos/negativos e idioma/modelo.
6. Encerrar/ocultar app e verificar indicador de monitoramento. Não presumir escuta se serviço parar.
7. Autorizar localização enquanto em uso; conferir precisão, horário e sincronização no painel.
8. Desligar rede; produzir evento autorizado e testar política de fila/reenvio implementada. Não marcar sincronizado antes do ACK.
9. Executar comando remoto com aparelho online e cliente ativo; observar resultado no painel.
10. Testar modo perdido, alarme/parada, recuperação e confirmação forte, sem promessa de remoção do PIN.
11. Revogar dispositivo pelo painel e verificar rejeição de novas requisições do token.
12. Não testar combinações de SOS nativo nem apagar/reiniciar aparelho sem autorização adicional.

## Dependências e bloqueios

Ausência de SDK, Gradle, emulador, aparelho USB autorizado, credenciais Neon, FCM ou provedores de entrega precisa ser registrada. Nesses casos, entregar código e instruções, sem afirmar APK gerado, bloqueio físico validado ou alertas reais enviados.
