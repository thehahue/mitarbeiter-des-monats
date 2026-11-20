"use strict";

const $ = (id) => document.getElementById(id);
const list = $("employee-list");
const saveButton = $("save-button");
const resetButton = $("reset-button");
const newEmployeeButton = $("new-employee-button");
const newEmployeeDialog = $("new-employee-dialog");
const newEmployeeForm = $("new-employee-form");
const newEmployeeName = $("new-employee-name");
const palette = [
  ["#eee2d5", "#8f6445"],
  ["#e7e9dc", "#687254"],
  ["#e8e2ed", "#81708d"],
  ["#f2dfd6", "#a36b53"],
  ["#e0e8e4", "#5d7a6e"],
  ["#eee5cd", "#95804b"],
  ["#e3e6ed", "#65768d"],
  ["#efdfe4", "#966b7a"],
  ["#e7e6d9", "#7b7b56"],
  ["#e8ded5", "#95735b"],
];
let employees = [];
let savedEmployees = [];
let loaded = false;
let busy = false;
let creating = false;
let deleting = false;
let drag = null;
let toastTimer;
let scrollFrame;

$("current-month").textContent = new Intl.DateTimeFormat("de-AT", {
  month: "long",
  year: "numeric",
}).format(new Date());

function icon(name) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.classList.add("icon");
  svg.setAttribute("aria-hidden", "true");
  const use = document.createElementNS("http://www.w3.org/2000/svg", "use");
  use.setAttribute("href", `#i-${name}`);
  svg.append(use);
  return svg;
}

function isDirty() {
  return (
    loaded &&
    (employees.length !== savedEmployees.length ||
      employees.some((entry, index) => entry.name !== savedEmployees[index]?.name))
  );
}

function announce(message) {
  $("announcement").textContent = message;
}

function toast(message, error = false) {
  clearTimeout(toastTimer);
  $("toast").textContent = message;
  $("toast").classList.toggle("error", error);
  $("toast").hidden = false;
  toastTimer = setTimeout(
    () => {
      $("toast").hidden = true;
    },
    error ? 6500 : 4000,
  );
}

function setConnection(text, offline = false) {
  const connection = $("connection");
  connection.replaceChildren(
    document.createElement("span"),
    document.createTextNode(text),
  );
  connection.classList.toggle("offline", offline);
}

function parseEmployees(data) {
  if (!Array.isArray(data?.mitarbeiter)) {
    throw new Error("Die Antwort enthält keine vollständige Rangliste.");
  }
  const names = new Set();
  const rankings = new Set();
  for (const entry of data.mitarbeiter) {
    if (
      !entry ||
      typeof entry.name !== "string" ||
      !entry.name.trim() ||
      !Number.isInteger(entry.ranking) ||
      entry.ranking < 0 ||
      entry.ranking >= data.mitarbeiter.length ||
      names.has(entry.name) ||
      rankings.has(entry.ranking)
    ) {
      throw new Error("Die empfangene Rangliste ist ungültig.");
    }
    names.add(entry.name);
    rankings.add(entry.ranking);
  }
  return data.mitarbeiter
    .map(({ name, ranking }) => ({ name, ranking }))
    .sort((a, b) => a.ranking - b.ranking);
}

async function readResponse(response) {
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(
      data?.detail ||
        `Die Anfrage ist fehlgeschlagen (HTTP ${response.status}).`,
    );
  }
  return parseEmployees(data);
}

function makeMoveButton(direction, name) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "move-button";
  button.dataset.direction = direction;
  const label = `${name} nach ${direction === "up" ? "oben" : "unten"} verschieben`;
  button.setAttribute("aria-label", label);
  button.title = label;
  button.append(icon(direction));
  return button;
}

