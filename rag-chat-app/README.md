# DocuMind - RAG Chat Application

A ChatGPT-like document retrieval assistant built with Java, Spring Boot, LangChain4j, ChromaDB, and DeepSeek API.

## Features
- **Modern UI**: Clean, responsive ChatGPT-style interface.
- **Document-Based RAG**: Upload PDF or TXT files and ask questions about them.
- **LangChain4j**: Seamless integration with LLMs and Vector Stores.
- **DeepSeek API**: High-performance AI model for natural language understanding.
- **ChromaDB**: Efficient vector storage for document retrievals.

## Prerequisites
1. **Java 17+**
2. **Maven 3.8+**
3. **ChromaDB**: Running on `http://localhost:8000`.
   ```bash
   docker run -d -p 8000:8000 chromadb/chroma:latest
   ```
4. **DeepSeek API Key**: Get your key from [DeepSeek Platform](https://platform.deepseek.com/).

## Setup & Run

1. **Configure API Key**:
   Open `src/main/resources/application.yml` and replace `your-api-key-here` with your actual DeepSeek API key.
   Alternatively, set an environment variable:
   ```bash
   export DEEPSEEK_API_KEY=your_actual_key
   ```

2. **Build the Project**:
   ```bash
   mvn clean install
   ```

3. **Run the Application**:
   ```bash
   mvn spring-boot:run
   ```

4. **Access the UI**:
   Open [http://localhost:8080](http://localhost:8080) in your browser.

## Project Structure
- `src/main/java`: Backend Java code (Spring Boot, LangChain4j config, Services, Controllers).
- `src/main/resources/static`: Frontend assets (HTML, CSS, JS).
- `documents/`: Folder where uploaded files are stored.

## Usage
1. Click **Upload PDF/TXT** to add documents to the system.
2. The system automatically indexes the documents after upload.
3. Start chatting! The AI will use the content from your uploaded documents to answer questions.
4. Use the **Refresh** button in the sidebar if you manually add files to the `documents/` folder.
