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

# --- 全局变量 ---

def select_database():
    """
    在运行时指定数据库。
    """
    if len(sys.argv) > 1:
        db_name = sys.argv[1]
        if not os.path.exists(db_name):
            print(f"错误：指定的数据库文件 '{db_name}' 不存在。")
            exit()
        print(f"--- 正在加载指定的数据库: {db_name} ---")
        return db_name
    else:
        print("未指定数据库，正在自动查找最新文件...")
        db_files = glob.glob("simulation_data_seed_*.db")
        if not db_files:
            print("错误：在当前目录中未找到 'simulation_data_seed_*.db' 文件。")
            print("请先运行 Java 模拟器。")
            exit()

        db_name = max(db_files, key=os.path.getmtime)
        print(f"--- 自动加载最新数据库: {db_name} ---")
        return db_name

DB_NAME = select_database()

# --- 辅助函数 ---
def connect_db():
    """连接到 SQLite 数据库"""
    return sqlite3.connect(DB_NAME, check_same_thread=False)

def load_data_from_db():
    """从数据库加载数据到 Pandas DataFrames"""
    conn = connect_db()
    try:
        print("正在加载 market_log...")
        df_market = pd.read_sql_query("SELECT * FROM market_log", conn)

        print("正在加载 stock_log...")
        df_stock = pd.read_sql_query("SELECT * FROM stock_log", conn)

        print("正在加载 trader_log...")
        df_trader = pd.read_sql_query("SELECT * FROM trader_log", conn)

        print("数据加载完成。")
        return df_market, df_stock, df_trader
    except Exception as e:
        print(f"加载数据时出错: {e}")
        return pd.DataFrame(), pd.DataFrame(), pd.DataFrame()
    finally:
        conn.close()

# --- 加载数据 ---
df_market, df_stock, df_trader = load_data_from_db()

stock_list = df_stock['stock_id'].unique() if not df_stock.empty else []
trader_list = df_trader['trader_id'].unique() if not df_trader.empty else []
day_list = df_market['day'].unique() if not df_market.empty else []
max_day = 1
if day_list.size > 0:
    max_day = int(day_list.max())


# --- 初始化 Dash App ---
app = dash.Dash(__name__)
app.title = "ABM 模拟分析 (中文版)"

# --- 布局 (Layout) ---
app.layout = html.Div(style={'fontFamily': 'sans-serif'}, children=[
    html.H1(f"ABM 股票市场模拟分析 ({DB_NAME})"),

    dcc.Tabs(id="main-tabs", value='tab-market', children=[

        # --- 市场标签 ---
        dcc.Tab(label='1. 市场 (Market)', value='tab-market', children=[
            html.H3("市场指数 K 线图"),
            dcc.Graph(id='market-kline-chart'),
            html.H3("市场详细数据 (Market Data)"),
            dash_table.DataTable(
                id='market-data-table',
                columns=[
                    {"name": "天 (Day)", "id": "day"},
                    {"name": "开盘 (Open)", "id": "open"},
                    {"name": "最高 (High)", "id": "high"},
                    {"name": "最低 (Low)", "id": "low"},
                    {"name": "收盘 (Close)", "id": "close"},
                    {"name": "成交量 (Volume)", "id": "volume"},
                    {"name": "成交额 (Turnover)", "id": "turnover"},
                ],
                data=df_market.to_dict('records'),
                sort_action="native",
                filter_action="native",
                page_size=10,
                style_header={'fontWeight': 'bold'},
            )
        ]),

        # --- 股票标签 ---
        dcc.Tab(label='2. 股票 (Stock)', value='tab-stock', children=[
            html.H3("选择股票 (Select Stock)"),
            dcc.Dropdown(
                id='stock-dropdown',
                options=[{'label': stock, 'value': stock} for stock in stock_list],
                value=stock_list[0] if stock_list.size > 0 else None
            ),
            dcc.Graph(id='stock-kline-chart')
        ]),

        # --- 交易员标签 ---
        dcc.Tab(label='3. 交易员 (Trader)', value='tab-trader', children=[
            html.H3("选择交易员 (Select Trader)"),
            dcc.Dropdown(
                id='trader-dropdown',
                options=[{'label': f"交易员 {trader}", 'value': trader} for trader in trader_list],
                value=trader_list[0] if trader_list.size > 0 else None
            ),
            dcc.RadioItems(
                id='trader-analysis-type',
                options=[
                    {'label': 'A. 表现图表 (Performance Charts)', 'value': 'charts'},
                    {'label': 'B. 每日持仓记录 (Daily Holdings)', 'value': 'holdings'}
                ],
                value='charts',
                labelStyle={'display': 'inline-block', 'margin': '10px'}
            ),

            # --- 交易员 - A. 图表 ---
            html.Div(id='trader-charts-output', children=[
                dcc.Graph(id='trader-assets-chart'),
                dcc.Graph(id='trader-traits-chart')
            ]),

            # --- 交易员 - B. 持仓 (重大修改) ---
            html.Div(id='trader-holdings-output', children=[
                html.H4("每日持仓快照 (Portfolio Snapshot)"),
                html.Div(style={'width': '80%', 'paddingLeft': '20px'}, children=[
                    html.Label("选择日期 (Select Day):"),
                    dcc.Slider(
                        id='holdings-day-slider',
                        min=1,
                        max=max_day,
                        value=1,
                        step=1,
                        marks={i: str(i) for i in range(1, max_day + 1, 25)}
                    ),
                ]),
                dash_table.DataTable(
                    id='trader-holdings-table',
                    columns=[
                        {"name": "持仓 (Holding)", "id": "holding"},
                        {"name": "市值 (Value)", "id": "value"},
                        {"name": "数量 (Quantity)", "id": "quantity"}
                    ],
                    data=[],
                    style_header={'fontWeight': 'bold'},
                    style_cell={'textAlign': 'left'},
                )
            ])
        ]),
    ])
])

