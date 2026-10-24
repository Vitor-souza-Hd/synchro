document.addEventListener("DOMContentLoaded", () => {
    const authActions = document.getElementById("authActions");

    if (!authActions) {
        return;
    }

    if (window.location.protocol === "file:") {
        return;
    }

    fetch("/auth/cookie", { credentials: "same-origin" })
        .then(async (response) => {
            const result = (await response.text()).trim();
            authActions.hidden = response.ok && result === "ok";
        })
        .catch(() => {
            authActions.hidden = false;
        });
});
