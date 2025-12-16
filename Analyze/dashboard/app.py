import dash
from dash import dcc, html
import dash_bootstrap_components as dbc
from dash.dependencies import Input, Output, State
from dash.exceptions import PreventUpdate
import glob
import os
import importlib.util

# 尝试导入 utils
try:
    from utils import connect_db
except ImportError:
    try:
        from .utils import connect_db
    except ImportError:
        pass

# --- 初始化 Dash ---
app = dash.Dash(__name__, external_stylesheets=[dbc.themes.BOOTSTRAP])
app.title = "Utopia Market Analysis"

# --- 动态加载 Tabs ---
tabs_dir = os.path.join(os.path.dirname(__file__), "tabs")
tab_modules = []

if os.path.exists(tabs_dir):
    for filename in os.listdir(tabs_dir):
        if filename.endswith(".py") and filename != "__init__.py":
            module_name = f"tabs.{filename[:-3]}"
            file_path = os.path.join(tabs_dir, filename)
            try:
                spec = importlib.util.spec_from_file_location(module_name, file_path)
                mod = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(mod)
                # 检查模块是否符合接口规范
                if hasattr(mod, 'layout') and hasattr(mod, 'register_callbacks') and hasattr(mod, 'order'):
                    tab_modules.append(mod)
            except Exception as e:
                print(f"Error loading module {filename}: {e}")

# 按 order 排序
tab_modules.sort(key=lambda x: x.order)

# 注册所有子模块的回调函数
for mod in tab_modules:
    mod.register_callbacks(app)

# --- 构建 Tab UI ---
tab_components = []
for mod in tab_modules:
    tab_components.append(dbc.Tab(mod.layout, label=mod.label, tab_id=mod.id))

# --- 主布局 ---
app.layout = dbc.Container(fluid=True, className="p-4 bg-light", children=[
    dcc.Store(id='global-selected-trader-store'),
    dcc.Store(id='global-selected-stock-store'),
    dcc.Store(id='current-db-path-store'),

    html.H1("Utopia Market Analysis", className="mb-4 text-primary"),

    # 1. 顶部控制栏
    dbc.Card(dbc.CardBody([
        dbc.Row([
            dbc.Col(html.H5("Simulation Data", className="mt-2 text-secondary"), width="auto"),

            # 【简洁版】只保留输入框，移除 Browse 按钮
            # persistence=True 会自动记住你上次输入的路径（存储在浏览器本地）
            dbc.Col(
                dcc.Input(
                    id='directory-input',
                    value='output', # 默认值
                    type='text',
                    placeholder="Enter absolute path to .db files...",
                    className="form-control",
                    persistence=True,
                    persistence_type='local'
                ),
                width=4
            ),

            dbc.Col(dcc.Dropdown(id='db-dropdown', placeholder='Select Simulation Result...'), width=4),

            dbc.Col(
                dbc.ButtonGroup([
                    dbc.Button("Refresh", id='refresh-button', color='info', outline=True, size="sm"),
                    dbc.Button("Load Data", id='connect-button', color='primary', size="sm"),
                ]), width="auto"
            ),
            dbc.Col(html.Div(id="connection-status", className="mt-2 font-weight-bold"), width="auto")
        ], align="center"),
    ]), className="mb-3 shadow-sm"),

    # Tab 内容区
    dbc.Tabs(id="main-tabs", active_tab=tab_modules[0].id if tab_modules else None, children=tab_components) if tab_modules else html.Div("No tabs loaded.")
])

# --- 全局通用回调 ---

# 1. 刷新文件列表 (监听输入框变化 或 点击刷新按钮)
@app.callback(
    Output('db-dropdown', 'options'),
    [Input('refresh-button', 'n_clicks'),
     Input('directory-input', 'value')],
    State('directory-input', 'value')
)
def update_db_dropdown(n_clicks, input_val_trigger, directory_state):
    # directory_state 是当前输入框的值
    directory = directory_state

    if not directory or not os.path.isdir(directory):
        return []

    try:
        # 兼容 Windows/Mac/Linux 路径
        search_path = os.path.join(directory, "*.db")
        db_files = glob.glob(search_path)
        # 按修改时间倒序，最新的在前面
        db_files.sort(key=os.path.getmtime, reverse=True)
        return [{'label': os.path.basename(f), 'value': f} for f in db_files]
    except Exception as e:
        print(f"Error listing files: {e}")
        return []

# 2. 连接数据库
@app.callback(
    [Output('current-db-path-store', 'data'),
     Output('connection-status', 'children')],
    Input('connect-button', 'n_clicks'),
    State('db-dropdown', 'value'),
    prevent_initial_call=True
)
def connect_database(n_clicks, db_path):
    if not db_path:
        raise PreventUpdate

    conn = connect_db(db_path)
    if not conn:
        return None, dbc.Badge("Failed", color="danger")

    conn.close()
    return db_path, dbc.Badge("Connected", color="success")

if __name__ == '__main__':
    app.run(debug=True)