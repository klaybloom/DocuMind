/**
 * Pure utility functions — no DOM or state dependency.
 */

export function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

export function truncateFilename(filename, maxLen = 28) {
    if (!filename || filename.length <= maxLen) return filename;
    const ext = filename.lastIndexOf('.');
    if (ext > 0 && filename.length - ext <= 6) {
        const name = filename.substring(0, ext);
        const suffix = filename.substring(ext);
        const available = maxLen - suffix.length - 3;
        return name.substring(0, Math.max(8, available)) + '...' + suffix;
    }
    return filename.substring(0, maxLen - 3) + '...';
}

export function getFileTypeLabel(filename) {
    if (!filename) return '文件';
    const ext = filename.split('.').pop().toLowerCase();
    const map = {
        pdf: 'PDF', txt: 'TXT', doc: 'DOC', docx: 'DOCX',
        ppt: 'PPT', pptx: 'PPTX', xls: 'XLS', xlsx: 'XLSX'
    };
    return map[ext] || ext.toUpperCase();
}

export function getFileColorClass(filename) {
    if (!filename) return 'file-color-default';
    const ext = filename.split('.').pop().toLowerCase();
    const map = {
        pdf: 'file-color-pdf', txt: 'file-color-txt',
        doc: 'file-color-word', docx: 'file-color-word',
        ppt: 'file-color-ppt', pptx: 'file-color-ppt',
        xls: 'file-color-excel', xlsx: 'file-color-excel'
    };
    return map[ext] || 'file-color-default';
}

export function getFileIconElement(filename) {
    const label = getFileTypeLabel(filename);
    const colorClass = getFileColorClass(filename);
    const icon = document.createElement('span');
    icon.className = `file-type-icon ${colorClass}`;
    icon.textContent = label;
    return icon;
}

export function formatDate(value) {
    if (!value) return '';
    try {
        const d = new Date(value);
        if (isNaN(d.getTime())) return value;
        return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
    } catch { return value; }
}

export function formatDateTime(value) {
    if (!value) return '';
    try {
        const d = new Date(value);
        if (isNaN(d.getTime())) return value;
        return d.toLocaleString('zh-CN', {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
    } catch { return value; }
}

export function actionText(action) {
    const map = {
        CHAT_REQUEST: '问答请求',
        RATE_LIMITED_CHAT_REQUEST: '限流问答',
        CLEAR_CHAT_SESSION: '清理会话',
        UPLOAD_DOCUMENT: '上传文档',
        DOWNLOAD_DOCUMENT: '下载文档',
        DELETE_DOCUMENT: '删除文档',
        REFRESH_INDEX: '刷新索引',
        GENERATE_FAQ_DRAFT: '生成 FAQ',
        RESOLVE_KNOWLEDGE_GAP: '处理缺口'
    };
    return map[action] || action;
}

export function statusClass(status) {
    if (status === 'INDEXED') return 'status-indexed';
    if (status === 'FAILED') return 'status-failed';
    if (status === 'INDEXING') return 'status-indexing';
    return 'status-pending';
}

export function statusText(file) {
    const stale = file.stale ? ' · 可能过期' : '';
    if (file.indexStatus === 'INDEXED') return `已索引 · ${file.chunkCount || 0} 片段${stale}`;
    if (file.indexStatus === 'FAILED') return `索引失败${file.error ? ' · ' + file.error : ''}`;
    if (file.indexStatus === 'INDEXING') return `索引中${stale}`;
    return `待处理${stale}`;
}

export function statusDetailText(file) {
    if (!file.lastIndexedAt) return '';
    return `上次索引: ${formatDate(file.lastIndexedAt)}`;
}

export function knowledgeBaseLabel(value) {
    if (!value || value === 'default') return '默认知识库';
    return value;
}

export function formatResponse(text) {
    if (!text) return '';
    // Basic markdown-like formatting
    let html = escapeHtml(text);
    // Bold
    html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
    // Line breaks
    html = html.replace(/\n/g, '<br>');
    return html;
}
