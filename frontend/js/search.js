var currentPage = 1;
var currentSize = 20;
var totalItems = 0;
var currentQuery = '';

document.addEventListener('DOMContentLoaded', function () {
    document.getElementById('search-btn').addEventListener('click', doSearch);
    document.getElementById('search-input').addEventListener('keypress', function (e) {
        if (e.key === 'Enter') doSearch();
    });
    document.getElementById('prev-btn').addEventListener('click', function () {
        if (currentPage > 1) { currentPage--; fetchResults(); }
    });
    document.getElementById('next-btn').addEventListener('click', function () {
        var totalPages = Math.ceil(totalItems / currentSize);
        if (currentPage < totalPages) { currentPage++; fetchResults(); }
    });
});

function doSearch() {
    currentQuery = document.getElementById('search-input').value.trim();
    if (!currentQuery) return;
    currentPage = 1;
    fetchResults();
}

function fetchResults() {
    var params = '?q=' + encodeURIComponent(currentQuery) +
        '&page=' + currentPage + '&size=' + currentSize;

    API.get('/api/quotes/search' + params)
        .then(function (data) {
            renderResults(data);
        })
        .catch(function (err) {
            console.error('Search failed:', err);
        });
}

function renderResults(data) {
    var container = document.getElementById('results');
    var emptyState = document.getElementById('empty-state');
    var items = data.items;
    totalItems = data.total;

    container.innerHTML = '';

    if (!items || items.length === 0) {
        emptyState.style.display = 'block';
        container.style.display = 'none';
    } else {
        emptyState.style.display = 'none';
        container.style.display = '';

        for (var i = 0; i < items.length; i++) {
            var q = items[i];
            var card = document.createElement('div');
            card.className = 'card';

            var html = '<p>' + escapeHtml(q.text_content) + '</p>';
            html += '<div style="margin-top:8px; font-size:0.85rem; color:#666;">';
            if (q.book_title) {
                html += 'Book: <a href="book.html?id=' + q.book_id + '">' + escapeHtml(q.book_title) + '</a>';
            }
            if (q.authors && q.authors.length > 0) {
                html += ' by ' + escapeHtml(q.authors.join(', '));
            }
            html += ' &middot; ' + formatDate(q.telegram_message_date);
            html += '</div>';

            card.innerHTML = html;
            container.appendChild(card);
        }
    }

    updatePagination();
}

function updatePagination() {
    var totalPages = Math.ceil(totalItems / currentSize);
    if (totalPages < 1) totalPages = 1;

    var pageInfo = document.getElementById('page-info');
    pageInfo.textContent = 'Page ' + currentPage + ' of ' + totalPages + ' (' + totalItems + ' results)';
    document.getElementById('prev-btn').disabled = (currentPage <= 1);
    document.getElementById('next-btn').disabled = (currentPage >= totalPages);
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
