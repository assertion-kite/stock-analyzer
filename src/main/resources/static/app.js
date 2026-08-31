const state = {
    currentOverview: null,
    chartMode: 'minute',
    insightFocus: 'review',
    watchlistItems: [],
    selectedMonitorCodes: new Set(),
    changeSortDirection: 'desc',
    monitorActive: false,
    monitorIntervalSeconds: 5,
    sectors: [],
    sectorResponse: null,
    sectorSortKey: 'score',
    sectorSortDirection: 'desc',
    currentSectorId: null,
    currentSectorSelectedCode: null,
    sectorDetailLoading: false,
    top10AutoRefreshTimer: null,
    top10AutoRefreshSeconds: 30,
    top10AutoRefreshActive: false,
    top10LastRefreshAt: null,
    fundamentals: null,
    fundamentalTab: 'performance'
};

document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => switchTab(tab.dataset.tab));
});

document.querySelectorAll('[data-sector-sort]').forEach((button) => {
    button.addEventListener('click', () => changeSectorSort(button.dataset.sectorSort));
});

document.getElementById('search-form').addEventListener('submit', handleSearch);
document.getElementById('refresh-watchlist').addEventListener('click', loadWatchlist);
document.getElementById('refresh-sectors').addEventListener('click', () => loadSectors(true));
document.getElementById('refresh-sector-detail').addEventListener('click', () => {
    if (state.currentSectorId) {
        loadSectorDetail(state.currentSectorId, state.currentSectorSelectedCode, true, {activateTab: false});
    }
});
document.getElementById('start-top10-auto-refresh').addEventListener('click', startTop10AutoRefresh);
document.getElementById('stop-top10-auto-refresh').addEventListener('click', () => stopTop10AutoRefresh());
document.getElementById('refresh-ranking-logs').addEventListener('click', loadRankingLogs);
document.getElementById('refresh-capital-flow').addEventListener('click', loadCapitalFlow);
document.getElementById('sort-change').addEventListener('click', toggleChangeSort);
document.getElementById('select-all-monitor').addEventListener('change', toggleAllMonitorSelections);
document.getElementById('start-monitor').addEventListener('click', startDesktopMonitor);
document.getElementById('stop-monitor').addEventListener('click', stopDesktopMonitor);
document.getElementById('overview-watch-btn').addEventListener('click', addCurrentToWatchlist);
document.getElementById('overview-ai-btn').addEventListener('click', () => openResearch(state.currentOverview));
document.getElementById('refresh-fundamentals').addEventListener('click', () => {
    if (state.currentOverview) loadFundamentals(state.currentOverview.stock.fullCode, true);
});
document.getElementById('insight-form').addEventListener('submit', requestInsight);
document.getElementById('insight-question').addEventListener('input', (event) => {
    document.getElementById('question-count').textContent = event.target.value.length;
});

document.querySelectorAll('.chart-mode').forEach((button) => {
    button.addEventListener('click', () => {
        state.chartMode = button.dataset.chart;
        document.querySelectorAll('.chart-mode').forEach((item) => item.classList.toggle('active', item === button));
        renderChart();
    });
});

document.querySelectorAll('.fundamental-tab').forEach((button) => {
    button.addEventListener('click', () => switchFundamentalTab(button.dataset.fundamentalTab));
});

document.querySelectorAll('.prompt-chip').forEach((button) => {
    button.addEventListener('click', () => {
        state.insightFocus = button.dataset.focus;
        document.querySelectorAll('.prompt-chip').forEach((item) => item.classList.toggle('active', item === button));
    });
});

async function api(url, options = {}) {
    const response = await fetch(url, options);
    if (response.status === 204) return null;

    let payload = null;
    try {
        payload = await response.json();
    } catch (ignored) {
        // Some upstream failures return no JSON body.
    }
    if (!response.ok) {
        throw new Error(payload && payload.message ? payload.message : '请求失败，请稍后重试');
    }
    return payload;
}

