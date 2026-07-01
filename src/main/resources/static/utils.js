export function escapeHtml(text) {
    return String(text ?? '').replace(/[&<>"']/g, char => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    })[char]);
}

export function truncateFilename(filename, maxLen = 20) {
    if (!filename) return '';
    if (filename.length <= maxLen) return filename;
    const ext = filename.lastIndexOf('.');
    if (ext > 0) {
        return filename.substring(0, Math.max(1, maxLen - 8)) + '...' + filename.substring(ext);
    }
    return filename.substring(0, Math.max(1, maxLen - 3)) + '...';
}

export function getFileTypeLabel(filename) {
    const lower = String(filename || '').toLowerCase();
    if (lower.endsWith('.pdf')) return 'PDF';
    if (lower.endsWith('.doc') || lower.endsWith('.docx')) return 'DOC';
    if (lower.endsWith('.xls') || lower.endsWith('.xlsx')) return 'XLS';
    if (lower.endsWith('.ppt') || lower.endsWith('.pptx')) return 'PPT';
    return 'TXT';
}

export function getFileColorClass(filename) {
    const lower = String(filename || '').toLowerCase();
    if (lower.endsWith('.pdf')) return 'file-pdf';
    if (lower.endsWith('.doc') || lower.endsWith('.docx')) return 'file-word';
    if (lower.endsWith('.xls') || lower.endsWith('.xlsx')) return 'file-excel';
    if (lower.endsWith('.ppt') || lower.endsWith('.pptx')) return 'file-ppt';
    return 'file-text';
}

export function getFileIconElement(filename) {
    const el = document.createElement('i');
    el.setAttribute('data-lucide', 'file-text');
    el.classList.add(getFileColorClass(filename));
    return el;
}

export function formatDate(value) {
    if (!value) return '未知时间';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '未知时间';
    return date.toLocaleDateString('zh-CN');
}

export function formatDateTime(value) {
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

export function actionText(action) {
    const labels = {
        CHAT_REQUEST: '发起问答',
        UPLOAD_DOCUMENT: '上传文档',
        DOWNLOAD_DOCUMENT: '下载文档',
        DELETE_DOCUMENT: '删除文档',
        REFRESH_INDEX: '刷新索引',
        GENERATE_FAQ_DRAFT: '生成 FAQ 草稿',
        RESOLVE_KNOWLEDGE_GAP: '处理知识缺口',
        CREATE_USER: '创建用户',
        UPDATE_USER: '更新用户',
        RESET_USER_PASSWORD: '重置密码'
    };
    return labels[action] || action || '未知操作';
}

export function statusClass(status) {
    if (status === 'INDEXED') return 'indexed';
    if (status === 'FAILED') return 'failed';
    if (status === 'INDEXING') return 'indexing';
    return 'pending';
}

export function statusText(file) {
    const stale = file.stale ? ` · 可能过期 ${file.daysSinceUpload || 0} 天` : '';
    if (file.indexStatus === 'INDEXED') return `已索引 · ${file.chunkCount || 0} 片段${stale}`;
    if (file.indexStatus === 'FAILED') return `索引失败${file.error ? ' · ' + file.error : ''}`;
    if (file.indexStatus === 'INDEXING') return `索引中${stale}`;
    return `待索引${stale}`;
}

export function statusDetailText(file) {
    if (file.indexStatus === 'INDEXED' && file.lastIndexedAt) {
        return `索引于 ${formatDateTime(file.lastIndexedAt)}`;
    }
    if (file.indexStatus === 'FAILED' && file.lastIndexedAt) {
        return `失败于 ${formatDateTime(file.lastIndexedAt)}`;
    }
    return '';
}

export function knowledgeBaseLabel(value, defaultKnowledgeBase = 'default') {
    return value === defaultKnowledgeBase ? '默认知识库' : value;
}

export function formatResponse(text) {
    if (!text) return '';

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
