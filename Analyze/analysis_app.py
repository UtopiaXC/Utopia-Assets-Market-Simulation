import dash
from dash import dcc, html, dash_table
from dash.dependencies import Input, Output, State
import plotly.graph_objects as go
import plotly.express as px
import pandas as pd
import sqlite3
import glob
import os
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate

# --- 全局配置 ---
# 使用 "按需查询 (Query on Demand)" 模式，避免一次性加载大数据。

# --- 辅助函数 ---
def connect_db(db_path):
    if not db_path or not os.path.exists(db_path):
        return None
    try:
        # check_same_thread=False 允许在 Dash 的多线程回调中使用
        return sqlite3.connect(db_path, check_same_thread=False)
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return None

def format_large_number(n):
    if n is None or pd.isna(n) or n == 0: return '-'
    if abs(n) > 1e12: return f"{n / 1e12:.2f} T"
    if abs(n) > 1e9: return f"{n / 1e9:.2f} B"
    if abs(n) > 1e6: return f"{n / 1e6:.2f} M"
    if abs(n) > 1e3: return f"{n / 1e3:.2f} K"
    return f"{n:.2f}"

def format_percent(n):
    if n is None or pd.isna(n): return '-'
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
app.title = "ABM Analysis (Enhanced)"

# --- 布局 (Layout) ---
app.layout = dbc.Container(fluid=True, className="p-4 bg-light", children=[
    dcc.Store(id='current-db-path-store'),

    html.H1("ABM Simulation Analysis", className="mb-4"),

    # 1. 数据库选择器
    dbc.Card(dbc.CardBody([
        html.H4("Database Loader", className="card-title"),
        dbc.Row([
            dbc.Col(dcc.Input(id='directory-input', value='output', type='text'), width=12, md=4),
            dbc.Col(dcc.Dropdown(id='db-dropdown', placeholder='Select a .db file...'), width=12, md=5),
            dbc.Col(
                dbc.ButtonGroup([
                    dbc.Button("Refresh", id='refresh-button', color='primary', outline=True),
                    dbc.Button("Connect", id='connect-button', color='primary'),
                ]), width=12, md=2
            ),
            dbc.Col(html.Div(id="connection-status", className="mt-2"), width=12, md=1)
        ]),
    ]), className="mb-4"),

    # 2. 主面板
    dbc.Tabs(id="main-tabs", active_tab="tab-market", children=[

        # --- Tab 1: Market ---
        dbc.Tab(label='1. Market', tab_id='tab-market', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Market Overview"),
                dcc.Loading(dcc.Graph(id='market-kline-chart')),
                html.Hr(),
                html.H4("Daily Details"),
                dbc.Row([
                    dbc.Col([
                        html.Label("Select Day:"),
                        dcc.Slider(id='market-day-slider', min=1, max=100, value=1, step=1, marks={}),
                        html.Div(id='market-day-display')
                    ], width=8)
                ]),
                html.Div(id='market-details-output', className="mt-3")
            ]), className="mt-3")
        ]),

        # --- Tab 2: Stock ---
        dbc.Tab(label='2. Stock', tab_id='tab-stock', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Stock Analysis"),
                dbc.Row([
                    dbc.Col(dcc.Dropdown(id='stock-dropdown', placeholder="Select Stock..."), width=4),
                    dbc.Col(html.Div(id='stock-meta-info'), width=8)
                ]),
                dcc.Loading(dcc.Graph(id='stock-kline-chart')),
                html.Hr(),
                html.H4("Daily Details"),
                dcc.Slider(id='stock-day-slider', min=1, max=100, value=1, step=1, marks={}),
                html.Div(id='stock-details-output', className="mt-3")
            ]), className="mt-3")
        ]),

        # --- Tab 3: Trader ---
        dbc.Tab(label='3. Trader', tab_id='tab-trader', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Trader Analysis"),
                dbc.Row([
                    dbc.Col(dcc.Dropdown(id='trader-dropdown', placeholder="Select Trader ID..."), width=4),
                ]),
                html.Hr(),
                html.H4("Performance"),
                dcc.Loading(dcc.Graph(id='trader-asset-chart')),
                html.Hr(),
                html.H4("Holdings Snapshot"),
                dcc.Slider(id='trader-day-slider', min=1, max=100, value=1, step=1, marks={}),
                html.Div(id='trader-day-display'),
                html.Div(id='trader-holdings-output', className="mt-3")
            ]), className="mt-3")
        ]),

        # --- Tab 4: Agent Types ---
        dbc.Tab(label='4. Agent Stats', tab_id='tab-agent-type', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Total Assets by Agent Type"),
                # 【修改点3】图表：添加 TOTAL 线
                dcc.Loading(dcc.Graph(id='agent-type-asset-chart')),

                html.Hr(),
                html.H3("Average Risk Tolerance Evolution"),
                # 【修改点4】新图表：风险承受能力变化
                dcc.Loading(dcc.Graph(id='agent-type-risk-chart')),

                html.Hr(),
                html.H4("Daily Statistics Table"),
                dcc.Slider(id='agent-type-day-slider', min=1, max=100, value=1, step=1, marks={}),
                html.Div(id='agent-type-details-output', className="mt-3")
            ]), className="mt-3")
        ]),

        # --- Tab 5: Sector Analysis (New) ---
        # 【修改点5】新增页面
        dbc.Tab(label='5. Sector Analysis', tab_id='tab-sector', children=[
            dbc.Card(dbc.CardBody([
                html.H3("Sector Performance Comparison"),

                dbc.Row([
                    dbc.Col([
                        html.H5("Total Market Cap by Sector"),
                        dcc.Loading(dcc.Graph(id='sector-mcap-chart'))
                    ], width=12, lg=6),
                    dbc.Col([
                        html.H5("Average PE Ratio by Sector"),
                        dcc.Loading(dcc.Graph(id='sector-pe-chart'))
                    ], width=12, lg=6)
                ]),

                html.Hr(),
                html.H4("Daily Sector Statistics"),
                dcc.Slider(id='sector-day-slider', min=1, max=100, value=1, step=1, marks={}),
                html.Div(id='sector-details-output', className="mt-3")
            ]), className="mt-3")
        ])
    ])
])

