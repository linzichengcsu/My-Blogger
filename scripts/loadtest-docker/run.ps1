﻿# ============================================================
# Docker 部署环境 - 一键性能压测 (PowerShell 5+)
#
# 用途: 一条命令完成「拉起隔离压测栈(整栈容器化) → 等应用健康 →
#       用容器化 k6 压测 → 抓取干净汇总 → 可选拆栈」, 全程无需在宿主机装 k6。
#
# 目标栈: deploy/docker-compose.loadtest.yml (project: blog-loadtest, --profile app)
#         独立库/卷/端口, 不污染生产数据。
#
# 运行 (推荐用同名 .cmd 启动器, 自动绕过执行策略):
#   .\scripts\loadtest-docker\run.cmd                    # 默认: 01-main.js, 20 VUs, 60s
#   .\scripts\loadtest-docker\run.cmd -Vus 100 -Duration 2m
#   .\scripts\loadtest-docker\run.cmd -Script 04-search.js -Vus 50
#   .\scripts\loadtest-docker\run.cmd -SkipUp             # 栈已在跑, 只跑压测
#   .\scripts\loadtest-docker\run.cmd -TearDown           # 压完拆掉栈
#
# 或直接调用 ps1 (需自行处理执行策略):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\loadtest-docker\run.ps1 -Vus 50
# ============================================================

param(
    # 压哪个 k6 脚本 (位于 scripts/loadtest/ 下): 01-main.js / 02-breakdown.js / 03-penetration.js / 04-search.js
    [string]$Script = '01-main.js',
    # k6 容器内访问应用的地址 (加入 ltnet 后用服务名解析, 不能用 127.0.0.1)
    [string]$BaseUrl = 'http://blog:8080',
    # 宿主机上做健康检查/种子验证的地址 (blog 容器映射到 127.0.0.1:8080)
    [string]$HostUrl = 'http://127.0.0.1:8080',
    [int]$Vus = 20,
    [string]$Duration = '60s',
    # 栈已在运行, 跳过 up (只跑压测)
    [switch]$SkipUp,
    # up 时不加 --build (镜像已存在时加速)
    [switch]$NoBuild,
    # 压完后 docker compose down 拆掉整个栈
    [switch]$TearDown,
    # 等待应用健康的上限 (秒)。start_period=90s + 种子灌入 + ES 重建, 给足余量
    [int]$HealthTimeoutSec = 240
)

$ErrorActionPreference = 'Continue'

# ── 定位路径 ──
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
$DeployDir = Join-Path $ProjectRoot 'deploy'
$ComposeFile = Join-Path $DeployDir 'docker-compose.loadtest.yml'
$EnvFile = Join-Path $DeployDir '.env.loadtest'
$ScriptsLoadtest = Join-Path $ProjectRoot 'scripts\loadtest'
$LogDir = Join-Path $ProjectRoot 'target\loadtest-docker'
$K6Log = Join-Path $LogDir 'k6.log'

