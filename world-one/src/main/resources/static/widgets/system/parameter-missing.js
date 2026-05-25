const STYLE_ID = 'sys-parameter-missing-styles';

const CSS = `
.spm-root{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:var(--text,#d0d8f0);background:var(--surface,#13151f);border:1px solid var(--border,#272b3e);border-radius:10px;padding:12px;display:flex;flex-direction:column;gap:10px}
.spm-title{font-size:14px;font-weight:700;color:var(--text,#d0d8f0)}
.spm-detail{font-size:12px;color:var(--text-dim,#9ba3c2);line-height:1.45}
.spm-row{display:flex;flex-direction:column;gap:4px}
.spm-section{display:flex;flex-direction:column;gap:8px}
.spm-section-title{font-size:12px;font-weight:600;color:var(--text,#d0d8f0)}
.spm-known{border:1px dashed var(--border,#272b3e);border-radius:8px;padding:6px 8px;background:var(--surface2,#191c29)}
.spm-label{font-size:12px;font-weight:500;color:var(--text,#d0d8f0)}
.spm-input{background:var(--surface2,#191c29);border:1px solid var(--border,#272b3e);border-radius:6px;color:var(--text,#d0d8f0);padding:7px 9px;font-size:12px;width:100%;box-sizing:border-box}
.spm-input.spm-error-frame{border-color:var(--danger,#ff4f6a)}
.spm-btn{align-self:flex-start;background:var(--accent,#7c6ff7);border:0;border-radius:7px;color:white;padding:7px 12px;font-size:12px;cursor:pointer}
.spm-btn:disabled{opacity:.55;cursor:default}
.spm-error{font-size:12px;color:var(--danger,#ff4f6a)}
.spm-ok{font-size:12px;color:var(--success,#4fbf8f)}
`;

let cleanupFns = [];

function hasKeys(v) {
  return !!(v && typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length > 0);
}

function ensureStyles() {
  if (document.getElementById(STYLE_ID)) return;
  const s = document.createElement('style');
  s.id = STYLE_ID;
  s.textContent = CSS;
  document.head.appendChild(s);
}

function esc(s) {
  return String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function lookupProvided(provided, path) {
  if (!provided) return undefined;
  const parts = path.split('.');
  let cur = provided;
  for (const part of parts) {
    if (cur == null || typeof cur !== 'object') return undefined;
    cur = cur[part];
  }
  return cur;
}

function setNested(obj, path, value) {
  const parts = path.split('.');
  let cur = obj;
  for (let i = 0; i < parts.length - 1; i++) {
    const k = parts[i];
    if (typeof cur[k] !== 'object' || cur[k] === null) cur[k] = {};
    cur = cur[k];
  }
  cur[parts[parts.length - 1]] = value;
}

function flattenProvided(obj, prefix = '') {
  if (!obj || typeof obj !== 'object') return [];
  const out = [];
  Object.entries(obj).forEach(([k, v]) => {
    const path = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) out.push(...flattenProvided(v, path));
    else out.push({ path, value: v });
  });
  return out;
}

function inferTypeFromValue(v) {
  if (typeof v === 'number') return 'number';
  if (typeof v === 'boolean') return 'boolean';
  return 'text';
}

function fieldList(event) {
  const widget = event?.widget || {};
  const schema = widget.schema || {};
  const provided = event?.business_data?.provided_parameters || event?.business_data?.resume_args?.parameters || {};
  const withProvidedDefaults = (arr) => {
    const out = Array.isArray(arr) ? arr.slice() : [];
    const names = new Set(out.map(f => typeof f === 'string' ? f : String(f?.name || '')));
    const parents = new Set();
    out.forEach(f => {
      const n = typeof f === 'string' ? f : String(f?.name || '');
      const dot = n.indexOf('.');
      if (dot > 0) parents.add(n.substring(0, dot));
    });
    flattenProvided(provided)
      .filter(item => {
        if (!item.path || names.has(item.path)) return false;
        const dot = item.path.indexOf('.');
        if (dot <= 0) return false;
        return parents.has(item.path.substring(0, dot));
      })
      .forEach(item => {
        out.unshift({
          name: item.path,
          type: inferTypeFromValue(item.value),
          required: false,
          editable: true,
          detail: '已识别参数'
        });
      });
    return out;
  };
  if (Array.isArray(schema.fields) && schema.fields.length) {
    return withProvidedDefaults(schema.fields.map(f => (typeof f === 'string' ? { name: f, type: 'text', required: true, editable: true } : f)));
  }
  if (Array.isArray(event?.missing_parameters) && event.missing_parameters.length) {
    return withProvidedDefaults(event.missing_parameters.map(item => (
      typeof item === 'string'
        ? { name: item, type: 'text', required: true, editable: true }
        : item
    )));
  }
  const names = event?.business_data?.missing_parameters || [];
  return withProvidedDefaults(names.map(name => ({ name, type: 'text', required: true, editable: true })));
}

function pathOf(parentPath, name) {
  return parentPath ? `${parentPath}.${name}` : name;
}

