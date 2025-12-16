from dash import dcc, html, dash_table
from dash.dependencies import Input, Output, State
import plotly.graph_objects as go
import pandas as pd
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate
from utils import connect_db, format_num, format_large_number, format_percent, TABLE_STYLE_HEADER, TABLE_STYLE_CELL

# --- 模块配置 ---
order = 1          # Tab 顺序
label = "1. Market Overview"  # Tab 显示名称
id = "tab-market"  # Tab ID

# --- 布局定义 ---
layout = dbc.Card(dbc.CardBody([
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
            {"name": "Stock ID", "id": "stock_id"},
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

# --- 回调注册 ---
def register_callbacks(app):
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