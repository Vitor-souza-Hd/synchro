document.addEventListener("DOMContentLoaded", () => {
    const authActions = document.getElementById("authActions");
    const sessionActions = document.querySelector(".session-actions");
    const logoutButton = sessionActions?.querySelector(".logout-button");
    const errorMessage = sessionActions?.querySelector(".session-error");

    if (window.location.protocol === "file:") return;

    async function updateSession() {
        try {
            const response = await fetch("/auth/cookie", {
                credentials: "same-origin",
                cache: "no-store",
                signal: AbortSignal.timeout(10000)
            });
            const loggedIn = response.ok && (await response.text()).trim() === "ok";
            if (authActions) authActions.hidden = loggedIn;
            if (sessionActions) sessionActions.hidden = !loggedIn;
        } catch {
            if (authActions) authActions.hidden = false;
            if (sessionActions) sessionActions.hidden = true;
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
        if (event.persisted) updateSession();
    });
});
