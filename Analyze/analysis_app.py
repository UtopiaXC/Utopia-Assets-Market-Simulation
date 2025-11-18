import dash
from dash import dcc, html, dash_table
from dash.dependencies import Input, Output, State
import plotly.graph_objects as go
import plotly.express as px
import pandas as pd
import sqlite3
import glob
import os
import sys

# 导入 Bootstrap 库
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate

# --- 辅助函数 ---
def connect_db(db_path):
    if not os.path.exists(db_path):
        print(f"Error: Database file not found: {db_path}")
        return None
    try:
        return sqlite3.connect(db_path, check_same_thread=False)
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return None

def load_data_from_db(db_path):
    """
    (V4.28: 加载 ipo_subscription_log)
    """
    conn = connect_db(db_path)
    if conn is None:
        return pd.DataFrame(), pd.DataFrame(), pd.DataFrame(), pd.DataFrame(), pd.DataFrame()

    try:
        print(f"Loading data from {db_path}...")
        df_market = pd.read_sql_query("SELECT * FROM market_log", conn)
        df_stock = pd.read_sql_query("SELECT * FROM stock_log", conn)
        df_trader = pd.read_sql_query("SELECT * FROM trader_log", conn)

        df_ipo = pd.DataFrame()
        df_ipo_subs = pd.DataFrame()
        try:
            df_ipo = pd.read_sql_query("SELECT * FROM ipo_log", conn)
            print("Loading ipo_log...")
        except pd.errors.DatabaseError:
            print("ipo_log not found, skipping.")
        try:
            df_ipo_subs = pd.read_sql_query("SELECT * FROM ipo_subscription_log", conn)
            print("Loading ipo_subscription_log...")
        except pd.errors.DatabaseError:
            print("ipo_subscription_log not found, skipping.")

        print("Data load complete.")
        return df_market, df_stock, df_trader, df_ipo, df_ipo_subs
    except Exception as e:
        print(f"Error loading data: {e}")
        return pd.DataFrame(), pd.DataFrame(), pd.DataFrame(), pd.DataFrame(), pd.DataFrame()
    finally:
        if conn:
            conn.close()

# (V4.16 格式化函数 - 保持不变)
def format_large_number(n):
    if n is None or pd.isna(n) or n == 0: return '-'
    if abs(n) > 1e12: return f"{n / 1e12:.2f} T"
    if abs(n) > 1e9: return f"{n / 1e9:.2f} B"
    if abs(n) > 1e6: return f"{n / 1e6:.2f} M"
    if abs(n) > 1e3: return f"{n / 1e3:.2f} K"
    return f"{n:.2f}"
def format_pe(n):
    if n is None or pd.isna(n) or n < 0: return '-'
    return f"{n:.2f}"
def format_percent(n):
    if n is None or pd.isna(n) or n == 0: return '-'
    return f"{n * 100:.2f}%"
def format_num(n, precision=2):
    if n is None or pd.isna(n): return '-'
    return f"{n:.{precision}f}"
def create_details_table(table_data):
    half_len = (len(table_data) + 1) // 2
    col1, col2 = table_data[:half_len], table_data[half_len:]
    def create_table_col(data_list):
        return [html.Tr([html.Th(item[0], style={'textAlign': 'left'}), html.Td(item[1], style={'textAlign': 'right'})]) for item in data_list]
    table_body_col1 = create_table_col(col1)
    table_body_col2 = create_table_col(col2)
    return dbc.Card(dbc.CardBody([
        dbc.Row([
            dbc.Col(dbc.Table(table_body_col1, bordered=False, striped=True, hover=True, size='sm'), md=6),
            dbc.Col(dbc.Table(table_body_col2, bordered=False, striped=True, hover=True, size='sm'), md=6),
        ])
    ]))

# --- 初始化 Dash App ---
app = dash.Dash(__name__, external_stylesheets=[dbc.themes.BOOTSTRAP])
app.title = "ABM Simulation Analysis (V4.28-EN)" # 版本更新

