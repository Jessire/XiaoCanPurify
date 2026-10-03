param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $sdkLine = Get-Content -LiteralPath 'local.properties' | Where-Object { $_ -like 'sdk.dir=*' }
    $sdk = $sdkLine.Substring(8).Replace('\\', '\').Replace('\:', ':')
    $android = Join-Path $sdk 'platforms\android-37.0\android.jar'
    $gradle = Get-ChildItem -Path "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.5.1-bin\*\gradle-9.5.1\bin\gradle.bat" | Select-Object -First 1
    if (!$SkipBuild) {
        & $gradle.FullName :app:compileReleaseJavaWithJavac --offline --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Production compilation failed' }
    }
    $api = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\9.5.1\transforms\*\transformed\api-102.0.0-api.jar" | Select-Object -First 1
    if (!$api -or !(Test-Path -LiteralPath $android)) { throw 'Android/Xposed compilation dependencies missing' }
    $cp = "app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes;$android;$($api.FullName)"
    $sources = @(Get-ChildItem -LiteralPath tests -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
    & javac -encoding UTF-8 -cp $cp -d work/regression @sources
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & java -cp "work/regression;$cp" RegressionTests
    if ($LASTEXITCODE -ne 0) { throw 'Regression tests failed' }
} finally {
    Pop-Location
}
