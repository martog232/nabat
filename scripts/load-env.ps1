# Loads key=value pairs from .env into the current PowerShell session.
# Usage:   . .\scripts\load-env.ps1
# Then:    .\mvnw.cmd spring-boot:run

$envFile = Join-Path $PSScriptRoot '..\.env'
if (-not (Test-Path $envFile)) {
    Write-Error ".env not found at $envFile. Copy .env.example to .env first."
    return
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $eq = $line.IndexOf('=')
    if ($eq -lt 1) { return }
    $name  = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1).Trim()
    # Strip optional surrounding quotes
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    Set-Item -Path "Env:$name" -Value $value
    Write-Host "  set $name"
}

Write-Host "`n.env loaded into this PowerShell session." -ForegroundColor Green

