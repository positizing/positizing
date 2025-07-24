// options.js
document.addEventListener('DOMContentLoaded', () => {
    const enabledBox = document.getElementById('enabled');
    const apiUrlInput = document.getElementById('apiUrl');

    // Load saved settings
    chrome.storage.sync.get({ enabled: true, apiUrl: '' }, prefs => {
        enabledBox.checked = prefs.enabled;
        apiUrlInput.value = prefs.apiUrl;
    });

    // Save on change
    enabledBox.addEventListener('change', () => {
        chrome.storage.sync.set({ enabled: enabledBox.checked });
    });
    apiUrlInput.addEventListener('input', () => {
        chrome.storage.sync.set({ apiUrl: apiUrlInput.value });
    });
});
