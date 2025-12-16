from dash import dcc, html
from dash.dependencies import Input, Output
import plotly.graph_objects as go
import plotly.express as px
import pandas as pd
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate
from utils import connect_db

order = 4
label = "4. Macro Stats"
id = "tab-macro"

layout = dbc.Card(dbc.CardBody([
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

def register_callbacks(app):
    @app.callback(
        [Output('macro-population-chart', 'figure'), Output('macro-wealth-chart', 'figure'),
         Output('agent-type-asset-chart', 'figure'), Output('agent-type-risk-chart', 'figure')],
        Input('current-db-path-store', 'data')
    )
    def update_macro(db_path):
        if not db_path: raise PreventUpdate
        conn = connect_db(db_path)

        # 1. Population
        df_pop = pd.read_sql_query("SELECT day, COUNT(*) as count FROM trader_log WHERE is_active=1 GROUP BY day", conn)
        fig_pop = px.line(df_pop, x='day', y='count', title="Active Agents")

        # 2. Wealth Stack (Social + Savings + Liquidity)
        df_m = pd.read_sql_query("SELECT day, social_wealth_pool FROM market_log", conn)
        df_t = pd.read_sql_query("SELECT day, SUM(private_savings) as sav, SUM(total_assets) as tot FROM trader_log GROUP BY day", conn)

        if df_m.empty or df_t.empty:
            fig_wealth = go.Figure()
        else:
            df_all = pd.merge(df_m, df_t, on='day').fillna(0)
            df_all['Liquidity'] = df_all['tot'] - df_all['sav']

            fig_wealth = go.Figure()
            fig_wealth.add_trace(go.Scatter(x=df_all['day'], y=df_all['social_wealth_pool'], stackgroup='A', name='Social Pool', line=dict(color='gray')))
            fig_wealth.add_trace(go.Scatter(x=df_all['day'], y=df_all['sav'], stackgroup='A', name='Savings', line=dict(color='green')))
            fig_wealth.add_trace(go.Scatter(x=df_all['day'], y=df_all['Liquidity'], stackgroup='A', name='Market Liq', line=dict(color='blue')))
            fig_wealth.update_layout(title="Macro Wealth Flow")

        # 3. Assets by Type
        df_type = pd.read_sql_query("SELECT day, trader_type, SUM(total_assets) as v FROM trader_log GROUP BY day, trader_type", conn)
        fig_type = px.line(df_type, x='day', y='v', color='trader_type', title="Assets by Type")

        # 4. Risk Tolerance
        df_risk = pd.read_sql_query("SELECT day, trader_type, AVG(risk_tolerance) as v FROM trader_log WHERE is_active=1 GROUP BY day, trader_type", conn)
        fig_risk = px.line(df_risk, x='day', y='v', color='trader_type', title="Avg Risk Tolerance")

        conn.close()
        return fig_pop, fig_wealth, fig_type, fig_risk