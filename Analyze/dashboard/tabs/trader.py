from dash import dcc, html, dash_table
from dash.dependencies import Input, Output, State
import plotly.graph_objects as go
import plotly.express as px
import pandas as pd
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate
from utils import connect_db, format_num, format_large_number, TABLE_STYLE_HEADER, TABLE_STYLE_CELL

order = 3
label = "3. Trader Analysis"
id = "tab-trader"

layout = dbc.Card(dbc.CardBody([
    dbc.Row([
        dbc.Col([
            html.Label("Select Date"),
            dcc.Slider(id='trader-day-slider', min=1, max=100, value=1, step=1, marks=None,
                       tooltip={"placement": "bottom", "always_visible": True}),
        ], width=12)
    ], className="mb-3"),

    dbc.Row([
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
    dbc.Row([
        dbc.Col([
            dbc.Card([
                dbc.CardHeader(html.H5(id='trader-detail-title', children="Trader Details", className="m-0")),
                dbc.CardBody([
                    html.Div(id='trader-metrics-row', className="mb-3"),
                    dbc.Row([
                        dbc.Col([
                            html.H6("Asset Composition History"),
                            dcc.Loading(dcc.Graph(id='trader-asset-structure-chart', style={'height': '300px'})),
                            html.Hr(),
                            html.H6("Risk Tolerance & Behavior"),
                            dcc.Loading(dcc.Graph(id='trader-risk-chart', style={'height': '200px'}))
                        ], width=8),
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

def register_callbacks(app):
    # --- 股票跳转到交易员的逻辑 ---
    @app.callback(
        Output('global-selected-trader-store', 'data'),
        Input('shareholder-table', 'active_cell'), # 注意：这个ID来自 Stock Tab，但这里注册是合法的
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
         Output('trader-metrics-row', 'children')],
        [Input('trader-list-table', 'selected_rows'), Input('trader-day-slider', 'value')],
        [State('trader-list-table', 'data'), State('current-db-path-store', 'data')]
    )
    def update_trader_detail(selected_rows, day, table_data, db_path):
        if not db_path or not selected_rows or not table_data: raise PreventUpdate
        if selected_rows[0] >= len(table_data): raise PreventUpdate
        trader_id = table_data[selected_rows[0]]['trader_id']
        conn = connect_db(db_path)

        # 1. History
        df_hist = pd.read_sql_query(
            "SELECT day, total_assets, private_savings, cash, reserved_cash, stock_value, risk_tolerance FROM trader_log WHERE trader_id = ? ORDER BY day",
            conn, params=(trader_id,))

        # Metrics
        row_curr = df_hist[df_hist['day'] == day]
        if row_curr.empty: metrics_cards = html.Div("No Data")
        else:
            s = row_curr.iloc[0]
            pnl = 0
            pnl_color = "text-secondary"
            prev_row = df_hist[df_hist['day'] == day - 1]
            if not prev_row.empty:
                pnl = s['total_assets'] - prev_row.iloc[0]['total_assets']
                if pnl > 0: pnl_color = "text-success"
                elif pnl < 0: pnl_color = "text-danger"
            pnl_str = ("+" if pnl > 0 else "") + format_large_number(pnl)

            metrics_cards = dbc.Row([
                dbc.Col(dbc.Card([html.H4(format_large_number(s['total_assets'])), html.Small("Total Assets")]), width=2),
                dbc.Col(dbc.Card([html.H4(pnl_str, className=pnl_color), html.Small("Daily PnL")]), width=2),
                dbc.Col(dbc.Card([html.H4(format_large_number(s['private_savings'])), html.Small("Savings")]), width=2),
                dbc.Col(dbc.Card([html.H4(format_large_number(s['cash'] + s['reserved_cash'])), html.Small("Cash")]), width=2),
                dbc.Col(dbc.Card([html.H4(format_large_number(s['stock_value'])), html.Small("Stocks")]), width=2),
                dbc.Col(dbc.Card([html.H4(format_num(s['risk_tolerance'])), html.Small("Risk Tol.")]), width=2),
            ], className="text-center mb-3")

        # Charts
        fig_asset = go.Figure()
        fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['private_savings'], name='Savings', stackgroup='A', line=dict(color='green')))
        fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['stock_value'], name='Stocks', stackgroup='A', line=dict(color='blue')))
        fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['cash'], name='Cash', stackgroup='A', line=dict(color='gold')))
        fig_asset.add_trace(go.Scatter(x=df_hist['day'], y=df_hist['reserved_cash'], name='Frozen', stackgroup='A', line=dict(color='orange')))
        fig_asset.update_layout(title="Asset Composition", margin=dict(t=30, b=20, l=10, r=10), hovermode="x unified")

        fig_risk = px.line(df_hist, x='day', y='risk_tolerance', title=None)
        fig_risk.update_layout(yaxis_title="Risk Tolerance", margin=dict(t=10, b=20, l=10, r=10))

        # Holdings
        df_h = pd.read_sql_query("""
                                 SELECT h.stock_id, h.quantity, s.close
                                 FROM holdings_log h JOIN stock_log s ON h.stock_id=s.stock_id AND h.day=s.day
                                 WHERE h.trader_id=? AND h.day=?""", conn, params=(trader_id, day))
        conn.close()
        df_h['market_value'] = (df_h['quantity'] * df_h['close']).apply(format_large_number)
        df_h['price'] = df_h['close'].apply(format_num)
        df_h['quantity'] = df_h['quantity'].apply(lambda x: f"{int(x):,}")

        return fig_asset, fig_risk, df_h.to_dict('records'), f"Trader {trader_id} Deep Dive", metrics_cards