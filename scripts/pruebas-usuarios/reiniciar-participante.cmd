@echo off
rem Doble clic antes de cada participante: borra su conversacion y deja la app como recien instalada.
rem -ExecutionPolicy Bypass solo afecta a esta ejecucion; no cambia la configuracion de Windows.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0reiniciar-participante.ps1" %*
pause