async function switchTab(tabName) {
    document.querySelectorAll('.tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.tab === tabName));
    document.querySelectorAll('.tab-panel').forEach((panel) => panel.classList.toggle('active', panel.id === `tab-${tabName}`));

    if (tabName === 'watchlist') {
        await loadWatchlist();
        await loadMonitorStatus();
    } else if (tabName === 'sectors' && state.sectors.length === 0) {
        await loadSectors();
    } else if (tabName === 'capital-flow') {
        await loadCapitalFlow();
    } else if (tabName === 'sector-strength') {
        syncTop10RefreshControls();
    } else if (tabName === 'ranking-logs') {
        await loadRankingLogs();
    }
}

async function handleSearch(event) {
    event.preventDefault();
    const input = document.getElementById('search-input');
    const keyword = input.value.trim();
    if (!keyword) {
        showToast('请输入股票名称或代码');
        return;
    }

    toggle('search-loading', true);
    document.getElementById('search-btn').disabled = true;
    try {
        const [sectorSearch, stocks] = await Promise.all([
            api(`/api/sectors/search?keyword=${encodeURIComponent(keyword)}`),
            api(`/api/stock/search?keyword=${encodeURIComponent(keyword)}`)
        ]);
        const matchedSector = sectorSearch && sectorSearch.results && sectorSearch.results[0];
        if (matchedSector) {
            await loadSectorDetail(matchedSector.id);
            showToast(`已进入${matchedSector.type || ''}板块：${matchedSector.name}`);
            return;
        }
        renderSearchResults(stocks || []);
        if (stocks && stocks.length === 1) {
            await loadOverview(stocks[0].fullCode);
        } else if (stocks && stocks.length > 1) {
            const exact = stocks.find((stock) => stock.code === keyword || stock.fullCode === keyword.toLowerCase());
            if (exact) await loadOverview(exact.fullCode);
        }
    } catch (error) {
        showToast(error.message);
    } finally {
        toggle('search-loading', false);
        document.getElementById('search-btn').disabled = false;
    }
}

function renderSearchResults(stocks) {
    const section = document.getElementById('search-results-section');
    const list = document.getElementById('search-results');
    list.replaceChildren();
    section.classList.remove('hidden');
    setText('search-result-count', `${stocks.length} 个结果`);

    if (stocks.length === 0) {
        list.append(createElement('p', 'no-results', '没有找到匹配股票。在线股票目录可能仍在后台更新。'));
        return;
    }

    stocks.forEach((stock) => {
        const button = createElement('button', 'search-result-item');
        button.type = 'button';
        const identity = createElement('span', 'result-identity');
        identity.append(
            createElement('strong', '', stock.name),
            createElement('span', 'stock-code', stock.fullCode)
        );
        button.append(identity, createElement('span', 'industry-tag', stock.industry || '其他'));
        button.addEventListener('click', () => loadOverview(stock.fullCode));
        list.append(button);
    });
}

async function loadOverview(fullCode) {
    toggle('market-empty', false);
    toggle('stock-overview', false);
    toggle('overview-loading', true);
    try {
        const overview = await api(`/api/stock/${encodeURIComponent(fullCode)}/overview`);
        state.currentOverview = overview;
        renderOverview(overview);
        toggle('stock-overview', true);
        loadFundamentals(overview.stock.fullCode);
        await updateWatchButton(overview.stock.fullCode);
    } catch (error) {
        toggle('market-empty', true);
        showToast(error.message);
    } finally {
        toggle('overview-loading', false);
    }
}

function renderOverview(overview) {
    const {stock, quote} = overview;
    setText('overview-name', stock.name);
    setText('overview-code', stock.fullCode);
    setText('overview-industry', stock.industry || '其他');
    setText('overview-time', quote.dateTime ? `行情时间 ${formatQuoteTime(quote.dateTime)}` : '行情时间暂不可用');
    setText('overview-price', formatPrice(quote.currentPrice));

    const change = document.getElementById('overview-change');
    change.textContent = formatPercent(quote.changePercent);
    setTrendClass(change, quote.changePercent);
    setTrendClass(document.getElementById('overview-price'), quote.changePercent);

    setText('metric-open', formatPrice(quote.openPrice));
    setText('metric-high', formatPrice(quote.highPrice));
    setText('metric-low', formatPrice(quote.lowPrice));
    setText('metric-close', formatPrice(quote.yesterdayClose));
    setText('metric-volume', formatVolume(quote.volume));
    setText('metric-turnover', formatAmount(quote.turnover));
    renderChart();
}

function renderChart() {
    if (!state.currentOverview) return;
    const fullCode = state.currentOverview.stock.fullCode;
    const chart = document.getElementById('stock-chart');
    const error = document.getElementById('chart-error');
    error.classList.add('hidden');
    chart.classList.remove('hidden');
    chart.alt = `${state.currentOverview.stock.name}${state.chartMode === 'minute' ? '分时图' : '日K线图'}`;
    chart.onload = () => error.classList.add('hidden');
    chart.onerror = () => {
        chart.classList.add('hidden');
        error.classList.remove('hidden');
    };
    const path = state.chartMode === 'minute' ? 'min' : 'daily';
    chart.src = `https://image.sinajs.cn/newchart/${path}/n/${fullCode}.gif?t=${Date.now()}`;
}

function switchFundamentalTab(tabName) {
    state.fundamentalTab = tabName;
    document.querySelectorAll('.fundamental-tab').forEach((button) => {
        button.classList.toggle('active', button.dataset.fundamentalTab === tabName);
    });
    document.querySelectorAll('.fundamental-panel').forEach((panel) => {
        panel.classList.toggle('active', panel.id === `fundamental-panel-${tabName}`);
    });
}

async function loadFundamentals(fullCode, refresh = false) {
    toggle('fundamentals-loading', true);
    toggle('fundamentals-unavailable', false);
    toggle('fundamentals-content', false);
    document.getElementById('refresh-fundamentals').disabled = true;
    setText('fundamentals-status', '正在连接数据源');
    try {
        const query = refresh ? '?refresh=true' : '';
        const snapshot = await api(`/api/stock/${encodeURIComponent(fullCode)}/fundamentals${query}`);
        if (!state.currentOverview || state.currentOverview.stock.fullCode !== fullCode) return;
        state.fundamentals = snapshot;
        renderFundamentals(snapshot);
    } catch (error) {
        toggle('fundamentals-content', false);
        const notice = document.getElementById('fundamentals-unavailable');
        notice.textContent = error.message;
        notice.classList.remove('hidden');
        setText('fundamentals-status', '资料暂不可用');
    } finally {
        toggle('fundamentals-loading', false);
        document.getElementById('refresh-fundamentals').disabled = false;
    }
}

function renderFundamentals(snapshot) {
    const warnings = snapshot.warnings || [];
    const sources = snapshot.sources || [];
    const availableSources = sources.filter((source) => source.available).length;
    const statusParts = [];
    if (snapshot.fetchedAt) statusParts.push(`更新 ${formatDataDateTime(snapshot.fetchedAt)}`);
    if (sources.length) statusParts.push(`数据源 ${availableSources}/${sources.length}`);
    if (warnings.length) statusParts.push(`${warnings.length} 项暂缺`);
    setText('fundamentals-status', statusParts.join(' · ') || '资料已更新');

    const notice = document.getElementById('fundamentals-unavailable');
    if (!snapshot.available) {
        toggle('fundamentals-content', false);
        notice.textContent = warnings[0] || '基本面资料暂不可用';
        notice.classList.remove('hidden');
        return;
    }
    notice.classList.add('hidden');
    toggle('fundamentals-content', true);

    if (snapshot.profile && snapshot.profile.industry) {
        setText('overview-industry', snapshot.profile.industry);
    }
    renderPerformance(snapshot.performance || []);
    renderValuation(snapshot.valuation);
    renderResearchReports(snapshot.researchReports || []);
    renderIndustryProfile(snapshot);
    switchFundamentalTab(state.fundamentalTab);
}

function renderPerformance(periods) {
    const summary = document.getElementById('performance-summary');
    const body = document.getElementById('performance-body');
    summary.replaceChildren();
    body.replaceChildren();
    toggle('performance-empty', periods.length === 0);
    document.querySelector('#fundamental-panel-performance .research-table-wrap')
        .classList.toggle('hidden', periods.length === 0);
    if (periods.length === 0) return;

    const latest = periods[0];
    summary.append(
        metricItem('营业收入', formatFinancialAmount(latest.revenue), formatPercent(latest.revenueYoY), latest.revenueYoY),
        metricItem('归母净利润', formatFinancialAmount(latest.netProfit), formatPercent(latest.netProfitYoY), latest.netProfitYoY),
        metricItem('ROE', formatPlainPercent(latest.roe), latest.reportDate || ''),
        metricItem('毛利率', formatPlainPercent(latest.grossMargin), latest.reportName || '')
    );

    periods.forEach((period) => {
        const row = document.createElement('tr');
        row.append(createElement('td', '', period.reportDate || period.reportName || '--'));
        row.append(numberCell(formatFinancialAmount(period.revenue)));
        row.append(trendNumberCell(formatPercent(period.revenueYoY), period.revenueYoY));
        row.append(numberCell(formatFinancialAmount(period.netProfit)));
        row.append(trendNumberCell(formatPercent(period.netProfitYoY), period.netProfitYoY));
        row.append(numberCell(formatPlainPercent(period.roe)));
        row.append(numberCell(formatPlainPercent(period.grossMargin)));
        row.append(numberCell(formatPlainPercent(period.debtRatio)));
        row.append(createElement('td', 'source-cell', period.source || '--'));
        body.append(row);
    });
}

function renderValuation(valuation) {
    const metrics = document.getElementById('valuation-metrics');
    const peerBody = document.getElementById('valuation-peer-body');
    metrics.replaceChildren();
    peerBody.replaceChildren();
    toggle('valuation-empty', !valuation);
    if (!valuation) {
        toggle('valuation-peer-wrap', false);
        return;
    }

    metrics.append(
        metricItem('PE-TTM', formatMultiple(valuation.peTtm), valuation.peRank ? `行业排名 ${valuation.peRank}` : valuation.date || ''),
        metricItem('市净率 PB', formatMultiple(valuation.pb), valuation.industryPb != null ? `行业中位 ${formatMultiple(valuation.industryPb)}` : ''),
        metricItem('PEG', formatMultiple(valuation.peg), '盈利增速匹配度'),
        metricItem('市销率 PS', formatMultiple(valuation.ps), valuation.industryPeTtm != null ? `行业PE ${formatMultiple(valuation.industryPeTtm)}` : ''),
        metricItem('市现率 PCF', formatMultiple(valuation.pcf), valuation.date || ''),
        metricItem('总市值', formatFinancialAmount(valuation.totalMarketValue), '最新估值数据')
    );

    const peers = valuation.peers || [];
    toggle('valuation-peer-wrap', peers.length > 0);
    peers.forEach((peer) => {
        const row = document.createElement('tr');
        const nameCell = document.createElement('td');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', peer.name || '--'), createElement('span', 'stock-code', peer.code || ''));
        nameCell.append(identity);
        row.append(nameCell);
        row.append(numberCell(formatMultiple(peer.peTtm)));
        row.append(numberCell(formatMultiple(peer.pb)));
        row.append(numberCell(formatMultiple(peer.peg)));
        row.append(numberCell(peer.rank || '--'));
        peerBody.append(row);
    });
}

function renderResearchReports(reports) {
    const list = document.getElementById('research-report-list');
    list.replaceChildren();
    toggle('reports-empty', reports.length === 0);
    reports.forEach((report) => {
        const article = createElement('article', 'research-report-item');
        const meta = createElement('div', 'report-meta');
        meta.append(
            createElement('span', '', report.date || '--'),
            createElement('span', '', report.institution || '未知机构'),
            createElement('strong', '', report.rating || '未评级')
        );
        const title = report.pdfUrl && report.pdfUrl.startsWith('http')
            ? document.createElement('a')
            : document.createElement('strong');
        title.textContent = report.title || '未命名研报';
        if (title.tagName === 'A') {
            title.href = report.pdfUrl;
            title.target = '_blank';
            title.rel = 'noopener noreferrer';
        }
        const forecasts = createElement('div', 'report-forecasts');
        appendForecasts(forecasts, 'EPS', report.epsForecasts);
        appendForecasts(forecasts, 'PE', report.peForecasts);
        article.append(meta, title);
        if (forecasts.childElementCount) article.append(forecasts);
        list.append(article);
    });
}

function renderIndustryProfile(snapshot) {
    const profileContainer = document.getElementById('company-profile');
    const positionContainer = document.getElementById('industry-position');
    const tagsContainer = document.getElementById('business-tags');
    const sourcesContainer = document.getElementById('data-sources');
    profileContainer.replaceChildren();
    positionContainer.replaceChildren();
    tagsContainer.replaceChildren();
    sourcesContainer.replaceChildren();

    const profile = snapshot.profile;
    if (profile) {
        const heading = createElement('div', 'subsection-heading');
        heading.append(createElement('h3', '', profile.companyName || '公司概况'), createElement('span', '', profile.industry || '行业待补充'));
        const grid = createElement('dl', 'profile-grid');
        appendProfileItem(grid, '上市日期', profile.listDate);
        appendProfileItem(grid, '总市值', formatFinancialAmount(profile.totalMarketValue));
        appendProfileItem(grid, '流通市值', formatFinancialAmount(profile.floatMarketValue));
        appendProfileItem(grid, '主营业务', profile.mainBusiness, true);
        appendProfileItem(grid, '经营范围', profile.businessScope, true);
        profileContainer.append(heading, grid);
    }

    const position = snapshot.industryPosition;
    if (position) {
        positionContainer.append(createElement('h3', '', '同行位置'));
        const metrics = createElement('div', 'position-metrics');
        metrics.append(
            metricItem('估值排名', position.valuationRank || '--', position.industry || ''),
            metricItem('成长排名', position.growthRank || '--', '同行净利润增速'),
            metricItem('ROE排名', position.roeRank || '--', '数据源可用时更新'),
            metricItem('规模排名', position.scaleRank || '--', '数据源可用时更新')
        );
        positionContainer.append(metrics);
    }

    const concepts = snapshot.concepts || [];
    if (concepts.length) {
        tagsContainer.append(createElement('h3', '', '业务标签'));
        const tags = createElement('div', 'tag-list');
        concepts.forEach((concept) => tags.append(createElement('span', 'business-tag', concept)));
        tagsContainer.append(tags);
    }

    const sources = snapshot.sources || [];
    if (sources.length) {
        sourcesContainer.append(createElement('h3', '', '数据来源'));
        const list = createElement('div', 'source-list');
        sources.forEach((item) => {
            const sourceItem = createElement('div', `source-status ${item.available ? 'available' : 'unavailable'}`);
            sourceItem.append(createElement('span', 'source-dot'), createElement('span', '', item.name));
            if (!item.available && item.message) sourceItem.title = item.message;
            list.append(sourceItem);
        });
        sourcesContainer.append(list);
    }
}

function metricItem(label, value, detail = '', trendValue = null) {
    const item = createElement('div', 'fundamental-metric');
    const strong = createElement('strong', '', value == null || value === '' ? '--' : value);
    if (Number.isFinite(trendValue)) setTrendClass(strong, trendValue);
    item.append(createElement('span', '', label), strong);
    if (detail) item.append(createElement('small', '', detail));
    return item;
}

function appendProfileItem(list, label, value, wide = false) {
    if (!value || value === '--') return;
    const item = createElement('div', wide ? 'profile-item wide' : 'profile-item');
    item.append(createElement('dt', '', label), createElement('dd', '', value));
    list.append(item);
}

function appendForecasts(container, label, values) {
    if (!values) return;
    Object.entries(values).forEach(([year, value]) => {
        if (!Number.isFinite(value)) return;
        container.append(createElement('span', '', `${year} ${label} ${Number(value).toFixed(2)}`));
    });
}

function trendNumberCell(text, value) {
    const cell = numberCell(text);
    if (Number.isFinite(value)) setTrendClass(cell, value);
    return cell;
}

function formatPlainPercent(value) {
    return Number.isFinite(value) ? `${Number(value).toFixed(2)}%` : '--';
}

function formatMultiple(value) {
    return Number.isFinite(value) ? `${Number(value).toFixed(2)}x` : '--';
}

function formatFinancialAmount(value) {
    if (!Number.isFinite(value)) return '--';
    const absolute = Math.abs(value);
    const sign = value < 0 ? '-' : '';
    if (absolute >= 100000000) return `${sign}${(absolute / 100000000).toFixed(2)}亿元`;
    if (absolute >= 10000) return `${sign}${(absolute / 10000).toFixed(1)}万元`;
    return `${sign}${absolute.toFixed(0)}元`;
}

function formatDataDateTime(value) {
    return value ? String(value).replace('T', ' ').slice(0, 16) : '--';
}

async function updateWatchButton(fullCode) {
    const button = document.getElementById('overview-watch-btn');
    try {
        const result = await api(`/api/watchlist/${encodeURIComponent(fullCode)}/exists`);
        button.textContent = result.exists ? '已在自选' : '加入自选';
        button.disabled = result.exists;
    } catch (ignored) {
        button.textContent = '加入自选';
        button.disabled = false;
    }
}

async function addCurrentToWatchlist() {
    if (!state.currentOverview) return;
    const button = document.getElementById('overview-watch-btn');
    button.disabled = true;
    try {
        await api('/api/watchlist', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({code: state.currentOverview.stock.fullCode})
        });
        button.textContent = '已在自选';
        showToast('已加入自选');
    } catch (error) {
        button.disabled = false;
        showToast(error.message);
    }
}

