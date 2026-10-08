<#
.SYNOPSIS
  Create GitHub Releases for mcp-in-action tags (v08, v09 by default).

.DESCRIPTION
  - Idempotent: skips a tag if that release already exists.
  - Release notes are read from scripts/release-notes/<tag>.md
  - GitHub auto-attaches "Source code (zip)" and "Source code (tar.gz)" assets,
    whose download_count is the only free download metric available.
  - With -ShowTraffic also prints 14-day clone traffic (needs repo scope).

.EXAMPLE
  .\scripts\publish-github-releases.ps1 -Token ghp_xxx
  $env:GITHUB_TOKEN='ghp_xxx'; .\scripts\publish-github-releases.ps1
#>
param(
    [string]$Token = $env:GITHUB_TOKEN,
    [string]$Repo = "ethanliang2016/mcp-in-action",
    [string[]]$Tags = @("v08", "v09"),
    [switch]$DryRun,
    [switch]$ShowTraffic
)

$ErrorActionPreference = "Stop"

$names = @{
    "v08" = "v08 - (8) Production Readiness Checklist for Stateless MCP Server"
    "v09" = "v09 - (9) Six-Layer Security for Production MCP Server"
}

if (-not $Token) { throw "No token. Pass -Token <PAT> or set `$env:GITHUB_TOKEN (scope: repo)." }

$headers = @{
    Authorization       = "Bearer $Token"
    Accept              = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}
$base = "https://api.github.com/repos/$Repo"

function Get-JsonBody([hashtable]$obj) {
    return [System.Text.Encoding]::UTF8.GetBytes(($obj | ConvertTo-Json -Depth 6))
}

foreach ($tag in $Tags) {
    $notesPath = Join-Path $PSScriptRoot "release-notes\$tag.md"
    if (-not (Test-Path $notesPath)) { throw "Missing release notes: $notesPath" }
    $body = Get-Content -Raw -Encoding UTF8 $notesPath
    $name = if ($names.ContainsKey($tag)) { $names[$tag] } else { $tag }

    if ($DryRun) {
        Write-Host "[dryrun] would create $tag : $name (notes: $((Get-Item $notesPath).Length) bytes)" -ForegroundColor Cyan
        continue
    }

    # already released?
    $existing = $null
    try {
        $existing = Invoke-RestMethod -Uri "$base/releases/tags/$tag" -Headers $headers -Method Get
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        if ($code -eq 401) { throw "401 Unauthorized - token invalid or missing 'repo' scope." }
        if ($code -ne 404) { throw }
    }
    if ($existing) {
        Write-Host "[skip ] $tag already released -> $($existing.html_url)" -ForegroundColor Yellow
        continue
    }

    $payload = @{
        tag_name         = $tag
        name             = $name
        body             = $body
        draft            = $false
        prerelease       = $false
        target_commitish = "main"
    }

    try {
        $rel = Invoke-RestMethod -Uri "$base/releases" -Headers $headers -Method Post `
            -Body (Get-JsonBody $payload) -ContentType "application/json; charset=utf-8"
    } catch {
        $code = $_.Exception.Response.StatusCode.value__
        Write-Host "[FAIL ] $tag -> HTTP $code" -ForegroundColor Red
        throw
    }

    Write-Host "[ok   ] $tag released -> $($rel.html_url)" -ForegroundColor Green
    Write-Host ("        auto assets: " + (($rel.assets | ForEach-Object { "$($_.name) (downloads: $($_.download_count))" }) -join ", "))
}

if ($ShowTraffic) {
    try {
        $clones = Invoke-RestMethod -Uri "$base/traffic/clones" -Headers $headers -Method Get
        Write-Host "`nClone traffic (14d): total=$($clones.count) uniques=$($clones.uniques)" -ForegroundColor Cyan
        $clones.clones | Select-Object -Last 7 | ForEach-Object { Write-Host ("  {0}  {1,4} clones / {2,3} uniques" -f $_.timestamp.Substring(0,10), $_.count, $_.uniques) }
    } catch {
        Write-Host "`n[warn ] clone traffic unavailable (HTTP $($_.Exception.Response.StatusCode.value__)) - token needs 'repo' scope." -ForegroundColor Yellow
    }

    try {
        $views = Invoke-RestMethod -Uri "$base/traffic/views" -Headers $headers -Method Get
        Write-Host "Views (14d): total=$($views.count) uniques=$($views.uniques)" -ForegroundColor Cyan
    } catch { }
}