# ── 前置检查 ──
function Fail($msg, $code) {
    Write-Host "[错误] $msg" -ForegroundColor Red
    exit $code
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Fail '找不到 docker 命令。请先启动 Docker Desktop 并确保 docker 在 PATH 中。' 2
}
# CLI 存在不代表 daemon 起了; 提前探测, 给出明确提示而非走到 compose up 才报原生错误
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Fail 'Docker daemon 未运行。请先启动 Docker Desktop，等其状态变为 running 后重试。' 2
}
if (-not (Test-Path $ComposeFile)) { Fail "找不到 Compose 文件: $ComposeFile" 2 }
if (-not (Test-Path $EnvFile))     { Fail "找不到环境变量文件: $EnvFile (可从 deploy/.env.example 复制)" 2 }
if (-not (Test-Path (Join-Path $ScriptsLoadtest $Script))) {
    Fail "找不到 k6 脚本: scripts\loadtest\$Script" 2
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# compose 公共参数 (数组展开, 避免 PS5 引号坑)
$composeBase = @('--env-file', $EnvFile, '-f', $ComposeFile)

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' Docker 部署环境 - 一键压测' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host " 工程根 : $ProjectRoot"
Write-Host " 目标栈 : blog-loadtest (隔离, 整栈容器化)"
Write-Host " 脚本   : $Script"
Write-Host " 压力   : VUs=$Vus  Duration=$Duration"
Write-Host " k6 打  : $BaseUrl  (容器内服务名)"
Write-Host " 健康检 : $HostUrl/actuator/health"
Write-Host " 模式   : $(if ($SkipUp) {'仅压测 (栈已在跑)'} else {'起栈 + 压测'})$(if ($TearDown) {' + 压完拆栈'})"
Write-Host '------------------------------------------------------------' -ForegroundColor Cyan
Write-Host ''

# ── 1. 起栈 (整栈容器化) ──
if (-not $SkipUp) {
    Write-Host '[1/4] 拉起压测栈 (docker compose --profile app up -d) ...' -ForegroundColor Cyan
    $upArgs = $composeBase + @('--profile', 'app', 'up', '-d')
    if (-not $NoBuild) { $upArgs += '--build' }
    & docker compose @upArgs
    if ($LASTEXITCODE -ne 0) {
        Fail "docker compose up 失败 (退出码 $LASTEXITCODE), 请检查上方输出。" 3
    }
    Write-Host '      栈已启动, 等待应用健康 ...' -ForegroundColor DarkGray
} else {
    Write-Host '[1/4] 跳过起栈 (-SkipUp), 假定栈已在运行。' -ForegroundColor DarkGray
}

# ── 2. 等应用健康 ──
Write-Host "[2/4] 等待 blog 健康 (上限 ${HealthTimeoutSec}s) ..." -ForegroundColor Cyan
$healthy = $false
$deadline = (Get-Date).AddSeconds($HealthTimeoutSec)
$attempts = 0
while ((Get-Date) -lt $deadline) {
    $attempts++
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri "$HostUrl/actuator/health" -TimeoutSec 5
        if ($r.StatusCode -eq 200) {
            try {
                # PS5.1 对 application/vnd.spring-boot.actuator.v3+json 这类非标准 content-type
                # 会把 .Content 当成 byte[] 而非 string; 必须先 UTF8 解码, 否则 ConvertFrom-Json
                # 得到的是数字数组, 取不到 .status, 导致永远等不到 UP (即使应用实际已健康)
                $raw = $r.Content
                if ($raw -is [byte[]]) { $raw = [System.Text.Encoding]::UTF8.GetString($raw) }
                $j = $raw | ConvertFrom-Json
                if ($j.status -eq 'UP') { $healthy = $true; break }
                if ($attempts % 6 -eq 0) {
                    Write-Host ("      actuator/health 返回 200 但 status={0}, 继续等待依赖就绪 ..." -f $j.status) -ForegroundColor DarkGray
                }
            } catch { }
        }
    } catch { }
    if ($attempts % 6 -eq 0) {
        Write-Host ("      仍未健康 (已等 {0}s) ..." -f [int]((Get-Date) - ($deadline.AddSeconds(-$HealthTimeoutSec))).TotalSeconds) -ForegroundColor DarkGray
    }
    Start-Sleep -Seconds 5
}
if (-not $healthy) {
    Write-Host "[错误] blog 在 ${HealthTimeoutSec}s 内未变为健康。" -ForegroundColor Red
    Write-Host '  排查命令: docker compose --env-file deploy/.env.loadtest -f deploy/docker-compose.loadtest.yml logs blog' -ForegroundColor Yellow
    if ($TearDown) {
        Write-Host '  因 -TearDown, 正在拆栈 ...' -ForegroundColor Yellow
        & docker compose @composeBase down
    }
    exit 4
}
Write-Host '      blog 已健康 (UP)。' -ForegroundColor Green

# 种子数据验证 (loadtest profile 启动时自动灌 1000 篇, ID 1-1000)
try {
    $seed = Invoke-WebRequest -UseBasicParsing -Uri "$HostUrl/api/articles/1" -TimeoutSec 5
    if ($seed.StatusCode -eq 200) {
        Write-Host '      种子数据验证通过 (GET /api/articles/1 = 200)。' -ForegroundColor Green
    } else {
        Write-Host "      种子验证: /api/articles/1 返回 $($seed.StatusCode), 继续压测 (可能 ID 偏移)。" -ForegroundColor Yellow
    }
} catch {
    Write-Host '      种子验证请求失败, 继续压测 (不阻断)。' -ForegroundColor Yellow
}

# ── 3. 定位 ltnet 网络 (k6 容器要加入才能用服务名访问 blog) ──
Write-Host '[3/4] 定位压测网络 ltnet ...' -ForegroundColor Cyan
$net = docker network ls --format '{{.Name}}' 2>$null | Where-Object { $_ -like '*ltnet*' } | Select-Object -First 1
if (-not $net) {
    Fail '找不到 *ltnet* 网络。压测栈可能没起成功, 请先 docker compose up。' 5
}
Write-Host "      网络: $net" -ForegroundColor DarkGray

