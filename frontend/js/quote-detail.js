var currentQuote = null;
var currentQuoteId = null;

document.addEventListener('DOMContentLoaded', function () {
    var params = new URLSearchParams(window.location.search);
    currentQuoteId = params.get('id');
    if (!currentQuoteId) {
        document.getElementById('loading').textContent = 'No quote ID specified.';
        return;
    }
    loadQuote();

    document.getElementById('edit-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-modal').classList.remove('active');
    });
    document.getElementById('edit-save-btn').addEventListener('click', saveEdit);
    document.getElementById('link-cancel-btn').addEventListener('click', function () {
        document.getElementById('link-modal').classList.remove('active');
    });
    var linkSearchTimer = null;
    document.getElementById('link-book-search').addEventListener('input', function () {
        clearTimeout(linkSearchTimer);
        linkSearchTimer = setTimeout(searchBooksForLink, 300);
    });
});

function loadQuote() {
    API.get('/api/quotes/' + currentQuoteId)
        .then(function (q) {
            currentQuote = q;
            renderQuote(q);
        })
        .catch(function (err) {
            document.getElementById('loading').textContent = 'Failed to load quote: ' + err.message;
        });
}

function renderQuote(q) {
    var detail = document.getElementById('quote-detail');
    detail.innerHTML = '';

    if (q.source_type === 'photo' && q.photo_id) {
        var img = document.createElement('img');
        img.src = '/api/photos/' + q.photo_id;
        img.alt = 'Quote photo';
        img.style.cssText = 'max-width:100%;border-radius:8px;';
        detail.appendChild(img);
    }

    if (q.text_content) {
        var text = document.createElement('div');
        text.style.cssText = 'margin-top:12px;line-height:1.6;font-size:1rem;white-space:pre-wrap;';
        text.textContent = q.text_content;
        detail.appendChild(text);
    }

    if (q.telegram_message_date) {
        var date = document.createElement('div');
        date.style.cssText = 'margin-top:12px;font-size:0.82rem;color:var(--text-secondary);';
        date.textContent = formatDate(q.telegram_message_date);
        if (q.source_type) date.textContent += ' \u00B7 ' + q.source_type;
        detail.appendChild(date);
    }

    // Book info
    var bookCard = document.getElementById('quote-book');
    var bookInfo = document.getElementById('quote-book-info');
    if (q.book_id) {
        var html = '<strong>Book:</strong> <a href="book.html?id=' + q.book_id + '">' + escapeHtml(q.book_title) + '</a>';
        if (q.authors && q.authors.length > 0) {
            html += ' &mdash; ' + escapeHtml(q.authors.join(', '));
        }
        bookInfo.innerHTML = html;
        bookCard.style.display = '';
    } else {
        bookInfo.innerHTML = '<span style="color:var(--text-secondary);font-style:italic;">Personal Note (not linked to any book)</span>';
        bookCard.style.display = '';
    }

    // Actions (admin only)
    var actions = document.getElementById('quote-actions');
    actions.innerHTML = '';

    if (API.isAdmin()) {
        actions.style.display = 'flex';

        var editBtn = document.createElement('button');
        editBtn.textContent = 'Edit';
        editBtn.style.cssText = 'background:var(--accent);color:#fff;padding:8px 16px;border:none;border-radius:4px;cursor:pointer;';
        editBtn.addEventListener('click', openEdit);
        actions.appendChild(editBtn);

        if (q.book_id) {
            var unlinkBtn = document.createElement('button');
            unlinkBtn.textContent = 'Unlink from Book';
            unlinkBtn.style.cssText = 'background:var(--bg-surface-hover);color:var(--text-primary);padding:8px 16px;border:1px solid var(--border-color);border-radius:4px;cursor:pointer;';
            unlinkBtn.addEventListener('click', unlinkQuote);
            actions.appendChild(unlinkBtn);
        } else {
            var linkBtn = document.createElement('button');
            linkBtn.textContent = 'Link to Book';
            linkBtn.style.cssText = 'background:var(--bg-surface-hover);color:var(--text-primary);padding:8px 16px;border:1px solid var(--border-color);border-radius:4px;cursor:pointer;';
            linkBtn.addEventListener('click', openLinkModal);
            actions.appendChild(linkBtn);
        }

        var deleteBtn = document.createElement('button');
        deleteBtn.textContent = 'Delete';
        deleteBtn.style.cssText = 'background:#c62828;color:#fff;padding:8px 16px;border:none;border-radius:4px;cursor:pointer;';
        deleteBtn.addEventListener('click', deleteQuote);
        actions.appendChild(deleteBtn);
    } else {
        actions.style.display = 'none';
    }
}

