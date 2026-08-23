param([string]$BaseUrl = 'http://localhost:8080')

$ErrorActionPreference = 'Stop'
$requestId = 'smoke-' + [guid]::NewGuid().ToString()
$headers = @{ 'Idempotency-Key' = $requestId; 'X-Trace-Id' = $requestId }
$body = @{
    type = 'REPORT'
    payload = @{
        reportName = 'smoke-regional-sales'
        requestedBy = 'smoke@asyncflow.local'
        records = @(
            @{ orderId = 'SO-SMOKE-1'; region = 'East'; product = 'Keyboard'; quantity = 2; unitPrice = 199.50 },
            @{ orderId = 'SO-SMOKE-2'; region = 'West'; product = 'Mouse'; quantity = 3; unitPrice = 89.90 }
        )
    }
    maxAttempts = 3
} | ConvertTo-Json -Depth 8
$task = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/tasks" -Headers $headers -ContentType 'application/json' -Body $body

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 250
    $current = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/tasks/$($task.taskId)"
} while ($current.status -notin @('SUCCEEDED', 'DEAD', 'CANCELLED') -and (Get-Date) -lt $deadline)

if ($current.status -ne 'SUCCEEDED') { throw "Smoke task ended in $($current.status)" }
$events = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/tasks/$($task.taskId)/events"
$downloadPath = Join-Path ([System.IO.Path]::GetTempPath()) ("asyncflow-" + $task.taskId + ".csv")
try {
    Invoke-WebRequest -Method Get -Uri "$BaseUrl/api/tasks/$($task.taskId)/result" -OutFile $downloadPath
    $csv = Get-Content -LiteralPath $downloadPath -Raw
    if ($csv -notmatch 'TOTAL,2,5,668.70') { throw 'Generated report total is incorrect' }
} finally {
    Remove-Item -LiteralPath $downloadPath -Force -ErrorAction SilentlyContinue
}
[pscustomobject]@{
    TaskId = $task.taskId
    Status = $current.status
    SourceRecords = $current.result.sourceRecordCount
    TotalAmount = $current.result.totalAmount
    Sha256 = $current.result.sha256
    EventCount = $events.Count
    TraceId = $requestId
}
