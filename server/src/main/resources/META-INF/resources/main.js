// main.js — handles user input, API calls, and feedback

// generate or retrieve unique session token
let token = localStorage.getItem('positizing_token');
if (!token) {
    token = crypto.randomUUID();
    localStorage.setItem('positizing_token', token);
}

// capture user agent
const ua = navigator.userAgent;

// DOM elements
const inputEl    = document.getElementById('input');
const detectBtn  = document.getElementById('detectBtn');
const resultCard = document.getElementById('resultCard');

// hook button and Enter key
detectBtn.addEventListener('click', runDetect);
inputEl.addEventListener('keypress', e => { if (e.key === 'Enter') runDetect(); });

/**
 * Send the user text to the rephrase API and render results
 */
async function runDetect() {
    const prompt = inputEl.value.trim();
    if (!prompt) return;

    // show loading state
    resultCard.innerHTML = '<p>Loading&hellip;</p>';

    // call your backend
    const res = await fetch('/api/rephrase', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt, token, ua })
    });

    if (!res.ok) {
        resultCard.innerHTML = '<p>Error fetching suggestions.</p>';
        return;
    }

    const data = await res.json();
    // handle top‑level array or object response
    let suggestions;
    if (Array.isArray(data)) {
        suggestions = data;
    } else if (data.suggestions && Array.isArray(data.suggestions)) {
        suggestions = data.suggestions;
    } else if (typeof data.response === 'string') {
        suggestions = [data.response];
    } else {
        suggestions = [];
    }

    renderResults(prompt, suggestions);
}

/**
 * Render each suggestion with feedback buttons
 */
function renderResults(prompt, suggestions) {
    resultCard.innerHTML = '';

    suggestions.forEach(text => {
        const div = document.createElement('div');
        div.className = 'suggestion';

        const span = document.createElement('span');
        span.textContent = text || '';
        div.appendChild(span);

        ['up', 'down'].forEach(dir => {
            const btn = document.createElement('button');
            btn.className = 'feedback';
            btn.textContent = dir === 'up' ? '👍' : '👎';
            btn.addEventListener('click', () => sendFeedback(prompt, text, dir === 'up' ? 1 : -1));
            div.appendChild(btn);
        });

        resultCard.appendChild(div);
    });
}

/**
 * POST a vote to /cache/vote
 */
async function sendFeedback(prompt, suggestion, vote) {
    try {
        await fetch('/cache/vote', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ prompt, suggestion, vote, token, ua })
        });
    } catch (err) {
        console.error('Feedback error', err);
    }
}

// periodically flush in-memory cache to disk (every 5 minutes)
setInterval(() => {
    fetch('/cache/flush').catch(console.error);
}, 300_000);
