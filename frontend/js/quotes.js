var currentPage = 0;
var currentSize = 20;
var totalItems = 0;
var currentQuery = '';
var editQuoteId = null;

document.addEventListener('DOMContentLoaded', function () {
    loadQuotes();

    document.getElementById('search-btn').addEventListener('click', doSearch);
    document.getElementById('clear-btn').addEventListener('click', clearSearch);
    document.getElementById('search-input').addEventListener('keypress', function (e) {
        if (e.key === 'Enter') doSearch();
    });
    document.getElementById('prev-btn').addEventListener('click', function () {
        if (currentPage > 0) { currentPage--; loadQuotes(); }
    });
    document.getElementById('next-btn').addEventListener('click', function () {
        var totalPages = Math.ceil(totalItems / currentSize);
        if (currentPage + 1 < totalPages) { currentPage++; loadQuotes(); }
    });
    document.getElementById('edit-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-modal').classList.remove('active');
    });
    document.getElementById('edit-save-btn').addEventListener('click', saveEditQuote);
    document.getElementById('link-cancel-btn').addEventListener('click', function () {
        document.getElementById('link-modal').classList.remove('active');
    });
    var linkSearchInput = document.getElementById('link-book-search');
    var linkSearchTimer = null;
    linkSearchInput.addEventListener('input', function () {
        clearTimeout(linkSearchTimer);
        linkSearchTimer = setTimeout(function () { searchBooksForLink(); }, 300);
    });
});

function doSearch() {
    currentQuery = document.getElementById('search-input').value.trim();
    currentPage = 0;
    document.getElementById('clear-btn').style.display = currentQuery ? '' : 'none';
    loadQuotes();
}

function clearSearch() {
    currentQuery = '';
    document.getElementById('search-input').value = '';
    document.getElementById('clear-btn').style.display = 'none';
    currentPage = 0;
    loadQuotes();
}

function loadQuotes() {
    var params = '?page=' + currentPage + '&size=' + currentSize;
    if (currentQuery) params += '&q=' + encodeURIComponent(currentQuery);

    API.get('/api/quotes/browse' + params)
        .then(function (data) {
            renderQuotes(data);
        })
        .catch(function (err) {
            console.error('Failed to load quotes:', err);
        });
}

function renderQuotes(data) {
    var grid = document.getElementById('quotes-grid');
    var emptyState = document.getElementById('empty-state');
    var items = data.items;
    totalItems = data.total;

    grid.innerHTML = '';

    if (!items || items.length === 0) {
        emptyState.style.display = 'block';
        grid.style.display = 'none';
    } else {
        emptyState.style.display = 'none';
        grid.style.display = '';

        for (var i = 0; i < items.length; i++) {
            var q = items[i];
            var card = document.createElement('div');
            card.className = 'card quote-card';
            card.dataset.quoteId = q.id;
            card.dataset.textContent = q.text_content || '';

            var contentLink = document.createElement('a');
            contentLink.href = 'quote.html?id=' + q.id;
            contentLink.className = 'quote-card-content';
            contentLink.style.cssText = 'display:block;text-decoration:none;color:inherit;';

            if (q.photo_id) {
                var img = document.createElement('img');
                img.src = '/api/photos/' + q.photo_id;
                img.alt = 'Quote photo';
                img.loading = 'lazy';
                contentLink.appendChild(img);
            } else if (q.text_content) {
                var p = document.createElement('div');
                p.className = 'quote-card-text';
                p.textContent = q.text_content;
                contentLink.appendChild(p);
            }

            card.appendChild(contentLink);

            var meta = document.createElement('div');
            meta.className = 'quote-card-meta';
            var html = '';
            if (q.book_title) {
                html += '<a href="book.html?id=' + q.book_id + '">' + escapeHtml(q.book_title) + '</a>';
                if (q.authors && q.authors.length > 0) {
                    html += ' &mdash; ' + escapeHtml(q.authors.join(', '));
                }
            } else {
                html += '<span style="font-style:italic;color:var(--text-secondary);">Personal Note</span>';
            }
            if (q.telegram_message_date) {
                var d = new Date(q.telegram_message_date);
                html += '<span style="margin-left:8px;color:var(--text-secondary);font-size:0.8rem;">' + d.toLocaleDateString() + '</span>';
            }
            meta.innerHTML = html;
            card.appendChild(meta);

            if (API.isAdmin()) {
                var actions = document.createElement('div');
                actions.className = 'quote-card-actions';
                actions.innerHTML =
                    '<button onclick="openEditQuote(' + q.id + ', this)" class="btn-sm btn-secondary">Edit</button>' +
                    (q.book_id ? '<button onclick="unlinkQuote(' + q.id + ')" class="btn-sm btn-secondary">Unlink</button>' : '<button onclick="openLinkModal(' + q.id + ')" class="btn-sm btn-secondary">Link to Book</button>') +
                    '<button onclick="deleteQuote(' + q.id + ')" class="btn-sm btn-danger">Delete</button>';
                card.appendChild(actions);
            }

            grid.appendChild(card);
        }
    }

    updatePagination();
}

function openEditQuote(quoteId, btn) {
    editQuoteId = quoteId;
    var card = btn.closest('.card');
    var text = card.dataset.textContent || '';
    document.getElementById('edit-text').value = text;
    document.getElementById('edit-modal').classList.add('active');
    document.getElementById('edit-text').focus();
}

function saveEditQuote() {
    var text = document.getElementById('edit-text').value.trim();
    if (!text) {
        alert('Quote text cannot be empty.');
        return;
    }
    API.put('/api/quotes/' + editQuoteId + '/edit', { text_content: text })
        .then(function () {
            document.getElementById('edit-modal').classList.remove('active');
            loadQuotes();
        })
        .catch(function (err) {
            alert('Failed to save: ' + err.message);
        });
}

function deleteQuote(quoteId) {
    if (!confirm('Delete this quote permanently?')) return;
    API.delete('/api/quotes/' + quoteId)
        .then(function () { loadQuotes(); })
        .catch(function (err) { alert('Failed to delete: ' + err.message); });
}

function unlinkQuote(quoteId) {
    if (!confirm('Unlink this quote from its book? It will become a personal note.')) return;
    API.put('/api/quotes/' + quoteId + '/unlink')
        .then(function () { loadQuotes(); })
        .catch(function (err) { alert('Failed to unlink: ' + err.message); });
}

function updatePagination() {
    var totalPages = Math.ceil(totalItems / currentSize);
    if (totalPages < 1) totalPages = 1;

    var pageInfo = document.getElementById('page-info');
    pageInfo.textContent = 'Page ' + (currentPage + 1) + ' of ' + totalPages + ' (' + totalItems + ' quotes)';
    document.getElementById('prev-btn').disabled = (currentPage <= 0);
    document.getElementById('next-btn').disabled = (currentPage + 1 >= totalPages);
}

var linkQuoteId = null;

function openLinkModal(quoteId) {
    linkQuoteId = quoteId;
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
    API.put('/api/quotes/' + linkQuoteId + '/move', { book_id: bookId })
        .then(function () {
            document.getElementById('link-modal').classList.remove('active');
            loadQuotes();
        })
        .catch(function (err) { alert('Failed to link: ' + err.message); });
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}
