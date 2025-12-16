import dash
from dash import dcc, html, dash_table, ctx
from dash.dependencies import Input, Output, State
import plotly.graph_objects as go
import plotly.express as px
from plotly.subplots import make_subplots
import pandas as pd
import sqlite3
import glob
import os
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate

# --- 全局配置 ---
TABLE_STYLE_HEADER = {
    'backgroundColor': 'rgb(240, 240, 240)',
    'fontWeight': 'bold',
    'textAlign': 'center'
}
TABLE_STYLE_CELL = {
    'textAlign': 'left',
    'whiteSpace': 'normal',
    'height': 'auto',
    'padding': '5px'
}

# --- 辅助函数 ---
def connect_db(db_path):
    if not db_path or not os.path.exists(db_path):
        return None
    try:
        return sqlite3.connect(db_path, check_same_thread=False)
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return None

def format_large_number(n):
    if n is None or pd.isna(n): return '-'
    if abs(n) > 1e12: return f"{n / 1e12:.2f} T"
    if abs(n) > 1e9: return f"{n / 1e9:.2f} B"
    if abs(n) > 1e6: return f"{n / 1e6:.2f} M"
    if abs(n) > 1e3: return f"{n / 1e3:.2f} K"
    return f"{n:.2f}"

def format_num(n, precision=2):
    if n is None or pd.isna(n): return '-'
    return f"{n:.{precision}f}"

def format_percent(n):
    if n is None or pd.isna(n): return '-'
    return f"{n * 100:.2f}%"

# --- 初始化 Dash App ---
app = dash.Dash(__name__, external_stylesheets=[dbc.themes.BOOTSTRAP])
app.title = "Utopia Market Analysis"

