import { describe, expect, it, vi } from 'vitest';
import {
    createAuthFetch,
    handleStreamEvent,
    parseSseEvent,
    streamChatResponse
} from '../../main/resources/static/api.js';

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
});
