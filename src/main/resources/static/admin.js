import {
    addKnowledgeBaseOwners,
    apiPath,
    createAdminUser,
    createAuthFetch,
    createKnowledgeBase,
    fetchCurrentUser,
    listAdminKnowledgeBases,
    listAdminUsers,
    listUserOptions,
    readApiError,
    resetAdminUserPassword,
    selfTransferKnowledgeBase,
    setKnowledgeBaseMembers,
    setKnowledgeBaseOwners,
    updateAdminUser
} from './api.js';
import {
    actionText,
    formatDateTime,
    knowledgeBaseLabel,
    statusClass,
    statusDetailText,
    statusText,
    truncateFilename
} from './utils.js';

export function matchesAdminSearch(item, query, fields) {
    const normalized = String(query || '').trim().toLowerCase();
    if (!normalized) return true;
    return fields.some(field => String(item?.[field] ?? '').toLowerCase().includes(normalized));
}

export function userOptionLabel(user) {
    if (!user) return '';
    const role = user.role === 'ADMIN' ? '管理员' : '普通用户';
    return `${user.username} · ${role}${user.enabled ? '' : ' · 已停用'}`;
}

export function selectedValues(select) {
    return Array.from(select?.selectedOptions || []).map(option => option.value);
}

if (typeof document !== 'undefined') {
document.addEventListener('DOMContentLoaded', () => {
    const AUTH_KEY = 'documind_auth';
    const DEFAULT_KNOWLEDGE_BASE = 'default';

    const alertBox = document.getElementById('admin-alert');
    const userLabel = document.getElementById('admin-user-label');
    const refreshBtn = document.getElementById('admin-refresh-btn');
    const navButtons = Array.from(document.querySelectorAll('[data-admin-tab]'));
    const usersTabBtn = document.getElementById('admin-users-tab-btn');

    const userSearch = document.getElementById('user-search');
    const userTable = document.getElementById('user-table-body');
    const userEmpty = document.getElementById('user-empty');
    const openCreateUserBtn = document.getElementById('open-create-user');

    const kbSearch = document.getElementById('kb-search');
    const kbTable = document.getElementById('kb-table-body');
    const kbEmpty = document.getElementById('kb-empty');
    const openCreateKbBtn = document.getElementById('open-create-kb');

    const documentKbFilter = document.getElementById('document-kb-filter');
    const documentSearch = document.getElementById('document-search');
    const documentTable = document.getElementById('document-table-body');
    const documentEmpty = document.getElementById('document-empty');
    const openUploadDocumentBtn = document.getElementById('open-upload-document');
    const reindexAllBtn = document.getElementById('reindex-all-btn');

    const gapKbFilter = document.getElementById('gap-kb-filter');
    const gapSearch = document.getElementById('gap-search');
    const gapTable = document.getElementById('gap-table-body');
    const gapEmpty = document.getElementById('gap-empty');
    const generateFaqBtn = document.getElementById('generate-faq-btn');
    const faqDraft = document.getElementById('faq-draft');

    const auditSearch = document.getElementById('audit-search');
    const auditTable = document.getElementById('audit-table-body');
    const auditEmpty = document.getElementById('audit-empty');

    const modal = document.getElementById('admin-modal');
    const modalTitle = document.getElementById('admin-modal-title');
    const modalSubtitle = document.getElementById('admin-modal-subtitle');
    const modalBody = document.getElementById('admin-modal-body');
    const closeModalBtn = document.getElementById('close-admin-modal');

    let authCredentials = sessionStorage.getItem(AUTH_KEY);
    let currentUser = null;
    let users = [];
    let userOptions = [];
    let knowledgeBases = [];
    let documents = [];
    let gaps = [];
    let audits = [];

    const authFetch = createAuthFetch(() => authCredentials, () => {
        showAlert('登录已过期，请返回问答页重新登录', 'error');
    });

    function refreshIcons() {
        if (typeof lucide !== 'undefined') {
            lucide.createIcons();
        }
    }

    function showAlert(message, type = 'info') {
        alertBox.textContent = message || '';
        alertBox.dataset.type = type;
        alertBox.classList.toggle('hidden', !message);
    }

    function clearAlert() {
        showAlert('');
    }

    function isAdmin() {
        return currentUser?.roles?.includes('ADMIN');
    }

    function manageableKnowledgeBaseNames() {
        return knowledgeBases.map(kb => kb.knowledgeBase);
    }

    function selectedDocumentKnowledgeBase() {
        return documentKbFilter.value || '';
    }

    function selectedGapKnowledgeBase() {
        return gapKbFilter.value || '';
    }

    function enabledUsers() {
        return userOptions.filter(user => user.enabled);
    }

    function enabledNormalUsers() {
        return enabledUsers().filter(user => user.role === 'USER');
    }

    async function requireManager() {
        if (!authCredentials) {
            showAlert('未登录，请返回问答页登录', 'error');
            return false;
        }
        const response = await fetchCurrentUser(authCredentials);
        if (!response.ok) {
            showAlert('登录已过期，请返回问答页重新登录', 'error');
            return false;
        }
        currentUser = await response.json();
        if (!currentUser.canManageKnowledgeBases) {
            showAlert('当前账号没有后台管理权限', 'error');
            return false;
        }
        userLabel.textContent = `${currentUser.username} · ${isAdmin() ? '管理员' : '知识库负责人'}`;
        if (!isAdmin()) {
            usersTabBtn.classList.add('hidden');
            openCreateKbBtn.classList.add('hidden');
            reindexAllBtn.classList.add('hidden');
            switchTab('knowledge-bases');
        }
        return true;
    }

    async function loadAll() {
        clearAlert();
        userOptions = await listUserOptions(authFetch);
        knowledgeBases = await listAdminKnowledgeBases(authFetch);
        if (isAdmin()) {
            users = await listAdminUsers(authFetch);
        }
        fillKnowledgeBaseFilters();
        await Promise.all([loadDocuments(), loadGaps(), loadAudits()]);
        renderAll();
    }

    function fillKnowledgeBaseFilters() {
        const names = manageableKnowledgeBaseNames();
        fillSelect(documentKbFilter, names, '', '全部知识库');
        fillSelect(gapKbFilter, names, names[0] || DEFAULT_KNOWLEDGE_BASE, null);
    }

    async function loadDocuments() {
        const kb = selectedDocumentKnowledgeBase();
        const path = kb ? `/files/list?${new URLSearchParams({ knowledgeBase: kb })}` : '/files/list';
        const response = await authFetch(apiPath(path));
        if (!response.ok) throw new Error(await readApiError(response, '文档列表加载失败'));
        documents = await response.json();
    }

    async function loadGaps() {
        const kb = selectedGapKnowledgeBase();
        const path = kb ? `/files/gaps?${new URLSearchParams({ knowledgeBase: kb })}` : '/files/gaps';
        const response = await authFetch(apiPath(path));
        if (!response.ok) throw new Error(await readApiError(response, '知识缺口加载失败'));
        gaps = await response.json();
    }

    async function loadAudits() {
        const response = await authFetch(apiPath('/files/audit?limit=80'));
        if (!response.ok) throw new Error(await readApiError(response, '审计记录加载失败'));
        audits = await response.json();
    }

    function renderAll() {
        renderUsers();
        renderKnowledgeBases();
        renderDocuments();
        renderGaps();
        renderAudits();
        refreshIcons();
    }

    function renderUsers() {
        userTable.innerHTML = '';
        if (!isAdmin()) {
            userEmpty.textContent = '只有管理员可以管理用户';
            userEmpty.style.display = 'block';
            return;
        }
        const query = userSearch.value;
        const rows = users.filter(user => matchesAdminSearch(user, query, ['username', 'role']));
        userEmpty.style.display = rows.length === 0 ? 'block' : 'none';
        rows.forEach(user => {
            const tr = document.createElement('tr');

            const nameCell = document.createElement('td');
            nameCell.append(textBlock(user.username, `#${user.id}`));

            const roleCell = document.createElement('td');
            const roleSelect = document.createElement('select');
            roleSelect.innerHTML = '<option value="USER">普通用户</option><option value="ADMIN">管理员</option>';
            roleSelect.value = user.role;
            roleCell.appendChild(roleSelect);

            const enabledCell = document.createElement('td');
            const enabled = document.createElement('input');
            enabled.type = 'checkbox';
            enabled.checked = user.enabled;
            enabledCell.appendChild(enabled);

            const kbCell = document.createElement('td');
            const allLabel = document.createElement('label');
            allLabel.className = 'admin-check-row compact';
            const all = document.createElement('input');
            all.type = 'checkbox';
            all.checked = user.allKnowledgeBases;
            allLabel.append(all, document.createTextNode('全部'));
            const kbSelect = document.createElement('select');
            kbSelect.multiple = true;
            kbSelect.size = 3;
            fillSelect(kbSelect, manageableKnowledgeBaseNames(), null, null, user.knowledgeBases || []);
            kbCell.append(allLabel, kbSelect);

            function syncUserControls() {
                const adminRole = roleSelect.value === 'ADMIN';
                all.checked = adminRole || all.checked;
                all.disabled = adminRole;
                kbSelect.disabled = adminRole || all.checked;
            }
            roleSelect.addEventListener('change', syncUserControls);
            all.addEventListener('change', syncUserControls);
            syncUserControls();

            const actionCell = document.createElement('td');
            actionCell.className = 'admin-row-actions';
            actionCell.append(
                rowButton('save', '保存', async () => {
                    await runAction(async () => {
                        await updateAdminUser(authFetch, user.id, {
                            role: roleSelect.value,
                            enabled: enabled.checked,
                            knowledgeBases: all.checked ? ['*'] : selectedValues(kbSelect)
                        });
                        showAlert('用户已保存', 'success');
                        users = await listAdminUsers(authFetch);
                        renderUsers();
                    });
                }),
                rowButton('key-round', '密码', () => openPasswordModal(user))
            );

            tr.append(nameCell, roleCell, enabledCell, kbCell, actionCell);
            userTable.appendChild(tr);
        });
    }

    function renderKnowledgeBases() {
        kbTable.innerHTML = '';
        const query = kbSearch.value;
        const rows = knowledgeBases.filter(kb => {
            const flat = {
                knowledgeBase: kb.knowledgeBase,
                owners: (kb.owners || []).join(','),
                members: (kb.members || []).join(',')
            };
            return matchesAdminSearch(flat, query, ['knowledgeBase', 'owners', 'members']);
        });
        kbEmpty.style.display = rows.length === 0 ? 'block' : 'none';
        rows.forEach(kb => {
            const tr = document.createElement('tr');
            tr.append(
                cell(textBlock(knowledgeBaseLabel(kb.knowledgeBase), `创建人 ${kb.createdBy || '-'}`)),
                cell(chips(kb.owners)),
                cell(chips(kb.members)),
                cell(statusSummary(kb.status)),
                kbActionsCell(kb)
            );
            kbTable.appendChild(tr);
        });
    }

    function kbActionsCell(kb) {
        const actionCell = document.createElement('td');
        actionCell.className = 'admin-row-actions';
        actionCell.append(rowButton('users', '分发', () => openMembersModal(kb)));
        actionCell.append(rowButton('user-plus', '加负责人', () => openAddOwnersModal(kb)));
        if (isAdmin()) {
            actionCell.append(rowButton('settings', '负责人', () => openSetOwnersModal(kb)));
        }
        if ((kb.owners || []).includes(currentUser.username)) {
            actionCell.append(rowButton('move-right', '转移自己', () => openSelfTransferModal(kb)));
        }
        return actionCell;
    }

    function renderDocuments() {
        documentTable.innerHTML = '';
        const query = documentSearch.value;
        const rows = documents.filter(file => matchesAdminSearch(file, query, ['fileName', 'knowledgeBase', 'owner', 'uploadedBy']));
        documentEmpty.style.display = rows.length === 0 ? 'block' : 'none';
        rows.forEach(file => {
            const tr = document.createElement('tr');
            const status = document.createElement('div');
            status.className = `file-status ${statusClass(file.indexStatus)}`;
            status.textContent = statusText(file);
            const detail = statusDetailText(file);
            const statusWrap = document.createElement('div');
            statusWrap.appendChild(status);
            if (detail) {
                const small = document.createElement('small');
                small.textContent = detail;
                statusWrap.appendChild(small);
            }
            tr.append(
                cell(textBlock(truncateFilename(file.fileName, 42), `${formatSize(file.sizeBytes)} · ${file.contentType || 'unknown'}`)),
                cell(knowledgeBaseLabel(file.knowledgeBase)),
                cell(file.owner || '-'),
                cell(statusWrap),
                cell(textBlock(file.uploadedBy || '-', formatDateTime(file.uploadedAt))),
                documentActionsCell(file)
            );
            documentTable.appendChild(tr);
        });
    }

    function documentActionsCell(file) {
        const actionCell = document.createElement('td');
        actionCell.className = 'admin-row-actions';
        actionCell.append(
            rowButton('download', '下载', () => downloadFile(file)),
            rowButton('rotate-cw', '索引', () => reindexFile(file)),
            rowButton('trash-2', '删除', () => deleteFile(file))
        );
        return actionCell;
    }

    function renderGaps() {
        gapTable.innerHTML = '';
        const query = gapSearch.value;
        const rows = gaps.filter(gap => matchesAdminSearch(gap, query, ['question', 'knowledgeBase']));
        gapEmpty.style.display = rows.length === 0 ? 'block' : 'none';
        rows.forEach(gap => {
            const tr = document.createElement('tr');
            tr.append(
                cell(gap.question),
                cell(knowledgeBaseLabel(gap.knowledgeBase)),
                cell(String(gap.occurrences || 1)),
                cell(formatDateTime(gap.lastAskedAt)),
                cell(rowButton('check', '处理', () => resolveGap(gap)))
            );
            gapTable.appendChild(tr);
        });
    }

    function renderAudits() {
        auditTable.innerHTML = '';
        const query = auditSearch.value;
        const rows = audits.filter(event => {
            const flat = {
                timestamp: event.timestamp,
                action: actionLabel(event.action),
                actor: event.actor,
                knowledgeBase: event.knowledgeBase,
                fileName: event.fileName || event.details?.targetUsername || ''
            };
            return matchesAdminSearch(flat, query, ['timestamp', 'action', 'actor', 'knowledgeBase', 'fileName']);
        });
        auditEmpty.style.display = rows.length === 0 ? 'block' : 'none';
        rows.forEach(event => {
            const tr = document.createElement('tr');
            tr.append(
                cell(formatDateTime(event.timestamp)),
                cell(actionLabel(event.action)),
                cell(event.actor || '-'),
                cell(knowledgeBaseLabel(event.knowledgeBase || DEFAULT_KNOWLEDGE_BASE)),
                cell(event.fileName || event.details?.targetUsername || '-')
            );
            auditTable.appendChild(tr);
        });
    }

    function openCreateUserModal() {
        const form = document.createElement('form');
        form.className = 'admin-modal-form';
        form.innerHTML = `
            <label><span>用户名</span><input name="username" type="text" maxlength="100" required></label>
            <label><span>初始密码</span><input name="password" type="password" maxlength="200" required></label>
            <label><span>角色</span><select name="role"><option value="USER">普通用户</option><option value="ADMIN">管理员</option></select></label>
            <label class="admin-check-row"><input name="enabled" type="checkbox" checked><span>启用账号</span></label>
            <label class="admin-check-row"><input name="allKnowledgeBases" type="checkbox"><span>允许全部知识库</span></label>
            <label><span>知识库权限</span><select name="knowledgeBases" multiple size="6"></select></label>
            <button class="login-btn" type="submit">创建用户</button>
        `;
        const role = form.elements.role;
        const all = form.elements.allKnowledgeBases;
        const kbSelect = form.elements.knowledgeBases;
        fillSelect(kbSelect, manageableKnowledgeBaseNames(), null, null, [DEFAULT_KNOWLEDGE_BASE]);
        function sync() {
            const adminRole = role.value === 'ADMIN';
            all.checked = adminRole || all.checked;
            all.disabled = adminRole;
            kbSelect.disabled = adminRole || all.checked;
        }
        role.addEventListener('change', sync);
        all.addEventListener('change', sync);
        sync();
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await createAdminUser(authFetch, {
                    username: form.elements.username.value.trim(),
                    password: form.elements.password.value,
                    role: role.value,
                    enabled: form.elements.enabled.checked,
                    knowledgeBases: all.checked ? ['*'] : selectedValues(kbSelect)
                });
                closeModal();
                users = await listAdminUsers(authFetch);
                showAlert('用户已创建', 'success');
                renderUsers();
            });
        });
        openModal('新增用户', '', form);
    }

    function openPasswordModal(user) {
        const form = document.createElement('form');
        form.className = 'admin-modal-form';
        form.innerHTML = `
            <label><span>新密码</span><input name="password" type="password" maxlength="200" required></label>
            <button class="login-btn" type="submit">保存密码</button>
        `;
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await resetAdminUserPassword(authFetch, user.id, form.elements.password.value);
                closeModal();
                showAlert('密码已重置', 'success');
            });
        });
        openModal('重置密码', user.username, form);
    }

    function openCreateKnowledgeBaseModal() {
        const form = document.createElement('form');
        form.className = 'admin-modal-form';
        form.innerHTML = `
            <label><span>知识库名称</span><input name="name" type="text" maxlength="60" required></label>
            <label><span>负责人</span><select name="owners" multiple size="7"></select></label>
            <button class="login-btn" type="submit">创建知识库</button>
        `;
        fillUserSelect(form.elements.owners, enabledUsers(), [currentUser.username]);
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await createKnowledgeBase(authFetch, {
                    name: form.elements.name.value.trim(),
                    owners: selectedValues(form.elements.owners)
                });
                closeModal();
                await reloadKnowledgeBases();
                showAlert('知识库已创建', 'success');
            });
        });
        openModal('新增知识库', '知识库名称用文本输入，负责人从用户列表选择', form);
    }

    function openSetOwnersModal(kb) {
        const form = ownersForm(kb.owners || [], '保存负责人');
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await setKnowledgeBaseOwners(authFetch, kb.knowledgeBase, selectedValues(form.elements.owners));
                closeModal();
                await reloadKnowledgeBases();
                showAlert('负责人已保存', 'success');
            });
        });
        openModal('编辑负责人', kb.knowledgeBase, form);
    }

    function openAddOwnersModal(kb) {
        const form = ownersForm([], '添加负责人');
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await addKnowledgeBaseOwners(authFetch, kb.knowledgeBase, selectedValues(form.elements.owners));
                closeModal();
                await reloadKnowledgeBases();
                showAlert('负责人已添加', 'success');
            });
        });
        openModal('添加负责人', kb.knowledgeBase, form);
    }

    function openSelfTransferModal(kb) {
        const form = ownersForm([], '转移自己');
        Array.from(form.elements.owners.options).forEach(option => {
            option.disabled = option.value === currentUser.username;
        });
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await selfTransferKnowledgeBase(authFetch, kb.knowledgeBase, selectedValues(form.elements.owners));
                closeModal();
                await reloadKnowledgeBases();
                showAlert('负责人已转移', 'success');
            });
        });
        openModal('转移负责人', `${kb.knowledgeBase} · 将当前账号替换为所选用户`, form);
    }

    function openMembersModal(kb) {
        const form = document.createElement('form');
        form.className = 'admin-modal-form';
        form.innerHTML = `
            <label><span>访问用户</span><select name="members" multiple size="8"></select></label>
            <button class="login-btn" type="submit">保存分发</button>
        `;
        fillUserSelect(form.elements.members, enabledNormalUsers(), kb.members || []);
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                await setKnowledgeBaseMembers(authFetch, kb.knowledgeBase, selectedValues(form.elements.members));
                closeModal();
                await reloadKnowledgeBases();
                showAlert('访问权限已保存', 'success');
            });
        });
        openModal('分发访问权限', kb.knowledgeBase, form);
    }

    function ownersForm(selected, submitText) {
        const form = document.createElement('form');
        form.className = 'admin-modal-form';
        form.innerHTML = `
            <label><span>负责人</span><select name="owners" multiple size="8"></select></label>
            <button class="login-btn" type="submit">${submitText}</button>
        `;
        fillUserSelect(form.elements.owners, enabledUsers(), selected);
        return form;
    }

    function openUploadModal() {
        const form = document.createElement('form');
        form.className = 'admin-modal-form';
        form.innerHTML = `
            <label><span>知识库</span><select name="knowledgeBase" required></select></label>
            <label><span>文档负责人</span><select name="owner"></select></label>
            <label><span>文件</span><input name="file" type="file" accept=".pdf,.txt,.doc,.docx,.ppt,.pptx,.xls,.xlsx" required></label>
            <button class="login-btn" type="submit">上传文档</button>
            <p class="modal-message" data-upload-status></p>
        `;
        fillSelect(form.elements.knowledgeBase, manageableKnowledgeBaseNames(), selectedDocumentKnowledgeBase() || manageableKnowledgeBaseNames()[0], null);
        fillUserSelect(form.elements.owner, [{ username: '', role: '', enabled: true }, ...enabledUsers()], ['']);
        form.elements.owner.options[0].textContent = '不指定';
        const status = form.querySelector('[data-upload-status]');
        form.addEventListener('submit', event => {
            event.preventDefault();
            runAction(async () => {
                const file = form.elements.file.files[0];
                if (!file) return;
                const body = new FormData();
                body.append('file', file);
                body.append('knowledgeBase', form.elements.knowledgeBase.value);
                if (form.elements.owner.value) {
                    body.append('owner', form.elements.owner.value);
                }
                status.textContent = '正在上传...';
                const response = await authFetch(apiPath('/files/upload'), {
                    method: 'POST',
                    body
                });
                const data = await response.json().catch(() => ({}));
                if (!response.ok || data.success === false) {
                    throw new Error(data.error || '上传失败');
                }
                closeModal();
                documentKbFilter.value = form.elements.knowledgeBase.value;
                await reloadDocumentData();
                showAlert('文档已上传', 'success');
            });
        });
        openModal('上传文档', '知识库和负责人从下拉框选择', form);
    }

    async function reloadKnowledgeBases() {
        knowledgeBases = await listAdminKnowledgeBases(authFetch);
        fillKnowledgeBaseFilters();
        await reloadDocumentData();
        renderKnowledgeBases();
    }

    async function reloadDocumentData() {
        await Promise.all([loadDocuments(), loadGaps(), loadAudits()]);
        renderDocuments();
        renderGaps();
        renderAudits();
        refreshIcons();
    }

    async function downloadFile(file) {
        await runAction(async () => {
            const params = new URLSearchParams({ knowledgeBase: file.knowledgeBase });
            const response = await authFetch(apiPath(`/files/${encodeURIComponent(file.fileName)}/download?${params}`));
            if (!response.ok) throw new Error('下载失败');
            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = file.fileName;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        });
    }

    async function reindexFile(file) {
        await runAction(async () => {
            const params = new URLSearchParams({ knowledgeBase: file.knowledgeBase });
            const response = await authFetch(apiPath(`/files/${encodeURIComponent(file.fileName)}/reindex?${params}`), { method: 'POST' });
            if (!response.ok) throw new Error(await readApiError(response, '重新索引失败'));
            await reloadDocumentData();
            showAlert('文档已重新索引', 'success');
        });
    }

    async function deleteFile(file) {
        if (!confirm(`确定删除 "${file.fileName}" 吗？`)) return;
        await runAction(async () => {
            const params = new URLSearchParams({ knowledgeBase: file.knowledgeBase });
            const response = await authFetch(apiPath(`/files/${encodeURIComponent(file.fileName)}?${params}`), { method: 'DELETE' });
            if (!response.ok) throw new Error(await readApiError(response, '删除失败'));
            await reloadDocumentData();
            await reloadKnowledgeBases();
            showAlert('文档已删除', 'success');
        });
    }

    async function reindexAll() {
        await runAction(async () => {
            const response = await authFetch(apiPath('/files/refresh'), { method: 'POST' });
            if (!response.ok) throw new Error(await readApiError(response, '全量索引失败'));
            await reloadDocumentData();
            showAlert('全量索引已完成', 'success');
        });
    }

    async function resolveGap(gap) {
        await runAction(async () => {
            const params = new URLSearchParams({ knowledgeBase: gap.knowledgeBase });
            const response = await authFetch(apiPath(`/files/gaps/${encodeURIComponent(gap.id)}?${params}`), { method: 'DELETE' });
            if (!response.ok) throw new Error(await readApiError(response, '处理失败'));
            await reloadDocumentData();
            await reloadKnowledgeBases();
            showAlert('知识缺口已处理', 'success');
        });
    }

    async function generateFaqDraft() {
        const kb = selectedGapKnowledgeBase() || manageableKnowledgeBaseNames()[0] || DEFAULT_KNOWLEDGE_BASE;
        await runAction(async () => {
            const params = new URLSearchParams({ knowledgeBase: kb });
            const response = await authFetch(apiPath(`/files/faq-draft?${params}`));
            if (!response.ok) throw new Error(await readApiError(response, 'FAQ 草稿生成失败'));
            const data = await response.json();
            faqDraft.value = data.markdown || '';
            faqDraft.classList.remove('hidden');
            await loadAudits();
            renderAudits();
        });
    }

    function fillSelect(select, values, selected = null, emptyLabel = null, selectedValues = null) {
        select.innerHTML = '';
        if (emptyLabel !== null) {
            const option = document.createElement('option');
            option.value = '';
            option.textContent = emptyLabel;
            select.appendChild(option);
        }
        const selectedSet = new Set(Array.isArray(selectedValues) ? selectedValues : []);
        values.forEach(value => {
            const option = document.createElement('option');
            option.value = value;
            option.textContent = knowledgeBaseLabel(value);
            option.selected = selectedSet.has(value) || selected === value;
            select.appendChild(option);
        });
    }

    function fillUserSelect(select, options, selected = []) {
        select.innerHTML = '';
        const selectedSet = new Set(selected);
        options.forEach(user => {
            const option = document.createElement('option');
            option.value = user.username;
            option.textContent = user.username ? userOptionLabel(user) : '';
            option.selected = selectedSet.has(user.username);
            select.appendChild(option);
        });
    }

    function openModal(title, subtitle, content) {
        modalTitle.textContent = title;
        modalSubtitle.textContent = subtitle || '';
        modalBody.innerHTML = '';
        modalBody.appendChild(content);
        modal.classList.remove('hidden');
        modal.setAttribute('aria-hidden', 'false');
        refreshIcons();
        const first = modalBody.querySelector('input, select, button');
        first?.focus();
    }

    function closeModal() {
        modal.classList.add('hidden');
        modal.setAttribute('aria-hidden', 'true');
        modalBody.innerHTML = '';
    }

    async function runAction(action) {
        clearAlert();
        try {
            await action();
        } catch (error) {
            showAlert(error.message || '操作失败', 'error');
        } finally {
            refreshIcons();
        }
    }

    function switchTab(tab) {
        navButtons.forEach(button => button.classList.toggle('active', button.dataset.adminTab === tab));
        document.querySelectorAll('.admin-tab').forEach(panel => {
            panel.classList.toggle('active', panel.id === `admin-tab-${tab}`);
        });
    }

    function textBlock(title, subtitle) {
        const div = document.createElement('div');
        const strong = document.createElement('strong');
        strong.textContent = title || '-';
        div.appendChild(strong);
        if (subtitle) {
            const small = document.createElement('small');
            small.textContent = subtitle;
            div.appendChild(small);
        }
        return div;
    }

    function cell(content) {
        const td = document.createElement('td');
        if (content instanceof Node) {
            td.appendChild(content);
        } else {
            td.textContent = content;
        }
        return td;
    }

    function chips(values) {
        const wrap = document.createElement('div');
        wrap.className = 'admin-chip-list';
        const list = values && values.length > 0 ? values : ['-'];
        list.forEach(value => {
            const span = document.createElement('span');
            span.className = 'admin-chip';
            span.textContent = value;
            wrap.appendChild(span);
        });
        return wrap;
    }

    function rowButton(icon, label, handler) {
        const button = document.createElement('button');
        button.className = 'icon-text-btn compact';
        button.type = 'button';
        button.innerHTML = `<i data-lucide="${icon}"></i>${label}`;
        button.addEventListener('click', event => {
            event.preventDefault();
            handler();
        });
        return button;
    }

    function statusSummary(status) {
        const div = document.createElement('div');
        if (!status) {
            div.textContent = '-';
            return div;
        }
        div.appendChild(textBlock(
            `${status.indexedFiles}/${status.totalFiles} 已索引`,
            `${status.failedFiles} 失败 · ${status.staleFiles} 过期 · ${status.knowledgeGaps} 缺口`
        ));
        return div;
    }

    function actionLabel(action) {
        const labels = {
            CREATE_USER: '创建用户',
            UPDATE_USER: '更新用户',
            RESET_USER_PASSWORD: '重置密码',
            CREATE_KNOWLEDGE_BASE: '创建知识库',
            UPDATE_KNOWLEDGE_BASE_OWNERS: '编辑负责人',
            ADD_KNOWLEDGE_BASE_OWNERS: '添加负责人',
            TRANSFER_KNOWLEDGE_BASE_OWNER: '转移负责人',
            UPDATE_KNOWLEDGE_BASE_MEMBERS: '分发权限'
        };
        return labels[action] || actionText(action);
    }

    function formatSize(size) {
        const bytes = Number(size || 0);
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
        if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${bytes} B`;
    }

    navButtons.forEach(button => {
        button.addEventListener('click', () => switchTab(button.dataset.adminTab));
    });
    refreshBtn.addEventListener('click', () => runAction(loadAll));
    openCreateUserBtn.addEventListener('click', openCreateUserModal);
    openCreateKbBtn.addEventListener('click', openCreateKnowledgeBaseModal);
    openUploadDocumentBtn.addEventListener('click', openUploadModal);
    reindexAllBtn.addEventListener('click', reindexAll);
    generateFaqBtn.addEventListener('click', generateFaqDraft);
    closeModalBtn.addEventListener('click', closeModal);
    modal.addEventListener('click', event => {
        if (event.target === modal) closeModal();
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !modal.classList.contains('hidden')) closeModal();
    });

    userSearch.addEventListener('input', renderUsers);
    kbSearch.addEventListener('input', renderKnowledgeBases);
    documentSearch.addEventListener('input', renderDocuments);
    gapSearch.addEventListener('input', renderGaps);
    auditSearch.addEventListener('input', renderAudits);
    documentKbFilter.addEventListener('change', () => runAction(async () => {
        await loadDocuments();
        renderDocuments();
    }));
    gapKbFilter.addEventListener('change', () => runAction(async () => {
        faqDraft.classList.add('hidden');
        await loadGaps();
        renderGaps();
    }));

    requireManager()
        .then(ok => ok ? loadAll() : null)
        .catch(error => showAlert(error.message || '后台加载失败', 'error'))
        .finally(refreshIcons);
});
}
