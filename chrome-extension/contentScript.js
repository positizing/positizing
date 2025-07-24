(async function() {
    const HIGHLIGHT_CLASS = 'positizing-highlight';

    // Insert styling
    const styleEl = document.createElement('style');
    styleEl.textContent = `
    .${HIGHLIGHT_CLASS} {
      text-decoration: underline wavy #e74c3c;
      cursor: help;
    }
  `;
    document.head.append(styleEl);

    // Utility to wrap text
    function wrapTextNode(node, start, end, message, suggestion) {
        const text = node.nodeValue;
        const before = document.createTextNode(text.slice(0, start));
        const target = document.createElement('span');
        target.className = HIGHLIGHT_CLASS;
        target.title = message + (suggestion ? `\nSuggestion: ${suggestion}` : '');
        target.textContent = text.slice(start, end);
        const after  = document.createTextNode(text.slice(end));
        const frag = document.createDocumentFragment();
        frag.append(before, target, after);
        node.replaceWith(frag);
    }

    // Analyze a host element
    function analyze(el) {
        const text = el.value ?? el.innerText;
        chrome.runtime.sendMessage({
            type:   'REPHRASE_REQUEST',
            prompt: text
        }, resp => {
            // clear old highlights
            el.querySelectorAll?.(`.${HIGHLIGHT_CLASS}`)?.forEach(span => {
                span.replaceWith(document.createTextNode(span.textContent));
            });

            resp.suggestions.forEach((sug, idx) => {
                const vote = resp.votes[sug] || 0;
                // for simplicity, highlight the first match of sug
                const idxStart = text.indexOf(sug);
                if (idxStart >= 0) {
                    wrapTextNode(
                        el.firstChild,
                        idxStart,
                        idxStart + sug.length,
                        `Rephrase #${idx+1} (votes: ${vote})`,
                        sug
                    );
                }
            });
        });
    }

    // Attach blur listeners
    const selector = 'textarea, input[type="text"], [contenteditable="true"]';
    document.querySelectorAll(selector).forEach(el => {
        if (el.__positizingAttached) return;
        el.__positizingAttached = true;
        el.addEventListener('blur', () => analyze(el));
    });
})();
