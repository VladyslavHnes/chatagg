document.addEventListener('DOMContentLoaded', function () {
    API.get('/api/stats')
        .then(function (data) {
            renderSummary(data);
            renderHeatmap(data.books_per_day);
            renderYearlyChart(data.books_per_year);
            renderMonthlyChart(data.books_per_month);
            renderHorizontalBarChart('books-country-chart', data.books_per_country, 'country', { page: 'index.html', param: 'country' });
            renderHorizontalBarChart('authors-country-chart', data.authors_per_country, 'country', { page: 'authors.html', param: 'country' });
            renderHorizontalBarChart('fiction-country-chart', data.fiction_per_country, 'country', { page: 'index.html', param: 'country', extra: 'genre=Fiction' });
            renderHorizontalBarChart('nonfiction-country-chart', data.nonfiction_per_country, 'country', { page: 'index.html', param: 'country', extra: 'genre=Non-fiction' });
            renderTopAuthors(data.top_authors);
            renderHorizontalBarChart('genre-list', data.genre_distribution, 'genre', { page: 'index.html', param: 'genre' });
        })
        .catch(function (err) {
            console.error('Failed to load stats:', err);
        });
});

function renderSummary(data) {
    var container = document.getElementById('summary-cards');
    var cards = [
        { value: data.total_books, label: 'Total Books' },
        { value: data.total_quotes, label: 'Total Quotes' },
        { value: data.total_photos, label: 'Total Photos' },
        { value: data.total_authors != null ? data.total_authors : 'N/A', label: 'Total Authors' },
        { value: data.avg_books_per_month != null ? data.avg_books_per_month : 'N/A', label: 'Avg Books/Month' }
    ];

    container.innerHTML = '';
    for (var i = 0; i < cards.length; i++) {
        var card = document.createElement('div');
        card.className = 'stat-card';
        card.innerHTML = '<div class="value">' + cards[i].value + '</div><div class="label">' + cards[i].label + '</div>';
        container.appendChild(card);
    }
}

function renderHeatmap(booksPerDay) {
    var container = document.getElementById('heatmap');
    container.innerHTML = '';

    if (!booksPerDay || booksPerDay.length === 0) {
        container.innerHTML = '<p style="text-align:center;color:var(--text-secondary);">No data yet</p>';
        return;
    }

    // Build lookup map: "YYYY-MM-DD" -> count
    var countMap = {};
    for (var i = 0; i < booksPerDay.length; i++) {
        countMap[booksPerDay[i].day] = booksPerDay[i].count;
    }

    // Determine date range: from first data point to today
    var today = new Date();
    today.setHours(0, 0, 0, 0);

    var firstDate = new Date(booksPerDay[0].day + 'T00:00:00');
    var start = new Date(firstDate);
    start.setDate(start.getDate() - start.getDay()); // align to Sunday

    var dayNames = ['Mon', '', 'Wed', '', 'Fri', '', ''];
    var monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

    // Day labels (rows: Sun-Sat)
    var dayLabels = document.createElement('div');
    dayLabels.className = 'heatmap-day-labels';
    for (var d = 0; d < 7; d++) {
        var lbl = document.createElement('span');
        lbl.textContent = dayNames[d];
        dayLabels.appendChild(lbl);
    }

    // Build columns (weeks)
    var gridWrap = document.createElement('div');
    gridWrap.style.display = 'flex';
    gridWrap.appendChild(dayLabels);

    var grid = document.createElement('div');
    grid.className = 'heatmap-container';

    // Year labels row (above months)
    var yearRow = document.createElement('div');
    yearRow.className = 'heatmap-year-labels';

    // Month labels row
    var monthRow = document.createElement('div');
    monthRow.className = 'heatmap-month-labels';
    monthRow.style.marginLeft = '26px'; // offset for day labels

    var cursor = new Date(start);
    var prevMonth = -1;
    var prevYear = -1;
    var weekCount = 0;

    while (cursor <= today) {
        var col = document.createElement('div');
        col.className = 'heatmap-column';

        // Track month/year label for this week
        var weekStartMonth = cursor.getMonth();
        var weekStartYear = cursor.getFullYear();
        if (weekStartYear !== prevYear) {
            var yl = document.createElement('span');
            yl.textContent = weekStartYear;
            yl.style.position = 'absolute';
            yl.style.left = (weekCount * 17) + 'px';
            yearRow.appendChild(yl);
            prevYear = weekStartYear;
        }
        if (weekStartMonth !== prevMonth) {
            var ml = document.createElement('span');
            ml.textContent = monthNames[weekStartMonth];
            ml.style.position = 'absolute';
            ml.style.left = (weekCount * 17 + 26) + 'px';
            monthRow.appendChild(ml);
            prevMonth = weekStartMonth;
        }

        for (var day = 0; day < 7; day++) {
            var cell = document.createElement('div');
            cell.className = 'heatmap-cell';

            if (cursor <= today) {
                var key = cursor.getFullYear() + '-' +
                    String(cursor.getMonth() + 1).padStart(2, '0') + '-' +
                    String(cursor.getDate()).padStart(2, '0');
                var count = countMap[key] || 0;
                var capped = Math.min(count, 4);
                if (capped > 0) {
                    cell.setAttribute('data-count', String(capped));
                }
                cell.title = key + ': ' + count + ' book' + (count !== 1 ? 's' : '');
            } else {
                cell.style.visibility = 'hidden';
            }

            col.appendChild(cell);
            cursor.setDate(cursor.getDate() + 1);
        }

        grid.appendChild(col);
        weekCount++;
    }

    monthRow.style.position = 'relative';
    monthRow.style.height = '16px';
    container.appendChild(yearRow);
    container.appendChild(monthRow);
    gridWrap.appendChild(grid);
    container.appendChild(gridWrap);

    // Legend
    var legend = document.createElement('div');
    legend.className = 'heatmap-legend';
    legend.innerHTML = '<span>Less</span>';
    for (var lv = 0; lv <= 4; lv++) {
        var lc = document.createElement('div');
        lc.className = 'heatmap-cell';
        if (lv > 0) lc.setAttribute('data-count', String(lv));
        legend.appendChild(lc);
    }
    legend.innerHTML += '<span>More</span>';
    container.appendChild(legend);
}

