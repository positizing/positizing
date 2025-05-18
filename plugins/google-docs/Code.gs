/***************************************************************************
 *  Positizing Google Docs add‑on  –  ES5 edition
 *  -----------------------------------------------------------------------
 *  • Scans the user’s selection for negative language
 *  • Calls your Quarkus endpoints:
 *      ‑ POST /api/detect    { text:string }
 *      ‑ POST /api/rephrase  { userInput:string }
 *  • Highlights negative sentences and offers positive rewrites
 ***************************************************************************/

// ‑‑‑‑‑‑‑‑ adjust to your own Quarkus host ‑‑‑‑‑‑‑‑
var BASE_URL = 'https://positizing.com/api';

/* ======================================================================
 * 1.  Home‑page card: appears automatically in Docs sidebar
 * ====================================================================== */
function onHomePage(e) {
  return buildHomeCard();
}

function buildHomeCard(optMessage) {
  var btn = CardService.newTextButton()
      .setText('Scan current selection')
      .setOnClickAction(
          CardService.newAction().setFunctionName('scanSelection')
      );

  var section = CardService.newCardSection().addWidget(btn);

  if (optMessage) {
    section.addWidget(
      CardService.newTextParagraph().setText(optMessage)
    );
  }

  return CardService.newCardBuilder()
      .setHeader(CardService.newCardHeader().setTitle('Positizing'))
      .addSection(section)
      .build();
}

/* ======================================================================
 * 2.  Main entry – runs when user clicks the sidebar button
 * ====================================================================== */
function scanSelection(e) {
  var doc = DocumentApp.getActiveDocument();
  var sel = doc.getSelection();

  if (!sel) {
    return buildHomeCard('⚠️ Please select some text first.');
  }

  // Collect text in ES5 style
  var rangeElements = sel.getRangeElements();
  var text = '';
  for (var i = 0; i < rangeElements.length; i++) {
    text += rangeElements[i].getElement().asText().getText();
  }
  text = text.trim();

  if (text.length === 0) {
    return buildHomeCard('⚠️ Selection is empty.');
  }

  /* ---------- 1)  DETECT negative sentences ---------- */
  var detectRes = JSON.parse(
    UrlFetchApp.fetch(BASE_URL + '/detect', {
      method      : 'post',
      contentType : 'application/json',
      payload     : JSON.stringify({ text: text })
    }).getContentText()
  );

  if (!detectRes.needsReplacement) {
    return buildHomeCard('🎉 No negative language detected!');
  }

  /* ---------- 2)  HIGHLIGHT in document --------------- */
  highlightSpans(sel, detectRes.negativeSpans);

  /* ---------- 3)  GET REPHRASES for each span --------- */
  var suggestions = [];
  for (var j = 0; j < detectRes.negativeSpans.length; j++) {
    var span = detectRes.negativeSpans[j];

    var resp = UrlFetchApp.fetch(BASE_URL + '/rephrase', {
      method      : 'post',
      contentType : 'application/json',
      payload     : JSON.stringify({ userInput: span.text })
    }).getContentText();

    var arr = JSON.parse(resp);          // → array of strings
    for (var k = 0; k < arr.length; k++) {
      suggestions.push({
        original: span.text,
        positive: arr[k]
      });
    }
  }

  return buildSuggestionCard(suggestions);
}

/* ======================================================================
 * 3.  Utility functions
 * ====================================================================== */

/**
 * Give each span a pale‑yellow background.
 * Works only if selection contains text elements.
 */
function highlightSpans(selection, spans) {
  var YELLOW = '#FFF59D';
  var textElement = selection.getRangeElements()[0]
                             .getElement()
                             .asText();

  for (var i = 0; i < spans.length; i++) {
    var s = spans[i];
    textElement.setBackgroundColor(s.start, s.end - 1, YELLOW);
  }
}

/**
 * Build a card with buttons to accept each suggestion.
 */
function buildSuggestionCard(items) {
  var section = CardService.newCardSection();

  for (var i = 0; i < items.length; i++) {
    var it = items[i];

    section.addWidget(
      CardService.newTextButton()
        .setText('✔ Replace with suggestion #' + (i + 1))
        .setOnClickAction(
          CardService.newAction()
            .setFunctionName('applySuggestion')
            .setParameters({ orig: it.original, pos: it.positive })
        )
    ).addWidget(
      CardService.newTextParagraph().setText(
        '<b>Suggestion #' + (i + 1) + '</b><br>' + it.positive
      )
    );
  }

  return CardService.newCardBuilder()
      .setHeader(CardService.newCardHeader()
                 .setTitle('Positizing – Suggestions'))
      .addSection(section)
      .build();
}

/**
 * Replace the first occurrence of “orig” with “pos” in the document body.
 */
function applySuggestion(e) {
  var orig = e.parameters.orig;
  var pos  = e.parameters.pos;
  var body = DocumentApp.getActiveDocument().getBody();
  body.replaceText(orig, pos);
}