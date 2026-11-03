<#
.SYNOPSIS
  Prepara una jornada de pruebas con usuarios reales en un celular del equipo.
.DESCRIPTION
  1. Arranca el backend en otra ventana con TRUST_PROXY=1 (o reutiliza el que ya esté en marcha).
  2. Comprueba que el backend exige token y que care.apiToken coincide: el túnel lo expone a internet.
  3. Abre un túnel rápido de Cloudflare (gratis y sin cuenta) en otra ventana, o usa la URL HTTPS de -Url.
  4. Compila e instala la app debug apuntando a esa URL (con -P, sin tocar local.properties),
     concede los permisos y la abre.
  5. Envía un mensaje de prueba por el túnel para saber si responde Gemini o el banco de respaldo.
  Al terminar la jornada, cierra las ventanas "CARE backend" y "CARE túnel".
.PARAMETER Url
  URL HTTPS de un túnel propio hacia este PC (p. ej. un dominio fijo de ngrok). Sin ella se abre un túnel de Cloudflare.
.PARAMETER Serial
  Dispositivo de adb, si hay más de uno conectado.
.PARAMETER SinPruebaChat
  No envía el mensaje de prueba (cada mensaje gasta una petición de la cuota gratuita de Gemini).
.EXAMPLE
  scripts\pruebas-usuarios\iniciar-pruebas.cmd
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\pruebas-usuarios\iniciar-pruebas.ps1 -Url https://mi-dominio.ngrok-free.app
#>
param(
    [string]$Url,
    [string]$Serial,
    [switch]$SinPruebaChat
)

. (Join-Path $PSScriptRoot 'comun.ps1')

# winget instala cloudflared ahí, y el PATH nuevo no llega a las consolas que ya estaban abiertas
function Get-CloudflaredPath {
    $comando = Get-Command cloudflared -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }
    foreach ($carpeta in ${env:ProgramFiles(x86)}, $env:ProgramFiles) {
        $ruta = Join-Path $carpeta 'cloudflared\cloudflared.exe'
        if (Test-Path -LiteralPath $ruta) { return $ruta }
    }
    Stop-ConError 'Falta cloudflared. Instálalo con "winget install --id Cloudflare.cloudflared" o usa -Url con otro túnel HTTPS.'
}

function Get-PuertoLibre {
    $escucha = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
    $escucha.Start()
    $puerto = $escucha.LocalEndpoint.Port
    $escucha.Stop()
    return $puerto
}

# cloudflared publica el nombre del túnel rápido en /quicktunnel de su servidor de métricas
function Wait-UrlTunel([int]$Puerto, [int]$Segundos) {
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        try {
            $respuesta = Invoke-RestMethod -Uri "http://127.0.0.1:$Puerto/quicktunnel" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
            if ($respuesta.hostname) { return "https://$($respuesta.hostname)" }
        } catch { }
        Start-Sleep -Seconds 1
    }
    return $null
}

# --- Comprobaciones previas: fallar antes de abrir ventanas o compilar ---
Write-Paso 'Comprobaciones previas'
$token = Get-Token
$auth = @{ Authorization = "Bearer $token" }
if ((Read-LocalProperties).ContainsKey('care.apiBaseUrl.debug')) {
    Stop-ConError 'local.properties define care.apiBaseUrl.debug y tiene prioridad sobre la URL del túnel: comenta esa línea y vuelve a ejecutar.'
}
if ($Url) {
    $Url = $Url.Trim().TrimEnd('/')
    if (-not $Url.StartsWith('https://')) { Stop-ConError '-Url debe empezar por https://' }
} else {
    $cloudflared = Get-CloudflaredPath
}
$adb = Get-AdbPath
$dispositivo = Select-Dispositivo $adb $Serial
$javaHome = Get-JavaHome
Write-Ok "Dispositivo: $dispositivo"

# --- Backend ---
Write-Paso 'Backend'
$salud = Wait-Salud $BackendLocal 0
if ($salud) {
    Write-Aviso 'Ya había un backend en marcha y se reutiliza. Si no se inició con TRUST_PROXY=1, todos los participantes comparten el límite de mensajes por minuto.'
} else {
    $carpetaBackend = Join-Path $Raiz 'backend'
    if (-not (Test-Path -LiteralPath (Join-Path $carpetaBackend 'node_modules'))) {
        Stop-ConError 'Faltan las dependencias del backend: ejecuta "npm install" en backend/.'
    }
    # dotenv no pisa variables ya definidas: TRUST_PROXY=1 vale aunque .env diga otra cosa.
    # Detrás del túnel todas las peticiones llegan desde localhost; así el límite por minuto es por participante.
    Start-Ventana 'CARE backend' "`$env:TRUST_PROXY = '1'; Set-Location -LiteralPath $(ConvertTo-Literal $carpetaBackend); node server.js"
    $salud = Wait-Salud $BackendLocal 30
    if (-not $salud) { Stop-ConError 'El backend no respondió en 30 s. Revisa la ventana "CARE backend".' }
}
Write-Ok "Backend $($salud.version) en $BackendLocal (sesiones: $($salud.sessionStore))"

