from dash import dcc, html, dash_table, ctx
from dash.dependencies import Input, Output, State
import plotly.graph_objects as go
import plotly.express as px
from plotly.subplots import make_subplots
import pandas as pd
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate
from utils import connect_db, format_num, format_large_number, format_percent, TABLE_STYLE_HEADER, TABLE_STYLE_CELL

# --- 模块配置 ---
order = 2
label = "2. Stock Analysis"
id = "tab-stock"

# --- 布局定义 ---
layout = dbc.Card(dbc.CardBody([
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
                    # 关键指标卡片
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
                    html.Small("Click Trader ID to analyze agent behavior"),
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

# --- 回调注册 ---
def register_callbacks(app):

    # 1. 更新股票列表
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

    # 2. 处理跳转逻辑：从 Market Tab 的 Top10 表格跳转过来
    # (虽然 Input 是 Market Tab 的组件，但逻辑放在这里处理目标状态是合法的)
    @app.callback(
        Output('global-selected-stock-store', 'data'),
        Input('top-active-stocks-table', 'active_cell'), # 来自 Market Tab
        State('top-active-stocks-table', 'data')
    )
    def jump_to_stock(active_cell, table_data):
        if active_cell and table_data:
            return {'stock_id': table_data[active_cell['row']]['stock_id'], 'from': 'market'}
        return None

    # 3. 响应跳转：切换 Tab
    @app.callback(
        Output('main-tabs', 'active_tab', allow_duplicate=True),
        Input('global-selected-stock-store', 'data'),
        State('main-tabs', 'active_tab'),
        prevent_initial_call=True
    )
    def switch_tab_stock(store, current):
        if store and store.get('from') == 'market': return 'tab-stock'
        return current

    # 4. 更新股票详情 (核心逻辑)
    @app.callback(
        [Output('stock-kline-chart', 'figure'),
         Output('stock-valuation-chart', 'figure'),
         Output('stock-liquidity-chart', 'figure'),
         Output('stock-mcap-chart', 'figure'),
         Output('shareholder-table', 'data'),
         Output('stock-detail-title', 'children'),
         Output('stock-metrics-row', 'children'),
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

        # 获取历史数据
        df_hist = pd.read_sql_query("SELECT * FROM stock_log WHERE stock_id = ? ORDER BY day", conn, params=(stock_id,))

        # 指标卡片
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

        # 图表绘制
        fig_k = go.Figure(data=[go.Candlestick(x=df_hist['day'], open=df_hist['open'], high=df_hist['high'], low=df_hist['low'], close=df_hist['close'])])
        fig_k.update_layout(title=f"{stock_id} Price", height=350, margin=dict(t=30, b=20))

        fig_val = make_subplots(specs=[[{"secondary_y": True}]])
        fig_val.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['pe_ttm'], name="PE (TTM)", line=dict(color='orange')), secondary_y=False)
        fig_val.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['pb_ratio'], name="PB Ratio", line=dict(color='green', dash='dot')), secondary_y=True)
        fig_val.update_layout(margin=dict(t=10, b=10, l=10, r=10))

        fig_liq = make_subplots(specs=[[{"secondary_y": True}]])
        fig_liq.add_trace(go.Bar(x=df_hist['day'], y=df_hist['volume'], name="Volume", marker_color='rgba(100, 100, 255, 0.5)'), secondary_y=False)
        fig_liq.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['turnover_rate'], name="Turnover Rate", line=dict(color='red')), secondary_y=True)
        fig_liq.update_layout(margin=dict(t=10, b=10, l=10, r=10))

        fig_cap = px.area(df_hist, x='day', y='total_market_cap', title=None)
        fig_cap.update_layout(margin=dict(t=10, b=10, l=10, r=10))

        # 股东查询
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