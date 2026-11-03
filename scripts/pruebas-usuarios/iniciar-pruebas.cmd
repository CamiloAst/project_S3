@echo off
rem Doble clic: prepara la jornada de pruebas (backend, tunel HTTPS y app en el celular).
rem -ExecutionPolicy Bypass solo afecta a esta ejecucion; no cambia la configuracion de Windows.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-pruebas.ps1" %*
pause
