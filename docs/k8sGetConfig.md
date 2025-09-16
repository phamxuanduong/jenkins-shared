# k8sGetConfig

Lấy dữ liệu từ Kubernetes ConfigMap và lưu vào file. Tự động lấy từ 2 ConfigMaps: `general` (chung) và branch-specific.

## Tham số

**Tất cả tham số đều tùy chọn:**
- `namespace`: Kubernetes namespace (mặc định: tự động từ `getProjectVars().NAMESPACE`)
- `configmap`: Tên ConfigMap theo branch (mặc định: tự động từ `getProjectVars().SANITIZED_BRANCH`)
- `generalConfigmap`: Tên ConfigMap chung (mặc định: 'general')
- `items`: Map các key và đường dẫn file đích (mặc định: lấy tất cả keys từ cả 2 ConfigMaps)
- `vars`: Project variables (mặc định: tự động gọi `getProjectVars()`)

## Mặc định tự động

- `namespace` = REPO_NAME (từ Git URL)
- `generalConfigmap` = 'general' (chứa files dùng chung cho tất cả branches)
- `configmap` = SANITIZED_BRANCH (chứa files riêng cho branch này)
- Lấy tất cả data từ cả 2 ConfigMaps
- Files từ branch ConfigMap sẽ override files từ general ConfigMap (nếu cùng tên)
- Tự động bỏ qua nếu ConfigMap/key không tồn tại

## Workflow

1. **Lấy files từ ConfigMap `general`**: Files dùng chung cho tất cả branches
2. **Lấy files từ ConfigMap theo branch**: Files riêng cho branch hiện tại
3. **Override logic**: Files từ branch sẽ ghi đè lên files chung nếu trùng tên

## Implementation Details

### Key Extraction
- Sử dụng `kubectl -o yaml` + `awk` để parse ConfigMap keys
- Awk script: `/^data:/,/^[a-zA-Z]/ {extract keys}`
- Hỗ trợ tất cả ký tự hợp lệ trong ConfigMap keys

### Special Characters Support
- **Keys có dấu chấm**: `.env`, `.gitignore`, `.dockerignore`
- **Keys có space**: `my file.txt`
- **Keys có ký tự đặc biệt**: `app-config.yaml`, `db_config.json`

### Data Fetching
- Sử dụng `go-template` với `index` function thay vì `jsonpath`
- Template: `{{index .data "keyname"}}`
- Lý do: `jsonpath` không hoạt động với keys có dấu chấm

### Error Handling
- Graceful skip nếu ConfigMap không tồn tại
- Warning nếu key rỗng hoặc không tồn tại
- Continue với các keys khác nếu 1 key fail

### Multi-line Support
- Hỗ trợ content nhiều dòng từ ConfigMap
- Preserves line endings và formatting
- Handle binary data safely

## Troubleshooting

### `.env` báo empty
**Nguyên nhân**: ConfigMap key parsing issue hoặc empty data
**Giải pháp**:
```bash
# Check ConfigMap data
kubectl describe configmap general -n your-namespace
kubectl get configmap general -n your-namespace -o yaml

# Test manual extraction
kubectl get configmap general -n your-namespace -o go-template='{{index .data ".env"}}'
```

### Keys không được detect
**Nguyên nhân**: YAML format issue hoặc awk parsing
**Giải pháp**:
```bash
# Check ConfigMap structure
kubectl get configmap your-cm -n your-namespace -o yaml

# Test key extraction
kubectl get configmap your-cm -n your-namespace -o yaml | \
awk '/^data:/ {flag=1; next} /^[a-zA-Z]/ && flag {flag=0} flag && /^  [^ ]/ {gsub(/^  /, ""); gsub(/:.*/, ""); print}'
```

### Special keys fail
**Nguyên nhân**: jsonpath không support special characters
**Giải pháp**: Function đã dùng `go-template index` để handle tất cả key types

## Ví dụ sử dụng

### Hoàn toàn tự động
```groovy
k8sGetConfig()
// Lấy từ ConfigMap 'general' và branch ConfigMap
// Tất cả keys từ cả 2 ConfigMaps
```

### Custom general ConfigMap
```groovy
k8sGetConfig(generalConfigmap: 'shared')
// Lấy từ 'shared' thay vì 'general'
```

### Specific items only
```groovy
k8sGetConfig(
    items: [
        'Dockerfile': 'Dockerfile',
        '.env': '.env',
        'config.yaml': 'config/app.yaml'
    ]
)
// Chỉ lấy 3 files cụ thể
```

### Full custom
```groovy
k8sGetConfig(
    namespace: 'my-namespace',
    generalConfigmap: 'shared-config',
    configmap: 'beta-config',
    items: [
        'Dockerfile': 'build/Dockerfile',
        '.env': 'deploy/.env'
    ]
)
```

## ConfigMap Setup Examples

### ConfigMap general (shared)
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: general
  namespace: my-app
data:
  .env: |
    ENV=production
    DATABASE_CONNECTION=mysql
    # Shared environment variables
  docker-compose.yml: |
    version: '3'
    services:
      app:
        image: placeholder
  package.json: |
    {
      "name": "my-app",
      "version": "1.0.0"
    }
```

### ConfigMap beta-api (branch-specific)
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: beta-api
  namespace: my-app
data:
  Dockerfile: |
    FROM node:18-alpine
    WORKDIR /app
    COPY package*.json ./
    RUN npm ci --only=production
    COPY . .
    EXPOSE 3000
    CMD ["npm", "start"]
  .env: |
    ENV=beta
    DEBUG=true
    LOG_LEVEL=debug
    # Override .env from general ConfigMap
  nginx.conf: |
    server {
      listen 80;
      location / {
        proxy_pass http://app:3000;
      }
    }
```

## Best Practices

### ConfigMap Organization
- **General ConfigMap**: Chứa configs dùng chung (database, Redis, base .env)
- **Branch ConfigMaps**: Chứa configs riêng (Dockerfile, nginx config, environment-specific .env)
- **Naming convention**: Dùng SANITIZED_BRANCH làm tên ConfigMap

### File Management
- **Override strategy**: Branch files ghi đè general files
- **File naming**: Giữ nguyên tên key làm filename (`.env` → `.env`)
- **Directory structure**: Hỗ trợ nested paths (`config/app.yaml`)

### Security
- **Sensitive data**: Dùng Kubernetes Secrets thay vì ConfigMap
- **Access control**: Set appropriate RBAC cho ConfigMaps
- **Audit**: Log tất cả ConfigMap access

### Performance
- **Caching**: ConfigMaps được cache tại node level
- **Size limits**: ConfigMap có giới hạn 1MB per key
- **Batch operations**: Function tự động batch multiple keys