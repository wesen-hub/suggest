# 搜索提示词服务 (Search Suggestion Service)

基于 Elasticsearch 构建的高效、智能搜索提示词服务，实时响应用户输入，提供下拉提示列表,支持**中文**、**拼音**的**前缀补全**、**拼写纠错**和**中缀匹配提示**功能。

## 功能特性

- **前缀补全 (Prefix Completion)** 支持**中文,拼音,首字母**等多种输入方式的前缀补全
```text 
输入: "手"
返回: ["手机", "手机壳", "手机配件", "手表", ]
输入: "sj"
返回: ["手机", "手机壳", "手机配件", "手表", "手环"...]
输入: "sho"
返回: ["手机", "手机壳", "手机配件"...]
```
- **拼写纠错 (Spelling Correction)**: 支持中文拼音输入的拼写纠错和建议
```text
输入: "souji"
返回: ["手机", "手机壳", "手机配件"]
输入: "手集"
返回: ["手机", "手机壳", "手机配件"]
```
- **中缀匹配 (Infix Matching)**: 支持在任意位置匹配搜索词，提高召回率
```text
输入: "机配"
返回: ["手机配件", "手机配件包", "手机配件套装"]
输入: "jipei"
返回: ["手机配件", "手机配件包", "手机配件套装"]
```
- **多语言支持**: 原生支持中文和拼音混合输入
```text
输入: "配jian"
返回: ["配件包","手机配件","手机配件套装"]
```
- **智能排序**: 基于权重值的相关性排序
```text 
例如:  
   "手机" > "手机壳" > "手机配件"
```


## 技术架构

### Elasticsearch 索引设计

服务使用三个专用索引，各司其职：

1. **completion_index**- 前缀补全索引
    - 字段: `completionWords` (completion类型)
    - 分析器: 中文IK分词 + 拼音转换
    - 用途: 高性能前缀自动补全

2. **term_index** - 纠错索引
    - 字段: `termWords` (text类型) + `termWords.raw` (keyword类型)
    - 分析器: 中文IK分词 + 拼音转换
    - 用途: 拼写纠错和建议

3. **infix_index** - 中缀匹配索引
    - 字段: `infixWords` (search_as_you_type类型)
    - 分析器: 中文IK分词(细粒度) + 同义词扩展
    - 用途: 任意位置匹配搜索词

### 搜索逻辑流程

1. **优先尝试前缀匹配** → 如有结果则进行中缀扩展
2. **前缀无结果时尝试拼音匹配** → 直接返回匹配项
3. **拼音无结果时尝试纠错匹配** → 对纠错结果进行中缀扩展
4. **所有方式均无结果时** → 使用原始词进行中缀匹配  
图示:
```mermaid
flowchart TD
    A([开始]) --> B[输入查询词]
    B --> C{尝试前缀匹配}
    
    C -- 有结果 --> D[进行中缀扩展]
    D --> E[返回扩展结果]
    
    C -- 无结果 --> F{尝试拼音匹配}
    
    F -- 有结果 --> G[直接返回匹配项]
    
    F -- 无结果 --> H{尝试纠错匹配}
    
    H -- 有结果 --> I[对纠错结果进行中缀扩展]
    I --> J[返回扩展结果]
    
    H -- 无结果 --> K[使用原始词进行中缀匹配]
    K --> L[返回匹配结果]
```

## API 接口

### 搜索提示
GET /suggest?text={query}

**参数**:
- `text`: 用户输入的搜索词

**响应**:
- 返回匹配的提示词列表(JSON数组)

**示例**:
```bash
curl "http://localhost:8080/suggest?text=shouji"
```
**响应示例**:
```json
[
    "手机",
    "手机壳",
    "手机配件"
]
```
其他管理接口见 [接口文档.MD](https://github.com)
## 部署要求

### 环境依赖

- **Docker** 和 **Docker Compose** (Docker部署方式)
- **Java 19+** (直接部署方式)
- **Elasticsearch 8.13.x** (直接部署方式，建议8.13+)
- **Elasticsearch 插件**:
   - IK 中文分词插件
   - 拼音分析器插件

## 部署方式一：Docker Compose部署（推荐）

### 前提条件
- 已安装 Docker
- 已安装 Docker Compose

### 部署步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd suggest/docker
    ```
2. **启动服务**
   ```bash
   docker-compose up -d
   ```
   (首次启动需要拉取镜像,并初始化Elasticsearch索引，可能需要几分钟时间。)<br>
   <br>
3. **访问服务** 
   ```text
   服务默认运行在 `http://localhost:8080`
   ```

4. **停止服务**
   ```bash
   docker-compose down
   ```
### 配置说明
- `docker-compose.yml` 文件中定义了服务的配置，包括端口映射、环境变量等。
- `application.properties` 文件中可以配置服务的参数，如Elasticsearch地址、端口,词库挂载路径等。
- `termWords.txt` 是纠错词库文件
- `completionWords.txt` 是前缀补全词库文件

注:`termWords.txt` 和 `completionWords.txt` 以上两个文件可以根据需要替换为自定义词库，词库每次会在 suggest 服务启动时自动重新加载。
同时词库也支持热更新，更新接口见[接口文档.md](https://github.com)。

## 部署方式二：直接部署


### 部署步骤
1. **安装并配置 Elasticsearch**
```bash
# 下载并安装 Elasticsearch
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-8.13.0-linux-x86_64.tar.gz
tar -xzf elasticsearch-8.13.0-linux-x86_64.tar.gz
cd elasticsearch-8.13.0/
```
2. **安装 IK 分词器插件**

```bash
./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.16.2/elasticsearch-analysis-ik-7.16.2.zip
```
3. **安装拼音分析器插件**

```bash
./bin/elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-pinyin/releases/download/v7.16.2/elasticsearch-analysis-pinyin-7.16.2.zip
```
4. **配置 Elasticsearch**  
编辑 config/elasticsearch.yml 文件，添加以下配置：

```yaml
cluster.name: suggestion-cluster
network.host: 0.0.0.0
http.port: 9200
discovery.type: single-node
```
5. **启动 Elasticsearch**

```bash
./bin/elasticsearch -d
```
6. **部署提示词服务**

``` bash
# 克隆项目
git clone <repository-url>
cd suggest/

# 编译项目
mvn clean package

# 运行服务
java -jar target/suggest-1.0-SNAPSHOT.jar
```
## 数据初始化
服务启动后，会自动创建所需的Elasticsearch索引并加载词库

- **索引创建**: 服务会在启动时自动创建 `completion_index`, `term_index`, `infix_index` 三个索引
- **词库加载**: 服务会自动加载 `termWords.txt` 和 `completionWords.txt` 两个词库文件
## **验证部署**


1. **健康检查**  

执行
   ```bash
   curl http://localhost:8083/search/indices
   ```
返回:
   ```json
   [
      "term_index",
      "completion_index",
      "infix_index"
   ]
   ```
说明服务启动成功


2. **测试搜索建议**

```bash
curl "http://localhost:8080/suggest?text=手机"
```
3. **查看服务日志**

```bash
# Docker部署方式
docker logs suggestion_service

# 直接部署方式
tail -f logs/suggestion-service.log
```
## 故障排除
### 常见问题
1. **Elasticsearch 连接失败**

   - 检查Elasticsearch是否正常运行

   - 确认网络连接和防火墙设置

2. **插件加载失败**

    - 确认插件版本与Elasticsearch版本匹配

    - 检查插件目录权限

3. **服务启动失败**

    - 检查Java版本是否符合要求

    - 检查端口是否被占用

## 获取帮助
如果在部署过程中遇到问题，请查看项目文档或提交Issue。      