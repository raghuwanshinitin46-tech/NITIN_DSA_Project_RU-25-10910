$ErrorActionPreference = 'Stop'
$webRoot = Join-Path $PSScriptRoot 'web'
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add('http://localhost:8080/')
$listener.Start()
Write-Host 'Boardroom is live at http://localhost:8080'
Write-Host 'Press Ctrl+C to stop the local server.'

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $path = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath)

        if ($context.Request.HttpMethod -eq 'GET' -and $path -eq '/api/health') {
            $body = [Text.Encoding]::UTF8.GetBytes('{"status":"browser-mode"}')
            $context.Response.StatusCode = 200
            $context.Response.ContentType = 'application/json; charset=utf-8'
        } elseif ($context.Request.HttpMethod -eq 'GET' -and $path -in @('/', '/index.html', '/styles.css', '/app.js')) {
            $name = if ($path -eq '/') { 'index.html' } else { $path.TrimStart('/') }
            $file = Join-Path $webRoot $name
            $body = [IO.File]::ReadAllBytes($file)
            $context.Response.StatusCode = 200
            $context.Response.ContentType = switch ([IO.Path]::GetExtension($file)) {
                '.html' { 'text/html; charset=utf-8' }
                '.css' { 'text/css; charset=utf-8' }
                '.js' { 'text/javascript; charset=utf-8' }
            }
        } else {
            $body = [Text.Encoding]::UTF8.GetBytes('Not found')
            $context.Response.StatusCode = 404
            $context.Response.ContentType = 'text/plain; charset=utf-8'
        }

        $context.Response.Headers['Cache-Control'] = 'no-store'
        $context.Response.ContentLength64 = $body.Length
        $context.Response.OutputStream.Write($body, 0, $body.Length)
        $context.Response.Close()
    }
} finally {
    $listener.Stop()
    $listener.Close()
}
