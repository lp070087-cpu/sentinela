# Privacidade, consentimento e limites técnicos

## Dados mínimos

- A apresentação `/` utiliza apenas dados fictícios e armazenamento local da demonstração.
- O painel funcional utiliza contas e registros locais. Use contas de teste.
- Android: microfone e localização exigem ação explícita e permissão nativa. Revogar uma permissão deve ser refletido na interface.
- Não gravar nem transmitir áudio contínuo. Reconhecimento local só deve estar disponível quando o mecanismo no aparelho for suportado.
- Nenhum segredo, token de dispositivo, senha, código de reset ou coordenada deve ser impresso em logs de servidor ou incluído em auditoria genérica.
- A outbox é uma ferramenta de entrega local de desenvolvimento, restrita à conta/sessão correspondente. Não é serviço de e-mail/SMS e não deve existir em produção.

## Retenção e exclusão

`retentionDays` controla a janela de retenção do histórico autorizado. Exclusão precisa ocorrer no backend, não apenas na lista visual. Verificar a implementação do job/limpeza no servidor e testar com registros vencidos. Revogação de dispositivo não apaga automaticamente o último evento de emergência; a política deve ser explícita.

Antes de uso público: definir base legal, consentimento, exportação/exclusão de conta, retenção dos backups, controle dos provedores e proteção de logs. Não coletar localização de terceiros sem autorização.

## Limites por capacidade

| Capacidade | Limite real |
| --- | --- |
| Bloqueio Android | `DevicePolicyManager.lockNow()` requer administrador ativo com política de bloqueio; chamadas podem falhar. Teste em aparelho autorizado. |
| Power três vezes | App comum não recebe uma API geral confiável para interceptar essa combinação globalmente. Não interferir no SOS nativo. |
| Power + volume menos | Normalmente é ação do sistema; não prometer detecção global. Usar botão do app, atalho ou ação autorizada de notificação quando implementado. |
| Voz | `SpeechRecognizer` não foi projetado para reconhecimento contínuo indefinido. Reconhecimento no aparelho depende de disponibilidade. Indicador deve parar com encerramento/erro. |
| Segundo plano | Microfone e localização sofrem restrições de permissões “durante o uso” e de serviços em primeiro plano. Não reiniciar escuta oculta após reboot. |
| Localização | GPS/permissão/provedor precisam estar disponíveis. Última posição não equivale a posição atual. Nenhuma leitura de GPS ocorre com aparelho desligado. |
| Aparelho desligado | Redes Buscar/Find Hub são serviços nativos de hardware compatível; o app não herda automaticamente esse acesso. |
| Modo perdido | Mensagem dentro do app não substitui a tela de bloqueio nativa. Chamadas de emergência continuam sob controle do sistema. |
| Recuperação | Encerra apenas o estado SENTINELA, sem remover PIN/Face ID/biometria nativa. Voz isolada não autoriza ações críticas. |
| iPhone | Não há app iOS nativo nesta fase. Interface responsiva não é aplicativo iOS com poderes de administração do sistema. |
| Entrega de comandos | O serviço de sincronização dataSync é iniciado explicitamente pelo usuário e mantém notificação visível. O Android pode limitá-lo/encerrá-lo; não garante recebimento contínuo com app encerrado. FCM não está configurado. |

## Fontes oficiais

- https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/sensors-and-location/location/permissions
- https://developer.android.com/guide/topics/ui/notifiers/notifications
- https://support.google.com/pixelphone/answer/9338817
- https://www.apple.com/icloud/find-my/
