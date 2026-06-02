document.addEventListener('DOMContentLoaded', () => {
    const chatMessages = document.getElementById('chat-messages');
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const newChatBtn = document.getElementById('new-chat-btn');
    const welcomeScreen = document.getElementById('welcome-screen');
    const historyList = document.getElementById('history-list');
    const historyEmpty = document.getElementById('history-empty');
    const documentsBtn = document.getElementById('documents-btn');
    const documentModal = document.getElementById('document-modal');
    const closeDocumentModal = document.getElementById('close-document-modal');
    const docLockPanel = document.getElementById('doc-lock-panel');
    const docManagerPanel = document.getElementById('doc-manager-panel');
    const documentPassword = document.getElementById('document-password');
    const unlockDocumentsBtn = document.getElementById('unlock-documents-btn');
    const documentLockMessage = document.getElementById('document-lock-message');
    const fileInput = document.getElementById('file-input');
    const uploadBtn = document.getElementById('upload-btn');
    const uploadStatus = document.getElementById('upload-status');
    const fileList = document.getElementById('file-list');
    const fileEmpty = document.getElementById('file-empty');
    const refreshFilesBtn = document.getElementById('refresh-files-btn');
    const themeToggle = document.getElementById('theme-toggle');
    const themeLabel = document.getElementById('theme-label');

    const STORAGE_KEY = 'documind_conversations';
    const THEME_KEY = 'documind_theme';
    const DOC_AUTH_KEY = 'documind_documents_unlocked';
    const DOCUMENT_PASSWORD = 'documind';

    let isProcessing = false;
    let conversations = loadConversations();
    let currentId = null;
    let documentsUnlocked = sessionStorage.getItem(DOC_AUTH_KEY) === 'true';

    function loadConversations() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
        } catch (e) {
            return [];
        }
    }

    function saveConversations() {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations));
    }

    function getConversation(id) {
        return conversations.find(c => c.id === id);
    }

    function createConversation() {
        const conv = {
            id: 'conv-' + Date.now(),
            title: '新对话',
            messages: [],
            updatedAt: Date.now()
        };
        conversations.unshift(conv);
        currentId = conv.id;
        saveConversations();
        renderHistory();
        return conv;
    }

    function deriveTitle(text) {
        const title = text.trim().replace(/\s+/g, ' ');
        return title.length > 24 ? title.slice(0, 24) + '…' : title;
    }

    function persistMessage(role, text) {
        let conv = getConversation(currentId);
        if (!conv) conv = createConversation();

        conv.messages.push({ role, text });
        if (role === 'user' && conv.messages.filter(m => m.role === 'user').length === 1) {
            conv.title = deriveTitle(text);
        }
        conv.updatedAt = Date.now();
        conversations = conversations.filter(c => c.id !== conv.id);
        conversations.unshift(conv);
        saveConversations();
        renderHistory();
    }

    function renderHistory() {
        historyList.innerHTML = '';
        if (conversations.length === 0) {
            historyEmpty.style.display = 'block';
            return;
        }

        historyEmpty.style.display = 'none';
        conversations.forEach(conv => {
            const li = document.createElement('li');
            li.className = 'history-item' + (conv.id === currentId ? ' active' : '');
            li.dataset.id = conv.id;

            const icon = document.createElement('i');
            icon.className = 'far fa-comment';

            const title = document.createElement('span');
            title.className = 'history-title';
            title.textContent = conv.title || '新对话';

            const del = document.createElement('button');
            del.className = 'delete-history-btn';
            del.type = 'button';
            del.setAttribute('aria-label', '删除对话');
            del.innerHTML = '<i class="fas fa-trash-alt"></i>';
            del.addEventListener('click', (e) => {
                e.stopPropagation();
                deleteConversation(conv.id);
            });

            li.appendChild(icon);
            li.appendChild(title);
            li.appendChild(del);
            li.addEventListener('click', () => openConversation(conv.id));
            historyList.appendChild(li);
        });
    }

    function clearChatDom() {
        chatMessages.querySelectorAll('.message-row').forEach(m => m.remove());
    }

    function openConversation(id) {
        const conv = getConversation(id);
        if (!conv) return;

        currentId = id;
        clearChatDom();
        if (welcomeScreen) welcomeScreen.style.display = 'none';

        conv.messages.forEach(m => {
            if (m.role === 'assistant') {
                appendMessage('assistant', formatResponse(m.text), null, true);
            } else {
                appendMessage('user', m.text);
            }
        });
        renderHistory();
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function deleteConversation(id) {
        if (!confirm('确定要删除这条对话记录吗？')) return;

        conversations = conversations.filter(c => c.id !== id);
        saveConversations();
        if (currentId === id) {
            currentId = null;
            clearChatDom();
            if (welcomeScreen) welcomeScreen.style.display = 'block';
        }
        renderHistory();
    }

    function applyTheme(theme) {
        const nextTheme = theme === 'light' ? 'light' : 'dark';
        document.documentElement.dataset.theme = nextTheme;
        localStorage.setItem(THEME_KEY, nextTheme);

        const icon = themeToggle.querySelector('i');
        if (nextTheme === 'dark') {
            icon.className = 'fas fa-sun';
            themeLabel.textContent = '日间模式';
        } else {
            icon.className = 'fas fa-moon';
            themeLabel.textContent = '夜间模式';
        }
    }

    function showDocumentModal() {
        documentModal.classList.remove('hidden');
        documentModal.setAttribute('aria-hidden', 'false');
        renderDocumentAuthState();
        if (documentsUnlocked) {
            loadFileList();
        } else {
            setLockMessage('');
            setTimeout(() => documentPassword.focus(), 50);
        }
    }

    function hideDocumentModal() {
        documentModal.classList.add('hidden');
        documentModal.setAttribute('aria-hidden', 'true');
        documentPassword.value = '';
        setLockMessage('');
    }

    function renderDocumentAuthState() {
        docLockPanel.classList.toggle('hidden', documentsUnlocked);
        docManagerPanel.classList.toggle('hidden', !documentsUnlocked);
    }

    function setLockMessage(message, type = '') {
        documentLockMessage.textContent = message;
        documentLockMessage.dataset.type = type;
    }

    function setUploadStatus(message, type = '') {
        uploadStatus.textContent = message;
        uploadStatus.dataset.type = type;
    }

    function unlockDocuments() {
        if (documentPassword.value === DOCUMENT_PASSWORD) {
            documentsUnlocked = true;
            sessionStorage.setItem(DOC_AUTH_KEY, 'true');
            documentPassword.value = '';
            setLockMessage('');
            renderDocumentAuthState();
            loadFileList();
            return;
        }

        setLockMessage('密码不正确', 'error');
        documentPassword.select();
    }

    async function loadFileList() {
        try {
            const response = await fetch('/api/files/list');
            if (!response.ok) throw new Error('Failed to load files');

            const files = await response.json();
            fileList.innerHTML = '';
            fileEmpty.style.display = files.length === 0 ? 'block' : 'none';

            files.forEach(file => {
                const li = document.createElement('li');
                li.className = 'file-item';

                const icon = document.createElement('i');
                icon.className = getFileIcon(file);

                const name = document.createElement('span');
                name.className = 'file-name';
                name.textContent = file;

                const del = document.createElement('button');
                del.className = 'delete-file-btn';
                del.type = 'button';
                del.setAttribute('aria-label', `删除 ${file}`);
                del.innerHTML = '<i class="fas fa-trash-alt"></i>';
                del.addEventListener('click', () => deleteFile(file));

                li.appendChild(icon);
                li.appendChild(name);
                li.appendChild(del);
                fileList.appendChild(li);
            });
        } catch (error) {
            console.error('Error loading files:', error);
            fileList.innerHTML = '';
            fileEmpty.style.display = 'block';
            fileEmpty.textContent = '文档列表加载失败';
        }
    }

    function getFileIcon(filename) {
        const lower = filename.toLowerCase();
        if (lower.endsWith('.pdf')) return 'far fa-file-pdf file-pdf';
        if (lower.endsWith('.doc') || lower.endsWith('.docx')) return 'far fa-file-word file-word';
        if (lower.endsWith('.xls') || lower.endsWith('.xlsx')) return 'far fa-file-excel file-excel';
        if (lower.endsWith('.ppt') || lower.endsWith('.pptx')) return 'far fa-file-powerpoint file-ppt';
        return 'far fa-file-alt file-text';
    }

    async function deleteFile(filename) {
        if (!confirm(`确定要删除 ${filename} 吗？`)) return;

        try {
            const response = await fetch(`/api/files/${encodeURIComponent(filename)}`, { method: 'DELETE' });
            if (!response.ok) throw new Error('Delete failed');
            setUploadStatus(`已删除 ${filename}`, 'success');
            loadFileList();
        } catch (error) {
            console.error('Error deleting file:', error);
            setUploadStatus('删除失败', 'error');
        }
    }

    async function uploadSelectedFile() {
        if (fileInput.files.length === 0) return;

        const file = fileInput.files[0];
        const formData = new FormData();
        formData.append('file', file);

        uploadBtn.disabled = true;
        setUploadStatus(`正在解析并构建索引: ${file.name}`);

        try {
            const response = await fetch('/api/files/upload', {
                method: 'POST',
                body: formData
            });
            const data = await response.json().catch(() => ({}));

            if (response.ok) {
                setUploadStatus(`"${file.name}" 已入库`, 'success');
                loadFileList();
            } else {
                setUploadStatus(data.error || '文件上传或索引失败', 'error');
            }
        } catch (error) {
            console.error('Error uploading file:', error);
            setUploadStatus('连接服务时出错', 'error');
        } finally {
            uploadBtn.disabled = false;
            fileInput.value = '';
        }
    }

    userInput.addEventListener('input', () => {
        userInput.style.height = 'auto';
        userInput.style.height = Math.min(userInput.scrollHeight, 200) + 'px';
        sendBtn.disabled = userInput.value.trim() === '' || isProcessing;
    });

    const sendMessage = async () => {
        const message = userInput.value.trim();
        if (!message || isProcessing) return;

        isProcessing = true;
        sendBtn.disabled = true;

        if (!currentId || !getConversation(currentId)) createConversation();
        if (welcomeScreen) welcomeScreen.style.display = 'none';

        appendMessage('user', message);
        persistMessage('user', message);

        userInput.value = '';
        userInput.style.height = 'auto';

        const assistantRow = appendMessage('assistant', '<div class="typing"><span></span><span></span><span></span></div>', null, true);
        const textDiv = assistantRow.querySelector('.message-text');
        let assistantText = '';

        try {
            await streamChatResponse(message, currentId, {
                onToken: (token) => {
                    assistantText += token;
                    textDiv.innerHTML = formatResponse(assistantText);
                    chatMessages.scrollTop = chatMessages.scrollHeight;
                },
                onError: (errorMessage) => {
                    throw new Error(errorMessage);
                }
            });

            if (assistantText.trim() !== '') {
                persistMessage('assistant', assistantText);
            }
        } catch (error) {
            console.error('Error:', error);
            if (assistantText.trim() !== '') {
                persistMessage('assistant', assistantText);
            } else {
                textDiv.textContent = '无法建立连接，请检查后端服务是否启动。';
                textDiv.classList.add('message-error');
            }
        } finally {
            isProcessing = false;
            sendBtn.disabled = userInput.value.trim() === '';
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
    };

    async function streamChatResponse(message, sessionId, handlers) {
        const response = await fetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message, sessionId })
        });

        if (!response.ok || !response.body) {
            throw new Error('Stream request failed');
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { value, done } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const events = buffer.split(/\r?\n\r?\n/);
            buffer = events.pop() || '';

            events.forEach(rawEvent => handleStreamEvent(rawEvent, handlers));
        }

        buffer += decoder.decode();
        if (buffer.trim() !== '') {
            handleStreamEvent(buffer, handlers);
        }
    }

    function handleStreamEvent(rawEvent, handlers) {
        const event = parseSseEvent(rawEvent);
        if (!event) return;

        if (event.name === 'token') {
            handlers.onToken(event.data);
        }
        if (event.name === 'error') {
            handlers.onError(event.data || '处理请求时发生错误');
        }
    }

    function parseSseEvent(rawEvent) {
        let name = 'message';
        const data = [];

        rawEvent.split(/\r?\n/).forEach(line => {
            if (line.startsWith('event:')) {
                name = line.slice(6).trim();
            } else if (line.startsWith('data:')) {
                data.push(line.slice(5).replace(/^ /, ''));
            }
        });

        if (data.length === 0 && name === 'message') return null;
        return { name, data: data.join('\n') };
    }

    sendBtn.addEventListener('click', sendMessage);
    userInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    });

    newChatBtn.addEventListener('click', () => {
        currentId = null;
        if (welcomeScreen) welcomeScreen.style.display = 'block';
        clearChatDom();
        renderHistory();
    });

    documentsBtn.addEventListener('click', showDocumentModal);
    closeDocumentModal.addEventListener('click', hideDocumentModal);
    documentModal.addEventListener('click', (event) => {
        if (event.target === documentModal) hideDocumentModal();
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !documentModal.classList.contains('hidden')) {
            hideDocumentModal();
        }
    });
    unlockDocumentsBtn.addEventListener('click', unlockDocuments);
    documentPassword.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') unlockDocuments();
    });
    refreshFilesBtn.addEventListener('click', loadFileList);
    uploadBtn.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', uploadSelectedFile);
    themeToggle.addEventListener('click', () => {
        const currentTheme = document.documentElement.dataset.theme === 'light' ? 'light' : 'dark';
        applyTheme(currentTheme === 'dark' ? 'light' : 'dark');
    });
    document.querySelectorAll('.prompt-chip').forEach(button => {
        button.addEventListener('click', () => {
            userInput.value = button.dataset.prompt || '';
            userInput.dispatchEvent(new Event('input'));
            userInput.focus();
        });
    });

    function appendMessage(role, text, id = null, isHtml = false) {
        const row = document.createElement('div');
        row.className = `message-row ${role}`;
        if (id) row.id = id;

        const contentHtml = isHtml ? text : escapeHtml(text).replace(/\n/g, '<br>');
        row.innerHTML = `
            <div class="message-content">
                <div class="message-text">${contentHtml}</div>
            </div>
        `;

        chatMessages.appendChild(row);
        chatMessages.scrollTop = chatMessages.scrollHeight;
        return row;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function formatResponse(text) {
        const codeBlocks = [];
        let html = escapeHtml(text).replace(/```([\s\S]*?)```/g, (_, code) => {
            const key = `__DOCUMIND_CODE_BLOCK_${codeBlocks.length}__`;
            codeBlocks.push(`<div class="code-block"><pre><code>${code.trim()}</code></pre></div>`);
            return key;
        });

        html = html
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\n/g, '<br>');

        codeBlocks.forEach((block, index) => {
            html = html.replace(`__DOCUMIND_CODE_BLOCK_${index}__`, block);
        });

        return html;
    }

    applyTheme(localStorage.getItem(THEME_KEY) || 'dark');
    renderHistory();
});
