
/*
 * SYNCHRO - Seletor visual de notas
 * Funciona com os campos musicReviewRating
 * e albumReviewRating.
 *
 * Mantem o valor no input original, para nao
 * alterar a integracao com o backend.
 */

window.SynchroRating = {

    mount(input) {

        if (!input) return null;

        if (input.dataset.ratingMounted === "true") {
            return null;
        }

        input.dataset.ratingMounted = "true";

        const initialValue = Number(input.value) || 8;

        // Mantemos o ID original, mas removemos as setinhas.
        input.type = "hidden";

        const selector = document.createElement("div");
        selector.className = "synchro-rating";
        selector.setAttribute("role", "radiogroup");
        selector.setAttribute(
            "aria-label",
            "Escolha uma nota de 1 a 10"
        );

        const colors = [
            "#ff3c68",
            "#ff4568",
            "#ff8941",
            "#fa9741",
            "#f5db25",
            "#e7e525",
            "#90e238",
            "#78de51",
            "#28d9be",
            "#24dce5"
        ];

        const buttons = [];

        function setValue(value) {

            const number = Number(value);

            if (!Number.isInteger(number) ||
                number < 0 ||
                number > 10) {
                return;
            }

            // Este e o campo que o codigo antigo
            // continua lendo para publicar a review.
            input.value = number;

            buttons.forEach(button => {
                const selected =
                    Number(button.dataset.value) === number;

                button.classList.toggle(
                    "selected",
                    selected
                );

                button.setAttribute(
                    "aria-checked",
                    String(selected)
                );

                button.tabIndex = selected ? 0 : -1;
            });
        }

        for (let value = 1; value <= 10; value++) {

            const button = document.createElement("button");

            button.type = "button";
            button.className = "synchro-rating-option";
            button.dataset.value = value;
            button.style.setProperty(
                "--rating-color",
                colors[value - 1]
            );

            button.setAttribute("role", "radio");
            button.setAttribute(
                "aria-label",
                `Nota ${value} de 10`
            );

            const number = document.createElement("span");
            number.className = "synchro-rating-number";
            number.textContent = value;

            const bar = document.createElement("span");
            bar.className = "synchro-rating-bar";
            bar.setAttribute("aria-hidden", "true");

            button.append(number, bar);

            button.addEventListener("click", () => {
                setValue(value);
            });

            button.addEventListener("keydown", event => {
                let next = Number(input.value) || 1;

                if (event.key === "ArrowRight" ||
                    event.key === "ArrowUp") {
                    next = Math.min(10, next + 1);
                } else if (
                    event.key === "ArrowLeft" ||
                    event.key === "ArrowDown"
                ) {
                    next = Math.max(1, next - 1);
                } else if (event.key === "Home") {
                    next = 1;
                } else if (event.key === "End") {
                    next = 10;
                } else {
                    return;
                }

                event.preventDefault();
                setValue(next);
                buttons[next - 1].focus();
            });

            buttons.push(button);
            selector.appendChild(button);
        }

        // Coloca as barrinhas exatamente onde
        // o campo numerico estava no formulario.
        input.insertAdjacentElement(
            "afterend",
            selector
        );

        setValue(initialValue);

        return { setValue };
    }
};
