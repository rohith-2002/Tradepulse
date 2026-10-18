param([switch]$UseCache)
$ErrorActionPreference = 'Stop'
$repo = 'Ram9219/NIFTY-50-Stock-Market-Data-2000---2022-'
$revision = 'da4ed35f2474b2f9fafd85ccc15877b178f6ce69'
$root = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $root 'tradepulse/src/main/resources/market'
$cache = Join-Path $root '.market-downloads'
New-Item -ItemType Directory -Force $destination, $cache | Out-Null
$instruments = @()
foreach ($symbol in @('RELIANCE','TCS','INFY','HDFCBANK','ICICIBANK','ITC')) {
    $url = "https://raw.githubusercontent.com/$repo/$revision/$symbol.csv"
    $file = Join-Path $cache "$symbol.csv"
    if (-not $UseCache -or -not (Test-Path -LiteralPath $file)) {
        & curl.exe --fail --silent --show-error --location --max-time 60 $url -o $file
        if ($LASTEXITCODE -ne 0) { throw "Download failed for $symbol; existing snapshot is unchanged" }
    }
    $rows = @(Import-Csv $file | Where-Object { $_.Date -ge '2019-11-01' -and $_.Date -le '2020-04-30' } | Sort-Object Date)
    $seen = @{}
    $bars = @(foreach ($row in $rows) {
        $values = @($row.Open,$row.High,$row.Low,$row.Close) | ForEach-Object { [double]::Parse($_,[cultureinfo]::InvariantCulture) }
        if ($seen.ContainsKey($row.Date) -or ($values | Where-Object { -not [double]::IsFinite($_) -or $_ -le 0 }) -or $values[1] -lt [math]::Max($values[0],$values[3]) -or $values[2] -gt [math]::Min($values[0],$values[3]) -or [long]$row.Volume -lt 0) { throw "Invalid OHLCV: $symbol $($row.Date)" }
        $seen[$row.Date] = $true
        [ordered]@{date=$row.Date;open=[math]::Round($values[0],4);high=[math]::Round($values[1],4);low=[math]::Round($values[2],4);close=[math]::Round($values[3],4);volume=[long]$row.Volume}
    })
    $month = @($bars | Where-Object { $_.date -ge '2020-03-01' -and $_.date -le '2020-03-31' })
    if (@($bars | Where-Object { $_.date -ge '2020-04-01' }).Count -lt 18) { throw "Missing continuation data for $symbol" }
    if ($month.Count -lt 20 -or @($bars | Where-Object { $_.date -lt '2020-03-01' }).Count -lt 60) { throw "Insufficient history for $symbol" }
    $returns = @(for ($i=1; $i -lt $month.Count; $i++) { [math]::Log($month[$i].close / $month[$i-1].close) })
    $mean = ($returns | Measure-Object -Average).Average
    $variance = ($returns | ForEach-Object { [math]::Pow($_-$mean,2) } | Measure-Object -Sum).Sum / ($returns.Count-1)
    $instruments += [pscustomobject][ordered]@{symbol=$symbol;sourceUrl=$url;sha256=(Get-FileHash $file -Algorithm SHA256).Hash.ToLower();dailyVolatilityPercent=[math]::Round([math]::Sqrt($variance)*100,2);bars=$bars}
}
$snapshot = [ordered]@{
    id='nse-march-april-2020-v2';source='Public NSE historical dataset (Ram9219 GitHub archive)';sourceUrl="https://github.com/$repo/tree/$revision";revision=$revision
    downloadedAt=(Get-Item -LiteralPath $file).LastWriteTimeUtc.ToString('o');exchange='NSE';currency='INR';interval='1d';from='2020-03-01';to='2020-03-31';warmupFrom='2019-11-01';replayFrom='2020-04-01';replayTo='2020-04-30'
    notes='Historical stress-test month, not current quotes. OHLCV as published by the archive, rounded to four decimals; Adj Close is not used. Corporate-action adjustment methodology and redistribution license are not specified by the archive. Daily close replay is not intraday tick data. Volatility is sample standard deviation of within-month log close returns, not a forecast.'
    instruments=@($instruments | Sort-Object dailyVolatilityPercent -Descending)
}
$snapshot | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 (Join-Path $destination 'history.json')
$instruments | Select-Object symbol,dailyVolatilityPercent | Format-Table
