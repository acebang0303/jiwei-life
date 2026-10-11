#!/bin/bash
# 生成 N 个压测用户的登录 token 写入 tokens.csv（供 JMeter CSV Data Set Config 使用）
# 用法: bash gen_tokens.sh [数量] [输出文件]
# 前置: 后端须运行在 localhost:8080（dev profile，mock-login 可用）
set -euo pipefail

COUNT="${1:-100}"
OUT="${2:-tokens.csv}"
BASE="http://localhost:8080"

: > "$OUT"
for i in $(seq 1 "$COUNT"); do
  openid="jm_${i}_$(date +%s)_$RANDOM"
  token=$(curl -s -X POST "$BASE/user/user/mock-login?openid=$openid" \
    | grep -o '"token":"[^"]*' | cut -d\" -f4)
  if [ -z "$token" ]; then
    echo "WARN: 第 $i 个 token 获取失败（后端是否运行？）" >&2
  fi
  echo "$token" >> "$OUT"
done
echo "已生成 $(wc -l < "$OUT") 个 token -> $OUT"
