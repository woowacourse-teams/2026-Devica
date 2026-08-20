(() => {
    "use strict";

    const backButton = document.querySelector("#guide-back-button");
    if (!backButton) {
        return;
    }

    backButton.addEventListener("click", () => window.history.back());
})();