# --- 布局 (Layout) ---
app.layout = dbc.Container(fluid=True, className="p-4 bg-light", children=[
    dcc.Store(id='global-selected-trader-store'),
    dcc.Store(id='global-selected-stock-store'),
    dcc.Store(id='current-db-path-store'),

    html.H1("Utopia Market Analysis", className="mb-4 text-primary"),

    # 1. 顶部控制栏
    dbc.Card(dbc.CardBody([
        dbc.Row([
            dbc.Col(html.H5("Simulation Data", className="mt-2 text-secondary"), width="auto"),
            dbc.Col(dcc.Input(id='directory-input', value='output', type='text', placeholder="Output Dir", className="form-control"), width=2),
            dbc.Col(dcc.Dropdown(id='db-dropdown', placeholder='Select Simulation Result (.db)...'), width=5),
            dbc.Col(
                dbc.ButtonGroup([
                    dbc.Button("Refresh", id='refresh-button', color='info', outline=True, size="sm"),
                    dbc.Button("Load Data", id='connect-button', color='primary', size="sm"),
                ]), width="auto"
            ),
            dbc.Col(html.Div(id="connection-status", className="mt-2 font-weight-bold"), width="auto")
        ]),
    ]), className="mb-3 shadow-sm"),

    # 2. 主面板 Tabs
    dbc.Tabs(id="main-tabs", active_tab="tab-market", children=[

        # ==============================================================================
        # Tab 1: Market (市场总览 + 活跃股票)
        # ==============================================================================
        dbc.Tab(label='1. Market Overview', tab_id='tab-market', children=[
            dbc.Card(dbc.CardBody([
                dbc.Row([
                    dbc.Col([
                        html.H4("Market Index Trend"),
                        dcc.Loading(dcc.Graph(id='market-kline-chart'))
                    ], width=12),
                ]),
                html.Hr(),
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Day:"),
                        dcc.Slider(id='market-day-slider', min=1, max=100, value=1, step=1, marks=None,
                                   tooltip={"placement": "bottom", "always_visible": True}),
                    ], width=12)
                ], className="mb-3"),

                # 市场详情卡片
                html.Div(id='market-details-output', className="mb-4"),

                # Top 10 活跃股票
                html.H5("Top 10 Active Stocks (by Turnover)"),
                html.Small("Click a stock ID to investigate details."),
                dash_table.DataTable(
                    id='top-active-stocks-table',
                    columns=[
                        {"name": "Rank", "id": "rank"},
                        {"name": "Stock ID", "id": "stock_id"}, # Link logic
                        {"name": "Sector", "id": "sector"},
                        {"name": "Close", "id": "close"},
                        {"name": "Turnover", "id": "turnover"},
                        {"name": "Volume", "id": "volume"},
                        {"name": "Turnover Rate", "id": "turnover_rate"},
                        {"name": "Action", "id": "action", "presentation": "markdown"}
                    ],
                    style_table={'overflowX': 'auto'},
                    style_header=TABLE_STYLE_HEADER,
                    style_cell=TABLE_STYLE_CELL,
                    page_size=10,
                    active_cell=None
                )

            ]), className="mt-3 shadow-sm")
        ]),

        # ==============================================================================
        # Tab 2: Stocks (股票详情 + 量化指标)
        # ==============================================================================
        dbc.Tab(label='2. Stock Analysis', tab_id='tab-stock', children=[
            dbc.Card(dbc.CardBody([
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Date"),
                        dcc.Slider(id='stock-day-slider', min=1, max=100, value=1, step=1, marks=None,
                                   tooltip={"placement": "bottom", "always_visible": True}),
                    ], width=12)
                ], className="mb-3"),

                dbc.Row([
                    # 左侧：股票列表
                    dbc.Col([
                        html.H5("Stock List"),
                        dcc.Input(id='stock-search-input', type='text', placeholder='Search Stock ID...', className="mb-2 form-control form-control-sm"),
                        dash_table.DataTable(
                            id='stock-list-table',
                            columns=[
                                {"name": "ID", "id": "stock_id"},
                                {"name": "Sec", "id": "sector"},
                                {"name": "Price", "id": "close"},
                                {"name": "PE", "id": "pe_ttm"},
                                {"name": "Cap", "id": "total_market_cap"},
                            ],
                            style_table={'height': '700px', 'overflowY': 'auto'},
                            style_header=TABLE_STYLE_HEADER,
                            style_cell=TABLE_STYLE_CELL,
                            fixed_rows={'headers': True},
                            filter_action="native",
                            sort_action="native",
                            row_selectable="single",
                            selected_rows=[],
                            page_size=100
                        )
                    ], width=3),

                    # 右侧：详情面板
                    dbc.Col([
                        dbc.Card([
                            dbc.CardHeader(html.H5(id='stock-detail-title', children="Select a stock...", className="m-0")),
                            dbc.CardBody([
                                # 新增：股票核心指标卡片
                                html.Div(id='stock-metrics-row', className="mb-3"),

                                # 1. 价格 K 线
                                dcc.Loading(dcc.Graph(id='stock-kline-chart', style={'height': '350px'})),

                                # 2. 量化指标 (Tabs)
                                dbc.Tabs([
                                    dbc.Tab(label="Valuation (PE/PB)", children=[
                                        dcc.Loading(dcc.Graph(id='stock-valuation-chart', style={'height': '250px'}))
                                    ]),
                                    dbc.Tab(label="Liquidity (Vol/Turnover)", children=[
                                        dcc.Loading(dcc.Graph(id='stock-liquidity-chart', style={'height': '250px'}))
                                    ]),
                                    dbc.Tab(label="Market Cap", children=[
                                        dcc.Loading(dcc.Graph(id='stock-mcap-chart', style={'height': '250px'}))
                                    ]),
                                ], className="mt-3"),

                                html.Hr(),
                                html.H5("Top Shareholders"),
                                dash_table.DataTable(
                                    id='shareholder-table',
                                    columns=[
                                        {"name": "Trader ID", "id": "trader_id"},
                                        {"name": "Type", "id": "trader_type"},
                                        {"name": "Shares", "id": "quantity"},
                                        {"name": "Value", "id": "value"},
                                        {"name": "Jump", "id": "action", "presentation": "markdown"}
                                    ],
                                    style_table={'height': '200px', 'overflowY': 'auto'},
                                    style_header=TABLE_STYLE_HEADER,
                                    style_cell=TABLE_STYLE_CELL,
                                    page_size=50,
                                    active_cell=None
                                )
                            ])
                        ], className="shadow-sm")
                    ], width=9)
                ])
            ]), className="mt-3 shadow-sm")
        ]),

        # ==============================================================================
        # Tab 3: Trader (交易员深度分析)
        # ==============================================================================
        dbc.Tab(label='3. Trader Analysis', tab_id='tab-trader', children=[
            dbc.Card(dbc.CardBody([
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Date"),
                        dcc.Slider(id='trader-day-slider', min=1, max=100, value=1, step=1, marks=None,
                                   tooltip={"placement": "bottom", "always_visible": True}),
                    ], width=12)
                ], className="mb-3"),

                dbc.Row([
                    # 上半部分：交易员列表
                    dbc.Col([
                        html.H5("Trader List"),
                        dash_table.DataTable(
                            id='trader-list-table',
                            columns=[
                                {"name": "ID", "id": "trader_id"},
                                {"name": "Type", "id": "trader_type"},
                                {"name": "Status", "id": "is_active"},
                                {"name": "Total Assets", "id": "total_assets"},
                                {"name": "Savings", "id": "private_savings"},
                                {"name": "Cash", "id": "cash"},
                                {"name": "Stocks", "id": "stock_value"},
                            ],
                            style_table={'height': '250px', 'overflowY': 'auto'},
                            style_header=TABLE_STYLE_HEADER,
                            style_cell=TABLE_STYLE_CELL,
                            fixed_rows={'headers': True},
                            filter_action="native",
                            sort_action="native",
                            row_selectable="single",
                            selected_rows=[],
                            page_size=50
                        )
                    ], width=12)
                ]),

                html.Hr(),

                # 下半部分：详情
                dbc.Row([
                    dbc.Col([
                        dbc.Card([
                            dbc.CardHeader(html.H5(id='trader-detail-title', children="Trader Details", className="m-0")),
                            dbc.CardBody([
                                # 新增：Trader 核心指标卡片
                                html.Div(id='trader-metrics-row', className="mb-3"),

                                dbc.Row([
                                    # 左边图表：资产结构 + 风险偏好
                                    dbc.Col([
                                        html.H6("Asset Composition History"),
                                        dcc.Loading(dcc.Graph(id='trader-asset-structure-chart', style={'height': '300px'})),

                                        html.Hr(),
                                        html.H6("Risk Tolerance & Behavior"),
                                        dcc.Loading(dcc.Graph(id='trader-risk-chart', style={'height': '200px'}))
                                    ], width=8),

                                    # 右边：当前持仓
                                    dbc.Col([
                                        html.H6("Current Holdings"),
                                        dash_table.DataTable(
                                            id='trader-holdings-table',
                                            columns=[
                                                {"name": "Symbol", "id": "stock_id"},
                                                {"name": "Qty", "id": "quantity"},
                                                {"name": "Price", "id": "price"},
                                                {"name": "Value", "id": "market_value"}
                                            ],
                                            style_table={'height': '500px', 'overflowY': 'auto'},
                                            style_header=TABLE_STYLE_HEADER,
                                            style_cell={'fontSize': '12px', 'padding': '3px'},
                                            sort_action="native",
                                            page_size=20
                                        )
                                    ], width=4)
                                ])
                            ])
                        ], className="shadow-sm")
                    ], width=12)
                ])
            ]), className="mt-3 shadow-sm")
        ]),

        # ==============================================================================
        # Tab 4: Macro (宏观)
        # ==============================================================================
        dbc.Tab(label='4. Macro Stats', tab_id='tab-agent-type', children=[
            dbc.Card(dbc.CardBody([
                dbc.Row([
                    dbc.Col([
                        html.H4("Active Population"),
                        dcc.Loading(dcc.Graph(id='macro-population-chart'))
                    ], width=6),
                    dbc.Col([
                        html.H4("Macro Wealth Structure"),
                        dcc.Loading(dcc.Graph(id='macro-wealth-chart'))
                    ], width=6)
                ]),
                html.Hr(),
                dbc.Row([
                    dbc.Col([
                        html.H5("Total Assets by Agent Type"),
                        dcc.Loading(dcc.Graph(id='agent-type-asset-chart'))
                    ], width=6),
                    dbc.Col([
                        html.H5("Avg Risk Tolerance"),
                        dcc.Loading(dcc.Graph(id='agent-type-risk-chart'))
                    ], width=6)
                ])
            ]), className="mt-3 shadow-sm")
        ]),

        # ==============================================================================
        # Tab 5: Sector
        # ==============================================================================
        dbc.Tab(label='5. Sectors', tab_id='tab-sector', children=[
            dbc.Card(dbc.CardBody([
                dbc.Row([
                    dbc.Col([
                        html.H5("Total Market Cap by Sector"),
                        dcc.Loading(dcc.Graph(id='sector-mcap-chart'))
                    ], width=6),
                    dbc.Col([
                        html.H5("Average PE Ratio by Sector"),
                        dcc.Loading(dcc.Graph(id='sector-pe-chart'))
                    ], width=6)
                ])
            ]), className="mt-3 shadow-sm")
        ])
    ])
])

