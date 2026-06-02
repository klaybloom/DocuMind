document.addEventListener('DOMContentLoaded', () => {
    const chatMessages = document.getElementById('chat-messages');
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const newChatBtn = document.getElementById('new-chat-btn');
    const fileInput = document.getElementById('file-input');
    const uploadBtn = document.getElementById('upload-btn');
    const fileList = document.getElementById('file-list');
    const welcomeScreen = document.getElementById('welcome-screen');

    let isProcessing = false;

    // Auto-resize textarea
    userInput.addEventListener('input', () => {
        userInput.style.height = 'auto';
        userInput.style.height = Math.min(userInput.scrollHeight, 200) + 'px';
        sendBtn.disabled = userInput.value.trim() === '' || isProcessing;
    });

    // Send message
    const sendMessage = async () => {
        const message = userInput.value.trim();
        if (!message || isProcessing) return;

        isProcessing = true;
        sendBtn.disabled = true;

        // Hide welcome screen if first message
        if (welcomeScreen) welcomeScreen.style.display = 'none';

        // Add user message to UI
        appendMessage('user', message);

        // Clear input immediately
        userInput.value = '';
        userInput.style.height = 'auto';

        // Add assistant thinking message
        const thinkingId = 'thinking-' + Date.now();
        appendMessage('assistant', '<div class="typing"><span></span><span></span><span></span></div>', thinkingId, true);

        try {
            const response = await fetch('/api/chat', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message })
            });
            const data = await response.json();

            // Replace thinking message with actual response
            const thinkingMsg = document.getElementById(thinkingId);
            if (thinkingMsg) {
                const textDiv = thinkingMsg.querySelector('.message-text');
                textDiv.innerHTML = formatResponse(data.response);
            }
        } catch (error) {
            console.error('Error:', error);
            const thinkingMsg = document.getElementById(thinkingId);
            if (thinkingMsg) {
                const textDiv = thinkingMsg.querySelector('.message-text');
                textDiv.textContent = '无法建立连接，请检查后端服务是否启动。';
                textDiv.style.color = '#ef4444';
            }
        } finally {
            isProcessing = false;
            sendBtn.disabled = userInput.value.trim() === '';
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
    };

    sendBtn.addEventListener('click', sendMessage);
    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    // File Management
    const loadFileList = async () => {
        try {
            const response = await fetch('/api/files/list');
            const files = await response.json();
            fileList.innerHTML = '';
            files.forEach(file => {
                const li = document.createElement('li');
                li.className = 'file-item';
                li.innerHTML = `
                    <i class="far fa-file-pdf" style="color: #ef4444;"></i>
                    <span class="file-name">${file}</span>
                    <button class="delete-file-btn" onclick="deleteFile('${file}', event)"><i class="fas fa-trash-alt"></i></button>
                `;
                fileList.appendChild(li);
            });
        } catch (error) {
            console.error('Error loading files:', error);
        }
    };

    window.deleteFile = async (filename, event) => {
        event.stopPropagation();
        if (!confirm(`确定要删除 ${filename} 吗？`)) return;
        try {
            await fetch(`/api/files/${filename}`, { method: 'DELETE' });
            loadFileList();
        } catch (error) {
            alert('删除失败');
        }
    };

    uploadBtn.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', async () => {
        if (fileInput.files.length === 0) return;

        const file = fileInput.files[0];
        const formData = new FormData();
        formData.append('file', file);

        const tempId = 'uploading-' + Date.now();
        if (welcomeScreen) welcomeScreen.style.display = 'none';
        appendMessage('assistant', `正在解析并索引文件: ${file.name}...`, tempId);

        try {
            const response = await fetch('/api/files/upload', {
                method: 'POST',
                body: formData
            });

            const msgRow = document.getElementById(tempId);
            const textDiv = msgRow.querySelector('.message-text');

            if (response.ok) {
                loadFileList();
                textDiv.innerHTML = `<span style="color: #10a37f;"><i class="fas fa-check-circle"></i> "${file.name}" 已成功入库并构建索引。</span>`;
            } else {
                textDiv.textContent = '文件上传或索引失败。';
            }
        } catch (error) {
            const msgRow = document.getElementById(tempId);
            if (msgRow) {
                msgRow.querySelector('.message-text').textContent = '连接服务时出错。';
            }
        } finally {
            fileInput.value = '';
        }
    });

    newChatBtn.addEventListener('click', () => {
        if (welcomeScreen) welcomeScreen.style.display = 'block';
        const messages = chatMessages.querySelectorAll('.message-row');
        messages.forEach(msg => msg.remove());
    });

    function appendMessage(role, text, id = null, isHtml = false) {
        const row = document.createElement('div');
        row.className = `message-row ${role}`;
        if (id) row.id = id;

        const avatarClass = role === 'user' ? 'user-avatar' : 'ai-avatar';
        const icon = role === 'user' ? 'fa-user' : 'fa-robot';

        let contentHtml = isHtml ? text : escapeHtml(text).replace(/\n/g, '<br>');

        row.innerHTML = `
            <div class="message-content">
                <div class="avatar ${avatarClass}">
                    <i class="fas ${icon}" style="color: white; font-size: 14px;"></i>
                </div>
                <div class="message-text">${contentHtml}</div>
            </div>
        `;

        chatMessages.appendChild(row);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function formatResponse(text) {
        // More robust markdown-like formatting for response
        return text
            .replace(/```([\s\S]*?)```/g, '<div class="code-block"><pre><code>$1</code></pre></div>')
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\n/g, '<br>');
    }

    loadFileList();
});

