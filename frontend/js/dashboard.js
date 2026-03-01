document.addEventListener('DOMContentLoaded', function () {
    loadDashboard();
    loadRandomQuote();

    document.getElementById('random-quote-refresh').addEventListener('click', loadRandomQuote);
});

function loadDashboard() {
    var cachedStats = sessionStorage.getItem('stats_cache');
    if (cachedStats) renderSummaryCards(JSON.parse(cachedStats));

    API.get('/api/stats')
        .then(function (data) {
            sessionStorage.setItem('stats_cache', JSON.stringify(data));
            renderSummaryCards(data);
        })
        .catch(function (err) {
            console.error('Failed to load stats:', err);
        });

    var recentBooksKey = 'books_cache:?page=1&size=5&sort=date_desc';
    var cachedBooks = sessionStorage.getItem(recentBooksKey);
    if (cachedBooks) renderRecentBooks(JSON.parse(cachedBooks).items || []);

    API.get('/api/books?page=1&size=5&sort=date_desc')
        .then(function (data) {
            sessionStorage.setItem(recentBooksKey, JSON.stringify(data));
            renderRecentBooks(data.items || []);
        })
        .catch(function (err) {
            console.error('Failed to load recent books:', err);
        });
}

function renderSummaryCards(data) {
    var container = document.getElementById('summary-cards');
    var cards = [
        { value: data.total_books, label: 'Books', href: 'index.html' },
        { value: data.total_quotes, label: 'Quotes', href: 'quotes.html' },
        { value: data.total_authors != null ? data.total_authors : '0', label: 'Authors', href: 'authors.html' },
        { value: data.avg_books_per_month != null ? data.avg_books_per_month : '0', label: 'Avg/Month', href: null }
    ];

    container.innerHTML = '';
    for (var i = 0; i < cards.length; i++) {
        var card = cards[i].href ? document.createElement('a') : document.createElement('div');
        card.className = 'stat-card';
        if (cards[i].href) card.href = cards[i].href;
        card.innerHTML = '<div class="value">' + cards[i].value + '</div><div class="label">' + cards[i].label + '</div>';
        container.appendChild(card);
    }
}

function renderRecentBooks(books) {
    var container = document.getElementById('recent-books');
    container.innerHTML = '';

    if (books.length === 0) {
        container.innerHTML = '<p class="empty-state">No books yet.</p>';
        return;
    }

    for (var i = 0; i < books.length; i++) {
        var book = books[i];
        var card = document.createElement('a');
        card.href = 'book.html?id=' + book.id;
        card.className = 'card recent-book-card';

        var title = document.createElement('div');
        title.className = 'recent-book-title';
        title.textContent = book.title;
        card.appendChild(title);

        var meta = document.createElement('div');
        meta.className = 'recent-book-meta';
        var parts = [];
        if (book.authors && book.authors.length > 0) {
            var names = [];
            for (var j = 0; j < book.authors.length; j++) names.push(book.authors[j].name);
            parts.push(names.join(', '));
        }
        if (book.announcement_date) parts.push(formatDate(book.announcement_date));
        if (book.genre) {
            var badge = document.createElement('span');
            badge.className = genreBadgeClass(book.genre);
            badge.textContent = book.genre;
            meta.innerHTML = escapeHtml(parts.join(' \u2014 ')) + ' ';
            meta.appendChild(badge);
        } else {
            meta.textContent = parts.join(' \u2014 ');
        }
        card.appendChild(meta);

        container.appendChild(card);
    }
}

function loadRandomQuote() {
    var container = document.getElementById('random-quote-card');
    var content = document.getElementById('random-quote-content');
    var source = document.getElementById('random-quote-source');
    var actions = document.getElementById('random-quote-actions');

    API.get('/api/quotes/random')
        .then(function (q) {
            content.innerHTML = '';
            if (q.photo_id) {
                var img = document.createElement('img');
                img.src = '/api/photos/' + q.photo_id;
                img.alt = 'Quote photo';
                content.appendChild(img);
            } else if (q.text_content) {
                var p = document.createElement('div');
                p.className = 'quote-text';
                p.textContent = q.text_content;
                content.appendChild(p);
            }

            var attribution = '';
            if (q.book_title) {
                attribution = '<a href="book.html?id=' + q.book_id + '">' + escapeHtml(q.book_title) + '</a>';
            }
            if (q.authors && q.authors.length > 0) {
                attribution += (attribution ? ' \u2014 ' : '') + escapeHtml(q.authors.join(', '));
            }
            source.innerHTML = attribution;

            // Link to quote detail
            actions.innerHTML = '<a href="quote.html?id=' + q.id + '" class="btn-sm btn-secondary" style="text-decoration:none;display:inline-block;text-align:center;">View Quote</a>';

            container.style.display = '';
        })
        .catch(function () {
            container.style.display = 'none';
        });
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    return String(d.getDate()).padStart(2, '0') + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + d.getFullYear();
}

function genreBadgeClass(genre) {
    if (genre === 'Fiction') return 'genre-badge genre-fiction';
    if (genre === 'Non-fiction') return 'genre-badge genre-nonfiction';
    return 'genre-badge genre-unknown';
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}