# --- 回调逻辑 ---

# 0. 基础：连接数据库与刷新
@app.callback(
    Output('db-dropdown', 'options'),
    Input('refresh-button', 'n_clicks'),
    State('directory-input', 'value')
)
def update_db_dropdown(n_clicks, directory):
    if not directory: return []
    try:
        db_files = glob.glob(os.path.join(directory, "*.db"))
        db_files.sort(key=os.path.getmtime, reverse=True)
        return [{'label': os.path.basename(f), 'value': f} for f in db_files]
    except: return []

@app.callback(
    [Output('current-db-path-store', 'data'),
     Output('connection-status', 'children'),
     Output('market-day-slider', 'max'), Output('stock-day-slider', 'max'), Output('trader-day-slider', 'max')],
    Input('connect-button', 'n_clicks'),
    State('db-dropdown', 'value'),
    prevent_initial_call=True
)
def connect_database(n_clicks, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)
    if not conn: return None, dbc.Badge("Failed", color="danger"), 1, 1, 1
    try:
        max_day = pd.read_sql_query("SELECT MAX(day) as md FROM market_log", conn).iloc[0]['md']
        max_day = int(max_day) if max_day else 1
        conn.close()
        return db_path, dbc.Badge("Connected", color="success"), max_day, max_day, max_day
    except Exception as e:
        return None, dbc.Badge(f"Error: {e}", color="danger"), 1, 1, 1

