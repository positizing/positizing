(function () {
    /* ------------------------------------------------------------------ *
     *  Element handles
     * ------------------------------------------------------------------ */
    var $input = document.getElementById('input');
    var $btn   = document.getElementById('detectBtn');
    var $card  = document.getElementById('resultCard');

    /* -- helpers -------------------------------------------------------- */
    function clear(el) { el.innerHTML = ''; }

    function badge(text, color) {
        var span = document.createElement('span');
        span.style.fontWeight = '600';
        span.style.color      = color;
        span.textContent      = text;
        return span;
    }

    function buildList(items) {
        var ul = document.createElement('ul');
        items.forEach(function (t) {
            var li = document.createElement('li');
            li.textContent = t;
            ul.appendChild(li);
        });
        return ul;
    }

    /* ------------------------------------------------------------------ *
     *  Main action
     * ------------------------------------------------------------------ */
    $btn.addEventListener('click', function () {
        clear($card);

        var text = $input.value.trim();
        if (!text) { alert('Enter some text first'); return; }

        // Ensure at least one terminal punctuation mark
        if (!/[.!?]\s*$/.test(text)) { text += '.'; }

        $card.textContent = '… contacting API …';

        // ---- 1) DETECT ----------------------------------------------
        fetch('/api/detect', {
            method : 'POST',
            headers: { 'Content-Type': 'application/json' },
            body   : JSON.stringify({ text: text })
        })
            .then(function (r) { return r.ok ? r.json() : Promise.reject(r.text()); })
            .then(function (detect) {

                if (!detect.needsReplacement) {
                    clear($card);
                    $card.appendChild(badge('No negative language detected ✔', '#2e7d32'));
                    return;
                }

                // ---- 2) REPHRASE -----------------------------------------
                return fetch('/api/rephrase', {
                    method : 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body   : JSON.stringify({ userInput: text })
                })
                    .then(function (r) { return r.ok ? r.json() : Promise.reject(r.text()); })
                    .then(function (suggestions) {
                        render(detect, suggestions);
                    });

            })
            .catch(function (err) {
                clear($card);
                $card.appendChild(badge('Error contacting API', '#c62828'));
                if (err.then) err.then(console.error);   // if response body
                else console.error(err);
            });
    });

    /* ------------------------------------------------------------------ *
     *  Render results
     * ------------------------------------------------------------------ */
    function render(detection, suggestions) {
        clear($card);

        /* 1. heading + highlighted sentence --------------------------- */
        var h4 = document.createElement('h4');
        h4.textContent = 'We spotted negative language';
        $card.appendChild(h4);

        var p = document.createElement('p');
        var span = detection.negativeSpans[0];
        p.innerHTML = 'Highlighted phrase: ' +
            '<mark>' + span.text + '</mark>';
        $card.appendChild(p);

        /* 2. suggestions in bullet list ------------------------------ */
        var h5 = document.createElement('h5');
        h5.textContent = 'Here are some suggestions:';
        $card.appendChild(h5);

        $card.appendChild(buildList(suggestions));

        /* 3. collapsible raw JSON (dev) ------------------------------ */
        var details = document.createElement('details');
        var summary = document.createElement('summary');
        summary.textContent = 'Raw JSON (debug)';
        details.appendChild(summary);

        var pre = document.createElement('pre');
        pre.style.background = '#fafafa';
        pre.style.padding    = '0.8rem';
        pre.style.overflow   = 'auto';
        pre.textContent      = JSON.stringify(
            { detection: detection, suggestions: suggestions }, null, 2);
        details.appendChild(pre);

        $card.appendChild(details);
    }
})();