document.addEventListener('DOMContentLoaded', function () {
    document.getElementById('logout-btn').addEventListener('click', function () {
        sessionStorage.removeItem('chatagg_credentials');
        window.location.reload();
    });

    API.loadCurrentUser().then(function (user) {
        if (!user) return;
        var info = document.getElementById('profile-info');
        var html = '<p><strong>Display Name:</strong> ' + escapeHtml(user.display_name) + '</p>';
        html += '<p><strong>Username:</strong> ' + escapeHtml(user.username) + '</p>';
        html += '<p><strong>Role:</strong> ' + escapeHtml(user.role) + '</p>';
        html += '<p><strong>Created:</strong> ' + formatDate(user.created_at) + '</p>';
        info.innerHTML = html;
    });
});

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