# --- 1. Market Tab ---
@app.callback(
    [Output('market-kline-chart', 'figure'),
     Output('market-details-output', 'children'),
     Output('top-active-stocks-table', 'data')],
    [Input('market-day-slider', 'value'),
     Input('current-db-path-store', 'data')]
)
def update_market_tab(day, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)

    # K-Line
    df_m = pd.read_sql_query("SELECT * FROM market_log", conn)
    fig = go.Figure(data=[go.Candlestick(x=df_m['day'], open=df_m['open'], high=df_m['high'], low=df_m['low'], close=df_m['close'])])
    fig.update_layout(title="Market Index", height=350, margin=dict(t=30, b=20))

    # Details Cards
    row = df_m[df_m['day'] == day]
    if row.empty:
        cards = "No Data"
        top_stocks = []
    else:
        s = row.iloc[0]
        pool = format_large_number(s.get('social_wealth_pool', 0))
        cards = dbc.Row([
            dbc.Col(dbc.Card([html.H4(format_num(s['close'])), html.Small("Close")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['volume'])), html.Small("Volume")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['turnover'])), html.Small("Turnover")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_percent(s['turnover_rate'])), html.Small("Turnover Rate")]), width=2),
            dbc.Col(dbc.Card([html.H4(pool, className="text-success"), html.Small("Social Wealth Pool")]), width=3),
        ], className="text-center")

        # Top 10 Active Stocks
        query_top = """
                    SELECT stock_id, sector, close, turnover, volume, turnover_rate
                    FROM stock_log WHERE day = ? ORDER BY turnover DESC LIMIT 10 \
                    """
        df_top = pd.read_sql_query(query_top, conn, params=(day,))
        df_top['rank'] = range(1, len(df_top) + 1)
        df_top['close'] = df_top['close'].apply(format_num)
        df_top['turnover'] = df_top['turnover'].apply(format_large_number)
        df_top['volume'] = df_top['volume'].apply(format_large_number)
        df_top['turnover_rate'] = df_top['turnover_rate'].apply(format_percent)
        df_top['action'] = "🔍 View"
        top_stocks = df_top.to_dict('records')

    conn.close()
    return fig, cards, top_stocks

