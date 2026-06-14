# -*- coding: utf-8 -*-
import sys
sys.stdout.reconfigure(encoding='utf-8')

from pymilvus import MilvusClient

client = MilvusClient(uri='http://127.0.0.1:19530', db_name='aigc_knowledge')

# 先用简单关键词过滤搜索
res = client.search(
    collection_name='knowledge_chunks_v4',
    data=[[0.0] * 1024],  # 全零向量，测试是否能返回任何结果
    limit=5,
    search_params={'metric_type': 'COSINE', 'params': {'nprobe': 16}},
    output_fields=['doc_id', 'content']
)
print('全零向量搜索结果:', res)

# 看看里面到底有什么内容
print('\n前 3 条数据内容:')
rows = client.query(collection_name='knowledge_chunks_v4', output_fields=['doc_id', 'content'], limit=3)
for r in rows:
    print(r)