function normalizeInputType(fieldType) {
  const t = String(fieldType || '').toLowerCase();
  if (t === 'number') return 'number';
  if (t === 'date') return 'date';
  if (t === 'datetime') return 'datetime-local';
  if (t === 'time') return 'time';
  if (t === 'email') return 'email';
  if (t === 'url') return 'url';
  if (t === 'tel') return 'tel';
  return 'text';
}

function renderField(field, provided, parentPath = '') {
  const name = field.name || '';
  const path = pathOf(parentPath, name);
  const type = field.type || field.declared_type || 'text';
  if (type === 'object' && Array.isArray(field.fields)) {
    return `<div class="spm-row" style="border:1px solid var(--border,#272b3e);border-radius:8px;padding:8px;background:var(--surface2,#191c29)">
      <div class="spm-label" style="font-weight:700">${esc(name)}</div>
      ${field.fields.map(f => renderField(f, provided, path)).join('')}
    </div>`;
  }
  const value = lookupProvided(provided, path);
  const reqAttr = field.required === false ? 'false' : 'true';
  let control = '';
  if (type === 'enum' && Array.isArray(field.possible_values) && field.possible_values.length) {
    control = `<select class="spm-input" data-param="${esc(path)}" data-required="${reqAttr}">
      <option value=""></option>
      ${field.possible_values.map(v => {
        const sv = String(v);
        const selected = String(value ?? '') === sv ? ' selected' : '';
        return `<option value="${esc(sv)}"${selected}>${esc(sv)}</option>`;
      }).join('')}
    </select>`;
  } else if (type === 'boolean') {
    const v = String(value ?? '');
    control = `<select class="spm-input" data-param="${esc(path)}" data-required="${reqAttr}">
      <option value=""></option>
      <option value="true"${v === 'true' ? ' selected' : ''}>true</option>
      <option value="false"${v === 'false' ? ' selected' : ''}>false</option>
    </select>`;
  } else {
    control = `<input class="spm-input" type="${normalizeInputType(type)}" data-param="${esc(path)}" data-required="${reqAttr}" value="${esc(value == null || typeof value === 'object' ? '' : String(value))}" placeholder="${esc(field.placeholder || '')}">`;
  }
  return `<div class="spm-row">
    <label class="spm-label">${esc(name.includes('.') ? name.split('.').slice(-1)[0] : name)}${field.required === false ? '' : ' <span style="color:var(--danger,#ff4f6a)">*</span>'}</label>
    ${control}
    ${field.detail ? `<div class="spm-detail">${esc(field.detail)}</div>` : ''}
  </div>`;
}

export function mount(targetEl, hostApi, data) {
  ensureStyles();
  const event = data?.event || {};
  const bd = event.business_data || {};
  const directMode = !event.id;
  const loadedState = hasKeys(data?.__widget_state) ? data.__widget_state : {};
  const readOnly = data?.__readonly === true || event?.status === 'resolved' || event?.status === 'submitted';
  const baseProvided = directMode
    ? (data?.provided_parameters || {})
    : (bd.provided_parameters || bd.resume_args?.parameters || {});
  const provided = Object.assign({}, baseProvided, loadedState);
  const fields = directMode ? fieldList(data || {}) : fieldList(event);
  const missingFields = fields.filter(f => f && f.required !== false);
  const knownFields = fields.filter(f => f && f.required === false);
  const title = event?.widget?.schema?.title || '补充参数后继续执行';
  const decision = event?.tags?.decision || event?.source?.id || '';

  targetEl.innerHTML = `<div class="spm-root">
    <div class="spm-title">${esc(title)}</div>
    <div class="spm-detail">${decision ? `决策 ${esc(decision)} 缺少参数。` : '当前流程缺少参数。'}请填写后继续执行。</div>
    <div class="spm-section">
      <div class="spm-section-title">待补充必填参数</div>
      ${missingFields.map(f => renderField(f, provided)).join('')}
    </div>
    ${knownFields.length ? `<details class="spm-known">
      <summary class="spm-section-title">已识别参数（可编辑）</summary>
      <div class="spm-section" style="margin-top:8px">${knownFields.map(f => renderField(f, provided)).join('')}</div>
    </details>` : ''}
    <button class="spm-btn" data-role="submit"${readOnly ? ' disabled' : ''}>Continue</button>
    <div class="spm-error" data-role="error" style="display:none"></div>
    <div class="spm-ok" data-role="ok" style="display:${readOnly ? '' : 'none'}">${readOnly ? '已提交参数' : ''}</div>
  </div>`;

  const btn = targetEl.querySelector('[data-role="submit"]');
  const errEl = targetEl.querySelector('[data-role="error"]');
  const okEl = targetEl.querySelector('[data-role="ok"]');
  const inputs = Array.from(targetEl.querySelectorAll('[data-param]'));
  if (hasKeys(loadedState)) {
    load(targetEl, loadedState);
  }
  if (readOnly) {
    inputs.forEach(el => el.disabled = true);
  }

  const onSubmit = async () => {
    if (readOnly) return;
    inputs.forEach(el => el.classList.remove('spm-error-frame'));
    const missing = inputs
      .filter(el => el.getAttribute('data-required') === 'true' && String(el.value || '').trim() === '')
      .map(el => el.getAttribute('data-param'));
    if (missing.length) {
      inputs.forEach(el => {
        if (missing.includes(el.getAttribute('data-param'))) el.classList.add('spm-error-frame');
      });
      if (errEl) { errEl.textContent = '请补齐必填字段：' + missing.join(', '); errEl.style.display = ''; }
      return;
    }
    const params = generate(targetEl, data);
    btn.disabled = true;
    if (errEl) errEl.style.display = 'none';
    try {
      const action = resolveSubmitAction(data, event);
      const body = action && hostApi.invokeAction
        ? await hostApi.invokeAction(action, { parameters: params, event, data })
        : await legacySubmit(hostApi, data, event, params, directMode);
      if (!body || body.status === 'failed') {
        throw new Error(body?.error || body?.reason || body?.resume_result?.error || 'submit failed');
      }
      if (hostApi.markProcessedWithData) {
        await hostApi.markProcessedWithData(hasKeys(params)
          ? { providedParameters: params, widgetState: params }
          : {});
      } else {
        await hostApi.markProcessed?.();
      }
      const widgets = Array.isArray(body.widgets)
        ? body.widgets
        : [body.pre_widget, body.widget || body.resume_result?.html_widget, body.final_widget].filter(Boolean);
      for (const w of widgets) {
        await hostApi.appendWidget?.(w);
      }
      try { window.reloadPendingEvents && window.reloadPendingEvents(); } catch (_) {}
      if (okEl) {
        okEl.textContent = body.status === 'need_input'
          ? '已提交。流程仍需要补充更多参数，请查看新事件。'
          : '已提交，决策执行已继续。';
        okEl.style.display = '';
      }
    } catch (e) {
      btn.disabled = false;
      if (errEl) { errEl.textContent = e.message || String(e); errEl.style.display = ''; }
    }
  };

  btn?.addEventListener('click', onSubmit);
  cleanupFns.push(() => btn?.removeEventListener('click', onSubmit));
}

