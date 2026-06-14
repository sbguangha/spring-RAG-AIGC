# -*- coding: utf-8 -*-
import sys
sys.stdout.reconfigure(encoding='utf-8')
from pymilvus import MilvusClient

client = MilvusClient(uri='http://127.0.0.1:19530')
collections = client.list_collections()
print('所有集合:', collections)

for c in collections:
    schema = client.describe_collection(c)
    dim = None
    for f in schema.get('fields', []):
        if f.get('type') == 'FloatVector' or f.get('data_type') == 'FloatVector':
            dim = f.get('params', {}).get('dim')
            break
    print(f'集合: {c}')
    print(f'  维度: {dim}')
    field_names = [f.get('name') for f in schema.get('fields', [])]
    print(f'  字段: {field_names}')
    stats = client.get_collection_stats(c)
    print(f'  统计: {stats}')
