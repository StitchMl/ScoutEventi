param(
    [Parameter(Mandatory = $true)]
    [string]$CertificatePath,

    [Parameter(Mandatory = $true)]
    [string]$ProvisioningProfilePath,

    [Parameter(Mandatory = $true)]
    [string]$AppStoreConnectKeyPath,

    [Parameter(Mandatory = $true)]
    [string]$AppStoreConnectKeyId,

    [Parameter(Mandatory = $true)]
    [string]$AppStoreConnectIssuerId,

    [string]$CertificatePassword = "",

    [string]$Repository = "StitchMl/ScoutEventi",

    [string]$GitHubToken = $env:GITHUB_TOKEN,

    [string]$Ref = "",

    [string]$BundleId = "",

    [string]$AppName = "",

    [string]$DownloadDir = ""
)

$ErrorActionPreference = "Stop"

if (-not $GitHubToken) {
    throw "GITHUB_TOKEN mancante. Passalo con -GitHubToken oppure imposta `$env:GITHUB_TOKEN."
}

$repoParts = $Repository.Split("/", 2, [System.StringSplitOptions]::RemoveEmptyEntries)
if ($repoParts.Count -ne 2) {
    throw "Repository deve essere nel formato owner/repo."
}

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$nodeScript = Join-Path $scriptRoot "github_signed_ipa.mjs"
$packageJson = Join-Path $scriptRoot "package.json"

if (-not (Test-Path -LiteralPath $nodeScript)) {
    throw "Script Node non trovato: $nodeScript"
}

if (-not (Test-Path -LiteralPath $packageJson)) {
    throw "package.json non trovato: $packageJson"
}

$resolvedCertificatePath = (Resolve-Path -LiteralPath $CertificatePath).Path
$resolvedProvisioningProfilePath = (Resolve-Path -LiteralPath $ProvisioningProfilePath).Path
$resolvedAscKeyPath = (Resolve-Path -LiteralPath $AppStoreConnectKeyPath).Path

try {
    node --version | Out-Null
    npm --version | Out-Null
} catch {
    throw "Node.js e npm sono richiesti per caricare i secret e avviare la release iOS."
}

$env:GITHUB_TOKEN = $GitHubToken

try {
    if ($PSBoundParameters.ContainsKey("CertificatePassword")) {
        $env:IOS_CERTIFICATE_PASSWORD_LOCAL = $CertificatePassword
    } else {
        Remove-Item Env:IOS_CERTIFICATE_PASSWORD_LOCAL -ErrorAction SilentlyContinue
    }

    npm install --prefix $scriptRoot --no-audit --no-fund

    $args = @(
        $nodeScript,
        "--owner", $repoParts[0],
        "--repo", $repoParts[1],
        "--workflow", "ios-unlisted-release.yml",
        "--p12-path", $resolvedCertificatePath,
        "--provisioning-profile-path", $resolvedProvisioningProfilePath,
        "--export-method", "app-store",
        "--upload-to-app-store-connect", "true",
        "--asc-key-id", $AppStoreConnectKeyId,
        "--asc-issuer-id", $AppStoreConnectIssuerId,
        "--asc-private-key-path", $resolvedAscKeyPath
    )

    if ($Ref) {
        $args += @("--ref", $Ref)
    }
    if ($BundleId) {
        $args += @("--bundle-id", $BundleId)
    }
    if ($AppName) {
        $args += @("--app-name", $AppName)
    }
    if ($DownloadDir) {
        $args += @("--download-dir", $DownloadDir)
    }

    node @args
} finally {
    Remove-Item Env:IOS_CERTIFICATE_PASSWORD_LOCAL -ErrorAction SilentlyContinue
}
