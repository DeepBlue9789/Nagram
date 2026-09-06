param(
    [string]$ApkDir = "d:\Antigravity projects\Nagram\upstream_apk",
    [string]$DestDir = "d:\Antigravity projects\Nagram\TMessagesProj\src\main\libs"
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path $DestDir)) {
    New-Item -ItemType Directory -Path $DestDir -Force | Out-Null
}

Get-ChildItem -Path $ApkDir -Filter "*.apk" | ForEach-Object {
    $apkPath = $_.FullName
    Write-Host "Extracting libraries from $($_.Name)..."
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -like "lib/*/*.so") {
                # Format: lib/<abi>/<file>.so -> dest/<abi>/<file>.so
                $relative = $entry.FullName.Substring(4) # remove 'lib/'
                $targetFile = Join-Path $DestDir $relative
                $targetParent = Split-Path -Parent $targetFile
                if (-not (Test-Path $targetParent)) {
                    New-Item -ItemType Directory -Path $targetParent -Force | Out-Null
                }
                [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $targetFile, $true)
                Write-Host "Extracted: $relative"
            }
        }
    } finally {
        $zip.Dispose()
    }
}
Write-Host "Done extracting native libraries."
