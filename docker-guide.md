# Docker Desktop 完整教程（Windows） + JiweiLife 环境搭建

> 2026-07-26 · 从零开始，覆盖安装、常见问题排查、项目中间件一键启动

---

## 一、安装前：确保电脑满足条件

### 1. 检查系统版本

Win + R 打开运行，输入 `winver` 回车：
- **Windows 10**：版本号需 ≥ 19045（22H2）
- **Windows 11**：版本号需 ≥ 22621（22H2）

如果版本太低，先去 Windows 更新里升级系统。

### 2. 检查虚拟化是否开启

打开**任务管理器**（Ctrl+Shift+Esc）→ **性能**标签 → **CPU**：
- 看右下角"虚拟化：**已启用**"
- 如果显示"已禁用"，需要进 BIOS 开启（Intel VT-x 或 AMD-V）

### 3. 内存建议

至少 8GB，推荐 16GB。Docker + JiweiLife 全套中间件大约占用 2-3GB 内存。

---

## 二、第一步：安装 WSL2（绝大多数"打不开"的根源就在这里）

Docker Desktop 在 Windows 上依赖 WSL2 运行 Linux 容器。**很多人的 Docker 打不开就是因为 WSL2 没装好。**

### 以管理员身份打开 PowerShell

右键开始菜单 → **终端(管理员)** 或搜索 PowerShell → 右键 → 以管理员身份运行

### 一行命令安装 WSL2：

```powershell
wsl --install
```

安装完成后**必须重启电脑**。

### 重启后，再打开 PowerShell（管理员），确认版本：

```powershell
wsl --set-default-version 2
wsl --update
wsl --version
```

看到 "WSL 版本：2.x.x" 就说明 OK 了。

### 如果 `wsl --install` 报错或失败

试试手动启用组件：

```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
```

然后重启电脑，再运行 `wsl --set-default-version 2`。

---

## 三、第二步：下载和安装 Docker Desktop

### 下载

