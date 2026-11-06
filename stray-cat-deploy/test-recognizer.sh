#!/bin/bash
# 花纹识别服务快速测试脚本

set -e

echo "========================================"
echo "  花纹识别服务测试"
echo "========================================"
echo ""

BASE_URL="http://localhost:8001"

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_pass() {
    echo -e "${GREEN}✓${NC} $1"
}

log_fail() {
    echo -e "${RED}✗${NC} $1"
}

# 1. 健康检查
echo "1. 健康检查..."
HEALTH=$(curl -s "$BASE_URL/health" 2>/dev/null)
if [[ $? -eq 0 && "$HEALTH" == *"ok"* ]]; then
    log_pass "服务正常"
else
    log_fail "服务未启动或异常"
    exit 1
fi
echo ""

# 2. 获取统计信息
echo "2. 数据库统计..."
STATS=$(curl -s "$BASE_URL/api/recognize/stats" 2>/dev/null)
if [[ $? -eq 0 ]]; then
    log_pass "统计接口正常"
    echo "   $STATS" | python3 -m json.tool 2>/dev/null || echo "   $STATS"
else
    log_fail "统计接口异常"
fi
echo ""

# 3. 测试特征提取（需要测试图片）
TEST_IMAGE="$1"
if [[ -n "$TEST_IMAGE" && -f "$TEST_IMAGE" ]]; then
    echo "3. 测试特征提取..."
    EXTRACT_RESULT=$(curl -s -X POST \
        -F "file=@$TEST_IMAGE" \
        "$BASE_URL/api/recognize/extract" 2>/dev/null)
    
    if [[ "$EXTRACT_RESULT" == *"success"* ]]; then
        log_pass "特征提取成功"
        echo "   $EXTRACT_RESULT" | python3 -m json.tool 2>/dev/null || echo "   $EXTRACT_RESULT"
    else
        log_fail "特征提取失败"
        echo "   $EXTRACT_RESULT"
    fi
    echo ""
    
    # 4. 测试匹配
    echo "4. 测试匹配..."
    MATCH_RESULT=$(curl -s -X POST \
        -F "file=@$TEST_IMAGE" \
        -F "top_k=3" \
        "$BASE_URL/api/recognize/match" 2>/dev/null)
    
    if [[ "$MATCH_RESULT" == *"matches"* ]]; then
        log_pass "匹配接口正常"
        echo "   $MATCH_RESULT" | python3 -m json.tool 2>/dev/null || echo "   $MATCH_RESULT"
    else
        log_fail "匹配接口异常"
        echo "   $MATCH_RESULT"
    fi
    echo ""
else
    echo "3-4. 跳过特征提取和匹配测试（未提供测试图片）"
    echo "   用法: ./test-recognizer.sh /path/to/cat.jpg"
    echo ""
fi

# 5. 测试 Spring Boot 集成
echo "5. 测试 Spring Boot 集成..."
SPRING_HEALTH=$(curl -s http://localhost:8080/api/actuator/health 2>/dev/null)
if [[ "$SPRING_HEALTH" == *"UP"* ]]; then
    log_pass "Spring Boot 正常"
    
    # 测试识别接口
    if [[ -n "$TEST_IMAGE" && -f "$TEST_IMAGE" ]]; then
        echo ""
        echo "6. 测试 Spring Boot 识别接口..."
        SPRING_RECOGNIZE=$(curl -s -X POST \
            -H "Authorization: Bearer test-token" \
            -F "photo=@$TEST_IMAGE" \
            "http://localhost:8080/api/feeding/recognize" 2>/dev/null)
        
        if [[ "$SPRING_RECOGNIZE" == *"code"* ]]; then
            log_pass "Spring Boot 识别接口正常"
            echo "   $SPRING_RECOGNIZE" | python3 -m json.tool 2>/dev/null || echo "   $SPRING_RECOGNIZE"
        else
            log_fail "Spring Boot 识别接口异常"
            echo "   $SPRING_RECOGNIZE"
        fi
    fi
else
    log_fail "Spring Boot 未启动"
fi

echo ""
echo "========================================"
echo "  测试完成"
echo "========================================"