async function loadWatchlist() {
    toggle('watchlist-loading', true);
    try {
        const items = await api('/api/watchlist');
        renderWatchlist(items || []);
    } catch (error) {
        showToast(error.message);
    } finally {
        toggle('watchlist-loading', false);
    }
}

function renderWatchlist(items) {
    state.watchlistItems = items;
    const activeCodes = new Set(items.map((item) => item.stock.fullCode));
    state.selectedMonitorCodes.forEach((code) => {
        if (!activeCodes.has(code)) state.selectedMonitorCodes.delete(code);
    });

    const body = document.getElementById('watchlist-body');
    body.replaceChildren();
    toggle('watchlist-empty', items.length === 0);
    toggle('watchlist-table-wrap', items.length > 0);
    sortedWatchlistItems().forEach((item) => body.append(createWatchlistRow(item)));
    updateMonitorSelectionUi();
}

function sortedWatchlistItems() {
    const direction = state.changeSortDirection === 'desc' ? -1 : 1;
    return [...state.watchlistItems].sort((left, right) => {
        const leftAvailable = left.quoteAvailable && left.quote;
        const rightAvailable = right.quoteAvailable && right.quote;
        if (!leftAvailable && !rightAvailable) return 0;
        if (!leftAvailable) return 1;
        if (!rightAvailable) return -1;
        const leftChange = left.quote.changePercent;
        const rightChange = right.quote.changePercent;
        return (leftChange - rightChange) * direction;
    });
}

function toggleChangeSort() {
    state.changeSortDirection = state.changeSortDirection === 'desc' ? 'asc' : 'desc';
    setText('sort-change-icon', state.changeSortDirection === 'desc' ? '↓' : '↑');
    renderWatchlist(state.watchlistItems);
}

