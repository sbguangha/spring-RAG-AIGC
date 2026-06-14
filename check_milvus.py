from pymilvus import MilvusClient
from pymilvus import MilvusClient

client = MilvusClient(uri="http://127.0.0.1:19530")
collections = client.list_collections()
print('所有集合:', collections)

for c in collections:
    stats = client.get_collection_stats(c)
    print(f"集合 {c}: {stats}")

# 新版客户端，一行搞定连接
client = MilvusClient(uri="http://127.0.0.1:19530")

# 1. 安全地列出所有集合
collections = client.list_collections()
print('当前集合列表:', collections)

# 2. 安全地检查目标集合是否存在，不再直接抛异常
target_collection = "knowledge_chunks"
if client.has_collection(target_collection):
    # 获取集合统计信息
    stats = client.get_collection_stats(target_collection)
    print(f"集合 [{target_collection}] 存在，当前数据量: {stats}")
else:
    print(f"⚠️ 集合 [{target_collection}] 不存在！请检查 Spring AI 后端是否已成功写入数据。")