function renderList() {
  const fragment = document.createDocumentFragment();
  employees.forEach((employee) => {
    const row = document.createElement("li");
    row.className = "employee-row";
    row.dataset.name = employee.name;
    const rank = document.createElement("span");
    rank.className = "rank";
    rank.setAttribute("aria-hidden", "true");
    const avatar = document.createElement("span");
    avatar.className = "avatar";
    avatar.setAttribute("aria-hidden", "true");
    avatar.textContent = employee.name
      .split(/\s+/u)
      .map((part) => Array.from(part)[0])
      .slice(0, 2)
      .join("");
    // Die Farbe bleibt auch nach dem Verschieben an die Person gebunden.
    const colorIndex =
      Array.from(employee.name).reduce(
        (sum, char) => sum + char.codePointAt(0),
        0,
      ) % palette.length;
    avatar.style.setProperty("--avatar-bg", palette[colorIndex][0]);
    avatar.style.setProperty("--avatar-ink", palette[colorIndex][1]);
    const details = document.createElement("div");
    details.className = "employee-details";
    const name = document.createElement("span");
    name.className = "employee-name";
    name.textContent = employee.name;
    const badge = document.createElement("span");
    badge.className = "leader-tag";
    badge.append(icon("spark"), document.createTextNode("Spitzenplatz"));
    details.append(name, badge);
    const actions = document.createElement("div");
    actions.className = "move-actions";
    actions.append(
      makeMoveButton("up", employee.name),
      makeMoveButton("down", employee.name),
    );
    const handle = document.createElement("button");
    handle.type = "button";
    handle.className = "drag-handle";
    handle.setAttribute("aria-label", `${employee.name} verschieben`);
    handle.setAttribute("aria-describedby", "sort-help");
    handle.title = "Ziehen oder mit ↑ und ↓ verschieben";
    handle.append(icon("grip"));
    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.className = "delete-button";
    const deleteLabel = `${employee.name} löschen`;
    deleteButton.setAttribute("aria-label", deleteLabel);
    deleteButton.title = deleteLabel;
    deleteButton.append(icon("close"));
    row.append(rank, avatar, details, actions, handle, deleteButton);
    fragment.append(row);
  });
  list.replaceChildren(fragment);
  updateView();
}

function updateView() {
  const dirty = isDirty();
  Array.from(list.children).forEach((row, index) => {
    employees[index].ranking = index;
    row.querySelector(".rank").textContent = String(index + 1).padStart(2, "0");
    row.querySelector(".leader-tag").hidden = index !== 0;
    row.querySelector('[data-direction="up"]').disabled = busy || index === 0;
    row.querySelector('[data-direction="down"]').disabled =
      busy || index === employees.length - 1;
    row.querySelector(".drag-handle").disabled = busy;
    row.querySelector(".delete-button").disabled = busy;
    row.setAttribute("aria-posinset", String(index + 1));
    row.setAttribute("aria-setsize", String(employees.length));
  });
  saveButton.disabled = busy || !dirty;
  resetButton.disabled = busy || !dirty;
  newEmployeeButton.disabled = busy || !loaded;
  newEmployeeName.disabled = creating;
  $("new-employee-cancel").disabled = creating;
  $("new-employee-submit").disabled = creating;
  $("new-employee-submit").textContent = creating ? "Wird angelegt …" : "Anlegen";
  newEmployeeForm.setAttribute("aria-busy", String(creating));
  saveButton.querySelector("span").textContent =
    busy && !creating && !deleting ? "Wird gespeichert …" : "Speichern";
  $("ranking-panel").setAttribute("aria-busy", String(busy || !loaded));
  document.querySelector(".save-bar").classList.toggle("dirty", dirty);
  $("status-symbol").replaceChildren(icon(dirty ? "save" : "check"));
  if (loaded) {
    $("employee-count").textContent = String(employees.length);
    const empty = employees.length === 0;
    $("empty-state").hidden = !empty;
    $("sort-help").hidden = employees.length < 2;
    document.querySelector(".first-place").hidden = empty;
    $("spotlight-title").textContent = employees[0]?.name || "Dein Team wartet …";
    document.querySelector(".spotlight > p").hidden = empty;
    $("spotlight-state").textContent = empty
      ? "Gemeinsam beginnt es mit dem ersten Namen."
      : dirty
        ? "Vorschau · noch nicht gespeichert"
        : "Ein guter Grund für Applaus.";
    $("save-status").textContent = creating
      ? "Neuer Mitarbeiter wird angelegt"
      : deleting
        ? "Mitarbeiter wird gelöscht"
        : busy
          ? "Neue Reihenfolge wird gespeichert"
          : dirty
            ? "Ungespeicherte Änderungen"
            : "Alles auf dem aktuellen Stand";
    $("save-description").textContent = creating || deleting
      ? "Das Team wird aktualisiert."
      : busy
        ? "Die Rangliste wird übernommen."
        : dirty
          ? "Deine neue Reihenfolge ist bereit zum Speichern."
          : empty
            ? "Lege den ersten Mitarbeiter an."
            : "Die aktuelle Reihenfolge ist gespeichert.";
  }
}