# --- 布局 (Layout) ---
app.layout = dbc.Container(fluid=True, className="p-4 bg-light", children=[
    dcc.Store(id='db-data-store'),
    dcc.Store(id='loaded-db-path-store'),
    html.H1(id='main-title', children="ABM Stock Market Simulation Analysis", className="mb-4"),

    # (V4.17.3 数据库加载器)
    dbc.Card(dbc.CardBody([
        html.H4("Database Loader", className="card-title"),
        dbc.Row([
            dbc.Col(dcc.Input(id='directory-input', value='output', type='text'), width=12, md=4),
            dbc.Col(dcc.Dropdown(id='db-dropdown', placeholder='Select a .db file...'), width=12, md=5),
            dbc.Col(
                dbc.ButtonGroup([
                    dbc.Button("Refresh", id='refresh-button', color='primary', outline=True),
                    dbc.Button("Load", id='load-button', color='primary'),
                ]), width=12, md=2
            ),
            dbc.Col(
                dcc.Loading(id="loading-load-button", type="circle",
                            children=[html.Div(id="load-button-output-dummy")]
                            ), width=1
            )
        ]),
    ]), className="mb-4"),

    # --- Tabs (V4.26 布局 - 保持不变) ---
    dbc.Tabs(id="main-tabs", active_tab="tab-market", children=[
        # (1. Market - 保持不变)
        dbc.Tab(label='1. Market', tab_id='tab-market', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Market Daily Details", className="mt-3"),
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Day:"),
                        dcc.Slider(id='market-day-slider', min=1, max=1, value=1, step=1, marks={}),
                        html.H5(id='market-day-display', className="mt-2 text-primary"),
                    ], width=12, md=8, className="mb-4")
                ]),
                dcc.Loading(id="loading-market-details", children=[html.Div(id='market-details-output')]),
                html.H3("Market Index Candlestick", className="mt-4"),
                dcc.Loading(id="loading-market-kline", children=[dcc.Graph(id='market-kline-chart')]),
            ]), className="mt-3")
        ]),
        # (2. Stock - 保持不变)
        dbc.Tab(label='2. Stock', tab_id='tab-stock', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Select Stock", className="mt-3"),
                dcc.Dropdown(id='stock-dropdown', options=[], value=None, className="mb-3"),
                html.H4("Stock Daily Details", className="mt-4"),
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Day:"),
                        dcc.Slider(id='stock-day-slider', min=1, max=1, value=1, step=1, marks={}),
                        html.H5(id='stock-day-display', className="mt-2 text-primary"),
                    ], width=12, md=8, className="mb-4")
                ]),
                dcc.Loading(id="loading-stock-tab", children=[
                    html.Div(id='stock-details-output'),
                    html.H4("Stock Candlestick Chart", className="mt-4"),
                    dcc.Graph(id='stock-kline-chart')
                ]),
            ]), className="mt-3")
        ]),
        # (3. Trader - 保持不变)
        dbc.Tab(label='3. Trader', tab_id='tab-trader', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Select Trader", className="mt-3"),
                dcc.Dropdown(id='trader-dropdown', options=[], value=None, className="mb-3"),
                dbc.RadioItems(id='trader-analysis-type',
                               options=[{'label': 'A. Performance Charts', 'value': 'charts'},
                                        {'label': 'B. Daily Holdings', 'value': 'holdings'}],
                               value='charts', inline=True, className="mb-3"),
                dcc.Loading(id="loading-trader-charts", children=[html.Div(id='trader-charts-output')]),
                dcc.Loading(id="loading-trader-holdings", children=[
                    html.Div(id='trader-holdings-output', children=[
                        html.H4("Portfolio Snapshot", className="mt-4"),
                        dbc.Row([
                            dbc.Col([
                                html.Label("Select Day:"),
                                dcc.Slider(id='holdings-day-slider', min=1, max=1, value=1, step=1, marks={}),
                                html.H5(id='holdings-day-display', className="mt-2 text-primary"),
                            ], width=12, md=8, className="mb-4")
                        ]),
                        html.Div(id='trader-holdings-details-output')
                    ])
                ])
            ]), className="mt-3")
        ]),
        # (4. Agent Type - 保持不变)
        dbc.Tab(label='4. Agent Type Analysis', tab_id='tab-agent-type', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Agent Type Daily Snapshot", className="mt-3"),
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Day:"),
                        dcc.Slider(id='agent-type-day-slider', min=1, max=1, value=1, step=1, marks={}),
                        html.H5(id='agent-type-day-display', className="mt-2 text-primary"),
                    ], width=12, md=8, className="mb-4")
                ]),
                dcc.Loading(id="loading-agent-type-details", children=[html.Div(id='agent-type-details-output')]),
                html.H3("Total Assets by Agent Type (Time Series)", className="mt-4"),
                dcc.Loading(id="loading-agent-type-chart1", children=[dcc.Graph(id='agent-type-comparison-chart')]),
                html.H3("Average Risk Tolerance by Agent Type", className="mt-4"),
                dcc.Loading(id="loading-agent-type-chart2", children=[dcc.Graph(id='agent-type-params-chart')]),
            ]), className="mt-3")
        ]),
        # (5. IPO - 保持不变)
        dbc.Tab(label='5. IPO Analysis', tab_id='tab-ipo', children=[
            dbc.Card(dbc.CardBody([
                html.H3("IPO Summary", className="mt-3"),
                dcc.Loading(id="loading-ipo-table", children=[
                    dash_table.DataTable(
                        id='ipo-data-table',
                        columns=[
                            {"name": "Stock ID", "id": "stock_id"},
                            {"name": "IPO Price", "id": "ipo_price"},
                            {"name": "Available Shares", "id": "available_shares"},
                            {"name": "Demand Shares", "id": "demand_shares"},
                            {"name": "Win Rate (Ratio %)", "id": "oversubscription_ratio"},
                        ],
                        data=[], sort_action="native", filter_action="native", page_size=10,
                        style_header={'fontWeight': 'bold'}, style_table={'overflowX': 'auto'},
                    )
                ]),
                html.H3("IPO Subscription Details (Lists)", className="mt-4"),
                dcc.Dropdown(id='ipo-stock-dropdown', options=[],
                             placeholder="Select a stock to see subscribers...", className="mb-3"),
                dcc.Loading(id="loading-ipo-subscribers-table", children=[
                    dash_table.DataTable(
                        id='ipo-subscribers-table',
                        columns=[
                            {"name": "Trader ID", "id": "trader_id"},
                            {"name": "Trader Type", "id": "trader_type"},
                            {"name": "Demand (Shares)", "id": "demand_shares"},
                            {"name": "Allocated (Won)", "id": "allocated_shares"},
                        ],
                        data=[], sort_action="native", filter_action="native", page_size=20,
                        style_header={'fontWeight': 'bold'}, style_table={'overflowX': 'auto'},
                    )
                ]),
            ]), className="mt-3")
        ]),
    ])
])