function renderYearlyChart(booksPerYear) {
    var container = document.getElementById('yearly-chart');
    container.innerHTML = '';

    if (!booksPerYear || booksPerYear.length === 0) {
        container.innerHTML = '<p style="text-align:center; color:#666;">No data yet</p>';
        return;
    }

    var maxCount = 0;
    for (var i = 0; i < booksPerYear.length; i++) {
        if (booksPerYear[i].count > maxCount) maxCount = booksPerYear[i].count;
    }
    if (maxCount === 0) maxCount = 1;

    for (var i = 0; i < booksPerYear.length; i++) {
        var entry = booksPerYear[i];
        var heightPercent = Math.max((entry.count / maxCount) * 75, 8);

        var bar = document.createElement('div');
        bar.className = 'bar';
        bar.style.height = heightPercent + '%';
        bar.style.flex = '0 0 60px';
        bar.innerHTML = '<span class="bar-value">' + entry.count + '</span>' +
            '<span class="bar-label">' + entry.year + '</span>';
        container.appendChild(bar);
    }
}

function renderMonthlyChart(booksPerMonth) {
    var container = document.getElementById('monthly-chart');
    container.innerHTML = '';

    if (!booksPerMonth || booksPerMonth.length === 0) {
        container.innerHTML = '<p style="text-align:center; color:#666;">No data yet</p>';
        return;
    }

    var maxCount = 0;
    for (var i = 0; i < booksPerMonth.length; i++) {
        if (booksPerMonth[i].count > maxCount) maxCount = booksPerMonth[i].count;
    }
    if (maxCount === 0) maxCount = 1;

    for (var i = 0; i < booksPerMonth.length; i++) {
        var entry = booksPerMonth[i];
        var heightPercent = Math.max((entry.count / maxCount) * 75, 8);

        var label = entry.month;
        var parts = label.split('-');
        if (parts.length === 2) {
            label = parts[1] + '.' + parts[0];
        }

        var bar = document.createElement('div');
        bar.className = 'bar';
        bar.style.height = heightPercent + '%';
        bar.innerHTML = '<span class="bar-value">' + entry.count + '</span>' +
            '<span class="bar-label">' + label + '</span>';
        container.appendChild(bar);
    }
}

