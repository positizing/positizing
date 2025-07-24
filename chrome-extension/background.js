chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg.type !== 'REPHRASE_REQUEST') return;

    chrome.storage.sync.get(['enabled','apiUrl'], ({ enabled, apiUrl }) => {
        if (!enabled) {
            sendResponse({ suggestions: [], votes: {} });
            return;
        }

        fetch(apiUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                prompt:    msg.prompt,
                token:     msg.token || '',
                ua:        navigator.userAgent,
                force:     false
            })
        })
            .then(res => res.json())
            .then(json => sendResponse({
                suggestions: json.suggestions || [],
                votes:       json.votes       || {}
            }))
            .catch(err => {
                console.error('Positizing fetch error', err);
                sendResponse({ suggestions: [], votes: {} });
            });
    });

    return true;  // async
});
