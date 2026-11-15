@echo off
setlocal
rem Pubblica una nuova release di Kanso su GitHub incrementando la versione.
rem Uso: doppio clic (patch), oppure "rilascia.bat minor" / "rilascia.bat major".
title Kanso - Nuova release
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\publish-release.ps1" %*
set "CODE=%errorlevel%"
echo.
if "%CODE%"=="0" (echo Release completata.) else (echo Release NON completata: leggi il messaggio sopra.)
pause
exit /b %CODE%
