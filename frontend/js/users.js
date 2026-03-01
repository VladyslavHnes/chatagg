var resetUserId = null;

document.addEventListener('DOMContentLoaded', function () {
    loadUsers();

    document.getElementById('add-user-btn').addEventListener('click', function () {
        document.getElementById('new-username').value = '';
        document.getElementById('new-password').value = '';
        document.getElementById('new-display-name').value = '';
        document.getElementById('new-role').value = 'user';
        document.getElementById('add-user-modal').classList.add('active');
        document.getElementById('new-username').focus();
    });

    document.getElementById('add-user-cancel-btn').addEventListener('click', function () {
        document.getElementById('add-user-modal').classList.remove('active');
    });

    document.getElementById('add-user-save-btn').addEventListener('click', saveNewUser);

    document.getElementById('reset-cancel-btn').addEventListener('click', function () {
        document.getElementById('reset-password-modal').classList.remove('active');
    });

    document.getElementById('reset-save-btn').addEventListener('click', saveResetPassword);
});

function loadUsers() {
    API.get('/api/users')
        .then(function (users) {
            renderUsers(users);
        })
        .catch(function (err) {
            document.getElementById('empty-state').textContent = 'Failed to load users: ' + err.message;
            document.getElementById('empty-state').style.display = 'block';
        });
}

function renderUsers(users) {
    var tbody = document.getElementById('user-table-body');
    var emptyState = document.getElementById('empty-state');

    if (!users || users.length === 0) {
        tbody.innerHTML = '';
        emptyState.style.display = 'block';
        return;
    }

    emptyState.style.display = 'none';
    tbody.innerHTML = '';

    for (var i = 0; i < users.length; i++) {
        var u = users[i];
        var tr = document.createElement('tr');

        var tdUsername = document.createElement('td');
        tdUsername.textContent = u.username;
        tr.appendChild(tdUsername);

        var tdName = document.createElement('td');
        tdName.textContent = u.display_name;
        tr.appendChild(tdName);

        var tdRole = document.createElement('td');
        tdRole.textContent = u.role;
        tr.appendChild(tdRole);

        var tdCreated = document.createElement('td');
        tdCreated.textContent = formatDate(u.created_at);
        tr.appendChild(tdCreated);

        var tdActions = document.createElement('td');

        var resetBtn = document.createElement('button');
        resetBtn.textContent = 'Reset Password';
        resetBtn.style.cssText = 'background:#1a73e8;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;';
        resetBtn.dataset.id = u.id;
        resetBtn.addEventListener('click', function () {
            openResetPassword(parseInt(this.dataset.id));
        });
        tdActions.appendChild(resetBtn);

        var delBtn = document.createElement('button');
        delBtn.textContent = 'Delete';
        delBtn.style.cssText = 'background:#c62828;color:#fff;padding:4px 12px;border:none;border-radius:4px;cursor:pointer;font-size:0.8rem;margin-left:4px;';
        delBtn.dataset.id = u.id;
        delBtn.dataset.username = u.username;
        delBtn.addEventListener('click', function () {
            deleteUser(parseInt(this.dataset.id), this.dataset.username);
        });
        tdActions.appendChild(delBtn);

        tr.appendChild(tdActions);
        tbody.appendChild(tr);
    }
}

function saveNewUser() {
    var username = document.getElementById('new-username').value.trim();
    var password = document.getElementById('new-password').value;
    var displayName = document.getElementById('new-display-name').value.trim();
    var role = document.getElementById('new-role').value;

    if (!username) { alert('Username is required.'); return; }
    if (!password) { alert('Password is required.'); return; }
    if (!displayName) { alert('Display name is required.'); return; }

    API.post('/api/users', {
        username: username,
        password: password,
        display_name: displayName,
        role: role
    })
        .then(function () {
            document.getElementById('add-user-modal').classList.remove('active');
            loadUsers();
        })
        .catch(function (err) {
            alert('Failed to create user: ' + err.message);
        });
}

function deleteUser(id, username) {
    if (!confirm('Delete user "' + username + '"?')) return;
    API.delete('/api/users/' + id)
        .then(function () {
            loadUsers();
        })
        .catch(function (err) {
            alert('Failed to delete user: ' + err.message);
        });
}

function openResetPassword(id) {
    resetUserId = id;
    document.getElementById('reset-password-input').value = '';
    document.getElementById('reset-password-modal').classList.add('active');
    document.getElementById('reset-password-input').focus();
}

function saveResetPassword() {
    var password = document.getElementById('reset-password-input').value;
    if (!password) { alert('Password is required.'); return; }

    API.put('/api/users/' + resetUserId + '/password', { password: password })
        .then(function () {
            document.getElementById('reset-password-modal').classList.remove('active');
            alert('Password updated.');
        })
        .catch(function (err) {
            alert('Failed to reset password: ' + err.message);
        });
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    return String(d.getDate()).padStart(2, '0') + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + d.getFullYear();
}
