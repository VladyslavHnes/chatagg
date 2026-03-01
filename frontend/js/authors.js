var allAuthors = [];
var editAuthorId = null;

document.addEventListener('DOMContentLoaded', function () {
    // Pre-fill filter from URL param
    var urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('country')) {
        document.getElementById('filter-input').value = urlParams.get('country');
    }

    loadAuthors();

    document.getElementById('filter-input').addEventListener('input', function () {
        renderAuthors(filterAuthors(this.value));
    });

    document.getElementById('edit-author-cancel-btn').addEventListener('click', function () {
        document.getElementById('edit-author-modal').classList.remove('active');
    });
    document.getElementById('edit-author-save-btn').addEventListener('click', saveEditAuthor);

    document.getElementById('select-all').addEventListener('change', function () {
        var checkboxes = document.querySelectorAll('.author-checkbox');
        for (var i = 0; i < checkboxes.length; i++) {
            checkboxes[i].checked = this.checked;
        }
        updateMergeButton();
    });

    document.getElementById('merge-btn').addEventListener('click', mergeSelected);
});

function loadAuthors() {
    API.get('/api/authors')
        .then(function (authors) {
            allAuthors = authors;
            var filterVal = document.getElementById('filter-input').value;
            renderAuthors(filterVal ? filterAuthors(filterVal) : authors);
        })
        .catch(function (err) {
            document.getElementById('empty-state').textContent = 'Failed to load authors: ' + err.message;
            document.getElementById('empty-state').style.display = 'block';
        });
}

function filterAuthors(query) {
    if (!query) return allAuthors;
    var q = query.toLowerCase();
    return allAuthors.filter(function (a) {
        return (a.name && a.name.toLowerCase().indexOf(q) !== -1) ||
               (a.country && a.country.toLowerCase().indexOf(q) !== -1);
    });
}

function renderAuthors(authors) {
    var tbody = document.getElementById('author-table-body');
    var emptyState = document.getElementById('empty-state');
    document.getElementById('select-all').checked = false;
    updateMergeButton();

    if (!authors || authors.length === 0) {
        tbody.innerHTML = '';
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';
    tbody.innerHTML = '';

    for (var i = 0; i < authors.length; i++) {
        var a = authors[i];
        var tr = document.createElement('tr');

        var tdCheck = document.createElement('td');
        if (API.isAdmin()) {
            var checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.className = 'author-checkbox';
            checkbox.dataset.id = a.id;
            checkbox.dataset.name = a.name || '';
            checkbox.addEventListener('change', updateMergeButton);
            tdCheck.appendChild(checkbox);
        }
        tr.appendChild(tdCheck);

        var tdName = document.createElement('td');
        var nameLink = document.createElement('a');
        nameLink.href = 'author.html?id=' + a.id;
        nameLink.textContent = a.name || '';
        tdName.appendChild(nameLink);
        tr.appendChild(tdName);

        var tdCountry = document.createElement('td');
        tdCountry.textContent = a.country || '';
        tr.appendChild(tdCountry);

        var tdAction = document.createElement('td');
        if (API.isAdmin()) {
            var editBtn = document.createElement('button');
            editBtn.textContent = 'Edit';
            editBtn.style.cssText = 'background:#1a73e8;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;';
            editBtn.dataset.id = a.id;
            editBtn.dataset.name = a.name || '';
            editBtn.dataset.country = a.country || '';
            editBtn.addEventListener('click', function () {
                openEditAuthorModal(
                    parseInt(this.dataset.id),
                    this.dataset.name,
                    this.dataset.country
                );
            });
            tdAction.appendChild(editBtn);

            var delBtn = document.createElement('button');
            delBtn.textContent = 'Delete';
            delBtn.style.cssText = 'background:#c62828;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;margin-left:4px;';
            delBtn.dataset.id = a.id;
            delBtn.dataset.name = a.name || '';
            delBtn.addEventListener('click', function () {
                deleteAuthor(parseInt(this.dataset.id), this.dataset.name);
            });
            tdAction.appendChild(delBtn);
        }
        tr.appendChild(tdAction);

        tbody.appendChild(tr);
    }
}

function updateMergeButton() {
    var checked = document.querySelectorAll('.author-checkbox:checked');
    var btn = document.getElementById('merge-btn');
    btn.disabled = checked.length < 2;
    btn.textContent = checked.length >= 2 ? 'Merge Selected (' + checked.length + ')' : 'Merge Selected';
}

function mergeSelected() {
    var checked = document.querySelectorAll('.author-checkbox:checked');
    if (checked.length < 2) return;

    var names = [];
    for (var i = 0; i < checked.length; i++) {
        names.push(checked[i].dataset.name);
    }

    var keepId = parseInt(checked[0].dataset.id);
    var keepName = checked[0].dataset.name;
    var mergeIds = [];
    for (var i = 1; i < checked.length; i++) {
        mergeIds.push(parseInt(checked[i].dataset.id));
    }

    if (!confirm('Merge ' + names.length + ' authors into "' + keepName + '"?\n\nKeep: ' + keepName + '\nMerge & delete: ' + names.slice(1).join(', '))) {
        return;
    }

    API.post('/api/authors/merge', { keep_id: keepId, merge_ids: mergeIds })
        .then(function () {
            loadAuthors();
        })
        .catch(function (err) {
            alert('Failed to merge: ' + err.message);
        });
}

function openEditAuthorModal(authorId, name, country) {
    editAuthorId = authorId;
    document.getElementById('edit-author-name').value = name;
    document.getElementById('edit-author-country').value = country;
    document.getElementById('edit-author-modal').classList.add('active');
}

function deleteAuthor(authorId, name) {
    if (!confirm('Delete author "' + name + '"? This will remove them from all books.')) {
        return;
    }
    API.delete('/api/authors/' + authorId)
        .then(function () {
            loadAuthors();
        })
        .catch(function (err) {
            alert('Failed to delete author: ' + err.message);
        });
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
            loadAuthors();
        })
        .catch(function (err) {
            alert('Failed to save author: ' + err.message);
        });
}