async function loadEmployees() {
  $("loading-state").hidden = false;
  $("load-error").hidden = true;
  list.hidden = true;
  $("ranking-panel").setAttribute("aria-busy", "true");
  setConnection("Wird geladen");
  try {
    const response = await fetch("/api/mitarbeiter", {
      cache: "no-store",
      signal: AbortSignal.timeout(15000),
    });
    employees = await readResponse(response);
    savedEmployees = structuredClone(employees);
    loaded = true;
    renderList();
    list.hidden = false;
    setConnection("Rangliste geladen");
  } catch (error) {
    $("load-error").hidden = false;
    $("load-error-detail").textContent =
      error.name === "TimeoutError"
        ? "Die Verbindung dauert zu lange. Bitte versuche es erneut."
        : "Bitte prüfe die Verbindung und versuche es erneut.";
    $("save-status").textContent = "Noch keine Verbindung";
    $("save-description").textContent =
      "Die Rangliste konnte nicht geladen werden.";
    $("ranking-panel").setAttribute("aria-busy", "false");
    setConnection("Nicht verbunden", true);
  } finally {
    $("loading-state").hidden = true;
  }
}

function moveEmployee(from, to) {
  if (busy || from === to || to < 0 || to >= employees.length) return;
  const row = list.children[from];
  const remainingRows = Array.from(list.children).filter(
    (item) => item !== row,
  );
  const [employee] = employees.splice(from, 1);
  employees.splice(to, 0, employee);
  // Bestehende DOM-Knoten verwenden; nach dem Umhängen Pointer Capture erneuern.
  const focused = document.activeElement;
  list.insertBefore(row, remainingRows[to] || null);
  if (drag?.row === row) {
    drag.handle.setPointerCapture(drag.pointerId);
  }
  updateView();
  if (focused instanceof HTMLButtonElement && row.contains(focused)) {
    const focusTarget = focused.disabled
      ? row.querySelector(".drag-handle")
      : focused;
    focusTarget.focus({ preventScroll: true });
  }
}

function announcePosition(index) {
  announce(
    `${employees[index].name} ist jetzt auf Platz ${index + 1}. Noch nicht gespeichert.`,
  );
}

list.addEventListener("click", async (event) => {
  const button = event.target.closest(".delete-button");
  if (!button || busy || drag || !loaded) return;
  const index = Array.from(list.children).indexOf(button.closest(".employee-row"));
  const { name } = employees[index];
  if (
    isDirty() &&
    !window.confirm(
      `${name} löschen? Dabei wird die gespeicherte Rangliste neu geladen und deine noch ungespeicherte Reihenfolge verworfen.`,
    )
  ) {
    return;
  }

  deleting = true;
  busy = true;
  $("toast").hidden = true;
  updateView();
  let success = false;
  try {
    const response = await fetch("/api/delete-mitarbeiter", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
      signal: AbortSignal.timeout(15000),
    });
    employees = await readResponse(response);
    savedEmployees = structuredClone(employees);
    renderList();
    setConnection("Team aktualisiert");
    toast(`${name} wurde gelöscht.`);
    announce(
      `${name} wurde gelöscht. Das Team hat jetzt ${employees.length} Mitarbeiter.`,
    );
    success = true;
  } catch (error) {
    const message =
      error.name === "TimeoutError"
        ? "Die Antwort dauert zu lange. Bitte prüfe die Rangliste vor einem erneuten Versuch."
        : error instanceof TypeError
          ? "Keine Verbindung. Bitte prüfe die Rangliste vor einem erneuten Versuch."
          : error.message;
    setConnection("Löschen fehlgeschlagen", true);
    toast(message, true);
    announce(`Löschen fehlgeschlagen. ${message}`);
  } finally {
    deleting = false;
    busy = false;
    updateView();
  }
  const focusTarget = success
    ? list.children[Math.min(index, employees.length - 1)]?.querySelector(
        ".delete-button",
      ) || newEmployeeButton
    : button;
  focusTarget.focus({ preventScroll: true });
});