if (-not $salud.authRequired) {
    Stop-ConError 'El backend acepta peticiones sin token y el túnel lo dejaría abierto a cualquiera. Define API_ACCESS_TOKEN en backend/.env (igual a care.apiToken) y reinicia el backend.'
}
if ((Get-CodigoHttp GET "$BackendLocal/api/chat/session/pruebas-verificacion" $auth) -eq 401) {
    Stop-ConError 'care.apiToken (local.properties) no coincide con API_ACCESS_TOKEN (backend/.env).'
}
Write-Ok 'El backend exige token y care.apiToken coincide'

# --- Túnel HTTPS ---
Write-Paso 'Túnel HTTPS'
if (-not $Url) {
    $puerto = Get-PuertoLibre
    Start-Ventana 'CARE túnel (al cerrarla se corta el acceso)' "& $(ConvertTo-Literal $cloudflared) tunnel --no-autoupdate --metrics 127.0.0.1:$puerto --url $BackendLocal"
    $Url = Wait-UrlTunel $puerto 60
    if (-not $Url) {
        Stop-ConError 'Cloudflare no dio la URL en 60 s. Busca "trycloudflare.com" en la ventana del túnel y vuelve a ejecutar con -Url <esa URL>.'
    }
}
if (-not (Wait-Salud $Url 60)) { Stop-ConError "No se llega al backend a través de $Url." }
Write-Ok "Backend accesible en $Url"

# --- App ---
Write-Paso "Compilando e instalando la app en $dispositivo (1-3 min)"
$env:JAVA_HOME = $javaHome
$env:ANDROID_SERIAL = $dispositivo
Push-Location -LiteralPath $Raiz
try {
    & (Join-Path $Raiz 'gradlew.bat') :app:installDebug "-Pcare.apiBaseUrl.debug=$Url/" "-Dorg.gradle.java.home=$($javaHome.Replace('\', '/'))" --console=plain
    $codigoGradle = $LASTEXITCODE
} finally {
    Pop-Location
}
if ($codigoGradle -ne 0) { Stop-ConError 'Falló la compilación o la instalación (mira el mensaje de Gradle arriba).' }
Write-Ok 'App instalada'
Grant-Permisos $adb $dispositivo
Start-App $adb $dispositivo

# --- Mensaje de prueba ---
if (-not $SinPruebaChat) {
    Write-Paso 'Mensaje de prueba a través del túnel'
    $sesion = 'pruebas-verificacion-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $cuerpo = @{ message = 'Hola, buenos días'; sessionId = $sesion; companion = 'female' } | ConvertTo-Json -Compress
    $peticion = @{
        Method          = 'Post'
        Uri             = "$Url/api/chat"
        Headers         = $auth
        ContentType     = 'application/json; charset=utf-8'
        Body            = [Text.Encoding]::UTF8.GetBytes($cuerpo)
        UseBasicParsing = $true
        TimeoutSec      = 30
        ErrorAction     = 'Stop'
    }
    $inicio = Get-Date
    try {
        $respuesta = Invoke-RestMethod @peticion
        $segundos = [math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)
        $voz = if ($respuesta.data.audio_base64) { 'con voz neural' } else { 'sin voz neural (la app usaría su voz local)' }
        if ($respuesta.source -eq 'gemini') {
            Write-Ok "Respondió Gemini en $segundos s, $voz"
        } else {
            Write-Aviso "Respondió el banco de respaldo ($($respuesta.fallback_reason)) en $segundos s, $voz. Revisa GEMINI_API_KEY y la cuota en $BackendLocal/api/health."
        }
    } catch {
        Write-Aviso "El mensaje de prueba falló: $($_.Exception.Message)"
    }
    [void](Get-CodigoHttp DELETE "$BackendLocal/api/chat/session/$sesion" $auth)
}

Write-Host ''
Write-Host "Listo: la app de $dispositivo usa $Url" -ForegroundColor Green
Write-Host '  - Deja abiertas las ventanas "CARE backend" y "CARE túnel" toda la jornada; ciérralas al terminar.'
Write-Host '  - Puedes desconectar el cable: el celular funciona con Wi-Fi o datos móviles.'
Write-Host '  - Antes de cada participante, conecta el celular y ejecuta reiniciar-participante.cmd.'
if ($cloudflared) {
    Write-Host '  - La URL del túnel rápido cambia cada vez que se abre: repite este script en cada jornada.'
}
