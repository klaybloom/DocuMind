import { describe, expect, it } from 'vitest';
import {
    actionText,
    escapeHtml,
    formatResponse,
    getFileColorClass,
    getFileTypeLabel,
    knowledgeBaseLabel,
    statusClass,
    statusDetailText,
    statusText,
    truncateFilename
} from '../../main/resources/static/utils.js';

describe('frontend utils', () => {
    it('escapes user controlled HTML before rendering', () => {
        expect(escapeHtml('<script>alert("x")</script>')).toBe('&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;');
    });

    it('keeps file labels and CSS classes aligned with the document UI', () => {
        expect(getFileTypeLabel('proposal.pdf')).toBe('PDF');
        expect(getFileTypeLabel('report.docx')).toBe('DOC');
        expect(getFileTypeLabel('metrics.xlsx')).toBe('XLS');
        expect(getFileTypeLabel('slides.pptx')).toBe('PPT');
        expect(getFileColorClass('proposal.pdf')).toBe('file-pdf');
        expect(getFileColorClass('report.docx')).toBe('file-word');
        expect(getFileColorClass('metrics.xlsx')).toBe('file-excel');
        expect(getFileColorClass('slides.pptx')).toBe('file-ppt');
    });

    it('formats index status with stale document detail', () => {
        const file = {
            indexStatus: 'INDEXED',
            chunkCount: 12,
            stale: true,
            daysSinceUpload: 7,
            lastIndexedAt: '2026-06-25T08:30:00Z'
        };

        expect(statusClass(file.indexStatus)).toBe('indexed');
        expect(statusText(file)).toBe('已索引 · 12 片段 · 可能过期 7 天');
        expect(statusDetailText(file)).toContain('索引于');
    });

    it('formats markdown-like answers without exposing raw tags', () => {
        const html = formatResponse('**重点**\n```js\nconsole.log("<x>")\n```');

        expect(html).toContain('<strong>重点</strong>');
        expect(html).toContain('<div class="code-block">');
        expect(html).toContain('&lt;x&gt;');
    });

    it('normalizes labels used across chat and document panels', () => {
        expect(actionText('GENERATE_FAQ_DRAFT')).toBe('生成 FAQ 草稿');
        expect(actionText('UNKNOWN_ACTION')).toBe('UNKNOWN_ACTION');
        expect(knowledgeBaseLabel('default')).toBe('默认知识库');
        expect(knowledgeBaseLabel('finance')).toBe('finance');
        expect(truncateFilename('very-long-business-proposal.pdf')).toBe('very-long-bu....pdf');
    });
});
