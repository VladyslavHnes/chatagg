var currentPage = 1;
var currentSize = 20;
var totalItems = 0;
var cachedAuthors = null;
var cachedCountries = null;

function clearBooksCache() {
    var keys = [];
    for (var i = 0; i < sessionStorage.length; i++) {
        if (sessionStorage.key(i).indexOf('books_cache:') === 0) keys.push(sessionStorage.key(i));
    }
    for (var i = 0; i < keys.length; i++) sessionStorage.removeItem(keys[i]);
}

document.addEventListener('DOMContentLoaded', function () {
    // Pre-fill filters from URL params
    var urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('country')) document.getElementById('filter-country').value = urlParams.get('country');
    if (urlParams.get('genre')) document.getElementById('filter-genre').value = urlParams.get('genre');
    if (urlParams.get('title')) document.getElementById('filter-title').value = urlParams.get('title');
    if (urlParams.get('author')) document.getElementById('filter-author').value = urlParams.get('author');

    loadBooks();

    document.getElementById('sync-btn').addEventListener('click', syncNow);
    document.getElementById('add-book-btn').addEventListener('click', function () {
        document.getElementById('new-book-title').value = '';
        document.getElementById('new-book-author').value = '';
        document.getElementById('new-book-genre').value = '';
        document.getElementById('new-book-date').value = new Date().toISOString().slice(0, 10);
        document.getElementById('new-book-country').value = '';
        document.getElementById('new-book-country-group').style.display = 'none';
        document.getElementById('author-suggestions').style.display = 'none';
        document.getElementById('country-suggestions').style.display = 'none';
        document.getElementById('add-book-modal').classList.add('active');
        document.getElementById('new-book-title').focus();
        if (!cachedAuthors) {
            API.get('/api/authors').then(function (authors) { cachedAuthors = authors; });
        }
        if (!cachedCountries) {
            API.get('/api/countries').then(function (countries) { cachedCountries = countries; });
        }
    });
    document.getElementById('add-book-cancel-btn').addEventListener('click', function () {
        document.getElementById('add-book-modal').classList.remove('active');
    });
    document.getElementById('add-book-save-btn').addEventListener('click', saveNewBook);

    var authorInput = document.getElementById('new-book-author');
    var suggestionsDiv = document.getElementById('author-suggestions');
    authorInput.addEventListener('input', function () {
        var q = this.value.trim().toLowerCase();
        if (!q || !cachedAuthors) {
            suggestionsDiv.style.display = 'none';
            return;
        }
        var matches = cachedAuthors.filter(function (a) {
            return a.name && a.name.toLowerCase().indexOf(q) !== -1;
        }).slice(0, 10);
        if (matches.length === 0) {
            suggestionsDiv.style.display = 'none';
            return;
        }
        suggestionsDiv.innerHTML = '';
        for (var i = 0; i < matches.length; i++) {
            var div = document.createElement('div');
            div.style.cssText = 'padding:8px 12px;cursor:pointer;border-bottom:1px solid var(--border-color);';
            div.textContent = matches[i].name + (matches[i].country ? ' (' + matches[i].country + ')' : '');
            div.dataset.name = matches[i].name;
            div.addEventListener('mousedown', function (e) {
                e.preventDefault();
                authorInput.value = this.dataset.name;
                suggestionsDiv.style.display = 'none';
            });
            div.addEventListener('mouseenter', function () {
                this.style.background = 'var(--bg-surface-hover)';
            });
            div.addEventListener('mouseleave', function () {
                this.style.background = '';
            });
            suggestionsDiv.appendChild(div);
        }
        suggestionsDiv.style.display = 'block';
    });
    authorInput.addEventListener('blur', function () {
        setTimeout(function () {
            suggestionsDiv.style.display = 'none';
            // Show country field if the typed author doesn't match an existing one
            var val = authorInput.value.trim().toLowerCase();
            var isExisting = false;
            if (val && cachedAuthors) {
                for (var i = 0; i < cachedAuthors.length; i++) {
                    if (cachedAuthors[i].name && cachedAuthors[i].name.toLowerCase() === val) {
                        isExisting = true;
                        break;
                    }
                }
            }
            document.getElementById('new-book-country-group').style.display = (val && !isExisting) ? '' : 'none';
        }, 200);
    });

    // Country auto-suggest for add-book modal
    var countryInput = document.getElementById('new-book-country');
    var countrySuggestionsDiv = document.getElementById('country-suggestions');
    setupCountrySuggest(countryInput, countrySuggestionsDiv);
    document.getElementById('sort-select').addEventListener('change', function () {
        currentPage = 1;
        loadBooks();
    });
    document.getElementById('filter-genre').addEventListener('change', function () {
        currentPage = 1;
        loadBooks();
    });

    var filterTimer = null;
    function onFilterInput() {
        clearTimeout(filterTimer);
        filterTimer = setTimeout(function () {
            currentPage = 1;
            loadBooks();
        }, 300);
    }
    document.getElementById('filter-title').addEventListener('input', onFilterInput);
    document.getElementById('filter-author').addEventListener('input', onFilterInput);
    document.getElementById('filter-country').addEventListener('input', onFilterInput);
    document.getElementById('prev-btn').addEventListener('click', function () {
        if (currentPage > 1) {
            currentPage--;
            loadBooks();
        }
    });
    document.getElementById('next-btn').addEventListener('click', function () {
        var totalPages = Math.ceil(totalItems / currentSize);
        if (currentPage < totalPages) {
            currentPage++;
            loadBooks();
        }
    });
});

