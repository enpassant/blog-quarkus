"use strict";

(() => {
  const dialog = document.getElementById("login-dialog");
  const loginForm = document.getElementById("login-form");
  const loginButton = document.getElementById("login");
  const message = document.getElementById("message");
  const tokenKey = "rapids-token";
  const userKey = "rapids-user";
  const userIdKey = "rapids-user-id";
  const commandErrors = new Map();
  let updateSocket;
  let loginResult = null;
  let pendingLogin = null;

  function updateLoginButton() {
    const user = sessionStorage.getItem(userKey);
    loginButton.textContent = user ? `Belépve: ${user}` : "Belépés";
  }

  async function connectUpdates() {
    const userId = sessionStorage.getItem(userIdKey);
    const token = sessionStorage.getItem(tokenKey);
    if (!userId || !token || updateSocket?.readyState === WebSocket.OPEN) return;
    updateSocket?.close();
    updateSocket = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/updates/${encodeURIComponent(userId)}?token=${encodeURIComponent(token)}`);
    updateSocket.addEventListener("message", event => {
      try {
        const update = JSON.parse(event.data);
        if (update.value === "CommandFailed") commandErrors.set(update.requestId, update.message);
      } catch { /* Ignore unrecognized notifications. */ }
    });
    await new Promise(resolve => {
      const timer = setTimeout(resolve, 2000);
      const finish = () => { clearTimeout(timer); resolve(); };
      updateSocket.addEventListener("open", finish, { once: true });
      updateSocket.addEventListener("error", finish, { once: true });
    });
  }

  async function refreshNavigation() {
    try {
      const response = await fetch("/services", { headers: { Accept: "application/json" } });
      if (!response.ok) return;
      const registrations = await response.json();
      const nav = document.getElementById("navigation");
      const catalogue = document.getElementById("services");
      catalogue?.replaceChildren();
      const urls = new Set();
      nav.replaceChildren();
      registrations.filter(link => link.navigation).sort((a, b) => a.order - b.order).forEach(link => {
        if (urls.has(link.url) || !link.url.startsWith("/") || link.url.startsWith("//")) return;
        urls.add(link.url);
        const anchor = document.createElement("a"); anchor.href = link.url; anchor.textContent = link.title;
        nav.append(anchor);
        if (catalogue) {
          const paragraph = document.createElement("p"); paragraph.append(anchor.cloneNode(true)); catalogue.append(paragraph);
        }
      });
      if (catalogue && urls.size === 0) catalogue.textContent = "A szolgáltatások csatlakozására várunk…";
    } catch { /* Standalone query pages also have server-rendered navigation. */ }
  }

  function hasComment(comments, id, content) {
    return (comments || []).some(comment => (comment.commentId === id && comment.content === content) || hasComment(comment.comments, id, content));
  }

  async function waitForProjection(form, command) {
    message.textContent = "A mentés feldolgozása folyamatban…";
    const deadline = Date.now() + 30000;
    while (Date.now() < deadline) {
      const failure = commandErrors.get(command._requestId);
      if (failure) throw new Error(failure);
      const response = await fetch(form.dataset.next, { headers: { Accept: "application/json" }, cache: "no-store" });
      if (response.ok) {
        const data = await response.json();
        if (command._t === "ModifyBlog" && data.title === command.title && data.content === command.content) return;
        if (command._t === "CreateBlog" && data.title === command.title && data.content === command.content && data.discussions?.some(d => d.id === `disc-${form.dataset.id}`)) {
          const discussion = await fetch(`/discussion/disc-${encodeURIComponent(form.dataset.id)}`, { headers: { Accept: "application/json" }, cache: "no-store" });
          if (discussion.ok) return;
        }
        if ((command._t === "AddComment" || command._t === "ReplyComment") && hasComment(data.comments, command.id, command.content)) return;
      } else if (response.status !== 404) { await readResponse(response); }
      await new Promise(resolve => setTimeout(resolve, 300));
    }
    throw new Error("A parancsot a rendszer fogadta, de a feldolgozás még nem fejeződött be. Ugyanazzal a tartalommal újrapróbálhatod a mentést; a beírt szöveg megmaradt.");
  }

  function showLogin() {
    if (pendingLogin) return pendingLogin;
    document.getElementById("login-error").textContent = "";
    pendingLogin = new Promise(resolve => { loginResult = resolve; });
    dialog.showModal();
    return pendingLogin;
  }

  dialog.addEventListener("close", () => {
    if (loginResult) loginResult(dialog.returnValue === "logged-in");
    loginResult = null;
    pendingLogin = null;
    loginForm.elements.password.value = "";
  });
  dialog.addEventListener("cancel", () => { dialog.returnValue = "cancelled"; });
  document.getElementById("login-cancel").addEventListener("click", () => dialog.close("cancelled"));
  loginButton.addEventListener("click", showLogin);

  async function readResponse(response) {
    const text = await response.text();
    let data;
    try { data = JSON.parse(text); } catch { data = {}; }
    if (!response.ok) {
      const error = new Error(data.error || `A kérés sikertelen (HTTP ${response.status}).`);
      error.status = response.status;
      throw error;
    }
    const token = response.headers.get("X-Token");
    if (token) sessionStorage.setItem(tokenKey, token);
    return data;
  }

  loginForm.addEventListener("submit", async event => {
    event.preventDefault();
    const submit = loginForm.querySelector('button[type="submit"]');
    const errorLabel = document.getElementById("login-error");
    submit.disabled = true;
    errorLabel.textContent = "";
    try {
      const bytes = new TextEncoder().encode(`${loginForm.elements.username.value}:${loginForm.elements.password.value}`);
      const credentials = btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(""));
      const data = await readResponse(await fetch("/login", {
        method: "POST", credentials: "omit",
        headers: { Authorization: `Basic ${credentials}`, Accept: "application/json" }
      }));
      sessionStorage.setItem(userKey, data.userName);
      sessionStorage.setItem(userIdKey, data.userId);
      updateLoginButton();
      dialog.close("logged-in");
    } catch (error) {
      errorLabel.textContent = error.status === 401 ? "Sikertelen belépés. Helyi módban a felhasználónév és a jelszó azonos (például john / john)." : error.message;
    } finally { submit.disabled = false; }
  });

  document.querySelectorAll("form[data-topic]").forEach(form => {
    form.addEventListener("submit", async event => {
      event.preventDefault();
      const submit = form.querySelector("button");
      submit.disabled = true;
      message.textContent = "";
      try {
        if (!sessionStorage.getItem(tokenKey) && !await showLogin()) return;
        const command = Object.fromEntries(new FormData(form));
        command._t = form.dataset.type;
        const signature = JSON.stringify(command);
        if (form.pendingCommand?.signature !== signature) {
          form.pendingCommand = { signature, datetime: new Date().toISOString(), requestId: crypto.randomUUID?.() || Array.from(crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, "0")).join("") };
        }
        command.datetime = form.pendingCommand.datetime;
        command._requestId = form.pendingCommand.requestId;
        await connectUpdates();
        const url = `/commands/${encodeURIComponent(form.dataset.topic)}/${encodeURIComponent(form.dataset.id)}`;
        await readResponse(await fetch(url, {
          method: "POST", credentials: "omit",
          headers: { "Content-Type": "application/json", Accept: "application/json", Authorization: `Bearer ${sessionStorage.getItem(tokenKey)}` },
          body: JSON.stringify(command)
        }));
        await waitForProjection(form, command);
        window.location.assign(form.dataset.next);
      } catch (error) {
        if (error.status === 401) {
          sessionStorage.removeItem(tokenKey);
          sessionStorage.removeItem(userKey);
          sessionStorage.removeItem(userIdKey);
          updateSocket?.close();
          updateLoginButton();
          message.textContent = "A belépés lejárt. Jelentkezz be, majd nyomd meg újra a Mentés gombot. A beírt szöveg megmaradt.";
          await showLogin();
        } else { message.textContent = error.message; }
      } finally { submit.disabled = false; }
    });
  });

  const stats = document.getElementById("stats");
  if (stats) {
    const socket = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/monitor/ws`);
    let timer;
    socket.addEventListener("open", () => { timer = setInterval(() => socket.send("stats"), 1000); });
    socket.addEventListener("message", event => {
      try { stats.textContent = JSON.stringify(JSON.parse(event.data), null, 2); }
      catch { stats.textContent = event.data; }
    });
    socket.addEventListener("close", () => { clearInterval(timer); });
    socket.addEventListener("error", () => { message.textContent = "A monitor kapcsolata megszakadt. Frissítsd az oldalt."; });
  }
  updateLoginButton();
  refreshNavigation();
  setInterval(refreshNavigation, 10000);
})();
