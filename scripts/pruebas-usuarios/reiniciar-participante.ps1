<#
.SYNOPSIS
  Deja la app como recién instalada antes de cada participante.
.DESCRIPTION
  El sessionId es uno por instalación: sin este paso, el siguiente participante heredaría durante 60 min
  el historial y las notas de memoria del anterior (contamina los resultados y expone su conversación).
  1. Lee el sessionId del celular y borra esa sesión del backend (historial y notas de memoria).
  2. Borra los datos de la app (nuevo sessionId; vuelve a pedir que se elija acompañante).
  3. Vacía el registro del sistema (logcat): el build debug escribe ahí el texto de la conversación.
  4. Vuelve a conceder los permisos y abre la app.
.PARAMETER Serial
  Dispositivo de adb, si hay más de uno conectado.
.PARAMETER Backend
  Backend donde borrar la sesión (por defecto el de este PC).
.EXAMPLE
  scripts\pruebas-usuarios\reiniciar-participante.cmd
#>
param(
    [string]$Serial,
    [string]$Backend = 'http://localhost:3000'
)

. (Join-Path $PSScriptRoot 'comun.ps1')

$token = Get-Token
$adb = Get-AdbPath
$dispositivo = Select-Dispositivo $adb $Serial

Write-Paso 'Conversación del participante anterior en el backend'
# run-as solo funciona con builds debug (depurables), que son los que instala iniciar-pruebas
$preferencias = (& $adb -s $dispositivo shell run-as $Paquete cat shared_prefs/care_session.xml 2>$null) -join "`n"
if ($preferencias -match 'name="session_id">([^<]+)<') {
    $codigo = Get-CodigoHttp DELETE "$Backend/api/chat/session/$($Matches[1])" @{ Authorization = "Bearer $token" }
    switch ($codigo) {
        200 { Write-Ok 'Historial y notas de memoria borrados del backend' }
        404 { Write-Ok 'El backend no tenía historial de esta sesión' }
        401 { Write-Aviso 'El backend rechazó el token: care.apiToken no coincide con API_ACCESS_TOKEN.' }
        default { Write-Aviso "No se pudo borrar en el backend (código $codigo): la sesión caducará sola tras 60 min sin uso." }
    }
} else {
    Write-Ok 'La app no tenía sesión guardada'
}

Write-Paso 'Datos de la app'
& $adb -s $dispositivo shell pm clear $Paquete | Out-Null
if ($LASTEXITCODE -ne 0) { Stop-ConError "No se pudieron borrar los datos de la app. ¿Está instalada en $dispositivo?" }
Write-Ok 'Datos borrados: sesión nueva y elección de acompañante pendiente'
& $adb -s $dispositivo logcat -c
if ($LASTEXITCODE -eq 0) { Write-Ok 'Registro del sistema (logcat) vaciado' } else { Write-Aviso 'No se pudo vaciar logcat.' }

Write-Paso 'Permisos y arranque'
Grant-Permisos $adb $dispositivo
Start-App $adb $dispositivo

Write-Host ''
Write-Host 'Listo para el siguiente participante.' -ForegroundColor Green
