param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('acquire', 'status', 'release')]
    [string]$Action,
    [string]$Repository = '.',
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9_-]{0,80}$')]
    [string]$Resource,
    [ValidateSet('Codex', 'Claude Code')]
    [string]$Owner,
    [string]$Task,
    [string]$Token
)

# Local coordination for all worktrees sharing this Git common directory.
# Never expires claims, stops processes, grants permissions or starts a build.
$ErrorActionPreference = 'Stop'
$common = & git -C $Repository rev-parse --path-format=absolute --git-common-dir
if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve the repository Git common directory.' }
$leaseRoot = Join-Path ([IO.Path]::GetFullPath($common.Trim())) 'atomic-coordinator'
[IO.Directory]::CreateDirectory($leaseRoot) | Out-Null
$gate = $null
try {
    # OS handle serializes both mutation and observation. A crash releases it.
    $gate = [IO.File]::Open((Join-Path $leaseRoot 'mutation.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    if ($Action -eq 'status') {
        $items = @(Get-ChildItem -LiteralPath $leaseRoot -Filter '*.json' | ForEach-Object {
            Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
        })
        ConvertTo-Json -InputObject $items -Depth 5
        return
    }
    if ([string]::IsNullOrWhiteSpace($Resource)) { throw 'Resource is required.' }
    $leasePath = Join-Path $leaseRoot ($Resource + '.json')
    if ($Action -eq 'acquire') {
        if ([string]::IsNullOrWhiteSpace($Owner) -or [string]::IsNullOrWhiteSpace($Task)) {
            throw 'Owner and Task are required for acquisition.'
        }
        if (Test-Path -LiteralPath $leasePath) { throw "Resource already claimed: $Resource. Inspect status; do not steal it." }
        $record = [ordered]@{
            resource = $Resource
            owner = $Owner
            task = $Task
            token = [Guid]::NewGuid().ToString()
            acquired_utc = [DateTime]::UtcNow.ToString('o')
            repository = (& git -C $Repository rev-parse --show-toplevel).Trim()
            branch = (& git -C $Repository branch --show-current).Trim()
        }
        $json = $record | ConvertTo-Json
        $stream = [IO.File]::Open($leasePath, 'CreateNew', 'Write', 'None')
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes($json)
            $stream.Write($bytes, 0, $bytes.Length)
            $stream.Flush()
        } finally { $stream.Dispose() }
        $json
    } else {
        if ([string]::IsNullOrWhiteSpace($Token)) { throw 'The acquisition token is required for release.' }
        $record = Get-Content -LiteralPath $leasePath -Raw | ConvertFrom-Json
        if ($record.token -cne $Token) { throw 'Token mismatch; the claim remains held.' }
        Remove-Item -LiteralPath $leasePath
        Write-Output "Released $Resource"
    }
} finally {
    if ($null -ne $gate) { $gate.Dispose() }
}
