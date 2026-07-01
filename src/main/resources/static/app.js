import {
    createAuthFetch,
    fetchCurrentUser,
    streamChatResponse as streamChatResponseApi
} from './api.js';
import {
    escapeHtml,
    formatResponse,
    getFileColorClass,
    getFileTypeLabel,
    knowledgeBaseLabel,
    truncateFilename
} from './utils.js';

document.addEventListener('DOMContentLoaded', () => {
    // 登录相关 DOM
    const loginScreen = document.getElementById('login-screen');
    const loginForm = document.getElementById('login-form');
    const loginUsername = document.getElementById('login-username');
    const loginPassword = document.getElementById('login-password');
    const loginError = document.getElementById('login-error');
    const loginBtn = document.getElementById('login-btn');
    const appRoot = document.getElementById('app-root');
    const userInfo = document.getElementById('user-info');
    const userAvatar = document.getElementById('user-avatar');
    const userName = document.getElementById('user-name');
    const userRole = document.getElementById('user-role');
    const logoutBtn = document.getElementById('logout-btn');

    const chatMessages = document.getElementById('chat-messages');
    const userInput = document.getElementById('user-input');
    const sendBtn = document.getElementById('send-btn');
    const newChatBtn = document.getElementById('new-chat-btn');
    const welcomeScreen = document.getElementById('welcome-screen');
    const historyList = document.getElementById('history-list');
    const historyEmpty = document.getElementById('history-empty');
    const adminConsoleBtn = document.getElementById('admin-console-btn');
    const knowledgeBaseSelect = document.getElementById('knowledge-base-select');
    const themeToggle = document.getElementById('theme-toggle');
    const themeLabel = document.getElementById('theme-label');
    const debugToggle = document.getElementById('debug-toggle');
    const mobileSidebarClose = document.getElementById('mobile-sidebar-close');

    const STORAGE_KEY = 'documind_conversations';
    const THEME_KEY = 'documind_theme';
    const KNOWLEDGE_BASE_KEY = 'documind_knowledge_base';
    const AUTH_KEY = 'documind_auth';
    const DEBUG_KEY = 'documind_debug_mode';
    const DEFAULT_KNOWLEDGE_BASE = 'default';
    let debugMode = localStorage.getItem(DEBUG_KEY) === 'true';

    // Lucide icon helper
    function refreshIcons() {
        if (typeof lucide !== 'undefined') {
            lucide.createIcons();
        }
    }
    refreshIcons();

    // 侧边栏收起/展开
    const sidebarToggle = document.getElementById('sidebar-toggle');
    const SIDEBAR_KEY = 'documind_sidebar_collapsed';
    if (sidebarToggle) {
        const storedSidebarState = localStorage.getItem(SIDEBAR_KEY);
        const preferCollapsedSidebar = storedSidebarState === 'true'
            || (storedSidebarState === null && window.matchMedia('(max-width: 840px)').matches);
        if (preferCollapsedSidebar) {
            appRoot.classList.add('sidebar-collapsed');
        }
        sidebarToggle.addEventListener('click', () => {
            appRoot.classList.toggle('sidebar-collapsed');
            localStorage.setItem(SIDEBAR_KEY,
                appRoot.classList.contains('sidebar-collapsed') ? 'true' : 'false');
        });
    }
    if (mobileSidebarClose) {
        mobileSidebarClose.addEventListener('click', () => {
            appRoot.classList.add('sidebar-collapsed');
            localStorage.setItem(SIDEBAR_KEY, 'true');
        });
    }

    // Initialize debug toggle state
    if (debugToggle) {
        debugToggle.checked = debugMode;
        debugToggle.addEventListener('change', () => {
            debugMode = debugToggle.checked;
            localStorage.setItem(DEBUG_KEY, debugMode ? 'true' : 'false');
        });
    }

    // 认证状态
    let authCredentials = null;  // Base64 编码的 "username:password"

    function saveAuth(username, password) {
        authCredentials = btoa(username + ':' + password);
        sessionStorage.setItem(AUTH_KEY, authCredentials);
    }

    function clearAuth() {
        authCredentials = null;
        sessionStorage.removeItem(AUTH_KEY);
    }

    function restoreAuth() {
        authCredentials = sessionStorage.getItem(AUTH_KEY);
        return authCredentials;
    }

    async function verifyAuth() {
        if (!authCredentials) return false;
        try {
            const resp = await fetchCurrentUser(authCredentials);
            if (resp.ok) {
                const data = await resp.json();
                showApp(data);
                return true;
            }
        } catch (e) {
            // 忽略网络错误
        }
        clearAuth();
        return false;
    }

    function showApp(user) {
        loginScreen.classList.add('hidden');
        appRoot.classList.remove('hidden');
        userInfo.classList.remove('hidden');
        userName.textContent = user.username;
        userAvatar.textContent = (user.username || '?')[0].toUpperCase();
        const roles = user.roles || [];
        if (roles.includes('ADMIN')) {
            userRole.textContent = '管理员';
        } else if (user.canManageKnowledgeBases) {
            userRole.textContent = '知识库负责人';
        } else {
            userRole.textContent = '普通用户';
        }
        if (adminConsoleBtn) {
            if (roles.includes('ADMIN') || user.canManageKnowledgeBases) {
                adminConsoleBtn.classList.remove('hidden');
            } else {
                adminConsoleBtn.classList.add('hidden');
            }
        }
    }

    function openAdminConsole() {
        window.location.href = 'admin.html';
    }

    function showLogin(errorMsg) {
        clearAuth();
        appRoot.classList.add('hidden');
        userInfo.classList.add('hidden');
        loginScreen.classList.remove('hidden');
        if (errorMsg) {
            loginError.textContent = errorMsg;
            loginError.classList.remove('hidden');
        } else {
            loginError.classList.add('hidden');
        }
        loginPassword.value = '';
        loginUsername.focus();
    }

    const authFetch = createAuthFetch(() => authCredentials, () => {
        showLogin('登录已过期，请重新登录');
    });

    // 登录表单提交
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = loginUsername.value.trim();
        const password = loginPassword.value;
        if (!username || !password) return;

        loginBtn.disabled = true;
        loginError.classList.add('hidden');

        const tempCreds = btoa(username + ':' + password);
        try {
            const resp = await fetchCurrentUser(tempCreds);
            if (resp.ok) {
                const user = await resp.json();
                saveAuth(username, password);
                showApp(user);
                loadKnowledgeBases();
                renderHistory();
            } else {
                loginError.textContent = '用户名或密码错误';
                loginError.classList.remove('hidden');
            }
        } catch (err) {
            loginError.textContent = '连接服务器失败';
            loginError.classList.remove('hidden');
        } finally {
            loginBtn.disabled = false;
        }
    });

    // 退出登录
    logoutBtn.addEventListener('click', () => {
        if (!confirm('确定要退出登录吗？')) return;
        showLogin();
    });

    let isProcessing = false;
    let conversations = loadConversations();
    let currentId = null;
    let currentKnowledgeBases = loadSelectedKnowledgeBases();

    function loadSelectedKnowledgeBases() {
        const raw = localStorage.getItem(KNOWLEDGE_BASE_KEY);
        if (!raw) return [DEFAULT_KNOWLEDGE_BASE];
        try {
            const parsed = JSON.parse(raw);
            if (Array.isArray(parsed)) {
                const values = parsed.map(value => String(value || '').trim()).filter(Boolean);
                return values.length > 0 ? values : [DEFAULT_KNOWLEDGE_BASE];
            }
        } catch (e) {
            // 兼容旧版单值存储
        }
        const values = raw.split(',').map(value => value.trim()).filter(Boolean);
        return values.length > 0 ? values : [DEFAULT_KNOWLEDGE_BASE];
    }

    function saveSelectedKnowledgeBases(values) {
        const normalized = Array.from(new Set((values || [])
            .map(value => String(value || '').trim())
            .filter(Boolean)));
        currentKnowledgeBases = normalized.length > 0 ? normalized : [DEFAULT_KNOWLEDGE_BASE];
        localStorage.setItem(KNOWLEDGE_BASE_KEY, JSON.stringify(currentKnowledgeBases));
    }

    function selectedKnowledgeBaseList() {
        return currentKnowledgeBases.length > 0 ? currentKnowledgeBases : [DEFAULT_KNOWLEDGE_BASE];
    }

    function selectedKnowledgeBaseValue() {
        return selectedKnowledgeBaseList().join(',');
    }

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

    function createConversation(id = 'conv-' + Date.now()) {
        const conv = {
            id,
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

    function persistMessage(conversationId, role, text, meta = {}) {
        conversations = loadConversations();
        let conv = getConversation(conversationId);
        if (!conv) {
            conv = {
                id: conversationId || 'conv-' + Date.now(),
                title: '新对话',
                messages: [],
                updatedAt: Date.now()
            };
            conversations.unshift(conv);
        }

        const message = { role, text };
        if (Array.isArray(meta.sources) && meta.sources.length > 0) {
            message.sources = meta.sources;
        }
        if (meta.debugInfo) {
            message.debugInfo = meta.debugInfo;
        }

        conv.messages.push(message);
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
            icon.setAttribute('data-lucide', 'message-square');

            const title = document.createElement('span');
            title.className = 'history-title';
            title.textContent = conv.title || '新对话';

            const del = document.createElement('button');
            del.className = 'delete-history-btn';
            del.type = 'button';
            del.setAttribute('aria-label', '删除对话');
            del.innerHTML = '<i data-lucide="trash-2"></i>';
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
        refreshIcons();
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

        chatMessages.classList.add('no-animate');
        conv.messages.forEach(m => {
            if (m.role === 'assistant') {
                const assistantRow = appendMessage('assistant', formatResponse(m.text), null, true);
                if (Array.isArray(m.sources) && m.sources.length > 0) {
                    renderSourceCards(assistantRow, m.sources);
                }
                if (m.debugInfo) {
                    renderDebugPanel(assistantRow, m.debugInfo);
                }
            } else {
                appendMessage('user', m.text);
            }
        });
        chatMessages.classList.remove('no-animate');
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
            await authFetch(`/api/v1/chat/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' });
        } catch (error) {
            console.error('Error clearing server session:', error);
        }
    }

    function applyTheme(theme) {
        const nextTheme = theme === 'light' ? 'light' : 'dark';
        document.documentElement.dataset.theme = nextTheme;
        localStorage.setItem(THEME_KEY, nextTheme);

        const icon = themeToggle.querySelector('[data-lucide]');
        if (icon) {
            // Rotation crossfade animation
            icon.style.transition = 'transform 0.3s ease, opacity 0.3s ease';
            icon.style.transform = 'rotate(90deg)';
            icon.style.opacity = '0';
            setTimeout(() => {
                if (nextTheme === 'dark') {
                    icon.setAttribute('data-lucide', 'sun');
                    themeLabel.textContent = '日间模式';
                } else {
                    icon.setAttribute('data-lucide', 'moon');
                    themeLabel.textContent = '夜间模式';
                }
                refreshIcons();
                // Re-query after refreshIcons replaces <i> with <svg>
                const newIcon = themeToggle.querySelector('[data-lucide]');
                if (newIcon) {
                    newIcon.style.transition = 'transform 0.3s ease, opacity 0.3s ease';
                    newIcon.style.opacity = '0';
                    newIcon.style.transform = 'rotate(0)';
                    // Force reflow then animate in
                    newIcon.offsetHeight;
                    newIcon.style.opacity = '1';
                }
            }, 150);
        }
    }

    async function loadKnowledgeBases() {
        try {
            const response = await authFetch('/api/v1/files/knowledge-bases');
            if (!response.ok) return;

            const knowledgeBases = await response.json();
            renderKnowledgeBaseOptions(knowledgeBases.length === 0 ? [DEFAULT_KNOWLEDGE_BASE] : knowledgeBases);
        } catch (error) {
            console.error('Error loading knowledge bases:', error);
            renderKnowledgeBaseOptions([DEFAULT_KNOWLEDGE_BASE]);
        }
    }

    function renderKnowledgeBaseOptions(knowledgeBases) {
        const available = knowledgeBases.length === 0 ? [DEFAULT_KNOWLEDGE_BASE] : knowledgeBases;
        const validSelection = currentKnowledgeBases.filter(kb => available.includes(kb));
        if (validSelection.length === 0) {
            saveSelectedKnowledgeBases([available[0] || DEFAULT_KNOWLEDGE_BASE]);
        } else if (validSelection.length !== currentKnowledgeBases.length) {
            saveSelectedKnowledgeBases(validSelection);
        }

        knowledgeBaseSelect.innerHTML = '';
        knowledgeBaseSelect.multiple = true;
        knowledgeBaseSelect.size = 1;
        knowledgeBaseSelect.title = '按住 Command/Ctrl 可选择多个知识库';
        available.forEach(kb => {
            const option = document.createElement('option');
            option.value = kb;
            option.textContent = knowledgeBaseLabel(kb);
            option.selected = selectedKnowledgeBaseList().includes(kb);
            knowledgeBaseSelect.appendChild(option);
        });

    }

    function syncKnowledgeBaseControls() {
        Array.from(knowledgeBaseSelect.options).forEach(option => {
            option.selected = selectedKnowledgeBaseList().includes(option.value);
        });
    }

    function setCurrentKnowledgeBases(values) {
        saveSelectedKnowledgeBases(values);
        syncKnowledgeBaseControls();
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
        const conversationId = currentId;
        const requestKnowledgeBases = selectedKnowledgeBaseList();
        const requestKnowledgeBaseValue = selectedKnowledgeBaseValue();
        const requestDebugMode = debugMode;
        if (welcomeScreen) welcomeScreen.style.display = 'none';

        appendMessage('user', message);
        persistMessage(conversationId, 'user', message);

        userInput.value = '';
        userInput.style.height = 'auto';

        const assistantRow = appendMessage('assistant', '<div class="typing"><span></span><span></span><span></span></div>', null, true);
        const textDiv = assistantRow.querySelector('.message-text');
        let assistantText = '';
        let pendingSources = null;
        let pendingDebugInfo = null;

        try {
            await streamChatResponse(message, conversationId, requestKnowledgeBases, requestKnowledgeBaseValue, {
                onToken: (token) => {
                    assistantText += token;
                    if (currentId === conversationId && assistantRow.isConnected) {
                        textDiv.innerHTML = formatResponse(assistantText);
                        chatMessages.scrollTop = chatMessages.scrollHeight;
                    }
                },
                onSources: (sources) => {
                    pendingSources = sources;
                },
                onDebug: (debugInfo) => {
                    pendingDebugInfo = debugInfo;
                },
                onError: (errorMessage) => {
                    throw new Error(errorMessage);
                }
            }, requestDebugMode);

            const debugInfoToPersist = requestDebugMode ? (pendingDebugInfo || {
                allCandidates: [],
                usedCount: 0,
                knowledgeBase: requestKnowledgeBaseValue
            }) : null;

            if (assistantText.trim() !== '') {
                persistMessage(conversationId, 'assistant', assistantText, {
                    sources: pendingSources || [],
                    debugInfo: debugInfoToPersist
                });
            }
            if (currentId === conversationId && assistantRow.isConnected && pendingSources && pendingSources.length > 0) {
                renderSourceCards(assistantRow, pendingSources);
            }
            if (currentId === conversationId && assistantRow.isConnected && requestDebugMode) {
                renderDebugPanel(assistantRow, debugInfoToPersist);
            }
        } catch (error) {
            console.error('Error:', error);
            if (assistantText.trim() !== '') {
                persistMessage(conversationId, 'assistant', assistantText, {
                    sources: pendingSources || [],
                    debugInfo: requestDebugMode ? (pendingDebugInfo || {
                        allCandidates: [],
                        usedCount: 0,
                        knowledgeBase: requestKnowledgeBaseValue
                    }) : null
                });
            } else {
                if (currentId === conversationId && assistantRow.isConnected) {
                    textDiv.textContent = error.message || '无法建立连接，请检查后端服务是否启动。';
                    textDiv.classList.add('message-error');
                }
            }
        } finally {
            isProcessing = false;
            sendBtn.disabled = userInput.value.trim() === '';
            chatMessages.scrollTop = chatMessages.scrollHeight;
        }
    };

    async function streamChatResponse(message, sessionId, knowledgeBases, knowledgeBase, handlers, debug = false) {
        return streamChatResponseApi(authFetch, {
            message,
            sessionId,
            knowledgeBase,
            knowledgeBases,
            debug
        }, handlers);
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

    if (adminConsoleBtn) {
        adminConsoleBtn.addEventListener('click', openAdminConsole);
    }
    knowledgeBaseSelect.addEventListener('change', () => {
        const selected = Array.from(knowledgeBaseSelect.selectedOptions).map(option => option.value);
        setCurrentKnowledgeBases(selected);
    });
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

    function renderSourceCards(container, sources) {
        const wrapper = document.createElement('div');
        wrapper.className = 'source-cards';

        const header = document.createElement('div');
        header.className = 'source-cards-header';
        header.innerHTML = `<span class="source-cards-title">参考来源 (${sources.length})</span>`;
        wrapper.appendChild(header);

        sources.forEach((source, index) => {
            const card = document.createElement('div');
            card.className = 'source-card';

            const scorePercent = Math.round((source.score || 0) * 100);
            const scoreClass = scorePercent >= 80 ? 'high' : scorePercent >= 60 ? 'medium' : 'low';

            const excerpt = source.text || '';

            card.innerHTML = `
                <div class="source-card-header">
                    <span class="source-card-icon ${getFileColorClass(source.fileName || '')}">${getFileTypeLabel(source.fileName || '')}</span>
                    <span class="source-card-name" title="${escapeHtml(source.fileName || '')}">${escapeHtml(source.fileName || '未知文件')}</span>
                    <span class="source-card-score ${scoreClass}">${scorePercent}%</span>
                </div>
                <div class="source-card-detail">
                    <span class="source-card-chunk">${escapeHtml(source.chunkId || '')}</span>
                    ${source.page ? '<span class="source-card-page">页码 ' + escapeHtml(source.page) + '</span>' : ''}
                </div>
                <div class="source-card-excerpt collapsed" data-expanded="false">${escapeHtml(excerpt)}</div>
                <button class="source-card-toggle" type="button">展开摘录</button>
            `;

            const toggleBtn = card.querySelector('.source-card-toggle');
            const excerptDiv = card.querySelector('.source-card-excerpt');
            toggleBtn.addEventListener('click', () => {
                const expanded = excerptDiv.dataset.expanded === 'true';
                excerptDiv.dataset.expanded = expanded ? 'false' : 'true';
                excerptDiv.classList.toggle('collapsed', expanded);
                toggleBtn.textContent = expanded ? '展开摘录' : '收起摘录';
            });

            wrapper.appendChild(card);
        });

        const textDiv = container.querySelector('.message-text');
        if (textDiv && textDiv.nextSibling) {
            textDiv.parentNode.insertBefore(wrapper, textDiv.nextSibling);
        } else if (textDiv) {
            textDiv.parentNode.appendChild(wrapper);
        }
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    function renderDebugPanel(container, debugInfo) {
        const wrapper = document.createElement('div');
        wrapper.className = 'debug-panel';

        const header = document.createElement('div');
        header.className = 'debug-panel-header';
        header.innerHTML = `
            <span class="debug-panel-title">检索调试信息</span>
            <span class="debug-panel-summary">共 ${debugInfo.allCandidates ? debugInfo.allCandidates.length : 0} 个候选，${debugInfo.usedCount || 0} 个被采用</span>
        `;
        wrapper.appendChild(header);

        const content = document.createElement('div');
        content.className = 'debug-panel-content';

        if (debugInfo.allCandidates && debugInfo.allCandidates.length > 0) {
            const table = document.createElement('table');
            table.className = 'debug-table';
            table.innerHTML = `
                <thead>
                    <tr>
                        <th>得分</th>
                        <th>匹配</th>
                        <th>文件</th>
                        <th>片段</th>
                        <th>采用</th>
                        <th>摘录</th>
                    </tr>
                </thead>
                <tbody>
                    ${debugInfo.allCandidates.map(c => `
                        <tr class="${c.usedInAnswer ? 'debug-used' : 'debug-unused'}">
                            <td><span class="debug-score ${c.score >= 0.8 ? 'high' : c.score >= 0.6 ? 'medium' : 'low'}">${Math.round(c.score * 100)}%</span></td>
                            <td><span class="debug-match-type ${c.matchType.toLowerCase()}">${c.matchType}</span></td>
                            <td title="${escapeHtml(c.fileName || '')}">${escapeHtml(truncateFilename(c.fileName || ''))}</td>
                            <td title="${escapeHtml(c.chunkId || '')}">${escapeHtml(c.chunkId || '')}</td>
                            <td>${c.usedInAnswer ? '✓' : '✗'}</td>
                            <td title="${escapeHtml(c.text || '')}">${escapeHtml((c.text || '').substring(0, 80))}${(c.text || '').length > 80 ? '...' : ''}</td>
                        </tr>
                    `).join('')}
                </tbody>
            `;
            content.appendChild(table);
        } else {
            content.innerHTML = '<p class="debug-empty">无候选片段</p>';
        }

        wrapper.appendChild(content);

        const toggleBtn = document.createElement('button');
        toggleBtn.className = 'debug-toggle-btn';
        toggleBtn.type = 'button';
        toggleBtn.textContent = '收起调试信息';
        toggleBtn.addEventListener('click', () => {
            const expanded = content.classList.contains('collapsed');
            content.classList.toggle('collapsed', !expanded);
            toggleBtn.textContent = expanded ? '收起调试信息' : '展开调试信息';
        });
        wrapper.appendChild(toggleBtn);

        const textDiv = container.querySelector('.message-text');
        const sourceCards = container.querySelector('.source-cards');
        const insertAfter = sourceCards || textDiv;
        if (insertAfter && insertAfter.nextSibling) {
            insertAfter.parentNode.insertBefore(wrapper, insertAfter.nextSibling);
        } else if (insertAfter) {
            insertAfter.parentNode.appendChild(wrapper);
        }
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }

    applyTheme(localStorage.getItem(THEME_KEY) || 'dark');

    // 初始化：检查登录状态
    if (restoreAuth()) {
        verifyAuth().then(ok => {
            if (ok) {
                loadKnowledgeBases();
                renderHistory();
            }
        });
    } else {
        showLogin();
    }
});
