@echo off
setlocal
set "FRONTEND_DIR=%~dp0..\frontend"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$listener = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue; if (-not $listener) { Start-Process -FilePath 'npm.cmd' -ArgumentList 'run','dev' -WorkingDirectory '%FRONTEND_DIR%' -WindowStyle Minimized }"
echo Vite dev server is ready or starting on http://localhost:5173
exit /b 0
