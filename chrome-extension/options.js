document.addEventListener('DOMContentLoaded', () => {
    const enabledBox = document.getElementById('enabled');
    const apiUrlInput = document.getElementById('apiUrl');
    const tokenInput  = document.getElementById('token');

    chrome.storage.sync.get(
        { enabled: true, apiUrl: '', token: '' },
        prefs => {
            enabledBox.checked  = prefs.enabled;
            apiUrlInput.value   = prefs.apiUrl;
            tokenInput.value    = prefs.token;
        }
    );

    enabledBox.onchange = () => chrome.storage.sync.set({ enabled: enabledBox.checked });
    apiUrlInput.oninput  = () => chrome.storage.sync.set({ apiUrl: apiUrlInput.value });
    tokenInput.oninput   = () => chrome.storage.sync.set({ token: tokenInput.value });
});