# --- 回调 (Callbacks) ---

# 1. 刷新文件列表
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
        options = [{'label': os.path.basename(f), 'value': f} for f in db_files]
        return options
    except Exception as e:
        print(e)
        return []

# 2. 连接数据库
@app.callback(
    [Output('current-db-path-store', 'data'),
     Output('connection-status', 'children'),
     Output('market-day-slider', 'max'), Output('market-day-slider', 'marks'),
     Output('stock-day-slider', 'max'), Output('stock-day-slider', 'marks'),
     Output('trader-day-slider', 'max'), Output('trader-day-slider', 'marks'),
     Output('agent-type-day-slider', 'max'), Output('agent-type-day-slider', 'marks'),
     Output('sector-day-slider', 'max'), Output('sector-day-slider', 'marks'),
     Output('stock-dropdown', 'options'),
     Output('trader-dropdown', 'options')],
    Input('connect-button', 'n_clicks'),
    State('db-dropdown', 'value'),
    prevent_initial_call=True
)
def connect_database(n_clicks, db_path):
    if not db_path: raise PreventUpdate

    conn = connect_db(db_path)
    if not conn:
        return None, dbc.Badge("Failed", color="danger"), 1, {}, 1, {}, 1, {}, 1, {}, 1, {}, [], []

    try:
        max_day_df = pd.read_sql_query("SELECT MAX(day) as md FROM market_log", conn)
        max_day = int(max_day_df.iloc[0]['md']) if not max_day_df.empty and max_day_df.iloc[0]['md'] else 1

        stocks_df = pd.read_sql_query("SELECT DISTINCT stock_id FROM stock_log ORDER BY stock_id", conn)
        stock_opts = [{'label': s, 'value': s} for s in stocks_df['stock_id']]

        traders_df = pd.read_sql_query("SELECT DISTINCT trader_id, trader_type FROM trader_log", conn)
        trader_opts = [{'label': f"{row.trader_id} ({row.trader_type})", 'value': row.trader_id} for row in traders_df.itertuples()]

        marks = {i: str(i) for i in range(1, max_day + 1, max(1, max_day // 10))}
        marks[max_day] = str(max_day)

        conn.close()

        # 返回 max_day 给 5 个 slider (Market, Stock, Trader, Agent, Sector)
        return (db_path, dbc.Badge("Connected", color="success"),
                max_day, marks, max_day, marks, max_day, marks, max_day, marks, max_day, marks,
                stock_opts, trader_opts)
    except Exception as e:
        print(f"Init Error: {e}")
        return None, dbc.Badge("Error", color="danger"), 1, {}, 1, {}, 1, {}, 1, {}, 1, {}, [], []

# --- Market Tab 回调 ---
@app.callback(
    [Output('market-kline-chart', 'figure'),
     Output('market-details-output', 'children')],
    [Input('market-day-slider', 'value'),
     Input('current-db-path-store', 'data')]
)
def update_market_tab(day, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)

    df_market = pd.read_sql_query("SELECT * FROM market_log", conn)
    fig = go.Figure(data=[go.Candlestick(
        x=df_market['day'], open=df_market['open'], high=df_market['high'],
        low=df_market['low'], close=df_market['close']
    )])
    fig.update_layout(title="Market Index", xaxis_title="Day", yaxis_title="Index", height=400)

    row = df_market[df_market['day'] == day]
    if row.empty:
        details = "No data"
    else:
        s = row.iloc[0]
        data = [
            ("Day", s['day']), ("Index", format_num(s['close'])),
            ("Volume", format_large_number(s['volume'])), ("Turnover", format_large_number(s['turnover'])),
            ("Amplitude", format_percent(s['amplitude'])), ("Turnover Rate", format_percent(s['turnover_rate']))
        ]
        details = create_details_table(data)

    conn.close()
    return fig, details

# --- Stock Tab 回调 ---
@app.callback(
    [Output('stock-kline-chart', 'figure'),
     Output('stock-details-output', 'children')],
    [Input('stock-dropdown', 'value'),
     Input('stock-day-slider', 'value')],
    State('current-db-path-store', 'data')
)
def update_stock_tab(stock_id, day, db_path):
    if not db_path or not stock_id: raise PreventUpdate
    conn = connect_db(db_path)

    df_stock = pd.read_sql_query("SELECT * FROM stock_log WHERE stock_id = ?", conn, params=(stock_id,))

    fig = go.Figure(data=[go.Candlestick(
        x=df_stock['day'], open=df_stock['open'], high=df_stock['high'],
        low=df_stock['low'], close=df_stock['close']
    )])
    fig.update_layout(title=f"{stock_id} Price", height=400)

    row = df_stock[df_stock['day'] == day]
    if row.empty:
        details = "No data"
    else:
        s = row.iloc[0]
        # 【修改点1】添加 Sector 信息
        data = [
            ("Sector", s['sector']),  # 新增
            ("Close", format_num(s['close'])),
            ("PE (TTM)", format_num(s['pe_ttm'])),
            ("PB", format_num(s['pb_ratio'])),
            ("Volume", format_large_number(s['volume'])),
            ("Market Cap", format_large_number(s['total_market_cap'])),
            ("Turnover Rate", format_percent(s['turnover_rate']))
        ]
        details = create_details_table(data)

    conn.close()
    return fig, details

# --- Trader Tab 回调 ---
@app.callback(
    [Output('trader-asset-chart', 'figure'),
     Output('trader-holdings-output', 'children'),
     Output('trader-day-display', 'children')],
    [Input('trader-dropdown', 'value'),
     Input('trader-day-slider', 'value')],
    State('current-db-path-store', 'data')
)
def update_trader_tab(trader_id, day, db_path):
    if not db_path or not trader_id: raise PreventUpdate
    conn = connect_db(db_path)

    df_assets = pd.read_sql_query(
        "SELECT day, total_assets, cash, reserved_cash, stock_value FROM trader_log WHERE trader_id = ?",
        conn, params=(trader_id,))

    fig = go.Figure()
    fig.add_trace(go.Scatter(x=df_assets['day'], y=df_assets['total_assets'], name='Total Assets'))
    # 【修改点2】移除了 visible='legendonly'，默认显示全部
    fig.add_trace(go.Scatter(x=df_assets['day'], y=df_assets['cash'], name='Cash'))
    fig.add_trace(go.Scatter(x=df_assets['day'], y=df_assets['stock_value'], name='Stocks'))
    fig.update_layout(title=f"Trader {trader_id} Assets", height=400)

    query_holdings = """
                     SELECT h.stock_id, h.quantity, s.close
                     FROM holdings_log h
                              JOIN stock_log s ON h.stock_id = s.stock_id AND h.day = s.day
                     WHERE h.trader_id = ? AND h.day = ? \
                     """
    df_holdings = pd.read_sql_query(query_holdings, conn, params=(trader_id, day))

    row_t = df_assets[df_assets['day'] == day]
    cash = row_t.iloc[0]['cash'] if not row_t.empty else 0
    reserved = row_t.iloc[0]['reserved_cash'] if not row_t.empty else 0

    items = [
        {"Asset": "Cash (Available)", "Qty": "-", "Value": format_num(cash)},
        {"Asset": "Cash (Reserved)", "Qty": "-", "Value": format_num(reserved)}
    ]
    for r in df_holdings.itertuples():
        val = r.quantity * r.close
        items.append({"Asset": r.stock_id, "Qty": int(r.quantity), "Value": format_num(val)})

    tbl = dash_table.DataTable(
        columns=[{"name": i, "id": i} for i in ["Asset", "Qty", "Value"]],
        data=items,
        style_cell={'textAlign': 'left'},
        page_size=10
    )

    conn.close()
    return fig, tbl, f"Day: {day}"

# --- Agent Type Tab 回调 ---
@app.callback(
    [Output('agent-type-asset-chart', 'figure'),
     Output('agent-type-risk-chart', 'figure'),
     Output('agent-type-details-output', 'children')],
    [Input('agent-type-day-slider', 'value'),
     Input('current-db-path-store', 'data')]
)
def update_agent_type_tab(day, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)

    # 1. 资产图表
    query_chart = """
                  SELECT day, trader_type, SUM(total_assets) as total_assets
                  FROM trader_log
                  GROUP BY day, trader_type \
                  """
    df_chart = pd.read_sql_query(query_chart, conn)

    # 【修改点3】计算 TOTAL 并添加为一条线
    df_total = df_chart.groupby('day')['total_assets'].sum().reset_index()
    df_total['trader_type'] = 'TOTAL'

    # 合并数据
    df_combined = pd.concat([df_chart, df_total])

    fig_assets = px.line(df_combined, x='day', y='total_assets', color='trader_type',
                         title="Total Assets by Agent Type (with TOTAL)",
                         color_discrete_map={'TOTAL': 'black'}) # 设置 TOTAL 为黑色

    # 2. 【修改点4】风险承受能力图表
    query_risk = """
                 SELECT day, trader_type, AVG(risk_tolerance) as avg_risk
                 FROM trader_log
                 GROUP BY day, trader_type \
                 """
    df_risk = pd.read_sql_query(query_risk, conn)
    fig_risk = px.line(df_risk, x='day', y='avg_risk', color='trader_type',
                       title="Average Risk Tolerance Evolution")

    # 3. 每日详情表
    query_day = """
                SELECT trader_type, COUNT(*) as cnt,
                       SUM(total_assets) as sum_assets, AVG(total_assets) as avg_assets,
                       AVG(risk_tolerance) as avg_risk_tol,
                       SUM(cash) as sum_cash, SUM(stock_value) as sum_stock
                FROM trader_log
                WHERE day = ?
                GROUP BY trader_type \
                """
    df_day = pd.read_sql_query(query_day, conn, params=(day,))

    df_day['sum_assets'] = df_day['sum_assets'].apply(format_large_number)
    df_day['avg_assets'] = df_day['avg_assets'].apply(format_large_number)
    df_day['avg_risk_tol'] = df_day['avg_risk_tol'].apply(format_num)
    df_day['sum_cash'] = df_day['sum_cash'].apply(format_large_number)
    df_day['sum_stock'] = df_day['sum_stock'].apply(format_large_number)

    tbl = dbc.Table.from_dataframe(df_day, striped=True, bordered=True, hover=True)

    conn.close()
    return fig_assets, fig_risk, tbl

# --- 【修改点5】Sector Tab 回调 ---
@app.callback(
    [Output('sector-mcap-chart', 'figure'),
     Output('sector-pe-chart', 'figure'),
     Output('sector-details-output', 'children')],
    [Input('sector-day-slider', 'value'),
     Input('current-db-path-store', 'data')]
)
def update_sector_tab(day, db_path):
    if not db_path: raise PreventUpdate
    conn = connect_db(db_path)

    # 1. 聚合查询：按板块和日期汇总
    query_sector = """
                   SELECT day, sector,
                       SUM(total_market_cap) as total_cap,
                       AVG(pe_ttm) as avg_pe,
                       SUM(volume) as total_volume
                   FROM stock_log
                   GROUP BY day, sector \
                   """
    df_sector = pd.read_sql_query(query_sector, conn)

    # 图表 1: 板块市值走势
    fig_cap = px.line(df_sector, x='day', y='total_cap', color='sector',
                      title="Total Market Cap by Sector")

    # 图表 2: 板块平均 PE 走势
    fig_pe = px.line(df_sector, x='day', y='avg_pe', color='sector',
                     title="Average PE (TTM) by Sector")

    # 2. 每日详情表
    df_day = df_sector[df_sector['day'] == day].copy()

    # 格式化
    df_day['total_cap'] = df_day['total_cap'].apply(format_large_number)
    df_day['avg_pe'] = df_day['avg_pe'].apply(format_num)
    df_day['total_volume'] = df_day['total_volume'].apply(format_large_number)

    # 只显示相关列
    cols_to_show = ['sector', 'total_cap', 'avg_pe', 'total_volume']
    df_display = df_day[cols_to_show].sort_values('total_cap', ascending=False)

    tbl = dbc.Table.from_dataframe(df_display, striped=True, bordered=True, hover=True)

    conn.close()
    return fig_cap, fig_pe, tbl

if __name__ == '__main__':
    app.run(debug=True)