function loadBooks() {
    var sort = document.getElementById('sort-select').value;
    var title = document.getElementById('filter-title').value.trim();
    var author = document.getElementById('filter-author').value.trim();
    var country = document.getElementById('filter-country').value.trim();
    var genre = document.getElementById('filter-genre').value;

    var params = '?page=' + currentPage + '&size=' + currentSize + '&sort=' + sort;
    if (title) params += '&title=' + encodeURIComponent(title);
    if (author) params += '&author=' + encodeURIComponent(author);
    if (country) params += '&country=' + encodeURIComponent(country);
    if (genre) params += '&genre=' + encodeURIComponent(genre);

    var cacheKey = 'books_cache:' + params;
    var cached = sessionStorage.getItem(cacheKey);
    if (cached) renderBooks(JSON.parse(cached));

    API.get('/api/books' + params)
        .then(function (data) {
            sessionStorage.setItem(cacheKey, JSON.stringify(data));
            renderBooks(data);
        })
        .catch(function (err) {
            console.error('Failed to load books:', err);
        });
}

function renderBooks(data) {
    var tbody = document.getElementById('book-table-body');
    var emptyState = document.getElementById('empty-state');
    var items = data.items;

    totalItems = data.total;
    currentPage = data.page;

    tbody.innerHTML = '';

    if (items.length === 0) {
        emptyState.style.display = 'block';
    } else {
        emptyState.style.display = 'none';

        for (var i = 0; i < items.length; i++) {
            var book = items[i];
            var tr = document.createElement('tr');

            var titleTd = document.createElement('td');
            var titleLink = document.createElement('a');
            titleLink.href = 'book.html?id=' + book.id;
            titleLink.textContent = book.title;
            titleTd.appendChild(titleLink);
            tr.appendChild(titleTd);

            var authorsTd = document.createElement('td');
            var authorNames = [];
            if (book.authors) {
                for (var j = 0; j < book.authors.length; j++) {
                    authorNames.push(book.authors[j].name);
                }
            }
            authorsTd.textContent = authorNames.join(', ');
            tr.appendChild(authorsTd);

            var countryTd = document.createElement('td');
            var countries = [];
            if (book.authors) {
                for (var j = 0; j < book.authors.length; j++) {
                    if (book.authors[j].country) countries.push(book.authors[j].country);
                }
            }
            countryTd.textContent = countries.length > 0 ? countries.join(', ') : 'Unknown';
            tr.appendChild(countryTd);

            var genreTd = document.createElement('td');
            genreTd.className = 'nowrap';
            var badge = document.createElement('span');
            badge.className = genreBadgeClass(book.genre);
            badge.textContent = genreBadgeLabel(book.genre);
            genreTd.appendChild(badge);
            tr.appendChild(genreTd);

            var dateTd = document.createElement('td');
            dateTd.className = 'nowrap';
            dateTd.textContent = formatDate(book.announcement_date);
            tr.appendChild(dateTd);

            var quotesTd = document.createElement('td');
            quotesTd.textContent = book.quote_count != null ? book.quote_count : 0;
            tr.appendChild(quotesTd);

            tbody.appendChild(tr);
        }
    }

    updatePagination();
}

