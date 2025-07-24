// contentScript.js

// ————————————————————————————————————————————————
// 1) Debounce helper
// ————————————————————————————————————————————————
function debounce(fn, delay) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
}

// ————————————————————————————————————————————————
// 2) Globals: cache & highlight class
// ————————————————————————————————————————————————
const HIGHLIGHT_CLASS = 'positizing-highlight';
const sentenceCache   = new Map();  // Map<sentenceText, Promise<{suggestions,votes}>>

// ————————————————————————————————————————————————
// 3) Insert our CSS for the wavy underline
// ————————————————————————————————————————————————
const styleEl = document.createElement('style');
styleEl.textContent = `
  .${HIGHLIGHT_CLASS} {
    text-decoration: underline wavy #e74c3c;
    cursor: help;
  }
`;
document.head.append(styleEl);

// ————————————————————————————————————————————————
// 4) Utility: clear all prior highlights in an element
// ————————————————————————————————————————————————
function clearHighlights(el) {
    el.querySelectorAll(`.${HIGHLIGHT_CLASS}`).forEach(span => {
        span.replaceWith(document.createTextNode(span.textContent));
    });
}

// ————————————————————————————————————————————————
// 5) Utility: wrap a text node range in a <span> with tooltip
// ————————————————————————————————————————————————
function wrapTextNode(node, start, end, message, suggestion) {
    // const text = node.nodeValue;el.value ?? el.innerText
    const text = node.value ?? node.innerText
    const before = document.createTextNode(text.slice(0, start));
    const target = document.createElement('span');
    console.log("Wrapping", node)
    target.className = HIGHLIGHT_CLASS;
    target.title = message + (suggestion ? `\nSuggestion: ${suggestion}` : '');
    target.textContent = text.slice(start, end);
    const after = document.createTextNode(text.slice(end));

    const frag = document.createDocumentFragment();
    frag.append(before, target, after);
    node.replaceWith(frag);
}

// ————————————————————————————————————————————————
// 6) Apply suggestions to the full element text
// ————————————————————————————————————————————————
function applySuggestions(el, resp, fullText) {
    resp.suggestions.forEach((sug, i) => {
        const idx = fullText.indexOf(sug);
        console.log("Processing", fullText, idx, sug)
        // we assume the element has a single text node; for more complex nodes,
        // you'd need a tree-walk to find the right textNode and offset.
        wrapTextNode(
            el.firstChild ? el.firstChild : el,
            idx,
            idx + sug.length,
            `Suggestion #${i+1} (votes: ${resp.votes[sug] || 0})`,
            sug
        );
    });
}

// ————————————————————————————————————————————————
// 7) Core: analyze one element’s text
// ————————————————————————————————————————————————
function analyzeElement(el) {
    const text = el.value ?? el.innerText;
    clearHighlights(el);

    // Split on ., !, ?, or newline (including the terminator)
    const sentences = text.match(/[^.!?\n]+[.!?\n]?/g) || [];
    sentences.forEach(raw => {
        console.log("Processing sentence", raw, el)
        const sentence = raw.trim();
        if (!sentence) return;

        // If we’ve already fetched this sentence, reuse the Promise
        let p = sentenceCache.get(sentence);
        if (!p) {
            p = new Promise(resolve => {
                console.log("Sending message", raw, el)
                chrome.runtime.sendMessage(
                    { type: 'REPHRASE_REQUEST', prompt: sentence },
                    resp => {
                        console.log("Positizing response", resp)
                        resolve(resp)
                    }
                );
                console.log("Sent message", raw, el)
            });
            sentenceCache.set(sentence, p);
        }

        p.then(resp => applySuggestions(el, resp, text));
    });
}

// Debounced version so we don’t hammer the background script/API
const analyzeDebounced = debounce(el => {
    console.log("[Positizing] Debounced analyze:", el, el.value ?? el.innerText);
    analyzeElement(el);
}, 500);

// ————————————————————————————————————————————————
// 8) Attach listeners to all text inputs & contentedits
// ————————————————————————————————————————————————
const selector = 'textarea, input[type="text"], [contenteditable="true"]';
document.querySelectorAll(selector).forEach(el => {
    if (el.__positizingAttached) return;
    el.__positizingAttached = true;

    // on each keystroke, schedule a debounced analysis
    el.addEventListener('input', () => analyzeDebounced(el));
    // also do a final pass on blur
    el.addEventListener('blur', () => analyzeElement(el));
});

// 9) Listen for GET_TEXT from the popup
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    console.log("Message received", msg)
    if (msg.type === 'GET_TEXT') {
        const el = document.activeElement;
        const isTextField = el && (
            el.tagName === 'TEXTAREA' ||
            (el.tagName === 'INPUT' && el.type === 'text') ||
            el.isContentEditable
        );
        const text = isTextField ? (el.value ?? el.innerText) : '';
        console.log("[Positizing] GET_TEXT →", text);
        sendResponse({text});
        return true;  // keep channel open
    }
});