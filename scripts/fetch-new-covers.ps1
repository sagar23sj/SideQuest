# Fetches topical cover photos for newly added default buckets.
# Tries Unsplash (source) first, falls back to LoremFlickr (CC Flickr photos).
$ErrorActionPreference = 'Stop'
$dest = Join-Path $PSScriptRoot '..\app\src\main\res\drawable-nodpi'

# domain -> search keywords
$targets = @{
    'vault'        = 'safe,vault,lockbox,documents'
    'appointments' = 'calendar,planner,schedule'
    'bills'        = 'invoice,receipt,money,finance'
}

function Save-Cover([string]$name, [string]$keywords) {
    $out = Join-Path $dest "bucket_photo_$name.jpg"
    $kw = ($keywords -split ',')[0]
    $urls = @(
        "https://source.unsplash.com/featured/800x600/?$keywords",
        "https://loremflickr.com/800/600/$kw"
    )
    foreach ($u in $urls) {
        try {
            Write-Host "Fetching $name from $u"
            Invoke-WebRequest -Uri $u -OutFile $out -MaximumRedirection 5 -TimeoutSec 30
            if ((Get-Item $out).Length -gt 5000) {
                Write-Host "  saved $name ($((Get-Item $out).Length) bytes)"
                return
            }
        } catch {
            Write-Host "  failed: $($_.Exception.Message)"
        }
    }
    Write-Warning "Could not fetch a good image for $name"
}

foreach ($k in $targets.Keys) {
    Save-Cover $k $targets[$k]
}