# --- 2. Stock Tab ---
@app.callback(
    Output('stock-list-table', 'data'),
    [Input('stock-day-slider', 'value'), Input('current-db-path-store', 'data')]
)
def update_stock_list(day, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)
    df = pd.read_sql_query("SELECT stock_id, sector, close, pe_ttm, total_market_cap FROM stock_log WHERE day = ?", conn, params=(day,))
    conn.close()
    df['total_market_cap'] = df['total_market_cap'].apply(format_large_number)
    df['pe_ttm'] = df['pe_ttm'].apply(format_num)
    df['close'] = df['close'].apply(format_num)
    return df.to_dict('records')

# 处理股票跳转：Market Tab 点击 -> Stock Tab 选中
@app.callback(
    Output('global-selected-stock-store', 'data'),
    Input('top-active-stocks-table', 'active_cell'),
    State('top-active-stocks-table', 'data')
)
def jump_to_stock(active_cell, table_data):
    if active_cell and table_data:
        return {'stock_id': table_data[active_cell['row']]['stock_id'], 'from': 'market'}
    return None

@app.callback(
    Output('main-tabs', 'active_tab', allow_duplicate=True),
    Input('global-selected-stock-store', 'data'),
    State('main-tabs', 'active_tab'),
    prevent_initial_call=True
)
def switch_tab_stock(store, current):
    if store and store.get('from') == 'market': return 'tab-stock'
    return current

