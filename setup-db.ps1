param(
    [ValidateSet('survey_lab4', 'survey_lab4_test')]
    [string]$Database = 'survey_lab4',
    [string]$PgHost = 'localhost',
    [int]$Port = 5432,
    [string]$Psql = 'C:\Program Files\PostgreSQL\18\bin\psql.exe'
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Psql)) { throw 'psql.exe not found. Specify -Psql.' }
$previousPassword = $env:PGPASSWORD
$previousEncoding = $env:PGCLIENTENCODING
try {
    if ($env:DB_ADMIN_PASSWORD) { $env:PGPASSWORD = $env:DB_ADMIN_PASSWORD }
    else {
        $securePassword = Read-Host 'PostgreSQL password for postgres' -AsSecureString
        $env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
    }
    $env:PGCLIENTENCODING = 'UTF8'
    $envFile = Join-Path $PSScriptRoot '.env'
    $appPassword = $env:DB_PASSWORD
    if (-not $appPassword -and (Test-Path -LiteralPath $envFile)) {
        $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DB_PASSWORD=' } | Select-Object -First 1
        if ($line) { $appPassword = $line.Substring('DB_PASSWORD='.Length) }
    }
    if (-not $appPassword) { $appPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N') }
    $pgArgs = @('-X', '-w', '-h', $PgHost, '-p', "$Port", '-U', 'postgres', '-v', 'ON_ERROR_STOP=1')
    & $Psql @pgArgs -d postgres -v "database_name=$Database" -v "app_password=$appPassword" -f (Join-Path $PSScriptRoot 'database/setup-role.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Database / role setup failed.' }
    $initialized = & $Psql @pgArgs -d $Database -Atc "SELECT to_regclass('public.app_users') IS NOT NULL"
    if ($LASTEXITCODE -ne 0) { throw 'Schema check failed.' }
    if ($initialized -eq 'f') {
        & $Psql @pgArgs -d $Database --single-transaction -f (Join-Path $PSScriptRoot 'src/main/resources/schema.sql') -f (Join-Path $PSScriptRoot 'src/main/resources/data.sql')
        if ($LASTEXITCODE -ne 0) { throw 'Schema / data setup failed.' }
    }
    & $Psql @pgArgs -d $Database -v "database_name=$Database" -f (Join-Path $PSScriptRoot 'database/grants.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Application grants failed.' }
    @("DB_URL=jdbc:postgresql://${PgHost}:${Port}/survey_lab4", 'DB_USER=survey_lab4_app', "DB_PASSWORD=$appPassword") |
        Set-Content -LiteralPath $envFile -Encoding utf8
    Write-Host "Ready: $Database. Local .env saved."
} finally {
    $env:PGPASSWORD = $previousPassword
    $env:PGCLIENTENCODING = $previousEncoding
}
