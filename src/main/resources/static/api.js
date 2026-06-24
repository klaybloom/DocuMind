/**
 * API helper functions — fetch wrappers and SSE handling.
 */

/**
 * Create an auth-aware fetch wrapper.
 * @param {function} getCredentials - returns current Base64 auth credentials
 * @returns {function} authFetch(url, options)
 */
export function createAuthFetch(getCredentials) {
    return async function authFetch(url, options = {}) {
        const credentials = getCredentials();
        const headers = { ...(options.headers || {}) };
        if (credentials) {
            headers['Authorization'] = 'Basic ' + credentials;
        }
        return fetch(url, { ...options, headers });
    };
}

/**
 * Stream a chat response via SSE.
 */
export async function streamChatResponse(authFetch, message, sessionId, knowledgeBase, handlers, debug = false) {
    const body = { message, sessionId, knowledgeBase };
    if (debug) body.debug = true;

    try {
        const response = await authFetch('/api/chat/stream', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (!response.ok) {
            const errorText = await response.text();
            handlers.onError?.(new Error(`HTTP ${response.status}: ${errorText}`));
            return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop(); // Keep incomplete line in buffer

            for (const line of lines) {
                if (line.trim()) {
                    handleStreamEvent(line, handlers);
                }
            }
        }

        // Process any remaining buffer
        if (buffer.trim()) {
            handleStreamEvent(buffer, handlers);
        }

        handlers.onComplete?.();
    } catch (error) {
        handlers.onError?.(error);
    }
}

/**
 * Parse and dispatch a single SSE event line.
 */
function handleStreamEvent(rawEvent, handlers) {
    const event = parseSseEvent(rawEvent);
    if (!event) return;

    switch (event.type) {
        case 'token':
            handlers.onToken?.(event.data);
            break;
        case 'sources':
            try {
                const sources = JSON.parse(event.data);
                handlers.onSources?.(sources);
            } catch (e) {
                console.warn('Failed to parse sources:', e);
            }
            break;
        case 'done':
            handlers.onDone?.();
            break;
        case 'error':
            handlers.onError?.(new Error(event.data));
            break;
        case 'debug':
            if (handlers.onDebug) {
                try {
                    handlers.onDebug(JSON.parse(event.data));
                } catch (e) {
                    handlers.onDebug(event.data);
                }
            }
            break;
    }
}

/**
 * Parse an SSE event string into { type, data }.
 */
export function parseSseEvent(rawEvent) {
    const lines = rawEvent.split('\n');
    let eventType = 'message';
    let data = '';

    for (const line of lines) {
        if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
        } else if (line.startsWith('data:')) {
            data += (data ? '\n' : '') + line.substring(5).trim();
        }
    }

    if (!data) return null;
    return { type: eventType, data };
}
