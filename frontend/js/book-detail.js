var currentBookId = null;
var neighbors = null;
var moveQuoteId = null;
var editQuoteId = null;
var editAuthorId = null;
var cachedCountries = null;

document.addEventListener('DOMContentLoaded', function () {
    var params = new URLSearchParams(window.location.search);
    currentBookId = params.get('id');
    if (!currentBookId) {
        document.getElementById('loading').textContent = 'No book ID specified.';
        return;
    }
    loadBookDetail(currentBookId);

    document.getElementById('move-cancel-btn').addEventListener('click', function () {
        document.getElementById('move-modal').classList.remove('active');
    });
    document.getElementById('edit-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-modal').classList.remove('active');
    });
    document.getElementById('edit-save-btn').addEventListener('click', saveEditQuote);
    document.getElementById('edit-title-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-title-modal').classList.remove('active');
    });
    document.getElementById('edit-title-save-btn').addEventListener('click', saveEditTitle);
    document.getElementById('edit-author-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-author-modal').classList.remove('active');
    });
    document.getElementById('edit-author-save-btn').addEventListener('click', saveEditAuthor);
    document.getElementById('save-impression-btn').addEventListener('click', saveImpression);

    // Country auto-suggest for edit-author modal
    var countryInput = document.getElementById('edit-author-country');
    var countrySuggestionsDiv = document.getElementById('country-suggestions');
    countryInput.addEventListener('input', function () {
        var q = this.value.trim().toLowerCase();
        if (!q || !cachedCountries) {
            countrySuggestionsDiv.style.display = 'none';
            return;
        }
        var matches = cachedCountries.filter(function (c) {
            return c.toLowerCase().indexOf(q) !== -1;
        }).slice(0, 10);
        if (matches.length === 0) {
            countrySuggestionsDiv.style.display = 'none';
            return;
        }
        countrySuggestionsDiv.innerHTML = '';
        for (var i = 0; i < matches.length; i++) {
            var div = document.createElement('div');
            div.style.cssText = 'padding:8px 12px;cursor:pointer;border-bottom:1px solid var(--border-color);';
            div.textContent = matches[i];
            div.dataset.country = matches[i];
            div.addEventListener('mousedown', function (e) {
                e.preventDefault();
                countryInput.value = this.dataset.country;
                countrySuggestionsDiv.style.display = 'none';
            });
            div.addEventListener('mouseenter', function () { this.style.background = 'var(--bg-surface-hover)'; });
            div.addEventListener('mouseleave', function () { this.style.background = ''; });
            countrySuggestionsDiv.appendChild(div);
        }
        countrySuggestionsDiv.style.display = 'block';
    });
    countryInput.addEventListener('blur', function () {
        setTimeout(function () { countrySuggestionsDiv.style.display = 'none'; }, 150);
    });

    // Load countries for auto-suggest
    API.get('/api/countries').then(function (countries) { cachedCountries = countries; });
});

function loadBookDetail(bookId) {
    API.get('/api/books/' + bookId)
        .then(function (book) {
            renderBookDetail(book);
            document.getElementById('impression-text').value = book.impression || '';
            loadQuotes(bookId);
            loadNeighbors(bookId);
        })
        .catch(function (err) {
            document.getElementById('loading').textContent = 'Failed to load book: ' + err.message;
        });
}

function loadNeighbors(bookId) {
    API.get('/api/books/' + bookId + '/neighbors')
        .then(function (data) {
            neighbors = data;
        })
        .catch(function (err) {
            console.error('Failed to load neighbors:', err);
        });
}