# 确保 k6 镜像存在 (首次自动拉取)
$hasImg = docker image inspect grafana/k6 2>$null
if (-not $hasImg) {
    Write-Host '      首次运行, 拉取 grafana/k6 镜像 ...' -ForegroundColor DarkGray
    & docker pull grafana/k6
    if ($LASTEXITCODE -ne 0) { Fail '拉取 grafana/k6 失败, 请检查网络。' 6 }
}

# ── 4. 跑 k6 (容器化, 加入 ltnet) ──
Write-Host "[4/4] 启动 k6 压测 (VUs=$Vus, Duration=$Duration) ..." -ForegroundColor Cyan
# Windows 路径转成正斜杠, docker 绑定挂载更稳
$hostScripts = $ScriptsLoadtest -replace '\\', '/'
$k6Args = @(
    'run', '--rm',
    '--network', $net,
    '-e', "BASE_URL=$BaseUrl",
    '-e', "VUS=$Vus",
    '-e', "DURATION=$Duration",
    '-v', "$hostScripts`:/scripts:ro",
    'grafana/k6', 'run', "/scripts/$Script"
)
# k6 容器输出 UTF-8 (含中文摘要); PS5 默认用系统代码页 (中文 Windows = 936/GBK)
# 解码 docker 的字节流, 会把 UTF-8 中文读成乱码 (如 "鍘嬫祴鎽樿"), 进而让日志里的
# "压测摘要" 匹配失败。临时切到 UTF-8 解码, 跑完恢复, 避免污染会话后续编码。
$prevEnc = [Console]::OutputEncoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
# 2>&1 会把 docker 的 stderr 行包成 ErrorRecord, 直接 Tee 会带出
# "docker.exe : ...  CategoryInfo : NativeCommandError" 红噪音; 拍平成纯字符串。
# 同时收集每行, 跑完显式以 UTF-8 BOM 写盘, 保证 Get-Content / -match 能正确识别中文。
$utf8Bom = New-Object System.Text.UTF8Encoding($true)
$k6Lines = New-Object System.Collections.Generic.List[string]
try {
    & docker @k6Args 2>&1 | ForEach-Object {
        $line = "$_"
        [void]$k6Lines.Add($line)
        $line
    }
} finally {
    [Console]::OutputEncoding = $prevEnc
}
$k6Exit = $LASTEXITCODE
# WriteAllLines 会覆盖/创建文件, 无需先删 (避免触发某些终端的 safe-rm 钩子)
[System.IO.File]::WriteAllLines($K6Log, $k6Lines, $utf8Bom)

# ── 汇总 ──
Write-Host ''
Write-Host '==================== k6 压测汇总 ====================' -ForegroundColor Cyan
if (Test-Path $K6Log) {
    $lines = Get-Content -LiteralPath $K6Log
    $summaryIdx = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '压测摘要') { $summaryIdx = $i; break }
    }
    if ($summaryIdx -ge 0) {
        $lines | Select-Object -Skip $summaryIdx -First 10 | ForEach-Object { Write-Host "  $_" }
    } else {
        Write-Host '  (未在日志中找到"压测摘要"块, k6 可能未正常结束, 请看上方输出)' -ForegroundColor Yellow
    }
}
$verdict = if ($k6Exit -eq 0) { '通过 ✓ (全部 thresholds 通过)' } else { '未通过 ✗ (有 threshold 失败或运行异常)' }
Write-Host "  k6 退出码: $k6Exit  →  $verdict" -ForegroundColor $(if ($k6Exit -eq 0) {'Green'} else {'Red'})
Write-Host "  完整日志: $K6Log" -ForegroundColor DarkGray
Write-Host '=====================================================' -ForegroundColor Cyan

# 应用侧观测提示 (loadtest profile 已放开 actuator, 无需鉴权)
Write-Host '  应用侧缓存指标 (压后核对回源):' -ForegroundColor DarkGray
Write-Host "    $HostUrl/actuator/prometheus  |  Select-String 'cache_'" -ForegroundColor DarkGray
Write-Host "    $HostUrl/actuator/metrics/cache.gets?tag=result:miss" -ForegroundColor DarkGray
Write-Host '=====================================================' -ForegroundColor Cyan

# ── 可选拆栈 ──
if ($TearDown) {
    Write-Host ''
    Write-Host '[-TearDown] 拆除压测栈 ...' -ForegroundColor Cyan
    & docker compose @composeBase down
}

exit $(if ($k6Exit -eq 0) { 0 } else { 1 })