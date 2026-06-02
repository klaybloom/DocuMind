# DocuMind - 智能文档 RAG 助手

DocuMind 是一款基于 Java Spring Boot 和 LangChain4j 构建的智能文档检索助手。它利用检索增强生成 (RAG) 技术，能够针对用户上传的文档提供精准的解读和问答，并在文档库未命中时自动切换至通用的 AI 知识库。

## ✨ 核心特性

- **现代感 UI 界面**：采用暗黑磨砂玻璃风格设计，提供流畅的交互体验。
- **智能 RAG 检索**：通过向量相似度计算，精准锁定文档相关内容。
- **分流机制 (Hybrid AI)**：
  - **精准命中**：若文档中存在相关信息，AI 将基于本地知识进行深度解读。
  - **通用回退**：若文档未提及，AI 将利用自身税务知识库提供专业建议。
- **多轮对话记忆**：支持上下文理解，让沟通更自然、更连贯。
- **文件管理**：支持 PDF 和 TXT 格式文档的快速上传、解析及索引构建。

## 🛠️ 技术栈

- **后端**: Java 17, Spring Boot 3.2.1
- **AI 框架**: [LangChain4j](https://github.com/langchain4j/langchain4j)
- **嵌入模型**: All-MiniLM-L6-V2 (本地运行)
- **大语言模型**: DeepSeek API (兼容 OpenAI 格式)
- **前端**: 原生 HTML5, Vanilla JS, CSS3 (针对现代浏览器优化)

## 🚀 快速启动

### 1. 环境准备
- JDK 17+
- Maven 3.8+
- [DeepSeek API Key](https://platform.deepseek.com/)

### 2. 配置设置

**重要：请勿将 API Key 直接写入配置文件！**

#### 方法 1：使用环境变量（推荐）
```bash
export DEEPSEEK_API_KEY=your_actual_api_key_here
```

#### 方法 2：使用 application-local.yml
```bash
cp src/main/resources/application-local.yml.template src/main/resources/application-local.yml
# 编辑 application-local.yml，填入你的 API Key
```

详细配置说明请参考 [SECURITY.md](./SECURITY.md)

### 3. 运行项目
```bash
mvn spring-boot:run
```
访问地址: `http://localhost:8080`

## 📂 项目结构
```text
DocuMind/
├── src/main/java/            # 业务逻辑 (RagService, Controller 等)
├── src/main/resources/       # 静态资源 (HTML/JS/CSS) 及配置文件
├── pom.xml                   # 项目依赖
└── documents/                # 上传文档存储目录
```

## 📝 许可证
MIT License
