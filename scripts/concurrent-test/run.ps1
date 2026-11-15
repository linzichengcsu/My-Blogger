# ============================================================
# 并发/性能测试 - 单命令速通脚本 (PowerShell 5+)
#
# 用途: 一条命令跑完并发正确性测试或既有性能测试, 自动探测 Docker,
#       压力参数可通过命令行调整 (无需改源码), 结束后给出干净汇总。
#
# 运行 (推荐用同名 .cmd 启动器, 自动绕过执行策略):
#   .\scripts\concurrent-test\run.cmd                  # 默认: H2 并发用例 (无需 Docker, 快)
#   .\scripts\concurrent-test\run.cmd -All             # H2 + MySQL 并发 (无 Docker 时 MySQL 自动跳过)
#   .\scripts\concurrent-test\run.cmd -MysqlOnly       # 仅 MySQL 并发 (需 Docker)
#   .\scripts\concurrent-test\run.cmd -Perf            # 跑既有性能测试 (实体 mass-ops + 批量插入)
#   .\scripts\concurrent-test\run.cmd -TestPattern X   # 直接指定 surefire -Dtest 模式 (最高优先级)
#
# 调整压力 (不改源码, 透传为 -Dstress.* 给 Maven):
#   .\scripts\concurrent-test\run.cmd -Scale 2         # 所有压力参数 x2 (并发线程/循环次数/批量)
#   .\scripts\concurrent-test\run.cmd -Threads 500     # 所有并发场景线程数强制 = 500
#   .\scripts\concurrent-test\run.cmd -Profile high    # 预设档: smoke(0.1x)/default(1x)/high(2x)/extreme(5x)
#   可叠加: run.cmd -Profile high -Scale 1.5           # = 2 x 1.5 = 3x; -Threads 再覆盖并发线程数
#
# 或直接调用 ps1 (需自行处理执行策略):
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\concurrent-test\run.ps1 -All -Scale 2
# ============================================================

param(
    # 测试范围 (互斥, 优先级: -TestPattern > -Perf > -MysqlOnly/-All > 默认)
    [switch]$All,          # H2 + MySQL 并发; MySQL 在 Docker 不可用时自动跳过
    [switch]$MysqlOnly,    # 仅 MySQL 并发; Docker 不可用时直接友好退出
    [switch]$Perf,         # 跑既有性能测试 (实体 set/get 循环 + 仓库批量插入), 无需 Docker
    [string]$TestPattern,  # 直接指定 surefire -Dtest 模式 (覆盖以上所有), 高级用法
    # 压力调整 (透传为 -Dstress.*)
    [int]$Threads = 0,     # >0 时全局覆盖所有并发场景的线程数 (绝对值, 不再乘 scale)
    [double]$Scale = 1.0,  # 所有压力参数的倍率 (与 -Profile 叠加相乘)
    [string]$Profile       # 预设档位: smoke / default / high / extreme
)

$ErrorActionPreference = 'Continue'

# ── 定位工程根目录与 Maven Wrapper ──
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptDir '..\..')
$Mvnw = Join-Path $ProjectRoot 'mvnw.cmd'
$SurefireDir = Join-Path $ProjectRoot 'target\surefire-reports'
$MvnLog = Join-Path $ProjectRoot 'target\concurrent-test-mvn.log'

if (-not (Test-Path $Mvnw)) {
    Write-Host "[错误] 找不到 Maven Wrapper: $Mvnw" -ForegroundColor Red
    exit 2
}

