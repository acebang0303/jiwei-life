#!/bin/bash
# 运行秒杀压测（headless 模式）
# 用法: bash run_load.sh <ACTIVITY_ID> <DISH_ID> [并发数] [输出前缀]
# 前置:
#   1. 后端运行在 localhost:8080（事务消息版）
#   2. 已用 gen_tokens.sh 生成 tokens.csv
#   3. 已重置秒杀活动库存: DB stock + Redis seckill:stock:{act}:{dish}
set -euo pipefail

JMETER="/d/software/apache-jmeter-5.6.3/bin/jmeter"
ACT_ID="${1:?需要 ACTIVITY_ID}"
DISH_ID="${2:?需要 DISH_ID}"
CONC="${3:-100}"
PREFIX="${4:-seckill}"

# 用 sed 动态替换 .jmx 里的活动参数（ACTIVITY_ID/DISH_ID/并发数）
TMP_JMX="/tmp/${PREFIX}-${ACT_ID}.jmx"
sed -e "s/\${ACTIVITY_ID}/${ACT_ID}/g" -e "s/\${DISH_ID}/${DISH_ID}/g" \
    -e "s/ThreadGroup.num_threads\">100/ThreadGroup.num_threads\">${CONC}/" \
    "$(dirname "$0")/seckill-load.jmx" > "$TMP_JMX"

echo "=== 运行秒杀压测: 活动=${ACT_ID}, 并发=${CONC} ==="
"$JMETER" -n -t "$TMP_JMX" \
  -l "/tmp/${PREFIX}.jtl" \
  -e -o "/tmp/${PREFIX}-report" 2>&1 | tail -20

echo "=== 报告已生成: /tmp/${PREFIX}-report/index.html ==="
echo "=== 原始结果: /tmp/${PREFIX}.jtl ==="