function updatePagination() {
    var totalPages = Math.ceil(totalItems / currentSize);
    if (totalPages < 1) {
        totalPages = 1;
    }

    var pageInfo = document.getElementById('page-info');
    pageInfo.textContent = 'Page ' + currentPage + ' of ' + totalPages + ' (' + totalItems + ' books)';

    document.getElementById('prev-btn').disabled = (currentPage <= 1);
    document.getElementById('next-btn').disabled = (currentPage >= totalPages);
}

function formatDate(dateStr) {
    if (!dateStr) {
        return '';
    }
    var d = new Date(dateStr);
    var year = d.getFullYear();
    var month = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return day + '-' + month + '-' + year;
}


function genreBadgeClass(genre) {
    if (genre === 'Fiction') return 'genre-badge genre-fiction';
    if (genre === 'Non-fiction') return 'genre-badge genre-nonfiction';
    return 'genre-badge genre-unknown';
}

function genreBadgeLabel(genre) {
    return genre || '—';
}

function setupCountrySuggest(input, suggestionsDiv) {
    input.addEventListener('input', function () {
        var q = this.value.trim().toLowerCase();
        if (!q || !cachedCountries) {
            suggestionsDiv.style.display = 'none';
            return;
        }
        var matches = cachedCountries.filter(function (c) {
            return c.toLowerCase().indexOf(q) !== -1;
        }).slice(0, 10);
        if (matches.length === 0) {
            suggestionsDiv.style.display = 'none';
            return;
        }
        suggestionsDiv.innerHTML = '';
        for (var i = 0; i < matches.length; i++) {
            var div = document.createElement('div');
            div.style.cssText = 'padding:8px 12px;cursor:pointer;border-bottom:1px solid var(--border-color);';
            div.textContent = matches[i];
            div.dataset.country = matches[i];
            div.addEventListener('mousedown', function (e) {
                e.preventDefault();
                input.value = this.dataset.country;
                suggestionsDiv.style.display = 'none';
            });
            div.addEventListener('mouseenter', function () {
                this.style.background = 'var(--bg-surface-hover)';
            });
            div.addEventListener('mouseleave', function () {
                this.style.background = '';
            });
            suggestionsDiv.appendChild(div);
        }
        suggestionsDiv.style.display = 'block';
    });
    input.addEventListener('blur', function () {
        setTimeout(function () { suggestionsDiv.style.display = 'none'; }, 150);
    });
}

function saveNewBook() {
    var title = document.getElementById('new-book-title').value.trim();
    var author = document.getElementById('new-book-author').value.trim();
    var genre = document.getElementById('new-book-genre').value.trim();
    var country = document.getElementById('new-book-country').value.trim();
    if (!title) {
        alert('Title is required.');
        return;
    }
    var date = document.getElementById('new-book-date').value;
    API.post('/api/books', {
        title: title,
        author: author || null,
        author_country: country || null,
        genre: genre || null,
        date: date || null
    })
        .then(function (result) {
            clearBooksCache();
            sessionStorage.removeItem('authors_cache');
            document.getElementById('add-book-modal').classList.remove('active');
            window.location.href = 'book.html?id=' + result.id;
        })
        .catch(function (err) {
            alert('Failed to add book: ' + err.message);
        });
}

