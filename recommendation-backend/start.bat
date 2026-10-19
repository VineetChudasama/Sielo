@echo off
title Sielo Recommendation Backend
cd /d "%~dp0"
echo Starting Sielo Recommendation Backend...
call venv\Scripts\activate.bat
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
pause
