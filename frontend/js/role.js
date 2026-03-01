function applyAdminVisibility(isAdmin) {
    if (!isAdmin) {
        var adminElements = document.querySelectorAll('.admin-only');
        for (var i = 0; i < adminElements.length; i++) {
            adminElements[i].style.display = 'none';
        }
    }
}

document.addEventListener('DOMContentLoaded', function () {
    // Apply cached role immediately to avoid layout shift on every navigation
    var cachedRole = sessionStorage.getItem('chatagg_role');
    if (cachedRole) {
        applyAdminVisibility(cachedRole === 'admin');
    }

    API.loadCurrentUser().then(function (user) {
        if (!user) return;
        sessionStorage.setItem('chatagg_role', user.role || '');
        applyAdminVisibility(API.isAdmin());
    });
});
