# 快速启动指南

## 1. 准备工作

确保您的运行环境满足以下条件：
- **Java 21 (JDK)**：编译源码需要 JDK 21（仅 JRE 不够）。
- **Maven 3.6+**：用于编译和打包。
- **PostgreSQL 数据库**：服务正在运行，且已创建 `worldone` 数据库。

## 2. 编译源码

项目没有顶层聚合 POM，需按依赖顺序逐模块编译：

```bash
# 安装共享库到本地 Maven 仓库
cd llm-client && mvn clean install && cd ..
cd aipp-protocol && mvn clean install && cd ..

# 打包两个 Spring Boot 应用
cd world-one && mvn clean package && cd ..
cd memory-one && mvn clean package && cd ..
```

编译完成后，产物位于各模块的 `target/` 目录。将 jar 包复制到 `deploy/` 目录下（启动脚本要求 jar 包与脚本在同一目录）：

```bash
cp world-one/target/world-one-1.0-SNAPSHOT.jar deploy/
cp memory-one/target/memory-one-1.0-SNAPSHOT.jar deploy/
```

## 3. 设置数据库信息与模型信息

编辑 `deploy/start-all.sh`，将正确信息填写到脚本中（数据库连接、LLM API Key 等）。

## 4. 启动服务

进入 `deploy/` 目录，运行脚本启动服务：

```bash
cd deploy && bash start-all.sh
```

完成后，访问 `http://localhost:8090` 即可开始使用。
