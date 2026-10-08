#!/bin/sh
# 连接池饱和对照实验 —— 在 MySQL 容器里跑（bash 由容器提供，宿主机无需安装任何依赖）
#
# 前置（在 MySQL 容器内执行一次）：
#   CREATE TABLE IF NOT EXISTS store_sales (store_code VARCHAR(16) PRIMARY KEY,
#                                          sales_text VARCHAR(128) NOT NULL);
#   INSERT IGNORE INTO store_sales VALUES ('ST001','tianhe-12480');
#   # 关键：必须用没有 SUPER / CONNECTION_ADMIN 权限的账号跑，否则连接数打满时
#   #   root 仍能挤进去（MySQL 为管理员保留了 1 个连接），测不出真实结果
#   CREATE USER IF NOT EXISTS 'app'@'%' IDENTIFIED BY 'app1234';
#   GRANT SELECT ON storeha.* TO 'app'@'%';
#   FLUSH PRIVILEGES;
#   SET GLOBAL max_connections=8;      # 用 max_connections 模拟有限大小的连接池
#
# 用法：cat pool-saturation.sh | docker exec -i <mysql容器> sh -s fast|slow
#   fast —— 快查询（200ms 级）
#   slow —— 慢查询（SLEEP 4s，模拟 DB 或下游变慢）
rm -f /tmp/rc /tmp/err
N=12
if [ "$1" = "slow" ]; then
  SQL='SELECT SLEEP(4), store_code FROM store_sales LIMIT 1'
else
  SQL='SELECT store_code FROM store_sales LIMIT 1'
fi
i=1
while [ $i -le $N ]; do
  ( mysql -uapp -papp1234 storeha -e "$SQL" >/dev/null 2>>/tmp/err; echo $? >> /tmp/rc ) &
  i=$((i+1))
done
wait
echo "退出码分布(0=成功 / 非0=失败):"
sort /tmp/rc | uniq -c
echo "Too many connections 次数:"
grep -c "Too many connections" /tmp/err
echo "(0 表示没有发生连接耗尽)"
