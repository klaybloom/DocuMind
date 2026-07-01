const API_BASE = '/api/v1';

/**
 * Create an auth-aware fetch wrapper.
 * @param {function} getCredentials - returns current Base64 auth credentials
 * @param {function} onUnauthorized - called when server returns 401
 * @returns {function} authFetch(url, options)
 */
export function createAuthFetch(getCredentials, onUnauthorized) {
    return async function authFetch(url, options = {}) {
        const credentials = getCredentials();
        const headers = { ...(options.headers || {}) };
        if (credentials) {
            headers['Authorization'] = 'Basic ' + credentials;
        }
        const response = await fetch(url, { ...options, headers });
        if (response.status === 401 && typeof onUnauthorized === 'function') {
            onUnauthorized();
        }
        return response;
    };
}

export async function fetchCurrentUser(credentials) {
    return fetch(`${API_BASE}/auth/me`, {
        headers: { 'Authorization': 'Basic ' + credentials }
    });
}

/**
 * Stream a chat response via SSE.
 */
export async function streamChatResponse(authFetch, request, handlers) {
    const { message, sessionId, knowledgeBase, knowledgeBases = [], debug = false } = request;
    const url = debug ? `${API_BASE}/chat/stream?debug=true` : `${API_BASE}/chat/stream`;
    const selectedKnowledgeBases = Array.isArray(knowledgeBases)
        ? knowledgeBases.map(value => String(value || '').trim()).filter(Boolean)
        : [];
    const body = {
        message,
        sessionId,
        knowledgeBase: selectedKnowledgeBases[0] || knowledgeBase
    };
    if (selectedKnowledgeBases.length > 0) {
        body.knowledgeBases = selectedKnowledgeBases;
    }
    if (debug) body.debug = true;

    const response = await authFetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
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

/**
 * Parse and dispatch a single SSE event line.
 */
export function handleStreamEvent(rawEvent, handlers) {
    const event = parseSseEvent(rawEvent);
    if (!event) return;

    if (event.name === 'token') {
        handlers.onToken?.(event.data);
    }
    if (event.name === 'sources') {
        try {
            const sources = JSON.parse(event.data);
            if (handlers.onSources && Array.isArray(sources)) {
                handlers.onSources(sources);
            }
        } catch (e) {
            console.error('Failed to parse sources event:', e);
        }
    }
    if (event.name === 'debug') {
        try {
            const debugInfo = JSON.parse(event.data);
            handlers.onDebug?.(debugInfo);
        } catch (e) {
            console.error('Failed to parse debug event:', e);
        }
    }
    if (event.name === 'error') {
        handlers.onError?.(event.data || '处理请求时发生错误');
    }
}

export async function fetchJson(authFetch, url, options = {}) {
    const response = await authFetch(url, options);
    const data = await response.json().catch(() => ({}));
    return { response, data };
}

export function apiPath(path) {
    return `${API_BASE}${path}`;
}

export async function readApiError(response, fallback = '请求失败') {
    const data = await response.json().catch(() => ({}));
    return data.error || data.message || fallback;
}

export async function listAdminUsers(authFetch) {
    const response = await authFetch(apiPath('/admin/users'));
    if (!response.ok) {
        throw new Error(await readApiError(response, '用户列表加载失败'));
    }
    return response.json();
}

export async function listUserOptions(authFetch) {
    const response = await authFetch(apiPath('/admin/users/options'));
    if (!response.ok) {
        throw new Error(await readApiError(response, '用户选项加载失败'));
    }
    return response.json();
}

export async function listAdminKnowledgeBases(authFetch) {
    const response = await authFetch(apiPath('/admin/knowledge-bases'));
    if (!response.ok) {
        throw new Error(await readApiError(response, '知识库加载失败'));
    }
    return response.json();
}

export async function createKnowledgeBase(authFetch, payload) {
    return submitJsonRequest(authFetch, apiPath('/admin/knowledge-bases'), 'POST', payload, '知识库创建失败');
}

export async function setKnowledgeBaseOwners(authFetch, knowledgeBase, owners) {
    return submitJsonRequest(
        authFetch,
        apiPath(`/admin/knowledge-bases/${encodeURIComponent(knowledgeBase)}/owners`),
        'PUT',
        { owners },
        '负责人保存失败'
    );
}

export async function addKnowledgeBaseOwners(authFetch, knowledgeBase, owners) {
    return submitJsonRequest(
        authFetch,
        apiPath(`/admin/knowledge-bases/${encodeURIComponent(knowledgeBase)}/owners`),
        'POST',
        { owners },
        '负责人添加失败'
    );
}

export async function selfTransferKnowledgeBase(authFetch, knowledgeBase, owners) {
    return submitJsonRequest(
        authFetch,
        apiPath(`/admin/knowledge-bases/${encodeURIComponent(knowledgeBase)}/owners/self-transfer`),
        'PUT',
        { owners },
        '负责人转移失败'
    );
}

export async function setKnowledgeBaseMembers(authFetch, knowledgeBase, members) {
    return submitJsonRequest(
        authFetch,
        apiPath(`/admin/knowledge-bases/${encodeURIComponent(knowledgeBase)}/members`),
        'PUT',
        { members },
        '访问用户保存失败'
    );
}

export async function createAdminUser(authFetch, payload) {
    return submitAdminUserRequest(authFetch, apiPath('/admin/users'), 'POST', payload, '用户创建失败');
}

export async function updateAdminUser(authFetch, userId, payload) {
    return submitAdminUserRequest(authFetch, apiPath(`/admin/users/${encodeURIComponent(userId)}`), 'PUT', payload, '用户更新失败');
}

export async function resetAdminUserPassword(authFetch, userId, password) {
    return submitAdminUserRequest(
        authFetch,
        apiPath(`/admin/users/${encodeURIComponent(userId)}/password`),
        'PUT',
        { password },
        '密码重置失败'
    );
}

async function submitAdminUserRequest(authFetch, url, method, payload, fallback) {
    return submitJsonRequest(authFetch, url, method, payload, fallback);
}

async function submitJsonRequest(authFetch, url, method, payload, fallback) {
    const response = await authFetch(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    if (!response.ok) {
        throw new Error(await readApiError(response, fallback));
    }
    return response.json();
}

/**
 * Parse an SSE event string into { name, data }.
 */
export function parseSseEvent(rawEvent) {
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
