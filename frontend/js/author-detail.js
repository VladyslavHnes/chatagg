var currentAuthorId = null;

document.addEventListener('DOMContentLoaded', function () {
    var params = new URLSearchParams(window.location.search);
    currentAuthorId = params.get('id');
    if (!currentAuthorId) {
        document.getElementById('loading').textContent = 'No author ID specified.';
        return;
    }
    loadAuthor();

    document.getElementById('edit-author-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-author-modal').classList.remove('active');
    });
    document.getElementById('edit-author-save-btn').addEventListener('click', saveEditAuthor);
    document.getElementById('add-book-btn').addEventListener('click', addBook);

    document.getElementById('new-book-title').addEventListener('keydown', function (e) {
        if (e.key === 'Enter') addBook();
    });
});

function loadAuthor() {
    API.get('/api/authors/' + currentAuthorId)
        .then(function (author) {
            renderAuthorDetail(author);
            renderBooks(author.books);
        })
        .catch(function (err) {
            document.getElementById('loading').textContent = 'Failed to load author: ' + err.message;
        });
}

function renderAuthorDetail(author) {
    var container = document.getElementById('author-detail');
    var country = author.country ? ' (' + escapeHtml(author.country) + ')' : '';
    var html = '<h2>' + escapeHtml(author.name) + country + '</h2>';
    if (API.isAdmin()) {
        html += '<div style="margin-top: 12px;">';
        html += '<button onclick="openEditModal()" style="background:#1a73e8;color:#fff;padding:8px 16px;border:none;border-radius:4px;cursor:pointer;">Edit Author</button>';
        html += '</div>';
    }
    container.innerHTML = html;
}

function renderBooks(books) {
    var heading = document.getElementById('books-heading');
    var table = document.getElementById('books-table');
    var tbody = document.getElementById('books-table-body');
    var noBooks = document.getElementById('no-books');
    var controls = document.getElementById('add-book-controls');

    heading.style.display = 'block';
    controls.style.display = API.isAdmin() ? 'flex' : 'none';

    if (!books || books.length === 0) {
        table.style.display = 'none';
        noBooks.style.display = 'block';
        tbody.innerHTML = '';
        return;
    }

    noBooks.style.display = 'none';
    table.style.display = 'table';
    tbody.innerHTML = '';

    for (var i = 0; i < books.length; i++) {
        var b = books[i];
        var tr = document.createElement('tr');

        var tdTitle = document.createElement('td');
        var link = document.createElement('a');
        link.href = 'book.html?id=' + b.id;
        link.textContent = b.title;
        tdTitle.appendChild(link);
        tr.appendChild(tdTitle);

        var tdGenre = document.createElement('td');
        tdGenre.className = 'nowrap';
        var gBadge = document.createElement('span');
        gBadge.className = genreBadgeClass(b.genre);
        gBadge.textContent = genreBadgeLabel(b.genre);
        tdGenre.appendChild(gBadge);
        tr.appendChild(tdGenre);

        var tdDate = document.createElement('td');
        tdDate.textContent = formatDate(b.announcement_date);
        tr.appendChild(tdDate);

        tbody.appendChild(tr);
    }
}

function addBook() {
    var title = document.getElementById('new-book-title').value.trim();
    var genre = document.getElementById('new-book-genre').value.trim();
    if (!title) {
        alert('Book title is required.');
        return;
    }
    API.post('/api/authors/' + currentAuthorId + '/books', {
        title: title,
        genre: genre || null
    })
        .then(function () {
            document.getElementById('new-book-title').value = '';
            document.getElementById('new-book-genre').value = '';
            loadAuthor();
        })
        .catch(function (err) {
            alert('Failed to add book: ' + err.message);
        });
}

function openEditModal() {
    var nameEl = document.querySelector('#author-detail h2');
    var text = nameEl.textContent;
    var match = text.match(/^(.+?)(?:\s*\((.+)\))?$/);
    var name = match ? match[1].trim() : text;
    var country = match && match[2] ? match[2].trim() : '';
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
    API.put('/api/authors/' + currentAuthorId, { name: name, country: country || null })
        .then(function () {
            document.getElementById('edit-author-modal').classList.remove('active');
            loadAuthor();
        })
        .catch(function (err) {
            alert('Failed to save author: ' + err.message);
        });
}

function genreBadgeClass(genre) {
    if (genre === 'Fiction') return 'genre-badge genre-fiction';
    if (genre === 'Non-fiction') return 'genre-badge genre-nonfiction';
    return 'genre-badge genre-unknown';
}

function genreBadgeLabel(genre) {
    return genre || '—';
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
