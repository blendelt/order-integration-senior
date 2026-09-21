param(
    [string]$Api = 'http://localhost:18080',
    [string]$Erp = 'http://localhost:18081',
    [string]$Frontend = 'http://localhost:8088'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)
$script:checks = 0
function Check($Condition, [string]$Label) {
    if (-not $Condition) { throw "FALHOU: $Label" }
    $script:checks++
    Write-Host "OK: $Label"
}
function Send([string]$Method, [string]$Url, $Body, [int]$Expected) {
    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Url)
    try {
        if ($null -ne $Body) {
            $json = if ($Body -is [string]) { $Body } else { $Body | ConvertTo-Json -Depth 8 -Compress }
            $request.Content = [System.Net.Http.StringContent]::new($json, [Text.Encoding]::UTF8, 'application/json')
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $text = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            Check ([int]$response.StatusCode -eq $Expected) "$Method $Url -> $Expected (recebido $([int]$response.StatusCode))"
            if ($response.Content.Headers.ContentType.MediaType -eq 'application/json' -and $text) {
                return ($text | ConvertFrom-Json)
            }
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
function Current($Id) {
    $all = @(Send GET "$Api/orders" $null 200)
    return ($all | Where-Object { $_.id -eq $Id } | Select-Object -First 1)
}
try {
    $initial = @(Send GET "$Api/orders" $null 200)
    if (@($initial | Where-Object { $_.status -in @('PENDING','PROCESSING') }).Count) {
        throw 'Ha pedidos pendentes/em processamento. Use ambiente isolado antes de disparar um lote.'
    }
    Send GET "$Frontend/" $null 200 | Out-Null
    Send GET "$Frontend/orders" $null 200 | Out-Null
    Send GET "$Erp/health" $null 200 | Out-Null
    $suffix = [guid]::NewGuid().ToString('N')
    Write-Host "Dados sinteticos desta execucao: DOC-$suffix e FAIL-DOC-$suffix"
    $goodBody = @{ externalId="DOC-$suffix"; customerName='Cliente smoke'; totalValue=12.50 }
    $badBody = @{ externalId="FAIL-DOC-$suffix"; customerName='Cliente smoke'; totalValue=25.50 }
    $good = Send POST "$Api/orders" $goodBody 201
    $bad = Send POST "$Api/orders" $badBody 201
    $duplicate = Send POST "$Api/orders" $goodBody 409
    Check ($duplicate.code -eq 'DUPLICATE_EXTERNAL_ID') 'Codigo de duplicidade'
    $invalid = Send POST "$Api/orders" @{ externalId=''; customerName=''; totalValue=0 } 400
    Check ($invalid.code -eq 'VALIDATION_ERROR') 'Codigo de validacao'
    Send POST "$Api/orders" '{' 400 | Out-Null
    Send POST "$Api/orders/process" $null 200 | Out-Null
    # Aguarda inclusive um eventual lote concorrente do scheduler, sem repetir o POST.
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $goodNow = Current $good.id
        $badNow = Current $bad.id
        if ($goodNow.status -eq 'SUCCESS' -and $badNow.status -eq 'ERROR') { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    Check ($goodNow.status -eq 'SUCCESS') 'Pedido valido integrado'
    Check ($badNow.status -eq 'ERROR' -and $badNow.lastError) 'Falha registrada no pedido'
    $retry = @{ order=@{ externalId="DOC-RETRY-$suffix"; customerName='Cliente smoke'; totalValue=25.50 }; version=$badNow.version; confirmedNotIntegrated=$false }
    Send POST "$Api/orders/$($bad.id)/retry" $retry 400 | Out-Null
    $retry.confirmedNotIntegrated = $true
    $retry.version = -1
    Send POST "$Api/orders/$($bad.id)/retry" $retry 409 | Out-Null
    $retry.version = $badNow.version
    Send POST "$Api/orders/9223372036854775807/retry" $retry 404 | Out-Null
    $retried = Send POST "$Api/orders/$($bad.id)/retry" $retry 200
    Check ($retried.id -eq $bad.id -and $retried.attemptCount -eq $badNow.attemptCount) 'Retry preserva pedido e tentativas'
    Send POST "$Api/orders/process" $null 200 | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $final = Current $bad.id
        if ($final.status -eq 'SUCCESS') { break }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    Check ($final.status -eq 'SUCCESS' -and $final.attemptCount -eq 2 -and $null -eq $final.lastError) 'Reenvio conclui na segunda tentativa e limpa erro'
    $retry.version = $final.version
    Send POST "$Api/orders/$($bad.id)/retry" $retry 409 | Out-Null
    $erpBody = @{ externalId="ERP-DOC-$suffix"; customerName='Cliente smoke'; totalValue=10.50 }
    $accepted = Send POST "$Erp/erp/orders" $erpBody 200
    $replayed = Send POST "$Erp/erp/orders" $erpBody 200
    Check ($accepted.status -eq 'ACCEPTED' -and $accepted.processedAt -eq $replayed.processedAt) 'ERP retorna a mesma aceitacao no reenvio'
    $erpBody.totalValue = 11.50
    Send POST "$Erp/erp/orders" $erpBody 409 | Out-Null
    Send POST "$Erp/erp/orders" $badBody 500 | Out-Null
    Send POST "$Erp/erp/orders" @{} 400 | Out-Null
    Write-Host "PASSOU: $script:checks verificacoes. Pedidos criados: $($good.id), $($bad.id)."
} finally {
    $client.Dispose()
}