# --- 回调 (Callbacks) ---

# === 1. 数据库加载控件 ===
@app.callback(
    Output('db-dropdown', 'options'),
    Input('refresh-button', 'n_clicks'),
    State('directory-input', 'value')
)
def update_db_dropdown(n_clicks, directory):
    if not directory: return []
    try:
        db_files = glob.glob(os.path.join(directory, "*.db"))
        options = [{'label': os.path.basename(f), 'value': f} for f in db_files]
        options.sort(key=lambda x: os.path.getmtime(x['value']), reverse=True)
        return options
    except Exception as e:
        print(f"Error scanning directory: {e}")
        return []

@app.callback(
    [Output('db-data-store', 'data'),
     Output('loaded-db-path-store', 'data'),
     Output('load-button-output-dummy', 'children')],
    Input('load-button', 'n_clicks'),
    State('db-dropdown', 'value'),
    prevent_initial_call=True
)
def load_data_to_store(n_clicks, selected_db):
    if not selected_db:
        print("No database selected, cancelling load.")
        raise PreventUpdate

    # 【【修改 V4.28】】
    df_market, df_stock, df_trader, df_ipo, df_ipo_subs = load_data_from_db(selected_db)

    if df_market.empty or df_stock.empty or df_trader.empty:
        print("Data load failed, one or more DataFrames are empty.")
        return None, None, ""

    data_store = {
        'market': df_market.to_json(orient='split', date_format='iso'),
        'stock': df_stock.to_json(orient='split', date_format='iso'),
        'trader': df_trader.to_json(orient='split', date_format='iso'),
        'ipo': df_ipo.to_json(orient='split', date_format='iso'),
        'ipo_subs': df_ipo_subs.to_json(orient='split', date_format='iso'),
    }
    return data_store, selected_db, ""