function createWatchlistRow(item) {
    const {stock, quote, quoteAvailable} = item;
    const row = document.createElement('tr');
    if (!quoteAvailable) row.classList.add('quote-missing-row');

    const stockCell = document.createElement('td');
    const identity = createElement('div', 'table-stock-identity');
    identity.append(createElement('strong', '', stock.name), createElement('span', 'stock-code', stock.fullCode));
    stockCell.append(identity);
    row.append(stockCell);

    if (quoteAvailable && quote) {
        row.append(numberCell(formatPrice(quote.currentPrice)));
        const changeCell = numberCell(formatPercent(quote.changePercent));
        changeCell.classList.add('strong-cell');
        setTrendClass(changeCell, quote.changePercent);
        row.append(changeCell);
        row.append(numberCell(`${formatPrice(quote.highPrice)} / ${formatPrice(quote.lowPrice)}`));
        row.append(numberCell(formatVolume(quote.volume)));
        row.append(numberCell(formatAmount(quote.turnover)));
        row.append(createElement('td', 'time-cell', formatQuoteTime(quote.dateTime)));
    } else {
        row.append(numberCell('--'), numberCell('--'), numberCell('--'), numberCell('--'), numberCell('--'));
        row.append(createElement('td', 'time-cell', '行情暂不可用'));
    }

    const actionCell = document.createElement('td');
    const actions = createElement('div', 'table-actions');
    const viewButton = createElement('button', 'text-button', '走势');
    viewButton.type = 'button';
    viewButton.addEventListener('click', () => {
        switchTab('market');
        loadOverview(stock.fullCode);
    });
    const aiButton = createElement('button', 'text-button', 'AI研判');
    aiButton.type = 'button';
    aiButton.addEventListener('click', async () => {
        if (quoteAvailable && quote) {
            openResearch({stock, quote});
            return;
        }
        try {
            openResearch(await api(`/api/stock/${encodeURIComponent(stock.fullCode)}/overview`));
        } catch (error) {
            showToast(error.message);
        }
    });
    const sectorButton = createElement('button', 'text-button', '板块强度');
    sectorButton.type = 'button';
    sectorButton.addEventListener('click', () => openStockSector(stock.fullCode));
    const removeButton = createElement('button', 'text-button danger', '移除');
    removeButton.type = 'button';
    removeButton.addEventListener('click', () => removeWatchlistItem(stock.fullCode));
    actions.append(viewButton, sectorButton, aiButton, removeButton);
    actionCell.append(actions);
    row.append(actionCell);

    const monitorCell = createElement('td', 'monitor-cell');
    const monitorCheckbox = document.createElement('input');
    monitorCheckbox.type = 'checkbox';
    monitorCheckbox.className = 'monitor-checkbox';
    monitorCheckbox.checked = state.selectedMonitorCodes.has(stock.fullCode);
    monitorCheckbox.disabled = !quoteAvailable;
    monitorCheckbox.setAttribute('aria-label', `盯盘 ${stock.name}`);
    monitorCheckbox.addEventListener('change', () => {
        if (monitorCheckbox.checked) state.selectedMonitorCodes.add(stock.fullCode);
        else state.selectedMonitorCodes.delete(stock.fullCode);
        updateMonitorSelectionUi();
    });
    monitorCell.append(monitorCheckbox);
    row.append(monitorCell);
    return row;
}

async function removeWatchlistItem(fullCode) {
    try {
        await api(`/api/watchlist/${encodeURIComponent(fullCode)}`, {method: 'DELETE'});
        state.selectedMonitorCodes.delete(fullCode);
        showToast('已移除自选');
        await loadWatchlist();
    } catch (error) {
        showToast(error.message);
    }
}

function numberCell(value) {
    return createElement('td', 'number-cell', value);
}

function toggleAllMonitorSelections(event) {
    const selectableCodes = state.watchlistItems
        .filter((item) => item.quoteAvailable)
        .map((item) => item.stock.fullCode);
    selectableCodes.forEach((code) => {
        if (event.target.checked) state.selectedMonitorCodes.add(code);
        else state.selectedMonitorCodes.delete(code);
    });
    renderWatchlist(state.watchlistItems);
}

function updateMonitorSelectionUi() {
    const selectedCount = state.selectedMonitorCodes.size;
    setText('monitor-selection-count', `已选 ${selectedCount} 只`);
    document.getElementById('start-monitor').disabled = selectedCount === 0;

    const selectableCodes = state.watchlistItems
        .filter((item) => item.quoteAvailable)
        .map((item) => item.stock.fullCode);
    const selectAll = document.getElementById('select-all-monitor');
    const checkedCount = selectableCodes.filter((code) => state.selectedMonitorCodes.has(code)).length;
    selectAll.checked = selectableCodes.length > 0 && checkedCount === selectableCodes.length;
    selectAll.indeterminate = checkedCount > 0 && checkedCount < selectableCodes.length;
}

async function startDesktopMonitor() {
    const codes = [...state.selectedMonitorCodes];
    if (codes.length === 0) return;
    const intervalInput = document.getElementById('monitor-interval-seconds');
    const intervalSeconds = Number.parseInt(intervalInput.value, 10);
    if (!Number.isInteger(intervalSeconds) || intervalSeconds < 1 || intervalSeconds > 300) {
        showToast('刷新频率请输入 1 到 300 秒');
        intervalInput.focus();
        return;
    }
    const button = document.getElementById('start-monitor');
    button.disabled = true;
    try {
        const status = await api('/api/watchlist/monitor/start', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({codes, intervalSeconds})
        });
        renderMonitorStatus(status);
        showToast(`已启动 ${status.count} 只股票桌面盯盘`);
    } catch (error) {
        showToast(error.message);
    } finally {
        button.disabled = state.selectedMonitorCodes.size === 0;
    }
}

async function stopDesktopMonitor() {
    try {
        const status = await api('/api/watchlist/monitor/stop', {method: 'POST'});
        renderMonitorStatus(status);
        showToast('已停止桌面盯盘');
    } catch (error) {
        showToast(error.message);
    }
}

async function loadMonitorStatus() {
    try {
        const status = await api('/api/watchlist/monitor/status');
        if (status.active && status.codes) {
            state.selectedMonitorCodes = new Set(status.codes);
            if (state.watchlistItems.length) renderWatchlist(state.watchlistItems);
        }
        if (Number.isInteger(status.intervalSeconds)) {
            state.monitorIntervalSeconds = status.intervalSeconds;
            document.getElementById('monitor-interval-seconds').value = status.intervalSeconds;
        }
        renderMonitorStatus(status);
    } catch (ignored) {
        // Monitor status does not block watchlist rendering.
    }
}

function renderMonitorStatus(status) {
    state.monitorActive = Boolean(status && status.active);
    const element = document.getElementById('monitor-status');
    if (!state.monitorActive) {
        element.classList.add('hidden');
        return;
    }
    element.textContent = `桌面盯盘运行中，共 ${status.count} 只，每 ${status.intervalSeconds} 秒刷新。悬浮文字可直接拖动位置。`;
    element.classList.remove('hidden');
}

async function loadSectors(refresh = false) {
    toggle('sector-loading', true);
    toggle('sector-empty', false);
    toggle('sector-table-wrap', false);
    document.getElementById('refresh-sectors').disabled = true;
    try {
        const response = await api(`/api/sectors?refresh=${refresh}`);
        state.sectors = response.sectors || [];
        state.sectorResponse = response;
        renderSectors(response);
    } catch (error) {
        toggle('sector-empty', true);
        showToast(error.message);
    } finally {
        toggle('sector-loading', false);
        document.getElementById('refresh-sectors').disabled = false;
    }
}

