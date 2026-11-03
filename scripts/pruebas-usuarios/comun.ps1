# Funciones compartidas por los scripts de pruebas con usuarios reales.
# Se cargan con ". (Join-Path $PSScriptRoot 'comun.ps1')". Compatibles con Windows PowerShell 5.1:
# las órdenes nativas (adb, gradlew) se comprueban con $LASTEXITCODE y nunca se redirigen con "2>&1",
# porque en 5.1 cada línea de stderr se convierte en un error.

$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Paquete = 'uq.seminario.projects3'
$BackendLocal = 'http://localhost:3000'

# Las URL de los túneles (trycloudflare.com, ngrok) exigen TLS 1.2
[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

function Write-Paso([string]$Texto) { Write-Host "`n==> $Texto" -ForegroundColor Cyan }
function Write-Ok([string]$Texto) { Write-Host "    OK  $Texto" -ForegroundColor Green }
function Write-Aviso([string]$Texto) { Write-Host "    !!  $Texto" -ForegroundColor Yellow }
function Stop-ConError([string]$Texto) {
    Write-Host "`nERROR: $Texto" -ForegroundColor Red
    exit 1
}

# local.properties con el formato de java.util.Properties (clave=valor; "\" escapa, p. ej. sdk.dir=E\:\\Users).
function Read-LocalProperties {
    $propiedades = @{}
    $ruta = Join-Path $Raiz 'local.properties'
    if (-not (Test-Path -LiteralPath $ruta)) { return $propiedades }
    foreach ($linea in Get-Content -LiteralPath $ruta -Encoding UTF8) {
        $texto = $linea.Trim()
        if ($texto -eq '' -or $texto.StartsWith('#') -or $texto.StartsWith('!')) { continue }
        $separador = $texto.IndexOfAny([char[]]'=:')
        if ($separador -lt 1) { continue }
        $propiedades[$texto.Substring(0, $separador).Trim()] = $texto.Substring($separador + 1).Trim() -replace '\\(.)', '$1'
    }
    return $propiedades
}

# Token que la app envía como Bearer; nunca se imprime.
function Get-Token {
    $token = (Read-LocalProperties)['care.apiToken']
    if (-not $token) {
        Stop-ConError 'Falta care.apiToken en local.properties (debe ser igual a API_ACCESS_TOKEN de backend/.env).'
    }
    return $token
}

function Get-AdbPath {
    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Read-LocalProperties)['sdk.dir'], (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))
    foreach ($sdk in $sdks) {
        if (-not $sdk) { continue }
        $adb = Join-Path $sdk 'platform-tools\adb.exe'
        if (Test-Path -LiteralPath $adb) { return $adb }
    }
    $comando = Get-Command adb -ErrorAction SilentlyContinue
    if ($comando) { return $comando.Source }
    Stop-ConError 'No se encontró adb. Instala "Android SDK Platform-Tools" desde el SDK Manager de Android Studio.'
}

# Serial del dispositivo: el indicado con -Serial, el único celular conectado o, si no hay ninguno, el único emulador.
function Select-Dispositivo([string]$Adb, [string]$Serial) {
    $listos = @()
    $pendientes = @()
    foreach ($linea in (& $Adb devices)) {
        if ($linea -match '^(\S+)\t(.+)$') {
            if ($Matches[2] -eq 'device') { $listos += $Matches[1] } else { $pendientes += "$($Matches[1]) ($($Matches[2]))" }
        }
    }
    if ($Serial) {
        if ($listos -contains $Serial) { return $Serial }
        Stop-ConError "El dispositivo '$Serial' no está conectado. Listos: $($listos -join ', ')"
    }
    $celulares = @($listos | Where-Object { $_ -notlike 'emulator-*' })
    $emuladores = @($listos | Where-Object { $_ -like 'emulator-*' })
    if ($celulares.Count -eq 1) { return $celulares[0] }
    if ($celulares.Count -gt 1) { Stop-ConError "Hay varios celulares conectados ($($celulares -join ', ')): elige uno con -Serial." }
    if ($pendientes.Count -gt 0) {
        Stop-ConError "El celular no está listo: $($pendientes -join ', '). Desbloquéalo y acepta «¿Permitir depuración por USB?»."
    }
    if ($emuladores.Count -eq 1) {
        Write-Aviso "No hay ningún celular conectado: se usa el emulador $($emuladores[0])."
        return $emuladores[0]
    }
    if ($emuladores.Count -gt 1) { Stop-ConError "Hay varios emuladores abiertos ($($emuladores -join ', ')): elige uno con -Serial." }
    Stop-ConError 'No hay ningún celular conectado. Conéctalo por USB con la «Depuración por USB» activada (Ajustes > Opciones de desarrollador).'
}

