# Execute localmente no Windows. Não conecta o Desktop a um ambiente remoto.
# Uso: powershell -File .\IMPORTAR_WINDOWS.ps1 -ArchivePath .\sentinela-fase2.zip
param([Parameter(Mandatory=$true)][string]$ArchivePath)
$ErrorActionPreference = 'Stop'
$sentinelaArchive = (Resolve-Path -LiteralPath $ArchivePath).Path
$sentinelaDesktop = [Environment]::GetFolderPath('DesktopDirectory')
if ([string]::IsNullOrWhiteSpace($sentinelaDesktop) -or !(Test-Path -LiteralPath $sentinelaDesktop -PathType Container)) {
  throw 'O caminho real da Área de Trabalho não foi encontrado. Abra a pasta sentinela no Explorer e forneça esse caminho manualmente.'
}
$sentinelaTarget = Join-Path $sentinelaDesktop 'sentinela'
if (!(Test-Path -LiteralPath $sentinelaTarget -PathType Container)) {
  $sentinelaCandidates = @()
  foreach ($sentinelaOneDrive in @($env:OneDrive, $env:OneDriveConsumer, $env:OneDriveCommercial)) {
    if ($sentinelaOneDrive) {
      foreach ($sentinelaDesktopName in @('Desktop', 'Área de Trabalho')) {
        $sentinelaCandidate = Join-Path (Join-Path $sentinelaOneDrive $sentinelaDesktopName) 'sentinela'
        if (Test-Path -LiteralPath $sentinelaCandidate -PathType Container) { $sentinelaCandidates += $sentinelaCandidate }
      }
    }
  }
  $sentinelaCandidates = @($sentinelaCandidates | Select-Object -Unique)
  if ($sentinelaCandidates.Count -eq 1) { $sentinelaTarget = $sentinelaCandidates[0] }
  elseif ($sentinelaCandidates.Count -gt 1) { throw ('Mais de uma pasta sentinela encontrada: ' + ($sentinelaCandidates -join ', ') + '. Escolha manualmente antes de importar.') }
  else { throw ('A pasta sentinela não existe no Desktop real (' + $sentinelaDesktop + ') ou no OneDrive. Nenhuma pasta foi alterada.') }
}
$sentinelaTemp = Join-Path ([IO.Path]::GetTempPath()) ('sentinela-import-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $sentinelaTemp | Out-Null
$sentinelaConflicts = @()
try {
  Expand-Archive -LiteralPath $sentinelaArchive -DestinationPath $sentinelaTemp
  $sentinelaSource = $sentinelaTemp
  $sentinelaRoots = @(Get-ChildItem -LiteralPath $sentinelaTemp -Force)
  if ($sentinelaRoots.Count -eq 1 -and $sentinelaRoots[0].PSIsContainer) { $sentinelaSource = $sentinelaRoots[0].FullName }
  if (!(Test-Path -LiteralPath (Join-Path $sentinelaSource 'apps') -PathType Container)) { throw 'O ZIP não contém a estrutura esperada apps/. Nenhuma transferência foi realizada.' }
  foreach ($sentinelaFile in Get-ChildItem -LiteralPath $sentinelaSource -File -Recurse -Force) {
    $sentinelaRelative = $sentinelaFile.FullName.Substring($sentinelaSource.Length).TrimStart([IO.Path]::DirectorySeparatorChar)
    if ($sentinelaRelative -match '(^|[\\/])(\.git|node_modules|data|\.gradle|build)([\\/]|$)' -or $sentinelaRelative -match '(^|[\\/])\.env$') { continue }
    $sentinelaDestination = Join-Path $sentinelaTarget $sentinelaRelative
    $sentinelaParent = Split-Path -Parent $sentinelaDestination
    if (!(Test-Path -LiteralPath $sentinelaParent)) { New-Item -ItemType Directory -Path $sentinelaParent -Force | Out-Null }
    if (Test-Path -LiteralPath $sentinelaDestination) {
      if ((Get-FileHash -LiteralPath $sentinelaDestination).Hash -eq (Get-FileHash -LiteralPath $sentinelaFile.FullName).Hash) { continue }
      $sentinelaIncoming = $sentinelaDestination + '.incoming-' + [guid]::NewGuid().ToString('N').Substring(0,8)
      Copy-Item -LiteralPath $sentinelaFile.FullName -Destination $sentinelaIncoming
      $sentinelaConflicts += $sentinelaRelative
    } else { Copy-Item -LiteralPath $sentinelaFile.FullName -Destination $sentinelaDestination }
  }
  Write-Host ('Projeto importado para: ' + $sentinelaTarget)
  if ($sentinelaConflicts.Count) {
    Write-Warning 'Arquivos conflitantes foram preservados. A versão nova tem sufixo .incoming-* e precisa de revisão antes de executar.'
    $sentinelaConflicts | ForEach-Object { Write-Host ('Conflito: ' + $_) }
  } else {
    Set-Location -LiteralPath $sentinelaTarget
    Write-Host 'Pasta de trabalho selecionada. Consulte README.md e docs/TEST_PLAN.md antes de testar.'
  }
} finally {
  Remove-Item -LiteralPath $sentinelaTemp -Recurse -Force
}