# === 2. 更新 UI 元素 ===
@app.callback(
    Output('main-title', 'children'),
    Input('loaded-db-path-store', 'data')
)
def update_title(db_path):
    if db_path:
        return f"ABM Simulation Analysis ({os.path.basename(db_path)})"
    return "ABM Simulation Analysis (No data loaded)"

@app.callback(
    [# 下拉框
        Output('stock-dropdown', 'options'),
        Output('trader-dropdown', 'options'),
        # (Market Tab Slider)
        Output('market-day-slider', 'max'),
        Output('market-day-slider', 'marks'),
        Output('market-day-slider', 'value'),
        # (Stock Tab Slider)
        Output('stock-day-slider', 'max'),
        Output('stock-day-slider', 'marks'),
        Output('stock-day-slider', 'value'),
        # (Trader Tab Slider)
        Output('holdings-day-slider', 'max'),
        Output('holdings-day-slider', 'marks'),
        Output('holdings-day-slider', 'value'),
        # (Agent Type Tab Slider)
        Output('agent-type-day-slider', 'max'),
        Output('agent-type-day-slider', 'marks'),
        Output('agent-type-day-slider', 'value'),
        # 【【修改 V4.26】】 (IPO Tab)
        Output('ipo-data-table', 'data'),
        Output('ipo-stock-dropdown', 'options')
    ],
    Input('db-data-store', 'data')
)
def populate_dropdowns_and_sliders(data):
    # (V4.26 逻辑 - 保持不变)
    if data is None:
        return ([], [], 1, {}, 1, 1, {}, 1, 1, {}, 1, 1, {}, 1, [], [])
    df_market = pd.read_json(data['market'], orient='split')
    df_stock = pd.read_json(data['stock'], orient='split')
    df_trader = pd.read_json(data['trader'], orient='split')
    df_ipo = pd.DataFrame()
    if 'ipo' in data and data['ipo']:
        df_ipo = pd.read_json(data['ipo'], orient='split')
    stock_options = [{'label': stock, 'value': stock} for stock in df_stock['stock_id'].unique()]
    trader_info_df = df_trader[['trader_id', 'trader_type']].drop_duplicates().sort_values('trader_id')
    trader_options = [
        {'label': f"Trader {row.trader_id} ({row.trader_type})", 'value': row.trader_id}
        for row in trader_info_df.itertuples()
    ]
    max_day = 1
    if not df_market.empty:
        max_day = int(df_market['day'].max())
    slider_marks = {i: str(i) for i in range(1, max_day + 1) if i == 1 or i % 25 == 0 or i == max_day}
    ipo_records = []
    ipo_stock_options = []
    if not df_ipo.empty:
        df_ipo['available_shares'] = df_ipo['available_shares'].apply(format_large_number)
        df_ipo['demand_shares'] = df_ipo['demand_shares'].apply(format_large_number)
        df_ipo['oversubscription_ratio'] = df_ipo['oversubscription_ratio'].apply(lambda x: f"{x:.4f}%")
        df_ipo['ipo_price'] = df_ipo['ipo_price'].apply(format_num)
        ipo_records = df_ipo.to_dict('records')
        ipo_stock_options = [{'label': stock_id, 'value': stock_id} for stock_id in df_ipo['stock_id'].unique()]
    return (stock_options, trader_options,
            max_day, slider_marks, max_day,  # Market
            max_day, slider_marks, max_day,  # Stock
            max_day, slider_marks, max_day,  # Trader
            max_day, slider_marks, max_day,  # Agent Type
            ipo_records, ipo_stock_options # IPO
            )