# 更新股票详情（包括K线和多维图表）
@app.callback(
    [Output('stock-kline-chart', 'figure'),
     Output('stock-valuation-chart', 'figure'),
     Output('stock-liquidity-chart', 'figure'),
     Output('stock-mcap-chart', 'figure'),
     Output('shareholder-table', 'data'),
     Output('stock-detail-title', 'children'),
     Output('stock-metrics-row', 'children'), # 新增 metrics output
     Output('stock-list-table', 'filter_query')],
    [Input('stock-list-table', 'selected_rows'),
     Input('stock-day-slider', 'value'),
     Input('global-selected-stock-store', 'data')],
    [State('stock-list-table', 'data'),
     State('current-db-path-store', 'data')]
)
def update_stock_detail(selected_rows, day, jump_store, table_data, db_path):
    if not db_path: raise PreventUpdate

    stock_id = None
    filter_query = ""

    # 优先响应跳转
    ctx_triggered = ctx.triggered_id
    if ctx_triggered == 'global-selected-stock-store' and jump_store:
        stock_id = jump_store['stock_id']
        filter_query = f"{{stock_id}} eq '{stock_id}'"
    elif selected_rows and table_data:
        if selected_rows[0] < len(table_data):
            stock_id = table_data[selected_rows[0]]['stock_id']

    if not stock_id: raise PreventUpdate

    conn = connect_db(db_path)

    # 获取该股票历史数据
    df_hist = pd.read_sql_query("SELECT * FROM stock_log WHERE stock_id = ? ORDER BY day", conn, params=(stock_id,))

    # Metrics Cards logic
    row_day = df_hist[df_hist['day'] == day]
    if row_day.empty:
        metrics_cards = html.Div("No Data for Selected Day")
    else:
        s = row_day.iloc[0]
        metrics_cards = dbc.Row([
            dbc.Col(dbc.Card([html.H4(format_num(s['close'])), html.Small("Price")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_num(s['pe_ttm'])), html.Small("PE (TTM)")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_num(s['pb_ratio'])), html.Small("PB Ratio")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['total_market_cap'])), html.Small("Market Cap")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['volume'])), html.Small("Volume")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_percent(s['turnover_rate'])), html.Small("Turnover Rate")]), width=2),
        ], className="text-center mb-3")

    # 1. Price K-Line
    fig_k = go.Figure(data=[go.Candlestick(x=df_hist['day'], open=df_hist['open'], high=df_hist['high'], low=df_hist['low'], close=df_hist['close'])])
    fig_k.update_layout(title=f"{stock_id} Price", height=350, margin=dict(t=30, b=20))

    # 2. Valuation (PE/PB)
    fig_val = make_subplots(specs=[[{"secondary_y": True}]])
    fig_val.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['pe_ttm'], name="PE (TTM)", line=dict(color='orange')), secondary_y=False)
    fig_val.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['pb_ratio'], name="PB Ratio", line=dict(color='green', dash='dot')), secondary_y=True)
    fig_val.update_yaxes(title_text="PE", secondary_y=False)
    fig_val.update_yaxes(title_text="PB", secondary_y=True)
    fig_val.update_layout(margin=dict(t=10, b=10, l=10, r=10))

    # 3. Liquidity
    fig_liq = make_subplots(specs=[[{"secondary_y": True}]])
    fig_liq.add_trace(go.Bar(x=df_hist['day'], y=df_hist['volume'], name="Volume", marker_color='rgba(100, 100, 255, 0.5)'), secondary_y=False)
    fig_liq.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['turnover_rate'], name="Turnover Rate", line=dict(color='red')), secondary_y=True)
    fig_liq.update_layout(margin=dict(t=10, b=10, l=10, r=10))

    # 4. Market Cap
    fig_cap = px.area(df_hist, x='day', y='total_market_cap', title=None)
    fig_cap.update_layout(margin=dict(t=10, b=10, l=10, r=10))

    # 5. Shareholders
    query_holders = """
                    SELECT h.trader_id, t.trader_type, h.quantity, (h.quantity * s.close) as value
                    FROM holdings_log h
                        JOIN stock_log s ON h.stock_id = s.stock_id AND h.day = s.day
                        LEFT JOIN trader_log t ON h.trader_id = t.trader_id AND t.day = h.day
                    WHERE h.stock_id = ? AND h.day = ? ORDER BY h.quantity DESC LIMIT 50 \
                    """
    df_h = pd.read_sql_query(query_holders, conn, params=(stock_id, day))
    conn.close()

    df_h['value'] = df_h['value'].apply(format_large_number)
    df_h['quantity'] = df_h['quantity'].apply(lambda x: f"{int(x):,}")
    df_h['action'] = "🔍 View"

    return fig_k, fig_val, fig_liq, fig_cap, df_h.to_dict('records'), f"Analysis: {stock_id}", metrics_cards, filter_query

# --- 3. Trader Tab ---

# 处理跳转：Stock Tab -> Trader Tab
@app.callback(
    Output('global-selected-trader-store', 'data'),
    Input('shareholder-table', 'active_cell'),
    State('shareholder-table', 'data')
)
def jump_to_trader(active_cell, table_data):
    if active_cell and table_data:
        return {'trader_id': table_data[active_cell['row']]['trader_id'], 'from': 'stock'}
    return None

@app.callback(
    Output('main-tabs', 'active_tab', allow_duplicate=True),
    Input('global-selected-trader-store', 'data'),
    State('main-tabs', 'active_tab'),
    prevent_initial_call=True
)
def switch_tab_trader(store, current):
    if store and store.get('from') == 'stock': return 'tab-trader'
    return current