# ── Docker 探测 (仅在需要 MySQL 时) ──
function Test-DockerRunning {
    # 不输出任何探测噪声; 只看退出码 (*> 为 PS 原生全流重定向)
    & docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

$dockerUp = Test-DockerRunning

# ── 压力参数解析: Profile 设基础倍率, -Scale 叠加, -Threads 强制覆盖并发线程数 ──
$profileScale = 1.0
if ($Profile) {
    switch ($Profile.ToLowerInvariant()) {
        'smoke'   { $profileScale = 0.1 }
        'default' { $profileScale = 1.0 }
        'high'    { $profileScale = 2.0 }
        'extreme' { $profileScale = 5.0 }
        default {
            Write-Host "[错误] 未知 -Profile: '$Profile' (可选: smoke / default / high / extreme)" -ForegroundColor Red
            exit 2
        }
    }
}
$finalScale = $profileScale * $Scale
$stressProps = @()
if ([math]::Abs($finalScale - 1.0) -gt 0.0001) { $stressProps += "-Dstress.scale=$finalScale" }
if ($Threads -gt 0) { $stressProps += "-Dstress.threads=$Threads" }

# ── 根据模式决定要跑哪些测试 (优先级: -TestPattern > -Perf > -MysqlOnly/-All > 默认) ──
$testFilter = $null
$needDocker = $false

if ($TestPattern) {
    $testFilter = $TestPattern
} elseif ($Perf) {
    # 既有性能测试: 实体嵌套类 PerformanceTests + 两个仓库的 testBatchInsert 方法
    $testFilter = '*PerformanceTests,ArticleRepositoryTest#testBatchInsert,CommentRepositoryTest#testBatchInsert'
} elseif ($MysqlOnly) {
    $testFilter = 'MysqlConcurrentCorrectnessTest'
    $needDocker = $true
} elseif ($All) {
    $testFilter = '*ConcurrentCorrectnessTest'
    $needDocker = $true   # 包含 MySQL 用例
} else {
    # 默认: 仅 H2 引擎无关不变量用例, 零外部依赖
    $testFilter = 'ConcurrentCorrectnessTest'
}

if ($needDocker -and -not $dockerUp) {
    if ($MysqlOnly) {
        Write-Host "[跳过] -MysqlOnly 需要 Docker, 但未检测到运行中的 Docker 守护进程。" -ForegroundColor Yellow
        Write-Host "       请先启动 Docker Desktop, 再重跑; 或改用默认模式 (仅 H2 用例)。" -ForegroundColor Yellow
        exit 0
    }
    Write-Host "[提示] 未检测到 Docker, MySQL 用例将被 Testcontainers 自动跳过, 仅执行 H2 用例。" -ForegroundColor Yellow
    Write-Host "       启动 Docker Desktop 后重跑 -All 即可覆盖完整生产语义。" -ForegroundColor Yellow
}

# ── 清空 surefire 报告目录, 保证汇总只反映本次运行 (不混入历史报告) ──
if (Test-Path $SurefireDir) {
    Get-ChildItem -Path $SurefireDir -ErrorAction SilentlyContinue |
        Remove-Item -Force -Recurse -ErrorAction SilentlyContinue
}

# ── 组装 Maven 参数 (数组 + 展开, 避免逗号/引号在 PS5 下的解析坑) ──
$mvnArgs = @(
    '-B',
    '-ntp',
    "-Dtest=$testFilter",
    '-DfailIfNoTests=false',
    '-Dsurefire.failIfNoSpecifiedTests=false'
)
$mvnArgs += $stressProps   # 透传 -Dstress.* 压力参数 (数组为空时无影响)
$mvnArgs += 'test'

Write-Host ''
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " 并发正确性测试 速通" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " 工程根 : $ProjectRoot"
$modeLabel = if ($TestPattern) {"自定义 ($TestPattern)"} elseif ($Perf) {'性能测试 (Perf)'} elseif ($MysqlOnly) {'MySQL only'} elseif ($All) {'H2 + MySQL (All)'} else {'H2 only (默认)'}
Write-Host " 模式   : $modeLabel"
Write-Host " 测试   : $testFilter"
$stressLabel = "scale=$finalScale"
if ($Threads -gt 0) { $stressLabel += "  threads=$Threads (全局覆盖)" }
if ($Profile) { $stressLabel += "  profile=$Profile" }
Write-Host " 压力   : $stressLabel"
Write-Host " Docker : $(if ($dockerUp) {'可用'} else {'不可用'})"
Write-Host " 命令   : .\mvnw.cmd $($mvnArgs -join ' ')"
Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
Write-Host ''

# ── 执行 Maven (输出同时打到屏幕与日志, 结束后从日志提取性能指标) ──
Push-Location $ProjectRoot
try {
    & $Mvnw @mvnArgs | Tee-Object -FilePath $MvnLog
    $mvnExit = $LASTEXITCODE
} finally {
    Pop-Location
}

# ── 从 surefire 报告解析每个测试类的汇总 ──
$rows = @()
$totalRun = 0; $totalFail = 0; $totalErr = 0; $totalSkip = 0

if (Test-Path $SurefireDir) {
    # 解析 XML (testsuite 属性), 而非 .txt: surefire 的 .txt 对 @Nested 嵌套类会漏计,
    # XML 的 tests/failures/errors/skipped 属性才是该类(含嵌套)的完整准确汇总
    Get-ChildItem -Path $SurefireDir -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        ForEach-Object {
            $raw = Get-Content -LiteralPath $_.FullName -Raw -ErrorAction SilentlyContinue
            if ($raw -match '<testsuite\b') {
                $run  = if ($raw -match '\btests="(\d+)"')    { [int]$Matches[1] } else { 0 }
                $fail = if ($raw -match '\bfailures="(\d+)"') { [int]$Matches[1] } else { 0 }
                $err  = if ($raw -match '\berrors="(\d+)"')   { [int]$Matches[1] } else { 0 }
                $skip = if ($raw -match '\bskipped="(\d+)"')  { [int]$Matches[1] } else { 0 }
                $className = (($_.BaseName -replace '^TEST-', '') -replace '^csulzc\.My_Personal_Blogger\.', '')
                $rows += [PSCustomObject]@{
                    Class    = $className
                    Run      = $run
                    Failures = $fail
                    Errors   = $err
                    Skipped  = $skip
                    Result   = if (($fail + $err) -gt 0) { 'FAIL' } elseif ($skip -eq $run) { 'SKIP' } else { 'PASS' }
                }
                $totalRun += $run; $totalFail += $fail; $totalErr += $err; $totalSkip += $skip
            }
        }
}

# ── 从日志提取性能指标行 (每个并发场景的吞吐/延迟汇总) ──
$perfLines = @()
if (Test-Path $MvnLog) {
    # 日志行带 logback 时间戳前缀 (行首不是 [), 故不能用 ^ 锚定
    $perfLines = Select-String -Path $MvnLog -Pattern '\[like-dedup\]|\[like-count\]|\[view-count\]|\[mysql-' -ErrorAction SilentlyContinue |
        ForEach-Object { $_.Line }
}

# ── 打印最终汇总 ──
Write-Host ''
Write-Host "==================== 测试结果汇总 ====================" -ForegroundColor Cyan
if ($rows.Count -gt 0) {
    $rows | Format-Table -AutoSize | Out-String | Write-Host
} else {
    Write-Host ' (未找到 surefire 报告 — Maven 可能在测试启动前就失败了, 请看上方日志)' -ForegroundColor Yellow
}

if ($perfLines.Count -gt 0) {
    Write-Host '-------------------- 性能指标 (来自测试日志) --------------------' -ForegroundColor DarkGray
    $perfLines | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
}

Write-Host "------------------------------------------------------------" -ForegroundColor Cyan
Write-Host (" 合计: 运行 {0}  通过 {1}  失败 {2}  错误 {3}  跳过 {4}" -f `
    $totalRun, ($totalRun - $totalFail - $totalErr - $totalSkip), $totalFail, $totalErr, $totalSkip) -ForegroundColor White

$overallFail = ($totalFail + $totalErr) -gt 0
if ($mvnExit -ne 0 -and -not $overallFail) {
    # Maven 非零退出但报告无失败 (如编译错误): 以 Maven 退出码为准
    Write-Host " Maven 退出码: $mvnExit (测试报告未显示失败, 可能是编译/启动问题)" -ForegroundColor Yellow
    exit $mvnExit
}

if ($overallFail) {
    Write-Host " 结果: 失败 ✗" -ForegroundColor Red
    Write-Host " 详细堆栈见: target\surefire-reports\  或日志: $MvnLog" -ForegroundColor DarkGray
    exit 1
}

Write-Host " 结果: 通过 ✓" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
exit 0