# === 3. 市场 (Market) Tab 回调 (V4.17 - 保持不变) ===
@app.callback(
    Output('market-kline-chart', 'figure'),
    Input('db-data-store', 'data')
)
def update_market_kline(data):
    if data is None: return go.Figure().update_layout(title="Please load a database (No Data)")
    df_market = pd.read_json(data['market'], orient='split')
    fig = go.Figure(data=[go.Candlestick(
        x=df_market['day'], open=df_market['open'], high=df_market['high'],
        low=df_market['low'], close=df_market['close']
    )])
    fig.update_layout(title="Market Index Candlestick", xaxis_title="Day", yaxis_title="Index Points")
    return fig

@app.callback(
    [Output('market-details-output', 'children'),
     Output('market-day-display', 'children')],
    [Input('market-day-slider', 'value')],
    State('db-data-store', 'data'),
    prevent_initial_call=True
)
def update_market_details(selected_day, data):
    if data is None or selected_day is None: raise PreventUpdate
    df_market = pd.read_json(data['market'], orient='split')
    df_day = df_market[df_market['day'] == selected_day]
    if df_day.empty:
        return dbc.Alert("No data found for this day.", color="warning"), f"Selected Day: {selected_day}"
    s = df_day.iloc[0]
    df_market = df_market.set_index('day')
    vol_5d_avg = df_market['volume'].rolling(5).mean().shift(1)
    vol_ratio = s['volume'] / vol_5d_avg[s['day']] if vol_5d_avg.get(s['day'], 0) > 0 else 0
    table_data = [
        ("Date", f"Day {s['day']}"), ("Close", format_num(s['close'])),
        ("Open", format_num(s['open'])), ("High", format_num(s['high'])),
        ("Low", format_num(s['low'])), ("Volume", format_large_number(s['volume'])),
        ("Turnover", format_large_number(s['turnover'])),
        ("Total Market Cap", format_large_number(s['total_market_cap'])),
        ("Amplitude", format_percent(s['amplitude'])),
        ("Turnover Rate", format_percent(s['turnover_rate'])),
        ("Volume Ratio (5d)", format_num(vol_ratio)),
    ]
    details_layout = create_details_table(table_data)
    return details_layout, f"Selected Day: {selected_day}"

# === 4. 股票 (Stock) Tab 回调 (V4.17 - 保持不变) ===
@app.callback(
    [Output('stock-details-output', 'children'),
     Output('stock-kline-chart', 'figure'),
     Output('stock-day-display', 'children')],
    [Input('stock-dropdown', 'value'),
     Input('stock-day-slider', 'value')],
    State('db-data-store', 'data')
)
def update_stock_tab(selected_stock, selected_day, data):
    if not selected_stock or data is None or selected_day is None:
        fig = go.Figure().update_layout(title="Please select a stock")
        details = html.Div("Please select a stock and day.")
        day_display = ""
        return details, fig, day_display
    df_stock = pd.read_json(data['stock'], orient='split')
    df_selected = df_stock[df_stock['stock_id'] == selected_stock]
    fig = go.Figure(data=[go.Candlestick(
        x=df_selected['day'], open=df_selected['open'], high=df_selected['high'],
        low=df_selected['low'], close=df_selected['close']
    )])
    fig.update_layout(title=f"{selected_stock} Daily Candlestick", xaxis_title="Day", yaxis_title="Price (CNY)")
    df_day = df_selected[df_selected['day'] == selected_day]
    if df_day.empty:
        details = dbc.Alert("No data found for this day.", color="warning")
        return details, fig, f"Selected Day: {selected_day}"
    s = df_day.iloc[0]
    table_data = [
        ("Stock ID", s['stock_id']), ("Sector", s['sector']), ("Currency", "CNY"),
        ("Date", f"Day {s['day']}"), ("Close", format_num(s['close'])),
        ("Open", format_num(s['open'])), ("High", format_num(s['high'])),
        ("Low", format_num(s['low'])), ("Volume", format_large_number(s['volume'])),
        ("Turnover", format_large_number(s['turnover'])),
        ("Amplitude", format_percent(s['amplitude'])),
        ("Turnover Rate", format_percent(s['turnover_rate'])),
        ("PE (Dynamic)", format_pe(s['pe_dynamic'])),
        ("PE (TTM)", format_pe(s['pe_ttm'])),
        ("PE (Static)", format_pe(s['pe_static'])),
        ("PB Ratio", format_num(s['pb_ratio'])),
        ("EPS (TTM)", format_num(s['eps'])),
        ("Net Assets p.s.", format_num(s['net_assets'])),
        ("Total Market Cap", format_large_number(s['total_market_cap'])),
        ("Liquid Market Cap", format_large_number(s['liquid_market_cap'])),
        ("Total Shares", format_large_number(s['total_shares'])),
        ("Liquid Shares", format_large_number(s['liquid_shares'])),
        ("52-Week High", format_num(s['high_52w'])),
        ("52-Week Low", format_num(s['low_52w'])),
        ("Dividend", "-"), ("Dividend Yield", "-"), ("Goodwill / Net Assets", "-"),
    ]
    details_layout = create_details_table(table_data)
    return details_layout, fig, f"Selected Day: {selected_day}"

