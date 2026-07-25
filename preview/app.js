/**
 * NoteSketch 可交互预览
 * 行为对齐：MainActivity / AddNoteActivity / NoteDetailActivity /
 * NoteAdapter / TimelineAdapter / Ebbinghaus / DateUtil
 */
(function () {
  const M = window.NoteSketchModel;
  const STORAGE_KEY = "notesketch-preview-notes-v1";

  /** @type {{id:number,title:string,content:string,createdAt:number,stage:number,nextReviewTime:number,finished:boolean}[]} */
  let notes = loadNotes();
  let nextId = notes.reduce((m, n) => Math.max(m, n.id), 0) + 1;
  let currentNoteId = null;
  let toastTimer = null;

  const els = {
    listView: document.getElementById("view-list"),
    addView: document.getElementById("view-add"),
    detailView: document.getElementById("view-detail"),
    noteList: document.getElementById("note-list"),
    emptyState: document.getElementById("empty-state"),
    fab: document.getElementById("fab-add"),
    titleInput: document.getElementById("input-title"),
    contentInput: document.getElementById("input-content"),
    btnSave: document.getElementById("btn-save"),
    detailTitle: document.getElementById("detail-title"),
    detailBody: document.getElementById("detail-body"),
    timelineList: document.getElementById("timeline-list"),
    toast: document.getElementById("toast"),
    syncMeta: document.getElementById("sync-meta"),
  };

  function loadNotes() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return seedNotes();
      return JSON.parse(raw);
    } catch {
      return seedNotes();
    }
  }

  function seedNotes() {
    const now = Date.now();
    const day = 24 * 60 * 60 * 1000;
    const aCreated = now - 2 * day;
    const bCreated = now - 8 * day;
    return [
      {
        id: 1,
        title: "英语单词 Unit 3",
        content: "acquire / retain / recall — 主动回忆比反复阅读更有效。",
        createdAt: aCreated,
        stage: 1,
        nextReviewTime: M.reviewTimeFor(aCreated, 1),
        finished: false,
      },
      {
        id: 2,
        title: "艾宾浩斯曲线要点",
        content: "间隔重复：1、2、4、7、15、30 天六个节点。",
        createdAt: bCreated,
        stage: 3,
        nextReviewTime: M.reviewTimeFor(bCreated, 3),
        finished: false,
      },
    ];
  }

  function saveNotes() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(notes));
  }

  function showToast(msg) {
    els.toast.textContent = msg;
    els.toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => els.toast.classList.remove("show"), 1600);
  }

  function showView(name) {
    els.listView.classList.toggle("active", name === "list");
    els.addView.classList.toggle("active", name === "add");
    els.detailView.classList.toggle("active", name === "detail");
  }

  function sortedNotes() {
    return [...notes].sort((a, b) => a.nextReviewTime - b.nextReviewTime);
  }

  function renderList() {
    const list = sortedNotes();
    els.emptyState.classList.toggle("show", list.length === 0);
    els.noteList.innerHTML = "";

    list.forEach((note) => {
      const row = document.createElement("article");
      row.className = "note-row";
      row.setAttribute("role", "button");
      row.tabIndex = 0;

      const title = document.createElement("div");
      title.className = "note-title";
      title.textContent = note.title;

      const preview = document.createElement("div");
      preview.className = "note-preview";
      preview.textContent = note.content || "";

      const footer = document.createElement("div");
      footer.className = "note-footer";

      const review = document.createElement("div");
      review.className = "note-review";
      review.textContent = M.reviewText(note.nextReviewTime, note.finished);
      if (M.isDue(note.nextReviewTime, note.finished)) {
        review.classList.add("due");
      }

      const del = document.createElement("button");
      del.type = "button";
      del.className = "btn-delete";
      del.textContent = M.strings.delete;
      del.addEventListener("click", (e) => {
        e.stopPropagation();
        notes = notes.filter((n) => n.id !== note.id);
        saveNotes();
        renderList();
      });

      footer.append(review, del);
      row.append(title, preview, footer);

      const open = () => openDetail(note.id);
      row.addEventListener("click", open);
      row.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          open();
        }
      });

      els.noteList.appendChild(row);
    });
  }

  function openAdd() {
    els.titleInput.value = "";
    els.contentInput.value = "";
    showView("add");
    setTimeout(() => els.titleInput.focus(), 50);
  }

  function saveNote() {
    const title = els.titleInput.value.trim();
    if (!title) {
      showToast(M.strings.toastNeedTitle);
      return;
    }
    const now = Date.now();
    notes.push({
      id: nextId++,
      title,
      content: els.contentInput.value.trim(),
      createdAt: now,
      stage: 0,
      nextReviewTime: M.reviewTimeFor(now, 0),
      finished: false,
    });
    saveNotes();
    showToast(M.strings.toastSaved);
    renderList();
    showView("list");
  }

  function openDetail(id) {
    const note = notes.find((n) => n.id === id);
    if (!note) {
      showToast("笔记不存在");
      showView("list");
      return;
    }
    currentNoteId = id;
    els.detailTitle.textContent = note.title;
    els.detailBody.textContent = note.content || M.strings.noBody;
    renderTimeline(note);
    showView("detail");
  }

  function renderTimeline(note) {
    els.timelineList.innerHTML = "";
    const count = M.intervalDays.length;

    for (let i = 0; i < count; i++) {
      const li = document.createElement("li");
      li.className = "timeline-item";

      const rail = document.createElement("div");
      rail.className = "timeline-rail";

      const cb = document.createElement("input");
      cb.type = "checkbox";
      const done = note.finished || i < note.stage;
      const canCheck = !note.finished && i === note.stage;
      cb.checked = done;
      cb.disabled = !canCheck;
      if (canCheck) {
        cb.addEventListener("change", () => {
          if (cb.checked) completeStage(i);
        });
      }
      rail.appendChild(cb);

      const meta = document.createElement("div");
      const label = document.createElement("div");
      label.className = "stage-label";
      label.textContent = M.stageLabel(i);
      const date = document.createElement("div");
      date.className = "stage-date";
      date.textContent = `计划：${M.formatDateTime(M.reviewTimeFor(note.createdAt, i))}`;
      meta.append(label, date);

      li.append(rail, meta);
      els.timelineList.appendChild(li);
    }
  }

  function completeStage(index) {
    const note = notes.find((n) => n.id === currentNoteId);
    if (!note) return;
    if (note.finished || index !== note.stage) {
      renderTimeline(note);
      return;
    }
    const nextStage = note.stage + 1;
    const nextTime = M.reviewTimeFor(note.createdAt, nextStage);
    note.stage = nextStage;
    note.nextReviewTime = nextTime;
    note.finished = nextTime < 0;
    saveNotes();
    renderTimeline(note);
    renderList();
  }

  function bindChrome() {
    document.getElementById("main-header").textContent = M.strings.mainHeader;
    document.getElementById("add-header").textContent = M.strings.addHeader;
    document.getElementById("detail-header").textContent = M.strings.detailHeader;
    els.emptyState.textContent = M.strings.empty;
    els.titleInput.placeholder = M.strings.titleHint;
    els.contentInput.placeholder = M.strings.contentHint;
    els.btnSave.textContent = M.strings.save;
    document.getElementById("timeline-title").textContent = M.strings.timelineTitle;
    document.getElementById("timeline-helper").textContent = M.strings.timelineHelper;

    if (els.syncMeta) {
      els.syncMeta.textContent = `synced · ${M.meta.syncedFromBranch} · ${M.meta.updatedAt}`;
    }
  }

  // Header back: tap header on add/detail returns to list (web convenience)
  document.getElementById("add-header").style.cursor = "pointer";
  document.getElementById("detail-header").style.cursor = "pointer";
  document.getElementById("add-header").title = "返回列表";
  document.getElementById("detail-header").title = "返回列表";
  document.getElementById("add-header").addEventListener("click", () => showView("list"));
  document.getElementById("detail-header").addEventListener("click", () => {
    renderList();
    showView("list");
  });

  els.fab.addEventListener("click", openAdd);
  els.btnSave.addEventListener("click", saveNote);

  bindChrome();
  renderList();
  showView("list");
})();
