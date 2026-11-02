# SENTINELA — demonstração de produto

Website estático em HTML, CSS e JavaScript, sem dependências de runtime ou backend. Os estados são preservados em localStorage. Nenhuma integração de segurança, localização, cobrança, SMS, e-mail ou biometria real.

## Executar
`python3 -m http.server 3000 --directory dist`
Abra http://localhost:3000. Em ambientes Sites, use `sites-preview start /workspace/sites/sentinela`.

## Vercel
Importe este diretório em um repositório próprio, selecione Other, sem build command, output directory `dist`. Não há projeto Vercel nem repositório GitHub fornecido para este trabalho. `vercel.json` incluído. Não publique em repositórios de outros projetos.

## Demonstrações
Simulação de incidente de 6 etapas; comandos com edição, seleção e exclusão; sequências visuais; painel de 6 módulos; mapa esquemático, zoom e posição fictícia; modo perdido e áudio voluntário; recuperação com segunda etapa (123456); 14 telas navegáveis; seleção mensal/anual de 3 planos sem preços; FAQs; interesse local.

## Limitações
Não é aplicativo nativo. Android exige permissões administrativas para bloqueio e gestão especial para restrições. Apps comuns de iOS não dispõem de bloqueio global ou Face ID fora de autenticação nativa. Localização desligada é capacidade de hardware e redes nativas compatíveis. Voz e atalhos físicos dependem do sistema. O site não promete impedir desligamento.