async function syncNow() {
    var syncBtn = document.getElementById('sync-btn');
    var syncLog = document.getElementById('sync-log');
    var syncLogBody = document.getElementById('sync-log-body');

    syncBtn.disabled = true;
    syncLogBody.innerHTML = '';
    syncLog.style.display = 'block';

    var reader = null;
    var aborted = false;

    function abort() {
        aborted = true;
        if (reader) { try { reader.cancel(); } catch (e) {} }
    }

    // Open SSE stream first
    var response;
    try {
        response = await fetch('/api/sync/events', {
            headers: { 'Authorization': 'Basic ' + API.credentials }
        });
    } catch (err) {
        appendLogLine(syncLogBody, 'error', 'Failed to connect: ' + err.message);
        syncBtn.disabled = false;
        return;
    }

    if (!response.ok) {
        appendLogLine(syncLogBody, 'error', 'HTTP ' + response.status);
        syncBtn.disabled = false;
        return;
    }

    reader = response.body.getReader();
    var decoder = new TextDecoder();
    var buffer = '';

    // Consume SSE stream in the background
    (async function consumeStream() {
        while (!aborted) {
            var chunk;
            try { chunk = await reader.read(); } catch (e) { break; }
            if (chunk.done) break;
            buffer += decoder.decode(chunk.value, { stream: true });
            var parts = buffer.split('\n\n');
            buffer = parts.pop();
            for (var i = 0; i < parts.length; i++) {
                handleSseFrame(parts[i], syncLogBody, syncBtn, abort);
            }
        }
        // Stream ended without a done/error event — re-enable button
        if (!aborted && syncBtn.disabled) {
            appendLogLine(syncLogBody, 'error', 'Stream closed unexpectedly');
            syncBtn.disabled = false;
        }
    })();

    // Trigger sync (returns 202 immediately)
    try {
        var r = await fetch('/api/sync', {
            method: 'POST',
            headers: {
                'Authorization': 'Basic ' + API.credentials,
                'Content-Type': 'application/json'
            }
        });
        if (r.status === 409) {
            appendLogLine(syncLogBody, 'error', 'Sync already in progress');
            syncBtn.disabled = false;
            abort();
        } else if (!r.ok) {
            var txt = await r.text();
            appendLogLine(syncLogBody, 'error', 'HTTP ' + r.status + ': ' + txt);
            syncBtn.disabled = false;
            abort();
        }
        // 202 = started; SSE events will follow
    } catch (err) {
        appendLogLine(syncLogBody, 'error', 'Failed to start sync: ' + err.message);
        syncBtn.disabled = false;
        abort();
    }
}

function handleSseFrame(frame, container, syncBtn, abort) {
    var lines = frame.split('\n');
    var eventType = '';
    var data = '';
    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        if (line.indexOf('event:') === 0) {
            eventType = line.slice(6).trim();
        } else if (line.indexOf('data:') === 0) {
            data = line.slice(5).trim();
        }
    }
    if (!eventType || !data) return;
    var json;
    try { json = JSON.parse(data); } catch (e) { return; }

    switch (eventType) {
        case 'connecting':
            appendLogLine(container, 'info', 'Connecting to Telegram...');
            break;
        case 'fetched':
            appendLogLine(container, 'info', 'Fetched ' + json.count + ' messages');
            break;
        case 'book_found':
            appendLogLine(container, 'success', 'Found book: ' + json.title + ' \u2014 ' + json.author);
            break;
        case 'quote_found':
            appendLogLine(container, 'muted', '\u201c' + json.snippet + '\u201d');
            break;
        case 'photo_processed':
            appendLogLine(container, 'info', 'Photo processed');
            break;
        case 'flagged':
            appendLogLine(container, 'warning', '\u26a0 Flagged: ' + json.reason);
            break;
        case 'enriching_authors':
            appendLogLine(container, 'info', 'Enriching ' + json.count + ' authors...');
            break;
        case 'enriching_books':
            appendLogLine(container, 'info', 'Enriching ' + json.count + ' books...');
            break;
        case 'done':
            appendLogLine(container, 'done',
                'Done \u2014 ' + json.new_messages + ' messages, ' +
                json.books_found + ' books, ' +
                json.quotes_found + ' quotes, ' +
                json.photos_processed + ' photos, ' +
                json.flagged_items + ' flagged (' +
                parseFloat(json.duration_seconds).toFixed(1) + 's)');
            syncBtn.disabled = false;
            clearBooksCache();
            currentPage = 1;
            loadBooks();
            abort();
            break;
        case 'error':
            appendLogLine(container, 'error', 'Error: ' + json.message);
            syncBtn.disabled = false;
            abort();
            break;
        case 'auth_required':
            appendLogLine(container, 'warning', 'Authentication required (' + json.auth_type + ')');
            showAuthModal(json.auth_type);
            abort();
            break;
    }
}

