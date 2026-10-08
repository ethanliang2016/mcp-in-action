# 缓存穿透放大倍数实验 —— 在宿主机跑，通过 docker exec 调 MySQL / Redis 容器内的客户端
# 宿主机无需安装任何 Python 包或数据库驱动。
#
# 前置：docker run -d --name ha-db -e MYSQL_ROOT_PASSWORD=test1234 -e MYSQL_DATABASE=storeha -p 3307:3306 mysql:8.0
#       docker run -d --name ha-cache -p 6379:6379 redis:7-alpine
#
# 用法：.\cache-stampede.ps1
$ErrorActionPreference = 'Continue'

function Get-Questions {
    $q = docker exec ha-db mysql -uroot -ptest1234 -N -e "SHOW GLOBAL STATUS LIKE 'Questions'" 2>$null
    return [int]($q -split "`t")[1]
}

docker exec ha-cache redis-cli DEL sales:ST001 | Out-Null

Write-Output "=== 场景1：Redis 正常（有缓存层）==="
$q0 = Get-Questions
1..5 | ForEach-Object {
    $hit = (docker exec ha-cache redis-cli GET sales:ST001 2>$null | Out-String).Trim()
    if ([string]::IsNullOrEmpty($hit) -or $hit -eq '(nil)') {
        $v = docker exec ha-db mysql -uapp -papp1234 -N storeha -e "SELECT sales_text FROM store_sales WHERE store_code='ST001'" 2>$null
        docker exec ha-cache redis-cli SET sales:ST001 "$v" EX 300 | Out-Null
        Write-Output "  请求 $_ : 缓存未命中 -> 查 DB"
    } else {
        Write-Output "  请求 $_ : 缓存命中（DB 零查询）"
    }
}
Start-Sleep -Seconds 1
$q1 = Get-Questions
Write-Output "  MySQL Questions 增量 = $($q1 - $q0)  （5 次请求）"

Write-Output ""
Write-Output "=== 场景2：缓存层不可用 ==="
docker stop ha-cache | Out-Null
Start-Sleep -Seconds 3
$q2 = Get-Questions
1..5 | ForEach-Object {
    docker exec ha-db mysql -uapp -papp1234 -N storeha -e "SELECT sales_text FROM store_sales WHERE store_code='ST001'" 2>$null | Out-Null
    Write-Output "  请求 $_ : 缓存不可用 -> 全部直连 DB"
}
$q3 = Get-Questions
Write-Output "  MySQL Questions 增量 = $($q3 - $q2)  （5 次请求）"
docker start ha-cache | Out-Null
