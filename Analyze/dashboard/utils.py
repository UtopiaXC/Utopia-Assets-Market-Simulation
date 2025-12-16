import sqlite3
import pandas as pd
import os

# --- 全局样式配置 ---
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

# --- 数据库辅助函数 ---
def connect_db(db_path):
    """创建数据库连接（支持多线程）"""
    if not db_path or not os.path.exists(db_path):
        return None
    try:
        return sqlite3.connect(db_path, check_same_thread=False)
    except Exception as e:
        print(f"Error connecting to database: {e}")
        return None

# --- 格式化辅助函数 ---
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