function renderHorizontalBarChart(containerId, data, labelField, linkConfig) {
    var container = document.getElementById(containerId);
    container.innerHTML = '';

    if (!data || data.length === 0) {
        container.innerHTML = '<p style="text-align:center; color:var(--text-secondary);">No data yet</p>';
        return;
    }

    var maxCount = 0;
    for (var i = 0; i < data.length; i++) {
        if (data[i].count > maxCount) maxCount = data[i].count;
    }
    if (maxCount === 0) maxCount = 1;

    for (var i = 0; i < data.length; i++) {
        var item = data[i];
        var widthPercent = Math.max((item.count / maxCount) * 100, 4);

        var row = document.createElement('div');
        row.style.cssText = 'display:flex;align-items:center;gap:8px;margin-bottom:6px;';

        var labelText = item[labelField] || 'Unknown';
        var label;
        if (linkConfig) {
            label = document.createElement('a');
            var href = linkConfig.page + '?' + linkConfig.param + '=' + encodeURIComponent(labelText);
            if (linkConfig.extra) href += '&' + linkConfig.extra;
            label.href = href;
            label.style.cssText = 'min-width:120px;text-align:right;font-size:0.85rem;color:var(--accent);flex-shrink:0;text-decoration:none;cursor:pointer;';
        } else {
            label = document.createElement('span');
            label.style.cssText = 'min-width:120px;text-align:right;font-size:0.85rem;color:var(--text-primary);flex-shrink:0;';
        }
        label.textContent = labelText;
        row.appendChild(label);

        var barWrap = document.createElement('div');
        barWrap.style.cssText = 'flex:1;background:var(--bg-surface-hover);border-radius:4px;height:24px;overflow:hidden;';

        var bar = document.createElement('div');
        bar.style.cssText = 'height:100%;background:var(--accent);border-radius:4px;transition:width 0.3s;width:' + widthPercent + '%;';
        barWrap.appendChild(bar);
        row.appendChild(barWrap);

        var value = document.createElement('span');
        value.style.cssText = 'min-width:30px;font-size:0.85rem;font-weight:600;color:var(--text-primary);';
        value.textContent = item.count;
        row.appendChild(value);

        container.appendChild(row);
    }
}

function renderTopAuthors(data) {
    var container = document.getElementById('top-authors-chart');
    container.innerHTML = '';

    if (!data || data.length === 0) {
        container.innerHTML = '<p style="text-align:center; color:var(--text-secondary);">No data yet</p>';
        return;
    }

    var filtered = data.filter(function (item) { return item.count > 1; });
    if (filtered.length === 0) {
        container.innerHTML = '<p style="text-align:center; color:var(--text-secondary);">No authors with multiple books</p>';
        return;
    }

    var maxCount = filtered[0].count;
    if (maxCount === 0) maxCount = 1;

    // Measure the widest label
    var measurer = document.createElement('span');
    measurer.style.cssText = 'position:absolute;visibility:hidden;white-space:nowrap;font-size:0.85rem;font-family:inherit;';
    document.body.appendChild(measurer);
    var maxLabelWidth = 0;
    for (var i = 0; i < filtered.length; i++) {
        var text = filtered[i].name + (filtered[i].country && filtered[i].country !== 'Unknown' ? ' (' + filtered[i].country + ')' : '');
        measurer.textContent = text;
        if (measurer.offsetWidth > maxLabelWidth) maxLabelWidth = measurer.offsetWidth;
    }
    document.body.removeChild(measurer);
    var labelWidth = (maxLabelWidth + 8) + 'px';

    for (var i = 0; i < filtered.length; i++) {
        var item = filtered[i];
        var widthPercent = Math.max((item.count / maxCount) * 100, 4);

        var row = document.createElement('div');
        row.style.cssText = 'display:flex;align-items:center;gap:8px;margin-bottom:6px;';

        var label = document.createElement('a');
        label.href = 'index.html?author=' + encodeURIComponent(item.name);
        label.style.cssText = 'width:' + labelWidth + ';text-align:right;font-size:0.85rem;color:var(--accent);flex-shrink:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;text-decoration:none;cursor:pointer;';
        label.textContent = item.name + (item.country && item.country !== 'Unknown' ? ' (' + item.country + ')' : '');
        row.appendChild(label);

        var barWrap = document.createElement('div');
        barWrap.style.cssText = 'flex:1;background:var(--bg-surface-hover);border-radius:4px;height:24px;overflow:hidden;';

        var bar = document.createElement('div');
        bar.style.cssText = 'height:100%;background:var(--accent);border-radius:4px;transition:width 0.3s;width:' + widthPercent + '%;';
        barWrap.appendChild(bar);
        row.appendChild(barWrap);

        var value = document.createElement('span');
        value.style.cssText = 'min-width:30px;font-size:0.85rem;font-weight:600;color:var(--text-primary);';
        value.textContent = item.count;
        row.appendChild(value);

        container.appendChild(row);
    }
}

function renderDistribution(containerId, data, labelField) {
    var container = document.getElementById(containerId);
    container.innerHTML = '';

    if (!data || data.length === 0) {
        container.innerHTML = '<div class="card"><p style="color:#666;">No data yet</p></div>';
        return;
    }

    for (var i = 0; i < data.length; i++) {
        var item = data[i];
        var card = document.createElement('div');
        card.className = 'card';
        card.style.display = 'flex';
        card.style.justifyContent = 'space-between';
        card.style.alignItems = 'center';
        card.innerHTML = '<span>' + escapeHtml(item[labelField]) + '</span><strong>' + item.count + '</strong>';
        container.appendChild(card);
    }
}

function escapeHtml(text) {
    if (!text) return '';
    var div = document.createElement('div');
    div.appendChild(document.createTextNode(text));
    return div.innerHTML;
}
