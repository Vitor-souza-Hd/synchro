document.addEventListener("DOMContentLoaded", () => {
    const authActions = document.getElementById("authActions");
    const sessionActions = document.querySelector(".session-actions");
    const logoutButton = sessionActions?.querySelector(".logout-button");
    const errorMessage = sessionActions?.querySelector(".session-error");
    const profileIcons = [...document.querySelectorAll(".personal-profile-icon")]
        .map(image => ({image, fallback: image.getAttribute("src")}));
    let sessionRefresh = 0;

    if (window.location.protocol === "file:") return;

    function safeAvatar(value) {
        try {
            const url = new URL(value);
            return url.protocol === "https:" && url.hostname && !url.username && !url.password ? url.href : null;
        } catch {
            return null;
        }
    }

    function renderProfileIcons(photos = []) {
        for (const {image, fallback} of profileIcons) {
            const sources = [...photos];
            function next() {
                const source = sources.shift();
                image.classList.toggle("has-user-photo", Boolean(source));
                image.onerror = source ? next : null;
                image.src = source || fallback;
            }
            next();
        }
    }

    async function loadProfileIcons(refresh) {
        try {
            const response = await fetch("/users/me", {
                credentials: "same-origin",
                cache: "no-store",
                signal: AbortSignal.timeout(10000)
            });
            const user = response.ok ? await response.json() : null;
            if (refresh !== sessionRefresh) return;
            const photos = [user?.data?.profileImageUrl, user?.lastFmConnected ? user.data?.lastFmImageUrl : null]
                .map(safeAvatar).filter(Boolean);
            renderProfileIcons([...new Set(photos)]);
        } catch {
            if (refresh === sessionRefresh) renderProfileIcons();
        }
    }

    async function updateSession() {
        const refresh = ++sessionRefresh;
        try {
            const response = await fetch("/auth/cookie", {
                credentials: "same-origin",
                cache: "no-store",
                signal: AbortSignal.timeout(10000)
            });
            const loggedIn = response.ok && (await response.text()).trim() === "ok";
            if (refresh !== sessionRefresh) return;
            if (authActions) authActions.hidden = loggedIn;
            if (sessionActions) sessionActions.hidden = !loggedIn;
            if (loggedIn && profileIcons.length) await loadProfileIcons(refresh);
            else renderProfileIcons();
        } catch {
            if (refresh !== sessionRefresh) return;
            if (authActions) authActions.hidden = false;
            if (sessionActions) sessionActions.hidden = true;
            renderProfileIcons();
        }
    }

    logoutButton?.addEventListener("click", async () => {
        if (logoutButton.disabled) return;
        logoutButton.disabled = true;
        logoutButton.textContent = "Saindo…";
        errorMessage.hidden = true;
        try {
            const response = await fetch("/auth/logout", {
                method: "POST",
                credentials: "same-origin",
                signal: AbortSignal.timeout(15000)
            });
            if (!response.ok) throw new Error("Logout failed");
            ++sessionRefresh;
            renderProfileIcons();
            window.location.replace("/index.html");
        } catch {
            errorMessage.textContent = "Não foi possível sair. Tente novamente.";
            errorMessage.hidden = false;
            logoutButton.disabled = false;
            logoutButton.textContent = "Sair";
        }
    });

    updateSession();
    // Recheck the session when the browser restores a page from its back/forward cache.
    window.addEventListener("pageshow", (event) => {
        if (event.persisted) {
            renderProfileIcons();
            updateSession();
        }
    });
});