list.addEventListener("click", (event) => {
  const button = event.target.closest(".move-button");
  if (!button || busy || drag) return;
  const index = Array.from(list.children).indexOf(
    button.closest(".employee-row"),
  );
  const target = index + (button.dataset.direction === "up" ? -1 : 1);
  if (target < 0 || target >= employees.length) return;
  moveEmployee(index, target);
  announcePosition(target);
});

list.addEventListener("keydown", (event) => {
  if (!event.target.closest(".drag-handle") || busy || drag) return;
  const index = Array.from(list.children).indexOf(
    event.target.closest(".employee-row"),
  );
  let target;
  if (event.key === "ArrowUp") target = Math.max(0, index - 1);
  else if (event.key === "ArrowDown")
    target = Math.min(employees.length - 1, index + 1);
  else if (event.key === "Home") target = 0;
  else if (event.key === "End") target = employees.length - 1;
  else return;
  event.preventDefault();
  moveEmployee(index, target);
  list.children[target].scrollIntoView({ block: "nearest" });
  announcePosition(target);
});

function placeDraggedRow() {
  if (!drag?.active) return;
  const otherRows = Array.from(list.children).filter((row) => row !== drag.row);
  let target = otherRows.findIndex((row) => {
    const rect = row.getBoundingClientRect();
    return drag.y < rect.top + rect.height / 2;
  });
  if (target === -1) target = otherRows.length;
  moveEmployee(Array.from(list.children).indexOf(drag.row), target);
}

function autoScroll() {
  if (!drag?.active) return;
  const edge = 85;
  const speed =
    drag.y < edge
      ? -Math.ceil((edge - drag.y) / 6)
      : drag.y > window.innerHeight - edge
        ? Math.ceil((drag.y - window.innerHeight + edge) / 6)
        : 0;
  if (speed) {
    window.scrollBy(0, speed);
    placeDraggedRow();
  }
  scrollFrame = requestAnimationFrame(autoScroll);
}

list.addEventListener("pointerdown", (event) => {
  const handle = event.target.closest(".drag-handle");
  if (!handle || busy || drag || event.button !== 0 || !event.isPrimary) return;
  const row = handle.closest(".employee-row");
  drag = {
    row,
    handle,
    pointerId: event.pointerId,
    startY: event.clientY,
    y: event.clientY,
    active: false,
    originalOrder: [...employees],
  };
  handle.setPointerCapture(event.pointerId);
});

list.addEventListener("pointermove", (event) => {
  if (!drag || event.pointerId !== drag.pointerId) return;
  drag.y = event.clientY;
  if (!drag.active && Math.abs(drag.y - drag.startY) > 5) {
    drag.active = true;
    drag.row.classList.add("dragging");
    document.body.classList.add("is-dragging");
    autoScroll();
  }
  if (drag.active) {
    event.preventDefault();
    placeDraggedRow();
  }
});

function finishDrag(event, cancel = false) {
  if (
    !drag ||
    (event.pointerId !== undefined && event.pointerId !== drag.pointerId)
  )
    return;
  const current = drag;
  drag = null;
  cancelAnimationFrame(scrollFrame);
  current.row.classList.remove("dragging");
  document.body.classList.remove("is-dragging");
  if (current.handle.hasPointerCapture(current.pointerId))
    current.handle.releasePointerCapture(current.pointerId);
  if (cancel) {
    employees = current.originalOrder;
    renderList();
    announce("Verschieben abgebrochen.");
  } else if (current.active) {
    current.handle.focus({ preventScroll: true });
    announcePosition(Array.from(list.children).indexOf(current.row));
  }
}

list.addEventListener("pointerup", (event) => finishDrag(event));
list.addEventListener("pointercancel", (event) => finishDrag(event, true));
list.addEventListener("lostpointercapture", (event) => finishDrag(event, true));
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && drag) finishDrag(event, true);
});

