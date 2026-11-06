"""
测试花纹识别服务
"""
import requests
import json
from pathlib import Path

# 服务地址
BASE_URL = "http://localhost:8001"

def test_health():
    """测试健康检查"""
    print("=== 测试健康检查 ===")
    resp = requests.get(f"{BASE_URL}/health")
    print(f"状态码：{resp.status_code}")
    print(f"响应：{resp.json()}")
    print()

def test_extract_feature(image_path: str):
    """测试特征提取"""
    print(f"=== 测试特征提取：{image_path} ===")
    with open(image_path, "rb") as f:
        files = {"file": ("test.jpg", f, "image/jpeg")}
        resp = requests.post(f"{BASE_URL}/api/recognize/extract", files=files)
    
    print(f"状态码：{resp.status_code}")
    print(f"响应：{resp.json()}")
    print()
    return resp.json()

def test_match(image_path: str):
    """测试匹配"""
    print(f"=== 测试匹配：{image_path} ===")
    with open(image_path, "rb") as f:
        files = {"file": ("test.jpg", f, "image/jpeg")}
        data = {"top_k": 3}
        resp = requests.post(f"{BASE_URL}/api/recognize/match", files=files, data=data)
    
    print(f"状态码：{resp.status_code}")
    print(f"响应：{resp.json()}")
    print()
    return resp.json()

def test_register(image_path: str, cat_id: int):
    """测试注册"""
    print(f"=== 测试注册：cat_id={cat_id} ===")
    with open(image_path, "rb") as f:
        files = {"file": ("test.jpg", f, "image/jpeg")}
        data = {"cat_id": cat_id}
        resp = requests.post(f"{BASE_URL}/api/recognize/register", files=files, data=data)
    
    print(f"状态码：{resp.status_code}")
    print(f"响应：{resp.json()}")
    print()
    return resp.json()

def test_stats():
    """测试统计"""
    print("=== 测试统计 ===")
    resp = requests.get(f"{BASE_URL}/api/recognize/stats")
    print(f"状态码：{resp.status_code}")
    print(f"响应：{resp.json()}")
    print()

if __name__ == "__main__":
    # 测试图片路径（需要替换为实际图片）
    test_image = "test_cat.jpg"
    
    if not Path(test_image).exists():
        print(f"测试图片不存在：{test_image}")
        print("请准备一张猫的照片用于测试")
        exit(1)
    
    # 运行测试
    test_health()
    test_stats()
    
    # 注册一只猫
    result = test_register(test_image, cat_id=1)
    if result["success"]:
        # 匹配测试
        test_match(test_image)
    
    print("=== 测试完成 ===")