@app.callback(
    [Output('trader-list-table', 'data'), Output('trader-list-table', 'filter_query')],
    [Input('trader-day-slider', 'value'), Input('global-selected-trader-store', 'data')],
    State('current-db-path-store', 'data')
)
def update_trader_list(day, jump_store, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)
    df = pd.read_sql_query("SELECT * FROM trader_log WHERE day = ?", conn, params=(day,))
    conn.close()

    df['is_active'] = df['is_active'].apply(lambda x: '✅' if x else '❌')
    for col in ['total_assets', 'private_savings', 'cash', 'stock_value']:
        df[col] = df[col].apply(format_large_number)

    filter_query = ""
    if jump_store and jump_store.get('from') == 'stock':
        tid = jump_store['trader_id']
        filter_query = f"{{trader_id}} eq {tid}"

    return df.to_dict('records'), filter_query

@app.callback(
    [Output('trader-asset-structure-chart', 'figure'),
     Output('trader-risk-chart', 'figure'),
     Output('trader-holdings-table', 'data'),
     Output('trader-detail-title', 'children'),
     Output('trader-metrics-row', 'children')], # 新增 metrics output
    [Input('trader-list-table', 'selected_rows'), Input('trader-day-slider', 'value')],
    [State('trader-list-table', 'data'), State('current-db-path-store', 'data')]
)
def update_trader_detail(selected_rows, day, table_data, db_path):
    if not db_path or not selected_rows or not table_data: raise PreventUpdate
    if selected_rows[0] >= len(table_data): raise PreventUpdate

    trader_id = table_data[selected_rows[0]]['trader_id']
    conn = connect_db(db_path)

    # 1. 历史数据
    df_hist = pd.read_sql_query(
        "SELECT day, total_assets, private_savings, cash, reserved_cash, stock_value, risk_tolerance FROM trader_log WHERE trader_id = ? ORDER BY day",
        conn, params=(trader_id,))

    # Metrics Cards Logic
    row_curr = df_hist[df_hist['day'] == day]
    if row_curr.empty:
        metrics_cards = html.Div("No Data")
    else:
        s = row_curr.iloc[0]
        # 计算当日盈亏 PnL
        pnl = 0
        pnl_color = "text-secondary"
        prev_row = df_hist[df_hist['day'] == day - 1]
        if not prev_row.empty:
            pnl = s['total_assets'] - prev_row.iloc[0]['total_assets']
            if pnl > 0: pnl_color = "text-success"
            elif pnl < 0: pnl_color = "text-danger"

        pnl_str = format_large_number(pnl)
        if pnl > 0: pnl_str = "+" + pnl_str

        metrics_cards = dbc.Row([
            dbc.Col(dbc.Card([html.H4(format_large_number(s['total_assets'])), html.Small("Total Assets")]), width=2),
            dbc.Col(dbc.Card([html.H4(pnl_str, className=pnl_color), html.Small("Daily PnL")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['private_savings'])), html.Small("Savings")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['cash'] + s['reserved_cash'])), html.Small("Cash")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_large_number(s['stock_value'])), html.Small("Stocks")]), width=2),
            dbc.Col(dbc.Card([html.H4(format_num(s['risk_tolerance'])), html.Small("Risk Tol.")]), width=2),
        ], className="text-center mb-3")

    # 图表1：详细资产堆叠图
    fig_asset = go.Figure()
    fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['private_savings'], name='Savings (Off)', stackgroup='one', fillcolor='rgba(46, 204, 113, 0.5)', line=dict(color='rgba(46, 204, 113, 1)')))
    fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['stock_value'], name='Stock Value', stackgroup='one', fillcolor='rgba(52, 152, 219, 0.5)', line=dict(color='rgba(52, 152, 219, 1)')))
    fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['cash'], name='Cash (Avail)', stackgroup='one', fillcolor='rgba(241, 196, 15, 0.5)', line=dict(color='rgba(241, 196, 15, 1)')))
    fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['reserved_cash'], name='Cash (Frozen)', stackgroup='one', fillcolor='rgba(230, 126, 34, 0.5)', line=dict(color='rgba(230, 126, 34, 1)')))
    fig_asset.update_layout(title="Deep Asset Composition", hovermode="x unified", margin=dict(t=30, b=20, l=10, r=10))

    # 图表2：风险容忍度变化
    fig_risk = px.line(df_hist, x='day', y='risk_tolerance', title=None, markers=False)
    fig_risk.update_layout(yaxis_title="Risk Tolerance", margin=dict(t=10, b=20, l=10, r=10))

    # 2. 持仓
    df_h = pd.read_sql_query("""
                             SELECT h.stock_id, h.quantity, s.close
                             FROM holdings_log h JOIN stock_log s ON h.stock_id=s.stock_id AND h.day=s.day
                             WHERE h.trader_id=? AND h.day=?""", conn, params=(trader_id, day))
    conn.close()

    df_h['market_value'] = (df_h['quantity'] * df_h['close']).apply(format_large_number)
    df_h['price'] = df_h['close'].apply(format_num)
    df_h['quantity'] = df_h['quantity'].apply(lambda x: f"{int(x):,}")

    return fig_asset, fig_risk, df_h.to_dict('records'), f"Trader {trader_id} Deep Dive", metrics_cards

