var currentPage = 1;
var currentSize = 20;
var totalItems = 0;
var approveItemId = null;

document.addEventListener('DOMContentLoaded', function () {
    loadReview();

    document.getElementById('type-filter').addEventListener('change', function () {
        currentPage = 1;
        loadReview();
    });
    document.getElementById('prev-btn').addEventListener('click', function () {
        if (currentPage > 1) { currentPage--; loadReview(); }
    });
    document.getElementById('next-btn').addEventListener('click', function () {
        var totalPages = Math.ceil(totalItems / currentSize);
        if (currentPage < totalPages) { currentPage++; loadReview(); }
    });
});

function loadReview() {
    var type = document.getElementById('type-filter').value;
    var params = '?page=' + currentPage + '&size=' + currentSize + '&type=' + type;

    API.get('/api/review' + params)
        .then(function (data) {
            renderReview(data);
        })
        .catch(function (err) {
            console.error('Failed to load review items:', err);
        });
}

function renderReview(data) {
    var container = document.getElementById('review-list');
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
            var item = items[i];
            var card = document.createElement('div');
            card.className = 'card review-item type-' + item.type;

            var html = '<div style="display:flex; justify-content:space-between; align-items:center;">';
            html += '<span class="badge badge-flagged">' + escapeHtml(item.type) + '</span>';
            html += '<small>' + formatDate(item.message_date) + '</small>';
            html += '</div>';

            if (item.raw_text) {
                html += '<p style="margin-top:8px;">' + escapeHtml(item.raw_text) + '</p>';
            }

            if (item.photo_path) {
                html += '<div style="margin-top:8px;"><img src="' + escapeHtml(item.photo_path) + '" alt="Photo" style="max-width:200px; border-radius:4px;"></div>';
            }

            if (item.suggested_book_title) {
                html += '<p style="margin-top:4px; font-size:0.85rem;">Suggested book: <a href="book.html?id=' + item.suggested_book_id + '">' + escapeHtml(item.suggested_book_title) + '</a></p>';
            }

            if (API.isAdmin()) {
                html += '<div style="margin-top:12px; display:flex; gap:8px;">';
                html += '<button onclick="openApprove(' + item.id + ')" style="background:#2e7d32; color:#fff; border:none; padding:6px 12px; border-radius:4px; cursor:pointer;">Approve</button>';
                html += '<button onclick="dismissItem(' + item.id + ')" style="background:#c62828; color:#fff; border:none; padding:6px 12px; border-radius:4px; cursor:pointer;">Dismiss</button>';
                html += '</div>';
            }

            card.innerHTML = html;
            container.appendChild(card);
        }
    }

    updatePagination();
}

function updatePagination() {
    var totalPages = Math.ceil(totalItems / currentSize);
    if (totalPages < 1) totalPages = 1;
    document.getElementById('page-info').textContent = 'Page ' + currentPage + ' of ' + totalPages + ' (' + totalItems + ' items)';
    document.getElementById('prev-btn').disabled = (currentPage <= 1);
    document.getElementById('next-btn').disabled = (currentPage >= totalPages);
}

function openApprove(id) {
    approveItemId = id;
    document.getElementById('corrected-text').value = '';
    document.getElementById('approve-book-id').value = '';
    document.getElementById('approve-modal').classList.add('active');
}

function closeModal() {
    document.getElementById('approve-modal').classList.remove('active');
    approveItemId = null;
}

function submitApprove() {
    if (!approveItemId) return;
    var body = {};
    var text = document.getElementById('corrected-text').value.trim();
    var bookId = document.getElementById('approve-book-id').value.trim();
    if (text) body.corrected_text = text;
    if (bookId) body.book_id = parseInt(bookId);

    API.put('/api/review/' + approveItemId + '/approve', body)
        .then(function () {
            closeModal();
            loadReview();
        })
        .catch(function (err) {
            alert('Failed to approve: ' + err.message);
        });
}

function dismissItem(id) {
    API.put('/api/review/' + id + '/dismiss')
        .then(function () {
            loadReview();
        })
        .catch(function (err) {
            alert('Failed to dismiss: ' + err.message);
        });
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
