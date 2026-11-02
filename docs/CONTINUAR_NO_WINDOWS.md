# Continuar pelo Claude ou Codex no Windows

1. Baixe e extraia o pacote de fontes em uma pasta temporária.
2. Use o script de importação do pacote para localizar a pasta sentinela no Desktop real/OneDrive. Resolva os arquivos .incoming-* antes de executar.
3. No VS Code, Arquivo → Abrir Pasta → selecione a pasta sentinela verificada. Abra a extensão Claude ou Codex nessa mesma janela.
4. Envie:

> Continue este projeto SENTINELA a partir dos arquivos presentes. Leia README.md, docs/VALIDACAO_FASE2.md e apps/android/BUILD_VALIDATION.md. Preserve dist/ e sua identidade visual. Confirme o diretório aberto e a versão Node24+. Execute npm test e npm run dev. Abra /panel.html. Para Android, use o projeto apps/android e o APK de desenvolvimento fornecido ou gere com Gradle. Não crie outra apresentação, não use senhas ou OTPs fixos, não declare capacidade física validada sem teste no aparelho, e não faça push/deploy sem identificar o destino. Primeiro resolva apenas eventuais conflitos .incoming-* da importação.

Esta instrução não envia mensagem à Claude nem controla o computador local; configura a continuidade quando você abrir o projeto no seu Windows.