# --- 4. Macro & Sector (复用之前的逻辑) ---
@app.callback(
    [Output('macro-population-chart', 'figure'), Output('macro-wealth-chart', 'figure'),
     Output('agent-type-asset-chart', 'figure'), Output('agent-type-risk-chart', 'figure')],
    Input('current-db-path-store', 'data')
)
def update_macro(db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)

    # Population
    df_pop = pd.read_sql_query("SELECT day, COUNT(*) as count FROM trader_log WHERE is_active=1 GROUP BY day", conn)
    fig_pop = px.line(df_pop, x='day', y='count', title="Active Agents")

    # Wealth Stack
    df_m = pd.read_sql_query("SELECT day, social_wealth_pool FROM market_log", conn)
    df_t = pd.read_sql_query("SELECT day, SUM(private_savings) as sav, SUM(total_assets) as tot FROM trader_log GROUP BY day", conn)
    df_all = pd.merge(df_m, df_t, on='day').fillna(0)
    df_all['Liquidity'] = df_all['tot'] - df_all['sav']
    fig_wealth = go.Figure()
    fig_wealth.add_trace(go.Scatter(x=df_all['day'], y=df_all['social_wealth_pool'], stackgroup='A', name='Social Pool', line=dict(color='gray')))
    fig_wealth.add_trace(go.Scatter(x=df_all['day'], y=df_all['sav'], stackgroup='A', name='Savings', line=dict(color='green')))
    fig_wealth.add_trace(go.Scatter(x=df_all['day'], y=df_all['Liquidity'], stackgroup='A', name='Market Liq', line=dict(color='blue')))
    fig_wealth.update_layout(title="Macro Wealth Flow")

    # Type Assets
    df_type = pd.read_sql_query("SELECT day, trader_type, SUM(total_assets) as v FROM trader_log GROUP BY day, trader_type", conn)
    fig_type = px.line(df_type, x='day', y='v', color='trader_type', title="Assets by Type")

    # Type Risk
    df_risk = pd.read_sql_query("SELECT day, trader_type, AVG(risk_tolerance) as v FROM trader_log WHERE is_active=1 GROUP BY day, trader_type", conn)
    fig_risk = px.line(df_risk, x='day', y='v', color='trader_type', title="Avg Risk Tolerance")

    conn.close()
    return fig_pop, fig_wealth, fig_type, fig_risk

@app.callback(
    [Output('sector-mcap-chart', 'figure'), Output('sector-pe-chart', 'figure')],
    Input('current-db-path-store', 'data')
)
def update_sector(db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)
    df = pd.read_sql_query("SELECT day, sector, SUM(total_market_cap) as cap, AVG(pe_ttm) as pe FROM stock_log GROUP BY day, sector", conn)
    conn.close()
    return px.line(df, x='day', y='cap', color='sector'), px.line(df, x='day', y='pe', color='sector')

if __name__ == '__main__':
    app.run(debug=True)