# === 5. 交易员 (Trader) Tab 回调 (V4.20.1 - 保持不变) ===
@app.callback(
    [Output('trader-charts-output', 'style'),
     Output('trader-holdings-output', 'style')],
    Input('trader-analysis-type', 'value')
)
def toggle_trader_analysis(analysis_type):
    if analysis_type == 'charts':
        return {'display': 'block'}, {'display': 'none'}
    else:
        return {'display': 'none'}, {'display': 'block'}

@app.callback(
    Output('trader-charts-output', 'children'),
    Input('trader-dropdown', 'value'),
    State('db-data-store', 'data')
)
def update_trader_charts_output(selected_trader, data):
    if selected_trader is None or data is None:
        return html.Div("Please select a trader.")
    df_trader = pd.read_json(data['trader'], orient='split')
    df_selected = df_trader[df_trader['trader_id'] == selected_trader]

    # 【【V4.28 修复】】 添加 'reserved_cash' 到图表
    fig_assets = px.line(df_selected, x='day', y=['total_assets', 'cash', 'reserved_cash', 'stock_value'],
                         title=f"Trader {selected_trader} - Asset Variation")
    fig_assets.update_layout(xaxis_title="Day", yaxis_title="Value (CNY)")
    fig_assets.data[0].name = "Total Assets"
    fig_assets.data[1].name = "Available Cash"
    fig_assets.data[2].name = "Reserved Cash (Pending)"
    fig_assets.data[3].name = "Stock Value"

    fig_traits = px.line(df_selected, x='day', y=['risk_tolerance'],
                         title=f"Trader {selected_trader} - Risk Tolerance Variation")
    fig_traits.update_layout(xaxis_title="Day", yaxis_title="Value")
    fig_traits.data[0].name = "Risk Tolerance"
    return [dcc.Graph(figure=fig_assets), dcc.Graph(figure=fig_traits)]