打开 [https://docs.docker.com/desktop/setup/install/windows-install/](https://docs.docker.com/desktop/setup/install/windows-install/) 下载最新版。

> 如果官网打不开或下载慢，可以选择国内的镜像下载：
> - 腾讯云：https://mirrors.cloud.tencent.com/docker-ce/win/stable/
> - 清华源：https://mirrors.tuna.tsinghua.edu.cn/docker-ce/win/static/stable/

### 安装

1. 双击 `Docker Desktop Installer.exe`
2. 安装模式选 **"Use WSL 2 instead of Hyper-V"**（务必勾选）
3. 其他一路默认
4. 安装完**重启电脑**

---

## 四、第三步：首次启动

### 启动 Docker Desktop

从开始菜单点 Docker Desktop 图标，任务栏右下角会出现鲸鱼图标。

**非常常见的情况**：第一次启动可能卡在 "Starting..." 长达几分钟，这是正常的，它在初始化 WSL。

### 如果卡住超过 5 分钟不动

按顺序尝试：

**方案 A：杀掉进程重来**
```batch
taskkill /IM "Docker Desktop.exe" /F
```
然后再启动 Docker Desktop。

**方案 B：重置 WSL 数据（很有效）**
```powershell
wsl --shutdown
wsl --unregister docker-desktop
wsl --unregister docker-desktop-data
```
然后启动 Docker Desktop，它会自动重建。

**方案 C：检查服务**
Win+R → `services.msc` → 找到 **Docker Desktop Service**，确保状态是"正在运行"。如果不是，右键启动。

**方案 D：重置 Docker 配置**
删除以下文件夹后重新启动：
```
%APPDATA%\Docker
%LOCALAPPDATA%\Docker
```
> 路径里的 `%APPDATA%` 是变量，直接复制到资源管理器地址栏就行

**方案 E：核武器——彻底重装**
1. 设置 → 应用 → 卸载 Docker Desktop
2. 手动删除以下所有残留文件夹：
   - `%APPDATA%\Docker`
   - `%LOCALAPPDATA%\Docker`
   - `C:\ProgramData\DockerDesktop`
   - `%USERPROFILE%\.docker`
3. 重启电脑
4. 以管理员身份运行安装程序重新安装

---

## 五、第四步：验证安装成功

打开 PowerShell 或 CMD（不需要管理员），运行：

```cmd
docker --version
```
看到类似 "Docker version 28.x.x" 的输出就说明安装成功了。

再跑个测试容器：

```cmd
docker run hello-world
```

看到 "Hello from Docker!" 就一切正常。

---

## 六、第五步：配置镜像加速（重要！）

不配加速的话，拉镜像可能慢到怀疑人生。

右键右下角鲸鱼图标 → **Settings** → **Docker Engine**，把内容改成：

```json
{
  "registry-mirrors": [
    "https://docker.1panelproxy.com",
    "https://docker.m.daocloud.io",
    "https://docker.mirrors.ustc.edu.cn",
    "https://mirror.aliyuncs.com"
  ]
}
```

点 **Apply & Restart**，等 Docker 重启完。

---

## 七、第六步：一键启动 JiweiLife 全部中间件

### 创建 docker-compose.yml

在你的项目文件夹（比如 `D:\software\sky-take-out-master\`）新建一个文件 `docker-compose.yml`，复制下面的内容：

```yaml
version: '3.8'

services:
  # ========== MySQL 8.0 ==========
  mysql:
    image: mysql:8.0
    container_name: jiwei-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root123
    volumes:
      - jiwei_mysql_data:/var/lib/mysql
      - ./jiwei-life/db:/docker-entrypoint-initdb.d  # 自动执行SQL
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
      - --default-authentication-plugin=mysql_native_password
    restart: unless-stopped

  # ========== Redis 7.x ==========
  redis:
    image: redis:7-alpine
    container_name: jiwei-redis
    ports:
      - "6379:6379"
    volumes:
      - jiwei_redis_data:/data
    restart: unless-stopped

  # ========== MongoDB 7.x ==========
  mongodb:
    image: mongo:7
    container_name: jiwei-mongodb
    ports:
      - "27017:27017"
    volumes:
      - jiwei_mongo_data:/data/db
    restart: unless-stopped

  # ========== PostgreSQL 16 + pgvector ==========
  postgres:
    image: pgvector/pgvector:pg16
    container_name: jiwei-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: jiwei_ai
    volumes:
      - jiwei_pg_data:/var/lib/postgresql/data
    restart: unless-stopped

  # ========== RocketMQ NameServer ==========
  rocketmq-namesrv:
    image: apache/rocketmq:5.3.0
    container_name: jiwei-rmq-namesrv
    ports:
      - "9876:9876"
    environment:
      JAVA_OPT_EXT: "-Xms256m -Xmx256m"
    command: sh mqnamesrv
    restart: unless-stopped

  # ========== RocketMQ Broker ==========
  rocketmq-broker:
    image: apache/rocketmq:5.3.0
    container_name: jiwei-rmq-broker
    depends_on:
      - rocketmq-namesrv
    ports:
      - "10911:10911"
      - "10909:10909"
    environment:
      JAVA_OPT_EXT: "-Xms512m -Xmx512m"
      NAMESRV_ADDR: "rocketmq-namesrv:9876"
    command: sh mqbroker -n rocketmq-namesrv:9876 -c /home/rocketmq/conf/broker.conf
    restart: unless-stopped

volumes:
  jiwei_mysql_data:
  jiwei_redis_data:
  jiwei_mongo_data:
  jiwei_pg_data:
```

### 启动一切

在 `docker-compose.yml` 所在目录打开终端：

```cmd
docker compose up -d
```

- `-d` 表示后台运行
- 第一次会下载镜像（可能需要几分钟，取决于网速）
- 之后每次 `docker compose up -d` 秒启动

### 验证所有服务

```cmd
docker compose ps
```

应该看到 6 个容器都是 "Up" 状态。

---

## 八、Docker 常用命令速查

| 命令 | 作用 |
|------|------|
| `docker compose up -d` | 启动所有服务（后台） |
| `docker compose down` | 停止并删除所有容器 |
| `docker compose ps` | 查看所有容器状态 |
| `docker compose logs -f mysql` | 实时看 MySQL 日志 |
| `docker compose restart mysql` | 重启 MySQL |
| `docker ps` | 查看所有运行中的容器 |
| `docker exec -it jiwei-mysql mysql -uroot -proot123` | 进入 MySQL 命令行 |
| `docker exec -it jiwei-redis redis-cli` | 进入 Redis 命令行 |

---

## 九、各中间件连接信息

安装完 Docker 并启动后，在 Spring Boot 的 `application-dev.yml` 中用以下配置：

| 服务 | Host | Port | 用户名 | 密码 |
|------|------|:--:|------|------|
| MySQL | localhost | 3306 | root | root123 |
| Redis | localhost | 6379 | — | (无) |
| MongoDB | localhost | 27017 | — | (无) |
| PostgreSQL | localhost | 5432 | postgres | postgres |
| RocketMQ NameServer | localhost | 9876 | — | — |

---

## 十、常见问题

### Q: docker 命令提示 "Permission denied"？

在 Windows 上一般是 Docker Desktop 没在运行。检查右下角有没有鲸鱼图标，图标里有没有绿点。

### Q: 某张镜像拉不下来？

检查步骤五的镜像加速配置好了没。配置后需要 Apply & Restart。

### Q: 端口被占用（3306/6379 等）？

你电脑上可能已经装了 MySQL/Redis 占用了这些端口。要么停掉旧的，要么改 docker-compose 里的端口号（比如改成 `3307:3306`）。

### Q: 电脑内存不够？

修改 docker-compose，在 RocketMQ broker 里把 `JAVA_OPT_EXT: "-Xms512m -Xmx512m"` 改成 `"-Xms256m -Xmx256m"`。

也可以按需启动单个服务，比如今天只开发 auth 模块：
```cmd
docker compose up -d mysql redis
```

### Q: 不想要 Docker，想手动装中间件？

- MySQL 8.0: https://dev.mysql.com/downloads/mysql/
- Redis Windows 版: https://github.com/tporadowski/redis/releases
- MongoDB 7: https://www.mongodb.com/try/download/community
- PostgreSQL 16: https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
  - pgvector 安装指南: https://github.com/pgvector/pgvector#windows
- RocketMQ: https://rocketmq.apache.org/download/
