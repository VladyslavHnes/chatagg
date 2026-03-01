(function() {
    const saved = localStorage.getItem('theme');
    const preferred = saved || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    document.documentElement.setAttribute('data-theme', preferred);

    document.addEventListener('DOMContentLoaded', function() {
        const btn = document.getElementById('theme-toggle');
        if (btn) {
            btn.addEventListener('click', function() {
                const current = document.documentElement.getAttribute('data-theme');
                const next = current === 'dark' ? 'light' : 'dark';
                document.documentElement.setAttribute('data-theme', next);
                localStorage.setItem('theme', next);
            });
        }

        // Mark active nav link
        const page = location.pathname.split('/').pop() || 'index.html';
        document.querySelectorAll('nav a').forEach(function(a) {
            if (a.getAttribute('href') === page) a.classList.add('active');
        });
    });
})();
