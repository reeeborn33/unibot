@echo off
chcp 65001 >nul
REM ===== 运行前请填写以下配置 =====
set WORLDONE_DB_URL=jdbc:postgresql://localhost:5432/worldone
set WORLDONE_DB_USER=postgres
set WORLDONE_DB_PASS=
set LLM_API_KEY=
set LLM_BASE_URL=
set LLM_MODEL=

if "%WORLDONE_DB_PASS%"=="" (
    echo ERROR: 请在脚本中填写 WORLDONE_DB_PASS
    pause
    exit /b 1
)
if "%LLM_API_KEY%"=="" (
    echo ERROR: 请在脚本中填写 LLM_API_KEY
    pause
    exit /b 1
)
if "%LLM_BASE_URL%"=="" (
    echo ERROR: 请在脚本中填写 LLM_BASE_URL（如 https://api.deepseek.com/v1）
    pause
    exit /b 1
)
if "%LLM_MODEL%"=="" (
    echo ERROR: 请在脚本中填写 LLM_MODEL（如 deepseek-chat）
    pause
    exit /b 1
)

echo 正在启动 World-One...
start "World-One" cmd /c "java -jar world-one-1.0-SNAPSHOT.jar --server.port=8090 > world-one.log 2>&1"

echo 等待 5 秒...
timeout /t 5 /nobreak >nul

curl -s -o nul -w "%%{http_code}" http://localhost:8090/api/registry >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: World-One 启动失败，查看 world-one.log
    type world-one.log
    pause
    exit /b 1
)

echo 正在启动 Memory-One...
start "Memory-One" cmd /c "java -jar memory-one-1.0-SNAPSHOT.jar --server.port=8091 > memory-one.log 2>&1"

echo 等待 10 秒以确保服务启动...
timeout /t 10 /nobreak >nul

curl -s -o nul -w "%%{http_code}" http://localhost:8091/api/widgets >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Memory-One 启动失败，查看 memory-one.log
    type memory-one.log
    pause
    exit /b 1
)

echo 正在注册 Memory-One...
curl -s -X POST "http://localhost:8090/api/registry/install" -H "Content-Type: application/json" -d "{\"app_id\":\"memory-one\", \"base_url\":\"http://localhost:8091\"}" > regist-result.txt 2>&1
findstr /i "error" regist-result.txt >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo ERROR: Memory-One 注册失败
    type regist-result.txt
    pause
    exit /b 1
)

echo.
echo 启动与注册完成！请访问 http://localhost:8090
pause