# Concede de antemano los permisos que la app pide al abrirse, para que el participante no vea esos diálogos.
function Grant-Permisos([string]$Adb, [string]$Serial) {
    & $Adb -s $Serial shell pm grant $Paquete android.permission.RECORD_AUDIO
    if ($LASTEXITCODE -eq 0) { Write-Ok 'Micrófono concedido' } else { Write-Aviso 'No se pudo conceder el micrófono: acéptalo en el celular la primera vez.' }

    $sdk = 0
    [void][int]::TryParse(((& $Adb -s $Serial shell getprop ro.build.version.sdk) -join '').Trim(), [ref]$sdk)
    if ($sdk -ge 37) {
        # El build debug lo pide al abrirse en Android 17+ (MainActivity). Con el túnel HTTPS no hace falta,
        # pero concederlo evita ese diálogo delante del participante.
        & $Adb -s $Serial shell pm grant $Paquete android.permission.ACCESS_LOCAL_NETWORK
        if ($LASTEXITCODE -eq 0) { Write-Ok 'Permiso de red local concedido' } else { Write-Aviso 'No se pudo conceder «Dispositivos cercanos»: acéptalo en el celular.' }
    }
}

function Start-App([string]$Adb, [string]$Serial) {
    & $Adb -s $Serial shell am start -n "$Paquete/.MainActivity" | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Ok 'App abierta en el celular' } else { Write-Aviso 'No se pudo abrir la app: ábrela a mano.' }
}

# Código HTTP de una petición; 0 si no hubo respuesta. Invoke-WebRequest lanza excepción en 4xx/5xx.
function Get-CodigoHttp([string]$Metodo, [string]$Url, [hashtable]$Cabeceras = @{}) {
    try {
        return [int](Invoke-WebRequest -Method $Metodo -Uri $Url -Headers $Cabeceras -UseBasicParsing -TimeoutSec 15 -ErrorAction Stop).StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return 0
    }
}

# Espera a que <base>/api/health responda y devuelve su JSON, o $null si se agota el plazo (0 = un solo intento).
function Wait-Salud([string]$Base, [int]$Segundos) {
    $limite = (Get-Date).AddSeconds($Segundos)
    do {
        try {
            return Invoke-RestMethod -Uri "$Base/api/health" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        } catch {
            if ((Get-Date) -lt $limite) { Start-Sleep -Seconds 1 }
        }
    } while ((Get-Date) -lt $limite)
    return $null
}

# JDK para Gradle: el "jbr" de Android Studio (JDK 25, como pide gradle-daemon-jvm.properties) y, si no está, JAVA_HOME.
function Get-JavaHome {
    $candidatos = @()
    foreach ($clave in 'HKLM:\SOFTWARE\Android Studio', 'HKCU:\SOFTWARE\Android Studio') {
        $instalacion = (Get-ItemProperty -Path $clave -ErrorAction SilentlyContinue).Path
        if ($instalacion) { $candidatos += (Join-Path $instalacion 'jbr') }
    }
    $candidatos += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
    $candidatos += $env:JAVA_HOME
    foreach ($candidato in $candidatos) {
        if ($candidato -and (Test-Path -LiteralPath (Join-Path $candidato 'bin\java.exe'))) { return $candidato }
    }
    Stop-ConError 'No se encontró el JDK de Android Studio. Define JAVA_HOME con su carpeta "jbr".'
}

# Abre otra ventana de PowerShell que queda abierta, para ver sus registros y cerrarla al terminar.
# -EncodedCommand evita los problemas de comillas de Start-Process en 5.1.
function Start-Ventana([string]$Titulo, [string]$Comando) {
    $script = "`$Host.UI.RawUI.WindowTitle = '$($Titulo.Replace("'", "''"))'; $Comando"
    $codificado = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script))
    Start-Process powershell -ArgumentList "-NoExit -NoProfile -ExecutionPolicy Bypass -EncodedCommand $codificado" | Out-Null
}

function ConvertTo-Literal([string]$Texto) { return "'" + $Texto.Replace("'", "''") + "'" }