function renderSectors(response) {
    const sectors = response.sectors || [];
    const body = document.getElementById('sector-body');
    body.replaceChildren();
    toggle('sector-empty', sectors.length === 0);
    toggle('sector-table-wrap', sectors.length > 0);

    sortedSectorItems(sectors).forEach((sector) => {
        const row = document.createElement('tr');
        row.classList.add('clickable-sector-row');
        row.tabIndex = 0;
        row.addEventListener('click', () => loadSectorDetail(sector.id));
        row.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                loadSectorDetail(sector.id);
            }
        });
        const sectorCell = document.createElement('td');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', sector.name), createElement('span', 'stock-code', sector.id));
        sectorCell.append(identity);
        row.append(sectorCell);
        row.append(flowNumberCell(formatPercent(sector.changePercent), sector.changePercent));
        row.append(flowNumberCell(formatYuanAmount(sector.netInflow), sector.netInflow));
        row.append(flowNumberCell(formatPercent(sector.netInflowRatio), sector.netInflowRatio));
        row.append(numberCell(formatYuanAmount(sector.totalAmount)));
        row.append(numberCell(String(sector.companyCount || 0)));

        const leaderCell = document.createElement('td');
        const leader = createElement('div', 'table-stock-identity');
        leader.append(
            createElement('strong', '', sector.leaderName || '--'),
            createElement('span', 'stock-code', sector.leaderCode || '')
        );
        leaderCell.append(leader);
        row.append(leaderCell);
        row.append(flowNumberCell(formatPercent(sector.leaderChangePercent), sector.leaderChangePercent));

        const scoreCell = numberCell(Number.isFinite(sector.score) ? sector.score.toFixed(1) : '--');
        scoreCell.classList.add('mainline-score');
        row.append(scoreCell);
        row.append(createElement('td', `sector-status status-${sectorStatusClass(sector.status)}`, sector.status || '观察'));

        const actionCell = document.createElement('td');
        const button = createElement('button', 'text-button', '查看龙头');
        button.type = 'button';
        button.addEventListener('click', (event) => {
            event.stopPropagation();
            loadSectorDetail(sector.id);
        });
        actionCell.append(button);
        row.append(actionCell);
        body.append(row);
    });

    const warnings = [...(response.warnings || [])];
    if (!response.flowAvailable) warnings.push('行业资金流暂不可用，当前主线评分仅使用板块涨幅、领涨强度与成交活跃度。');
    const warningBox = document.getElementById('sector-warnings');
    warningBox.textContent = [...new Set(warnings)].join(' ');
    warningBox.classList.toggle('hidden', warnings.length === 0);
    updateSectorSortIcons();
}

function changeSectorSort(key) {
    if (state.sectorSortKey === key) {
        state.sectorSortDirection = state.sectorSortDirection === 'desc' ? 'asc' : 'desc';
    } else {
        state.sectorSortKey = key;
        state.sectorSortDirection = ['name', 'leaderName', 'status'].includes(key) ? 'asc' : 'desc';
    }
    if (state.sectorResponse) renderSectors(state.sectorResponse);
}

function sortedSectorItems(sectors) {
    const direction = state.sectorSortDirection === 'desc' ? -1 : 1;
    const key = state.sectorSortKey;
    return [...sectors].sort((left, right) => {
        const leftValue = left[key];
        const rightValue = right[key];
        const leftMissing = leftValue === null || leftValue === undefined || leftValue === '';
        const rightMissing = rightValue === null || rightValue === undefined || rightValue === '';
        if (leftMissing && rightMissing) return 0;
        if (leftMissing) return 1;
        if (rightMissing) return -1;
        if (typeof leftValue === 'string' || typeof rightValue === 'string') {
            return String(leftValue).localeCompare(String(rightValue), 'zh-CN') * direction;
        }
        return (Number(leftValue) - Number(rightValue)) * direction;
    });
}

function updateSectorSortIcons() {
    document.querySelectorAll('[data-sector-sort-icon]').forEach((icon) => {
        icon.textContent = icon.dataset.sectorSortIcon === state.sectorSortKey
            ? state.sectorSortDirection === 'desc' ? '↓' : '↑'
            : '';
    });
}

function sectorStatusClass(status) {
    if (status === '主线候选') return 'mainline';
    if (status === '活跃') return 'active';
    if (status === '偏弱') return 'weak';
    return 'watch';
}

async function openStockSector(fullCode) {
    await switchTab('sector-strength');
    state.currentSectorSelectedCode = fullCode;
    await requestSectorDetail(`/api/sectors/stock/${encodeURIComponent(fullCode)}`, true, {activateTab: false});
}

async function loadSectorDetail(sectorId, selectedCode = null, refresh = false, options = {}) {
    if (state.sectorDetailLoading) return false;
    state.currentSectorId = sectorId;
    state.currentSectorSelectedCode = selectedCode;
    syncTop10RefreshControls();
    const selectedQuery = selectedCode ? `&selected=${encodeURIComponent(selectedCode)}` : '';
    return requestSectorDetail(
        `/api/sectors/${encodeURIComponent(sectorId)}?refresh=${refresh}${selectedQuery}`,
        false,
        options
    );
}

async function requestSectorDetail(path, stockLookup, options = {}) {
    if (state.sectorDetailLoading) return false;
    if (options.activateTab !== false) await switchTab('sector-strength');

    state.sectorDetailLoading = true;
    toggle('sector-detail-loading', true);
    toggle('sector-detail-empty', false);
    toggle('sector-strength-table-wrap', false);
    syncTop10RefreshControls();
    try {
        const response = await api(path);
        if (response.sector) state.currentSectorId = response.sector.id;
        state.currentSectorSelectedCode = response.selectedCode || state.currentSectorSelectedCode;
        state.top10LastRefreshAt = response.fetchedAt || new Date().toISOString();
        renderSectorDetail(response);
        updateTop10AutoStatus();
        return true;
    } catch (error) {
        toggle('sector-detail-empty', true);
        showToast(error.message);
        return false;
    } finally {
        state.sectorDetailLoading = false;
        toggle('sector-detail-loading', false);
        syncTop10RefreshControls();
    }
}

function readTop10RefreshSeconds() {
    const input = document.getElementById('top10-interval-seconds');
    const seconds = Number.parseInt(input.value, 10);
    if (!Number.isInteger(seconds) || seconds < 5 || seconds > 3600) {
        showToast('定时刷新间隔需设置为 5 到 3600 秒');
        input.focus();
        return null;
    }
    input.value = String(seconds);
    return seconds;
}

async function startTop10AutoRefresh() {
    if (!state.currentSectorId) {
        showToast('请先选择一个板块');
        return;
    }
    const seconds = readTop10RefreshSeconds();
    if (seconds === null) return;

    stopTop10AutoRefresh(true);
    state.top10AutoRefreshSeconds = seconds;
    state.top10AutoRefreshActive = true;
    syncTop10RefreshControls();
    updateTop10AutoStatus();
    await runTop10AutoRefresh();
}

async function runTop10AutoRefresh() {
    if (!state.top10AutoRefreshActive) return;
    if (!state.sectorDetailLoading && state.currentSectorId) {
        await loadSectorDetail(
            state.currentSectorId,
            state.currentSectorSelectedCode,
            true,
            {activateTab: false}
        );
    }
    scheduleTop10AutoRefresh();
}

function scheduleTop10AutoRefresh() {
    window.clearTimeout(state.top10AutoRefreshTimer);
    if (!state.top10AutoRefreshActive) return;
    state.top10AutoRefreshTimer = window.setTimeout(
        runTop10AutoRefresh,
        state.top10AutoRefreshSeconds * 1000
    );
}

function stopTop10AutoRefresh(silent = false) {
    window.clearTimeout(state.top10AutoRefreshTimer);
    state.top10AutoRefreshTimer = null;
    state.top10AutoRefreshActive = false;
    syncTop10RefreshControls();
    updateTop10AutoStatus();
    if (!silent) showToast('Top10 定时刷新已停止');
}