function appendLogLine(container, type, text) {
    var div = document.createElement('div');
    div.className = 'sync-log-line sync-log-' + type;
    div.textContent = text;
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

function showAuthModal(authType) {
    var modal = document.getElementById('auth-modal');
    var title = document.getElementById('auth-modal-title');
    var desc = document.getElementById('auth-modal-desc');
    var input = document.getElementById('auth-code-input');
    var errorEl = document.getElementById('auth-error');

    if (authType === 'password') {
        title.textContent = 'Enter Telegram 2FA Password';
        desc.textContent = 'Your account has two-factor authentication enabled. Enter your password below.';
        input.type = 'password';
        input.placeholder = 'Password';
        input.style.letterSpacing = 'normal';
    } else {
        title.textContent = 'Enter Telegram Verification Code';
        desc.textContent = 'A verification code has been sent to your Telegram app. Enter it below to authenticate.';
        input.type = 'text';
        input.placeholder = '12345';
        input.style.letterSpacing = '4px';
    }

    input.value = '';
    errorEl.style.display = 'none';
    modal.classList.add('active');
    input.focus();
}

function hideAuthModal() {
    var modal = document.getElementById('auth-modal');
    modal.classList.remove('active');
}

var lastSubmittedAuthState = null;

function submitAuthCode() {
    var input = document.getElementById('auth-code-input');
    var errorEl = document.getElementById('auth-error');
    var submitBtn = document.getElementById('auth-submit-btn');
    var code = input.value.trim();

    if (!code) {
        errorEl.textContent = 'Please enter a code.';
        errorEl.style.display = 'block';
        return;
    }

    submitBtn.disabled = true;
    errorEl.style.display = 'none';

    // Track which state we're submitting for, so polling doesn't re-prompt
    API.get('/api/auth/status').then(function (s) {
        lastSubmittedAuthState = s.state;
    });

    API.post('/api/auth/code', { code: code })
        .then(function () {
            hideAuthModal();
            // Delay before first poll to give TDLib time to transition state
            setTimeout(function () { pollAuthStatus(); }, 1500);
        })
        .catch(function (err) {
            errorEl.textContent = 'Failed to submit: ' + err.message;
            errorEl.style.display = 'block';
            submitBtn.disabled = false;
        });
}

function pollAuthStatus() {
    var syncLog = document.getElementById('sync-log');
    var syncLogBody = document.getElementById('sync-log-body');
    appendLogLine(syncLogBody, 'info', 'Authenticating with Telegram...');
    syncLog.style.display = 'block';

    var attempts = 0;
    var maxAttempts = 30;

    function poll() {
        attempts++;
        API.get('/api/auth/status')
            .then(function (result) {
                if (result.state === 'ready') {
                    hideAuthModal();
                    document.getElementById('auth-submit-btn').disabled = false;
                    appendLogLine(syncLogBody, 'info', 'Authenticated! Retrying sync...');
                    syncNow();
                } else if (result.state === 'waiting_password' && lastSubmittedAuthState !== 'waiting_password') {
                    // After submitting the code, Telegram wants 2FA password
                    document.getElementById('auth-submit-btn').disabled = false;
                    showAuthModal('password');
                    return; // stop polling — user will submit password, which restarts polling
                } else if (result.state === 'waiting_code' && lastSubmittedAuthState !== 'waiting_code') {
                    // Unexpected, but handle it
                    document.getElementById('auth-submit-btn').disabled = false;
                    showAuthModal('code');
                    return;
                } else if (result.state === 'error') {
                    hideAuthModal();
                    document.getElementById('auth-submit-btn').disabled = false;
                    appendLogLine(syncLogBody, 'error', 'Telegram authentication failed.');
                    document.getElementById('sync-btn').disabled = false;
                } else if (attempts >= maxAttempts) {
                    hideAuthModal();
                    document.getElementById('auth-submit-btn').disabled = false;
                    appendLogLine(syncLogBody, 'error', 'Authentication timed out. Please try again.');
                    document.getElementById('sync-btn').disabled = false;
                } else {
                    setTimeout(poll, 1000);
                }
            })
            .catch(function () {
                if (attempts < maxAttempts) {
                    setTimeout(poll, 1000);
                }
            });
    }

    poll();
}

document.addEventListener('DOMContentLoaded', function () {
    var authSubmitBtn = document.getElementById('auth-submit-btn');
    var authCancelBtn = document.getElementById('auth-cancel-btn');
    var authInput = document.getElementById('auth-code-input');

    if (authSubmitBtn) {
        authSubmitBtn.addEventListener('click', submitAuthCode);
    }
    if (authCancelBtn) {
        authCancelBtn.addEventListener('click', function () {
            hideAuthModal();
            document.getElementById('sync-btn').disabled = false;
            var syncLog = document.getElementById('sync-log');
            var syncLogBody = document.getElementById('sync-log-body');
            syncLog.style.display = 'none';
            syncLogBody.innerHTML = '';
        });
    }
    if (authInput) {
        authInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                submitAuthCode();
            }
        });
    }
});