@app.callback(
    Output('trader-holdings-details-output', 'children'),
    [Input('trader-dropdown', 'value'),
     Input('holdings-day-slider', 'value')],
    State('loaded-db-path-store', 'data'),
    prevent_initial_call=True
)
def update_trader_holdings_output(selected_trader, selected_day, db_path):
    if selected_trader is None or selected_day is None or db_path is None: raise PreventUpdate
    conn = connect_db(db_path)
    if conn is None: return []
    try:
        # 【【V4.28 修复】】
        query = "SELECT cash, reserved_cash, stock_value, total_assets FROM trader_log WHERE trader_id = ? AND day = ?"
        df_trader_day = pd.read_sql_query(query, conn, params=(int(selected_trader), int(selected_day)))

        cash_value = 0.0
        reserved_cash_value = 0.0
        stock_value_db = 0.0
        total_assets = 0.0

        if not df_trader_day.empty:
            cash_value = df_trader_day.iloc[0]['cash']
            reserved_cash_value = df_trader_day.iloc[0]['reserved_cash']
            stock_value_db = df_trader_day.iloc[0]['stock_value']
            total_assets = df_trader_day.iloc[0]['total_assets']

        holdings_query = """
                         SELECT H.stock_id, H.quantity, S.close AS price_at_close
                         FROM holdings_log H
                                  JOIN stock_log S ON H.stock_id = S.stock_id AND H.day = S.day
                         WHERE H.trader_id = ? AND H.day = ?
                         """
        df_holdings = pd.read_sql_query(holdings_query, conn, params=(int(selected_trader), int(selected_day)))

        # (V4.28: 我们不再手动计算 stock_value, 我们直接使用 logger 的值)

        # 1. 现金
        table_rows_data = [
            {'holding': 'Available Cash', 'value': f"{cash_value:.2f} CNY", 'quantity': '-'},
            {'holding': 'Reserved Cash (Pending)', 'value': f"{reserved_cash_value:.2f} CNY", 'quantity': '-'}
        ]

        # 2. 股票
        for row in df_holdings.itertuples():
            stock_value = row.quantity * row.price_at_close
            table_rows_data.append({
                'holding': row.stock_id,
                'value': f"{stock_value:.2f} CNY",
                'quantity': f"{row.quantity:.0f} shares"
            })

        summary_alert = dbc.Alert(
            f"Total Assets: {total_assets:.2f} CNY (Cash: {cash_value:.2f} + Reserved: {reserved_cash_value:.2f} + Stocks: {stock_value_db:.2f})",
            color="primary", className="mt-4"
        )
        holdings_table = dash_table.DataTable(
            columns=[
                {"name": "Holding", "id": "holding"},
                {"name": "Value", "id": "value"},
                {"name": "Quantity", "id": "quantity"}
            ],
            data=table_rows_data,
            style_header={'fontWeight': 'bold'},
            style_cell={'textAlign': 'left'},
        )
        return [summary_alert, holdings_table]
    except Exception as e:
        print(f"Error querying holdings: {e}")
        return [dbc.Alert(f"Error: {e}", color="danger")]
    finally:
        if conn: conn.close()


# === 6. 代理类型 (Agent Type) Tab 回调 ===
@app.callback(
    Output('holdings-day-display', 'children'),
    Input('holdings-day-slider', 'value')
)
def update_holdings_day_display(selected_day):
    return f"Selected Day: {selected_day}"

# 【【修改 V4.28】】 重写此回调以显示详细资产
@app.callback(
    [Output('agent-type-details-output', 'children'),
     Output('agent-type-day-display', 'children')],
    [Input('agent-type-day-slider', 'value')],
    State('db-data-store', 'data'),
    prevent_initial_call=True
)
def update_agent_type_details(selected_day, data):
    if data is None or selected_day is None:
        raise PreventUpdate
    df_trader = pd.read_json(data['trader'], orient='split')
    df_day = df_trader[df_trader['day'] == selected_day]
    if df_day.empty:
        return dbc.Alert("No data found for this day.", color="warning"), f"Selected Day: {selected_day}"

    # --- V4.28 修正: 聚合所有需要的列 (Sum 和 Mean) ---
    df_grouped = df_day.groupby('trader_type').agg(
        count=('trader_id', 'count'),
        total_assets_sum=('total_assets', 'sum'),
        cash_sum=('cash', 'sum'),
        reserved_cash_sum=('reserved_cash', 'sum'), # 新增
        stock_value_sum=('stock_value', 'sum'),
        total_assets_mean=('total_assets', 'mean'),
        cash_mean=('cash', 'mean'),
        reserved_cash_mean=('reserved_cash', 'mean'), # 新增
        stock_value_mean=('stock_value', 'mean')
    ).reset_index()

    table_header = [html.Thead(html.Tr([
        html.Th("Agent Type"),
        html.Th("Count"),
        html.Th("Total Assets (Sum)"),
        html.Th("Cash (Sum)"),
        html.Th("Reserved (Sum)"), # 新增
        html.Th("Stock Value (Sum)"),
        html.Th("Avg Total Assets"),
        html.Th("Avg Cash"),
        html.Th("Avg Reserved"), # 新增
        html.Th("Avg Stock Value"),
    ]))]

    table_body = [html.Tbody([
        html.Tr([
            html.Td(row.trader_type),
            html.Td(f"{row.count}"),
            html.Td(format_large_number(row.total_assets_sum)),
            html.Td(format_large_number(row.cash_sum)),
            html.Td(format_large_number(row.reserved_cash_sum)), # 新增
            html.Td(format_large_number(row.stock_value_sum)),
            html.Td(format_large_number(row.total_assets_mean)),
            html.Td(format_large_number(row.cash_mean)),
            html.Td(format_large_number(row.reserved_cash_mean)), # 新增
            html.Td(format_large_number(row.stock_value_mean)),
        ]) for row in df_grouped.itertuples()
    ])]

    details_layout = dbc.Table(table_header + table_body,
                               bordered=True, striped=True, hover=True,
                               size='sm', responsive=True)

    return details_layout, f"Selected Day: {selected_day}"