function renderBookDetail(book) {
    var container = document.getElementById('book-detail');

    var authorParts = [];
    if (book.authors) {
        for (var i = 0; i < book.authors.length; i++) {
            var a = book.authors[i];
            var display = escapeHtml(a.name) + (a.country ? ' (' + escapeHtml(a.country) + ')' : '');
            if (API.isAdmin()) {
                authorParts.push('<span class="author-editable" style="cursor:pointer;text-decoration:underline dotted;text-underline-offset:3px;" onclick="openEditAuthorModal(' + a.id + ', \'' + escapeAttr(a.name) + '\', \'' + escapeAttr(a.country || '') + '\')">' + display + ' &#9998;</span>');
            } else {
                authorParts.push('<span>' + display + '</span>');
            }
        }
    }

    var html;
    if (API.isAdmin()) {
        html = '<h2 style="cursor:pointer;display:inline-block;" onclick="openEditTitleModal(\'' + escapeAttr(book.title) + '\')">' + escapeHtml(book.title) + ' &#9998;</h2>';
    } else {
        html = '<h2>' + escapeHtml(book.title) + '</h2>';
    }
    html += '<p><strong>Authors:</strong> ' + (authorParts.length ? authorParts.join(', ') : 'Unknown') + '</p>';
    html += '<p><strong>Announced:</strong> ' + formatDate(book.announcement_date) + '</p>';
    if (API.isAdmin()) {
        html += '<p><strong>Genre:</strong> <span id="genre-badge-detail" class="' + genreBadgeClass(book.genre) + ' clickable" title="Click to change genre" data-book-id="' + book.id + '">' + genreBadgeLabel(book.genre) + '</span></p>';
    } else {
        html += '<p><strong>Genre:</strong> <span class="' + genreBadgeClass(book.genre) + '">' + genreBadgeLabel(book.genre) + '</span></p>';
    }

    if (book.metadata_source) {
        html += '<p><span class="badge badge-text">' + escapeHtml(book.metadata_source) + '</span></p>';
    }

    if (book.review_note) {
        html += '<div style="margin-top: 16px; padding: 12px; background: #f8f9fa; border-radius: 4px;">';
        html += '<strong>Review note:</strong><br>' + escapeHtml(book.review_note);
        html += '</div>';
    }

    if (book.cover_photo_path) {
        html += '<div style="margin-top: 16px;"><img src="' + escapeHtml(book.cover_photo_path) + '" alt="Cover" style="max-width: 300px; border-radius: 4px;"></div>';
    }

    if (API.isAdmin()) {
        html += '<div style="margin-top: 20px;">';
        html += '<button onclick="deleteBook()" style="background:#c62828;color:#fff;padding:8px 16px;border:none;border-radius:4px;cursor:pointer;">Delete Book</button>';
        html += '</div>';
    }

    container.innerHTML = html;

    var genreBadge = document.getElementById('genre-badge-detail');
    if (genreBadge) {
        genreBadge.addEventListener('click', function () {
            var current = this.textContent === '—' ? null : this.textContent;
            var idx = GENRE_CYCLE.indexOf(current);
            var next = GENRE_CYCLE[(idx + 1) % GENRE_CYCLE.length];
            this.className = genreBadgeClass(next) + ' clickable';
            this.textContent = genreBadgeLabel(next);
            API.put('/api/books/' + this.dataset.bookId + '/genre', { genre: next });
        });
    }
}

function loadQuotes(bookId) {
    API.get('/api/books/' + bookId + '/quotes')
        .then(function (quotes) {
            renderQuotes(quotes);
        })
        .catch(function (err) {
            console.error('Failed to load quotes:', err);
        });
}

function renderQuotes(quotes) {
    var heading = document.getElementById('quotes-heading');
    var list = document.getElementById('quotes-list');
    var noQuotes = document.getElementById('no-quotes');

    if (!quotes || quotes.length === 0) {
        heading.style.display = 'block';
        noQuotes.style.display = 'block';
        list.innerHTML = '';
        return;
    }

    heading.style.display = 'block';
    noQuotes.style.display = 'none';
    list.innerHTML = '';

    for (var i = 0; i < quotes.length; i++) {
        var q = quotes[i];
        var card = document.createElement('div');
        card.className = 'card';

        var badgeClass = q.source_type === 'photo' ? 'badge-photo' : 'badge-text';
        var html = '<span class="badge ' + badgeClass + '">' + escapeHtml(q.source_type) + '</span>';
        html += ' <small>' + formatDate(q.telegram_message_date) + '</small>';

        if (q.source_type === 'photo' && q.photo_id) {
            html += '<div style="margin-top: 8px;"><img src="/api/photos/' + q.photo_id + '" alt="Quote photo" style="max-width:100%;border-radius:8px;"></div>';
        } else {
            html += '<p style="margin-top: 8px;">' + escapeHtml(q.text_content) + '</p>';
        }

        html += '<div style="margin-top: 8px; display: flex; gap: 8px;">';
        if (API.isAdmin()) {
            html += '<button onclick="openMoveModal(' + q.id + ')" style="background:#1a73e8;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;">Move</button>';
            html += '<button onclick="openEditModal(' + q.id + ', this)" style="background:#666;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;">Edit</button>';
            html += '<button onclick="unlinkQuote(' + q.id + ')" style="background:var(--bg-surface-hover);color:var(--text-primary);padding:4px 12px;border:1px solid var(--border-color);border-radius:4px;cursor:pointer;font-size:0.8rem;">Unlink</button>';
            html += '<button onclick="deleteQuote(' + q.id + ')" style="background:#c62828;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;">Delete</button>';
        }
        html += '</div>';

        card.innerHTML = html;
        card.dataset.textContent = q.text_content;
        card.dataset.quoteId = q.id;
        list.appendChild(card);
    }
}

function deleteBook() {
    if (!confirm('Delete this book? Its quotes will be moved to a neighboring book.')) {
        return;
    }
    API.delete('/api/books/' + currentBookId)
        .then(function (result) {
            if (result.quotes_moved_to) {
                window.location.href = 'book.html?id=' + result.quotes_moved_to;
            } else {
                window.location.href = 'index.html';
            }
        })
        .catch(function (err) {
            alert('Failed to delete: ' + err.message);
        });
}

