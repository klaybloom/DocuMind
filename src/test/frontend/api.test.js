import { describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import {
    apiPath,
    addKnowledgeBaseOwners,
    createAdminUser,
    createAuthFetch,
    listAdminKnowledgeBases,
    handleStreamEvent,
    listAdminUsers,
    listUserOptions,
    parseSseEvent,
    readApiError,
    resetAdminUserPassword,
    selfTransferKnowledgeBase,
    setKnowledgeBaseMembers,
    streamChatResponse
} from '../../main/resources/static/api.js';
import {
    matchesAdminSearch,
    userOptionLabel
} from '../../main/resources/static/admin.js';

describe('frontend API helpers', () => {
    it('parses named SSE events with multiline data', () => {
        expect(parseSseEvent('event: token\ndata: 第一行\ndata: 第二行')).toEqual({
            name: 'token',
            data: '第一行\n第二行'
        });
    });

    it('ignores empty message events', () => {
        expect(parseSseEvent(': keepalive')).toBeNull();
    });

    it('dispatches SSE events to matching handlers', () => {
        const handlers = {
            onToken: vi.fn(),
            onSources: vi.fn(),
            onDebug: vi.fn(),
            onError: vi.fn()
        };

        handleStreamEvent('event: token\ndata: 你好', handlers);
        handleStreamEvent('event: sources\ndata: [{"fileName":"a.pdf"}]', handlers);
        handleStreamEvent('event: debug\ndata: {"usedCount":1}', handlers);
        handleStreamEvent('event: error\ndata: 失败', handlers);

        expect(handlers.onToken).toHaveBeenCalledWith('你好');
        expect(handlers.onSources).toHaveBeenCalledWith([{ fileName: 'a.pdf' }]);
        expect(handlers.onDebug).toHaveBeenCalledWith({ usedCount: 1 });
        expect(handlers.onError).toHaveBeenCalledWith('失败');
    });

    it('adds Basic auth credentials to requests and reports 401', async () => {
        const onUnauthorized = vi.fn();
        const fetchMock = vi.fn(async () => new Response('{}', { status: 401 }));
        vi.stubGlobal('fetch', fetchMock);

        const authFetch = createAuthFetch(() => 'abc123', onUnauthorized);
        await authFetch('/api/v1/files/list', {
            headers: { Accept: 'application/json' }
        });

        expect(fetchMock).toHaveBeenCalledWith('/api/v1/files/list', {
            headers: {
                Accept: 'application/json',
                Authorization: 'Basic abc123'
            }
        });
        expect(onUnauthorized).toHaveBeenCalledTimes(1);

        vi.unstubAllGlobals();
    });

    it('sends selected knowledge bases when streaming chat in debug mode', async () => {
        const body = new ReadableStream({
            start(controller) {
                controller.enqueue(new TextEncoder().encode('event: token\ndata: ok\n\n'));
                controller.close();
            }
        });
        const authFetch = vi.fn(async () => new Response(body, {
            status: 200,
            headers: { 'Content-Type': 'text/event-stream' }
        }));
        const onToken = vi.fn();

        await streamChatResponse(authFetch, {
            message: '问题',
            sessionId: 'conv-1',
            knowledgeBase: 'HR,Legal',
            knowledgeBases: ['HR', 'Legal'],
            debug: true
        }, { onToken });

        expect(authFetch).toHaveBeenCalledWith('/api/v1/chat/stream?debug=true', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                message: '问题',
                sessionId: 'conv-1',
                knowledgeBase: 'HR',
                knowledgeBases: ['HR', 'Legal'],
                debug: true
            })
        });
        expect(onToken).toHaveBeenCalledWith('ok');
    });

    it('builds versioned API paths', () => {
        expect(apiPath('/admin/users')).toBe('/api/v1/admin/users');
    });

    it('uses admin user endpoints', async () => {
        const authFetch = vi.fn(async () => new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' }
        }));

        await listAdminUsers(authFetch);

        expect(authFetch).toHaveBeenCalledWith('/api/v1/admin/users');
    });

    it('uses admin knowledge base endpoints', async () => {
        const authFetch = vi.fn(async () => new Response(JSON.stringify([]), {
            status: 200,
            headers: { 'Content-Type': 'application/json' }
        }));

        await listUserOptions(authFetch);
        await listAdminKnowledgeBases(authFetch);
        await addKnowledgeBaseOwners(authFetch, 'HR', ['owner']);
        await setKnowledgeBaseMembers(authFetch, 'HR', ['reader']);
        await selfTransferKnowledgeBase(authFetch, 'HR', ['new-owner']);

        expect(authFetch).toHaveBeenNthCalledWith(1, '/api/v1/admin/users/options');
        expect(authFetch).toHaveBeenNthCalledWith(2, '/api/v1/admin/knowledge-bases');
        expect(authFetch).toHaveBeenNthCalledWith(3, '/api/v1/admin/knowledge-bases/HR/owners', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ owners: ['owner'] })
        });
        expect(authFetch).toHaveBeenNthCalledWith(4, '/api/v1/admin/knowledge-bases/HR/members', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ members: ['reader'] })
        });
        expect(authFetch).toHaveBeenNthCalledWith(5, '/api/v1/admin/knowledge-bases/HR/owners/self-transfer', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ owners: ['new-owner'] })
        });
    });

    it('submits admin user mutations as JSON', async () => {
        const authFetch = vi.fn(async () => new Response(JSON.stringify({ id: 7 }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' }
        }));

        await createAdminUser(authFetch, { username: 'reader' });
        await resetAdminUserPassword(authFetch, 7, 'new-password');

        expect(authFetch).toHaveBeenNthCalledWith(1, '/api/v1/admin/users', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: 'reader' })
        });
        expect(authFetch).toHaveBeenNthCalledWith(2, '/api/v1/admin/users/7/password', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ password: 'new-password' })
        });
    });

    it('reads API error messages with fallback', async () => {
        const response = new Response(JSON.stringify({ error: '失败原因' }), {
            status: 400,
            headers: { 'Content-Type': 'application/json' }
        });

        await expect(readApiError(response, '默认失败')).resolves.toBe('失败原因');
    });

    it('filters admin table data and renders user option labels', () => {
        expect(matchesAdminSearch({ username: 'reader', role: 'USER' }, 'read', ['username'])).toBe(true);
        expect(matchesAdminSearch({ username: 'reader', role: 'USER' }, 'admin', ['username'])).toBe(false);
        expect(userOptionLabel({ username: 'owner', role: 'ADMIN', enabled: false })).toBe('owner · 管理员 · 已停用');
    });

    it('keeps document management out of the chat shell', () => {
        const appJs = readFileSync(new URL('../../main/resources/static/app.js', import.meta.url), 'utf8');
        const indexHtml = readFileSync(new URL('../../main/resources/static/index.html', import.meta.url), 'utf8');

        expect(indexHtml).not.toContain('document-modal');
        expect(appJs).not.toContain('document-modal');
        expect(appJs).toContain('admin-console-btn');
    });
});
