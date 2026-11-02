# Transferência para a Área de Trabalho do Windows

O projeto foi preparado em um ambiente Linux remoto. Isso não comprova acesso a `C:\Users\55819\Desktop\sentinela` nem configura essa pasta no computador do usuário.

Para localizar o Desktop real, inclusive redirecionado para OneDrive, execute no PowerShell local:

```powershell
$sentinelaDesktop = [Environment]::GetFolderPath('DesktopDirectory')
$sentinelaFolder = Join-Path $sentinelaDesktop 'sentinela'
Get-Item -LiteralPath $sentinelaFolder
Get-ChildItem -LiteralPath $sentinelaFolder -Force
```

O script `IMPORTAR_WINDOWS.ps1` recebe o ZIP de entrega, verifica o Desktop real, procura a pasta existente e tenta alternativas OneDrive apenas quando necessário. Ele não substitui arquivos diferentes: mantém o original e copia a versão recebida com sufixo `.incoming-*`. Conflitos precisam ser revisados antes de rodar o projeto.

```powershell
powershell -File .\IMPORTAR_WINDOWS.ps1 -ArchivePath .\sentinela-fase2.zip
```

Se a política local bloquear scripts, o usuário pode extrair o ZIP em uma pasta temporária e copiar os arquivos manualmente após comparar os existentes. Não é necessário enfraquecer a política global do Windows.

Após importar:

1. Abra a pasta real `sentinela` no VS Code/Claude.
2. Confirme que `apps/web`, `apps/android`, `packages/shared` e `docs` estão presentes.
3. Verifique a versão do Node indicada pelo README; o backend local usa `node:sqlite` do Node 24.
4. Siga o comando de inicialização do README; não use o endereço `terminal.local` como URL do servidor no Windows.
5. Para aparelho físico, use apenas uma rede local confiável e a configuração de desenvolvimento explicitamente documentada. A URL remota privada da apresentação não é o backend autenticado Android.
6. Abra `apps/android` no Android Studio e instale SDK/Gradle requeridos antes de compilar. Fonte Android não equivale a APK compilado/testado.

Não importe `.env`, bancos de teste, `node_modules`, caches, credenciais ou `.git` de outro projeto. Configure serviços externos separadamente.