function openMoveModal(quoteId) {
    moveQuoteId = quoteId;
    var optionsDiv = document.getElementById('move-options');
    optionsDiv.innerHTML = '';

    if (!neighbors || (!neighbors.prev && !neighbors.next)) {
        optionsDiv.innerHTML = '<p>No neighboring books found.</p>';
        document.getElementById('move-modal').classList.add('active');
        return;
    }

    if (neighbors.prev) {
        var prevBtn = document.createElement('button');
        prevBtn.style.cssText = 'background:#fff;border:1px solid #ddd;padding:12px;border-radius:4px;cursor:pointer;text-align:left;';
        prevBtn.innerHTML = '<small style="color:#666;">Previous book:</small><br><strong>' + escapeHtml(neighbors.prev.title) + '</strong>';
        prevBtn.addEventListener('click', function () { doMoveQuote(neighbors.prev.id); });
        optionsDiv.appendChild(prevBtn);
    }

    if (neighbors.next) {
        var nextBtn = document.createElement('button');
        nextBtn.style.cssText = 'background:#fff;border:1px solid #ddd;padding:12px;border-radius:4px;cursor:pointer;text-align:left;';
        nextBtn.innerHTML = '<small style="color:#666;">Next book:</small><br><strong>' + escapeHtml(neighbors.next.title) + '</strong>';
        nextBtn.addEventListener('click', function () { doMoveQuote(neighbors.next.id); });
        optionsDiv.appendChild(nextBtn);
    }

    document.getElementById('move-modal').classList.add('active');
}

function doMoveQuote(targetBookId) {
    API.put('/api/quotes/' + moveQuoteId + '/move', { book_id: targetBookId })
        .then(function () {
            document.getElementById('move-modal').classList.remove('active');
            loadQuotes(currentBookId);
        })
        .catch(function (err) {
            alert('Failed to move quote: ' + err.message);
        });
}

function openEditModal(quoteId, btn) {
    editQuoteId = quoteId;
    // Find the quote text from the card
    var card = btn.closest('.card');
    var text = card.dataset.textContent || '';
    document.getElementById('edit-text').value = text;
    document.getElementById('edit-modal').classList.add('active');
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
            loadQuotes(currentBookId);
        })
        .catch(function (err) {
            alert('Failed to save: ' + err.message);
        });
}

function saveImpression() {
    var text = document.getElementById('impression-text').value.trim();
    var btn = document.getElementById('save-impression-btn');
    btn.disabled = true;
    btn.textContent = 'Saving...';
    API.put('/api/books/' + currentBookId + '/impression', { impression: text || null })
        .then(function () {
            btn.textContent = 'Saved!';
            setTimeout(function () { btn.textContent = 'Save'; btn.disabled = false; }, 1500);
        })
        .catch(function (err) {
            alert('Failed to save impression: ' + err.message);
            btn.textContent = 'Save';
            btn.disabled = false;
        });
}

function deleteQuote(quoteId) {
    if (!confirm('Delete this quote permanently?')) return;
    API.delete('/api/quotes/' + quoteId)
        .then(function () {
            loadQuotes(currentBookId);
        })
        .catch(function (err) {
            alert('Failed to delete quote: ' + err.message);
        });
}

function unlinkQuote(quoteId) {
    if (!confirm('Unlink this quote from the book? It will become a personal note.')) return;
    API.put('/api/quotes/' + quoteId + '/unlink')
        .then(function () {
            loadQuotes(currentBookId);
        })
        .catch(function (err) {
            alert('Failed to unlink quote: ' + err.message);
        });
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    var year = d.getFullYear();
    var month = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return day + '-' + month + '-' + year;
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}

function escapeAttr(text) {
    if (!text) return '';
    return text.replace(/\\/g, '\\\\').replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

var GENRE_CYCLE = [null, 'Fiction', 'Non-fiction'];

function genreBadgeClass(genre) {
    if (genre === 'Fiction') return 'genre-badge genre-fiction';
    if (genre === 'Non-fiction') return 'genre-badge genre-nonfiction';
    return 'genre-badge genre-unknown';
}

function genreBadgeLabel(genre) {
    return genre || '—';
}


function openEditTitleModal(title) {
    document.getElementById('edit-title-input').value = title;
    document.getElementById('edit-title-modal').classList.add('active');
    document.getElementById('edit-title-input').focus();
}

function saveEditTitle() {
    var title = document.getElementById('edit-title-input').value.trim();
    if (!title) {
        alert('Title cannot be empty.');
        return;
    }
    API.put('/api/books/' + currentBookId + '/title', { title: title })
        .then(function () {
            document.getElementById('edit-title-modal').classList.remove('active');
            loadBookDetail(currentBookId);
        })
        .catch(function (err) {
            alert('Failed to save title: ' + err.message);
        });
}

function openEditAuthorModal(authorId, name, country) {
    editAuthorId = authorId;
    document.getElementById('edit-author-name').value = name;
    document.getElementById('edit-author-country').value = country;
    document.getElementById('edit-author-modal').classList.add('active');
}

function saveEditAuthor() {
    var name = document.getElementById('edit-author-name').value.trim();
    var country = document.getElementById('edit-author-country').value.trim();
    if (!name) {
        alert('Author name cannot be empty.');
        return;
    }
    API.put('/api/authors/' + editAuthorId, { name: name, country: country || null })
        .then(function () {
            document.getElementById('edit-author-modal').classList.remove('active');
            loadBookDetail(currentBookId);
        })
        .catch(function (err) {
            alert('Failed to save author: ' + err.message);
        });
}