function syncTop10RefreshControls() {
    const hasSector = Boolean(state.currentSectorId);
    document.getElementById('refresh-sector-detail').disabled = !hasSector || state.sectorDetailLoading;
    document.getElementById('start-top10-auto-refresh').disabled = !hasSector || state.top10AutoRefreshActive;
    document.getElementById('stop-top10-auto-refresh').disabled = !state.top10AutoRefreshActive;
    document.getElementById('top10-interval-seconds').disabled = state.top10AutoRefreshActive;
}

function updateTop10AutoStatus() {
    const status = document.getElementById('top10-auto-status');
    if (!state.top10AutoRefreshActive) {
        status.classList.add('hidden');
        return;
    }
    const lastUpdate = state.top10LastRefreshAt
        ? formatRefreshClock(state.top10LastRefreshAt)
        : '等待首次更新';
    status.textContent = `定时刷新运行中，每 ${state.top10AutoRefreshSeconds} 秒更新一次 · 最近更新 ${lastUpdate}`;
    status.classList.remove('hidden');
}

function formatRefreshClock(value) {
    if (!value) return '--';
    const text = String(value).replace('T', ' ');
    return text.length >= 19 ? text.slice(11, 19) : text;
}

function renderSectorDetail(response) {
    const stocks = response.stocks || [];
    toggle('sector-detail-empty', stocks.length === 0);
    toggle('sector-strength-table-wrap', stocks.length > 0);
    if (response.sector) {
        setText('sector-detail-title', `${response.sector.name} · 龙头 Top10 分时强度`);
        setText('sector-detail-meta', `${response.sector.type || '板块'} · ${response.sector.companyCount || '--'} 家公司 · 展示 ${stocks.length} 只 · 今日 ${formatPercent(response.sector.changePercent)} · 主线评分 ${formatScore(response.sector.score)}`);
    } else {
        setText('sector-detail-title', '板块龙头分时强度');
        setText('sector-detail-meta', '暂未识别所属板块');
    }
    setText('sector-formula', response.formula || '');

    const selected = stocks.find((stock) => stock.selected);
    const selectedSummary = document.getElementById('sector-selected-summary');
    selectedSummary.replaceChildren();
    if (selected) {
        const score = createElement('strong', '', `${formatScore(selected.score)} 分`);
        setTrendClass(score, selected.score - 50);
        selectedSummary.append(
            createElement('span', '', `所选个股 ${selected.name}`),
            createElement('span', '', `板块排名 ${selected.rank}/${stocks.length}`),
            score,
            createElement('span', '', selected.strengthLabel || '')
        );
        selectedSummary.classList.remove('hidden');
    } else {
        selectedSummary.classList.add('hidden');
    }

    const body = document.getElementById('sector-strength-body');
    body.replaceChildren();
    stocks.forEach((stock) => body.append(createStrengthRow(stock)));

    const warnings = response.warnings && response.warnings.length
        ? ` · ${response.warnings.join('；')}`
        : '';
    setText('sector-detail-source', `${response.source || ''} · 更新时间 ${formatDataDateTime(response.fetchedAt)}${warnings}`);
}

async function loadRankingLogs() {
    const dateInput = document.getElementById('ranking-log-date');
    if (!dateInput.value) dateInput.value = localDateValue(new Date());
    toggle('ranking-log-loading', true);
    toggle('ranking-log-empty', false);
    toggle('ranking-log-content', false);
    document.getElementById('refresh-ranking-logs').disabled = true;
    try {
        const response = await api(`/api/sectors/ranking-logs?date=${encodeURIComponent(dateInput.value)}`);
        renderRankingLogs(response);
    } catch (error) {
        toggle('ranking-log-empty', true);
        showToast(error.message);
    } finally {
        toggle('ranking-log-loading', false);
        document.getElementById('refresh-ranking-logs').disabled = false;
    }
}

function renderRankingLogs(response) {
    const sectors = response.sectors || [];
    const snapshots = response.snapshots || [];
    const hasLogs = snapshots.length > 0;
    toggle('ranking-log-empty', !hasLogs);
    toggle('ranking-log-content', hasLogs);
    if (!hasLogs) return;

    const overview = document.getElementById('ranking-log-overview');
    overview.replaceChildren();
    overview.append(
        rankingOverviewItem('交易日期', response.date || '--'),
        rankingOverviewItem('累计刷新', `${response.refreshCount || 0} 次`),
        rankingOverviewItem('记录板块', `${sectors.length} 个`),
        rankingOverviewItem('排名记录', `${snapshots.reduce((sum, item) => sum + (item.stocks || []).length, 0)} 条`)
    );

    const statistics = document.getElementById('ranking-statistics');
    statistics.replaceChildren();
    sectors.forEach((sector) => statistics.append(createSectorRankingStatistics(sector)));

    const snapshotBody = document.getElementById('ranking-snapshot-body');
    snapshotBody.replaceChildren();
    snapshots.forEach((snapshot) => snapshotBody.append(createRankingSnapshotRow(snapshot)));
}

function rankingOverviewItem(label, value) {
    const item = createElement('div', 'ranking-overview-item');
    item.append(createElement('span', '', label), createElement('strong', '', value));
    return item;
}

function createSectorRankingStatistics(sector) {
    const section = createElement('section', 'ranking-sector-block');
    const heading = createElement('div', 'ranking-sector-heading');
    const title = createElement('div');
    title.append(
        createElement('h3', '', sector.sectorName || '--'),
        createElement('p', '', `当天刷新 ${sector.refreshCount || 0} 次 · 优先展示进入前三更稳定的个股`)
    );
    heading.append(title);
    section.append(heading);

    const wrap = createElement('div', 'stock-table-wrap');
    const table = createElement('table', 'stock-table ranking-stat-table');
    const thead = document.createElement('thead');
    const headRow = document.createElement('tr');
    ['股票', '出现次数', '前3次数', '第1名', '第2名', '第3名', '最佳名次', '平均名次', '名次轨迹'].forEach((label) => {
        headRow.append(createElement('th', label === '股票' || label === '名次轨迹' ? '' : 'number-cell', label));
    });
    thead.append(headRow);
    const tbody = document.createElement('tbody');
    (sector.stocks || []).forEach((stock) => tbody.append(createRankingStatisticRow(stock)));
    table.append(thead, tbody);
    wrap.append(table);
    section.append(wrap);
    return section;
}

function createRankingStatisticRow(stock) {
    const row = document.createElement('tr');
    const stockCell = document.createElement('td');
    const identity = createElement('div', 'table-stock-identity');
    identity.append(createElement('strong', '', stock.stockName), createElement('span', 'stock-code', stock.fullCode));
    stockCell.append(identity);
    row.append(stockCell);
    row.append(numberCell(String(stock.appearances || 0)));
    const topThreeCell = numberCell(String(stock.topThreeCount || 0));
    topThreeCell.classList.add('ranking-top-three');
    row.append(topThreeCell);
    row.append(numberCell(String(stock.firstCount || 0)));
    row.append(numberCell(String(stock.secondCount || 0)));
    row.append(numberCell(String(stock.thirdCount || 0)));
    row.append(numberCell(`#${stock.bestRank || '--'}`));
    row.append(numberCell(Number.isFinite(stock.averageRank) ? stock.averageRank.toFixed(2) : '--'));
    const historyCell = document.createElement('td');
    const history = createElement('div', 'rank-history');
    (stock.rankHistory || []).forEach((rank, index) => {
        const badge = createElement('span', rank <= 3 ? 'top-rank' : '', `#${rank}`);
        badge.title = `第 ${index + 1} 次刷新：第 ${rank} 名`;
        history.append(badge);
    });
    historyCell.append(history);
    row.append(historyCell);
    return row;
}

