<#
    Creates a minimal data tree for the server so the desktop app has
    something to download during local testing.

    Output:  ../server/build-local/serverdata/
    (that folder is where build.ps1 -Run copies + launches the jar, and the
     server resolves all paths relative to the jar's own folder.)

    For real data instead of this fixture, point the server at the full
    "Server Content" corpus - see local-dev/README.md.

    Layout the server expects (see WorkerRunnable.java):
      Covers/<PS1|PS2>/<region>/<coverType>/<gameId>/<gameId>_(<n>)<coverType>.jpg   (.png for _ICO)
      Lists/<console>_All_Covers.txt   (+ _All_Configs / _All_Cheats / _All_VMCs)
      Configs/<console>/<region>/<gameId>.cfg
      Cheats/<console>/<region>/<gameId>.cht
      region is one of  NTSCU | PAL | NTSCJ   (PopsGameManager.determineGameRegion)
#>
$ErrorActionPreference = 'Stop'
$dataRoot = Join-Path $PSScriptRoot '..\server\build-local\serverdata'

# --- sample catalogue -------------------------------------------------------
$games = @(
    @{ id = 'SLUS_207.68'; region = 'NTSCU'; title = 'God of War' }
    @{ id = 'SCES_509.24'; region = 'PAL';   title = 'Gran Turismo 4' }
)
$jpgCovers = '_COV', '_COV2', '_SCR', '_BG'
$pngCovers = '_ICO'

# --- folders --------------------------------------------------------------
foreach ($d in 'Covers', 'Lists', 'Configs', 'Cheats', 'MemoryCards', 'Application', 'Log', 'Uploads', 'Reports') {
    New-Item -ItemType Directory -Force -Path (Join-Path $dataRoot $d) | Out-Null
}

# --- tiny placeholder images via Pillow ---------------------------------
function New-Placeholder([string]$path, [string]$fmt, [string]$text, [int]$w, [int]$h) {
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    $py = @"
from PIL import Image, ImageDraw
im = Image.new('RGB', ($w, $h), (32, 64, 128))
d = ImageDraw.Draw(im)
d.rectangle([0, 0, $w-1, $h-1], outline=(255, 255, 255))
d.text((6, 6), '''$text''', fill=(255, 255, 255))
im.save(r'''$path''', '$fmt')
"@
    $py | python -
    if ($LASTEXITCODE -ne 0) { throw "Pillow failed generating $path (is 'python' the 3.11 install with PIL?)" }
}

foreach ($g in $games) {
    foreach ($ct in $jpgCovers) {
        $p = Join-Path $dataRoot "Covers/PS2/$($g.region)/$ct/$($g.id)/$($g.id)_(0)$ct.jpg"
        New-Placeholder $p 'JPEG' "$($g.title)`n$ct" 280 400
    }
    foreach ($ct in $pngCovers) {
        $p = Join-Path $dataRoot "Covers/PS2/$($g.region)/$ct/$($g.id)/$($g.id)_(0)$ct.png"
        New-Placeholder $p 'PNG' "$($g.title)`nICO" 64 64
    }
    # a config + cheat so those code paths have something too
    $cfg = Join-Path $dataRoot "Configs/PS2/$($g.region)/$($g.id).cfg"
    New-Item -ItemType Directory -Force -Path (Split-Path $cfg) | Out-Null
    "`$ConfigSource=2`nCfgVersion=3`nTitle=$($g.title)`n" | Set-Content -Encoding ascii $cfg
    $cht = Join-Path $dataRoot "Cheats/PS2/$($g.region)/$($g.id).cht"
    New-Item -ItemType Directory -Force -Path (Split-Path $cht) | Out-Null
    "`"$($g.title)`"`n// sample cheat file, no codes`n" | Set-Content -Encoding ascii $cht
}

# --- list files (opaque cache blobs client-side; ids one per line) ---------
$ids = ($games | ForEach-Object { $_.id }) -join "`n"
foreach ($suffix in '_All_Covers.txt', '_All_Configs.txt', '_All_Cheats.txt', '_All_VMCs.txt') {
    Set-Content -Encoding ascii -Path (Join-Path $dataRoot "Lists/PS2$suffix") -Value $ids
    Set-Content -Encoding ascii -Path (Join-Path $dataRoot "Lists/PS1$suffix") -Value ''
}

Write-Host "==> serverdata fixture ready at:"
Write-Host "    $((Resolve-Path $dataRoot).Path)"
Get-ChildItem -Recurse -File $dataRoot | ForEach-Object { "    " + $_.FullName.Substring((Resolve-Path $dataRoot).Path.Length + 1) }
