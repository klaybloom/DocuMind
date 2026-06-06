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
    const fileInput = document.getElementById('file-input');
    const uploadBtn = document.getElementById('upload-btn');
    const uploadStatus = document.getElementById('upload-status');
    const fileList = document.getElementById('file-list');
    const fileEmpty = document.getElementById('file-empty');
    const statusList = document.getElementById('status-list');
    const gapList = document.getElementById('gap-list');
    const gapEmpty = document.getElementById('gap-empty');
    const auditList = document.getElementById('audit-list');
    const auditEmpty = document.getElementById('audit-empty');
    const refreshFilesBtn = document.getElementById('refresh-files-btn');
    const knowledgeBaseSelect = document.getElementById('knowledge-base-select');
    const documentKnowledgeBaseSelect = document.getElementById('document-knowledge-base-select');
    const knowledgeBaseInput = document.getElementById('knowledge-base-input');
    const documentOwnerInput = document.getElementById('document-owner-input');
    const generateFaqBtn = document.getElementById('generate-faq-btn');
    const faqDraft = document.getElementById('faq-draft');
    const themeToggle = document.getElementById('theme-toggle');
    const themeLabel = document.getElementById('theme-label');

    const STORAGE_KEY = 'documind_conversations';
    const THEME_KEY = 'documind_theme';
    const KNOWLEDGE_BASE_KEY = 'documind_knowledge_base';
    const DEFAULT_KNOWLEDGE_BASE = 'default';

    let isProcessing = false;
    let conversations = loadConversations();
    let currentId = null;
    let currentKnowledgeBase = localStorage.getItem(KNOWLEDGE_BASE_KEY) || DEFAULT_KNOWLEDGE_BASE;

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

    async function deleteConversation(id) {
        if (!confirm('确定要删除这条对话记录吗？')) return;

        conversations = conversations.filter(c => c.id !== id);
        saveConversations();
        if (currentId === id) {
            currentId = null;
            clearChatDom();
            if (welcomeScreen) welcomeScreen.style.display = 'block';
        }
        renderHistory();
        await clearServerSession(id);
    }

    async function clearServerSession(id) {
        if (!id) return;
        try {
            await fetch(`/api/chat/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' });
        } catch (error) {
            console.error('Error clearing server session:', error);
        }
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
        syncKnowledgeBaseControls();
        loadFileList();
        loadKnowledgeBaseStatus();
        loadKnowledgeGaps();
        loadAuditEvents();
    }

    function hideDocumentModal() {
        documentModal.classList.add('hidden');
        documentModal.setAttribute('aria-hidden', 'true');
    }

    function setUploadStatus(message, type = '') {
        uploadStatus.textContent = message;
        uploadStatus.dataset.type = type;
    }

    async function loadKnowledgeBases() {
        try {
            const response = await fetch('/api/files/knowledge-bases');
            if (!response.ok) return;

            const knowledgeBases = await response.json();
            renderKnowledgeBaseOptions(knowledgeBases.length === 0 ? [DEFAULT_KNOWLEDGE_BASE] : knowledgeBases);
        } catch (error) {
            console.error('Error loading knowledge bases:', error);
            renderKnowledgeBaseOptions([DEFAULT_KNOWLEDGE_BASE]);
        }
    }

    function renderKnowledgeBaseOptions(knowledgeBases) {
        if (!knowledgeBases.includes(currentKnowledgeBase)) {
            currentKnowledgeBase = knowledgeBases[0] || DEFAULT_KNOWLEDGE_BASE;
            localStorage.setItem(KNOWLEDGE_BASE_KEY, currentKnowledgeBase);
        }

        [knowledgeBaseSelect, documentKnowledgeBaseSelect].forEach(select => {
            select.innerHTML = '';
            knowledgeBases.forEach(kb => {
                const option = document.createElement('option');
                option.value = kb;
                option.textContent = knowledgeBaseLabel(kb);
                select.appendChild(option);
            });
            select.value = currentKnowledgeBase;
        });
    }

    function syncKnowledgeBaseControls() {
        knowledgeBaseSelect.value = currentKnowledgeBase;
        documentKnowledgeBaseSelect.value = currentKnowledgeBase;
    }

    function setCurrentKnowledgeBase(value) {
        currentKnowledgeBase = value || DEFAULT_KNOWLEDGE_BASE;
        localStorage.setItem(KNOWLEDGE_BASE_KEY, currentKnowledgeBase);
        faqDraft.hidden = true;
        faqDraft.value = '';
        syncKnowledgeBaseControls();
    }

    function selectedUploadKnowledgeBase() {
        const newKnowledgeBase = knowledgeBaseInput.value.trim();
        return newKnowledgeBase || documentKnowledgeBaseSelect.value || currentKnowledgeBase;
    }

    function knowledgeBaseLabel(value) {
        return value === DEFAULT_KNOWLEDGE_BASE ? '默认知识库' : value;
    }

    async function loadFileList() {
        try {
            const params = new URLSearchParams({ knowledgeBase: currentKnowledgeBase });
            const response = await fetch(`/api/files/list?${params}`);
            if (response.status === 401 || response.status === 403) {
                fileList.innerHTML = '';
                fileEmpty.style.display = 'block';
                fileEmpty.textContent = '当前账号没有文档管理权限';
                setUploadStatus('请使用管理员账号登录后管理文档', 'error');
                statusList.innerHTML = '';
                gapList.innerHTML = '';
                auditList.innerHTML = '';
                gapEmpty.style.display = 'none';
                auditEmpty.style.display = 'none';
                return;
            }
            if (!response.ok) throw new Error('Failed to load files');

            const files = await response.json();
            fileList.innerHTML = '';
            fileEmpty.style.display = files.length === 0 ? 'block' : 'none';

            files.forEach(file => {
                const li = document.createElement('li');
                li.className = 'file-item';

                const icon = document.createElement('i');
                icon.className = getFileIcon(file.fileName);

                const meta = document.createElement('div');
                meta.className = 'file-meta';

                const name = document.createElement('span');
                name.className = 'file-name';
                name.textContent = file.fileName;

                const status = document.createElement('span');
                status.className = `file-status ${statusClass(file.indexStatus)}`;
                status.textContent = `${statusText(file)}${file.owner ? ' · 负责人 ' + file.owner : ''}`;

                meta.appendChild(name);
                meta.appendChild(status);

                const download = document.createElement('button');
                download.className = 'download-file-btn';
                download.type = 'button';
                download.setAttribute('aria-label', `下载 ${file.fileName}`);
                download.innerHTML = '<i class="fas fa-download"></i>';
                download.addEventListener('click', () => downloadFile(file.fileName, file.knowledgeBase));

                const del = document.createElement('button');
                del.className = 'delete-file-btn';
                del.type = 'button';
                del.setAttribute('aria-label', `删除 ${file.fileName}`);
                del.innerHTML = '<i class="fas fa-trash-alt"></i>';
                del.addEventListener('click', () => deleteFile(file.fileName, file.knowledgeBase));

                li.appendChild(icon);
                li.appendChild(meta);
                li.appendChild(download);
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

    async function loadKnowledgeBaseStatus() {
        try {
            const response = await fetch('/api/files/status');
            if (!response.ok) {
                statusList.innerHTML = '';
                return;
            }

            const statuses = await response.json();
            statusList.innerHTML = '';
            statuses.forEach(status => {
                const li = document.createElement('li');
                li.className = 'status-item';
                li.innerHTML = `
                    <span>${escapeHtml(knowledgeBaseLabel(status.knowledgeBase))}</span>
                    <small>${status.indexedFiles}/${status.totalFiles} 已索引 · ${status.failedFiles} 失败 · ${status.staleFiles} 过期 · ${status.knowledgeGaps} 缺口</small>
                `;
                statusList.appendChild(li);
            });
        } catch (error) {
            console.error('Error loading knowledge base status:', error);
            statusList.innerHTML = '';
        }
    }

    async function loadKnowledgeGaps() {
        try {
            const params = new URLSearchParams({ knowledgeBase: currentKnowledgeBase });
            const response = await fetch(`/api/files/gaps?${params}`);
            if (!response.ok) {
                gapList.innerHTML = '';
                gapEmpty.style.display = 'none';
                return;
            }

            const gaps = await response.json();
            gapList.innerHTML = '';
            gapEmpty.style.display = gaps.length === 0 ? 'block' : 'none';
            gaps.slice(0, 8).forEach(gap => {
                const li = document.createElement('li');
                li.className = 'gap-item';
                const question = document.createElement('span');
                question.textContent = gap.question;
                const detail = document.createElement('small');
                detail.textContent = `${gap.occurrences || 1} 次 · ${formatDate(gap.lastAskedAt)}`;
                const resolve = document.createElement('button');
                resolve.className = 'resolve-gap-btn';
                resolve.type = 'button';
                resolve.title = '标记已处理';
                resolve.setAttribute('aria-label', `标记已处理: ${gap.question}`);
                resolve.innerHTML = '<i class="fas fa-check"></i>';
                resolve.addEventListener('click', () => resolveKnowledgeGap(gap.id, gap.knowledgeBase));
                li.appendChild(question);
                li.appendChild(detail);
                li.appendChild(resolve);
                gapList.appendChild(li);
            });
        } catch (error) {
            console.error('Error loading knowledge gaps:', error);
            gapList.innerHTML = '';
            gapEmpty.style.display = 'block';
            gapEmpty.textContent = '知识缺口加载失败';
        }
    }

    async function resolveKnowledgeGap(gapId, knowledgeBase) {
        if (!gapId) return;

        try {
            const params = new URLSearchParams({ knowledgeBase: knowledgeBase || currentKnowledgeBase });
            const response = await fetch(`/api/files/gaps/${encodeURIComponent(gapId)}?${params}`, { method: 'DELETE' });
            if (response.status === 401 || response.status === 403) {
                setUploadStatus('当前账号没有处理知识缺口权限', 'error');
                return;
            }
            if (!response.ok) throw new Error('Resolve gap failed');
            setUploadStatus('知识缺口已标记为已处理', 'success');
            loadKnowledgeGaps();
            loadKnowledgeBaseStatus();
            loadAuditEvents();
        } catch (error) {
            console.error('Error resolving knowledge gap:', error);
            setUploadStatus('知识缺口处理失败', 'error');
        }
    }

    async function loadAuditEvents() {
        try {
            const response = await fetch('/api/files/audit?limit=12');
            if (!response.ok) {
                auditList.innerHTML = '';
                auditEmpty.style.display = 'none';
                return;
            }

            const events = await response.json();
            auditList.innerHTML = '';
            auditEmpty.style.display = events.length === 0 ? 'block' : 'none';
            events.forEach(event => {
                const li = document.createElement('li');
                li.className = 'audit-item';
                const target = event.fileName || knowledgeBaseLabel(event.knowledgeBase || DEFAULT_KNOWLEDGE_BASE);
                li.innerHTML = `
                    <span>${escapeHtml(actionText(event.action))}</span>
                    <small>${escapeHtml(event.actor || 'unknown')} · ${escapeHtml(target)} · ${formatDateTime(event.timestamp)}</small>
                `;
                auditList.appendChild(li);
            });
        } catch (error) {
            console.error('Error loading audit events:', error);
            auditList.innerHTML = '';
            auditEmpty.style.display = 'block';
            auditEmpty.textContent = '审计记录加载失败';
        }
    }

    function actionText(action) {
        const labels = {
            CHAT_REQUEST: '发起问答',
            UPLOAD_DOCUMENT: '上传文档',
            DOWNLOAD_DOCUMENT: '下载文档',
            DELETE_DOCUMENT: '删除文档',
            REFRESH_INDEX: '刷新索引',
            GENERATE_FAQ_DRAFT: '生成 FAQ 草稿',
            RESOLVE_KNOWLEDGE_GAP: '处理知识缺口'
        };
        return labels[action] || action || '未知操作';
    }

    function getFileIcon(filename) {
        const lower = filename.toLowerCase();
        if (lower.endsWith('.pdf')) return 'far fa-file-pdf file-pdf';
        if (lower.endsWith('.doc') || lower.endsWith('.docx')) return 'far fa-file-word file-word';
        if (lower.endsWith('.xls') || lower.endsWith('.xlsx')) return 'far fa-file-excel file-excel';
        if (lower.endsWith('.ppt') || lower.endsWith('.pptx')) return 'far fa-file-powerpoint file-ppt';
        return 'far fa-file-alt file-text';
    }

    function statusClass(status) {
        if (status === 'INDEXED') return 'indexed';
        if (status === 'FAILED') return 'failed';
        if (status === 'INDEXING') return 'indexing';
        return 'pending';
    }

    function statusText(file) {
        const stale = file.stale ? ` · 可能过期 ${file.daysSinceUpload || 0} 天` : '';
        if (file.indexStatus === 'INDEXED') return `已索引 · ${file.chunkCount || 0} 片段${stale}`;
        if (file.indexStatus === 'FAILED') return `索引失败${file.error ? ' · ' + file.error : ''}`;
        if (file.indexStatus === 'INDEXING') return `索引中${stale}`;
        return `待索引${stale}`;
    }

    function formatDate(value) {
        if (!value) return '未知时间';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '未知时间';
        return date.toLocaleDateString('zh-CN');
    }

    function formatDateTime(value) {
        if (!value) return '未知时间';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '未知时间';
        return date.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });
    }

    function downloadFile(filename, knowledgeBase) {
        const params = new URLSearchParams({ knowledgeBase: knowledgeBase || currentKnowledgeBase });
        window.location.href = `/api/files/${encodeURIComponent(filename)}/download?${params}`;
        setTimeout(loadAuditEvents, 800);
    }

    async function deleteFile(filename, knowledgeBase) {
        if (!confirm(`确定要删除 ${filename} 吗？`)) return;

        try {
            const params = new URLSearchParams({ knowledgeBase: knowledgeBase || currentKnowledgeBase });
            const response = await fetch(`/api/files/${encodeURIComponent(filename)}?${params}`, { method: 'DELETE' });
            if (response.status === 401 || response.status === 403) {
                setUploadStatus('当前账号没有删除权限', 'error');
                return;
            }
            if (response.status === 404) {
                setUploadStatus('文件不存在或已被删除', 'error');
                loadFileList();
                return;
            }
            if (!response.ok) throw new Error('Delete failed');
            setUploadStatus(`已删除 ${filename}`, 'success');
            loadFileList();
            loadAuditEvents();
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
        formData.append('knowledgeBase', selectedUploadKnowledgeBase());
        if (documentOwnerInput.value.trim() !== '') {
            formData.append('owner', documentOwnerInput.value.trim());
        }

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
                setCurrentKnowledgeBase(selectedUploadKnowledgeBase());
                knowledgeBaseInput.value = '';
                documentOwnerInput.value = '';
                await loadKnowledgeBases();
                loadFileList();
                loadKnowledgeBaseStatus();
                loadKnowledgeGaps();
                loadAuditEvents();
            } else if (response.status === 401 || response.status === 403) {
                setUploadStatus('当前账号没有上传权限', 'error');
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
                textDiv.textContent = error.message || '无法建立连接，请检查后端服务是否启动。';
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
            body: JSON.stringify({ message, sessionId, knowledgeBase: currentKnowledgeBase })
        });

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.error || '处理请求时发生错误');
        }
        if (!response.body) {
            throw new Error('无法建立连接，请检查后端服务是否启动。');
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

    async function generateFaqDraft() {
        try {
            const params = new URLSearchParams({ knowledgeBase: currentKnowledgeBase });
            const response = await fetch(`/api/files/faq-draft?${params}`);
            if (response.status === 401 || response.status === 403) {
                setUploadStatus('当前账号没有生成 FAQ 草稿权限', 'error');
                return;
            }
            if (!response.ok) throw new Error('FAQ draft failed');

            const draft = await response.json();
            faqDraft.hidden = false;
            faqDraft.value = draft.markdown || '';
            loadAuditEvents();
        } catch (error) {
            console.error('Error generating FAQ draft:', error);
            setUploadStatus('FAQ 草稿生成失败', 'error');
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
    refreshFilesBtn.addEventListener('click', () => {
        loadFileList();
        loadKnowledgeBaseStatus();
        loadKnowledgeGaps();
        loadAuditEvents();
    });
    knowledgeBaseSelect.addEventListener('change', () => {
        setCurrentKnowledgeBase(knowledgeBaseSelect.value);
        if (!documentModal.classList.contains('hidden')) {
            loadFileList();
            loadKnowledgeGaps();
            loadAuditEvents();
        }
    });
    documentKnowledgeBaseSelect.addEventListener('change', () => {
        setCurrentKnowledgeBase(documentKnowledgeBaseSelect.value);
        loadFileList();
        loadKnowledgeGaps();
        loadAuditEvents();
    });
    uploadBtn.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', uploadSelectedFile);
    generateFaqBtn.addEventListener('click', generateFaqDraft);
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
    loadKnowledgeBases();
    renderHistory();
});