function createRankingSnapshotRow(snapshot) {
    const row = document.createElement('tr');
    row.append(createElement('td', 'time-cell', formatDataDateTime(snapshot.capturedAt)));
    const sectorCell = document.createElement('td');
    const sector = createElement('div', 'table-stock-identity');
    sector.append(createElement('strong', '', snapshot.sectorName), createElement('span', 'stock-code', snapshot.sectorId));
    sectorCell.append(sector);
    row.append(sectorCell);
    const listCell = document.createElement('td');
    const list = createElement('div', 'snapshot-top10-list');
    (snapshot.stocks || []).forEach((stock) => {
        const item = createElement('span', stock.rank <= 3 ? 'top-rank' : '');
        item.append(
            createElement('b', '', `#${stock.rank}`),
            document.createTextNode(` ${stock.stockName} `),
            createElement('small', '', formatPercent(stock.dailyChangePercent))
        );
        list.append(item);
    });
    listCell.append(list);
    row.append(listCell);
    return row;
}

function localDateValue(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function createStrengthRow(stock) {
    const row = document.createElement('tr');
    if (stock.selected) row.classList.add('selected-strength-row');

    const stockCell = document.createElement('td');
    const identity = createElement('div', 'strength-identity');
    identity.append(
        createElement('strong', '', stock.name),
        createElement('span', 'stock-code', stock.fullCode),
        createElement('small', '', stock.performanceLabel || ''),
        createElement('small', 'leader-reason', stock.leaderReason || '板块综合排名靠前')
    );
    stockCell.append(identity);
    row.append(stockCell);

    const rankCell = numberCell(`#${stock.rank || '--'}`);
    rankCell.classList.add('rank-cell');
    row.append(rankCell);

    const chartCell = document.createElement('td');
    const canvas = document.createElement('canvas');
    canvas.className = 'sector-sparkline';
    canvas.width = 112;
    canvas.height = 42;
    canvas.setAttribute('aria-label', `${stock.name}近36分钟走势`);
    chartCell.append(canvas);
    row.append(chartCell);
    window.requestAnimationFrame(() => drawSparkline(canvas, stock.points || []));

    const priceCell = numberCell('');
    const price = createElement('strong', '', formatPrice(stock.currentPrice));
    const dayChange = createElement('span', '', formatPercent(stock.dailyChangePercent));
    setTrendClass(dayChange, stock.dailyChangePercent);
    priceCell.append(price, dayChange);
    priceCell.classList.add('stacked-number');
    row.append(priceCell);

    row.append(metricTripletCell([stock.return1m, stock.return3m, stock.return5m], true));

    const volumeCell = numberCell(formatRatio(stock.volumeRatio));
    if (stock.volumeExpanded) {
        volumeCell.append(createElement('small', stock.return5m >= 0 ? 'signal-up' : 'signal-risk', stock.return5m >= 0 ? '放量上攻' : '放量回落'));
    } else {
        volumeCell.append(createElement('small', '', '未明显放量'));
    }
    volumeCell.classList.add('stacked-number');
    row.append(volumeCell);

    const flowCell = numberCell(formatYuanAmount(stock.mainNetInflow));
    setTrendClass(flowCell, stock.mainNetInflow);
    flowCell.append(createElement('small', '', `${formatPercent(stock.mainNetRatio)} · ${stock.flowDate || '--'}`));
    flowCell.classList.add('stacked-number');
    row.append(flowCell);

    const valuationCell = numberCell(formatMultiple(stock.pe));
    valuationCell.append(createElement('small', '', '实时板块行情口径'));
    valuationCell.classList.add('stacked-number');
    row.append(valuationCell);

    row.append(researchRatingCell(stock.latestReport));
    row.append(quarterlyPerformanceCell(stock.quarterlyPerformance, 'revenue'));
    row.append(quarterlyPerformanceCell(stock.quarterlyPerformance, 'netProfit'));

    const scoreCell = numberCell(formatScore(stock.score));
    scoreCell.classList.add('strength-score', 'stacked-number');
    scoreCell.append(
        createElement('small', '', stock.strengthLabel || ''),
        createElement('small', 'strength-signals', (stock.signals || []).join(' · '))
    );
    row.append(scoreCell);
    return row;
}

function researchRatingCell(report) {
    const cell = document.createElement('td');
    cell.className = 'research-rating-cell';
    if (!report) {
        cell.append(createElement('span', 'subtle-value', '暂无近期评级'));
        return cell;
    }
    cell.append(
        createElement('strong', '', report.rating || '未评级'),
        createElement('span', '', report.institution || '--'),
        createElement('small', '', report.date || '--')
    );
    if (report.title) cell.title = report.title;
    return cell;
}

function quarterlyPerformanceCell(periods, field) {
    const cell = document.createElement('td');
    const list = createElement('div', 'quarterly-series');
    const items = (periods || []).slice(0, 8);
    if (items.length === 0) {
        list.append(createElement('span', 'subtle-value', '暂无季度数据'));
    } else {
        items.forEach((period) => {
            const row = createElement('span', 'quarterly-line');
            row.append(
                createElement('small', '', period.period || '--'),
                createElement('strong', '', formatFinancialAmount(period[field]))
            );
            if (field === 'netProfit') setTrendClass(row.querySelector('strong'), period[field]);
            list.append(row);
        });
    }
    cell.append(list);
    return cell;
}

function metricTripletCell(values, directional) {
    const cell = document.createElement('td');
    const grid = createElement('div', 'metric-triplet');
    ['1m', '3m', '5m'].forEach((label, index) => {
        const item = createElement('span', 'metric-triplet-item');
        item.append(createElement('small', '', label));
        const value = createElement('strong', '', formatPercent(values[index]));
        if (directional) setTrendClass(value, values[index]);
        item.append(value);
        grid.append(item);
    });
    cell.append(grid);
    return cell;
}

function drawSparkline(canvas, points) {
    const context = canvas.getContext('2d');
    context.clearRect(0, 0, canvas.width, canvas.height);
    const prices = points.map((point) => point.price).filter(Number.isFinite);
    if (prices.length < 2) {
        context.fillStyle = '#8b9692';
        context.font = '11px sans-serif';
        context.fillText('暂无分钟线', 34, 25);
        return;
    }
    const min = Math.min(...prices);
    const max = Math.max(...prices);
    const range = max - min || 1;
    const rising = prices[prices.length - 1] >= prices[0];
    context.strokeStyle = rising ? '#c93632' : '#14805e';
    context.lineWidth = 1.6;
    context.beginPath();
    prices.forEach((price, index) => {
        const x = 2 + index / (prices.length - 1) * (canvas.width - 4);
        const y = 3 + (max - price) / range * (canvas.height - 6);
        if (index === 0) context.moveTo(x, y);
        else context.lineTo(x, y);
    });
    context.stroke();
}

function formatRatio(value) {
    return Number.isFinite(value) ? `${value.toFixed(2)}x` : '--';
}

function formatScore(value) {
    return Number.isFinite(value) ? value.toFixed(1) : '--';
}

async function loadCapitalFlow() {
    toggle('capital-flow-loading', true);
    toggle('capital-flow-empty', false);
    toggle('capital-flow-table-wrap', false);
    toggle('capital-flow-summary', false);
    try {
        const response = await api('/api/watchlist/capital-flow');
        renderCapitalFlow(response.items || []);
    } catch (error) {
        toggle('capital-flow-empty', true);
        showToast(error.message);
    } finally {
        toggle('capital-flow-loading', false);
    }
}

function renderCapitalFlow(items) {
    const body = document.getElementById('capital-flow-body');
    body.replaceChildren();
    toggle('capital-flow-empty', items.length === 0);
    toggle('capital-flow-table-wrap', items.length > 0);

    let totalNetAmount = 0;
    let availableCount = 0;
    items.forEach((item) => {
        const row = document.createElement('tr');
        const stockCell = document.createElement('td');
        const identity = createElement('div', 'table-stock-identity');
        identity.append(createElement('strong', '', item.stock.name), createElement('span', 'stock-code', item.stock.fullCode));
        stockCell.append(identity);
        row.append(stockCell);

        if (!item.available || !item.latest) {
            const unavailable = createElement('td', 'flow-unavailable', item.message || '资金流暂不可用');
            unavailable.colSpan = 8;
            row.append(unavailable);
            body.append(row);
            return;
        }

        const flow = item.latest;
        availableCount += 1;
        totalNetAmount += flow.netAmount;
        row.append(createElement('td', '', flow.date));
        row.append(flowNumberCell(formatPercent(flow.changePercent), flow.changePercent));
        row.append(flowNumberCell(formatYuanAmount(flow.netAmount), flow.netAmount));
        row.append(flowNumberCell(formatPercent(flow.netRatio), flow.netRatio));
        row.append(flowNumberCell(formatYuanAmount(flow.superLargeNet), flow.superLargeNet));
        row.append(flowNumberCell(formatYuanAmount(flow.largeNet), flow.largeNet));
        row.append(flowNumberCell(formatYuanAmount(flow.mediumNet), flow.mediumNet));
        row.append(flowNumberCell(formatYuanAmount(flow.smallNet), flow.smallNet));
        body.append(row);
    });

    const summary = document.getElementById('capital-flow-summary');
    summary.replaceChildren();
    const total = createElement('strong', '', formatYuanAmount(totalNetAmount));
    setTrendClass(total, totalNetAmount);
    summary.append(
        createElement('span', '', `可用数据 ${availableCount}/${items.length} 只`),
        createElement('span', '', '自选股合计净流入'),
        total,
        createElement('small', '', '最近交易日数据，正数代表净流入，负数代表净流出')
    );
    toggle('capital-flow-summary', items.length > 0);
}

function flowNumberCell(text, value) {
    const cell = numberCell(text);
    setTrendClass(cell, value);
    return cell;
}

function openResearch(overview) {
    if (!overview) {
        showToast('请先选择一只股票');
        return;
    }
    state.currentOverview = overview;
    const {stock, quote} = overview;
    setText('research-stock-name', stock.name);
    setText('research-stock-code', stock.fullCode);
    const price = document.getElementById('research-stock-price');
    price.textContent = `${formatPrice(quote.currentPrice)}  ${formatPercent(quote.changePercent)}`;
    setTrendClass(price, quote.changePercent);
    toggle('research-empty', false);
    toggle('research-workspace', true);
    switchTab('research');
}

async function requestInsight(event) {
    event.preventDefault();
    if (!state.currentOverview) {
        showToast('请先选择一只股票');
        return;
    }

    const question = document.getElementById('insight-question').value.trim();
    const submit = document.getElementById('insight-submit');
    submit.disabled = true;
    toggle('insight-result', false);
    toggle('insight-loading', true);
    try {
        const response = await api('/api/stock/insight', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({
                code: state.currentOverview.stock.fullCode,
                focus: state.insightFocus,
                question
            })
        });
        renderInsight(response);
        toggle('insight-result', true);
    } catch (error) {
        showToast(error.message);
    } finally {
        toggle('insight-loading', false);
        submit.disabled = false;
    }
}

