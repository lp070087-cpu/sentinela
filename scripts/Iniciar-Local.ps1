$ErrorActionPreference = 'Stop'
$sentinelaRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $sentinelaRoot
$sentinelaNode = node -p "process.versions.node.split('.')[0]"
if ([int]$sentinelaNode -lt 24) { throw 'Instale Node.js 24 ou superior antes de iniciar este backend.' }
Write-Host 'Apresentação: http://localhost:4173/'
Write-Host 'Login: http://localhost:4173/panel.html'
Write-Host 'Demo direta: http://localhost:4173/demo-app.html'
npm run dev