function openEdit() {
    document.getElementById('edit-text').value = currentQuote.text_content || '';
    document.getElementById('edit-modal').classList.add('active');
    document.getElementById('edit-text').focus();
}

function saveEdit() {
    var text = document.getElementById('edit-text').value.trim();
    if (!text) { alert('Quote text cannot be empty.'); return; }
    API.put('/api/quotes/' + currentQuoteId + '/edit', { text_content: text })
        .then(function () {
            document.getElementById('edit-modal').classList.remove('active');
            loadQuote();
        })
        .catch(function (err) { alert('Failed to save: ' + err.message); });
}

function unlinkQuote() {
    if (!confirm('Unlink this quote from its book?')) return;
    API.put('/api/quotes/' + currentQuoteId + '/unlink')
        .then(function () { loadQuote(); })
        .catch(function (err) { alert('Failed to unlink: ' + err.message); });
}

function deleteQuote() {
    if (!confirm('Delete this quote permanently?')) return;
    API.delete('/api/quotes/' + currentQuoteId)
        .then(function () { window.location.href = 'quotes.html'; })
        .catch(function (err) { alert('Failed to delete: ' + err.message); });
}

function openLinkModal() {
    document.getElementById('link-book-search').value = '';
    document.getElementById('link-book-results').innerHTML = '<p style="color:var(--text-secondary);font-size:0.85rem;">Type to search books...</p>';
    document.getElementById('link-modal').classList.add('active');
    document.getElementById('link-book-search').focus();
}

function searchBooksForLink() {
    var q = document.getElementById('link-book-search').value.trim();
    var resultsDiv = document.getElementById('link-book-results');
    if (!q) {
        resultsDiv.innerHTML = '<p style="color:var(--text-secondary);font-size:0.85rem;">Type to search books...</p>';
        return;
    }
    API.get('/api/books?page=1&size=10&title=' + encodeURIComponent(q))
        .then(function (data) {
            var books = data.items || [];
            resultsDiv.innerHTML = '';
            if (books.length === 0) {
                resultsDiv.innerHTML = '<p style="color:var(--text-secondary);font-size:0.85rem;">No books found.</p>';
                return;
            }
            for (var i = 0; i < books.length; i++) {
                var book = books[i];
                var btn = document.createElement('div');
                btn.style.cssText = 'padding:10px 12px;cursor:pointer;border-bottom:1px solid var(--border-color);';
                var authors = [];
                if (book.authors) {
                    for (var j = 0; j < book.authors.length; j++) authors.push(book.authors[j].name);
                }
                btn.innerHTML = '<strong>' + escapeHtml(book.title) + '</strong>' +
                    (authors.length ? '<br><small style="color:var(--text-secondary);">' + escapeHtml(authors.join(', ')) + '</small>' : '');
                btn.dataset.bookId = book.id;
                btn.addEventListener('mouseenter', function () { this.style.background = 'var(--bg-surface-hover)'; });
                btn.addEventListener('mouseleave', function () { this.style.background = ''; });
                btn.addEventListener('click', function () {
                    doLinkQuote(parseInt(this.dataset.bookId));
                });
                resultsDiv.appendChild(btn);
            }
        })
        .catch(function () {
            resultsDiv.innerHTML = '<p style="color:var(--text-secondary);">Search failed.</p>';
        });
}

function doLinkQuote(bookId) {
    API.put('/api/quotes/' + currentQuoteId + '/move', { book_id: bookId })
        .then(function () {
            document.getElementById('link-modal').classList.remove('active');
            loadQuote();
        })
        .catch(function (err) { alert('Failed to link: ' + err.message); });
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    return String(d.getDate()).padStart(2, '0') + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + d.getFullYear();
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}