function renderInsight(response) {
    const insight = response.insight;
    const container = document.getElementById('insight-result');
    container.replaceChildren();

    const heading = createElement('div', 'insight-heading');
    heading.append(
        createElement('span', 'eyebrow', 'AI RESEARCH NOTE'),
        createElement('h3', '', insight.headline),
        createElement('p', 'answered-question', `问题：${response.question}`)
    );
    container.append(heading);

    const answer = createElement('div', 'insight-answer');
    const paragraphs = Array.isArray(insight.answer) ? insight.answer : [insight.answer];
    paragraphs.filter(Boolean).forEach((paragraph) => answer.append(createElement('p', '', paragraph)));
    container.append(answer);

    if (insight.keyPoints && insight.keyPoints.length) {
        const grid = createElement('div', 'insight-points');
        insight.keyPoints.forEach((point) => {
            const item = createElement('article', `insight-point stance-${normalizeStance(point.stance)}`);
            item.append(createElement('h4', '', point.title), createElement('p', '', point.detail));
            grid.append(item);
        });
        container.append(grid);
    }

    appendInsightList(container, '风险与数据边界', insight.risks, 'risk-list');
    appendInsightList(container, '后续关注', insight.watchItems, 'watch-list');

    if (insight.followUpQuestions && insight.followUpQuestions.length) {
        const followUp = createElement('section', 'follow-up');
        followUp.append(createElement('h4', '', '继续追问'));
        const buttons = createElement('div', 'follow-up-buttons');
        insight.followUpQuestions.forEach((question) => {
            const button = createElement('button', 'follow-up-button', question);
            button.type = 'button';
            button.addEventListener('click', () => {
                const input = document.getElementById('insight-question');
                input.value = question;
                input.dispatchEvent(new Event('input'));
                input.focus();
            });
            buttons.append(button);
        });
        followUp.append(buttons);
        container.append(followUp);
    }

    container.append(createElement('p', 'disclaimer', insight.disclaimer));
}

function appendInsightList(container, title, items, className) {
    if (!items || items.length === 0) return;
    const section = createElement('section', `insight-list ${className}`);
    section.append(createElement('h4', '', title));
    const list = document.createElement('ul');
    items.forEach((item) => list.append(createElement('li', '', item)));
    section.append(list);
    container.append(section);
}

function createElement(tag, className = '', text = '') {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined && text !== null) element.textContent = text;
    return element;
}

function setText(id, value) {
    document.getElementById(id).textContent = value == null ? '' : value;
}

function toggle(id, visible) {
    document.getElementById(id).classList.toggle('hidden', !visible);
}

function setTrendClass(element, value) {
    element.classList.remove('trend-up', 'trend-down', 'trend-flat');
    element.classList.add(value > 0 ? 'trend-up' : value < 0 ? 'trend-down' : 'trend-flat');
}

function normalizeStance(stance) {
    return ['positive', 'neutral', 'risk'].includes(stance) ? stance : 'neutral';
}

function formatPrice(value) {
    return Number.isFinite(value) ? value.toFixed(2) : '--';
}

function formatPercent(value) {
    if (!Number.isFinite(value)) return '--';
    return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function formatVolume(value) {
    if (!Number.isFinite(value)) return '--';
    if (value >= 100000000) return `${(value / 100000000).toFixed(2)}亿股`;
    if (value >= 10000) return `${(value / 10000).toFixed(1)}万股`;
    return `${Math.round(value)}股`;
}

function formatAmount(value) {
    if (!Number.isFinite(value)) return '--';
    if (value >= 10000) return `${(value / 10000).toFixed(2)}亿元`;
    return `${value.toFixed(1)}万元`;
}

function formatYuanAmount(value) {
    if (!Number.isFinite(value)) return '--';
    const absolute = Math.abs(value);
    const sign = value > 0 ? '+' : value < 0 ? '-' : '';
    if (absolute >= 100000000) return `${sign}${(absolute / 100000000).toFixed(2)}亿元`;
    if (absolute >= 10000) return `${sign}${(absolute / 10000).toFixed(1)}万元`;
    return `${sign}${absolute.toFixed(0)}元`;
}

function formatQuoteTime(value) {
    if (!value || value.length < 14) return value || '--';
    return `${value.slice(8, 10)}:${value.slice(10, 12)}:${value.slice(12, 14)}`;
}

let toastTimer = null;
function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.remove('hidden');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.add('hidden'), 3500);
}

window.addEventListener('beforeunload', () => window.clearTimeout(state.top10AutoRefreshTimer));
