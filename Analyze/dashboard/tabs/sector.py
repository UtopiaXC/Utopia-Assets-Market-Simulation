from dash import dcc, html
from dash.dependencies import Input, Output
import plotly.express as px
import pandas as pd
import dash_bootstrap_components as dbc
from dash.exceptions import PreventUpdate
from utils import connect_db

order = 5
label = "5. Sectors"
id = "tab-sector"

layout = dbc.Card(dbc.CardBody([
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

def register_callbacks(app):
    @app.callback(
        [Output('sector-mcap-chart', 'figure'), Output('sector-pe-chart', 'figure')],
        Input('current-db-path-store', 'data')
    )
    def update_sector(db_path):
        if not db_path: raise PreventUpdate
        conn = connect_db(db_path)
        df = pd.read_sql_query("SELECT day, sector, SUM(total_market_cap) as cap, AVG(pe_ttm) as pe FROM stock_log GROUP BY day, sector", conn)
        conn.close()

        fig1 = px.line(df, x='day', y='cap', color='sector', title="Sector Market Cap")
        fig2 = px.line(df, x='day', y='pe', color='sector', title="Sector Avg PE")

        return fig1, fig2