@app.callback(
    Output('agent-type-comparison-chart', 'figure'),
    Input('db-data-store', 'data')
)
def update_agent_type_assets_chart(data):
    # (V4.17 逻辑 - 保持不变)
    if data is None: return go.Figure().update_layout(title="No Data")
    df_trader = pd.read_json(data['trader'], orient='split')
    df_grouped = df_trader.groupby(['day', 'trader_type'])['total_assets'].sum().reset_index()
    df_total = df_trader.groupby('day')['total_assets'].sum().reset_index()
    df_total['trader_type'] = 'All Agents'
    df_combined = pd.concat([df_grouped, df_total])
    fig = px.line(df_combined, x='day', y='total_assets', color='trader_type',
                  title="Total Assets by Agent Type")
    fig.update_layout(xaxis_title="Day", yaxis_title="Total Assets (CNY)")
    return fig

@app.callback(
    Output('agent-type-params-chart', 'figure'),
    Input('db-data-store', 'data')
)
def update_agent_type_params_chart(data):
    # (V4.20.1 逻辑 - 保持不变)
    if data is None: return go.Figure().update_layout(title="No Data")
    df_trader = pd.read_json(data['trader'], orient='split')
    df_filtered = df_trader[df_trader['trader_type'] != 'NOISE']
    df_grouped = df_filtered.groupby(['day', 'trader_type'])[['risk_tolerance']].mean().reset_index()
    fig = px.line(df_grouped, x='day', y='risk_tolerance', color='trader_type',
                  title="Average Risk Tolerance by Agent Type",
                  labels={"risk_tolerance": "Risk Tolerance", "trader_type": "Agent Type"})
    fig.update_layout(xaxis_title="Day")
    return fig

# === 7. 【【V4.26】】 IPO Tab 回调 ===
@app.callback(
    Output('ipo-subscribers-table', 'data'),
    Input('ipo-stock-dropdown', 'value'),
    State('db-data-store', 'data')
)
def update_ipo_subscribers_table(selected_stock, data):
    if not selected_stock or data is None or 'ipo_subs' not in data or not data['ipo_subs']:
        return []

    df_ipo_subs = pd.read_json(data['ipo_subs'], orient='split')
    df_trader_info = pd.read_json(data['trader'], orient='split')

    df_selected = df_ipo_subs[df_ipo_subs['stock_id'] == selected_stock]

    df_trader_info = df_trader_info[['trader_id', 'trader_type']].drop_duplicates()
    df_merged = pd.merge(df_selected, df_trader_info, on='trader_id')

    df_merged['demand_shares'] = df_merged['demand_shares'].apply(format_large_number)
    df_merged['allocated_shares'] = df_merged['allocated_shares'].apply(format_large_number)

    return df_merged.to_dict('records')

# --- 运行 App ---
if __name__ == '__main__':
    print("Dash app starting (V4.28). Open http://127.0.0.1:8050/ in your browser.")
    print("Please use the UI controls to load a database.")
    app.run(debug=True)