# --- 回调 (Callbacks) ---

# 1. 市场 K 线图
@app.callback(
    Output('market-kline-chart', 'figure'),
    Input('main-tabs', 'value')
)
def update_market_kline(tab):
    if tab == 'tab-market' and not df_market.empty:
        fig = go.Figure(data=[go.Candlestick(
            x=df_market['day'],
            open=df_market['open'],
            high=df_market['high'],
            low=df_market['low'],
            close=df_market['close']
        )])
        fig.update_layout(title="市场指数 K线图 (Market Index Candlestick)", xaxis_title="天 (Day)", yaxis_title="指数点位 (Index Points)")
        return fig
    return go.Figure().update_layout(title="无数据 (No Data)")

# 2. 股票 K 线图
@app.callback(
    Output('stock-kline-chart', 'figure'),
    Input('stock-dropdown', 'value')
)
def update_stock_kline(selected_stock):
    if not selected_stock or df_stock.empty:
        return go.Figure().update_layout(title="请选择一只股票")

    df_selected = df_stock[df_stock['stock_id'] == selected_stock]

    fig = go.Figure(data=[go.Candlestick(
        x=df_selected['day'],
        open=df_selected['open'],
        high=df_selected['high'],
        low=df_selected['low'],
        close=df_selected['close']
    )])
    fig.update_layout(title=f"{selected_stock} 每日K线 (Daily Candlestick)", xaxis_title="天 (Day)", yaxis_title="价格 (Price CNY)")
    return fig

# 3. 交易员分析 - 切换显示 (图表 vs 持仓)
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

# 4. 交易员分析 - 图表 (总资产)
@app.callback(
    Output('trader-assets-chart', 'figure'),
    Input('trader-dropdown', 'value')
)
def update_trader_assets_chart(selected_trader):
    if selected_trader is None or df_trader.empty:
        return go.Figure().update_layout(title="请选择一个交易员")

    df_selected = df_trader[df_trader['trader_id'] == selected_trader]

    fig = px.line(df_selected, x='day', y=['total_assets', 'cash', 'stock_value'],
                  title=f"交易员 {selected_trader} - 资产变化 (Asset Variation)")

    # 修改图例和标签
    fig.update_layout(xaxis_title="天 (Day)", yaxis_title="金额 (Value CNY)")
    fig.data[0].name = "总资产 (Total)"
    fig.data[1].name = "现金 (Cash)"
    fig.data[2].name = "股票市值 (Stock Value)"
    return fig

# 5. 交易员分析 - 图表 (参数)
@app.callback(
    Output('trader-traits-chart', 'figure'),
    Input('trader-dropdown', 'value')
)
def update_trader_traits_chart(selected_trader):
    if selected_trader is None or df_trader.empty:
        return go.Figure()

    df_selected = df_trader[df_trader['trader_id'] == selected_trader]

    fig = px.line(df_selected, x='day', y=['risk_tolerance', 'trading_frequency'],
                  title=f"交易员 {selected_trader} - 参数变化 (Parameter Variation)")

    fig.update_layout(xaxis_title="天 (Day)", yaxis_title="参数值 (Value)")
    fig.data[0].name = "风险容忍度 (Risk Tolerance)"
    fig.data[1].name = "交易频率 (Trading Frequency)"
    return fig

# 6. 交易员分析 - 持仓表 (重大修改)
@app.callback(
    Output('trader-holdings-table', 'data'),
    [Input('trader-dropdown', 'value'),
     Input('holdings-day-slider', 'value')]
)
def update_trader_holdings_table(selected_trader, selected_day):
    if selected_trader is None or selected_day is None:
        return []

    conn = connect_db()
    try:
        # 1. 获取当日现金
        cash_query = "SELECT cash FROM trader_log WHERE trader_id = ? AND day = ?"
        df_cash = pd.read_sql_query(cash_query, conn, params=(int(selected_trader), int(selected_day)))

        cash_value = 0.0
        if not df_cash.empty:
            cash_value = df_cash.iloc[0]['cash']

        data = [{'holding': '现金 (Cash)',
                 'value': f"{cash_value:.2f} 元",
                 'quantity': '-'}]

        # 2. 获取当日持仓 (股票)
        holdings_query = """
                         SELECT
                             H.stock_id,
                             H.quantity,
                             S.close AS price_at_close
                         FROM holdings_log H
                                  JOIN stock_log S ON H.stock_id = S.stock_id AND H.day = S.day
                         WHERE H.trader_id = ? AND H.day = ? \
                         """
        df_holdings = pd.read_sql_query(holdings_query, conn, params=(int(selected_trader), int(selected_day)))

        # 3. 格式化数据
        for row in df_holdings.itertuples():
            stock_value = row.quantity * row.price_at_close
            data.append({
                'holding': row.stock_id,
                'value': f"{stock_value:.2f} 元",
                'quantity': f"{row.quantity:.0f} 股"
            })

        return data

    except Exception as e:
        print(f"查询持仓时出错: {e}")
        return []
    finally:
        conn.close()


# --- 运行 App ---
if __name__ == '__main__':
    if df_market.empty or df_stock.empty or df_trader.empty:
        print("\n\n*** 警告：一个或多个数据表为空，UI可能无法正常显示。 ***")
        print("*** 请确保 Java 模拟已成功运行并生成了数据。 ***\n\n")

    app.run(debug=True)