saveButton.addEventListener("click", async () => {
  if (busy || !isDirty() || drag) return;
  busy = true;
  $("toast").hidden = true;
  updateView();
  const payload = {
    mitarbeiter: employees.map((entry, ranking) => ({
      name: entry.name,
      ranking,
    })),
  };
  try {
    const response = await fetch("/api/save-mitarbeiter", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(15000),
    });
    employees = await readResponse(response);
    savedEmployees = structuredClone(employees);
    renderList();
    setConnection("Rangliste gespeichert");
    toast("Gespeichert. Ein bisschen Applaus fürs ganze Team!");
  } catch (error) {
    setConnection("Speichern fehlgeschlagen", true);
    const message =
      error.name === "TimeoutError"
        ? "Die Antwort dauert zu lange. Bitte erneut speichern."
        : error instanceof TypeError
          ? "Keine Verbindung. Deine Änderungen bleiben hier erhalten."
          : error.message;
    toast(message, true);
    announce(`Speichern fehlgeschlagen. ${message}`);
  } finally {
    busy = false;
    updateView();
    if (!isDirty())
      $("save-status").textContent = "Rangliste erfolgreich gespeichert";
  }
});

resetButton.addEventListener("click", () => {
  if (busy || drag) return;
  employees = structuredClone(savedEmployees);
  renderList();
  announce(
    "Änderungen verworfen. Die gespeicherte Reihenfolge ist wiederhergestellt.",
  );
});

function showNewEmployeeError(message) {
  $("new-employee-error").textContent = message;
  $("new-employee-error").hidden = false;
}

newEmployeeButton.addEventListener("click", () => {
  if (busy || !loaded || drag) return;
  newEmployeeForm.reset();
  newEmployeeName.removeAttribute("aria-invalid");
  $("new-employee-error").hidden = true;
  $("new-employee-note").hidden = !isDirty();
  newEmployeeDialog.showModal();
});

$("new-employee-cancel").addEventListener("click", () => {
  if (!creating) newEmployeeDialog.close();
});

newEmployeeDialog.addEventListener("cancel", (event) => {
  if (creating) event.preventDefault();
});

newEmployeeDialog.addEventListener("close", () => newEmployeeButton.focus());
newEmployeeName.addEventListener("input", () => {
  newEmployeeName.removeAttribute("aria-invalid");
  $("new-employee-error").hidden = true;
});

newEmployeeForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (busy || !loaded || !newEmployeeDialog.open) return;
  const name = newEmployeeName.value.trim();
  if (!name) {
    showNewEmployeeError("Bitte gib einen Namen ein.");
    newEmployeeName.setAttribute("aria-invalid", "true");
    newEmployeeName.focus();
    return;
  }
  creating = true;
  busy = true;
  $("new-employee-error").hidden = true;
  $("toast").hidden = true;
  updateView();
  let success = false;
  try {
    const response = await fetch("/api/new-mitarbeiter", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
      signal: AbortSignal.timeout(15000),
    });
    employees = await readResponse(response);
    savedEmployees = structuredClone(employees);
    renderList();
    setConnection("Team aktualisiert");
    success = true;
  } catch (error) {
    const message =
      error.name === "TimeoutError"
        ? "Die Antwort dauert zu lange. Bitte prüfe die Rangliste vor einem erneuten Versuch."
        : error instanceof TypeError
          ? "Keine Verbindung. Bitte prüfe die Rangliste vor einem erneuten Versuch."
          : error.message;
    showNewEmployeeError(message);
    setConnection("Anlegen fehlgeschlagen", true);
  } finally {
    creating = false;
    busy = false;
    updateView();
  }
  if (success) {
    newEmployeeDialog.close();
    toast(`${name} wurde angelegt.`);
    announce(
      `${name} wurde angelegt. Das Team hat jetzt ${employees.length} Mitarbeiter.`,
    );
  } else {
    newEmployeeName.focus();
  }
});

$("retry-button").addEventListener("click", loadEmployees);
window.addEventListener("beforeunload", (event) => {
  if (isDirty()) {
    event.preventDefault();
    event.returnValue = "";
  }
});

loadEmployees();
