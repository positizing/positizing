chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
    if (msg.type !== 'REPHRASE_REQUEST') return;
    chrome.storage.sync.get(
        ['enabled','apiUrl','token'],
        ({ enabled, apiUrl, token }) => {
            if (!enabled || !apiUrl) {
    console.log("Skipping request", enabled, apiUrl)
                sendResponse({ suggestions: [], votes: {} });
                return;
            }

            fetch(apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept':       'application/json'
                },
                body: JSON.stringify({
                    prompt: msg.prompt,
                    token:  token,
                    ua:     navigator.userAgent,
                    force:  false
                })
            })
                .then(r => r.json())
                .then(json => {
                    console.log("Got positizing response", json);
                    sendResponse({
                        suggestions: json.suggestions || [],
                        votes: json.votes || {}
                    })
                })
                .catch(err => {
                    console.error('Positizing fetch error', err);
                    sendResponse({ suggestions: [], votes: {} });
                });
        }
    );

    return true; // keep the channel open
});
