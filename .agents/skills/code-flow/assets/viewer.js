(() => {
  'use strict';
  const data = JSON.parse(document.getElementById('flow-data').textContent);
  const root = document.getElementById('code-flow');
  const $ = id => document.getElementById(id);
  const calls = new Map(data.calls.map(call => [call.id, call]));
  const sources = new Map(data.sources.map(source => [source.id, source]));
  const expanded = new Set();
  const W = 240, GAP = 90, STEP = W + GAP;
  const measure = document.createElement('div');
  measure.id = 'measure';
  root.append(measure);
  let plan, selected = {call: data.entry}, visible = new Map();

  function element(tag, className, text) {
    const el = document.createElement(tag);
    if (className) el.className = className;
    if (text !== undefined) el.textContent = text;
    return el;
  }
  function size(el, width) {
    el.style.width = width + 'px';
    el.style.position = 'static';
    measure.append(el);
    const height = Math.ceil(el.getBoundingClientRect().height);
    el.remove();
    el.style.position = '';
    return height;
  }
  function place(el, x, y, width, height) {
    Object.assign(el.style, {left: x + 'px', top: y + 'px', width: width + 'px'});
    if (height !== undefined) el.style.height = height + 'px';
    return el;
  }
  function head(call, asyncRoot) {
    const button = element('button', 'call-head');
    button.type = 'button';
    button.append(element('small', '', asyncRoot ? '별도 비동기 스택' : '호출 · ' + call.id));
    button.append(element('strong', '', call.class), element('code', '', call.method));
    return button;
  }
  function textItem(text, x, y, width, className, owner, source) {
    const el = element('button', className, text);
    el.type = 'button';
    const height = Math.max(36, size(el, width));
    el.addEventListener('click', () => select({call: owner.id, source, title: text}));
    plan.items.push({el, x, y, width, height});
    return {bottom: y + height, right: x + width};
  }

  function layoutCall(id, x, top, ancestors, asyncRoot = false) {
    const call = calls.get(id);
    const txPad = call.tx ? Math.max(34, size(element('div', 'tx-label', call.tx.id + ' · ' + call.tx.label), W)) + 12 : 0;
    const y = top + txPad;
    const header = head(call, asyncRoot), hh = size(header, W - 6);
    header.style.width = '100%';
    const box = {id, x, y, top, header, hh, ancestors, asyncRoot, waits: [], right: x + W};
    plan.boxes.push(box);
    let end = layoutSteps(call.steps, box, y + hh + 22, false);
    const foot = element('div', 'call-foot', '반환 / 종료\n' + call.returns);
    const fh = size(foot, W - 28);
    box.bottom = Math.max(end.bottom + fh + 30, y + hh + 85);
    box.right = Math.max(box.right, end.right);
    box.foot = foot;
    if (call.tx) {
      plan.bands.unshift({x: x - 12, y: top, right: box.right + 12, bottom: box.bottom + 12, text: call.tx.id + ' · ' + call.tx.label});
    }
    return box;
  }

  function layoutSteps(steps, owner, top, inBranch) {
    let cursor = top, right = owner.x + W;
    for (const step of steps) {
      if (step.type === 'call') {
        const child = layoutCall(step.target, owner.x + STEP, cursor, [...owner.ancestors, owner.id]);
        plan.wires.push({x1: owner.x + W, y1: child.y + 26, x2: child.x, y2: child.y + 26, type: 'call'});
        plan.wires.push({x1: child.x, y1: child.bottom - 16, x2: owner.x + W, y2: child.bottom - 16, type: 'return'});
        owner.waits.push({top: child.y + 26, bottom: child.bottom - 16});
        cursor = child.bottom + 36;
        right = Math.max(right, child.right + (calls.get(child.id).tx ? 12 : 0));
      } else if (step.type === 'async') {
        const x = owner.x + STEP;
        const text = step.label + '\n' + step.trigger + '\n' + (expanded.has(step.target) ? '접기 ←' : '자세히 보기 →');
        const el = element('button', 'async-button', text);
        el.type = 'button'; el.dataset.async = step.target;
        el.setAttribute('aria-expanded', String(expanded.has(step.target)));
        const height = Math.max(90, size(el, W));
        el.addEventListener('click', () => toggleAsync(step.target));
        plan.items.push({el, x, y: cursor, width: W, height});
        const anchor = {target: step.target, x, y: cursor, height, trigger: step.trigger, source: step.source, owner: owner.id};
        plan.async.push(anchor);
        plan.wires.push({x1: owner.x + W, y1: cursor + 28, x2: x, y2: cursor + 28, type: 'async'});
        cursor += height + 30; right = Math.max(right, x + W);
      } else if (step.type === 'branch') {
        const x = owner.x + STEP - 12;
        const groupTop = cursor;
        let boxRight = x + W + 24;
        cursor = textItem('alt · ' + step.label, x + 1, cursor + 1, W + 22, 'branch-label', owner, step.source).bottom + 12;
        for (const branch of step.cases) {
          cursor = textItem(branch.when, x + 10, cursor, W + 4, 'case-label', owner, step.source).bottom + 12;
          const end = layoutSteps(branch.steps, owner, cursor, true);
          boxRight = Math.max(boxRight, end.right + 12);
          cursor = Math.max(end.bottom, cursor + 10) + 18;
        }
        plan.groups.unshift({x, y: groupTop, width: boxRight - x, height: cursor - groupTop});
        right = Math.max(right, boxRight); cursor += 24;
      } else {
        const x = inBranch ? owner.x + STEP : owner.x + 12;
        const width = inBranch ? W : W - 24;
        const prefix = step.type === 'return' ? '↩ ' : step.type === 'throw' ? '예외 · ' : '';
        const end = textItem(prefix + step.text, x, cursor, width, 'step ' + step.type, owner, step.source);
        if (inBranch) plan.wires.push({x1: owner.x + W, y1: cursor + 18, x2: x, y2: cursor + 18, type: 'call'});
        cursor = end.bottom + 20; right = Math.max(right, end.right);
      }
    }
    return {bottom: cursor, right};
  }

  function addWire(svg, wire) {
    const ns = 'http://www.w3.org/2000/svg';
    const path = document.createElementNS(ns, 'path');
    path.setAttribute('class', wire.type);
    path.setAttribute('d', `M${wire.x1} ${wire.y1} H${(wire.x1 + wire.x2) / 2} V${wire.y2} H${wire.x2}`);
    svg.append(path);
    const tip = document.createElementNS(ns, 'path'), dx = wire.x1 < wire.x2 ? -6 : 6;
    tip.setAttribute('d', `M${wire.x2 + dx} ${wire.y2 - 4} L${wire.x2} ${wire.y2} L${wire.x2 + dx} ${wire.y2 + 4}`);
    if (wire.type === 'async') tip.setAttribute('class', 'async');
    svg.append(tip);
  }

  function render() {
    plan = {boxes: [], items: [], wires: [], bands: [], groups: [], async: [], labels: []};
    const entry = layoutCall(data.entry, 28, 42, []);
    let right = entry.right + 30, bottom = entry.bottom + 45;
    for (let i = 0; i < plan.async.length; i++) {
      const anchor = plan.async[i];
      if (!expanded.has(anchor.target)) continue;
      const x = right + GAP;
      const child = layoutCall(anchor.target, x, anchor.y, [], true);
      const label = element('div', 'async-label', '별도 스택 · ' + anchor.trigger + ' · HTTP 응답과 상대 순서 미확정');
      const h = size(label, W * 2);
      plan.labels.push({el: label, x, y: anchor.y - h - 8, width: W * 2, height: h});
      plan.wires.push({x1: anchor.x + W, y1: anchor.y + 28, x2: child.x, y2: child.y + 26, type: 'async'});
      right = Math.max(child.right + 30, x + W * 2 + 20);
      bottom = Math.max(bottom, child.bottom + 45);
    }
    visible = new Map(plan.boxes.map(box => [box.id, box]));
    const canvas = $('canvas'); canvas.replaceChildren();
    canvas.style.width = Math.max(right, $('viewport').clientWidth - 2) + 'px';
    canvas.style.height = bottom + 'px';
    for (const band of plan.bands) {
      const el = place(element('div', 'tx'), band.x, band.y, band.right - band.x, band.bottom - band.y);
      el.append(element('span', 'tx-label', band.text));canvas.append(el);
    }
    for (const group of plan.groups) canvas.append(place(element('div', 'alt'), group.x, group.y, group.width, group.height));
    for (const box of plan.boxes) {
      const el = place(element('div', 'activation' + (box.asyncRoot ? ' async-root' : '')), box.x, box.y, W, box.bottom - box.y);
      el.dataset.call = box.id;
      box.header.addEventListener('click', () => select({call: box.id}));
      el.append(box.header);
      for (const wait of box.waits) {
        const bar = place(element('div', 'wait'), 0, wait.top - box.y, W - 6, Math.max(8, wait.bottom - wait.top));
        if (wait.bottom - wait.top > 50) bar.append(element('span', '', '하위 호출 반환 대기'));
        el.append(bar);
      }
      el.append(box.foot);canvas.append(el);
    }
    for (const item of [...plan.items, ...plan.labels]) canvas.append(place(item.el, item.x, item.y, item.width, item.height));
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.classList.add('wire');svg.setAttribute('width', String(right));svg.setAttribute('height', String(bottom));svg.setAttribute('aria-hidden', 'true');
    plan.wires.forEach(wire => addWire(svg, wire));canvas.append(svg);
    if (!visible.has(selected.call)) selected = {call: data.entry};
    select(selected);syncPan();
  }

  function select(selection) {
    selected = selection;
    const call = calls.get(selection.call), box = visible.get(selection.call);
    const source = sources.get(selection.source || call.source);
    $('selected').textContent = selection.title || call.class + '.' + call.method;
    $('explanation').textContent = (call.note || '호출 박스는 반환까지 유지됩니다.') + (call.tx ? ' · ' + call.tx.id + ': ' + call.tx.label : '');
    $('code').textContent = source.excerpt;
    $('source').textContent = `${source.path}:${source.start}-${source.end} · SHA-256 ${source.sha256}`;
    root.querySelectorAll('[data-call]').forEach(el => {
      el.classList.toggle('selected', el.dataset.call === selection.call);
      el.classList.toggle('ancestor', box.ancestors.includes(el.dataset.call));
      el.querySelector('button').setAttribute('aria-pressed', String(el.dataset.call === selection.call));
    });
  }
  function toggleAsync(id) {
    const open = !expanded.has(id);
    if (open) expanded.add(id); else expanded.delete(id);
    render();
    const button = [...root.querySelectorAll('[data-async]')].find(el => el.dataset.async === id);
    button.focus({preventScroll: true});
    if (open) {
      const box = visible.get(id);
      $('viewport').scrollTo({left: Math.max(0, box.x - W - GAP), top: Math.max(0, box.top - 90),
        behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'});
    }
  }
  function syncPan() {
    const viewport = $('viewport');
    $('pan').max = String(Math.max(0, viewport.scrollWidth - viewport.clientWidth));
    $('pan').value = String(viewport.scrollLeft);
  }
  $('title').textContent = data.title;
  $('description').textContent = data.description || '호출의 뿌리를 유지하며 클래스·메서드·분기·비동기를 탐색합니다.';
  $('snapshot').textContent = '생성 ' + data.snapshot.generatedAt + ' · Git ' + (data.snapshot.commit || '없음') + ' · 현재 파일 변경 여부는 check 명령으로 확인';
  $('origin').textContent = calls.get(data.entry).class + '.' + calls.get(data.entry).method;
  for (const [field, label] of [['omitted', '생략 범위'], ['uncertain', '미확정 사항']]) {
    $('coverage').append(element('h3', '', label));
    const list = element('ul');
    (data[field].length ? data[field] : ['별도 기록 없음 · 분석의 완전성을 보장하지 않음']).forEach(text => list.append(element('li', '', text)));
    $('coverage').append(list);
  }
  $('home').addEventListener('click', () => {$('viewport').scrollTo({left:0, top:0, behavior:'instant'});select({call:data.entry});});
  $('pan').addEventListener('input', () => {$('viewport').scrollLeft = Number($('pan').value);});
  $('right').addEventListener('click', () => {$('viewport').scrollLeft += Math.max(220, $('viewport').clientWidth * .65);});
  $('viewport').addEventListener('scroll', syncPan, {passive: true});
  new ResizeObserver(syncPan).observe($('viewport'));
  render();
})();
