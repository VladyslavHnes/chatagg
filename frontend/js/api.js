const API = {
    baseUrl: '',
    credentials: null,
    currentUser: null,

    init() {
        this.credentials = sessionStorage.getItem('chatagg_credentials');
        if (!this.credentials && !location.pathname.endsWith('login.html')) {
            location.replace('login.html?return=' + encodeURIComponent(location.pathname + location.search));
        }
    },

    async loadCurrentUser() {
        try {
            this.currentUser = await this.get('/api/me');
        } catch (e) {
            this.currentUser = null;
        }
        return this.currentUser;
    },

    isAdmin() {
        return this.currentUser && this.currentUser.role === 'admin';
    },

    async request(path, options = {}) {
        if (!this.credentials) {
            this.init();
        }

        const url = this.baseUrl + path;
        const config = {
            ...options,
            headers: {
                'Authorization': 'Basic ' + this.credentials,
                'Content-Type': 'application/json',
                ...(options.headers || {})
            }
        };

        const response = await fetch(url, config);

        if (response.status === 401) {
            sessionStorage.removeItem('chatagg_credentials');
            sessionStorage.removeItem('chatagg_role');
            this.credentials = null;
            this.currentUser = null;
            location.replace('login.html?return=' + encodeURIComponent(location.pathname + location.search));
            return new Promise(() => {}); // prevent further execution
        }

        if (!response.ok) {
            const text = await response.text();
            throw new Error(`HTTP ${response.status}: ${text}`);
        }

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return response.json();
        }
        return response.text();
    },

    get(path) {
        return this.request(path);
    },

    post(path, body) {
        return this.request(path, {
            method: 'POST',
            body: body ? JSON.stringify(body) : undefined
        });
    },

    put(path, body) {
        return this.request(path, {
            method: 'PUT',
            body: body ? JSON.stringify(body) : undefined
        });
    },

    delete(path) {
        return this.request(path, {
            method: 'DELETE'
        });
    }
};

API.init();