function resolveSubmitAction(data, event) {
  if (data?.submit_action) return data.submit_action;
  if (event?.business_data?.submit_action) return event.business_data.submit_action;
  if (event?.id) return { kind: 'world_event_submit', event_id: event.id };
  if (data?.submit_tool) {
    return { kind: 'tool', name: data.submit_tool, args: data.submit_args || {} };
  }
  return { kind: 'tool', name: 'world_invoke_capability', args: {} };
}

async function legacySubmit(hostApi, data, event, params, directMode) {
  if (directMode) {
    const submitTool = data?.submit_tool || 'world_invoke_capability';
    const submitArgs = Object.assign({}, data?.submit_args || {});
    if (submitTool === 'world_invoke_capability') {
      submitArgs.session_id = data?.session_id;
      submitArgs.capability_id = data?.capability?.id;
      submitArgs.input_text = data?.input_text || '';
      submitArgs.parameters = params;
    } else {
      submitArgs.parameters = Object.assign({}, submitArgs.parameters || {}, params);
    }
    await hostApi.callTool?.(submitTool, submitArgs);
    return { status: 'success' };
  }
  if (!hostApi.invokeAction) {
    throw new Error('Host action invoker unavailable');
  }
  return hostApi.invokeAction({ kind: 'world_event_submit', event_id: event?.id }, { parameters: params, event, data });
}

export function unmount() {
  cleanupFns.forEach(fn => { try { fn(); } catch (_) {} });
  cleanupFns = [];
}

export function generate(targetEl, data) {
  const event = data?.event || {};
  const bd = event.business_data || {};
  const directMode = !event.id;
  const baseProvided = directMode
    ? (data?.provided_parameters || {})
    : (bd.provided_parameters || bd.resume_args?.parameters || {});
  const out = JSON.parse(JSON.stringify(baseProvided || {}));
  const inputs = Array.from(targetEl?.querySelectorAll?.('[data-param]') || []);
  inputs.forEach(el => {
    const path = el.getAttribute('data-param');
    const value = String(el.value || '').trim();
    if (path && value) setNested(out, path, value);
  });
  return out;
}

export function load(targetEl, state) {
  if (!targetEl || !hasKeys(state)) return;
  const inputs = Array.from(targetEl.querySelectorAll('[data-param]'));
  inputs.forEach(el => {
    const path = el.getAttribute('data-param');
    if (!path) return;
    const v = lookupProvided(state, path);
    if (v == null || typeof v === 'object') return;
    el.value = String(v);
  });
  const btn = targetEl.querySelector('[data-role="submit"]');
  const okEl = targetEl.querySelector('[data-role="ok"]');
  if (btn) btn.disabled = true;
  inputs.forEach(el => { el.disabled = true; });
  if (okEl) {
    okEl.textContent = '已提交参数';
    okEl.style.display = '';
  }
}
