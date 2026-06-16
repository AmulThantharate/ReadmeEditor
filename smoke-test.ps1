# PowerShell Smoke Test Script for README Editor
# -----------------------------------------------------------------------------
$url = "http://localhost:8080/api/health"
$maxAttempts = 5
$delaySec = 3

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "Running Smoke Tests for README Editor" -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "Target URL: $url" -ForegroundColor Gray

for ($i = 1; $i -le $maxAttempts; $i++) {
    Write-Host "Attempt $i of $maxAttempts - Checking health check endpoint..." -ForegroundColor Gray
    try {
        $response = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 5 -UseBasicParsing
        if ($response -and $response.status -eq "UP") {
            Write-Host "Health Check passed!" -ForegroundColor Green
            Write-Host "Server status: $($response.status)" -ForegroundColor Green
            Write-Host "Redis connection: $($response.redis)" -ForegroundColor Green
            Write-Host "JVM Free Memory: $($response.jvmFreeMemoryBytes) bytes" -ForegroundColor Gray
            
            # Smoke test static assets
            Write-Host "Checking static assets..." -ForegroundColor Gray
            $indexUrl = "http://localhost:8080/index.html"
            $indexRes = Invoke-WebRequest -Uri $indexUrl -Method Get -TimeoutSec 5 -UseBasicParsing
            if ($indexRes.StatusCode -eq 200) {
                Write-Host "Static assets verified successfully!" -ForegroundColor Green
                Write-Host "=================================================" -ForegroundColor Green
                Write-Host "SMOKE TESTS PASSED SUCCESSFULLY!" -ForegroundColor Green
                Write-Host "=================================================" -ForegroundColor Green
                Exit 0
            } else {
                Write-Host "Static assets returned status code: $($indexRes.StatusCode)" -ForegroundColor Red
            }
        } else {
            Write-Host "Health Check returned unexpected status: $($response.status)" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "Attempt $i failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    
    if ($i -lt $maxAttempts) {
        Write-Host "Waiting $delaySec seconds before retrying..." -ForegroundColor Gray
        Start-Sleep -Seconds $delaySec
    }
}

Write-Host "=================================================" -ForegroundColor Red
Write-Host "SMOKE TESTS FAILED!" -ForegroundColor Red
Write-Host "=================================================" -ForegroundColor Red
Exit 1
