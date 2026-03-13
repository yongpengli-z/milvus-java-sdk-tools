### 5.8 Azure 部署方案（新增）

本项目新增了 Azure 云平台的 Milvus 部署方案，位于项目根目录：

#### 5.8.1 AKS + Workload Identity（生产推荐）

**目录**：`azure-aks-helm/`

**特点**：
- 使用 Azure 托管标识（无静态密钥）
- OIDC + Workload Identity 联合认证
- Helm 自动化部署
- 一键脚本部署

**文件说明**：
- `values.yaml` - Helm Chart 配置文件
- `setup-aks-workload-identity.sh` - 自动化部署脚本
- `README.md` - 详细部署指南

**快速部署**：
```bash
cd azure-aks-helm
chmod +x setup-aks-workload-identity.sh
./setup-aks-workload-identity.sh
```

**架构特点**：
- Milvus 集群配置（Proxy 2副本，QueryNode 2副本，DataNode 2副本，IndexNode 1副本）
- 禁用 MinIO，使用 Azure Blob Storage
- etcd 3副本持久化
- 外部 S3 兼容存储配置

#### 5.8.2 Docker Compose（本地开发/测试）

**目录**：`azure-docker-compose/`

**特点**：
- 适合本地开发和测试
- 快速启动
- etcd + Milvus standalone
- 支持 Azure Blob Storage

**文件说明**：
- `docker-compose.yml` - 完整编排文件
- `milvus.yaml` - Milvus 配置文件
- `README.md` - 使用指南

**快速启动**：
```bash
cd azure-docker-compose
# 编辑 milvus.yaml，配置 Azure Storage 信息
docker-compose up -d
```

**注意事项**：
- 生产环境建议使用 AKS + Workload Identity 方案
- 不要将包含真实密钥的配置文件提交到版本控制

---

