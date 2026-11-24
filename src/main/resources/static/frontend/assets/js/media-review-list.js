window.createMediaReviewList = function ({list, more, emptyText, setOwn, addReviewCard}) {
    let mediaId;
    let before = null;
    let version = 0;

    async function page(id, cursor) {
        const query = new URLSearchParams({midiaId: id});
        if (cursor !== null) query.set("before", cursor);
        const response = await fetch("/review?" + query);
        if (!response.ok) throw new Error("HTTP " + response.status);
        return {items: await response.json(), next: response.headers.get("X-Next-Before")};
    }

    async function ownReview(id) {
        const response = await fetch("/review/me?" + new URLSearchParams({midiaId: id}), {cache: "no-store"});
        if (response.status === 401) return null;
        if (!response.ok) throw new Error("HTTP " + response.status);
        return (await response.json())[0] ?? null;
    }

    function render(result) {
        result.items.forEach(addReviewCard);
        before = result.next;
        more.hidden = before === null;
        more.disabled = false;
        more.textContent = "Carregar mais";
    }

    more.addEventListener("click", async () => {
        if (more.disabled || before === null) return;
        const current = version;
        more.disabled = true;
        try {
            const result = await page(mediaId, before);
            if (current === version) render(result);
        } catch {
            if (current === version) {
                more.textContent = "Tentar carregar mais";
                more.disabled = false;
            }
        }
    });

    return async function reload(id, signedIn) {
        const current = ++version;
        mediaId = id;
        before = null;
        more.hidden = true;
        const [first, own] = await Promise.all([page(id, null), signedIn ? ownReview(id) : null]);
        if (current !== version) return;
        setOwn(own);
        list.replaceChildren();
        if (!first.items.length) {
            const message = document.createElement("p");
            message.textContent = emptyText;
            list.append(message);
        }
        render(first);
    };
};
