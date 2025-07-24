// popup.js

document.addEventListener('DOMContentLoaded', () => {
    const statusEl = document.getElementById('status');
    const sugEl    = document.getElementById('suggestions');

    // 1) Get text from the page
    chrome.tabs.query({ active: true, currentWindow: true }, tabs => {
        const tabId = tabs[0].id;
        chrome.tabs.sendMessage(tabId, { type: 'GET_TEXT' }, resp => {
            const text = resp?.text || '';
            if (!text.trim()) {
                statusEl.textContent = 'No text field focused.';
                return;
            }
            statusEl.textContent = 'Analyzing…';

            // 2) Send to background for rephrase
            chrome.runtime.sendMessage(
                { type: 'REPHRASE_REQUEST', prompt: text },
                data => {
                    statusEl.textContent = '';
                    const { suggestions = [], votes = {} } = data || {};
                    if (!suggestions.length) {
                        sugEl.textContent = 'No suggestions.';
                        return;
                    }
                    // 3) Render them
                    suggestions.forEach((s, i) => {
                        const div = document.createElement('div');
                        div.innerHTML =
                            `<span class="msg">#${i+1}</span>` +
                            `<span class="sug">${s}</span>` +
                            ` <small>(votes: ${votes[s]||0})</small>`;
                        sugEl.appendChild(div);
                    });
                }
            );
        });
    });
});
